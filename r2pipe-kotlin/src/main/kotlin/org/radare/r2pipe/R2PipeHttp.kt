package org.radare.r2pipe

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class R2PipeHttp private constructor(
    private val baseUrl: String,
    private val process: Process?,
    private val logger: R2PipeLogger?,
    private val processKiller: ProcessKiller
) : R2PipeSession {
    @Volatile
    private var running = true

    private val requestLock = Any()
    private var closed = false
    private val activeRequests = mutableMapOf<Call, HttpResponseInputStream?>()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .retryOnConnectionFailure(false)
        .build()

    override fun cmd(command: String): String {
        val call = registerCommand(command)
        return try {
            openResponse(call).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            updateRunningState()
            throw RuntimeException("HTTP cmd failed: ${e.message}", e)
        }
    }

    override fun cmdStream(command: String): InputStream {
        val call = registerCommand(command)
        return try {
            openResponse(call)
        } catch (e: Exception) {
            updateRunningState()
            throw RuntimeException("HTTP cmd stream failed: ${e.message}", e)
        }
    }

    override fun interrupt() {
        val currentProcess = process ?: return
        try {
            processKiller.interrupt(currentProcess)
        } catch (_: Exception) {
        }
    }

    override fun forceClose() {
        try {
            closeRequests()
            process?.let { processKiller.terminate(it, true) }
        } finally {
            httpClient.connectionPool.evictAll()
        }
    }

    override fun close() {
        if (!closeRequests()) return
        try {
            val currentProcess = process ?: return
            val processExited = try {
                currentProcess.exitValue()
                true
            } catch (_: IllegalThreadStateException) {
                false
            }
            if (!processExited) {
                try {
                    httpClient.newBuilder()
                        .connectTimeout(1, TimeUnit.SECONDS)
                        .readTimeout(1, TimeUnit.SECONDS)
                        .callTimeout(1, TimeUnit.SECONDS)
                        .build()
                        .newCall(commandRequest("q"))
                        .execute().use { }
                } catch (_: Exception) {
                }
            }
            processKiller.terminate(currentProcess, false)
        } finally {
            httpClient.connectionPool.evictAll()
        }
    }

    override fun isRunning(): Boolean {
        if (!running) return false
        return if (process == null) {
            true
        } else {
            try {
                process.exitValue()
                running = false
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        }
    }

    private fun ensureRunning() {
        if (!isRunning()) {
            throw IllegalStateException("R2 HTTP server is not running")
        }
    }

    private fun updateRunningState() {
        if (process == null) return
        try {
            process.exitValue()
            running = false
        } catch (_: IllegalThreadStateException) {
        }
    }

    private fun commandRequest(command: String): Request = Request.Builder()
        .url("$baseUrl/cmd/${encodeR2Command(command)}")
        .get()
        .build()

    private fun registerCommand(command: String): Call = synchronized(requestLock) {
        ensureRunning()
        httpClient.newCall(commandRequest(command)).also { activeRequests[it] = null }
    }

    private fun openResponse(call: Call): HttpResponseInputStream {
        var response: Response? = null
        try {
            response = call.execute()
            if (response.code != 200) throw IOException("HTTP ${response.code} from r2 server")
            val stream = HttpResponseInputStream(call, response) {
                synchronized(requestLock) { activeRequests.remove(call) }
            }
            val accepted = synchronized(requestLock) {
                if (closed) {
                    false
                } else {
                    activeRequests[call] = stream
                    true
                }
            }
            if (!accepted) throw IOException("R2 HTTP session closed during request")
            return stream
        } catch (e: Exception) {
            call.cancel()
            try {
                response?.close()
            } catch (cleanupError: Exception) {
                e.addSuppressed(cleanupError)
            } finally {
                synchronized(requestLock) { activeRequests.remove(call) }
            }
            throw e
        }
    }

    private fun closeRequests(): Boolean {
        val requests = synchronized(requestLock) {
            if (closed) return false
            closed = true
            running = false
            activeRequests.toList().also { activeRequests.clear() }
        }
        // Cancel sockets before closing streams: a stream may be blocked in read().
        requests.forEach { (call, _) -> call.cancel() }
        requests.forEach { (_, stream) ->
            try {
                stream?.close()
            } catch (_: Exception) {
            }
        }
        return true
    }

    private fun waitForHttpReady(maxRetries: Int, intervalMs: Long) {
        val currentProcess = requireNotNull(process)
        val probeClient = httpClient.newBuilder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .callTimeout(1, TimeUnit.SECONDS)
            .build()
        repeat(maxRetries) { attempt ->
            val ready = try {
                probeClient.newCall(commandRequest("?V")).execute().use { response ->
                    response.code == 200 &&
                        response.body.byteStream().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                            reader.readLine()?.startsWith("radare2 ") == true
                        }
                }
            } catch (_: IOException) {
                false
            }
            val exitCode = try {
                currentProcess.exitValue()
            } catch (_: IllegalThreadStateException) {
                null
            }
            if (exitCode != null) {
                running = false
                throw IllegalStateException("R2 HTTP process exited before becoming ready (exit code $exitCode)")
            }
            if (ready) {
                logger?.log(R2PipeLogLevel.INFO, "R2 HTTP server ready at $baseUrl")
                return
            }
            if (attempt + 1 < maxRetries) Thread.sleep(intervalMs)
        }
        throw IllegalStateException("R2 HTTP server did not become ready within ${maxRetries * intervalMs}ms")
    }

    private fun startDrainThread(
        name: String,
        stream: InputStream,
        level: R2PipeLogLevel,
        prefix: String
    ) {
        thread(name = name, isDaemon = true) {
            try {
                InputStreamReader(stream, StandardCharsets.UTF_8).buffered().useLines { lines ->
                    lines.forEach { line ->
                        logger?.log(level, "$prefix$line")
                    }
                }
            } catch (e: Exception) {
                logger?.log(R2PipeLogLevel.ERROR, "Failed to read $name: ${e.message}")
            }
        }
    }

    companion object {
        private const val HEX_DIGITS = "0123456789ABCDEF"

        @JvmStatic
        @JvmOverloads
        fun connect(
            baseUrl: String,
            logger: R2PipeLogger? = null,
            processKiller: ProcessKiller = ProcessKiller.DEFAULT
        ): R2PipeHttp {
            return R2PipeHttp(
                baseUrl = baseUrl.trimEnd('/'),
                process = null,
                logger = logger,
                processKiller = processKiller
            )
        }

        @JvmStatic
        @JvmOverloads
        fun spawn(
            launchSpec: LaunchSpec,
            port: Int,
            logger: R2PipeLogger? = null,
            processKiller: ProcessKiller = ProcessKiller.DEFAULT,
            maxRetries: Int = 30,
            intervalMs: Long = 200
        ): R2PipeHttp {
            require(port in 1..65535) { "HTTP port must be between 1 and 65535" }
            try {
                ServerSocket().use { socket ->
                    socket.bind(InetSocketAddress("127.0.0.1", port))
                }
            } catch (e: BindException) {
                throw IllegalStateException("R2 HTTP port $port is already in use", e)
            }
            val builder = ProcessBuilder(launchSpec.command)
            launchSpec.workingDirectory?.let(builder::directory)
            builder.environment().putAll(launchSpec.environment)
            builder.redirectErrorStream(false)
            val process = builder.start()
            val client = R2PipeHttp(
                baseUrl = "http://127.0.0.1:$port",
                process = process,
                logger = logger,
                processKiller = processKiller
            )
            try {
                client.startDrainThread(
                    name = "r2pipe-http-stdout",
                    stream = process.inputStream,
                    level = R2PipeLogLevel.INFO,
                    prefix = "[r2-http-stdout] "
                )
                client.startDrainThread(
                    name = "r2pipe-http-stderr",
                    stream = process.errorStream,
                    level = R2PipeLogLevel.WARNING,
                    prefix = ""
                )
                client.waitForHttpReady(maxRetries, intervalMs)
                return client
            } catch (e: Exception) {
                try {
                    client.forceClose()
                } catch (cleanupError: Exception) {
                    e.addSuppressed(cleanupError)
                }
                throw e
            }
        }

        private fun encodeR2Command(command: String): String {
            val output = StringBuilder(command.length)
            for (byte in command.toByteArray(StandardCharsets.UTF_8)) {
                val code = byte.toInt() and 0xFF
                if (code in 'A'.code..'Z'.code || code in 'a'.code..'z'.code ||
                    code in '0'.code..'9'.code || code == '-'.code || code == '_'.code ||
                    code == '.'.code || code == '~'.code
                ) {
                    output.append(code.toChar())
                } else {
                    output.append('%')
                    output.append(HEX_DIGITS[code ushr 4])
                    output.append(HEX_DIGITS[code and 0x0F])
                }
            }
            return output.toString()
        }
    }
}

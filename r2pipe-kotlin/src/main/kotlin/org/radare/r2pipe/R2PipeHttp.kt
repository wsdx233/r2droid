package org.radare.r2pipe

import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class R2PipeHttp private constructor(
    private val baseUrl: String,
    private val process: Process?,
    private val logger: R2PipeLogger?,
    private val processKiller: ProcessKiller
) : R2PipeSession {
    @Volatile
    private var running = true

    override fun cmd(command: String): String {
        ensureRunning()
        val connection = openCommandConnection(command)
        return try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP $responseCode from r2 server")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            updateRunningState()
            throw RuntimeException("HTTP cmd failed: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    override fun cmdStream(command: String): InputStream {
        ensureRunning()
        val connection = openCommandConnection(command)
        return try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                throw RuntimeException("HTTP $responseCode from r2 server")
            }
            HttpResponseInputStream(connection, connection.inputStream)
        } catch (e: Exception) {
            connection.disconnect()
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
        running = false
        process?.let { processKiller.terminate(it, true) }
    }

    override fun close() {
        if (!running) return
        running = false
        try {
            URL("$baseUrl/cmd/q").openConnection().let { it as HttpURLConnection }.run {
                connectTimeout = 1000
                readTimeout = 1000
                requestMethod = "GET"
                try {
                    responseCode
                } catch (_: Exception) {
                } finally {
                    disconnect()
                }
            }
        } catch (_: Exception) {
        }
        process?.let { processKiller.terminate(it, false) }
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

    private fun openCommandConnection(command: String): HttpURLConnection {
        val encoded = encodeR2Command(command)
        return (URL("$baseUrl/cmd/$encoded").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 600_000
            requestMethod = "GET"
        }
    }

    private fun waitForHttpReady(maxRetries: Int, intervalMs: Long) {
        repeat(maxRetries) {
            try {
                val connection = (URL("$baseUrl/cmd/?").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 500
                    readTimeout = 500
                    requestMethod = "GET"
                }
                val responseCode = connection.responseCode
                connection.disconnect()
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    logger?.log(R2PipeLogLevel.INFO, "R2 HTTP server ready at $baseUrl")
                    return
                }
            } catch (_: Exception) {
            }
            Thread.sleep(intervalMs)
        }
        throw RuntimeException("R2 HTTP server did not become ready within ${maxRetries * intervalMs}ms")
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
        private val CHARS_TO_ENCODE = setOf(
            ' ', '"', '#', '%', '<', '>', '[', ']', '^', '`', '{', '}', '\\'
        )

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
                client.forceClose()
                throw e
            }
        }

        private fun encodeR2Command(command: String): String {
            val output = StringBuilder()
            for (byte in command.toByteArray(StandardCharsets.UTF_8)) {
                val code = byte.toInt() and 0xFF
                if (code in 0x80..0xFF || code.toChar() in CHARS_TO_ENCODE) {
                    output.append(String.format("%%%02X", code))
                } else {
                    output.append(code.toChar())
                }
            }
            return output.toString()
        }
    }
}

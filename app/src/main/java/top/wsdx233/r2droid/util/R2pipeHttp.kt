package top.wsdx233.r2droid.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 基于 radare2 HTTP Server API 的 R2Pipe 实现。
 * 启动 r2 时使用 -qc=H 参数开启 HTTP 服务，
 * 然后通过 http://localhost:{port}/cmd/{encoded_cmd} 发送指令。
 */
class R2pipeHttp(
    context: Context,
    private val filePath: String? = null,
    private val flags: String = "",
    private val rawArgs: String? = null,
    private val port: Int = 9090
) {

    private val filesDir: File = context.filesDir
    private var process: Process? = null
    @Volatile
    private var isRunning = false
    private val baseUrl get() = "http://127.0.0.1:$port"

    init {
        startR2HttpServer()
    }

    private fun startR2HttpServer() {
        try {
            val workDir = File(filesDir, "radare2/bin")
            if (!workDir.exists()) workDir.mkdirs()

            val envMap = parseEnvArray(buildEnvironmentVariables())
            val r2Binary = File(workDir, "r2").absolutePath

            val cmdArgs = if (rawArgs != null) {
                "$r2Binary -qc=H -e http.port=$port $rawArgs"
            } else if (filePath != null) {
                "$r2Binary -qc=H -e http.port=$port $flags \"$filePath\""
            } else {
                "$r2Binary -qc=H -e http.port=$port -"
            }

            val command = listOf("/system/bin/sh", "-c", cmdArgs)

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(workDir)
            processBuilder.environment().putAll(envMap)
            processBuilder.redirectErrorStream(false)

            process = processBuilder.start()

            // 消耗 stdout（HTTP 模式下 r2 会在 stdout 输出启动信息）
            Thread {
                try {
                    val reader = process!!.inputStream.bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            LogManager.log(LogType.INFO, "[r2-http-stdout] $it")
                            Log.d("R2PipeHttp", "stdout: $it")
                        }
                    }
                } catch (e: Exception) {
                    LogManager.log(LogType.WARNING, "Failed to read R2 HTTP stdout: ${e.message}")
                }
            }.start()

            // 消耗 stderr
            Thread {
                try {
                    val reader = process!!.errorStream.bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            LogManager.log(LogType.WARNING, it)
                            Log.d("R2PipeHttp", "stderr: $it")
                        }
                    }
                } catch (e: Exception) {
                    LogManager.log(LogType.ERROR, "Failed to read R2 HTTP stderr: ${e.message}")
                }
            }.start()

            // 等待 HTTP 服务就绪
            waitForHttpReady()
            isRunning = true

        } catch (e: Exception) {
            LogManager.log(LogType.ERROR, "Failed to start R2 HTTP server: ${e.message}")
            throw RuntimeException("Failed to start R2 HTTP server: ${e.message}", e)
        }
    }

    /**
     * 轮询等待 HTTP 服务可用
     */
    private fun waitForHttpReady(maxRetries: Int = 30, intervalMs: Long = 200) {
        for (i in 0 until maxRetries) {
            try {
                val url = URL("$baseUrl/cmd/?")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 500
                conn.readTimeout = 500
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                if (code == 200) {
                    LogManager.log(LogType.INFO, "R2 HTTP server ready on port $port")
                    return
                }
            } catch (_: Exception) {
                // 服务还没起来，继续等
            }
            Thread.sleep(intervalMs)
        }
        throw RuntimeException("R2 HTTP server did not become ready within ${maxRetries * intervalMs}ms")
    }

    private fun buildEnvironmentVariables(): List<String> {
        val envs = mutableListOf<String>()
        val existingLd = System.getenv("LD_LIBRARY_PATH")
        val myLd = "${File(filesDir, "radare2/lib")}:${File(filesDir, "libs")}"
        envs.add("LD_LIBRARY_PATH=$myLd${if (existingLd != null) ":$existingLd" else ""}")
        envs.add("XDG_DATA_HOME=${File(filesDir, "r2work")}")
        envs.add("XDG_CACHE_HOME=${File(filesDir, ".cache")}")
        envs.add("HOME=${File(filesDir, "radare2/bin")}")
        envs.add("TERM=dumb")
        envs.add("R2_NOCOLOR=1")
        val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val customBin = File(filesDir, "radare2/bin").absolutePath
        envs.add("PATH=$customBin:$systemPath")
        return envs
    }

    private fun parseEnvArray(envs: List<String>): Map<String, String> {
        val envMap = mutableMapOf<String, String>()
        for (env in envs) {
            val parts = env.split("=", limit = 2)
            if (parts.size == 2) {
                envMap[parts[0]] = parts[1]
            }
        }
        return envMap
    }

    fun cmd(command: String): String {
        Log.i("R2PipeHttp", "cmd: $command")
        LogManager.log(LogType.COMMAND, command)

        if (!isRunning) {
            val msg = "R2 HTTP server is not running"
            LogManager.log(LogType.ERROR, msg)
            throw IllegalStateException(msg)
        }

        val result = try {
            // URLEncoder 将空格编码为 +，但 URL 路径中 + 是字面量，必须用 %20
            val encoded = URLEncoder.encode(command, "UTF-8").replace("+", "%20")
            val url = URL("$baseUrl/cmd/$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 600_000 // 10 分钟，长命令需要
            conn.requestMethod = "GET"

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.disconnect()
                throw RuntimeException("HTTP $responseCode from r2 server")
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            body.trim()
        } catch (e: Exception) {
            // 检查进程是否还活着（兼容低版本 Android）
            try {
                process?.exitValue() // 如果进程已退出，返回退出码；否则抛异常
                isRunning = false    // 没抛异常说明进程已退出
            } catch (_: IllegalThreadStateException) {
                // 进程仍在运行，忽略
            }
            LogManager.log(LogType.ERROR, "HTTP cmd failed: ${e.message}")
            throw RuntimeException("HTTP cmd failed: ${e.message}", e)
        }

        Log.i("R2PipeHttp", "result: $result")
        if (result.isNotBlank()) {
            LogManager.log(LogType.OUTPUT, result)
        }
        return result
    }

    fun cmdj(command: String): String {
        val jsonCommand = if (command.endsWith("j")) command else "${command}j"
        return cmd(jsonCommand)
    }

    /**
     * 流式执行命令 — HTTP 模式下返回 HTTP 响应的 InputStream。
     * 调用方必须在读取完毕后关闭返回的 InputStream。
     */
    fun cmdStream(command: String): InputStream {
        LogManager.log(LogType.COMMAND, command)
        if (!isRunning) throw IllegalStateException("R2 HTTP server not running")

        val encoded = URLEncoder.encode(command, "UTF-8").replace("+", "%20")
        val url = URL("$baseUrl/cmd/$encoded")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 600_000
        conn.requestMethod = "GET"
        // 返回 InputStream，由调用方负责关闭
        return conn.inputStream
    }

    fun quit() {
        try {
            if (isRunning) {
                isRunning = false
                // 尝试优雅关闭
                try {
                    val encoded = URLEncoder.encode("q", "UTF-8")
                    val url = URL("$baseUrl/cmd/$encoded")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    conn.requestMethod = "GET"
                    try { conn.responseCode } catch (_: Exception) {}
                    conn.disconnect()
                } catch (_: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process?.waitFor(500, TimeUnit.MILLISECONDS)
                }
                process?.destroy()
            }
        } catch (_: Exception) {}
    }

    fun interrupt() {
        try {
            val proc = process ?: return
            val pid = getProcessId(proc)
            if (pid > 0) {
                val cmd = "kill -2 -$pid 2>/dev/null; kill -2 \$(pgrep -P $pid) 2>/dev/null"
                Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", cmd))
                LogManager.log(LogType.INFO, "Sent SIGINT to R2 HTTP process (pid=$pid)")
            }
        } catch (e: Exception) {
            LogManager.log(LogType.WARNING, "Failed to send interrupt: ${e.message}")
        }
    }

    private fun getProcessId(process: Process): Int {
        return try {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        } catch (_: Exception) { -1 }
    }

    fun forceQuit() {
        isRunning = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process?.destroyForcibly()
            } else {
                process?.destroy()
            }
        } catch (_: Exception) {}
        process = null
    }

    fun isProcessRunning(): Boolean = isRunning

    protected fun finalize() {
        quit()
    }
}

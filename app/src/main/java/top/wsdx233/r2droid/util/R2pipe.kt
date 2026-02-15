package top.wsdx233.r2droid.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

class R2pipe(context: Context, private val filePath: String? = null, private val flags: String = "") {

    private val filesDir: File = context.filesDir
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var inputStream: BufferedInputStream? = null
    private var isRunning = false

    init {
        startR2Process()
    }

    private fun startR2Process() {
        try {
            val workDir = File(filesDir, "radare2/bin")
            if (!workDir.exists()) workDir.mkdirs()

            val envMap = parseEnvArray(buildEnvironmentVariables())
            val r2Binary = File(workDir, "r2").absolutePath

            // 使用 sh 启动
            val cmdArgs = if (filePath != null) {
                "$r2Binary -q0 $flags \"$filePath\""
            } else {
                "$r2Binary -q0 -"
            }

            val command = listOf("/system/bin/sh", "-c", cmdArgs)

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(workDir)
            processBuilder.environment().putAll(envMap)

            // 必须分离 stderr，否则日志混入 stdout 会导致协议解析错误
            processBuilder.redirectErrorStream(false)

            process = processBuilder.start()
            
            // 启动线程消耗 Stderr，防止缓冲区填满导致死锁
            Thread {
                try {
                    val reader = process!!.errorStream.bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        // 记录日志
                        line?.let {
                            LogManager.log(LogType.WARNING, it)
                            Log.d("R2Pipe", it)
                        }
                    }
                } catch (e: Exception) {
                    LogManager.log(LogType.ERROR, "Failed to read R2 stderr: ${e.message}")
                }
            }.start()

            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            inputStream = BufferedInputStream(process!!.inputStream)

            isRunning = true

        } catch (e: Exception) {
            LogManager.log(LogType.ERROR, "Failed to start R2 process: ${e.message}")
            throw RuntimeException("Failed to start R2 process: ${e.message}", e)
        }
    }

    private fun buildEnvironmentVariables(): List<String> {
        val envs = mutableListOf<String>()
        val existingLd = System.getenv("LD_LIBRARY_PATH")
        val myLd = "${File(filesDir, "radare2/lib")}:${File(filesDir, "libs")}"
        envs.add("LD_LIBRARY_PATH=$myLd${if (existingLd != null) ":$existingLd" else ""}")

        envs.add("XDG_DATA_HOME=${File(filesDir, "r2work")}")
        envs.add("XDG_CACHE_HOME=${File(filesDir, ".cache")}")
        envs.add("HOME=${File(filesDir, "radare2/bin")}")

        // 禁用颜色和控制码
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

    /**
     * 清理输入流中的残留数据
     * 这可以解决“启动后有残留 \0”导致的差一同步问题
     */
    private fun flushInputStream() {
        try {
            // available() 返回在不阻塞的情况下可以读取的字节数
            // 如果管道里有上次没读完的数据，或者启动时的残留，这里会全部丢弃
            while (inputStream != null && inputStream!!.available() > 0) {
                inputStream!!.read()
            }
        } catch (e: Exception) {
            // 忽略清理过程中的错误
        }
    }

    fun cmd(command: String): String {

        Log.i("R2Pipe", "cmd:$command")
        LogManager.log(LogType.COMMAND, command)

        if (!isRunning || process == null) {
            val msg = "R2 process is not running"
            LogManager.log(LogType.ERROR, msg)
            throw IllegalStateException(msg)
        }

        val result = try {
            // 发送命令前，先清理掉管道里残留的垃圾数据
            flushInputStream()

            // 发送命令
            writer?.write(command)
            writer?.newLine()
            writer?.flush()

            // 读取结果
            readResult()
        } catch (e: Exception) {
            isRunning = false
            LogManager.log(LogType.ERROR, "Cmd execution failed: ${e.message}")
            throw RuntimeException("Cmd execution failed: ${e.message}", e)
        }

        Log.i("R2Pipe", "result:$result")
        if (result.isNotBlank()) {
            LogManager.log(LogType.OUTPUT, result)
        }

        return result
    }

    private fun readResult(): String {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(4096) // 4KB Buffer，类似 Python 的 chunks

        while (true) {
            val bytesRead = inputStream?.read(buffer) ?: -1
            if (bytesRead == -1) {
                isRunning = false
                if (baos.size() > 0) return baos.toString("UTF-8")
                throw RuntimeException("R2 process terminated unexpectedly (EOF)")
            }

            // 查找 buffer 中是否有 0 (NULL)
            var nullIndex = -1
            for (i in 0 until bytesRead) {
                if (buffer[i] == 0.toByte()) {
                    nullIndex = i
                    break
                }
            }

            if (nullIndex != -1) {
                // 找到了结束符，只写入结束符之前的数据
                baos.write(buffer, 0, nullIndex)
                // 注意：如果有后续逻辑需要处理粘包（即 0 后面还有数据），
                // 这里需要像 Python 那样把剩下的存起来。
                // 但对于标准的请求-响应模型，通常扔掉或留给 flush 也可以。
                break
            } else {
                // 没找到结束符，写入全部读取的数据，继续读
                baos.write(buffer, 0, bytesRead)
            }
        }

        // 建议模仿 Python 加上 ignore，防止二进制数据导致 crash
        return try {
            baos.toString("UTF-8").trim()
        } catch (e: Exception) {
            baos.toString() // Fallback
        }
    }

    fun cmdj(command: String): String {
        val jsonCommand = if (command.endsWith("j")) command else "${command}j"
        return cmd(jsonCommand)
    }

    fun quit() {
        try {
            if (isRunning) {
                writer?.write("q\n")
                writer?.flush()
                writer?.close()
                inputStream?.close()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process?.waitFor(200, TimeUnit.MILLISECONDS)
                }
                process?.destroy()
                isRunning = false
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * 强制终止 R2 进程，不发送 quit 命令，不等待。
     * 用于在长时间命令执行期间需要立即终止的场景。
     */
    fun forceQuit() {
        isRunning = false
        try { writer?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
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

    companion object {
        fun open(context: Context, filePath: String, flags: String = ""): R2pipe {
            return R2pipe(context, filePath, flags)
        }

        fun open(context: Context): R2pipe {
            return R2pipe(context)
        }
    }
}
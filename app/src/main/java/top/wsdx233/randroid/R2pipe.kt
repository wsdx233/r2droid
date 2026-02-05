
package top.wsdx233.randroid

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

class R2pipe(private val context: Context, private val filePath: String? = null) {

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var inputStream: BufferedInputStream? = null
    private var isRunning = false

    init {
        startR2Process()
    }

    private fun startR2Process() {
        try {
            val workDir = File(context.filesDir, "radare2/bin")
            if (!workDir.exists()) workDir.mkdirs()

            val envMap = parseEnvArray(buildEnvironmentVariables())
            val r2Binary = File(workDir, "r2").absolutePath

            // 使用 sh 启动
            val cmdArgs = if (filePath != null) {
                "$r2Binary -q0 $filePath"
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

            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            inputStream = BufferedInputStream(process!!.inputStream)

            isRunning = true

        } catch (e: Exception) {
            throw RuntimeException("Failed to start R2 process: ${e.message}", e)
        }
    }

    private fun buildEnvironmentVariables(): List<String> {
        val envs = mutableListOf<String>()
        val existingLd = System.getenv("LD_LIBRARY_PATH")
        val myLd = "${File(context.filesDir, "radare2/lib")}:${File(context.filesDir, "libs")}"
        envs.add("LD_LIBRARY_PATH=$myLd${if (existingLd != null) ":$existingLd" else ""}")

        envs.add("XDG_DATA_HOME=${File(context.filesDir, "r2work")}")
        envs.add("XDG_CACHE_HOME=${File(context.filesDir, ".cache")}")
        envs.add("HOME=${File(context.filesDir, "radare2/bin")}")

        // 禁用颜色和控制码
        envs.add("TERM=dumb")
        envs.add("R2_NOCOLOR=1")

        val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val customBin = File(context.filesDir, "radare2/bin").absolutePath
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
        if (!isRunning || process == null) {
            throw IllegalStateException("R2 process is not running")
        }

        return try {
            // 1. 关键修复：发送命令前，先清理掉管道里残留的垃圾数据
            flushInputStream()

            // 2. 发送命令
            writer?.write(command)
            writer?.newLine()
            writer?.flush()

            // 3. 读取结果
            readResult()
        } catch (e: Exception) {
            isRunning = false
            throw RuntimeException("Cmd execution failed: ${e.message}", e)
        }
    }

    private fun readResult(): String {
        val baos = ByteArrayOutputStream()

        while (true) {
            // 阻塞读取
            val byte = inputStream?.read() ?: -1

            if (byte == -1) {
                isRunning = false
                if (baos.size() > 0) return baos.toString("UTF-8")
                throw RuntimeException("R2 process terminated unexpectedly (EOF)")
            }

            // 遇到 r2pipe 协议结束符 (NULL byte)
            if (byte == 0) {
                break
            }

            baos.write(byte)
        }

        return baos.toString("UTF-8").trim()
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    process?.waitFor(200, TimeUnit.MILLISECONDS)
                }
                process?.destroy()
                isRunning = false
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun isProcessRunning(): Boolean = isRunning

    protected fun finalize() {
        quit()
    }

    companion object {
        fun open(context: Context, filePath: String): R2pipe {
            return R2pipe(context, filePath)
        }

        fun open(context: Context): R2pipe {
            return R2pipe(context)
        }
    }
}
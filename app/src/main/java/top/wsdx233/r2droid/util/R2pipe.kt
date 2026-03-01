package top.wsdx233.r2droid.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

class R2pipe(context: Context, private val filePath: String? = null, private val flags: String = "", private val rawArgs: String? = null) {

    private val filesDir: File = context.filesDir
    private var process: Process? = null
    private var inputStream: BufferedInputStream? = null
    private var outputStream: OutputStream? = null
    private var inputChannel: ReadableByteChannel? = null
    private var outputChannel: WritableByteChannel? = null
    private var isRunning = false
    private var pid: Int = -1

    // 自动计算且自适应的缓冲区
    private var readBuffer: ByteBuffer = ByteBuffer.allocateDirect(256 * 1024) // 初始 256KB（读）
    private val writeBuffer: ByteBuffer = ByteBuffer.allocateDirect(16 * 1024) // 固定 16KB（写，不参与计算）
    private var resultBuffer: ByteBuffer = ByteBuffer.allocateDirect(512 * 1024) // 初始 512KB
    private val maxBuffer = 4 * 1024 * 1024 // 限制最大 4MB，防止过大可能产生额外的问题

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
            val cmdArgs = if (rawArgs != null) {
                "$r2Binary -q0 $rawArgs"
            } else if (filePath != null) {
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
            // 获取pid
            pid = getProcessId(process!!)

            // 异步消耗 Stderr，防止缓冲区填满导致死锁
            CompletableFuture.runAsync {
                try {
                    val reader = process!!.errorStream.bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            LogManager.log(LogType.WARNING, it)
                            Log.d("R2Pipe", it)
                        }
                    }
                } catch (e: Exception) {
                    LogManager.log(LogType.ERROR, "Failed to read R2 stderr: ${e.message}")
                }
            }

            outputStream = process!!.outputStream
            outputChannel = Channels.newChannel(outputStream!!)

            // 缓冲字节
            inputStream = BufferedInputStream(process!!.inputStream, 65536)
            inputChannel = Channels.newChannel(inputStream!!)

            isRunning = true
            // 及时释放残留数据
            flushInputStream()

        } catch (e: Exception) {
            LogManager.log(LogType.ERROR, "Failed to start R2 process: ${e.message}")
            throw RuntimeException("Failed to start R2 process: ${e.message}", e)
        }
    }

    private fun buildEnvironmentVariables(): List<String> = mutableListOf<String>().apply {
        val existingLd = System.getenv("LD_LIBRARY_PATH")
        val myLd = "${File(filesDir, "radare2/lib")}:${File(filesDir, "libs")}"
        add("LD_LIBRARY_PATH=$myLd${if (existingLd != null) ":$existingLd" else ""}")

        add("XDG_DATA_HOME=${File(filesDir, "r2work")}")
        add("XDG_CACHE_HOME=${File(filesDir, ".cache")}")
        add("HOME=${File(filesDir, "radare2/bin")}")

        // 禁用颜色和控制码
        add("TERM=dumb")
        add("R2_NOCOLOR=1")

        val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val customBin = File(filesDir, "radare2/bin").absolutePath
        add("PATH=$customBin:$systemPath")
    }

    private fun parseEnvArray(envs: List<String>): Map<String, String> = mutableMapOf<String, String>().apply {
        for (env in envs) {
            val parts = env.split("=", limit = 2)
            if (parts.size == 2) this[parts[0]] = parts[1]
        }
    }

    /**
     * 清理输入流中的残留数据
     * 这可以解决“启动后有残留 \0”导致的差一同步问题
     */
    private fun flushInputStream() {
        try {
            while (inputStream != null && inputStream!!.available() > 0) {
                inputStream!!.read()
            }
        } catch (_: Exception) {
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
            writeCommand(command)
            // 读取结果
            readResultNio()
        } catch (e: Exception) {
            isRunning = false
            LogManager.log(LogType.ERROR, "Cmd execution failed: ${e.message}")
            throw RuntimeException("Cmd execution failed: ${e.message}", e)
        }

        Log.i("R2Pipe", "result:$result")
        if (result.isNotBlank()) LogManager.log(LogType.OUTPUT, result)

        return result
    }

    private fun writeCommand(command: String) {
        writeBuffer.clear()
        writeBuffer.put(command.toByteArray(Charsets.UTF_8))
        writeBuffer.put(10.toByte()) // \n
        writeBuffer.flip()
        while (writeBuffer.hasRemaining()) outputChannel?.write(writeBuffer)
        outputStream?.flush()
    }

    /** NIO + 自动自适应读取 */
    private fun readResultNio(): String {
        resultBuffer.clear()

        while (true) {
            readBuffer.clear()
            val bytesRead = inputChannel?.read(readBuffer) ?: -1
            if (bytesRead == -1) {
                isRunning = false
                if (resultBuffer.position() > 0) return finishResult()
                throw RuntimeException("R2 process terminated unexpectedly (EOF)")
            }

            readBuffer.flip()

            val nullIndex = findFirstNull(readBuffer)
            if (nullIndex != -1) {
                ensureResultCapacity(resultBuffer.position() + nullIndex)
                readBuffer.limit(nullIndex) // 只取到 \0 前
                resultBuffer.put(readBuffer)
                break
            } else {
                ensureResultCapacity(resultBuffer.position() + readBuffer.remaining())
                resultBuffer.put(readBuffer)
            }
        }

        return finishResult()
    }

    /** 自适应逻辑：输出过大则下次扩容 */
    private fun ensureResultCapacity(needed: Int) {
        if (needed > resultBuffer.capacity()) {
            val newSize = min(maxBuffer, (resultBuffer.capacity() * 2).coerceAtLeast(needed))
            if (newSize > resultBuffer.capacity()) {
                val newByteBuffer = ByteBuffer.allocateDirect(newSize)
                resultBuffer.flip()
                newByteBuffer.put(resultBuffer)
                resultBuffer = newByteBuffer
                Log.i("R2Pipe", "Auto-expanded resultBuffer to ${newSize / 1024}KB")
            }
        }
    }

    // 查找 buffer 中是否有 0 (NULL)
    private fun findFirstNull(buffer: ByteBuffer): Int {
        val lim = buffer.limit()
        for (i in 0 until lim) {
            if (buffer.get(i) == 0.toByte()) return i
        }
        return -1
    }

    private fun finishResult(): String {
        resultBuffer.flip()
        val len = resultBuffer.remaining()
        if (len == 0) return ""

        val bytes = ByteArray(len)
        resultBuffer.get(bytes)
        return try {
            String(bytes, StandardCharsets.UTF_8).trim()
        } catch (_: Exception) {
            bytes.toString(Charsets.UTF_8).trim()
        }
    }

    fun cmdj(command: String): String {
        val jsonCommand = if (command.endsWith("j")) command else "${command}j"
        return cmd(jsonCommand)
    }

    /**
     * 流式执行命令，返回一个 InputStream，遇到 \0 时返回 EOF。
     * 调用方必须在读取完毕后关闭返回的 InputStream。
     */
    fun cmdStream(command: String): InputStream {
        LogManager.log(LogType.COMMAND, command)
        if (!isRunning || process == null) throw IllegalStateException("R2 not running")
        flushInputStream()
        writeCommand(command)
        return R2ProcessInputStream(inputStream!!)
    }

    fun quit() {
        try {
            if (isRunning) {
                writeCommand("q")
                outputChannel?.close()
                inputChannel?.close()
                inputStream?.close()
                outputStream?.close()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process?.waitFor(200, TimeUnit.MILLISECONDS)
                }
                process?.destroy()
                isRunning = false
            }
        } catch (_: Exception) {
            // ignore
        }
    }

    /**
     * 强制终止 R2 进程，不发送 quit 命令，不等待。
     * 用于在长时间命令执行期间需要立即终止的场景。
     */
    /**
     * 通过 kill 命令向 R2 进程及其子进程发送 SIGINT，中断当前操作。
     * 因为 r2 是通过 sh -c 启动的，直接 sendSignal 可能只发给了 sh 而非 r2 本身，
     * 所以这里用 kill 命令同时覆盖进程组、父进程和子进程。
     */
    fun interrupt() {
        try {
            if (pid > 0) {
                val cmd = "kill -2 -$pid 2>/dev/null; kill -2 \$(pgrep -P $pid) 2>/dev/null"
                Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", cmd))
                LogManager.log(LogType.INFO, "Sent SIGINT via kill to R2 process group (pid=$pid)")
            }
        } catch (e: Exception) {
            LogManager.log(LogType.WARNING, "Failed to send interrupt: ${e.message}")
        }
    }

    private fun getProcessId(process: Process): Int = try {
        val field = process.javaClass.getDeclaredField("pid")
        field.isAccessible = true
        field.getInt(process)
    } catch (_: Exception) { -1 }

    fun forceQuit() {
        isRunning = false
        try { outputChannel?.close(); inputChannel?.close() } catch (_: Exception) {}
        try { inputStream?.close(); outputStream?.close() } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) process?.destroyForcibly() else process?.destroy()
        } catch (_: Exception) {}
        process = null
    }

    fun isProcessRunning(): Boolean = isRunning

    protected fun finalize() { quit() }

    companion object {
        fun open(context: Context, filePath: String, flags: String = ""): R2pipe = R2pipe(context, filePath, flags)
        fun open(context: Context): R2pipe = R2pipe(context)
    }
}

/**
 * 包装 InputStream，当读到 \0 时表示当前命令输出结束，返回 EOF (-1)。
 * 注意：close() 不会关闭底层流（底层流由 R2pipe 管理）。
 */
class R2ProcessInputStream(private val source: InputStream) : InputStream() {
    private var ended = false

    override fun read(): Int {
        if (ended) return -1
        val b = source.read()
        if (b <= 0) { ended = true; return -1 }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (ended) return -1
        val count = source.read(b, off, len)
        if (count <= 0) { ended = true; return -1 }
        for (i in off until off + count) {
            if (b[i].toInt() == 0) {
                ended = true
                return if (i == off) -1 else i - off
            }
        }
        return count
    }

    override fun close() { /* 不关闭底层流 */ }
}
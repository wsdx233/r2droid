package org.radare.r2pipe

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import kotlin.math.min

class R2Pipe private constructor(
    private val process: Process,
    private val stdout: BufferedInputStream,
    private val stdoutChannel: ReadableByteChannel,
    private val stdin: OutputStream,
    private val logger: R2PipeLogger?,
    private val processKiller: ProcessKiller
) : R2PipeSession {
    @Volatile
    private var running = true

    private val readBuffer: ByteBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE)
    private var resultBuffer: ByteBuffer = ByteBuffer.allocateDirect(INITIAL_RESULT_BUFFER_SIZE)
    private var overflowChunks: MutableList<ByteArray>? = null
    private var overflowSize: Int = 0

    init {
        startDrainThread(
            name = "r2pipe-stderr",
            stream = process.errorStream,
            level = R2PipeLogLevel.WARNING,
            prefix = ""
        )
        awaitReadyMarker()
    }

    override fun cmd(command: String): String {
        ensureRunning()
        return try {
            discardAvailableInput()
            writeCommand(command)
            readUntilNull()
        } catch (e: Exception) {
            running = false
            throw RuntimeException("Cmd execution failed: ${e.message}", e)
        }
    }

    override fun cmdStream(command: String): InputStream {
        ensureRunning()
        return try {
            discardAvailableInput()
            writeCommand(command)
            NullDelimitedInputStream(stdout)
        } catch (e: Exception) {
            running = false
            throw RuntimeException("Cmd stream failed: ${e.message}", e)
        }
    }

    override fun interrupt() {
        if (!running) return
        try {
            processKiller.interrupt(process)
        } catch (_: Exception) {
        }
    }

    override fun forceClose() {
        running = false
        closeStreams()
        processKiller.terminate(process, true)
    }

    override fun close() {
        if (!running) return
        try {
            writeCommand("q")
        } catch (_: Exception) {
        } finally {
            running = false
            closeStreams()
            processKiller.terminate(process, false)
        }
    }

    override fun isRunning(): Boolean {
        if (!running) return false
        return try {
            process.exitValue()
            running = false
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }

    private fun ensureRunning() {
        if (!isRunning()) {
            throw IllegalStateException("R2 process is not running")
        }
    }

    private fun awaitReadyMarker() {
        val buffer = ByteArray(1)
        while (true) {
            val count = stdout.read(buffer)
            if (count == -1) {
                running = false
                throw IOException("R2 process terminated before initial ready marker")
            }
            if (buffer[0].toInt() == 0) {
                return
            }
        }
    }

    private fun discardAvailableInput() {
        while (stdout.available() > 0) {
            if (stdout.read() == -1) {
                running = false
                break
            }
        }
    }

    private fun writeCommand(command: String) {
        stdin.write(command.toByteArray(StandardCharsets.UTF_8))
        stdin.write('\n'.code)
        stdin.flush()
    }

    private fun readUntilNull(): String {
        resetResultBuffer()
        while (true) {
            readBuffer.clear()
            val count = stdoutChannel.read(readBuffer)
            if (count == -1) {
                running = false
                if (currentResultSize() > 0) {
                    return finishResult()
                }
                throw IOException("R2 process terminated unexpectedly (EOF)")
            }

            readBuffer.flip()
            val nullIndex = firstNullIndex(readBuffer)
            if (nullIndex >= 0) {
                val originalLimit = readBuffer.limit()
                readBuffer.limit(nullIndex)
                appendResult(readBuffer)
                readBuffer.limit(originalLimit)
                break
            }
            appendResult(readBuffer)
        }
        return finishResult()
    }

    private fun resetResultBuffer() {
        resultBuffer.clear()
        overflowChunks = null
        overflowSize = 0
    }

    private fun currentResultSize(): Int = resultBuffer.position() + overflowSize

    private fun appendResult(source: ByteBuffer) {
        val length = source.remaining()
        if (length == 0) return

        val needed = resultBuffer.position() + length
        if (overflowChunks == null && needed <= MAX_RETAINED_RESULT_BUFFER_SIZE) {
            ensureResultCapacity(needed)
            resultBuffer.put(source)
            return
        }

        val chunks = overflowChunks ?: mutableListOf<ByteArray>().also { overflowChunks = it }
        while (source.hasRemaining()) {
            val chunkSize = min(source.remaining(), OVERFLOW_CHUNK_SIZE)
            val chunk = ByteArray(chunkSize)
            source.get(chunk)
            chunks += chunk
            overflowSize += chunkSize
        }
    }

    private fun ensureResultCapacity(needed: Int) {
        if (needed <= resultBuffer.capacity()) return

        var newSize = resultBuffer.capacity()
        while (newSize < needed && newSize < MAX_RETAINED_RESULT_BUFFER_SIZE) {
            newSize *= 2
        }
        newSize = min(newSize, MAX_RETAINED_RESULT_BUFFER_SIZE)

        val newBuffer = ByteBuffer.allocateDirect(newSize)
        resultBuffer.flip()
        newBuffer.put(resultBuffer)
        resultBuffer = newBuffer
    }

    private fun finishResult(): String {
        val retainedSize = resultBuffer.position()
        val totalSize = retainedSize.toLong() + overflowSize.toLong()
        if (totalSize == 0L) return ""
        if (totalSize > Int.MAX_VALUE) {
            throw IOException("R2 command output is too large: $totalSize bytes")
        }

        val bytes = ByteArray(totalSize.toInt())
        resultBuffer.flip()
        resultBuffer.get(bytes, 0, retainedSize)

        var offset = retainedSize
        overflowChunks?.forEach { chunk ->
            System.arraycopy(chunk, 0, bytes, offset, chunk.size)
            offset += chunk.size
        }
        overflowChunks = null
        overflowSize = 0

        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun closeStreams() {
        try {
            stdin.close()
        } catch (_: Exception) {
        }
        try {
            stdoutChannel.close()
        } catch (_: Exception) {
        }
        try {
            stdout.close()
        } catch (_: Exception) {
        }
    }

    private fun startDrainThread(
        name: String,
        stream: InputStream,
        level: R2PipeLogLevel,
        prefix: String
    ) {
        thread(name = name, isDaemon = true) {
            try {
                stream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        logger?.log(level, if (prefix.isEmpty()) line else "$prefix$line")
                    }
                }
            } catch (e: Exception) {
                logger?.log(R2PipeLogLevel.ERROR, "Failed to read $name: ${e.message}")
            }
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun open(
            launchSpec: LaunchSpec,
            logger: R2PipeLogger? = null,
            processKiller: ProcessKiller = ProcessKiller.DEFAULT
        ): R2Pipe {
            val builder = ProcessBuilder(launchSpec.command)
            launchSpec.workingDirectory?.let(builder::directory)
            builder.environment().putAll(launchSpec.environment)
            builder.redirectErrorStream(false)
            val process = builder.start()
            val stdout = BufferedInputStream(process.inputStream, STREAM_BUFFER_SIZE)
            val stdoutChannel = Channels.newChannel(stdout)
            return try {
                R2Pipe(
                    process = process,
                    stdout = stdout,
                    stdoutChannel = stdoutChannel,
                    stdin = process.outputStream,
                    logger = logger,
                    processKiller = processKiller
                )
            } catch (e: Exception) {
                try {
                    stdoutChannel.close()
                } catch (_: Exception) {
                }
                try {
                    stdout.close()
                } catch (_: Exception) {
                }
                try {
                    process.outputStream.close()
                } catch (_: Exception) {
                }
                processKiller.terminate(process, true)
                throw e
            }
        }

        private const val STREAM_BUFFER_SIZE = 64 * 1024
        private const val READ_BUFFER_SIZE = 256 * 1024
        private const val INITIAL_RESULT_BUFFER_SIZE = 512 * 1024
        private const val MAX_RETAINED_RESULT_BUFFER_SIZE = 4 * 1024 * 1024
        private const val OVERFLOW_CHUNK_SIZE = 256 * 1024

        private fun firstNullIndex(buffer: ByteBuffer): Int {
            val limit = buffer.limit()
            for (index in 0 until limit) {
                if (buffer.get(index).toInt() == 0) {
                    return index
                }
            }
            return -1
        }
    }
}

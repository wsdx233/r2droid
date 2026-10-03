package org.radare.r2pipe

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.min

class R2Pipe private constructor(
    private val process: Process,
    private val stdout: PushbackInputStream,
    private val stdin: OutputStream,
    private val logger: R2PipeLogger?,
    private val processKiller: ProcessKiller
) : R2PipeSession {
    @Volatile
    private var running = true

    private val closed = AtomicBoolean(false)
    private val commandLock = Any()

    @Volatile
    private var activeStream: NullDelimitedInputStream? = null

    private val readBuffer: ByteBuffer = ByteBuffer.allocate(NullDelimitedInputStream.READ_BUFFER_SIZE)
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

    override fun cmd(command: String): String = synchronized(commandLock) {
        ensureCommandAvailable(command)
        try {
            val stream = beginResponse()
            writeCommand(command)
            readUntilNull(stream)
        } catch (e: Exception) {
            forceClose()
            throw RuntimeException("Cmd execution failed: ${e.message}", e)
        }
    }

    override fun cmdStream(command: String): InputStream = synchronized(commandLock) {
        ensureCommandAvailable(command)
        try {
            val stream = beginResponse()
            writeCommand(command)
            stream
        } catch (e: Exception) {
            forceClose()
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
        shutdown(force = true)
    }

    override fun close() {
        shutdown(force = !running || activeStream != null)
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

    private fun ensureCommandAvailable(command: String) {
        if (!isRunning()) {
            throw IllegalStateException("R2 process is not running")
        }
        check(activeStream == null) { "The previous R2 response stream is still active" }
        require('\u0000' !in command) {
            "Stdio commands must not contain NUL bytes"
        }
    }

    private fun beginResponse(): NullDelimitedInputStream {
        return NullDelimitedInputStream(
            source = stdout,
            onComplete = { synchronized(commandLock) { activeStream = null } },
            onIncomplete = ::forceClose
        ).also { activeStream = it }
    }

    private fun shutdown(force: Boolean) {
        running = false
        if (!closed.compareAndSet(false, true)) {
            if (force) {
                try {
                    process.exitValue()
                } catch (_: IllegalThreadStateException) {
                    processKiller.terminate(process, true)
                }
            }
            return
        }
        activeStream = null
        try {
            processKiller.terminate(process, force)
        } finally {
            closeStreams()
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


    private fun writeCommand(command: String) {
        val request = if ('\n' in command || '\r' in command) {
            buildString(command.length) {
                var previousWasCarriageReturn = false
                for (character in command) {
                    if (character != '\n' || !previousWasCarriageReturn) {
                        append(if (character == '\n' || character == '\r') ';' else character)
                    }
                    previousWasCarriageReturn = character == '\r'
                }
            }
        } else {
            command
        }
        stdin.write(request.toByteArray(StandardCharsets.UTF_8))
        stdin.write('\n'.code)
        stdin.flush()
    }

    private fun readUntilNull(stream: InputStream): String {
        resetResultBuffer()
        while (true) {
            val count = stream.read(readBuffer.array(), 0, readBuffer.capacity())
            if (count == -1) break
            readBuffer.clear()
            readBuffer.limit(count)
            appendResult(readBuffer)
        }
        return finishResult()
    }

    private fun resetResultBuffer() {
        resultBuffer.clear()
        overflowChunks = null
        overflowSize = 0
    }


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
            stdout.close()
        } catch (_: Exception) {
        }
        try {
            process.errorStream.close()
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
            val stdout = PushbackInputStream(
                BufferedInputStream(process.inputStream, STREAM_BUFFER_SIZE),
                NullDelimitedInputStream.READ_BUFFER_SIZE
            )
            return try {
                R2Pipe(
                    process = process,
                    stdout = stdout,
                    stdin = process.outputStream,
                    logger = logger,
                    processKiller = processKiller
                )
            } catch (e: Exception) {
                try {
                    processKiller.terminate(process, true)
                } catch (cleanupError: Exception) {
                    e.addSuppressed(cleanupError)
                }
                try {
                    stdout.close()
                } catch (_: Exception) {
                }
                try {
                    process.outputStream.close()
                } catch (_: Exception) {
                }
                try {
                    process.errorStream.close()
                } catch (_: Exception) {
                }
                throw e
            }
        }

        private const val STREAM_BUFFER_SIZE = 64 * 1024
        private const val INITIAL_RESULT_BUFFER_SIZE = 512 * 1024
        private const val MAX_RETAINED_RESULT_BUFFER_SIZE = 4 * 1024 * 1024
        private const val OVERFLOW_CHUNK_SIZE = 256 * 1024

    }
}

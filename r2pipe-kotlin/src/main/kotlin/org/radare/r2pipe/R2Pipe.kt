package org.radare.r2pipe

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class R2Pipe private constructor(
    private val process: Process,
    private val stdout: BufferedInputStream,
    private val stdin: OutputStream,
    private val logger: R2PipeLogger?,
    private val processKiller: ProcessKiller
) : R2PipeSession {
    @Volatile
    private var running = true

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
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = stdout.read(buffer)
            if (count == -1) {
                running = false
                if (result.size() > 0) {
                    return String(result.toByteArray(), StandardCharsets.UTF_8)
                }
                throw IOException("R2 process terminated unexpectedly (EOF)")
            }
            val nullIndex = firstNullIndex(buffer, count)
            if (nullIndex >= 0) {
                result.write(buffer, 0, nullIndex)
                break
            }
            result.write(buffer, 0, count)
        }
        return String(result.toByteArray(), StandardCharsets.UTF_8)
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
            return R2Pipe(
                process = process,
                stdout = BufferedInputStream(process.inputStream, BUFFER_SIZE),
                stdin = process.outputStream,
                logger = logger,
                processKiller = processKiller
            )
        }

        private const val BUFFER_SIZE = 64 * 1024

        private fun firstNullIndex(buffer: ByteArray, count: Int): Int {
            for (index in 0 until count) {
                if (buffer[index].toInt() == 0) {
                    return index
                }
            }
            return -1
        }
    }
}

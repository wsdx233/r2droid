package top.wsdx233.r2droid.testing

import java.io.BufferedInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.StandardCharsets

/**
 * Lightweight r2pipe client that talks to a host radare2 process.
 * No Android dependency — usable in plain JUnit tests.
 *
 * Protocol:  send  "command\n"   →  receive  "output\0"
 * This is the same -q0 protocol used by the app's Kotlin r2pipe integration.
 */
class HostR2Pipe(
    private val process: Process,
    private val verbose: Boolean = false
) {
    private val inputStream = BufferedInputStream(process.inputStream, 65536)
    private val outputStream: OutputStream = process.outputStream
    private val inputChannel: ReadableByteChannel = Channels.newChannel(inputStream)
    private val outputChannel: WritableByteChannel = Channels.newChannel(outputStream)
    private val writeBuffer = ByteBuffer.allocateDirect(16 * 1024)
    private var resultBuffer = ByteBuffer.allocateDirect(512 * 1024)
    private val readBuffer = ByteBuffer.allocateDirect(256 * 1024)

    fun cmd(command: String): String {
        if (verbose) println("[R2] >> $command")
        flushInput()
        writeCommand(command)
        val result = readResult()
        if (verbose) println("[R2] << ${result.take(200)}${if (result.length > 200) "..." else ""}")
        return result
    }

    fun cmdj(command: String): String {
        val jsonCmd = if (command.endsWith("j")) command else "${command}j"
        return cmd(jsonCmd)
    }

    fun quit() {
        try {
            writeCommand("q")
            Thread.sleep(100)
        } catch (_: Exception) {
        }
        try {
            process.destroy()
        } catch (_: Exception) {
        }
    }

    private fun flushInput() {
        try {
            while (inputStream.available() > 0) inputStream.read()
        } catch (_: Exception) {
        }
    }

    private fun writeCommand(command: String) {
        writeBuffer.clear()
        writeBuffer.put(command.toByteArray(Charsets.UTF_8))
        writeBuffer.put(10.toByte()) // \n
        writeBuffer.flip()
        while (writeBuffer.hasRemaining()) outputChannel.write(writeBuffer)
        outputStream.flush()
    }

    private fun readResult(): String {
        resultBuffer.clear()
        while (true) {
            readBuffer.clear()
            val bytesRead = inputChannel.read(readBuffer)
            if (bytesRead == -1) break
            readBuffer.flip()
            val nullIndex = findFirstNull(readBuffer)
            if (nullIndex != -1) {
                ensureCapacity(resultBuffer.position() + nullIndex)
                readBuffer.limit(nullIndex)
                resultBuffer.put(readBuffer)
                break
            } else {
                ensureCapacity(resultBuffer.position() + readBuffer.remaining())
                resultBuffer.put(readBuffer)
            }
        }
        resultBuffer.flip()
        val len = resultBuffer.remaining()
        if (len == 0) return ""
        val bytes = ByteArray(len)
        resultBuffer.get(bytes)
        return String(bytes, StandardCharsets.UTF_8).trim()
    }

    private fun findFirstNull(buffer: ByteBuffer): Int {
        for (i in 0 until buffer.limit()) {
            if (buffer.get(i) == 0.toByte()) return i
        }
        return -1
    }

    private fun ensureCapacity(needed: Int) {
        if (needed > resultBuffer.capacity()) {
            val newSize = minOf(4 * 1024 * 1024, (resultBuffer.capacity() * 2).coerceAtLeast(needed))
            val newBuffer = ByteBuffer.allocateDirect(newSize)
            resultBuffer.flip()
            newBuffer.put(resultBuffer)
            resultBuffer = newBuffer
        }
    }

    companion object {
        /**
         * Open an r2 session on the given binary file using the host radare2.
         * @param binaryPath  Path to the binary to analyze
         * @param r2Path      Path to the radare2 executable (default: "radare2")
         * @param extraArgs   Additional r2 launch flags (e.g. "-e bin.relocs.apply=true")
         * @param verbose     Print r2 commands and responses
         */
        fun open(
            binaryPath: String,
            r2Path: String = "radare2",
            extraArgs: List<String> = emptyList(),
            verbose: Boolean = false
        ): HostR2Pipe {
            val args = mutableListOf(r2Path, "-q0", "-e", "scr.color=false")
            args.addAll(extraArgs)
            args.add(binaryPath)
            val pb = ProcessBuilder(args)
            pb.redirectErrorStream(false)
            val process = pb.start()

            // Drain stderr in background to prevent buffer deadlock
            Thread {
                try {
                    process.errorStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (verbose) println("[R2-ERR] $line")
                        }
                    }
                } catch (_: Exception) {
                }
            }.start()

            val pipe = HostR2Pipe(process, verbose)
            // Consume any initial greeting / leftover bytes
            pipe.flushInput()
            return pipe
        }
    }
}

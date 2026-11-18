package org.radare.r2pipe.kotlin

import java.io.InputStream

interface R2PipeSession : AutoCloseable {
    fun cmd(command: String): String

    /** Executes a JSON-producing r2 command unchanged; returns its raw text without parsing. */
    fun cmdj(command: String): String = cmd(command)

    /**
     * Streams the command response. With [R2Pipe], NUL terminates both streamed and buffered
     * responses; request hex text with `p8` rather than raw `pr` output for arbitrary bytes.
     */
    fun cmdStream(command: String): InputStream

    fun interrupt()

    fun forceClose()

    fun isRunning(): Boolean

    override fun close()
}

internal fun requireNulFreeCommand(command: String) {
    require('\u0000' !in command) { "R2 commands must not contain NUL bytes" }
}

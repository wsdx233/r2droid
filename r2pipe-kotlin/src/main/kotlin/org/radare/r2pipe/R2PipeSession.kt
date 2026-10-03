package org.radare.r2pipe

import java.io.InputStream

interface R2PipeSession : AutoCloseable {
    fun cmd(command: String): String

    /** Executes a JSON-producing r2 command unchanged; returns its raw text without parsing. */
    fun cmdj(command: String): String = cmd(command)

    fun cmdStream(command: String): InputStream

    fun interrupt()

    fun forceClose()

    fun isRunning(): Boolean

    override fun close()
}

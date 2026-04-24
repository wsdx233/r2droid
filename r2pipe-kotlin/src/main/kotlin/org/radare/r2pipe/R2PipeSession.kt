package org.radare.r2pipe

import java.io.InputStream

interface R2PipeSession : AutoCloseable {
    fun cmd(command: String): String

    fun cmdj(command: String): String = cmd(if (command.endsWith("j")) command else "${command}j")

    fun cmdStream(command: String): InputStream

    fun interrupt()

    fun forceClose()

    fun isRunning(): Boolean

    override fun close()
}

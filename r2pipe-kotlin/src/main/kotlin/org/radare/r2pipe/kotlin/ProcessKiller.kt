package org.radare.r2pipe.kotlin

interface ProcessKiller {
    fun interrupt(process: Process) {}

    /**
     * The default stops only [process]. If a launcher keeps r2 in a child process,
     * its termination strategy must also stop the owned descendants that hold the pipe open.
     */
    fun terminate(process: Process, force: Boolean) {
        try {
            if (force) {
                process.destroyForcibly()
            } else {
                process.destroy()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        @JvmField
        val DEFAULT: ProcessKiller = object : ProcessKiller {}
    }
}

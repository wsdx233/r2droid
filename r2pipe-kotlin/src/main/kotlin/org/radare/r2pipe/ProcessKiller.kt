package org.radare.r2pipe

interface ProcessKiller {
    fun interrupt(process: Process) {}

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

package org.radare.r2pipe

enum class R2PipeLogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

fun interface R2PipeLogger {
    fun log(level: R2PipeLogLevel, message: String)
}

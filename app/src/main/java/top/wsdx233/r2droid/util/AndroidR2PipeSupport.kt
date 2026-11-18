package top.wsdx233.r2droid.util

import android.content.Context
import android.util.Log
import org.radare.r2pipe.kotlin.LaunchSpec
import org.radare.r2pipe.kotlin.ProcessKiller
import org.radare.r2pipe.kotlin.R2Pipe
import org.radare.r2pipe.kotlin.R2PipeHttp
import org.radare.r2pipe.kotlin.R2PipeLogLevel
import org.radare.r2pipe.kotlin.R2PipeLogger

internal object AndroidR2PipeSupport {
    val processKiller: ProcessKiller = object : ProcessKiller {
        override fun interrupt(process: Process) {
            val pid = getProcessId(process)
            if (pid <= 0) return
            runShellCommand(
                "kill -2 -$pid 2>/dev/null; kill -2 ${'$'}(pgrep -P $pid) 2>/dev/null"
            )
            LogManager.log(LogType.INFO, "Sent SIGINT to R2 process group (pid=$pid)")
        }

        override fun terminate(process: Process, force: Boolean) {
            val pid = getProcessId(process)
            if (pid > 0) {
                val signal = if (force) 9 else 15
                runShellCommand(
                    "kill -$signal -$pid 2>/dev/null; kill -$signal ${'$'}(pgrep -P $pid) 2>/dev/null"
                )
            }
            try {
                if (force) {
                    process.destroyForcibly()
                } else {
                    process.destroy()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun toCoreLaunchSpec(spec: R2Runtime.LaunchSpec): LaunchSpec {
        return LaunchSpec(
            command = spec.command,
            workingDirectory = spec.workingDirectory,
            environment = spec.environment
        )
    }

    fun openStdio(
        context: Context,
        filePath: String? = null,
        flags: String = "",
        rawArgs: String? = null,
        logger: R2PipeLogger
    ): R2Pipe {
        return R2Pipe.open(
            launchSpec = toCoreLaunchSpec(
                R2Runtime.buildStdioLaunch(context.applicationContext, filePath, flags, rawArgs)
            ),
            logger = logger,
            processKiller = processKiller
        )
    }

    fun openHttp(
        context: Context,
        filePath: String? = null,
        flags: String = "",
        rawArgs: String? = null,
        port: Int,
        logger: R2PipeLogger
    ): R2PipeHttp {
        return R2PipeHttp.spawn(
            launchSpec = toCoreLaunchSpec(
                R2Runtime.buildHttpLaunch(context.applicationContext, filePath, flags, rawArgs, port)
            ),
            port = port,
            logger = logger,
            processKiller = processKiller
        )
    }

    fun logger(tag: String, suppressKnownR2FridaWarnings: Boolean = false): R2PipeLogger {
        return R2PipeLogger { level, message ->
            if (suppressKnownR2FridaWarnings && shouldSuppressR2FridaRegisterWarning(message)) {
                Log.d(tag, "Suppressed r2frida register warning: $message")
                return@R2PipeLogger
            }

            when (level) {
                R2PipeLogLevel.DEBUG -> Log.d(tag, message)
                R2PipeLogLevel.INFO -> Log.i(tag, message)
                R2PipeLogLevel.WARNING -> Log.w(tag, message)
                R2PipeLogLevel.ERROR -> Log.e(tag, message)
            }

            val logType = when (level) {
                R2PipeLogLevel.DEBUG, R2PipeLogLevel.INFO -> LogType.INFO
                R2PipeLogLevel.WARNING -> LogType.WARNING
                R2PipeLogLevel.ERROR -> LogType.ERROR
            }
            LogManager.log(logType, message)
        }
    }

    private fun shouldSuppressR2FridaRegisterWarning(message: String): Boolean {
        val trimmed = message.trim()
        if (trimmed == "ERROR: Register not found") return true
        if (!trimmed.startsWith("ERROR: ar: Unknown register ")) return false
        val name = trimmed.substringAfter("'").substringBefore("'")
        return name in setOf(
            "sb", "sl", "fp", "ip", "lr", "sp", "pc", "cpsr", "apsr", "xpsr",
            "r0", "r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15"
        ) || Regex("[wx]\\d+").matches(name)
    }

    private fun runShellCommand(command: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", command))
        } catch (e: Exception) {
            LogManager.log(LogType.WARNING, "Failed to run shell command: ${e.message}")
        }
    }

    private fun getProcessId(process: Process): Int {
        return try {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        } catch (_: Exception) {
            -1
        }
    }
}

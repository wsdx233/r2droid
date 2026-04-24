package top.wsdx233.r2droid.util

import android.content.Context
import android.util.Log
import org.radare.r2pipe.LaunchSpec
import org.radare.r2pipe.ProcessKiller
import org.radare.r2pipe.R2Pipe
import org.radare.r2pipe.R2PipeHttp
import org.radare.r2pipe.R2PipeLogLevel
import org.radare.r2pipe.R2PipeLogger

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
        logTag: String = "R2Pipe"
    ): R2Pipe {
        return R2Pipe.open(
            launchSpec = toCoreLaunchSpec(
                R2Runtime.buildStdioLaunch(context.applicationContext, filePath, flags, rawArgs)
            ),
            logger = logger(logTag),
            processKiller = processKiller
        )
    }

    fun openHttp(
        context: Context,
        filePath: String? = null,
        flags: String = "",
        rawArgs: String? = null,
        port: Int,
        logTag: String = "R2PipeHttp"
    ): R2PipeHttp {
        return R2PipeHttp.spawn(
            launchSpec = toCoreLaunchSpec(
                R2Runtime.buildHttpLaunch(context.applicationContext, filePath, flags, rawArgs, port)
            ),
            port = port,
            logger = logger(logTag),
            processKiller = processKiller
        )
    }

    fun logger(tag: String): R2PipeLogger {
        return R2PipeLogger { level, message ->
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

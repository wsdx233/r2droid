package top.wsdx233.r2droid.feature.debug.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

open class NativeR2DebuggerBackend @Inject constructor(
    @ApplicationContext private val context: Context?,
    runner: R2DebugCommandRunner
) : BaseR2DebuggerBackend(runner) {

    override open val type: DebugBackend = DebugBackend.NATIVE_GDB
    override val registerCommands: List<String> = listOf("drj", "arj")
    override val breakpointListCommand: String = "dbj"
    override val seekCommand: String = "s"

    override open suspend fun probe(): Result<DebugCapabilities> = Result.success(
        DebugCapabilities(
            canStepInto = true,
            canStepOver = true,
            canStepOut = true,
            canContinue = true,
            canPause = true,
            canEditRegisters = true,
            canReadMemory = true,
            canWriteMemory = true,
            canUseBreakpoints = true,
            canUseHardwareBreakpoints = true,
            canListThreads = true,
            canListFrames = true
        )
    )

    override open suspend fun start(config: DebugSessionConfig): Result<DebugStateSnapshot> = runCatching {
        val session = R2PipeManager.activeSession() ?: error("No active session.")
        check(!session.writeCacheEnabled) {
            context?.getString(R.string.write_cache_replace_blocked)
                ?: "Export or write pending patches, then reopen without write cache to debug."
        }
        val preparedTarget = config.executablePath?.let { prepareExecutableTarget(it) }
        val existingBreakpoints = listBreakpoints().getOrDefault(emptyList())

        if (preparedTarget?.requiresSessionReopen == true) {
            reopenDebugSession(session, preparedTarget.file, existingBreakpoints)
        } else if (config.attachPid != null) {
            runner.executeFirstSuccessful(session, "doo ${config.attachPid}", "ood ${config.attachPid}").getOrThrow()
        } else {
            val launchArgs = config.args.trim()
            val openCommand = if (launchArgs.isBlank()) "ood" else "ood $launchArgs"
            val oodResult = runner.executeFirstSuccessful(session, openCommand, "doo")
            if (oodResult.isFailure) {
                val targetFile = preparedTarget?.file
                    ?: config.executablePath?.let { File(it) }
                    ?: throw oodResult.exceptionOrNull()
                    ?: IllegalStateException("Native debug failed and no executable target is available")
                reopenDebugSession(session, targetFile, existingBreakpoints)
            }
        }

        refresh().getOrThrow().copy(
            runState = DebugRunState.SUSPENDED,
            stopReason = DebugStopReason(DebugStopReasonType.STARTED, "Native R2 debugger started")
        )
    }

    override open suspend fun stop(): Result<Unit> = runCatching {
        runner.executeFirstSuccessful("doc", "dk").getOrThrow()
        lastRegisters = emptyMap()
    }

    override suspend fun stepInto(): Result<DebugStateSnapshot> = runCatching {
        runner.execute("ds").getOrThrow()
        refresh().getOrThrow().copy(
            stopReason = DebugStopReason(DebugStopReasonType.STEP, "Step into")
        )
    }

    override suspend fun stepOver(): Result<DebugStateSnapshot> = runCatching {
        runner.execute("dso").getOrThrow()
        refresh().getOrThrow().copy(
            stopReason = DebugStopReason(DebugStopReasonType.STEP, "Step over")
        )
    }

    override suspend fun stepOut(): Result<DebugStateSnapshot> = runCatching {
        runner.execute("dsf").getOrThrow()
        refresh().getOrThrow().copy(
            stopReason = DebugStopReason(DebugStopReasonType.STEP, "Step out")
        )
    }

    override suspend fun continueRun(): Result<DebugStateSnapshot> = runCatching {
        val output = runner.execute("dc").getOrThrow()
        refresh().getOrThrow().copy(
            stopReason = parseNativeStopReason(output)
        )
    }

    override suspend fun pause(): Result<DebugStateSnapshot> = refresh().mapCatching {
        it.copy(stopReason = DebugStopReason(DebugStopReasonType.MANUAL_PAUSE, "Execution interrupted"))
    }

    override suspend fun setBreakpoint(breakpoint: DebugBreakpoint): Result<DebugBreakpoint> = runCatching {
        runner.execute("db ${breakpoint.address}").getOrThrow()
        breakpoint
    }

    override suspend fun removeBreakpoint(address: Long): Result<Unit> = runCatching {
        runner.execute("db- $address").getOrThrow()
    }

    override suspend fun writeRegister(name: String, value: Long): Result<Unit> = runCatching {
        runner.execute("dr $name=$value").getOrThrow()
    }

    private data class PreparedNativeTarget(
        val file: File,
        val requiresSessionReopen: Boolean
    )

    private suspend fun reopenDebugSession(
        session: R2PipeManager.R2Session,
        target: File,
        breakpointsToRestore: List<DebugBreakpoint>
    ) {
        val flags = withDebugFlag(session.launchFlags)
        val appContext = context ?: throw IllegalStateException("Android context is required to reopen a native debug session")
        val reopened = R2PipeManager.replaceActiveSession(
            context = appContext,
            filePath = target.absolutePath,
            flags = flags,
            expectedSession = session
        ).getOrThrow()
        breakpointsToRestore.forEach { bp ->
            runner.execute(reopened, "db ${bp.address}").getOrNull()
        }
    }

    private fun prepareExecutableTarget(path: String): PreparedNativeTarget {
        val file = File(path)
        require(file.exists() && file.isFile) { "Native debug target does not exist: $path" }
        val lower = file.name.lowercase()
        require(!lower.endsWith(".apk") && !lower.endsWith(".dex")) {
            "Native debug requires an executable binary. Use ESIL or Frida for APK/DEX targets."
        }

        if (!file.canExecute()) {
            file.setExecutable(true, true)
        }
        if (file.canExecute()) {
            return PreparedNativeTarget(file = file, requiresSessionReopen = false)
        }

        val copied = copyToPrivateExecutable(file)
        require(copied.canExecute()) {
            "Native debug target is not executable even after copying to app-private storage. Use ESIL or Frida."
        }
        return PreparedNativeTarget(file = copied, requiresSessionReopen = true)
    }

    private fun copyToPrivateExecutable(source: File): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.absolutePath.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        val appContext = context ?: throw IllegalStateException("Android context is required to copy native debug target")
        val dir = File(appContext.filesDir, "debug-targets/$digest").apply { mkdirs() }
        val target = File(dir, source.name.ifBlank { "debug-target" })
        source.copyTo(target, overwrite = true)
        target.setReadable(true, true)
        target.setWritable(true, true)
        target.setExecutable(true, true)
        return target
    }

    private fun withDebugFlag(flags: String): String {
        val parts = flags.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.contains("-d")) flags.trim() else (listOf("-d") + parts).joinToString(" ")
    }

    private fun parseNativeStopReason(output: String): DebugStopReason {
        val text = output.trim()
        val lower = text.lowercase()
        return when {
            lower.contains("breakpoint") || lower.contains("hit breakpoint") -> {
                DebugStopReason(DebugStopReasonType.BREAKPOINT, text.ifBlank { "Breakpoint hit" }.take(240))
            }
            lower.contains("signal") || lower.contains("sigsegv") || lower.contains("sigill") || lower.contains("sigabrt") -> {
                val signal = Regex("SIG[A-Z0-9]+", RegexOption.IGNORE_CASE).find(text)?.value?.uppercase()
                DebugStopReason(DebugStopReasonType.SIGNAL, text.ifBlank { "Stopped by signal" }.take(240), signal = signal)
            }
            lower.contains("exit") || lower.contains("exited") || lower.contains("terminated") -> {
                DebugStopReason(DebugStopReasonType.PROCESS_EXIT, text.ifBlank { "Process exited" }.take(240))
            }
            lower.contains("exception") || lower.contains("fault") || lower.contains("crash") -> {
                DebugStopReason(DebugStopReasonType.EXCEPTION, text.take(240))
            }
            else -> DebugStopReason(DebugStopReasonType.UNKNOWN, text.ifBlank { "Execution stopped" }.take(240))
        }
    }
}

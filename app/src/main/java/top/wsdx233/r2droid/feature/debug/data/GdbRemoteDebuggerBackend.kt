package top.wsdx233.r2droid.feature.debug.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager
import javax.inject.Inject

class GdbRemoteDebuggerBackend @Inject constructor(
    @ApplicationContext private val context: Context?,
    runner: R2DebugCommandRunner
) : NativeR2DebuggerBackend(context, runner) {

    override val type: DebugBackend = DebugBackend.GDB_REMOTE

    override suspend fun probe(): Result<DebugCapabilities> = Result.success(
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
            canUseHardwareBreakpoints = false,
            canUseWatchpoints = true,
            canUseConditionalBreakpoints = true,
            canListThreads = true,
            canListFrames = true
        )
    )

    override suspend fun start(config: DebugSessionConfig): Result<DebugStateSnapshot> = runCatching {
        val host = config.gdbRemoteHost?.trim().orEmpty()
        val port = config.gdbRemotePort
        require(host.isNotBlank()) { "GDB remote host is required" }
        require(port != null && port in 1..65535) { "GDB remote port must be between 1 and 65535" }
        val uri = "gdb://$host:$port"
        val appContext = context ?: throw IllegalStateException("Android context is required to open GDB remote session")

        R2PipeManager.replaceActiveSession(
            context = appContext,
            filePath = uri,
            flags = "-d",
            expectedSession = R2PipeManager.activeSession() ?: error("No active session.")
        ).getOrThrow()

        refresh().getOrThrow().copy(
            backend = DebugBackend.GDB_REMOTE,
            runState = DebugRunState.SUSPENDED,
            stopReason = DebugStopReason(DebugStopReasonType.STARTED, "GDB remote connected: $uri")
        )
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        val session = R2PipeManager.activeSession() ?: error("No active session.")
        check(!session.writeCacheEnabled) {
            context?.getString(R.string.write_cache_replace_blocked)
                ?: "Export or write pending patches before replacing the backend."
        }
        runner.executeFirstSuccessful(session, "doc", "dk", "q").getOrThrow()
        lastRegisters = emptyMap()
    }
}

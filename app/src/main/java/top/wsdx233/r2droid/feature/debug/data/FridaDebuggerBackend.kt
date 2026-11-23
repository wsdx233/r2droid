package top.wsdx233.r2droid.feature.debug.data

import top.wsdx233.r2droid.util.R2PipeManager
import javax.inject.Inject

class FridaDebuggerBackend @Inject constructor(
    runner: R2DebugCommandRunner
) : BaseR2DebuggerBackend(runner) {

    override val type: DebugBackend = DebugBackend.FRIDA
    override val registerCommands: List<String> = listOf("drj", "dr.", "dr")
    override val breakpointListCommand: String = "dbj"
    override val seekCommand: String = "s"

    private fun requireFridaSession(): Result<Unit> = if (R2PipeManager.isR2FridaSession) {
        Result.success(Unit)
    } else {
        Result.failure(IllegalStateException(R2DebugCommandRunner.FRIDA_SESSION_REQUIRED_MESSAGE))
    }

    override suspend fun probe(): Result<DebugCapabilities> = runCatching {
        requireFridaSession().getOrThrow()
        val canReadRegisters = runner.executeFrida("drj").isSuccess
        val canListBreakpoints = runner.executeFrida("dbj").isSuccess
        val canReadMaps = runner.executeFrida("dmj").isSuccess
        val canContinue = runner.executeFrida("dc").isSuccess
        DebugCapabilities(
            canStepInto = false,
            canStepOver = false,
            canStepOut = false,
            canContinue = canContinue,
            canPause = true,
            canEditRegisters = false,
            canReadMemory = canReadMaps,
            canWriteMemory = false,
            canUseBreakpoints = canListBreakpoints,
            canListThreads = false,
            canListFrames = false
        ).also {
            if (!canReadRegisters) {
                throw IllegalStateException("r2frida register command :drj is not available")
            }
        }
    }

    override suspend fun start(config: DebugSessionConfig): Result<DebugStateSnapshot> = runCatching {
        requireFridaSession().getOrThrow()
        probe().getOrThrow()
        refresh().getOrThrow().copy(
            runState = DebugRunState.SUSPENDED,
            stopReason = DebugStopReason(DebugStopReasonType.STARTED, "Frida debug controls attached")
        )
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        requireFridaSession().getOrThrow()
        lastRegisters = emptyMap()
    }

    override suspend fun stepInto(): Result<DebugStateSnapshot> = Result.failure(
        UnsupportedOperationException("r2frida does not provide :ds single-step; use breakpoints/:dc or Native R2/GDB for stepping")
    )

    override suspend fun stepOver(): Result<DebugStateSnapshot> = Result.failure(
        UnsupportedOperationException("r2frida does not provide :dso step-over; use breakpoints/:dc or Native R2/GDB for stepping")
    )

    override suspend fun continueRun(): Result<DebugStateSnapshot> = runCatching {
        requireFridaSession().getOrThrow()
        runner.executeFrida("dc").getOrThrow()
        refresh().getOrThrow().copy(
            stopReason = DebugStopReason(DebugStopReasonType.UNKNOWN, "Frida execution stopped")
        )
    }

    override suspend fun pause(): Result<DebugStateSnapshot> = runCatching {
        requireFridaSession().getOrThrow()
        refresh().getOrThrow().copy(
            stopReason = DebugStopReason(DebugStopReasonType.MANUAL_PAUSE, "Frida execution interrupted")
        )
    }

    override suspend fun setBreakpoint(breakpoint: DebugBreakpoint): Result<DebugBreakpoint> = runCatching {
        requireFridaSession().getOrThrow()
        val address = "0x${breakpoint.address.toString(16)}"
        runner.executeFrida("db $address").getOrThrow()
        breakpoint
    }

    override suspend fun removeBreakpoint(address: Long): Result<Unit> = runCatching {
        requireFridaSession().getOrThrow()
        runner.executeFrida("db- ${formatHexAddress(address)}").getOrThrow()
    }

    override suspend fun writeMemory(address: Long, bytes: ByteArray): Result<Unit> = Result.failure(
        UnsupportedOperationException("Memory writing is not supported by ${type.displayName}")
    )
}

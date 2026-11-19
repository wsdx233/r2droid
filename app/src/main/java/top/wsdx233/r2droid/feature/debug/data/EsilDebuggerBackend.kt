package top.wsdx233.r2droid.feature.debug.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.radare.r2pipe.kotlin.R2PipeSession
import top.wsdx233.r2droid.util.R2PipeManager
import javax.inject.Inject

class EsilDebuggerBackend @Inject constructor(
    runner: R2DebugCommandRunner
) : BaseR2DebuggerBackend(runner) {
    override val type = DebugBackend.ESIL
    override val registerCommands = listOf("aerj")
    override val breakpointListCommand = "dbj"
    override val seekCommand = "s"

    private val operations = Mutex()
    private val execution = EsilExecution(runner)
    @Volatile private var session: EsilSession? = null
    private var lastSnapshot: DebugStateSnapshot? = null

    override suspend fun probe(): Result<DebugCapabilities> = Result.success(
        DebugCapabilities(canEditRegisters = true, canTrace = true)
    )

    private suspend fun <T> onCurrentPipe(block: suspend (R2PipeSession) -> T): Result<T> {
        val pipe = R2PipeManager.activeSessionPipe ?: return Result.failure(IllegalStateException("No active r2 session"))
        return try {
            Result.success(operations.withLock {
                check(R2PipeManager.activeSessionPipe === pipe) { "The active r2 session changed" }
                block(pipe)
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun owner(): EsilSession = session?.takeIf { it.lease.pipe === R2PipeManager.activeSessionPipe }
        ?: error("ESIL is not active in this session")

    override suspend fun query(command: String): Result<String> = runCatching {
        owner().lease.command(command).stdout.also { runner.validateCommandOutput(command, it) }
    }

    override fun registerRole(name: String): RegisterRole = checkNotNull(session).role(name)

    private suspend fun snapshot(reason: DebugStopReason): DebugStateSnapshot {
        val vm = owner()
        val regs = super.readRegisters().getOrThrow()
        val pc = lastRegisters[vm.pcName] ?: error("ESIL register ${vm.pcName} (PC) is unavailable")
        check(vm.spName in lastRegisters) { "ESIL register ${vm.spName} (SP) is unavailable" }
        return DebugStateSnapshot(
            backend = type, runState = DebugRunState.SUSPENDED, pc = pc, registers = regs,
            breakpoints = super.listBreakpoints().getOrThrow(), stopReason = reason
        ).also { lastSnapshot = it }
    }

    private suspend fun closeOwnedSession() {
        session?.close()
        session = null
        lastSnapshot = null
        lastRegisters = emptyMap()
    }

    private suspend fun initialize(config: DebugSessionConfig, reset: Boolean) = onCurrentPipe { pipe ->
        val previous = session?.takeIf { it.lease.pipe === pipe }
        val effective = if (reset && config.startAddress == null && previous != null) {
            config.copy(startAddress = previous.startAddress)
        } else config
        closeOwnedSession()
        val vm = EsilSession(R2PipeManager.acquireEsil(), effective, runner)
        session = vm
        try {
            check(vm.lease.pipe === pipe) { "The active r2 session changed" }
            vm.initialize()
            snapshot(DebugStopReason(DebugStopReasonType.STARTED, if (reset) "ESIL reset" else "ESIL initialized", address = vm.startAddress))
        } catch (failure: Throwable) {
            try { closeOwnedSession() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    override suspend fun start(config: DebugSessionConfig) = initialize(config, reset = false)
    override suspend fun reset(config: DebugSessionConfig) = initialize(config, reset = true)

    suspend fun stopForPipe(pipe: R2PipeSession) {
        if (session?.lease?.pipe === pipe) requestPause()
        operations.withLock { if (session?.lease?.pipe === pipe) closeOwnedSession() }
    }

    override suspend fun stop(): Result<Unit> = onCurrentPipe { pipe ->
        if (session?.lease?.pipe === pipe) closeOwnedSession()
    }

    override suspend fun refresh() = onCurrentPipe {
        snapshot(lastSnapshot?.stopReason ?: DebugStopReason())
    }

    private suspend fun step(over: Boolean) = onCurrentPipe {
        val vm = owner()
        snapshot(execution.step(over) { vm.lease.command(it, diagnostics = true) })
    }

    override suspend fun stepInto() = step(over = false)
    override suspend fun stepOver() = step(over = true)

    private suspend fun resume(cursor: Long?) = onCurrentPipe {
        val vm = owner()
        try {
            val reason = execution.resume(vm.config, { vm.lease.command(it, diagnostics = true) }, cursor)
            snapshot(reason)
        } catch (failure: Throwable) {
            val cleanup = failure as? EsilExecution.BreakpointCleanupException
                ?: failure.suppressed.filterIsInstance<EsilExecution.BreakpointCleanupException>().firstOrNull()
            if (cleanup != null) vm.pendingBreakpointRestore = cleanup.restoreCommand
            throw failure
        }
    }

    override suspend fun continueRun() = resume(null)
    suspend fun runToCursor(address: Long) = resume(address)
    fun requestPause() = execution.requestPause()

    override suspend fun pause(): Result<DebugStateSnapshot> {
        requestPause()
        return onCurrentPipe { owner(); checkNotNull(lastSnapshot) }
    }

    suspend fun setPc(address: Long) = onCurrentPipe {
        owner().setPc(address)
        snapshot(DebugStopReason(DebugStopReasonType.STEP, "PC set", address = address))
    }

    override suspend fun readRegisters() = onCurrentPipe { owner(); super.readRegisters().getOrThrow() }
    override suspend fun readMemory(address: Long, size: Int) = onCurrentPipe { owner(); super.readMemory(address, size).getOrThrow() }
    override suspend fun readStack(count: Int) = onCurrentPipe {
        val vm = owner()
        val sp = lastRegisters[vm.spName] ?: error("ESIL stack pointer unavailable")
        readStackAt(sp, count, vm.wordBytes)
    }

    // Gutter breakpoints are also usable before the debug panel starts ESIL.
    private suspend fun breakpointCommand(pipe: R2PipeSession, cmd: String): String {
        session?.takeIf { it.lease.pipe === pipe }?.let { return it.checked(cmd) }
        val target = R2PipeManager.activeSession()?.takeIf { it.pipe === pipe } ?: error("The active r2 session changed")
        return R2PipeManager.execute(target, cmd, markDirty = false, includeStderr = true).getOrThrow().also {
            runner.validateCommandOutput(cmd, it)
        }
    }

    override suspend fun listBreakpoints() = onCurrentPipe { runner.parseBreakpoints(breakpointCommand(it, "dbj")) }
    override suspend fun setBreakpoint(breakpoint: DebugBreakpoint) = onCurrentPipe {
        breakpointCommand(it, "db ${breakpoint.address}")
        breakpoint
    }

    override suspend fun removeBreakpoint(address: Long): Result<Unit> = onCurrentPipe { breakpointCommand(it, "db- $address"); Unit }
    override suspend fun writeRegister(name: String, value: Long): Result<Unit> = onCurrentPipe {
        val vm = owner()
        require(name in lastRegisters && name.matches(registerName)) { "Unknown ESIL register: $name" }
        vm.checked("aer $name=$value")
        lastRegisters = lastRegisters + (name to value)
    }

    private companion object { val registerName = Regex("[A-Za-z_][A-Za-z0-9_.]*") }
}

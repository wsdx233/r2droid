package top.wsdx233.r2droid.feature.debug.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.radare.r2pipe.kotlin.R2PipeSession
import top.wsdx233.r2droid.util.LogManager
import top.wsdx233.r2droid.util.LogType
import top.wsdx233.r2droid.util.R2PipeManager
import java.util.IdentityHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebuggerRepository @Inject constructor(
    private val runner: R2DebugCommandRunner,
    private val esilBackend: EsilDebuggerBackend,
    private val nativeBackend: NativeR2DebuggerBackend,
    private val fridaBackend: FridaDebuggerBackend,
    private val gdbRemoteBackend: GdbRemoteDebuggerBackend
) {
    // Confined to Main, like the UI that owns the debug session. Cleanup must outlive its VM.
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val esilCleanups = IdentityHashMap<R2PipeSession, Job>()

    private fun backendFor(type: DebugBackend): DebuggerBackend = when (type) {
        DebugBackend.ESIL -> esilBackend
        DebugBackend.NATIVE_GDB -> nativeBackend
        DebugBackend.FRIDA -> fridaBackend
        DebugBackend.GDB_REMOTE -> gdbRemoteBackend
    }

    private fun validateBackendSession(type: DebugBackend): Result<Unit> {
        if (type == DebugBackend.FRIDA && !R2PipeManager.isR2FridaSession) {
            return Result.failure(IllegalStateException(R2DebugCommandRunner.FRIDA_SESSION_REQUIRED_MESSAGE))
        }
        if (type != DebugBackend.FRIDA && R2PipeManager.isR2FridaSession) {
            return Result.failure(
                IllegalStateException("${type.displayName} debug backend is not available in an r2frida session; use Frida backend")
            )
        }
        return Result.success(Unit)
    }

    private inline fun <T> withBackend(type: DebugBackend, operation: (DebuggerBackend) -> Result<T>): Result<T> {
        validateBackendSession(type).getOrElse { return Result.failure(it) }
        return operation(backendFor(type))
    }

    suspend fun probe(backend: DebugBackend) = withBackend(backend) { it.probe() }

    suspend fun start(config: DebugSessionConfig) = withBackend(config.backend) { it.start(config) }

    suspend fun reset(config: DebugSessionConfig) = withBackend(config.backend) { it.reset(config) }

    suspend fun stop(backend: DebugBackend) = withBackend(backend) { it.stop() }

    suspend fun stepInto(backend: DebugBackend) = withBackend(backend) { it.stepInto() }

    suspend fun stepOver(backend: DebugBackend) = withBackend(backend) { it.stepOver() }

    suspend fun stepOut(backend: DebugBackend) = withBackend(backend) { it.stepOut() }

    suspend fun continueRun(backend: DebugBackend) = withBackend(backend) { it.continueRun() }

    suspend fun pause(backend: DebugBackend) = withBackend(backend) { it.pause() }

    fun requestEsilPause() = esilBackend.requestPause()

    fun releaseEsil(pipe: R2PipeSession) {
        if (pipe in esilCleanups) return
        val cleanup = cleanupScope.launch(start = CoroutineStart.LAZY) {
            try {
                esilBackend.stopForPipe(pipe)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogManager.log(LogType.WARNING, "Unable to clean up ESIL: ${error.message}")
            } finally {
                esilCleanups.remove(pipe)
            }
        }
        esilCleanups[pipe] = cleanup
        cleanup.start()
    }

    suspend fun awaitEsilCleanup(pipe: R2PipeSession) {
        esilCleanups[pipe]?.join()
    }

    suspend fun toggleBreakpoint(backend: DebugBackend, address: Long, add: Boolean): Result<Unit> =
        withBackend(backend) {
            if (add) it.setBreakpoint(DebugBreakpoint(address = address)).map { Unit }
            else it.removeBreakpoint(address)
        }

    suspend fun addBreakpoint(backend: DebugBackend, breakpoint: DebugBreakpoint): Result<List<DebugBreakpoint>> =
        withBackend(backend) { target ->
            runCatching {
                target.setBreakpoint(breakpoint).getOrThrow()
                target.listBreakpoints().getOrDefault(listOf(breakpoint))
            }
        }

    suspend fun removeBreakpoint(backend: DebugBackend, address: Long): Result<List<DebugBreakpoint>> =
        withBackend(backend) { target ->
            runCatching {
                target.removeBreakpoint(address).getOrThrow()
                target.listBreakpoints().getOrDefault(emptyList())
            }
        }

    suspend fun setBreakpointEnabled(
        backend: DebugBackend,
        address: Long,
        enabled: Boolean
    ): Result<List<DebugBreakpoint>> = withBackend(backend) { target ->
        runCatching {
            target.setBreakpointEnabled(address, enabled).getOrThrow()
            target.listBreakpoints().getOrThrow()
        }
    }

    suspend fun setBreakpointCondition(
        backend: DebugBackend,
        address: Long,
        condition: String?
    ): Result<List<DebugBreakpoint>> = withBackend(backend) { target ->
        runCatching {
            target.setBreakpointCondition(address, condition).getOrThrow()
            target.listBreakpoints().getOrThrow()
        }
    }

    suspend fun listBreakpoints(backend: DebugBackend) = withBackend(backend) { it.listBreakpoints() }

    suspend fun writeRegister(backend: DebugBackend, name: String, value: Long): Result<DebugStateSnapshot> =
        withBackend(backend) { target ->
            runCatching {
                val pipe = R2PipeManager.activeSessionPipe
                target.writeRegister(name, value).getOrThrow()
                check(pipe === R2PipeManager.activeSessionPipe) { "Debug session changed" }
                target.refresh().getOrThrow()
            }
        }

    suspend fun runToCursor(backend: DebugBackend, address: Long): Result<DebugStateSnapshot> =
        withBackend(backend) { target ->
            if (backend == DebugBackend.ESIL) {
                esilBackend.runToCursor(address)
            } else runCatching {
                val pipe = R2PipeManager.activeSessionPipe
                target.setBreakpoint(DebugBreakpoint(address = address, temporary = true)).getOrThrow()
                check(pipe === R2PipeManager.activeSessionPipe) { "Debug session changed" }
                val result = target.continueRun()
                val removed = pipe === R2PipeManager.activeSessionPipe && target.removeBreakpoint(address).isSuccess
                val snapshot = result.getOrThrow()
                if (removed) snapshot.copy(breakpoints = snapshot.breakpoints.filterNot { it.address == address })
                else snapshot
            }
        }

    suspend fun setProgramCounter(backend: DebugBackend, address: Long): Result<DebugStateSnapshot> =
        withBackend(backend) { target ->
            if (backend == DebugBackend.ESIL) {
                esilBackend.setPc(address)
            } else runCatching {
                val pipe = R2PipeManager.activeSessionPipe
                val registers = target.readRegisters().getOrThrow()
                val pcName = registers.firstOrNull { it.role == RegisterRole.PC }?.name ?: "pc"
                check(pipe === R2PipeManager.activeSessionPipe) { "Debug session changed" }
                target.writeRegister(pcName, address).getOrThrow()
                check(pipe === R2PipeManager.activeSessionPipe) { "Debug session changed" }
                runner.executeForBackend(backend, "s $address").getOrThrow()
                check(pipe === R2PipeManager.activeSessionPipe) { "Debug session changed" }
                target.refresh().getOrThrow()
            }
        }

    suspend fun readStack(backend: DebugBackend, count: Int = 16) = withBackend(backend) { it.readStack(count) }

    suspend fun readMemory(backend: DebugBackend, address: Long, size: Int) =
        withBackend(backend) { it.readMemory(address, size) }

    suspend fun writeMemory(backend: DebugBackend, address: Long, bytes: ByteArray) =
        withBackend(backend) { it.writeMemory(address, bytes) }

    suspend fun resolveExpression(backend: DebugBackend, expression: String) =
        withBackend(backend) { it.resolveExpression(expression) }

    suspend fun listFrames(backend: DebugBackend) = withBackend(backend) { it.listFrames() }

    suspend fun listThreads(backend: DebugBackend) = withBackend(backend) { it.listThreads() }

    suspend fun selectThread(backend: DebugBackend, threadId: String) =
        withBackend(backend) { it.selectThread(threadId) }
}

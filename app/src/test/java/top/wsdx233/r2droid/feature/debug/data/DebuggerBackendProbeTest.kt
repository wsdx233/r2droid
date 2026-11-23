package top.wsdx233.r2droid.feature.debug.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebuggerBackendProbeTest {
    @Test
    fun esilProbe_reportsSimulationCapabilities() = runTest {
        val capabilities = EsilDebuggerBackend(R2DebugCommandRunner()).probe().getOrThrow()

        assertTrue(capabilities.canStepInto)
        assertTrue(capabilities.canStepOver)
        assertTrue(capabilities.canContinue)
        assertTrue(capabilities.canPause)
        assertTrue(capabilities.canEditRegisters)
        assertTrue(capabilities.canReadMemory)
        assertTrue(capabilities.canWriteMemory)
        assertTrue(capabilities.canUseBreakpoints)
        assertTrue(capabilities.canUseConditionalBreakpoints)
        assertTrue(capabilities.canTrace)
        assertFalse(capabilities.canStepOut)
        assertFalse(capabilities.canUseWatchpoints)
    }

    @Test
    fun nativeProbe_reportsNativeDebuggerCapabilities() = runTest {
        val capabilities = NativeR2DebuggerBackend(null, R2DebugCommandRunner())
            .probe()
            .getOrThrow()

        assertNativeLikeCapabilities(capabilities, hardwareBreakpoints = true)
    }

    @Test
    fun gdbRemoteProbe_reportsRemoteCapabilities() = runTest {
        val capabilities = GdbRemoteDebuggerBackend(null, R2DebugCommandRunner())
            .probe()
            .getOrThrow()

        assertNativeLikeCapabilities(capabilities, hardwareBreakpoints = false)
    }

    private fun assertNativeLikeCapabilities(capabilities: DebugCapabilities, hardwareBreakpoints: Boolean) {
        assertTrue(capabilities.canStepInto)
        assertTrue(capabilities.canStepOver)
        assertTrue(capabilities.canStepOut)
        assertTrue(capabilities.canContinue)
        assertTrue(capabilities.canPause)
        assertTrue(capabilities.canEditRegisters)
        assertTrue(capabilities.canReadMemory)
        assertTrue(capabilities.canWriteMemory)
        assertTrue(capabilities.canUseBreakpoints)
        assertTrue(capabilities.canUseHardwareBreakpoints == hardwareBreakpoints)
        assertTrue(capabilities.canUseWatchpoints)
        assertTrue(capabilities.canUseConditionalBreakpoints)
        assertTrue(capabilities.canListThreads)
        assertTrue(capabilities.canListFrames)
    }

    @Test
    fun defaultBackendMethodsReturnUnsupportedForOptionalFeatures() = runTest {
        val backend = object : DebuggerBackend {
            override val type = DebugBackend.ESIL
            override suspend fun probe() = Result.success(DebugCapabilities())
            override suspend fun start(config: DebugSessionConfig) = error("not used")
            override suspend fun stop() = Result.success(Unit)
            override suspend fun refresh() = error("not used")
            override suspend fun stepInto() = error("not used")
            override suspend fun stepOver() = error("not used")
            override suspend fun continueRun() = error("not used")
            override suspend fun pause() = error("not used")
            override suspend fun setBreakpoint(breakpoint: DebugBreakpoint) = Result.success(breakpoint)
            override suspend fun removeBreakpoint(address: Long) = Result.success(Unit)
            override suspend fun listBreakpoints() = Result.success(emptyList<DebugBreakpoint>())
            override suspend fun readRegisters() = Result.success(emptyList<DebugRegister>())
        }

        assertTrue(backend.stepOut().isFailure)
        assertTrue(backend.writeRegister("pc", 1).isFailure)
        assertTrue(backend.readMemory(0, 1).isFailure)
        assertTrue(backend.writeMemory(0, byteArrayOf(1)).isFailure)
        assertTrue(backend.setBreakpointEnabled(0x1000L, false).isFailure)
        assertTrue(backend.setBreakpointCondition(0x1000L, "eax==1").isFailure)
        assertTrue(backend.resolveExpression("sp+8").isFailure)
        assertTrue(backend.selectThread("1").isFailure)
        assertTrue(backend.listThreads().getOrThrow().isEmpty())
        assertTrue(backend.listFrames().getOrThrow().isEmpty())
        assertTrue(backend.readStack().getOrThrow().isEmpty())
    }

    @Test
    fun baseBackendReadStackDoesNotClobberRegisterChangedTracking() = runTest {
        val runner = R2DebugCommandRunner()
        var currentRegs = """{"rip":4096,"rsp":8192,"rax":1}"""
        val backend = object : BaseR2DebuggerBackend(runner) {
            override val type = DebugBackend.NATIVE_GDB
            override val registerCommands = listOf("drj")
            override val breakpointListCommand = "dbj"
            override val seekCommand = "s"
            override suspend fun query(command: String): Result<String> = when {
                command == "drj" -> Result.success(currentRegs)
                command.startsWith("pxr") -> Result.success("""[{"addr":"0x2000","value":"0x1000","flag":"sym.main"}]""")
                else -> Result.success("[]")
            }
            override suspend fun probe() = Result.success(DebugCapabilities())
            override suspend fun start(config: DebugSessionConfig) = error("unused")
            override suspend fun stop() = Result.success(Unit)
            override suspend fun stepInto() = error("unused")
            override suspend fun stepOver() = error("unused")
            override suspend fun continueRun() = error("unused")
            override suspend fun pause() = error("unused")
            override suspend fun setBreakpoint(breakpoint: DebugBreakpoint) = Result.success(breakpoint)
            override suspend fun removeBreakpoint(address: Long) = Result.success(Unit)
        }

        // Initial read establishes baseline
        val initial = backend.readRegisters().getOrThrow()
        assertFalse(initial.first { it.name == "rax" }.changed)

        // Mutate registers before readStack; readStack must NOT overwrite lastRegisters
        currentRegs = """{"rip":4100,"rsp":8192,"rax":42}"""
        val stack = backend.readStack(1).getOrThrow()
        assertTrue(stack.size == 1 && stack[0].symbol == "sym.main")

        // Subsequent readRegisters still detects the change from 1 -> 42
        val after = backend.readRegisters().getOrThrow()
        val rax = after.first { it.name == "rax" }
        assertTrue(rax.changed)
        assertTrue(rax.previousValue == 1L && rax.value == 42L)
    }

}

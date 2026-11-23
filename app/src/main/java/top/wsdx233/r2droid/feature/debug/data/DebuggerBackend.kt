package top.wsdx233.r2droid.feature.debug.data

interface DebuggerBackend {
    val type: DebugBackend

    suspend fun probe(): Result<DebugCapabilities>
    suspend fun start(config: DebugSessionConfig = DebugSessionConfig(type)): Result<DebugStateSnapshot>
    suspend fun reset(config: DebugSessionConfig = DebugSessionConfig(type)): Result<DebugStateSnapshot> = start(config)
    suspend fun stop(): Result<Unit>
    suspend fun refresh(): Result<DebugStateSnapshot>

    suspend fun stepInto(): Result<DebugStateSnapshot>
    suspend fun stepOver(): Result<DebugStateSnapshot>
    suspend fun stepOut(): Result<DebugStateSnapshot> = Result.failure(
        UnsupportedOperationException("Step out is not supported by ${type.displayName}")
    )

    suspend fun continueRun(): Result<DebugStateSnapshot>
    suspend fun pause(): Result<DebugStateSnapshot>

    suspend fun setBreakpoint(breakpoint: DebugBreakpoint): Result<DebugBreakpoint>
    suspend fun removeBreakpoint(address: Long): Result<Unit>
    suspend fun setBreakpointEnabled(address: Long, enabled: Boolean): Result<Unit> = Result.failure(
        UnsupportedOperationException("Toggling breakpoint state is not supported by ${type.displayName}")
    )
    suspend fun setBreakpointCondition(address: Long, condition: String?): Result<Unit> = Result.failure(
        UnsupportedOperationException("Conditional breakpoints are not supported by ${type.displayName}")
    )
    suspend fun listBreakpoints(): Result<List<DebugBreakpoint>>

    suspend fun readRegisters(): Result<List<DebugRegister>>
    suspend fun writeRegister(name: String, value: Long): Result<Unit> = Result.failure(
        UnsupportedOperationException("Register editing is not supported by ${type.displayName}")
    )

    suspend fun readMemory(address: Long, size: Int): Result<ByteArray> = Result.failure(
        UnsupportedOperationException("Memory reading is not implemented for ${type.displayName}")
    )

    suspend fun writeMemory(address: Long, bytes: ByteArray): Result<Unit> = Result.failure(
        UnsupportedOperationException("Memory writing is not supported by ${type.displayName}")
    )

    suspend fun resolveExpression(expression: String): Result<Long> = Result.failure(
        UnsupportedOperationException("Expression evaluation is not supported by ${type.displayName}")
    )

    suspend fun listThreads(): Result<List<DebugThread>> = Result.success(emptyList())
    suspend fun selectThread(threadId: String): Result<DebugStateSnapshot> = Result.failure(
        UnsupportedOperationException("Thread switching is not supported by ${type.displayName}")
    )
    suspend fun listFrames(): Result<List<DebugFrame>> = Result.success(emptyList())
    suspend fun readStack(count: Int = 16): Result<List<DebugStackEntry>> = Result.success(emptyList())
}

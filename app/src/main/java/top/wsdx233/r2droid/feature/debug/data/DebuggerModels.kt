package top.wsdx233.r2droid.feature.debug.data

import java.util.Locale

/**
 * Debug backend type.
 *
 * NATIVE_GDB is kept for source compatibility with the existing UI, but it currently means
 * radare2's native debugger commands (ood/ds/dc/drj), not a full GDB remote integration.
 */
enum class DebugBackend {
    ESIL,
    NATIVE_GDB,
    FRIDA,
    GDB_REMOTE;

    val displayName: String
        get() = when (this) {
            ESIL -> "ESIL"
            NATIVE_GDB -> "Native R2"
            FRIDA -> "Frida"
            GDB_REMOTE -> "GDB Remote"
        }
}

data class DebugCapabilities(
    val canStepInto: Boolean = true,
    val canStepOver: Boolean = true,
    val canStepOut: Boolean = false,
    val canContinue: Boolean = true,
    val canPause: Boolean = true,
    val canEditRegisters: Boolean = false,
    val canReadMemory: Boolean = true,
    val canWriteMemory: Boolean = false,
    val canUseBreakpoints: Boolean = true,
    val canUseHardwareBreakpoints: Boolean = false,
    val canUseConditionalBreakpoints: Boolean = false,
    val canListThreads: Boolean = false,
    val canListFrames: Boolean = false,
    val canTrace: Boolean = false
)

data class DebugSessionConfig(
    val backend: DebugBackend,
    val startAddress: Long? = null,
    val startAtCurrentSeek: Boolean = true,
    val args: String = "",
    val env: Map<String, String> = emptyMap(),
    val workingDir: String? = null,
    val executablePath: String? = null,
    val stackAddress: Long? = null,
    val stackSize: Long = 0x10000L,
    val maxEsilSteps: Int = 10_000,
    val esilTimeoutMs: Long = 10_000L,
    val attachPid: Int? = null,
    val gdbRemoteHost: String? = null,
    val gdbRemotePort: Int? = null
)

enum class DebugRunState {
    IDLE,
    STARTING,
    SUSPENDED,
    RUNNING,
    STOPPING,
    TERMINATED,
    ERROR
}

enum class DebugStopReasonType {
    STEP,
    BREAKPOINT,
    MANUAL_PAUSE,
    SIGNAL,
    EXCEPTION,
    PROCESS_EXIT,
    ESIL_ERROR,
    TIMEOUT,
    STARTED,
    STOPPED,
    UNKNOWN,
    STEP_LIMIT,
    TARGET_REACHED
}

/** [address] is the stop location (the faulting instruction PC for an ESIL fault). */
data class DebugStopReason(
    val type: DebugStopReasonType = DebugStopReasonType.UNKNOWN,
    val message: String? = null,
    val signal: String? = null,
    val address: Long? = null,
    val accessAddress: Long? = null
)

enum class RegisterRole {
    PC,
    SP,
    BP,
    FLAGS,
    GENERAL,
    FLOAT,
    VECTOR,
    SEGMENT
}

data class DebugRegister(
    val name: String,
    val value: Long,
    val previousValue: Long? = null,
    val role: RegisterRole = RegisterRole.GENERAL
) {
    val changed: Boolean get() = previousValue != null && previousValue != value
}

data class DebugBreakpoint(
    val address: Long,
    val enabled: Boolean = true,
    val temporary: Boolean = false,
    val condition: String? = null,
    val hitCount: Int = 0,
    val backendId: String? = null
)

data class DebugThread(
    val id: String,
    val name: String = id,
    val selected: Boolean = false,
    val pc: Long? = null
)

data class DebugFrame(
    val index: Int,
    val address: Long,
    val functionName: String? = null,
    val sp: Long? = null,
    val bp: Long? = null
)

data class DebugStackEntry(
    val address: Long,
    val value: Long
)

data class DebugTraceEntry(
    val index: Int,
    val pc: Long,
    val action: String,
    val changedRegisters: Map<String, Pair<Long, Long>> = emptyMap(),
    val reason: String? = null
)

data class DebugStateSnapshot(
    val backend: DebugBackend,
    val runState: DebugRunState,
    val pc: Long? = null,
    val registers: List<DebugRegister> = emptyList(),
    val breakpoints: List<DebugBreakpoint> = emptyList(),
    val stopReason: DebugStopReason = DebugStopReason(),
    val threads: List<DebugThread> = emptyList(),
    val frames: List<DebugFrame> = emptyList(),
    val message: String? = null
) {
    fun registerMap(): Map<String, Long> = registers.associate { it.name to it.value }
    fun breakpointAddresses(): Set<Long> = breakpoints.map { it.address }.toSet()
}

internal fun registerRoleForName(name: String): RegisterRole {
    val normalized = name.lowercase(Locale.ROOT)
    return when {
        normalized in setOf("pc", "rip", "eip", "ip") -> RegisterRole.PC
        normalized == "sp" || normalized.endsWith("sp") -> RegisterRole.SP
        normalized == "bp" || normalized.endsWith("bp") || normalized == "fp" -> RegisterRole.BP
        normalized in setOf("flags", "eflags", "rflags", "cpsr", "apsr", "xpsr") -> RegisterRole.FLAGS
        normalized.startsWith("st") || normalized.startsWith("fp") -> RegisterRole.FLOAT
        normalized.startsWith("xmm") || normalized.startsWith("ymm") || normalized.startsWith("zmm") ||
            normalized.matches(Regex("v\\d+")) -> RegisterRole.VECTOR
        normalized in setOf("cs", "ds", "es", "fs", "gs", "ss") -> RegisterRole.SEGMENT
        else -> RegisterRole.GENERAL
    }
}

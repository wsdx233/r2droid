package top.wsdx233.r2droid.feature.disasm


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.radare.r2pipe.kotlin.R2PipeSession
import top.wsdx233.r2droid.core.data.model.FunctionDetailInfo
import top.wsdx233.r2droid.core.data.model.FunctionVariablesData
import top.wsdx233.r2droid.core.data.model.FunctionXref
import top.wsdx233.r2droid.core.data.model.InstructionDetail
import top.wsdx233.r2droid.core.data.model.XrefsData
import top.wsdx233.r2droid.feature.ai.data.AiRepository
import top.wsdx233.r2droid.feature.ai.data.AiSettingsManager
import top.wsdx233.r2droid.feature.ai.data.ChatMessage
import top.wsdx233.r2droid.feature.ai.data.ChatRole
import top.wsdx233.r2droid.feature.ai.data.ThinkingLevel
import top.wsdx233.r2droid.feature.disasm.data.DisasmAddressLayout
import top.wsdx233.r2droid.feature.disasm.data.DisasmAnnotations
import top.wsdx233.r2droid.feature.disasm.data.DisasmDataManager
import top.wsdx233.r2droid.feature.debug.data.DebugBackend
import top.wsdx233.r2droid.feature.debug.data.DebugCapabilities
import top.wsdx233.r2droid.feature.debug.data.DebugRegister
import top.wsdx233.r2droid.feature.debug.data.DebugRunState
import top.wsdx233.r2droid.feature.debug.data.DebugSessionConfig
import top.wsdx233.r2droid.feature.debug.data.DebugStateSnapshot
import top.wsdx233.r2droid.feature.debug.data.DebugStopReason
import top.wsdx233.r2droid.feature.debug.data.DebugTraceEntry
import top.wsdx233.r2droid.feature.debug.data.RegisterRole
import top.wsdx233.r2droid.feature.disasm.data.DisasmRepository
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.util.ViewerByteEdit
import top.wsdx233.r2droid.util.writeViewerBytes
import java.util.Locale
import javax.inject.Inject


data class DisasmScrollTarget(val address: Long, val index: Int, val animate: Boolean = true, val requestId: Long = 0)

data class XrefsState(
    val visible: Boolean = false,
    val data: XrefsData = XrefsData(),
    val isLoading: Boolean = false,
    val targetAddress: Long = 0L
)

data class FunctionInfoState(
    val visible: Boolean = false,
    val data: FunctionDetailInfo? = null,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val availableCallingConventions: List<top.wsdx233.r2droid.feature.types.data.CallingConventionInfo> = emptyList(),
    val typeSuggestions: List<String> = emptyList(),
    val targetAddress: Long = 0L
)

data class FunctionXrefsState(
    val visible: Boolean = false,
    val data: List<FunctionXref> = emptyList(),
    val isLoading: Boolean = false,
    val targetAddress: Long = 0L
)

data class FunctionVariablesState(
    val visible: Boolean = false,
    val data: FunctionVariablesData = FunctionVariablesData(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val typeSuggestions: List<String> = emptyList(),
    val targetAddress: Long = 0L
)

data class InstructionDetailState(
    val visible: Boolean = false,
    val data: InstructionDetail? = null,
    val isLoading: Boolean = false,
    val targetAddress: Long = 0L,
    val aiExplanation: String = "",
    val aiExplainLoading: Boolean = false,
    val aiExplainError: String? = null
)

data class AiPolishState(
    val visible: Boolean = false,
    val isLoading: Boolean = false,
    val targetAddress: Long = 0L,
    val result: String = "",
    val error: String? = null
)

data class MultiSelectState(
    val active: Boolean = false,
    val startAddr: Long = -1L,
    val endAddr: Long = -1L
) {
    fun contains(addr: Long): Boolean = active && addr in minOf(startAddr, endAddr)..maxOf(startAddr, endAddr)
    val rangeStart get() = minOf(startAddr, endAddr)
    val rangeEnd get() = maxOf(startAddr, endAddr)
}

enum class DebugStatus { IDLE, STARTING, SUSPENDED, RUNNING, STOPPING, ERROR }

data class DebugStackState(
    val entries: List<top.wsdx233.r2droid.feature.debug.data.DebugStackEntry> = emptyList(),
    val stackPointer: Long? = null,
    val isLoading: Boolean = false
)

data class DebugMemoryState(
    val address: Long? = null,
    val bytes: ByteArray = ByteArray(0),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DebugMemoryState) return false
        return address == other.address && bytes.contentEquals(other.bytes) &&
            isLoading == other.isLoading && error == other.error
    }

    override fun hashCode(): Int {
        var result = address?.hashCode() ?: 0
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

data class DebugLaunchConfigState(
    val startAddressText: String = "",
    val startAtCurrentSeek: Boolean = true,
    val stackAddressText: String = "",
    val stackSizeText: String = "0x10000",
    val maxEsilStepsText: String = "10000",
    val esilTimeoutMsText: String = "10000",
    val attachPidText: String = "",
    val gdbRemoteHostText: String = "127.0.0.1",
    val gdbRemotePortText: String = "1234"
)

/**
 * ViewModel for Disassembly Viewer.
 * Manages DisasmDataManager and disasm-related interactions.
 */

sealed interface DisasmEvent {
    data class LoadDisassembly(val session: R2PipeSession, val layout: DisasmAddressLayout, val currentOffset: Long) : DisasmEvent
    data class LoadChunk(val address: Long) : DisasmEvent
    data class Preload(val address: Long) : DisasmEvent
    data class LoadMore(val forward: Boolean) : DisasmEvent
    data class WriteAsm(val session: R2PipeManager.R2Session, val address: Long, val asm: String) : DisasmEvent
    data class WriteHex(val session: R2PipeManager.R2Session, val address: Long, val hex: String) : DisasmEvent
    data class WriteString(val session: R2PipeManager.R2Session, val address: Long, val text: String) : DisasmEvent
    data class WriteComment(val address: Long, val comment: String) : DisasmEvent
    object RefreshData : DisasmEvent
    object Reset : DisasmEvent
    data class FetchXrefs(val address: Long) : DisasmEvent
    object DismissXrefs : DisasmEvent
    // Function operations
    data class AnalyzeFunction(val address: Long) : DisasmEvent
    data class FetchFunctionInfo(val address: Long) : DisasmEvent
    object DismissFunctionInfo : DisasmEvent
    data class RenameFunctionFromInfo(val address: Long, val newName: String) : DisasmEvent
    data class UpdateFunctionSignature(val address: Long, val signature: String) : DisasmEvent
    data class UpdateFunctionCallingConvention(val address: Long, val callingConvention: String) : DisasmEvent
    object ClearFunctionInfoError : DisasmEvent
    data class FetchFunctionXrefs(val address: Long) : DisasmEvent
    object DismissFunctionXrefs : DisasmEvent
    data class FetchFunctionVariables(val address: Long) : DisasmEvent
    object DismissFunctionVariables : DisasmEvent
    data class RenameFunctionVariable(val address: Long, val oldName: String, val newName: String) : DisasmEvent
    data class RetypeFunctionVariable(val address: Long, val varName: String, val newType: String) : DisasmEvent
    object ClearFunctionVariablesError : DisasmEvent
    data class FetchInstructionDetail(val address: Long) : DisasmEvent
    data class ExplainInstructionWithAi(val address: Long) : DisasmEvent
    object DismissInstructionDetail : DisasmEvent
    data class AiPolishDisassembly(val address: Long) : DisasmEvent
    object DismissAiPolish : DisasmEvent
    // Multi-select
    data class StartMultiSelect(val addr: Long) : DisasmEvent
    data class UpdateMultiSelect(val addr: Long) : DisasmEvent
    object ClearMultiSelect : DisasmEvent
    object ExtendToFunction : DisasmEvent
}

@HiltViewModel
class DisasmViewModel @Inject constructor(
    private val disasmRepository: DisasmRepository,
    private val aiRepository: AiRepository,
    private val debuggerRepository: top.wsdx233.r2droid.feature.debug.data.DebuggerRepository
) : ViewModel() {

    // DisasmDataManager for virtualized disassembly viewing
    @Volatile private var sessionManager: DisasmDataManager? = null
    @Volatile private var managerSession: R2PipeSession? = null
    @Volatile private var annotations = DisasmAnnotations.Empty
    val disasmDataManager: DisasmDataManager?
        get() = sessionManager?.takeIf { managerSession === R2PipeManager.activeSessionPipe }

    private val _disasmDataManagerState = MutableStateFlow<DisasmDataManager?>(null)
    val disasmDataManagerState = _disasmDataManagerState.asStateFlow()

    // Cache version counter for disasm - increment to trigger UI recomposition when chunks load
    private val _disasmCacheVersion = MutableStateFlow(0)
    val disasmCacheVersion: StateFlow<Int> = _disasmCacheVersion.asStateFlow()

    // Xrefs State
    private val _xrefsState = MutableStateFlow(XrefsState())
    val xrefsState: StateFlow<XrefsState> = _xrefsState.asStateFlow()

    // Function Info State
    private val _functionInfoState = MutableStateFlow(FunctionInfoState())
    val functionInfoState: StateFlow<FunctionInfoState> = _functionInfoState.asStateFlow()

    // Function Xrefs State
    private val _functionXrefsState = MutableStateFlow(FunctionXrefsState())
    val functionXrefsState: StateFlow<FunctionXrefsState> = _functionXrefsState.asStateFlow()

    // Function Variables State
    private val _functionVariablesState = MutableStateFlow(FunctionVariablesState())
    val functionVariablesState: StateFlow<FunctionVariablesState> = _functionVariablesState.asStateFlow()

    // Instruction Detail State
    private val _instructionDetailState = MutableStateFlow(InstructionDetailState())
    val instructionDetailState: StateFlow<InstructionDetailState> = _instructionDetailState.asStateFlow()

    private val _aiPolishState = MutableStateFlow(AiPolishState())
    val aiPolishState: StateFlow<AiPolishState> = _aiPolishState.asStateFlow()

    // Multi-select State
    private val _multiSelectState = MutableStateFlow(MultiSelectState())
    val multiSelectState: StateFlow<MultiSelectState> = _multiSelectState.asStateFlow()

    // Scroll target: emitted after data is loaded at target address
    private val _scrollTarget = MutableStateFlow<DisasmScrollTarget?>(null)
    val scrollTarget: StateFlow<DisasmScrollTarget?> = _scrollTarget.asStateFlow()
    private val _isScrollbarSeeking = MutableStateFlow(false)
    val isScrollbarSeeking = _isScrollbarSeeking.asStateFlow()
    private val scrollbarNavigator = DisasmScrollbarNavigator(viewModelScope) { address, index ->
        _disasmCacheVersion.update { it + 1 }
        _scrollTarget.value = DisasmScrollTarget(address, index, animate = false, requestId = navigationRequestId)
    }
    val scrollbarPreview = scrollbarNavigator.preview

    // Event to notify that data has been modified
    private val _dataModifiedEvent = MutableStateFlow(0L)
    val dataModifiedEvent: StateFlow<Long> = _dataModifiedEvent.asStateFlow()

    // 调试后端模式
    private val _debugBackend = MutableStateFlow(defaultDebugBackendForCurrentSession())
    val debugBackend: StateFlow<DebugBackend> = _debugBackend.asStateFlow()

    fun setDebugBackend(backend: DebugBackend) {
        syncDebugPipe()
        val status = _debugStatus.value
        val canSelect = status == DebugStatus.IDLE ||
            (status == DebugStatus.ERROR && debugSession?.esilMayBeActive != true)
        if (!canSelect) return

        if (status == DebugStatus.ERROR) {
            retireDebugSession()
            clearDebugState()
        } else if (backend != _debugBackend.value) {
            debugSession = null
        }
        val sanitized = sanitizeDebugBackendForCurrentSession(backend)
        _debugBackend.value = sanitized
        _debugCapabilities.value = DebugCapabilities()
        if (backend != sanitized) {
            _debugError.value = backendUnavailableMessage(backend)
            _debugStatus.value = DebugStatus.ERROR
        }
    }

    private val _debugCapabilities = MutableStateFlow(DebugCapabilities())
    val debugCapabilities: StateFlow<DebugCapabilities> = _debugCapabilities.asStateFlow()

    // 当前 PC (Program Counter) 地址
    private val _pcAddress = MutableStateFlow<Long?>(null)
    val pcAddress: StateFlow<Long?> = _pcAddress.asStateFlow()

    private val _debugStopReason = MutableStateFlow<DebugStopReason?>(null)
    val debugStopReason = _debugStopReason.asStateFlow()

    // 断点集合
    private val _breakpoints = MutableStateFlow<Set<Long>>(emptySet())
    val breakpoints: StateFlow<Set<Long>> = _breakpoints.asStateFlow()

    // 寄存器状态
    private val _registers = MutableStateFlow<List<DebugRegister>>(emptyList())
    val registers = _registers.asStateFlow()

    private val _debugStackState = MutableStateFlow(DebugStackState())
    val debugStackState: StateFlow<DebugStackState> = _debugStackState.asStateFlow()

    private val _debugMemoryState = MutableStateFlow(DebugMemoryState())
    val debugMemoryState: StateFlow<DebugMemoryState> = _debugMemoryState.asStateFlow()

    private val _debugTraceEntries = MutableStateFlow<List<DebugTraceEntry>>(emptyList())
    val debugTraceEntries = _debugTraceEntries.asStateFlow()

    private val _debugLaunchConfig = MutableStateFlow(DebugLaunchConfigState())
    val debugLaunchConfig: StateFlow<DebugLaunchConfigState> = _debugLaunchConfig.asStateFlow()

    private var lastTraceRegisters: Map<String, Long> = emptyMap()
    private var traceIndex = 0

    // 调试器状态
    private val _debugStatus = MutableStateFlow(DebugStatus.IDLE)
    val debugStatus: StateFlow<DebugStatus> = _debugStatus.asStateFlow()

    private val _debugError = MutableStateFlow<String?>(null)
    val debugError: StateFlow<String?> = _debugError.asStateFlow()

    private class DebugSession(var pipe: R2PipeSession, val selection: String) {
        val mutations = Mutex()
        var started = false
        var esilMayBeActive = false
        var openingConfig: DebugSessionConfig? = null
        var operation = 0L
        var memoryRead = 0L
    }

    private var observedDebugPipe = R2PipeManager.activeSessionPipe
    private var debugSession: DebugSession? = null

    init {
        viewModelScope.launch {
            combine(R2PipeManager.activeSessionId, R2PipeManager.sessions) { _, _ -> Unit }
                .collect { syncDebugPipe() }
        }
    }

    private fun syncDebugPipe() {
        val active = R2PipeManager.activeSession()
        val pipe = active?.pipe
        if (pipe === observedDebugPipe) return
        observedDebugPipe = pipe

        val session = debugSession
        // Native launch and GDB attach can intentionally reopen the same drawer entry in -d mode.
        // ESIL never adopts a different pipe: all of its state belongs to the acquired pipe.
        if (active != null && session != null && active.sessionId == session.selection &&
            session.openingConfig?.let { config ->
                when (config.backend) {
                    DebugBackend.NATIVE_GDB -> "-d" in active.launchFlags.split(Regex("\\s+")) &&
                        (active.projectPath == config.executablePath ||
                            active.projectPath?.contains("/debug-targets/") == true)
                    DebugBackend.GDB_REMOTE -> active.projectPath ==
                        "gdb://${config.gdbRemoteHost}:${config.gdbRemotePort}"
                    else -> false
                }
            } == true
        ) {
            session.pipe = active.pipe
            return
        }
        retireDebugSession()
        clearDebugState()
    }

    private fun currentDebugSession(): DebugSession? {
        syncDebugPipe()
        val active = R2PipeManager.activeSession() ?: return null
        return debugSession ?: DebugSession(active.pipe, active.sessionId).also { debugSession = it }
    }

    private fun ownsDebugSession(session: DebugSession): Boolean =
        debugSession === session && session.pipe === R2PipeManager.activeSessionPipe

    private fun retireDebugSession() {
        val previous = debugSession
        debugSession = null
        if (previous?.esilMayBeActive == true) debuggerRepository.releaseEsil(previous.pipe)
    }

    override fun onCleared() {
        retireDebugSession()
        super.onCleared()
    }

    fun clearDebugError() {
        _debugError.value = null
    }

    private fun setDebugError(throwable: Throwable?) {
        _debugError.value = throwable?.message ?: "Debug operation failed"
        _debugStatus.value = DebugStatus.ERROR
    }

    private fun defaultDebugBackendForCurrentSession(): DebugBackend {
        return if (R2PipeManager.isR2FridaSession) DebugBackend.FRIDA else DebugBackend.ESIL
    }

    private fun sanitizeDebugBackendForCurrentSession(backend: DebugBackend): DebugBackend {
        return when {
            R2PipeManager.isR2FridaSession -> DebugBackend.FRIDA
            backend == DebugBackend.FRIDA -> DebugBackend.ESIL
            else -> backend
        }
    }

    private fun backendUnavailableMessage(backend: DebugBackend): String {
        return if (backend == DebugBackend.FRIDA) {
            "FRIDA backend requires an active r2frida session"
        } else {
            "${backend.displayName} debug backend is not available in an r2frida session; use Frida backend"
        }
    }

    private fun ensureDebugBackendMatchesCurrentSession(): Boolean {
        val current = _debugBackend.value
        val sanitized = sanitizeDebugBackendForCurrentSession(current)
        if (current == sanitized) return true
        _debugBackend.value = sanitized
        _debugCapabilities.value = DebugCapabilities()
        _debugError.value = backendUnavailableMessage(current)
        _debugStatus.value = DebugStatus.ERROR
        return false
    }

    private fun clearDebugState() {
        _pcAddress.value = null
        _debugStopReason.value = null
        _breakpoints.value = emptySet()
        _registers.value = emptyList()
        _debugStackState.value = DebugStackState()
        _debugMemoryState.value = DebugMemoryState()
        _debugTraceEntries.value = emptyList()
        lastTraceRegisters = emptyMap()
        traceIndex = 0
        _debugCapabilities.value = DebugCapabilities()
        _debugBackend.value = defaultDebugBackendForCurrentSession()
        _debugError.value = null
        _debugStatus.value = DebugStatus.IDLE
        _disasmCacheVersion.update { it + 1 }
    }

    fun onEvent(event: DisasmEvent) {
        when (event) {
            is DisasmEvent.LoadDisassembly -> loadDisassembly(event.session, event.layout, event.currentOffset)
            is DisasmEvent.LoadChunk -> loadDisasmChunkForAddress(event.address)
            is DisasmEvent.Preload -> preloadDisasmAround(event.address)
            is DisasmEvent.LoadMore -> loadDisasmMore(event.forward)
            is DisasmEvent.WriteAsm -> writeBytes(event.session, event.address, ViewerByteEdit.ASSEMBLY, event.asm)
            is DisasmEvent.WriteHex -> writeBytes(event.session, event.address, ViewerByteEdit.HEX, event.hex)
            is DisasmEvent.WriteString -> writeBytes(event.session, event.address, ViewerByteEdit.TEXT, event.text)
            is DisasmEvent.WriteComment -> writeComment(event.address, event.comment)
            is DisasmEvent.RefreshData -> refreshData()
            is DisasmEvent.Reset -> reset()
            is DisasmEvent.FetchXrefs -> fetchXrefs(event.address)
            is DisasmEvent.DismissXrefs -> dismissXrefs()
            is DisasmEvent.AnalyzeFunction -> analyzeFunction(event.address)
            is DisasmEvent.FetchFunctionInfo -> fetchFunctionInfo(event.address)
            is DisasmEvent.DismissFunctionInfo -> dismissFunctionInfo()
            is DisasmEvent.RenameFunctionFromInfo -> renameFunctionFromInfo(event.address, event.newName)
            is DisasmEvent.UpdateFunctionSignature -> updateFunctionSignature(event.address, event.signature)
            is DisasmEvent.UpdateFunctionCallingConvention -> updateFunctionCallingConvention(event.address, event.callingConvention)
            is DisasmEvent.ClearFunctionInfoError -> _functionInfoState.update { it.copy(errorMessage = null) }
            is DisasmEvent.FetchFunctionXrefs -> fetchFunctionXrefs(event.address)
            is DisasmEvent.DismissFunctionXrefs -> dismissFunctionXrefs()
            is DisasmEvent.FetchFunctionVariables -> fetchFunctionVariables(event.address)
            is DisasmEvent.DismissFunctionVariables -> dismissFunctionVariables()
            is DisasmEvent.RenameFunctionVariable -> renameFunctionVariable(event.address, event.oldName, event.newName)
            is DisasmEvent.RetypeFunctionVariable -> retypeFunctionVariable(event.address, event.varName, event.newType)
            is DisasmEvent.ClearFunctionVariablesError -> _functionVariablesState.update { it.copy(errorMessage = null) }
            is DisasmEvent.FetchInstructionDetail -> fetchInstructionDetail(event.address)
            is DisasmEvent.ExplainInstructionWithAi -> explainInstructionWithAi(event.address)
            is DisasmEvent.DismissInstructionDetail -> dismissInstructionDetail()
            is DisasmEvent.AiPolishDisassembly -> polishDisassemblyWithAi(event.address)
            is DisasmEvent.DismissAiPolish -> dismissAiPolish()
            is DisasmEvent.StartMultiSelect -> _multiSelectState.value = MultiSelectState(true, event.addr, event.addr)
            is DisasmEvent.UpdateMultiSelect -> _multiSelectState.value = _multiSelectState.value.copy(endAddr = event.addr)
            is DisasmEvent.ClearMultiSelect -> _multiSelectState.value = MultiSelectState()
            is DisasmEvent.ExtendToFunction -> extendSelectionToFunction()
        }
    }

    /**
     * 重置所有数据，用于切换项目时清理旧数据。
     */
    fun reset() {
        resetDisassembly()
        retireDebugSession()
        clearDebugState()
    }

    private fun resetDisassembly() {
        navigationRequestId++
        cancelScrollLoads()
        initialLoadJob?.cancel()
        initialLoadJob = null
        isScrollbarDragging = false
        _isScrollbarSeeking.value = false
        sessionManager?.onChunkLoaded = null
        sessionManager?.clearCache()
        sessionManager = null
        _disasmDataManagerState.value = null
        _disasmCacheVersion.value = 0
        annotations = DisasmAnnotations.Empty
        _scrollTarget.value = null
        _multiSelectState.value = MultiSelectState()
        managerSession = null
    }

    private var scrollJob: Job? = null
    private var preloadJob: Job? = null
    private var forwardLoadJob: Job? = null
    private var backwardLoadJob: Job? = null
    private var forwardLoadPending = false
    private var backwardLoadPending = false
    private var initialLoadJob: Job? = null
    private var navigationRequestId = 0L
    private var isScrollbarDragging = false
    private var preloadAddress: Long? = null

    private fun cancelScrollLoads() {
        scrollbarNavigator.cancel()
        scrollJob?.cancel()
        preloadJob?.cancel()
        forwardLoadJob?.cancel()
        backwardLoadJob?.cancel()
        scrollJob = null
        preloadJob = null
        forwardLoadJob = null
        backwardLoadJob = null
        forwardLoadPending = false
        backwardLoadPending = false
        preloadAddress = null
    }

    /** Pointer-down cancels obsolete work; region loads are independently dwell-debounced. */
    fun onScrollbarDragStateChange(dragging: Boolean) {
        isScrollbarDragging = dragging
        if (dragging) {
            initialLoadJob?.cancel()
            navigationRequestId++
            cancelScrollLoads()
            disasmDataManager?.prepareForJump()
            _scrollTarget.value = null
            _isScrollbarSeeking.value = false
            disasmDataManager?.let(scrollbarNavigator::begin)
        } else {
            scrollbarNavigator.endGesture()
        }
    }

    fun previewScrollbarAddress(addr: Long) = scrollbarNavigator.preview(addr)

    fun retryScrollbarPreview() = scrollbarNavigator.retry()

    fun dismissScrollbarPreview() {
        scrollbarNavigator.cancel()
        _scrollTarget.value = null
        _isScrollbarSeeking.value = false
    }

    fun loadAndScrollTo(addr: Long) {
        if (!isScrollbarDragging) startScroll(addr, animate = true)
    }

    /** Called once on release/tap, not for each pointer movement. */
    fun scrollbarJumpTo(addr: Long) {
        _isScrollbarSeeking.value = true
        scrollbarNavigator.commit(addr)
    }

    private fun startScroll(addr: Long, animate: Boolean) {
        val manager = disasmDataManager ?: return
        cancelScrollLoads()
        manager.prepareForJump()
        val requestId = ++navigationRequestId
        _scrollTarget.value = null
        _isScrollbarSeeking.value = !animate
        scrollJob = viewModelScope.launch {
            try {
                val index = manager.loadAndFindIndex(addr)
                currentCoroutineContext().ensureActive()
                if (disasmDataManager === manager && requestId == navigationRequestId && index >= 0) {
                    _disasmCacheVersion.update { it + 1 }
                    _scrollTarget.value = DisasmScrollTarget(addr, index, animate, requestId)
                }
            } finally {
                if (requestId == navigationRequestId && _scrollTarget.value == null) {
                    _isScrollbarSeeking.value = false
                }
            }
        }
    }

    private fun parseDebugLong(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return trimmed.removePrefix("0x").removePrefix("0X").toLongOrNull(16)
            ?: trimmed.toLongOrNull()
    }

    private fun buildDebugSessionConfig(): Result<DebugSessionConfig> = runCatching {
        check(ensureDebugBackendMatchesCurrentSession()) { _debugError.value ?: "Debug backend is unavailable for current session" }
        val config = _debugLaunchConfig.value
        DebugSessionConfig(
            backend = _debugBackend.value,
            executablePath = R2PipeManager.currentFilePath,
            startAddress = parseDebugLong(config.startAddressText),
            startAtCurrentSeek = config.startAtCurrentSeek,
            stackAddress = parseDebugLong(config.stackAddressText),
            stackSize = parseDebugLong(config.stackSizeText) ?: 0x10000L,
            maxEsilSteps = config.maxEsilStepsText.toIntOrNull()?.coerceAtLeast(1) ?: 10_000,
            esilTimeoutMs = config.esilTimeoutMsText.toLongOrNull()?.coerceAtLeast(0L) ?: 10_000L,
            attachPid = config.attachPidText.toIntOrNull()?.takeIf { it > 0 },
            gdbRemoteHost = config.gdbRemoteHostText.trim().takeIf { it.isNotBlank() },
            gdbRemotePort = config.gdbRemotePortText.toIntOrNull()
        )
    }

    fun updateDebugLaunchConfig(config: DebugLaunchConfigState) {
        _debugLaunchConfig.value = config
    }

    fun startDebugging() = openDebugger(reset = false)

    fun resetDebugging() = openDebugger(reset = true)

    private fun openDebugger(reset: Boolean) {
        val session = currentDebugSession() ?: return
        if (debugBusy() || (!reset && session.started)) return
        val config = buildDebugSessionConfig().getOrElse {
            setDebugError(it)
            return
        }.let { if (reset && it.backend != DebugBackend.ESIL) it.copy(startAddress = _pcAddress.value ?: it.startAddress) else it }

        runDebugOperation(if (reset) "reset" else "start", session, DebugStatus.STARTING) {
            debuggerRepository.awaitEsilCleanup(session.pipe)
            if (!ownsDebugSession(session)) return@runDebugOperation Result.failure(IllegalStateException("Debug session changed"))
            val capabilities = debuggerRepository.probe(config.backend)
            if (!ownsDebugSession(session)) return@runDebugOperation Result.failure(IllegalStateException("Debug session changed"))
            _debugCapabilities.value = capabilities.getOrDefault(_debugCapabilities.value)
            session.started = true
            session.esilMayBeActive = config.backend == DebugBackend.ESIL
            session.openingConfig = config
            if (reset) debuggerRepository.reset(config) else debuggerRepository.start(config)
        }
    }

    private fun debugBusy(): Boolean = when (_debugStatus.value) {
        DebugStatus.STARTING, DebugStatus.RUNNING, DebugStatus.STOPPING -> true
        else -> false
    }

    fun stopDebugging() {
        val session = currentDebugSession() ?: return
        if (debugBusy()) return
        val backend = _debugBackend.value
        _debugStatus.value = DebugStatus.STOPPING
        viewModelScope.launch {
            session.mutations.withLock {
                if (!ownsDebugSession(session)) return@launch
                val result = if (session.started) debuggerRepository.stop(backend) else Result.success(Unit)
                if (!ownsDebugSession(session)) return@launch
                if (result.isSuccess) {
                    debugSession = null
                    clearDebugState()
                } else {
                    setDebugError(result.exceptionOrNull())
                }
            }
        }
    }

    fun toggleBreakpoint(addr: Long) {
        val session = currentDebugSession() ?: return
        if (debugBusy() || !ensureDebugBackendMatchesCurrentSession()) return
        val backend = _debugBackend.value
        viewModelScope.launch {
            session.mutations.withLock {
                debuggerRepository.awaitEsilCleanup(session.pipe)
                if (!ownsDebugSession(session)) return@launch
                val before = _breakpoints.value
                val add = addr !in before
                val result = debuggerRepository.toggleBreakpoint(backend, addr, add)
                if (!ownsDebugSession(session)) return@launch
                if (result.isSuccess) {
                    val addresses = debuggerRepository.listBreakpoints(backend).getOrNull()?.map { it.address }?.toSet()
                    if (!ownsDebugSession(session)) return@launch
                    _breakpoints.value = addresses ?: if (add) before + addr else before - addr
                    _disasmCacheVersion.update { it + 1 }
                    _debugError.value = null
                } else {
                    _debugError.value = result.exceptionOrNull()?.message ?: "Breakpoint update failed"
                }
            }
        }
    }

    fun runToCursor(addr: Long) = runDebugOperation("run-to-cursor") { debuggerRepository.runToCursor(it, addr) }

    fun setDebugPc(addr: Long) = runDebugOperation("set-pc") { debuggerRepository.setProgramCounter(it, addr) }

    private fun refreshDebugStack(session: DebugSession, backend: DebugBackend, stackPointer: Long?) {
        val operation = session.operation
        _debugStackState.value = DebugStackState(stackPointer = stackPointer, isLoading = true)
        viewModelScope.launch {
            session.mutations.withLock {
                if (!ownsDebugSession(session) || session.operation != operation) return@launch
                val entries = debuggerRepository.readStack(backend).getOrDefault(emptyList())
                if (!ownsDebugSession(session) || session.operation != operation) return@launch
                _debugStackState.value = DebugStackState(entries, stackPointer ?: entries.firstOrNull()?.address)
            }
        }
    }

    private fun readDebugMemory(address: Long, size: Int = 128) {
        val session = currentDebugSession() ?: return
        if (!session.started || debugBusy()) return
        val backend = _debugBackend.value
        val operation = session.operation
        val request = ++session.memoryRead
        _debugMemoryState.value = DebugMemoryState(address = address, isLoading = true)
        viewModelScope.launch {
            session.mutations.withLock {
                if (!ownsDebugSession(session) || session.operation != operation || session.memoryRead != request) return@launch
                val result = debuggerRepository.readMemory(backend, address, size)
                if (!ownsDebugSession(session) || session.operation != operation || session.memoryRead != request) return@launch
                _debugMemoryState.value = result.fold(
                    onSuccess = { bytes -> DebugMemoryState(address = address, bytes = bytes) },
                    onFailure = { error -> DebugMemoryState(address = address, error = error.message) }
                )
            }
        }
    }

    fun readMemoryAtPc() {
        _pcAddress.value?.let { readDebugMemory(it) }
    }

    fun readMemoryAtSp() {
        _debugStackState.value.stackPointer?.let { readDebugMemory(it) }
    }

    fun clearDebugTrace() {
        _debugTraceEntries.value = emptyList()
        traceIndex = 0
    }

    private fun appendDebugTrace(action: String, snapshot: DebugStateSnapshot) {
        val current = snapshot.registerMap()
        val changed = current.mapNotNull { (name, value) ->
            val previous = lastTraceRegisters[name]
            if (previous != null && previous != value) name to (previous to value) else null
        }.toMap()
        lastTraceRegisters = current
        val reason = snapshot.stopReason
        val tracePc = snapshot.pc ?: reason.address ?: return
        traceIndex += 1
        val next = (_debugTraceEntries.value + DebugTraceEntry(
            index = traceIndex,
            pc = tracePc,
            action = action,
            changedRegisters = changed,
            reason = buildString {
                append(reason.type.name)
                reason.signal?.let { append(" · ").append(it) }
                reason.address?.let { append(" · instruction 0x").append(it.toULong().toString(16)) }
                reason.accessAddress?.let { append(" · access 0x").append(it.toULong().toString(16)) }
                reason.message?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
            }
        )).takeLast(200)
        _debugTraceEntries.value = next
    }

    fun performDebugAction(action: String) = runDebugOperation(action) { backend ->
        when (action) {
            "step" -> debuggerRepository.stepInto(backend)
            "over" -> debuggerRepository.stepOver(backend)
            "continue" -> debuggerRepository.continueRun(backend)
            else -> Result.failure(IllegalArgumentException("Unknown debug action: $action"))
        }
    }

    private fun runDebugOperation(
        action: String,
        session: DebugSession? = currentDebugSession(),
        status: DebugStatus = DebugStatus.RUNNING,
        command: suspend (DebugBackend) -> Result<DebugStateSnapshot>
    ) {
        if (session == null || debugBusy() || !ensureDebugBackendMatchesCurrentSession()) return
        if (status == DebugStatus.RUNNING && !session.started) return
        val backend = _debugBackend.value
        val operation = ++session.operation
        // A queued command isn't pausable yet. Publish RUNNING only as it enters the backend.
        _debugStatus.value = if (session.mutations.isLocked) DebugStatus.STARTING else status
        _debugError.value = null
        _debugStackState.value = _debugStackState.value.copy(isLoading = false)
        _debugMemoryState.value = _debugMemoryState.value.copy(isLoading = false)
        viewModelScope.launch {
            session.mutations.withLock {
                if (!ownsDebugSession(session)) return@launch
                _debugStatus.value = status
                val result = command(backend)
                syncDebugPipe()
                session.openingConfig = null
                if (!ownsDebugSession(session) || session.operation != operation || _debugStatus.value != status) return@launch
                result.fold(
                    onSuccess = { snapshot ->
                        if (action == "reset") clearDebugTrace()
                        applyDebugSnapshot(session, snapshot, action)
                    },
                    onFailure = { setDebugError(it) }
                )
            }
        }
    }

    fun pauseExecution() {
        val session = currentDebugSession() ?: return
        if (_debugStatus.value != DebugStatus.RUNNING) return
        val backend = _debugBackend.value
        if (backend == DebugBackend.ESIL) {
            // The in-flight operation alone publishes the stop and its reason.
            debuggerRepository.requestEsilPause()
            return
        }

        val operation = session.operation
        R2PipeManager.interrupt()
        // Preserve the other backends' interrupt path, without replacing a stop already published.
        viewModelScope.launch {
            delay(150L)
            if (!ownsDebugSession(session) || session.operation != operation || _debugStatus.value != DebugStatus.RUNNING) return@launch
            val result = debuggerRepository.pause(backend)
            if (!ownsDebugSession(session) || session.operation != operation || _debugStatus.value != DebugStatus.RUNNING) return@launch
            result.onSuccess { applyDebugSnapshot(session, it, "pause") }
        }
    }

    private fun applyDebugSnapshot(session: DebugSession, snapshot: DebugStateSnapshot, action: String) {
        _pcAddress.value = snapshot.pc
        _debugStopReason.value = snapshot.stopReason
        _registers.value = snapshot.registers
        _breakpoints.value = snapshot.breakpointAddresses()
        _debugError.value = null
        _debugStatus.value = when (snapshot.runState) {
            DebugRunState.IDLE -> DebugStatus.IDLE
            DebugRunState.STARTING -> DebugStatus.STARTING
            DebugRunState.SUSPENDED -> DebugStatus.SUSPENDED
            DebugRunState.RUNNING -> DebugStatus.RUNNING
            DebugRunState.STOPPING -> DebugStatus.STOPPING
            DebugRunState.TERMINATED, DebugRunState.ERROR -> DebugStatus.ERROR
        }
        appendDebugTrace(action, snapshot)

        // These are panel-specific reads, not a second debugger snapshot.
        if (snapshot.runState == DebugRunState.SUSPENDED) {
            val sp = snapshot.registers.firstOrNull { it.role == RegisterRole.SP }?.value
            refreshDebugStack(session, snapshot.backend, sp)
            snapshot.pc?.let { readDebugMemory(it) }
        }
        if (ownsDebugSession(session)) snapshot.pc?.let(::loadAndScrollTo)
    }

    fun clearScrollTarget(target: DisasmScrollTarget) {
        if (_scrollTarget.compareAndSet(target, null) && !target.animate) {
            scrollbarNavigator.acknowledgeCommit(target.address)
            _isScrollbarSeeking.value = false
        }
    }

    /**
     * Reset disasm data around the given address and emit scroll target to return to it.
     * Used after data-modifying operations (analyze, rename, write) to keep the view
     * at the affected address after refresh.
     */
    private suspend fun resetAndScrollTo(addr: Long) {
        val manager = disasmDataManager ?: return
        val requestId = ++navigationRequestId
        cancelScrollLoads()
        _scrollTarget.value = null
        _isScrollbarSeeking.value = false
        manager.resetAndLoadAround(addr)
        currentCoroutineContext().ensureActive()
        if (disasmDataManager !== manager || requestId != navigationRequestId) return
        val index = manager.findClosestIndex(addr)
        _disasmCacheVersion.update { it + 1 }
        if (index >= 0) _scrollTarget.value = DisasmScrollTarget(addr, index, requestId = requestId)
    }

    /** Initialize virtualized disassembly only after the session snapshot is ready. */
    private fun loadDisassembly(session: R2PipeSession, layout: DisasmAddressLayout, currentOffset: Long) {
        val active = R2PipeManager.activeSession()?.takeIf { it.pipe === session } ?: return
        syncDebugPipe()
        if (session !== managerSession) {
            resetDisassembly()
            managerSession = session
        }
        if (disasmDataManager != null) {
            preloadDisasmAround(currentOffset)
            return
        }

        val manager = DisasmDataManager(
            layout.startAddress, layout.endAddress,
            { offset, count -> disasmRepository.getDisassembly(active, offset, count, annotations) },
            addressOverview = layout.overview
        )
        manager.onChunkLoaded = {
            if (disasmDataManager === manager) _disasmCacheVersion.update { it + 1 }
        }
        sessionManager = manager
        _disasmDataManagerState.value = manager
        initialLoadJob = viewModelScope.launch {
            val snapshot = disasmRepository.loadAnnotations(active)
            if (disasmDataManager !== manager) return@launch
            annotations = snapshot
            manager.resetAndLoadAround(currentOffset)
            if (disasmDataManager === manager) _disasmCacheVersion.update { it + 1 }
        }
    }

    /**
     * Load a disasm chunk for a specific address (called from UI during scroll).
     */
    fun loadDisasmChunkForAddress(addr: Long) {
        preloadDisasmAround(addr)
    }

    /**
     * Preload disasm chunks around an address (called when user scrolls quickly).
     */
    fun preloadDisasmAround(addr: Long) {
        val manager = disasmDataManager ?: return
        if (isScrollbarDragging || scrollbarNavigator.isActive || scrollJob?.isActive == true || _scrollTarget.value != null) return
        if (preloadJob?.isActive == true && preloadAddress == addr) return
        preloadJob?.cancel()
        preloadAddress = addr
        preloadJob = viewModelScope.launch {
            delay(120)
            manager.preloadAround(addr, 1)
        }
    }

    /**
     * Load more disasm instructions (forward or backward).
     */
    fun loadDisasmMore(forward: Boolean) {
        val manager = disasmDataManager ?: return
        if (isScrollbarDragging || scrollbarNavigator.isActive || scrollJob?.isActive == true || _scrollTarget.value != null) return
        if ((if (forward) forwardLoadJob else backwardLoadJob)?.isActive == true) {
            // A new window may be published before this job resumes on Main. Remember its
            // edge demand instead of losing the only snapshotFlow emission for that boundary.
            if (forward) forwardLoadPending = true else backwardLoadPending = true
            return
        }
        val job = viewModelScope.launch {
            do {
                if (forward) forwardLoadPending = false else backwardLoadPending = false
                manager.loadMore(forward)
                currentCoroutineContext().ensureActive()
            } while (if (forward) forwardLoadPending else backwardLoadPending)
        }
        if (forward) forwardLoadJob = job else backwardLoadJob = job
    }

    private fun writeBytes(session: R2PipeManager.R2Session, addr: Long, type: ViewerByteEdit, value: String) {
        if (R2PipeManager.activeSessionPipe !== session.pipe) return
        viewModelScope.launch {
            if (!session.writeViewerBytes(type, value, listOf(addr)) || R2PipeManager.activeSessionPipe !== session.pipe) return@launch
            resetAndScrollTo(addr)
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }

    fun writeComment(addr: Long, comment: String) {
        viewModelScope.launch {
            val escaped = comment.replace("\"", "\\\"")
            top.wsdx233.r2droid.util.R2PipeManager.execute("CCu \"$escaped\" @ $addr")
            resetAndScrollTo(addr)
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }

    
    /**
     * Called when other modules modify data
     */
    fun refreshData(cursor: Long? = null) {
        val manager = disasmDataManager ?: return
        val active = R2PipeManager.activeSession()?.takeIf { it.pipe === managerSession } ?: return
        val currentAddr = cursor ?: manager.getSnapshot().let { snapshot ->
            if (snapshot.isNotEmpty()) snapshot[snapshot.size / 2].addr
            else manager.viewStartAddress
        }
        viewModelScope.launch {
            val snapshot = disasmRepository.loadAnnotations(active)
            if (disasmDataManager !== manager) return@launch
            annotations = snapshot
            resetAndScrollTo(currentAddr)
        }
    }
    
    // === Xrefs ===
    
    fun fetchXrefs(addr: Long) {
        // Show loading
        _xrefsState.value = _xrefsState.value.copy(
            visible = true, 
            isLoading = true, 
            data = XrefsData(),
            targetAddress = addr
        )
        
        viewModelScope.launch {
            val result = disasmRepository.getXrefs(addr)
            val xrefsData = result.getOrElse { XrefsData() }
            _xrefsState.value = _xrefsState.value.copy(isLoading = false, data = xrefsData)
        }
    }
    
    fun dismissXrefs() {
        _xrefsState.value = _xrefsState.value.copy(visible = false)
    }

    // === Multi-select Operations ===

    private fun extendSelectionToFunction() {
        val state = _multiSelectState.value
        if (!state.active) {
            android.util.Log.d("DisasmMS", "extendToFunction: not active")
            return
        }
        viewModelScope.launch {
            val cmd = "afij @ ${state.startAddr}"
            android.util.Log.d("DisasmMS", "extendToFunction: cmd=$cmd")
            val output = R2PipeManager.executeJson(cmd).getOrDefault("[]")
            android.util.Log.d("DisasmMS", "extendToFunction: output=$output")
            try {
                val arr = JSONArray(output)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val funcStart = obj.getLong("addr")
                    val funcSize = obj.getLong("realsz")
                    val funcEnd = funcStart + funcSize
                    android.util.Log.d("DisasmMS", "extendToFunction: funcStart=0x${funcStart.toString(16)} size=$funcSize end=0x${funcEnd.toString(16)}")
                    val snapshot = disasmDataManager?.getSnapshot()
                    if (snapshot == null) {
                        android.util.Log.d("DisasmMS", "extendToFunction: snapshot is null")
                        return@launch
                    }
                    val inRange = snapshot.filter { it.addr in funcStart..<funcEnd }
                    android.util.Log.d("DisasmMS", "extendToFunction: ${inRange.size} instrs in range")
                    val lastAddr = inRange.lastOrNull()?.addr ?: (funcEnd - 1)
                    android.util.Log.d("DisasmMS", "extendToFunction: lastAddr=0x${lastAddr.toString(16)}")
                    _multiSelectState.value = state.copy(
                        startAddr = funcStart, endAddr = lastAddr
                    )
                } else {
                    android.util.Log.d("DisasmMS", "extendToFunction: afij returned empty array")
                }
            } catch (e: Exception) {
                android.util.Log.e("DisasmMS", "extendToFunction: error", e)
            }
        }
    }

    fun fillSelectedRange(value: String) {
        val state = _multiSelectState.value
        val session = R2PipeManager.activeSession() ?: return
        if (!state.active) return
        val start = state.rangeStart
        val end = state.rangeEnd
        viewModelScope.launch {
            // Calculate byte length from instructions in range
            val snapshot = disasmDataManager?.getSnapshot() ?: return@launch
            val instrsInRange = snapshot.filter { it.addr in start..end }
            val totalBytes = instrsInRange.sumOf { it.bytes.length / 2 }
            if (totalBytes <= 0) return@launch

            val clean = value.replace(" ", "")
            val isHex = clean.isNotEmpty() && clean.all { it in "0123456789abcdefABCDEF" }
            if (isHex) {
                val unitLen = (clean.length / 2).coerceAtLeast(1)
                val repeated = clean.repeat((totalBytes + unitLen - 1) / unitLen).take(totalBytes * 2)
                if (!session.writeViewerBytes(ViewerByteEdit.HEX, repeated, listOf(start))) return@launch
            } else if (session.writeCacheEnabled) {
                if (!session.writeViewerBytes(ViewerByteEdit.ASSEMBLY, value, instrsInRange.map { it.addr })) return@launch
            } else {
                for (instr in instrsInRange) {
                    R2PipeManager.execute(session, "wa $value @ ${instr.addr}")
                }
            }
            if (R2PipeManager.activeSessionPipe !== session.pipe) return@launch
            resetAndScrollTo(start)
            _dataModifiedEvent.value = System.currentTimeMillis()
            _multiSelectState.value = MultiSelectState()
        }
    }

    fun getSelectedInstructions(): List<top.wsdx233.r2droid.core.data.model.DisasmInstruction> {
        val state = _multiSelectState.value
        if (!state.active) return emptyList()
        val snapshot = disasmDataManager?.getSnapshot() ?: return emptyList()
        return snapshot.filter { it.addr in state.rangeStart..state.rangeEnd }
    }

    fun getSelectedCount(): Int {
        return getSelectedInstructions().size
    }

    // === Function Operations ===

    private fun analyzeFunction(addr: Long) {
        viewModelScope.launch {
            disasmRepository.analyzeFunction(addr)
            resetAndScrollTo(addr)
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }

    private fun fetchFunctionInfo(addr: Long) {
        _functionInfoState.value = FunctionInfoState(visible = true, isLoading = true, targetAddress = addr)
        viewModelScope.launch {
            val detail = disasmRepository.getFunctionDetail(addr).getOrNull()
            val ccs = disasmRepository.getAvailableCallingConventions().getOrDefault(emptyList())
            val typeSuggestions = disasmRepository.getAvailableTypeSuggestions()
                .getOrDefault(top.wsdx233.r2droid.feature.types.data.LocalTypesSnapshot.COMMON_C_TYPES)
            _functionInfoState.update {
                if (it.targetAddress != addr) it
                else it.copy(
                    isLoading = false,
                    isMutating = false,
                    data = detail,
                    availableCallingConventions = ccs,
                    typeSuggestions = typeSuggestions
                )
            }
        }
    }

    private fun dismissFunctionInfo() {
        _functionInfoState.value = _functionInfoState.value.copy(visible = false, errorMessage = null)
    }

    private fun renameFunctionFromInfo(addr: Long, newName: String) {
        _functionInfoState.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            val res = disasmRepository.renameFunction(addr, newName)
            if (res.isFailure) {
                _functionInfoState.update {
                    it.copy(isMutating = false, errorMessage = res.exceptionOrNull()?.message ?: "Rename failed")
                }
                return@launch
            }
            val updated = disasmRepository.getFunctionDetail(addr).getOrNull()
            _functionInfoState.update { it.copy(isMutating = false, data = updated ?: it.data, errorMessage = null) }
            resetAndScrollTo(addr)
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }

    private fun updateFunctionSignature(addr: Long, signature: String) {
        _functionInfoState.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            val res = disasmRepository.setFunctionSignature(addr, signature)
            if (res.isFailure) {
                _functionInfoState.update {
                    it.copy(isMutating = false, errorMessage = res.exceptionOrNull()?.message ?: "Signature update failed")
                }
                return@launch
            }
            val updated = disasmRepository.getFunctionDetail(addr).getOrNull()
            _functionInfoState.update { it.copy(isMutating = false, data = updated ?: it.data, errorMessage = null) }
            resetAndScrollTo(addr)
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }

    private fun updateFunctionCallingConvention(addr: Long, callingConvention: String) {
        _functionInfoState.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            val res = disasmRepository.setFunctionCallingConvention(addr, callingConvention)
            if (res.isFailure) {
                _functionInfoState.update {
                    it.copy(isMutating = false, errorMessage = res.exceptionOrNull()?.message ?: "Calling convention update failed")
                }
                return@launch
            }
            val updated = disasmRepository.getFunctionDetail(addr).getOrNull()
            _functionInfoState.update { it.copy(isMutating = false, data = updated ?: it.data, errorMessage = null) }
            resetAndScrollTo(addr)
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }

    private fun fetchFunctionXrefs(addr: Long) {
        _functionXrefsState.value = FunctionXrefsState(visible = true, isLoading = true, targetAddress = addr)
        viewModelScope.launch {
            val result = disasmRepository.getFunctionXrefs(addr)
            _functionXrefsState.value = _functionXrefsState.value.copy(
                isLoading = false, data = result.getOrDefault(emptyList())
            )
        }
    }

    private fun dismissFunctionXrefs() {
        _functionXrefsState.value = _functionXrefsState.value.copy(visible = false)
    }

    private fun fetchFunctionVariables(addr: Long) {
        _functionVariablesState.value = FunctionVariablesState(visible = true, isLoading = true, targetAddress = addr)
        viewModelScope.launch {
            val result = disasmRepository.getFunctionVariables(addr)
            val suggestions = disasmRepository.getAvailableTypeSuggestions()
                .getOrDefault(top.wsdx233.r2droid.feature.types.data.LocalTypesSnapshot.COMMON_C_TYPES)
            _functionVariablesState.update {
                if (it.targetAddress != addr) it
                else it.copy(
                    isLoading = false,
                    isMutating = false,
                    data = result.getOrDefault(FunctionVariablesData()),
                    typeSuggestions = suggestions
                )
            }
        }
    }

    private fun dismissFunctionVariables() {
        _functionVariablesState.value = _functionVariablesState.value.copy(visible = false, errorMessage = null)
    }

    private fun renameFunctionVariable(addr: Long, oldName: String, newName: String) {
        _functionVariablesState.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            val res = disasmRepository.renameFunctionVariable(addr, newName, oldName)
            if (res.isFailure) {
                _functionVariablesState.update {
                    it.copy(isMutating = false, errorMessage = res.exceptionOrNull()?.message ?: "Rename failed")
                }
                return@launch
            }
            val updated = disasmRepository.getFunctionVariables(addr).getOrDefault(FunctionVariablesData())
            _functionVariablesState.update { it.copy(isMutating = false, data = updated, errorMessage = null) }
            resetAndScrollTo(addr)
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }

    private fun retypeFunctionVariable(addr: Long, varName: String, newType: String) {
        _functionVariablesState.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            val res = disasmRepository.retypeFunctionVariable(addr, varName, newType)
            if (res.isFailure) {
                _functionVariablesState.update {
                    it.copy(isMutating = false, errorMessage = res.exceptionOrNull()?.message ?: "Retype failed")
                }
                return@launch
            }
            val updated = disasmRepository.getFunctionVariables(addr).getOrDefault(FunctionVariablesData())
            _functionVariablesState.update { it.copy(isMutating = false, data = updated, errorMessage = null) }
            resetAndScrollTo(addr)
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }

    // === Instruction Detail ===

    private fun fetchInstructionDetail(addr: Long) {
        _instructionDetailState.value = InstructionDetailState(
            visible = true, isLoading = true, targetAddress = addr
        )
        viewModelScope.launch {
            val result = disasmRepository.getInstructionDetail(addr)
            _instructionDetailState.value = _instructionDetailState.value.copy(
                isLoading = false, data = result.getOrNull()
            )
        }
    }

    private fun dismissInstructionDetail() {
        _instructionDetailState.value = _instructionDetailState.value.copy(visible = false)
    }

    private fun explainInstructionWithAi(addr: Long) {
        _instructionDetailState.value = _instructionDetailState.value.copy(
            visible = true,
            targetAddress = addr,
            aiExplanation = "",
            aiExplainLoading = true,
            aiExplainError = null
        )
        viewModelScope.launch {
            runCatching {
                val detail = _instructionDetailState.value.data
                    ?: disasmRepository.getInstructionDetail(addr).getOrNull()
                    ?: throw IllegalStateException("No instruction detail")

                val userPrompt = buildString {
                    appendLine("Please explain this assembly instruction in a concise reverse-engineering style.")
                    appendLine("Address: 0x${detail.addr.toString(16).uppercase()}")
                    appendLine("Opcode: ${detail.opcode}")
                    appendLine("Disasm: ${detail.disasm}")
                    appendLine("Pseudo: ${detail.pseudo}")
                    appendLine("Type/Family: ${detail.type} / ${detail.family}")
                    if (detail.jump != null) appendLine("Jump: 0x${detail.jump.toString(16).uppercase()}")
                    if (detail.fail != null) appendLine("Fail: 0x${detail.fail.toString(16).uppercase()}")
                    appendLine("ESIL: ${detail.esil}")
                    appendLine("Use markdown headings and concise bullet points.")
                    appendLine("Sections: Summary / Effects / RE Tips.")
                }
                requestAiText(
                    userPrompt = userPrompt,
                    systemPrompt = AiSettingsManager.instrExplainPrompt,
                    onDelta = { delta ->
                        appendInstructionExplainWithTypewriter(delta)
                    }
                )
            }.onSuccess { text ->
                _instructionDetailState.value = _instructionDetailState.value.copy(
                    aiExplainLoading = false,
                    aiExplanation = text.ifBlank { _instructionDetailState.value.aiExplanation },
                    aiExplainError = null
                )
            }.onFailure { throwable ->
                _instructionDetailState.value = _instructionDetailState.value.copy(
                    aiExplainLoading = false,
                    aiExplainError = throwable.message ?: "AI explain failed"
                )
            }
        }
    }

    private fun polishDisassemblyWithAi(addr: Long) {
        _aiPolishState.value = AiPolishState(
            visible = true,
            isLoading = true,
            targetAddress = addr,
            result = "",
            error = null
        )
        viewModelScope.launch {
            runCatching {
                val inFunction = isAddressInFunction(addr)
                val source = if (inFunction) {
                    R2PipeManager.execute("pdf @ $addr").getOrDefault("")
                } else {
                    R2PipeManager.execute("pd 120 @ $addr").getOrDefault("")
                }.trim().ifBlank {
                    throw IllegalStateException("No disassembly output for explanation")
                }

                val userPrompt = buildString {
                    appendLine("Explain the following disassembly in a reverse-engineering friendly way.")
                    appendLine("Target address: 0x${addr.toString(16).uppercase()}")
                    appendLine("Context source: ${if (inFunction) "pdf (function)" else "pd (linear disassembly)"}")
                    appendLine()
                    appendLine("Disassembly Source:")
                    appendLine(source)
                    appendLine("Requirements:")
                    appendLine("1) Explain key instructions, control flow, and intent.")
                    appendLine("2) Keep addresses and symbol names if present.")
                    appendLine("3) Add concise semantic comments where useful.")
                    appendLine("4) Use markdown headings and bullet points, no fenced code blocks.")
                }
                requestAiText(
                    userPrompt = userPrompt,
                    systemPrompt = AiSettingsManager.disasmPolishPrompt,
                    onDelta = { delta ->
                        appendAiPolishWithTypewriter(delta)
                    }
                )
            }.onSuccess { text ->
                _aiPolishState.value = _aiPolishState.value.copy(
                    isLoading = false,
                    result = text.ifBlank { _aiPolishState.value.result },
                    error = null
                )
            }.onFailure { throwable ->
                _aiPolishState.value = _aiPolishState.value.copy(
                    isLoading = false,
                    error = throwable.message ?: "AI polish failed"
                )
            }
        }
    }

    private fun dismissAiPolish() {
        _aiPolishState.value = _aiPolishState.value.copy(visible = false)
    }

    private suspend fun requestAiText(
        userPrompt: String,
        systemPrompt: String,
        onDelta: suspend (String) -> Unit = {}
    ): String {
        val config = AiSettingsManager.configFlow.value
        val provider = config.providers.find { it.id == config.activeProviderId }
            ?: throw IllegalStateException("No AI provider configured")
        val model = config.activeModelName ?: provider.models.firstOrNull()
            ?: throw IllegalStateException("No model selected")

        val finalPrompt = buildString {
            appendLine(userPrompt.trim())
            appendLine()
            appendLine(buildLanguagePromptInstruction())
        }

        val output = StringBuilder()
        aiRepository.streamText(
            provider = provider,
            messages = listOf(ChatMessage(role = ChatRole.User, content = finalPrompt)),
            modelName = model,
            systemPrompt = systemPrompt,
            thinkingLevel = ThinkingLevel.Light
        ).collect { chunk ->
            output.append(chunk)
            onDelta(chunk)
        }

        return output.toString().trim().ifBlank {
            throw IllegalStateException("AI returned empty response")
        }
    }

    private fun buildLanguagePromptInstruction(): String {
        val locale = Locale.getDefault()
        val tag = locale.toLanguageTag()
        val display = locale.getDisplayLanguage(locale).ifBlank { tag }
        return "Respond in the system language: $display ($tag)."
    }

    private suspend fun isAddressInFunction(addr: Long): Boolean {
        return runCatching {
            val output = R2PipeManager.executeJson("afij @ $addr").getOrDefault("[]")
            if (output.isBlank() || output == "[]") {
                false
            } else {
                JSONArray(output).length() > 0
            }
        }.getOrDefault(false)
    }

    private suspend fun appendInstructionExplainWithTypewriter(delta: String) {
        delta.forEach { ch ->
            _instructionDetailState.value = _instructionDetailState.value.copy(
                aiExplanation = _instructionDetailState.value.aiExplanation + ch
            )
            delay(8L)
        }
    }

    private suspend fun appendAiPolishWithTypewriter(delta: String) {
        delta.forEach { ch ->
            _aiPolishState.value = _aiPolishState.value.copy(
                result = _aiPolishState.value.result + ch
            )
            delay(8L)
        }
    }
}

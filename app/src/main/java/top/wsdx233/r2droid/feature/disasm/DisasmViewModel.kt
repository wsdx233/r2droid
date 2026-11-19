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
import kotlinx.coroutines.launch
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
import top.wsdx233.r2droid.feature.disasm.data.DisasmDataManager
import top.wsdx233.r2droid.feature.debug.data.DebugBackend
import top.wsdx233.r2droid.feature.debug.data.DebugCapabilities
import top.wsdx233.r2droid.feature.debug.data.DebugSessionConfig
import top.wsdx233.r2droid.feature.disasm.data.DisasmRepository
import top.wsdx233.r2droid.util.R2PipeManager
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
    data class WriteAsm(val address: Long, val asm: String) : DisasmEvent
    data class WriteHex(val address: Long, val hex: String) : DisasmEvent
    data class WriteString(val address: Long, val text: String) : DisasmEvent
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
    data class FetchFunctionXrefs(val address: Long) : DisasmEvent
    object DismissFunctionXrefs : DisasmEvent
    data class FetchFunctionVariables(val address: Long) : DisasmEvent
    object DismissFunctionVariables : DisasmEvent
    data class RenameFunctionVariable(val address: Long, val oldName: String, val newName: String) : DisasmEvent
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
        if (_debugStatus.value == DebugStatus.IDLE || _debugStatus.value == DebugStatus.ERROR) {
            val sanitized = sanitizeDebugBackendForCurrentSession(backend)
            _debugBackend.value = sanitized
            _debugCapabilities.value = DebugCapabilities()
            if (backend != sanitized) {
                _debugError.value = backendUnavailableMessage(backend)
                _debugStatus.value = DebugStatus.ERROR
                return
            }
            if (_debugStatus.value == DebugStatus.ERROR) {
                _debugError.value = null
                _debugStatus.value = DebugStatus.IDLE
            }
        }
    }

    private val _debugCapabilities = MutableStateFlow(DebugCapabilities())
    val debugCapabilities: StateFlow<DebugCapabilities> = _debugCapabilities.asStateFlow()

    // 当前 PC (Program Counter) 地址
    private val _pcAddress = MutableStateFlow<Long?>(null)
    val pcAddress: StateFlow<Long?> = _pcAddress.asStateFlow()

    // 断点集合
    private val _breakpoints = MutableStateFlow<Set<Long>>(emptySet())
    val breakpoints: StateFlow<Set<Long>> = _breakpoints.asStateFlow()

    // 寄存器状态
    private val _registers = MutableStateFlow<org.json.JSONObject>(org.json.JSONObject())
    val registers: StateFlow<org.json.JSONObject> = _registers.asStateFlow()

    private val _debugStackState = MutableStateFlow(DebugStackState())
    val debugStackState: StateFlow<DebugStackState> = _debugStackState.asStateFlow()

    private val _debugMemoryState = MutableStateFlow(DebugMemoryState())
    val debugMemoryState: StateFlow<DebugMemoryState> = _debugMemoryState.asStateFlow()

    private val _debugTraceEntries = MutableStateFlow<List<top.wsdx233.r2droid.feature.debug.data.DebugTraceEntry>>(emptyList())
    val debugTraceEntries: StateFlow<List<top.wsdx233.r2droid.feature.debug.data.DebugTraceEntry>> = _debugTraceEntries.asStateFlow()

    private val _debugLaunchConfig = MutableStateFlow(DebugLaunchConfigState())
    val debugLaunchConfig: StateFlow<DebugLaunchConfigState> = _debugLaunchConfig.asStateFlow()

    private var lastTraceRegisters: Map<String, Long> = emptyMap()
    private var traceIndex = 0

    // 调试器状态
    private val _debugStatus = MutableStateFlow(DebugStatus.IDLE)
    val debugStatus: StateFlow<DebugStatus> = _debugStatus.asStateFlow()

    private val _debugError = MutableStateFlow<String?>(null)
    val debugError: StateFlow<String?> = _debugError.asStateFlow()

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
        _breakpoints.value = emptySet()
        _registers.value = org.json.JSONObject()
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
            is DisasmEvent.WriteAsm -> writeAsm(event.address, event.asm)
            is DisasmEvent.WriteHex -> writeHex(event.address, event.hex)
            is DisasmEvent.WriteString -> writeString(event.address, event.text)
            is DisasmEvent.WriteComment -> writeComment(event.address, event.comment)
            is DisasmEvent.RefreshData -> refreshData()
            is DisasmEvent.Reset -> reset()
            is DisasmEvent.FetchXrefs -> fetchXrefs(event.address)
            is DisasmEvent.DismissXrefs -> dismissXrefs()
            is DisasmEvent.AnalyzeFunction -> analyzeFunction(event.address)
            is DisasmEvent.FetchFunctionInfo -> fetchFunctionInfo(event.address)
            is DisasmEvent.DismissFunctionInfo -> dismissFunctionInfo()
            is DisasmEvent.RenameFunctionFromInfo -> renameFunctionFromInfo(event.address, event.newName)
            is DisasmEvent.FetchFunctionXrefs -> fetchFunctionXrefs(event.address)
            is DisasmEvent.DismissFunctionXrefs -> dismissFunctionXrefs()
            is DisasmEvent.FetchFunctionVariables -> fetchFunctionVariables(event.address)
            is DisasmEvent.DismissFunctionVariables -> dismissFunctionVariables()
            is DisasmEvent.RenameFunctionVariable -> renameFunctionVariable(event.address, event.oldName, event.newName)
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
        _scrollTarget.value = null
        _multiSelectState.value = MultiSelectState()
        clearDebugState()
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

    // 开始调试（根据当前后端自动选择 ESIL / Native / Frida）
    fun startDebugging() {
        if (_debugStatus.value == DebugStatus.STARTING || _debugStatus.value == DebugStatus.RUNNING) return

        viewModelScope.launch {
            val config = buildDebugSessionConfig().getOrElse {
                setDebugError(it)
                return@launch
            }
            _debugStatus.value = DebugStatus.STARTING
            _debugError.value = null
            _debugCapabilities.value = debuggerRepository.probe(config.backend).getOrDefault(DebugCapabilities())
            val result = debuggerRepository.start(config)
            if (result.isSuccess) {
                updateDebugState()
            } else {
                setDebugError(result.exceptionOrNull())
            }
        }
    }

    // 兼容旧调用
    fun initEsil() = startDebugging()

    fun stopDebugging() {
        if (_debugStatus.value == DebugStatus.RUNNING || _debugStatus.value == DebugStatus.STARTING) return
        if (!ensureDebugBackendMatchesCurrentSession()) return

        viewModelScope.launch {
            _debugStatus.value = DebugStatus.STOPPING
            val result = debuggerRepository.stopDebugging(_debugBackend.value)
            if (result.isSuccess) {
                clearDebugState()
            } else {
                setDebugError(result.exceptionOrNull())
            }
        }
    }

    // 切换断点 (本地管理状态，不依赖R2缓存)
    fun toggleBreakpoint(addr: Long) {
        if (!ensureDebugBackendMatchesCurrentSession()) return
        val before = _breakpoints.value
        val isAdd = !before.contains(addr)

        _breakpoints.value = if (isAdd) before + addr else before - addr
        _disasmCacheVersion.update { it + 1 }

        viewModelScope.launch {
            val result = debuggerRepository.toggleBreakpoint(addr, isAdd)
            if (result.isFailure) {
                _breakpoints.value = before
                _disasmCacheVersion.update { it + 1 }
                setDebugError(result.exceptionOrNull())
            } else {
                debuggerRepository.getBreakpoints().getOrNull()?.let { _breakpoints.value = it }
                _debugError.value = null
            }
        }
    }

    fun runToCursor(addr: Long) {
        if (_debugStatus.value == DebugStatus.RUNNING || _debugStatus.value == DebugStatus.STARTING) return
        if (!ensureDebugBackendMatchesCurrentSession()) return
        viewModelScope.launch {
            val previousStatus = _debugStatus.value
            _debugStatus.value = DebugStatus.RUNNING
            _debugError.value = null
            val result = debuggerRepository.runToCursor(_debugBackend.value, addr)
            if (result.isSuccess) {
                updateDebugState("run-to-cursor")
            } else {
                _debugStatus.value = previousStatus
                setDebugError(result.exceptionOrNull())
            }
        }
    }

    fun setDebugPc(addr: Long) {
        if (_debugStatus.value == DebugStatus.RUNNING || _debugStatus.value == DebugStatus.STARTING) return
        if (!ensureDebugBackendMatchesCurrentSession()) return
        viewModelScope.launch {
            val result = debuggerRepository.setProgramCounter(_debugBackend.value, addr)
            if (result.isSuccess) {
                updateDebugState("set-pc")
            } else {
                setDebugError(result.exceptionOrNull())
            }
        }
    }

    fun resetDebugging(startAddress: Long? = _pcAddress.value) {
        if (_debugStatus.value == DebugStatus.RUNNING || _debugStatus.value == DebugStatus.STARTING) return
        viewModelScope.launch {
            val config = buildDebugSessionConfig().getOrElse {
                setDebugError(it)
                return@launch
            }.copy(startAddress = startAddress)
            _debugStatus.value = DebugStatus.STARTING
            _debugError.value = null
            _debugCapabilities.value = debuggerRepository.probe(config.backend).getOrDefault(_debugCapabilities.value)
            val result = debuggerRepository.reset(config)
            if (result.isSuccess) {
                clearDebugTrace()
                updateDebugState("reset")
            } else {
                setDebugError(result.exceptionOrNull())
            }
        }
    }

    fun refreshDebugStack() {
        if (_debugStatus.value == DebugStatus.IDLE || _debugStatus.value == DebugStatus.STARTING) return
        if (!ensureDebugBackendMatchesCurrentSession()) return
        viewModelScope.launch {
            _debugStackState.value = _debugStackState.value.copy(isLoading = true)
            val entries = debuggerRepository.getStack(_debugBackend.value, 16).getOrDefault(emptyList())
            val sp = debuggerRepository.getStackPointer(_debugBackend.value).getOrNull()
            _debugStackState.value = DebugStackState(entries = entries, stackPointer = sp, isLoading = false)
        }
    }

    fun readDebugMemory(address: Long, size: Int = 128) {
        if (_debugStatus.value == DebugStatus.IDLE || _debugStatus.value == DebugStatus.STARTING) return
        if (!ensureDebugBackendMatchesCurrentSession()) return
        viewModelScope.launch {
            _debugMemoryState.value = _debugMemoryState.value.copy(address = address, isLoading = true, error = null)
            val result = debuggerRepository.readMemory(_debugBackend.value, address, size)
            _debugMemoryState.value = result.fold(
                onSuccess = { bytes -> DebugMemoryState(address = address, bytes = bytes, isLoading = false) },
                onFailure = { error -> DebugMemoryState(address = address, isLoading = false, error = error.message) }
            )
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
        lastTraceRegisters = currentRegisterMap()
        traceIndex = 0
    }

    private fun currentRegisterMap(): Map<String, Long> {
        val json = _registers.value
        val map = mutableMapOf<String, Long>()
        json.keys().forEach { key ->
            when (val value = json.opt(key)) {
                is Number -> map[key] = value.toLong()
                is String -> value.removePrefix("0x").removePrefix("0X").toLongOrNull(16)?.let { map[key] = it }
            }
        }
        return map
    }

    private fun appendDebugTrace(action: String, pc: Long?, reason: String? = null) {
        val current = currentRegisterMap()
        val changed = current.mapNotNull { (name, value) ->
            val previous = lastTraceRegisters[name]
            if (previous != null && previous != value) name to (previous to value) else null
        }.toMap()
        lastTraceRegisters = current
        val tracePc = pc ?: _pcAddress.value ?: return
        traceIndex += 1
        val next = (_debugTraceEntries.value + top.wsdx233.r2droid.feature.debug.data.DebugTraceEntry(
            index = traceIndex,
            pc = tracePc,
            action = action,
            changedRegisters = changed,
            reason = reason
        )).takeLast(200)
        _debugTraceEntries.value = next
    }

    // 调试操作 (Step / Continue)
    fun performDebugAction(action: String) {
        if (_debugStatus.value == DebugStatus.RUNNING || _debugStatus.value == DebugStatus.STARTING) return
        if (!ensureDebugBackendMatchesCurrentSession()) return

        viewModelScope.launch {
            val previousStatus = _debugStatus.value
            _debugStatus.value = DebugStatus.RUNNING
            _debugError.value = null

            val result = when (action) {
                "step" -> debuggerRepository.stepInto(_debugBackend.value)
                "over" -> debuggerRepository.stepOver(_debugBackend.value)
                "continue" -> debuggerRepository.continueExecution(_debugBackend.value)
                else -> Result.failure(IllegalArgumentException("Unknown debug action: $action"))
            }

            if (result.isSuccess) {
                // 阻塞命令返回后，更新状态
                updateDebugState(action)
            } else {
                _debugStatus.value = previousStatus
                setDebugError(result.exceptionOrNull())
            }
        }
    }

    // 暂停执行
    fun pauseExecution() {
        if (!ensureDebugBackendMatchesCurrentSession()) return
        // 利用已有的 interrupt 发送 SIGINT 给 R2 进程，强行打断 dc/aec 的阻塞
        R2PipeManager.interrupt()
        viewModelScope.launch {
            kotlinx.coroutines.delay(150L)
            val result = debuggerRepository.pause()
            if (result.isSuccess) {
                updateDebugState("pause")
            } else {
                setDebugError(result.exceptionOrNull())
            }
        }
    }

    // 获取 PC 和 寄存器更新 UI，并自动滚动到 PC 位置
    suspend fun updateDebugState(traceAction: String? = null) {
        if (!ensureDebugBackendMatchesCurrentSession()) return
        val backend = _debugBackend.value
        val pcResult = debuggerRepository.getCurrentPC(backend)
        val regsResult = debuggerRepository.getRegisters(backend)
        val pc = pcResult.getOrNull()
        val regs = regsResult.getOrNull()

        if (pc == null && regs == null) {
            setDebugError(pcResult.exceptionOrNull() ?: regsResult.exceptionOrNull())
            return
        }

        _pcAddress.value = pc
        _debugStatus.value = DebugStatus.SUSPENDED
        _debugError.value = null

        _registers.value = regs ?: org.json.JSONObject()
        debuggerRepository.getBreakpoints().getOrNull()?.let { _breakpoints.value = it }
        refreshDebugStack()
        pc?.let { readDebugMemory(it) }

        if (traceAction != null) {
            appendDebugTrace(traceAction, pc)
        } else if (lastTraceRegisters.isEmpty()) {
            lastTraceRegisters = currentRegisterMap()
        }

        // 自动让反汇编视图滚动到 PC 位置
        if (pc != null) {
            loadAndScrollTo(pc)
        }
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
        if (session !== managerSession) {
            reset()
            managerSession = session
        }
        if (disasmDataManager != null) {
            preloadDisasmAround(currentOffset)
            return
        }

        val manager = DisasmDataManager(
            layout.startAddress, layout.endAddress,
            { offset, count -> disasmRepository.getDisassembly(active, offset, count) },
            addressOverview = layout.overview
        )
        manager.onChunkLoaded = {
            if (disasmDataManager === manager) _disasmCacheVersion.update { it + 1 }
        }
        sessionManager = manager
        _disasmDataManagerState.value = manager
        initialLoadJob = viewModelScope.launch {
            if (disasmDataManager !== manager) return@launch
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

    fun writeAsm(addr: Long, asm: String) {
        viewModelScope.launch {
            // "wa [asm] @ [addr]"
            disasmRepository.writeAsm(addr, asm)

            resetAndScrollTo(addr)

            // Notify others
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }
    
    fun writeHex(addr: Long, hex: String) {
        viewModelScope.launch {
            // "wx [hex] @ [addr]"
            top.wsdx233.r2droid.util.R2PipeManager.execute("wx $hex @ $addr")

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

    fun writeString(addr: Long, text: String) {
        viewModelScope.launch {
            // "w [text] @ [addr]"
            val escaped = text.replace("\"", "\\\"")
            top.wsdx233.r2droid.util.R2PipeManager.execute("w \"$escaped\" @ $addr")

            resetAndScrollTo(addr)

            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }
    
    /**
     * Called when other modules modify data
     */
    fun refreshData() {
        val manager = disasmDataManager ?: return
        val currentAddr = manager.getSnapshot().let { snapshot ->
            if (snapshot.isNotEmpty()) snapshot[snapshot.size / 2].addr
            else manager.viewStartAddress
        }
        viewModelScope.launch {
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
                R2PipeManager.execute("wx $repeated @ $start")
            } else {
                // Treat as assembly opcode - write at each instruction
                for (instr in instrsInRange) {
                    R2PipeManager.execute("wa $value @ ${instr.addr}")
                }
            }
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
            val result = disasmRepository.getFunctionDetail(addr)
            _functionInfoState.value = _functionInfoState.value.copy(
                isLoading = false, data = result.getOrNull()
            )
        }
    }

    private fun dismissFunctionInfo() {
        _functionInfoState.value = _functionInfoState.value.copy(visible = false)
    }

    private fun renameFunctionFromInfo(addr: Long, newName: String) {
        viewModelScope.launch {
            disasmRepository.renameFunction(addr, newName)
            // Refresh the function info dialog with updated data
            fetchFunctionInfo(addr)
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
            _functionVariablesState.value = _functionVariablesState.value.copy(
                isLoading = false, data = result.getOrDefault(FunctionVariablesData())
            )
        }
    }

    private fun dismissFunctionVariables() {
        _functionVariablesState.value = _functionVariablesState.value.copy(visible = false)
    }

    private fun renameFunctionVariable(addr: Long, oldName: String, newName: String) {
        viewModelScope.launch {
            disasmRepository.renameFunctionVariable(addr, newName, oldName)
            // Refresh the variables dialog
            fetchFunctionVariables(addr)
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

        aiRepository.configure(provider)

        val finalPrompt = buildString {
            appendLine(userPrompt.trim())
            appendLine()
            appendLine(buildLanguagePromptInstruction())
        }

        val output = StringBuilder()
        aiRepository.streamChat(
            messages = listOf(ChatMessage(role = ChatRole.User, content = finalPrompt)),
            modelName = model,
            systemPrompt = systemPrompt,
            useResponsesApi = provider.useResponsesApi,
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

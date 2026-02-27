package top.wsdx233.r2droid.feature.disasm


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import top.wsdx233.r2droid.core.data.model.FunctionDetailInfo
import top.wsdx233.r2droid.core.data.model.FunctionVariablesData
import top.wsdx233.r2droid.core.data.model.FunctionXref
import top.wsdx233.r2droid.core.data.model.InstructionDetail
import top.wsdx233.r2droid.core.data.model.Section
import top.wsdx233.r2droid.core.data.model.XrefsData
import top.wsdx233.r2droid.feature.ai.data.AiRepository
import top.wsdx233.r2droid.feature.ai.data.AiSettingsManager
import top.wsdx233.r2droid.feature.ai.data.ChatMessage
import top.wsdx233.r2droid.feature.ai.data.ChatRole
import top.wsdx233.r2droid.feature.ai.data.ThinkingLevel
import top.wsdx233.r2droid.feature.disasm.data.DisasmDataManager
import top.wsdx233.r2droid.feature.disasm.data.DisasmRepository
import top.wsdx233.r2droid.util.R2PipeManager
import java.util.Locale
import javax.inject.Inject


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

enum class DebugStatus { IDLE, SUSPENDED, RUNNING }

/**
 * ViewModel for Disassembly Viewer.
 * Manages DisasmDataManager and disasm-related interactions.
 */

sealed interface DisasmEvent {
    data class LoadDisassembly(val sections: List<Section>, val currentFilePath: String?, val currentOffset: Long) : DisasmEvent
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
    var disasmDataManager: DisasmDataManager? = null
        private set

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
    // Pair of (targetAddress, index) - UI observes this to scroll after data is ready
    private val _scrollTarget = MutableStateFlow<Pair<Long, Int>?>(null)
    val scrollTarget: StateFlow<Pair<Long, Int>?> = _scrollTarget.asStateFlow()

    // Event to notify that data has been modified
    private val _dataModifiedEvent = MutableStateFlow(0L)
    val dataModifiedEvent: StateFlow<Long> = _dataModifiedEvent.asStateFlow()

    // 调试后端模式
    private val _debugBackend = MutableStateFlow(top.wsdx233.r2droid.feature.debug.data.DebugBackend.ESIL)
    val debugBackend: StateFlow<top.wsdx233.r2droid.feature.debug.data.DebugBackend> = _debugBackend.asStateFlow()

    fun setDebugBackend(backend: top.wsdx233.r2droid.feature.debug.data.DebugBackend) {
        _debugBackend.value = backend
    }

    // 当前 PC (Program Counter) 地址
    private val _pcAddress = MutableStateFlow<Long?>(null)
    val pcAddress: StateFlow<Long?> = _pcAddress.asStateFlow()

    // 断点集合
    private val _breakpoints = MutableStateFlow<Set<Long>>(emptySet())
    val breakpoints: StateFlow<Set<Long>> = _breakpoints.asStateFlow()

    // 寄存器状态
    private val _registers = MutableStateFlow<org.json.JSONObject>(org.json.JSONObject())
    val registers: StateFlow<org.json.JSONObject> = _registers.asStateFlow()

    // 调试器状态
    private val _debugStatus = MutableStateFlow(DebugStatus.IDLE)
    val debugStatus: StateFlow<DebugStatus> = _debugStatus.asStateFlow()

    fun onEvent(event: DisasmEvent) {
        when (event) {
            is DisasmEvent.LoadDisassembly -> loadDisassembly(event.sections, event.currentFilePath, event.currentOffset)
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

    // 当前数据管理器对应的会话 ID，用于检测会话变更
    private var currentSessionId: Int = -1

    /**
     * 重置所有数据，用于切换项目时清理旧数据。
     */
    fun reset() {
        disasmDataManager = null
        _disasmDataManagerState.value = null
        _disasmCacheVersion.value = 0
        _scrollTarget.value = null
        _multiSelectState.value = MultiSelectState()
        currentSessionId = -1
    }

    // Job for scroll-related loading - cancelled on each new scroll request
    private var scrollJob: Job? = null
    // Job for background preloading - cancelled on far jumps to prevent stale-address loads
    private var preloadJob: Job? = null

    /**
     * Load data at target address, then emit scroll target with correct index.
     * Cancels any previous scroll job to handle rapid scrollbar dragging.
     */
    fun loadAndScrollTo(addr: Long) {
        val manager = disasmDataManager ?: return
        scrollJob?.cancel()
        scrollJob = viewModelScope.launch {
            val index = manager.loadAndFindIndex(addr)
            _disasmCacheVersion.value++
            _scrollTarget.value = Pair(addr, index)
        }
    }

    /**
     * Jump to a distant address via scrollbar drag or explicit jump.
     * Cancels in-flight preloads and increments generation to discard stale loads.
     */
    fun scrollbarJumpTo(addr: Long) {
        val manager = disasmDataManager ?: return
        scrollJob?.cancel()
        preloadJob?.cancel()   // kill stale-address preloads
        scrollJob = viewModelScope.launch {
            manager.prepareForJump()
            val index = manager.loadAndFindIndex(addr)
            _disasmCacheVersion.value++
            _scrollTarget.value = Pair(addr, index)
        }
    }

    // 初始化 ESIL 环境
    fun initEsil() {
        viewModelScope.launch {
            R2PipeManager.execute("aei; aeim; aeip") // 初始化 ESIL 和内存，并设置当前 PC
            _debugBackend.value = top.wsdx233.r2droid.feature.debug.data.DebugBackend.ESIL
            updateDebugState()
        }
    }

    // 切换断点 (本地管理状态，不依赖R2缓存)
    fun toggleBreakpoint(addr: Long) {
        val currentBreakpoints = _breakpoints.value.toMutableSet()
        val isAdd = !currentBreakpoints.contains(addr)
        
        if (isAdd) currentBreakpoints.add(addr) else currentBreakpoints.remove(addr)
        _breakpoints.value = currentBreakpoints
        // Trigger UI update locally
        _disasmCacheVersion.value++

        viewModelScope.launch {
            debuggerRepository.toggleBreakpoint(addr, isAdd) // 发送实际指令到 r2
        }
    }

    // 调试操作 (Step / Continue)
    fun performDebugAction(action: String) {
        viewModelScope.launch {
            _debugStatus.value = DebugStatus.RUNNING
            
            when (action) {
                "step" -> debuggerRepository.stepInto(_debugBackend.value)
                "over" -> debuggerRepository.stepOver(_debugBackend.value)
                "continue" -> debuggerRepository.continueExecution(_debugBackend.value)
            }
            
            // 阻塞命令返回后，更新状态
            updateDebugState()
        }
    }

    // 暂停执行
    fun pauseExecution() {
        // 利用已有的 interrupt 发送 SIGINT 给 R2 进程，强行打断 dc 的阻塞
        R2PipeManager.interrupt()
    }

    // 获取 PC 和 寄存器更新 UI，并自动滚动到 PC 位置
    suspend fun updateDebugState() {
        val pc = debuggerRepository.getCurrentPC().getOrNull()
        _pcAddress.value = pc
        _debugStatus.value = DebugStatus.SUSPENDED
        
        val regs = debuggerRepository.getRegisters().getOrDefault(org.json.JSONObject())
        _registers.value = regs

        // 自动让反汇编视图滚动到 PC 位置
        if (pc != null) {
            loadAndScrollTo(pc)
        }
    }

    fun clearScrollTarget() {
        _scrollTarget.value = null
    }

    /**
     * Reset disasm data around the given address and emit scroll target to return to it.
     * Used after data-modifying operations (analyze, rename, write) to keep the view
     * at the affected address after refresh.
     */
    private suspend fun resetAndScrollTo(addr: Long) {
        val manager = disasmDataManager ?: return
        manager.resetAndLoadAround(addr)
        val index = manager.findClosestIndex(addr)
        _disasmCacheVersion.value++
        _scrollTarget.value = Pair(addr, index)
    }

    /**
     * Initialize disassembly viewer with virtualization.
     * Uses Section info to calculate virtual address range.
     */
    fun loadDisassembly(sections: List<Section>, currentFilePath: String?, currentOffset: Long) {
        // 检测会话变更，如果是新项目则重置旧数据
        val newSessionId = R2PipeManager.sessionId
        if (newSessionId != currentSessionId) {
            reset()
            currentSessionId = newSessionId
        }
        if (disasmDataManager != null) {
            // Already initialized, just preload around current cursor
            viewModelScope.launch {
                disasmDataManager?.preloadAround(currentOffset, 2)
            }
            return
        }

        viewModelScope.launch {
            var startAddress = 0L
            var endAddress = 0L

            if (sections.isNotEmpty()) {
                // Filter out non-mapped sections (vAddr=0 are typically debug/metadata sections)
                val mappedSections = sections.filter { it.vAddr != 0L }
                // For disassembly, prefer executable sections (containing 'x' in perm)
                val execSections = mappedSections.filter { it.perm.contains("x") }

                if (execSections.isNotEmpty()) {
                    // Use executable sections range
                    startAddress = execSections.minOf { it.vAddr }
                    endAddress = execSections.maxOf { it.vAddr + maxOf(it.vSize, it.size) }
                } else if (mappedSections.isNotEmpty()) {
                    // Fallback to all mapped sections
                    startAddress = mappedSections.minOf { it.vAddr }
                    endAddress = mappedSections.maxOf { it.vAddr + maxOf(it.vSize, it.size) }
                }
            }

            // r2frida mode: use :dmj for address boundaries
            if (endAddress <= startAddress && R2PipeManager.isR2FridaSession) {
                try {
                    val raw = R2PipeManager.execute(":dmj").getOrNull()?.trim() ?: ""
                    val idx = raw.indexOfFirst { it == '[' }
                    val json = if (idx > 0) raw.substring(idx) else raw
                    if (json.startsWith("[")) {
                        val arr = org.json.JSONArray(json)
                        var minAddr = Long.MAX_VALUE
                        var maxAddr = 0L
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val base = java.lang.Long.decode(obj.optString("base", "0"))
                            val size = obj.optLong("size", 0)
                            if (base > 0 && size > 0) {
                                minAddr = minOf(minAddr, base)
                                maxAddr = maxOf(maxAddr, base + size)
                            }
                        }
                        if (maxAddr > minAddr && minAddr != Long.MAX_VALUE) {
                            startAddress = minAddr
                            endAddress = maxAddr
                        }
                    }
                } catch (_: Exception) {}
            }

            // Fallback if sections are empty or invalid
            if (endAddress <= startAddress) {
                // Try Java File API as fallback (file offset based)
                currentFilePath?.let { path ->
                    try {
                        val file = java.io.File(path)
                        if (file.exists() && file.isFile) {
                            startAddress = 0L
                            endAddress = file.length()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Final fallback - use a reasonable default range
            if (endAddress <= startAddress) {
                startAddress = 0L
                endAddress = 1024L * 1024L // 1MB default
            }

            // Create DisasmDataManager with virtual address range
            val manager = DisasmDataManager(startAddress, endAddress, disasmRepository).apply {
                onChunkLoaded = { _ ->
                    // Increment version to trigger recomposition
                    _disasmCacheVersion.value++
                }
            }
            disasmDataManager = manager
            _disasmDataManagerState.value = manager

            // Load initial data around cursor
            disasmDataManager?.resetAndLoadAround(currentOffset)
            
            _disasmCacheVersion.value++
        }
    }

    /**
     * Load a disasm chunk for a specific address (called from UI during scroll).
     */
    fun loadDisasmChunkForAddress(addr: Long) {
        val manager = disasmDataManager ?: return
        viewModelScope.launch {
            manager.loadChunkAroundAddress(addr)
        }
    }

    /**
     * Preload disasm chunks around an address (called when user scrolls quickly).
     */
    fun preloadDisasmAround(addr: Long) {
        val manager = disasmDataManager ?: return
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            manager.preloadAround(addr, 2)
        }
    }

    /**
     * Load more disasm instructions (forward or backward).
     */
    fun loadDisasmMore(forward: Boolean) {
        val manager = disasmDataManager ?: return
        viewModelScope.launch {
            manager.loadMore(forward)
        }
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

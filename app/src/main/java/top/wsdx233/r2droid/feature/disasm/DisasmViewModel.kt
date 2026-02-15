package top.wsdx233.r2droid.feature.disasm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.core.data.model.FunctionDetailInfo
import top.wsdx233.r2droid.core.data.model.FunctionVariablesData
import top.wsdx233.r2droid.core.data.model.FunctionXref
import top.wsdx233.r2droid.core.data.model.InstructionDetail
import top.wsdx233.r2droid.core.data.model.Section
import top.wsdx233.r2droid.core.data.model.Xref
import top.wsdx233.r2droid.core.data.model.XrefsData
import top.wsdx233.r2droid.core.data.source.R2PipeDataSource
import top.wsdx233.r2droid.feature.disasm.data.DisasmDataManager
import top.wsdx233.r2droid.feature.disasm.data.DisasmRepository
import top.wsdx233.r2droid.util.R2PipeManager


import kotlinx.coroutines.Job

import dagger.hilt.android.lifecycle.HiltViewModel
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
    val targetAddress: Long = 0L
)

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
    object DismissInstructionDetail : DisasmEvent
}

@HiltViewModel
class DisasmViewModel @Inject constructor(
    private val disasmRepository: DisasmRepository
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

    // Scroll target: emitted after data is loaded at target address
    // Pair of (targetAddress, index) - UI observes this to scroll after data is ready
    private val _scrollTarget = MutableStateFlow<Pair<Long, Int>?>(null)
    val scrollTarget: StateFlow<Pair<Long, Int>?> = _scrollTarget.asStateFlow()

    // Event to notify that data has been modified
    private val _dataModifiedEvent = MutableStateFlow(0L)
    val dataModifiedEvent: StateFlow<Long> = _dataModifiedEvent.asStateFlow()

    fun onEvent(event: DisasmEvent) {
        when (event) {
            is DisasmEvent.LoadDisassembly -> loadDisassembly(event.sections, event.currentFilePath, event.currentOffset)
            is DisasmEvent.LoadChunk -> loadDisasmChunkForAddress(event.address)
            is DisasmEvent.Preload -> preloadDisasmAround(event.address)
            is DisasmEvent.LoadMore -> loadDisasmMore(event.forward)
            is DisasmEvent.WriteAsm -> writeAsm(event.address, event.asm)
            is DisasmEvent.WriteHex -> writeHex(event.address, event.hex)
            is DisasmEvent.WriteString -> writeString(event.address, event.text)
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
            is DisasmEvent.DismissInstructionDetail -> dismissInstructionDetail()
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
        currentSessionId = -1
    }

    // Job for scroll-related loading - cancelled on each new scroll request
    private var scrollJob: Job? = null

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
                // For disassembly, prefer executable sections (containing 'x' in perm)
                val execSections = sections.filter { it.perm.contains("x") }

                if (execSections.isNotEmpty()) {
                    // Use executable sections range
                    startAddress = execSections.minOf { it.vAddr }
                    endAddress = execSections.maxOf { it.vAddr + maxOf(it.vSize, it.size) }
                } else {
                    // Fallback to all sections
                    startAddress = sections.minOf { it.vAddr }
                    endAddress = sections.maxOf { it.vAddr + maxOf(it.vSize, it.size) }
                }
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
        viewModelScope.launch {
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
}

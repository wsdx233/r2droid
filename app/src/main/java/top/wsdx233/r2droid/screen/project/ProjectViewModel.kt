package top.wsdx233.r2droid.screen.project

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.data.DisasmDataManager
import top.wsdx233.r2droid.data.HexDataManager
import top.wsdx233.r2droid.data.model.*
import top.wsdx233.r2droid.data.repository.ProjectRepository
import top.wsdx233.r2droid.util.R2PipeManager

sealed class ProjectUiState {
    object Idle : ProjectUiState()
    data class Configuring(val filePath: String) : ProjectUiState()
    object Analyzing : ProjectUiState()
    object Loading : ProjectUiState()
    data class Success(
        val binInfo: BinInfo? = null,
        val sections: List<Section>? = null,
        val symbols: List<Symbol>? = null,
        val imports: List<ImportInfo>? = null,
        val relocations: List<Relocation>? = null,
        val strings: List<StringInfo>? = null,
        val functions: List<FunctionInfo>? = null,
        val hexReady: Boolean = false, // Indicates hex viewer is ready (virtualized)
        val disasmReady: Boolean = false, // Indicates disasm viewer is ready (virtualized)
        val disassembly: List<DisasmInstruction>? = null,
        val decompilation: DecompilationData? = null,
        val cursorAddress: Long = 0L
    ) : ProjectUiState()
    data class Error(val message: String) : ProjectUiState()
}

data class XrefsState(
    val visible: Boolean = false,
    val data: List<Xref> = emptyList()
)

class ProjectViewModel : ViewModel() {
    private val repository = ProjectRepository()

    private val _uiState = MutableStateFlow<ProjectUiState>(ProjectUiState.Idle)
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    // Expose logs from LogManager
    val logs: StateFlow<List<top.wsdx233.r2droid.util.LogEntry>> = top.wsdx233.r2droid.util.LogManager.logs
    
    // Xrefs State
//    private val _xrefsState = MutableStateFlow(XrefsState())
//    val xrefsState: StateFlow<XrefsState> = _xrefsState.asStateFlow()
    
    // === Hex Virtualization ===
    // HexDataManager for virtualized hex viewing
    var hexDataManager: HexDataManager? = null
        private set
    
    // Cache version counter - increment to trigger UI recomposition when chunks load
    private val _hexCacheVersion = MutableStateFlow(0)
    val hexCacheVersion: StateFlow<Int> = _hexCacheVersion.asStateFlow()
    
    // === Disasm Virtualization ===
    // DisasmDataManager for virtualized disassembly viewing
    var disasmDataManager: DisasmDataManager? = null
        private set
    
    // Cache version counter for disasm - increment to trigger UI recomposition when chunks load
    private val _disasmCacheVersion = MutableStateFlow(0)
    val disasmCacheVersion: StateFlow<Int> = _disasmCacheVersion.asStateFlow()
    
    // Scroll to selection trigger - increment to trigger scroll to current cursor position
    private val _scrollToSelectionTrigger = MutableStateFlow(0)
    val scrollToSelectionTrigger: StateFlow<Int> = _scrollToSelectionTrigger.asStateFlow()
    
    /**
     * Request the active viewer to scroll to the current cursor position (centered).
     * Called from TopAppBar button.
     */
    fun requestScrollToSelection() {
        _scrollToSelectionTrigger.value++
    }
    
    // === Address History Navigation ===
    // Stack to store previous addresses for back navigation
    private val addressHistory = ArrayDeque<Long>()
    private val MAX_HISTORY_SIZE = 50 // Limit history size to avoid memory issues
    
    // StateFlow to notify UI about history availability
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()
    
    /**
     * Push current address to history before navigating to a new address.
     */
    private fun pushAddressToHistory(addr: Long) {
        // Don't push if it's the same as the last address in history
        if (addressHistory.lastOrNull() == addr) return
        
        addressHistory.addLast(addr)
        
        // Trim history if it exceeds max size
        while (addressHistory.size > MAX_HISTORY_SIZE) {
            addressHistory.removeFirst()
        }
        
        _canGoBack.value = addressHistory.isNotEmpty()
    }
    
    /**
     * Navigate back to the previous address in history.
     */
    fun navigateBack() {
        if (addressHistory.isEmpty()) return
        
        val previousAddr = addressHistory.removeLast()
        _canGoBack.value = addressHistory.isNotEmpty()
        
        // Navigate to previous address without adding to history
        jumpToAddressInternal(previousAddr)
    }
    
    // Global pointer
    private var currentOffset: Long = 0L
    private val DISASM_CHUNK_SIZE = 50

    init {
        // Init logic moved to initialize() to support ViewModel reuse
        // in simple navigation setups (Activity-scoped ViewModel)
    }

    fun initialize() {
        val path = R2PipeManager.pendingFilePath
        if (path != null) {
            // New file waiting to be configured
            _uiState.value = ProjectUiState.Configuring(path)
        } else {
             // No new file pending.
             // If we are already displaying data (Success), do nothing.
             // If we are Idle/Error, try to recover session if connected.
             if (_uiState.value is ProjectUiState.Idle || _uiState.value is ProjectUiState.Error) {
                 if (R2PipeManager.isConnected) {
                    loadOverview()
                } else {
                     _uiState.value = ProjectUiState.Error("No file selected or session active")
                }
             }
        }
    }

    fun startAnalysisSession(context: Context, analysisCmd: String, writable: Boolean, startupFlags: String) {
         val currentState = _uiState.value
         if (currentState is ProjectUiState.Configuring) {
             viewModelScope.launch {
                 _uiState.value = ProjectUiState.Analyzing
                 
                 val flags = if (writable) "-w $startupFlags" else startupFlags
                 
                 // Open Session
                 val openResult = R2PipeManager.open(context, currentState.filePath, flags.trim())

                 if (openResult.isSuccess) {
                     // Run Analysis
                     if (analysisCmd.isNotBlank() && analysisCmd != "none") {
                         R2PipeManager.execute("$analysisCmd; iIj")
                     }
                     // Load Data (Overview only)
                     loadOverview()
                     
                     // Set initial offset to entry point if possible, else 0
                     val entryPointsResult = repository.getEntryPoints()
                     if (entryPointsResult.isSuccess) {
                         val entries = entryPointsResult.getOrNull()
                         // Use first entry point's vaddr
                         currentOffset = entries?.firstOrNull()?.vAddr ?: 0L
                     } else {
                         // Fallback to 0 if fails
                         currentOffset = 0L
                     }
                     
                     // Update cursor address in state
                     (_uiState.value as? ProjectUiState.Success)?.let {
                         _uiState.value = it.copy(cursorAddress = currentOffset)
                     }
                     
                     // Clear pending path so subsequent navigations (or rotations) rely on configured state
                     R2PipeManager.pendingFilePath = null
                 } else {
                     _uiState.value = ProjectUiState.Error(openResult.exceptionOrNull()?.message ?: "Unknown error")
                 }
             }
         }
    }

    private fun loadOverview() {
        viewModelScope.launch {
            val binInfoResult = repository.getOverview()
            if (binInfoResult.isFailure) {
                _uiState.value = ProjectUiState.Error("Failed to load binary info: ${binInfoResult.exceptionOrNull()?.message}")
                return@launch
            }
            
            _uiState.value = ProjectUiState.Success(
                binInfo = binInfoResult.getOrNull(),
                cursorAddress = currentOffset
            )
        }
    }

    fun loadSections() {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (current.sections != null) return
        
        viewModelScope.launch {
            val result = repository.getSections()
            // Ensure we are still in success state
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(sections = result.getOrDefault(emptyList()))
            }
        }
    }

    fun loadSymbols() {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (current.symbols != null) return

        viewModelScope.launch {
            val result = repository.getSymbols()
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(symbols = result.getOrDefault(emptyList()))
            }
        }
    }

    fun loadImports() {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (current.imports != null) return

        viewModelScope.launch {
            val result = repository.getImports()
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(imports = result.getOrDefault(emptyList()))
            }
        }
    }

    fun loadRelocations() {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (current.relocations != null) return

        viewModelScope.launch {
            val result = repository.getRelocations()
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(relocations = result.getOrDefault(emptyList()))
            }
        }
    }

    fun loadStrings() {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (current.strings != null) return

        viewModelScope.launch {
            val result = repository.getStrings()
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(strings = result.getOrDefault(emptyList()))
            }
        }
    }

    fun loadFunctions() {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (current.functions != null) return

        viewModelScope.launch {
            val result = repository.getFunctions()
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(functions = result.getOrDefault(emptyList()))
            }
        }
    }

    // === Xrefs ===
    data class XrefsState(
        val visible: Boolean = false,
        val data: XrefsData = XrefsData(),
        val isLoading: Boolean = false,
        val targetAddress: Long = 0L  // The address being analyzed
    )
    
    private val _xrefsState = MutableStateFlow(XrefsState())
    val xrefsState: StateFlow<XrefsState> = _xrefsState.asStateFlow()
    
    fun fetchXrefs(addr: Long) {
        // Show loading
        _xrefsState.value = _xrefsState.value.copy(
            visible = true, 
            isLoading = true, 
            data = XrefsData(),
            targetAddress = addr
        )
        
        viewModelScope.launch {
            val result = repository.getXrefs(addr)
            val xrefsData = result.getOrElse { XrefsData() }
            _xrefsState.value = _xrefsState.value.copy(isLoading = false, data = xrefsData)
        }
    }
    
    fun dismissXrefs() {
        _xrefsState.value = _xrefsState.value.copy(visible = false)
    }

    // === Virtualized Hex Viewer ===
    
    /**
     * Initialize hex viewer with virtualization.
     * Uses Section info to calculate virtual address range.
     */
    fun loadHex() {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (current.hexReady) return // Already initialized
        
        viewModelScope.launch {
            // Get sections to determine virtual address range
            val sectionsResult = repository.getSections()
            val sections = sectionsResult.getOrDefault(emptyList())
            
            var startAddress = 0L
            var endAddress = 0L
            
            if (sections.isNotEmpty()) {
                // Find min vAddr and max (vAddr + vSize) from all sections
                startAddress = sections.minOf { it.vAddr }
                endAddress = sections.maxOf { it.vAddr + maxOf(it.vSize, it.size) }
            }
            
            // Fallback if sections are empty or invalid
            if (endAddress <= startAddress) {
                // Try Java File API as fallback (file offset based)
                R2PipeManager.currentFilePath?.let { path ->
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
            
            // Create HexDataManager with virtual address range
            hexDataManager = HexDataManager(startAddress, endAddress, repository).apply {
                onChunkLoaded = { _ ->
                    // Increment version to trigger recomposition
                    _hexCacheVersion.value++
                }
            }
            
            // Mark hex as ready
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(hexReady = true)
            }
            
            // Preload initial chunks around cursor
            hexDataManager?.loadChunkIfNeeded(currentOffset)
            hexDataManager?.preloadAround(currentOffset, 3)
        }
    }
    
    /**
     * Load a hex chunk for a specific address (called from UI during scroll).
     */
    fun loadHexChunkForAddress(addr: Long) {
        val manager = hexDataManager ?: return
        viewModelScope.launch {
            manager.loadChunkIfNeeded(addr)
        }
    }
    
    /**
     * Preload hex chunks around an address (called when user scrolls quickly).
     */
    fun preloadHexAround(addr: Long) {
        val manager = hexDataManager ?: return
        viewModelScope.launch {
            manager.preloadAround(addr, 2)
        }
    }

    /**
     * Update cursor position and save previous address to history.
     * This is called when user clicks on a byte/instruction.
     */
    fun updateCursor(addr: Long) {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        
        // Save current address to history before updating (only if significantly different)
        // We add to history when the address change is significant (not just scrolling)
        if (kotlin.math.abs(addr - currentOffset) > 16) {
            pushAddressToHistory(currentOffset)
        }
        
        // Update state immediately for UI highlight
        currentOffset = addr
        
        _uiState.value = current.copy(cursorAddress = addr)
        
        viewModelScope.launch {
            R2PipeManager.execute("s $addr")
        }
    }

    // === Virtualized Disasm Viewer ===
    
    /**
     * Initialize disassembly viewer with virtualization.
     * Uses Section info to calculate virtual address range (prioritizing executable sections).
     */
    fun loadDisassembly() {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (current.disasmReady && disasmDataManager != null) {
            // Already initialized, just preload around current cursor
            viewModelScope.launch {
                disasmDataManager?.preloadAround(currentOffset, 2)
            }
            return
        }
        
        viewModelScope.launch {
            // Get sections to determine virtual address range
            val sectionsResult = repository.getSections()
            val sections = sectionsResult.getOrDefault(emptyList())
            
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
                R2PipeManager.currentFilePath?.let { path ->
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
            disasmDataManager = DisasmDataManager(startAddress, endAddress, repository).apply {
                onChunkLoaded = { _ ->
                    // Increment version to trigger recomposition
                    _disasmCacheVersion.value++
                }
            }
            
            // Load initial data around cursor
            disasmDataManager?.resetAndLoadAround(currentOffset)
            
            // Mark disasm as ready
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(disasmReady = true)
            }
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

    /**
     * Jump to a specific address - updates cursor and reloads views.
     * Saves current address to history for back navigation.
     */
    fun jumpToAddress(addr: Long) {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        
        // Save current address to history before jumping
        pushAddressToHistory(currentOffset)
        
        jumpToAddressInternal(addr)
    }
    
    /**
     * Internal jump implementation without adding to history.
     * Used by both jumpToAddress and navigateBack.
     */
    private fun jumpToAddressInternal(addr: Long) {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        
        // Clamp addr using virtual address ranges from managers
        val hexStart = hexDataManager?.viewStartAddress ?: 0L
        val hexEnd = hexDataManager?.viewEndAddress ?: Long.MAX_VALUE
        val disasmStart = disasmDataManager?.viewStartAddress ?: 0L
        val disasmEnd = disasmDataManager?.viewEndAddress ?: Long.MAX_VALUE
        
        // Use the widest valid range (covers both hex and disasm)
        val minAddr = minOf(hexStart, disasmStart)
        val maxAddr = maxOf(hexEnd, disasmEnd)
        
        val target = addr.coerceIn(minAddr, maxAddr)
        
        currentOffset = target
        
        viewModelScope.launch {
            // For virtualized hex: just preload around target
            hexDataManager?.let { manager ->
                manager.loadChunkIfNeeded(target)
                manager.preloadAround(target, 3)
            }
            
            // For virtualized disasm: preload around target
            disasmDataManager?.let { manager ->
                manager.loadChunkAroundAddress(target)
                manager.preloadAround(target, 3)
            }
             
            // Update r2 seek
            R2PipeManager.execute("s $target")
             
            _uiState.value = current.copy(
                decompilation = null,
                cursorAddress = target
            )
        }
    }

    fun loadDecompilation() {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        
        viewModelScope.launch {
            // "Get function where pointer is located"
            val funcStart = repository.getFunctionStart(currentOffset).getOrDefault(currentOffset)
            val result = repository.getDecompilation(funcStart)
            
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(decompilation = result.getOrNull())
            }
        }
    }

    fun loadAllData() {
        // Deprecated or fallback to loading overview
        loadOverview()
    }

    fun retryLoadAll() {
        loadOverview()
    }
    
//    fun fetchXrefs(addr: Long) {
//        viewModelScope.launch {
//            val xrefs = repository.getXrefs(addr).getOrDefault(emptyList())
//            _xrefsState.value = XrefsState(visible = true, data = xrefs)
//        }
//    }
//
//    fun dismissXrefs() {
//        _xrefsState.value = _xrefsState.value.copy(visible = false)
//    }

    // === Modification Commands ===
    
    fun writeHex(addr: Long, hex: String) {
        viewModelScope.launch {
            // "wx [hex] @ [addr]"
            R2PipeManager.execute("wx $hex @ $addr")
            // Reload chunks to reflect changes
            hexDataManager?.clearCache()
            disasmDataManager?.clearCache()
            _hexCacheVersion.value++
            _disasmCacheVersion.value++
            
            // Reload displayed data
            hexDataManager?.loadChunkIfNeeded(currentOffset)
            disasmDataManager?.loadChunkAroundAddress(currentOffset)
        }
    }
    
    fun writeString(addr: Long, text: String) {
        viewModelScope.launch {
            // "w [text] @ [addr]" - need to escape properly or use w wide string if needed
            // For safety, let's use "w" which writes string
            // Escape quotes in string
            val escaped = text.replace("\"", "\\\"")
            R2PipeManager.execute("w \"$escaped\" @ $addr")
            
            hexDataManager?.clearCache()
            disasmDataManager?.clearCache()
            _hexCacheVersion.value++
            _disasmCacheVersion.value++
            
            hexDataManager?.loadChunkIfNeeded(currentOffset)
            disasmDataManager?.loadChunkAroundAddress(currentOffset)
        }
    }
    
    fun writeAsm(addr: Long, asm: String) {
        viewModelScope.launch {
            // "wa [asm] @ [addr]"
            val escaped = asm.replace("\"", "\\\"") // enclose in quotes to be safe? usually wa instruction is fine
            R2PipeManager.execute("wa \"$escaped\" @ $addr")
            
            hexDataManager?.clearCache()
            disasmDataManager?.clearCache()
            _hexCacheVersion.value++
            _disasmCacheVersion.value++
            
            hexDataManager?.loadChunkIfNeeded(currentOffset)
            disasmDataManager?.loadChunkAroundAddress(currentOffset)
        }
    }
    
    // Generic command execution
    fun executeCommand(cmd: String, callback: (String) -> Unit) {
        viewModelScope.launch {
            val result = R2PipeManager.execute(cmd)
            val output = result.getOrDefault("")
            callback(output)
            
            // If command might modify data, reload
            if (cmd.startsWith("w") || cmd.startsWith("p")) {
                hexDataManager?.clearCache()
                disasmDataManager?.clearCache()
                _hexCacheVersion.value++
                _disasmCacheVersion.value++
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hexDataManager?.clearCache()
        disasmDataManager?.clearCache()
        R2PipeManager.close()
    }
}

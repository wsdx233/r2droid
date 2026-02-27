package top.wsdx233.r2droid.feature.project

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

import top.wsdx233.r2droid.core.data.db.*
import top.wsdx233.r2droid.core.data.model.*
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.bininfo.data.BinInfoRepository
import top.wsdx233.r2droid.feature.project.data.ProjectRepository
import top.wsdx233.r2droid.feature.project.data.SavedProjectRepository
import top.wsdx233.r2droid.util.R2PipeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Extensible graph type enum. Add new types here as needed.
 */
enum class GraphType {
    FunctionFlow,   // agj - function basic block flow graph
    XrefGraph,       // agrj - cross-reference graph
    CallGraph,       // agcj - function call graph
    GlobalCallGraph, // agCj - global function call graph
    DataRefGraph     // agaj - data reference graph
}

sealed interface ProjectEvent {
    data class JumpToAddress(val address: Long) : ProjectEvent
    data class UpdateCursor(val address: Long) : ProjectEvent
    object NavigateBack : ProjectEvent
    object RequestScrollToSelection : ProjectEvent
    object RefreshAllViews : ProjectEvent
    data class LoadSections(val forceRefresh: Boolean = false) : ProjectEvent
    data class LoadSymbols(val forceRefresh: Boolean = false) : ProjectEvent
    data class LoadImports(val forceRefresh: Boolean = false) : ProjectEvent
    data class LoadRelocations(val forceRefresh: Boolean = false) : ProjectEvent
    data class LoadStrings(val forceRefresh: Boolean = false) : ProjectEvent
    data class LoadFunctions(val forceRefresh: Boolean = false) : ProjectEvent
    object LoadDecompilation : ProjectEvent
    data class JumpAndDecompile(val address: Long) : ProjectEvent
    data class SwitchDecompiler(val decompilerType: String) : ProjectEvent
    data class LoadGraph(val graphType: GraphType) : ProjectEvent
    object Initialize : ProjectEvent
    object StartRestoreSession : ProjectEvent
    object StartCustomCommandSession : ProjectEvent
    data class StartAnalysisSession(val cmd: String, val writable: Boolean, val flags: String) : ProjectEvent
    object ClearLogs : ProjectEvent
    data class ExecuteCommand(val cmd: String, val callback: (String) -> Unit) : ProjectEvent
    data class SaveProject(val name: String, val analysisLevel: String = "") : ProjectEvent
    data class UpdateProject(val projectId: String) : ProjectEvent
    object ResetSaveState : ProjectEvent
}

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
        val graphData: GraphData? = null,
        val graphType: GraphType = GraphType.FunctionFlow,
        val graphLoading: Boolean = false,
        val cursorAddress: Long = 0L
    ) : ProjectUiState()
    data class Error(val message: String) : ProjectUiState()
}



// Save project state
sealed class SaveProjectState {
    object Idle : SaveProjectState()
    object Saving : SaveProjectState()
    data class Success(val message: String) : SaveProjectState()
    data class Error(val message: String) : SaveProjectState()
}

/**
 * Debug wrapper: logs every load() call from the Paging library
 * so we can see exactly what offset/limit is being requested.
 */
private class LoggingPagingSource<T : Any>(
    private val delegate: PagingSource<Int, T>,
    private val tag: String
) : PagingSource<Int, T>() {

    init {
        // Forward invalidation from delegate to this wrapper
        delegate.registerInvalidatedCallback { invalidate() }
        registerInvalidatedCallback { delegate.invalidate() }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return delegate.getRefreshKey(state)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val type = when (params) {
            is LoadParams.Refresh -> "REFRESH"
            is LoadParams.Append -> "APPEND"
            is LoadParams.Prepend -> "PREPEND"
        }
        Log.d(tag, "load($type) key=${params.key}, loadSize=${params.loadSize}")
        val start = System.currentTimeMillis()
        val result = delegate.load(params)
        val elapsed = System.currentTimeMillis() - start
        when (result) {
            is LoadResult.Page -> Log.d(tag, "  -> Page: ${result.data.size} items, prev=${result.prevKey}, next=${result.nextKey}, ${elapsed}ms")
            is LoadResult.Error -> Log.e(tag, "  -> Error: ${result.throwable}", result.throwable)
            is LoadResult.Invalid -> Log.w(tag, "  -> Invalid, ${elapsed}ms")
        }
        return result
    }

    override val jumpingSupported: Boolean get() = delegate.jumpingSupported
}

@HiltViewModel
class ProjectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    private val savedProjectRepository: SavedProjectRepository,
    private val binInfoRepository: BinInfoRepository,
    private val stringDao: StringDao,
    private val sectionDao: SectionDao,
    private val symbolDao: SymbolDao,
    private val importDao: ImportDao,
    private val relocationDao: RelocationDao,
    private val functionDao: FunctionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectUiState>(ProjectUiState.Idle)
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    // Expose logs from LogManager
    val logs: StateFlow<List<top.wsdx233.r2droid.util.LogEntry>> = top.wsdx233.r2droid.util.LogManager.logs
    
    // Clear logs
    fun clearLogs() {
        top.wsdx233.r2droid.util.LogManager.clear()
    }
    
    // Save project state
    private val _saveProjectState = MutableStateFlow<SaveProjectState>(SaveProjectState.Idle)
    val saveProjectState: StateFlow<SaveProjectState> = _saveProjectState.asStateFlow()
    
    // === Strings Paging ===
    private val _stringsSearchQuery = MutableStateFlow("")
    val stringsSearchQuery: StateFlow<String> = _stringsSearchQuery.asStateFlow()

    // 字符串是否正在同步到数据库
    private val _stringsSyncing = MutableStateFlow(false)
    val stringsSyncing: StateFlow<Boolean> = _stringsSyncing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val stringsPagingData: Flow<PagingData<StringEntity>> = _stringsSearchQuery.flatMapLatest { query ->
        Pager(PagingConfig(pageSize = 100, prefetchDistance = 30, enablePlaceholders = true, initialLoadSize = 200)) {
            if (query.isBlank()) stringDao.getPagingSource()
            else stringDao.searchStrings(query)
        }.flow
    }.cachedIn(viewModelScope)

    fun updateStringsSearchQuery(query: String) {
        _stringsSearchQuery.value = query
    }

    // === Sections Paging ===
    private val _sectionsSearchQuery = MutableStateFlow("")
    val sectionsSearchQuery: StateFlow<String> = _sectionsSearchQuery.asStateFlow()
    private val _sectionsSyncing = MutableStateFlow(false)
    val sectionsSyncing: StateFlow<Boolean> = _sectionsSyncing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val sectionsPagingData: Flow<PagingData<SectionEntity>> = _sectionsSearchQuery.flatMapLatest { query ->
        Pager(PagingConfig(pageSize = 100, prefetchDistance = 30, enablePlaceholders = true, initialLoadSize = 200)) {
            if (query.isBlank()) sectionDao.getPagingSource() else sectionDao.search(query)
        }.flow
    }.cachedIn(viewModelScope)

    fun updateSectionsSearchQuery(query: String) { _sectionsSearchQuery.value = query }

    // === Symbols Paging ===
    private val _symbolsSearchQuery = MutableStateFlow("")
    val symbolsSearchQuery: StateFlow<String> = _symbolsSearchQuery.asStateFlow()
    private val _symbolsSyncing = MutableStateFlow(false)
    val symbolsSyncing: StateFlow<Boolean> = _symbolsSyncing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val symbolsPagingData: Flow<PagingData<SymbolEntity>> = _symbolsSearchQuery.flatMapLatest { query ->
        Pager(PagingConfig(pageSize = 100, prefetchDistance = 30, enablePlaceholders = true, initialLoadSize = 200)) {
            if (query.isBlank()) symbolDao.getPagingSource() else symbolDao.search(query)
        }.flow
    }.cachedIn(viewModelScope)

    fun updateSymbolsSearchQuery(query: String) { _symbolsSearchQuery.value = query }

    // === Imports Paging ===
    private val _importsSearchQuery = MutableStateFlow("")
    val importsSearchQuery: StateFlow<String> = _importsSearchQuery.asStateFlow()
    private val _importsSyncing = MutableStateFlow(false)
    val importsSyncing: StateFlow<Boolean> = _importsSyncing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val importsPagingData: Flow<PagingData<ImportEntity>> = _importsSearchQuery.flatMapLatest { query ->
        Pager(PagingConfig(pageSize = 100, prefetchDistance = 30, enablePlaceholders = true, initialLoadSize = 200)) {
            if (query.isBlank()) importDao.getPagingSource() else importDao.search(query)
        }.flow
    }.cachedIn(viewModelScope)

    fun updateImportsSearchQuery(query: String) { _importsSearchQuery.value = query }

    // === Relocations Paging ===
    private val _relocationsSearchQuery = MutableStateFlow("")
    val relocationsSearchQuery: StateFlow<String> = _relocationsSearchQuery.asStateFlow()
    private val _relocationsSyncing = MutableStateFlow(false)
    val relocationsSyncing: StateFlow<Boolean> = _relocationsSyncing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val relocationsPagingData: Flow<PagingData<RelocationEntity>> = _relocationsSearchQuery.flatMapLatest { query ->
        Pager(PagingConfig(pageSize = 100, prefetchDistance = 30, enablePlaceholders = true, initialLoadSize = 200)) {
            if (query.isBlank()) relocationDao.getPagingSource() else relocationDao.search(query)
        }.flow
    }.cachedIn(viewModelScope)

    fun updateRelocationsSearchQuery(query: String) { _relocationsSearchQuery.value = query }

    // === Functions Paging ===
    private val _functionsSearchQuery = MutableStateFlow("")
    val functionsSearchQuery: StateFlow<String> = _functionsSearchQuery.asStateFlow()
    private val _functionsSyncing = MutableStateFlow(false)
    val functionsSyncing: StateFlow<Boolean> = _functionsSyncing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val functionsPagingData: Flow<PagingData<FunctionEntity>> = _functionsSearchQuery.flatMapLatest { query ->
        Pager(PagingConfig(pageSize = 100, prefetchDistance = 30, enablePlaceholders = true, initialLoadSize = 200)) {
            val source = if (query.isBlank()) functionDao.getPagingSource() else functionDao.search(query)
            LoggingPagingSource(source, "PagingLoad-Functions")
        }.flow
    }.cachedIn(viewModelScope)

    fun updateFunctionsSearchQuery(query: String) { _functionsSearchQuery.value = query }

    // === Hex/Disasm coordination ===
    // Event to notify UI that data might have changed globally (e.g. via console command)
    private val _globalDataInvalidated = MutableStateFlow(0L)
    val globalDataInvalidated: StateFlow<Long> = _globalDataInvalidated.asStateFlow()

    // Scroll to selection trigger - increment to trigger scroll to current cursor position
    private val _scrollToSelectionTrigger = MutableStateFlow(0)
    val scrollToSelectionTrigger: StateFlow<Int> = _scrollToSelectionTrigger.asStateFlow()

    // Track current detail tab to avoid unnecessary decompilation calls
    // 0=Hex, 1=Disasm, 2=Decompiler, 3=Graph
    var currentDetailTab: Int = 1

    // Project-scoped decompiler type (initialized from global default)
    private val _currentDecompiler = MutableStateFlow(SettingsManager.decompilerDefault)
    val currentDecompiler: StateFlow<String> = _currentDecompiler.asStateFlow()

    // Decompiler display settings (reactive)
    private val _decompilerShowLineNumbers = MutableStateFlow(SettingsManager.decompilerShowLineNumbers)
    val decompilerShowLineNumbers: StateFlow<Boolean> = _decompilerShowLineNumbers.asStateFlow()

    private val _decompilerWordWrap = MutableStateFlow(SettingsManager.decompilerWordWrap)
    val decompilerWordWrap: StateFlow<Boolean> = _decompilerWordWrap.asStateFlow()

    private val _decompilerSoraMode = MutableStateFlow(false)
    val decompilerSoraMode: StateFlow<Boolean> = _decompilerSoraMode.asStateFlow()
    fun toggleSoraMode() { _decompilerSoraMode.value = !_decompilerSoraMode.value }

    private val _decompilerZoomScale = MutableStateFlow(SettingsManager.decompilerZoomScale)
    val decompilerZoomScale: StateFlow<Float> = _decompilerZoomScale.asStateFlow()

    private val _resetZoomTrigger = MutableStateFlow(0)
    val resetZoomTrigger: StateFlow<Int> = _resetZoomTrigger.asStateFlow()

    fun toggleLineNumbers() {
        val new = !_decompilerShowLineNumbers.value
        _decompilerShowLineNumbers.value = new
        SettingsManager.decompilerShowLineNumbers = new
    }

    fun toggleWordWrap() {
        val new = !_decompilerWordWrap.value
        _decompilerWordWrap.value = new
        SettingsManager.decompilerWordWrap = new
    }

    fun updateDecompilerZoomScale(scale: Float) {
        _decompilerZoomScale.value = scale
        SettingsManager.decompilerZoomScale = scale
    }

    fun resetDecompilerZoom() {
        _decompilerZoomScale.value = 1f
        SettingsManager.decompilerZoomScale = 1f
        _resetZoomTrigger.value++
    }
    
    fun onEvent(event: ProjectEvent) {
        when (event) {
            is ProjectEvent.Initialize -> initialize()
            is ProjectEvent.StartRestoreSession -> startRestoreSession()
            is ProjectEvent.StartCustomCommandSession -> startCustomCommandSession()
            is ProjectEvent.StartAnalysisSession -> startAnalysisSession(event.cmd, event.writable, event.flags)
            is ProjectEvent.JumpToAddress -> jumpToAddress(event.address)
            is ProjectEvent.UpdateCursor -> updateCursor(event.address)
            is ProjectEvent.NavigateBack -> navigateBack()
            is ProjectEvent.RequestScrollToSelection -> requestScrollToSelection()
            is ProjectEvent.RefreshAllViews -> refreshAllViews()
            is ProjectEvent.LoadSections -> loadSections(event.forceRefresh)
            is ProjectEvent.LoadSymbols -> loadSymbols(event.forceRefresh)
            is ProjectEvent.LoadImports -> loadImports(event.forceRefresh)
            is ProjectEvent.LoadRelocations -> loadRelocations(event.forceRefresh)
            is ProjectEvent.LoadStrings -> loadStrings(event.forceRefresh)
            is ProjectEvent.LoadFunctions -> loadFunctions(event.forceRefresh)
            is ProjectEvent.LoadDecompilation -> loadDecompilation()
            is ProjectEvent.JumpAndDecompile -> jumpAndDecompile(event.address)
            is ProjectEvent.SwitchDecompiler -> switchDecompiler(event.decompilerType)
            is ProjectEvent.LoadGraph -> loadGraph(event.graphType)
            is ProjectEvent.ClearLogs -> clearLogs()
            is ProjectEvent.ExecuteCommand -> executeCommand(event.cmd, event.callback)
            is ProjectEvent.SaveProject -> saveProject(event.name, event.analysisLevel)
            is ProjectEvent.UpdateProject -> updateProject(event.projectId)
            is ProjectEvent.ResetSaveState -> resetSaveState()
        }
    }
    
    /**
     * Request the active viewer to scroll to the current cursor position (centered).
     * Called from TopAppBar button.
     */
    fun requestScrollToSelection() {
        _scrollToSelectionTrigger.value++
    }

    /**
     * Clear all caches and refresh hex/disasm/decompile views.
     * Called from long-press on the locate button.
     */
    fun refreshAllViews() {
        // Trigger hex + disasm refresh via globalDataInvalidated
        _globalDataInvalidated.value = System.currentTimeMillis()
        // Clear decompilation and reload
        val current = _uiState.value as? ProjectUiState.Success ?: return
        _uiState.value = current.copy(decompilation = null)
        loadDecompilation()
    }
    
    // === Address History Navigation ===
    // Stack to store previous addresses for back navigation
    private val addressHistory = ArrayDeque<Long>()
    private val MAX_HISTORY_SIZE = 50 // Limit history size to avoid memory issues

    // StateFlow to notify UI about history availability
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    // History dialog state
    data class HistoryState(
        val visible: Boolean = false,
        val isLoading: Boolean = false,
        val entries: List<top.wsdx233.r2droid.core.data.model.HistoryEntry> = emptyList()
    )

    private val _historyState = MutableStateFlow(HistoryState())
    val historyState: StateFlow<HistoryState> = _historyState.asStateFlow()

    fun showHistoryDialog() {
        if (addressHistory.isEmpty()) return
        _historyState.value = HistoryState(visible = true, isLoading = true)
        viewModelScope.launch {
            val addresses = addressHistory.toList().reversed()
            val result = repository.getHistoryDetails(addresses)
            val entries = result.getOrDefault(
                addresses.map { top.wsdx233.r2droid.core.data.model.HistoryEntry(address = it) }
            )
            _historyState.value = _historyState.value.copy(isLoading = false, entries = entries)
        }
    }

    fun dismissHistoryDialog() {
        _historyState.value = _historyState.value.copy(visible = false)
    }
    
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

    init {
        // Init logic moved to initialize() to support ViewModel reuse
        // in simple navigation setups (Activity-scoped ViewModel)
    }

    fun initialize() {
        val path = R2PipeManager.pendingFilePath
        val restoreFlags = R2PipeManager.pendingRestoreFlags
        val customCmd = R2PipeManager.pendingCustomCommand

        if (customCmd != null) {
            // Custom command - skip config, go directly to analyzing
            _uiState.value = ProjectUiState.Analyzing
        } else if (path != null) {
            if (restoreFlags != null) {
                // Restoring a saved project - skip config screen
                _uiState.value = ProjectUiState.Analyzing
            } else {
                // New file waiting to be configured
                _uiState.value = ProjectUiState.Configuring(path)
            }
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
    
    /**
     * Start a restore session for a saved project.
     * Called from LaunchedEffect when restoring.
     * Opens the binary file first, then loads the script using `. script_path` command.
     */
    fun startRestoreSession() {
        val path = R2PipeManager.pendingFilePath ?: return
        val scriptPath = R2PipeManager.pendingRestoreFlags ?: return  // This now stores the script path
        
        viewModelScope.launch {
            _uiState.value = ProjectUiState.Analyzing
            
            // Clear logs before starting new session
            clearLogs()
            
            // Open Session without restore flags (just open the binary)
            val openResult = R2PipeManager.open(context, path, "")
            
            if (openResult.isSuccess) {
                // Load the script using `. script_path` command
                // This executes all commands in the script file to restore analysis state
                val loadScriptResult = R2PipeManager.execute(". $scriptPath ; iIj")
                
                if (loadScriptResult.isFailure) {
                    // Log warning but continue - script may have partial success
                    android.util.Log.w("ProjectViewModel", "Script load warning: ${loadScriptResult.exceptionOrNull()?.message}")
                }
                
                // Load Data (Overview only)
                loadOverview()
                
                // Set initial offset to entry point if possible, else 0
                val entryPointsResult = repository.getEntryPoints()
                if (entryPointsResult.isSuccess) {
                    val entries = entryPointsResult.getOrNull()
                    currentOffset = entries?.firstOrNull()?.vAddr ?: 0L
                } else {
                    currentOffset = 0L
                }
                
                // Update cursor address in state
                (_uiState.value as? ProjectUiState.Success)?.let {
                    _uiState.value = it.copy(cursorAddress = currentOffset)
                }
                
                // Clear pending data
                R2PipeManager.pendingFilePath = null
                R2PipeManager.pendingRestoreFlags = null
            } else {
                _uiState.value = ProjectUiState.Error(openResult.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun startCustomCommandSession() {
        val rawArgs = R2PipeManager.pendingCustomCommand ?: return
        viewModelScope.launch {
            _uiState.value = ProjectUiState.Analyzing
            clearLogs()
            val result = R2PipeManager.openRaw(context, rawArgs)
            if (result.isSuccess) {
                R2PipeManager.pendingCustomCommand = null
                if (R2PipeManager.isR2FridaSession) {
                    // r2frida sessions don't support standard iIj, skip loadOverview
                    _uiState.value = ProjectUiState.Success(
                        binInfo = null,
                        cursorAddress = 0L
                    )
                } else {
                    loadOverview()
                }
            } else {
                _uiState.value = ProjectUiState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    fun startAnalysisSession(analysisCmd: String, writable: Boolean, startupFlags: String) {
         val currentState = _uiState.value
         if (currentState is ProjectUiState.Configuring) {
             viewModelScope.launch {
                 _uiState.value = ProjectUiState.Analyzing
                 
                 // Clear logs before starting new session
                 clearLogs()
                 
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

            // Load sections eagerly - hex/disasm views need them for boundary calculation
            loadSections()
        }
    }

    fun loadSections(forceRefresh: Boolean = false) {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (!forceRefresh && current.sections != null) return

        viewModelScope.launch {
            _sectionsSyncing.value = true
            binInfoRepository.syncSectionsToDb()
            _sectionsSyncing.value = false
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(sections = emptyList())
            }
        }
    }

    fun loadSymbols(forceRefresh: Boolean = false) {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (!forceRefresh && current.symbols != null) return

        viewModelScope.launch {
            _symbolsSyncing.value = true
            binInfoRepository.syncSymbolsToDb()
            _symbolsSyncing.value = false
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(symbols = emptyList())
            }
        }
    }

    fun loadImports(forceRefresh: Boolean = false) {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (!forceRefresh && current.imports != null) return

        viewModelScope.launch {
            _importsSyncing.value = true
            binInfoRepository.syncImportsToDb()
            _importsSyncing.value = false
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(imports = emptyList())
            }
        }
    }

    fun loadRelocations(forceRefresh: Boolean = false) {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (!forceRefresh && current.relocations != null) return

        viewModelScope.launch {
            _relocationsSyncing.value = true
            binInfoRepository.syncRelocationsToDb()
            _relocationsSyncing.value = false
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(relocations = emptyList())
            }
        }
    }

    fun loadStrings(forceRefresh: Boolean = false) {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (!forceRefresh && current.strings != null) return

        viewModelScope.launch {
            _stringsSyncing.value = true
            val result = binInfoRepository.syncStringsToDb()
            _stringsSyncing.value = false
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                // 标记 strings 已加载（用空列表占位，实际数据从 Paging 读取）
                _uiState.value = currentState.copy(strings = emptyList())
            }
        }
    }

    fun loadFunctions(forceRefresh: Boolean = false) {
        val current = _uiState.value as? ProjectUiState.Success ?: return
        if (!forceRefresh && current.functions != null) return

        viewModelScope.launch {
            _functionsSyncing.value = true
            binInfoRepository.syncFunctionsToDb()
            _functionsSyncing.value = false
            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(functions = emptyList())
            }
        }
    }

    // === Xrefs ===
    // Uses XrefsData from top.wsdx233.r2droid.core.data.model.XrefsData
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
        currentOffset = addr

        viewModelScope.launch {
            // Update r2 seek
            R2PipeManager.execute("s $addr")
            _uiState.value = current.copy(
                decompilation = if (currentDetailTab == 2) null else current.decompilation,
                cursorAddress = addr
            )

            // Only reload decompilation when on the decompiler tab
            if (currentDetailTab == 2) {
                val funcStart = repository.getFunctionStart(addr).getOrDefault(addr)
                val result = repository.getDecompilation(funcStart, _currentDecompiler.value)
                val currentState = _uiState.value
                if (currentState is ProjectUiState.Success) {
                    _uiState.value = currentState.copy(decompilation = result.getOrNull())
                }
            }
        }
    }

    /**
     * Jump to address from the decompiler view.
     * Delegates to jumpToAddress which handles decompilation reload when on tab 2.
     */
    fun jumpAndDecompile(addr: Long) {
        jumpToAddress(addr)
    }

    fun loadGraph(graphType: GraphType) {
        val current = _uiState.value as? ProjectUiState.Success ?: return

        viewModelScope.launch {
            _uiState.value = current.copy(graphLoading = true, graphType = graphType, graphData = null)

            val funcStart = repository.getFunctionStart(currentOffset).getOrDefault(currentOffset)
            val result = when (graphType) {
                GraphType.FunctionFlow -> repository.getFunctionGraph(funcStart)
                GraphType.XrefGraph -> repository.getXrefGraph(funcStart)
                GraphType.CallGraph -> repository.getCallGraph(funcStart)
                GraphType.GlobalCallGraph -> repository.getGlobalCallGraph()
                GraphType.DataRefGraph -> repository.getDataRefGraph(funcStart)
            }

            val currentState = _uiState.value
            if (currentState is ProjectUiState.Success) {
                _uiState.value = currentState.copy(
                    graphData = result.getOrNull(),
                    graphLoading = false
                )
            }
        }
    }

    fun switchDecompiler(decompilerType: String) {
        _currentDecompiler.value = decompilerType
        // Clear current decompilation to force reload
        val current = _uiState.value as? ProjectUiState.Success ?: return
        _uiState.value = current.copy(decompilation = null)
        loadDecompilation()
    }

    fun loadDecompilation() {
        val current = _uiState.value as? ProjectUiState.Success ?: return

        viewModelScope.launch {
            // "Get function where pointer is located"
            val funcStart = repository.getFunctionStart(currentOffset).getOrDefault(currentOffset)
            val result = repository.getDecompilation(funcStart, _currentDecompiler.value)

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
    
    // Generic command execution
    fun executeCommand(cmd: String, callback: (String) -> Unit) {
        viewModelScope.launch {
            val result = R2PipeManager.execute(cmd)
            val output = result.getOrDefault("")

            callback(output)

            // If command might modify data, reload
            if (cmd.startsWith("w") || cmd.startsWith("p")) {
                _globalDataInvalidated.value = System.currentTimeMillis()
            }
        }
    }

    /**
     * Resolve an expression (function name, symbol, or expression) to an address.
     */
    suspend fun resolveExpression(expression: String): Result<Long> {
        return repository.resolveExpression(expression)
    }
    
    // === Project Saving ===
    
    /**
     * Initialize the saved project repository with context.
     * Deprecated: Repository is now injected.
     */
    fun initializeSavedProjectRepository(context: Context) {
        // No-op
    }
    
    /**
     * Check if current session is from a restored project.
     */
    fun getCurrentProjectId(): String? = R2PipeManager.currentProjectId
    
    /**
     * Save current analysis as a new project.
     * 
     * @param name Display name for the project
     * @param analysisLevel The analysis level used
     */
    fun saveProject(name: String, analysisLevel: String = "") {
        val repo = savedProjectRepository
        
        viewModelScope.launch {
            _saveProjectState.value = SaveProjectState.Saving
            
            val result = repo.saveCurrentProject(name, analysisLevel)
            
            if (result.isSuccess) {
                val project = result.getOrNull()
                // Update currentProjectId so future saves can update instead of create new
                if (project != null) {
                    R2PipeManager.currentProjectId = project.id
                }
                R2PipeManager.isDirtyAfterSave = false
                _saveProjectState.value = SaveProjectState.Success("Project saved successfully")
            } else {
                _saveProjectState.value = SaveProjectState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to save project"
                )
            }
        }
    }
    
    /**
     * Update an existing saved project with current analysis.
     * 
     * @param projectId The ID of the project to update
     */
    fun updateProject(projectId: String) {
        val repo = savedProjectRepository
        
        viewModelScope.launch {
            _saveProjectState.value = SaveProjectState.Saving
            
            val result = repo.updateProject(projectId)

            if (result.isSuccess) {
                R2PipeManager.isDirtyAfterSave = false
                _saveProjectState.value = SaveProjectState.Success("Project updated successfully")
            } else {
                _saveProjectState.value = SaveProjectState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to update project"
                )
            }
        }
    }
    
    /**
     * Reset save state (call after showing success/error message)
     */
    fun resetSaveState() {
        _saveProjectState.value = SaveProjectState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        clearLogs()
        R2PipeManager.close()
    }
}

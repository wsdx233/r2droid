package top.wsdx233.r2droid.feature.r2frida

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.feature.r2frida.data.*
import top.wsdx233.r2droid.util.LogManager
import top.wsdx233.r2droid.util.LogType
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

sealed class StaticProjectLoadState {
    object Idle : StaticProjectLoadState()
    object Loading : StaticProjectLoadState()
    data class Success(val replacedCount: Int) : StaticProjectLoadState()
    data class Error(val message: String) : StaticProjectLoadState()
}

@HiltViewModel
class R2FridaViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    companion object {
        private const val TAG = "R2FridaViewModel"
    }

    private val repo = R2FridaRepository()

    private val _overview = MutableStateFlow<FridaInfo?>(null)
    val overview: StateFlow<FridaInfo?> = _overview.asStateFlow()

    private val _libraries = MutableStateFlow<List<FridaLibrary>?>(null)
    val libraries: StateFlow<List<FridaLibrary>?> = _libraries.asStateFlow()

    private val _entries = MutableStateFlow<List<FridaEntry>?>(null)
    val entries: StateFlow<List<FridaEntry>?> = _entries.asStateFlow()

    private val _exports = MutableStateFlow<List<FridaExport>?>(null)
    val exports: StateFlow<List<FridaExport>?> = _exports.asStateFlow()
    private val _rawExports = MutableStateFlow<List<FridaExport>?>(null)
    private val exportDemangleCache = ConcurrentHashMap<String, String>()
    private val _autoDemangleExports = MutableStateFlow(false)
    val autoDemangleExports: StateFlow<Boolean> = _autoDemangleExports.asStateFlow()

    private val _strings = MutableStateFlow<List<FridaString>?>(null)
    val strings: StateFlow<List<FridaString>?> = _strings.asStateFlow()

    private val _symbols = MutableStateFlow<List<FridaExport>?>(null)
    val symbols: StateFlow<List<FridaExport>?> = _symbols.asStateFlow()

    private val _sections = MutableStateFlow<List<FridaExport>?>(null)
    val sections: StateFlow<List<FridaExport>?> = _sections.asStateFlow()

    private val _mappings = MutableStateFlow<List<FridaMapping>?>(null)
    val mappings: StateFlow<List<FridaMapping>?> = _mappings.asStateFlow()

    val scriptLogs = LogManager.logs

    private val _scriptRunning = MutableStateFlow(false)
    val scriptRunning: StateFlow<Boolean> = _scriptRunning.asStateFlow()

    // Script content persistence (survives tab switches)
    private val _scriptContent = MutableStateFlow("")
    val scriptContent: StateFlow<String> = _scriptContent.asStateFlow()

    // Current script file name (null = unsaved)
    private val _currentScriptName = MutableStateFlow<String?>(null)
    val currentScriptName: StateFlow<String?> = _currentScriptName.asStateFlow()

    // Script file list
    private val _scriptFiles = MutableStateFlow<List<String>>(emptyList())
    val scriptFiles: StateFlow<List<String>> = _scriptFiles.asStateFlow()

    private val _staticProjectLoadState = MutableStateFlow<StaticProjectLoadState>(StaticProjectLoadState.Idle)
    val staticProjectLoadState: StateFlow<StaticProjectLoadState> = _staticProjectLoadState.asStateFlow()

    // --- Custom Functions ---
    private val _customFunctions = MutableStateFlow<List<FridaFunction>?>(null)
    val customFunctions: StateFlow<List<FridaFunction>?> = _customFunctions.asStateFlow()
    private val _rawCustomFunctions = MutableStateFlow<List<FridaFunction>?>(null)
    private val functionDemangleCache = ConcurrentHashMap<String, String>()
    private val _autoDemangleCustomFunctions = MutableStateFlow(false)
    val autoDemangleCustomFunctions: StateFlow<Boolean> = _autoDemangleCustomFunctions.asStateFlow()
    private val _customFunctionsSearchQuery = MutableStateFlow("")
    val customFunctionsSearchQuery: StateFlow<String> = _customFunctionsSearchQuery.asStateFlow()
    fun updateCustomFunctionsSearchQuery(q: String) { _customFunctionsSearchQuery.value = q }

    // --- Search ---
    private val _searchResults = MutableStateFlow<List<FridaSearchResult>?>(null)
    val searchResults: StateFlow<List<FridaSearchResult>?> = _searchResults.asStateFlow()
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Search config state (persists across tab switches)
    private val _searchValueType = MutableStateFlow(SearchValueType.DWORD)
    val searchValueType: StateFlow<SearchValueType> = _searchValueType.asStateFlow()
    private val _searchCompare = MutableStateFlow(SearchCompare.EQUAL)
    val searchCompare: StateFlow<SearchCompare> = _searchCompare.asStateFlow()
    private val _selectedRegions = MutableStateFlow(setOf(MemoryRegion.ALL))
    val selectedRegions: StateFlow<Set<MemoryRegion>> = _selectedRegions.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()
    // Max results limit
    private val _maxResults = MutableStateFlow(50000)
    val maxResults: StateFlow<Int> = _maxResults.asStateFlow()
    // Frozen addresses: address -> value
    private val _frozenAddresses = MutableStateFlow<Map<String, String>>(emptyMap())
    val frozenAddresses: StateFlow<Map<String, String>> = _frozenAddresses.asStateFlow()
    private var freezeJob: kotlinx.coroutines.Job? = null

    fun updateSearchValueType(t: SearchValueType) { _searchValueType.value = t }
    fun updateSearchCompare(c: SearchCompare) { _searchCompare.value = c }
    fun updateSelectedRegions(r: Set<MemoryRegion>) { _selectedRegions.value = r }
    fun clearSearchError() { _searchError.value = null }
    fun updateMaxResults(n: Int) { _maxResults.value = n }

    // --- Monitor ---
    private val _monitors = MutableStateFlow<List<MonitorInstance>>(emptyList())
    val monitors: StateFlow<List<MonitorInstance>> = _monitors.asStateFlow()
    private val monitorJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val monitorFiles = mutableMapOf<String, java.io.File>()

    /** Pre-fill address for the monitor input field, consumed once by the UI. */
    private val _monitorPrefillAddress = MutableStateFlow<String?>(null)
    val monitorPrefillAddress: StateFlow<String?> = _monitorPrefillAddress.asStateFlow()
    fun setMonitorPrefillAddress(addr: String) { _monitorPrefillAddress.value = addr }
    fun consumeMonitorPrefillAddress() { _monitorPrefillAddress.value = null }

    fun updateScriptContent(content: String) {
        _scriptContent.value = content
    }

    private fun getScriptsDir(): File {
        val dir = File(context.filesDir, "frida_scripts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun refreshScriptFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = getScriptsDir()
            val files = dir.listFiles()
                ?.filter { it.isFile && it.extension == "js" }
                ?.sortedByDescending { it.lastModified() }
                ?.map { it.name }
                ?: emptyList()
            _scriptFiles.value = files
        }
    }

    fun newScript() {
        _scriptContent.value = ""
        _currentScriptName.value = null
    }

    fun saveScript(name: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getScriptsDir(), if (name.endsWith(".js")) name else "$name.js")
            file.writeText(content)
            _currentScriptName.value = file.name
            _scriptContent.value = content
            refreshScriptFiles()
        }
    }

    fun openScript(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getScriptsDir(), name)
            if (file.exists()) {
                val content = file.readText()
                _scriptContent.value = content
                _currentScriptName.value = name
            }
        }
    }

    fun deleteScript(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getScriptsDir(), name)
            file.delete()
            if (_currentScriptName.value == name) {
                _currentScriptName.value = null
            }
            refreshScriptFiles()
        }
    }

    init {
        refreshScriptFiles()
    }

    // Search query state for each sub-tab (survives recomposition)
    private val _librariesSearchQuery = MutableStateFlow("")
    val librariesSearchQuery: StateFlow<String> = _librariesSearchQuery.asStateFlow()

    private val _mappingsSearchQuery = MutableStateFlow("")
    val mappingsSearchQuery: StateFlow<String> = _mappingsSearchQuery.asStateFlow()

    private val _entriesSearchQuery = MutableStateFlow("")
    val entriesSearchQuery: StateFlow<String> = _entriesSearchQuery.asStateFlow()

    private val _exportsSearchQuery = MutableStateFlow("")
    val exportsSearchQuery: StateFlow<String> = _exportsSearchQuery.asStateFlow()

    private val _stringsSearchQuery = MutableStateFlow("")
    val stringsSearchQuery: StateFlow<String> = _stringsSearchQuery.asStateFlow()

    private val _symbolsSearchQuery = MutableStateFlow("")
    val symbolsSearchQuery: StateFlow<String> = _symbolsSearchQuery.asStateFlow()

    private val _sectionsSearchQuery = MutableStateFlow("")
    val sectionsSearchQuery: StateFlow<String> = _sectionsSearchQuery.asStateFlow()

    fun updateLibrariesSearchQuery(q: String) { _librariesSearchQuery.value = q }
    fun updateMappingsSearchQuery(q: String) { _mappingsSearchQuery.value = q }
    fun updateEntriesSearchQuery(q: String) { _entriesSearchQuery.value = q }
    fun updateExportsSearchQuery(q: String) { _exportsSearchQuery.value = q }
    fun updateStringsSearchQuery(q: String) { _stringsSearchQuery.value = q }
    fun updateSymbolsSearchQuery(q: String) { _symbolsSearchQuery.value = q }
    fun updateSectionsSearchQuery(q: String) { _sectionsSearchQuery.value = q }

    fun setAutoDemangleExports(enabled: Boolean) {
        if (_autoDemangleExports.value == enabled) return
        _autoDemangleExports.value = enabled
        val raw = _rawExports.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _exports.value = if (enabled) applyDemangleToExports(raw) else raw
        }
    }

    fun setAutoDemangleCustomFunctions(enabled: Boolean) {
        if (_autoDemangleCustomFunctions.value == enabled) return
        _autoDemangleCustomFunctions.value = enabled
        val raw = _rawCustomFunctions.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _customFunctions.value = if (enabled) applyDemangleToFunctions(raw) else raw
        }
    }

    fun loadOverview() {
        viewModelScope.launch {
            repo.getOverview().onSuccess { _overview.value = it }
        }
    }

    fun loadLibraries(force: Boolean = false) {
        if (!force && _libraries.value != null) return
        viewModelScope.launch {
            repo.getLibraries().onSuccess { _libraries.value = it }
                .onFailure { _libraries.value = emptyList() }
        }
    }

    fun loadEntries(force: Boolean = false) {
        if (!force && _entries.value != null) return
        viewModelScope.launch {
            repo.getEntries().onSuccess { _entries.value = it }
                .onFailure { _entries.value = emptyList() }
        }
    }

    fun loadExports(force: Boolean = false) {
        if (!force && _exports.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.getExports().onSuccess { list ->
                _rawExports.value = list
                _exports.value = if (_autoDemangleExports.value) {
                    applyDemangleToExports(list)
                } else {
                    list
                }
            }.onFailure {
                _rawExports.value = emptyList()
                _exports.value = emptyList()
            }
        }
    }

    fun loadStrings(force: Boolean = false) {
        if (!force && _strings.value != null) return
        viewModelScope.launch {
            repo.getStrings().onSuccess { _strings.value = it }
                .onFailure { _strings.value = emptyList() }
        }
    }

    fun loadSymbols(force: Boolean = false) {
        if (!force && _symbols.value != null) return
        viewModelScope.launch {
            repo.getSymbols().onSuccess { _symbols.value = it }
                .onFailure { _symbols.value = emptyList() }
        }
    }

    fun loadSections(force: Boolean = false) {
        if (!force && _sections.value != null) return
        viewModelScope.launch {
            repo.getSections().onSuccess { _sections.value = it }
                .onFailure { _sections.value = emptyList() }
        }
    }

    fun loadMappings(force: Boolean = false) {
        if (!force && _mappings.value != null) return
        viewModelScope.launch {
            repo.getMappings().onSuccess { _mappings.value = it }
                .onFailure { _mappings.value = emptyList() }
        }
    }

    fun clearNonLibraryCache() {
        _entries.value = null
        _exports.value = null
        _rawExports.value = null
        _strings.value = null
        _symbols.value = null
        _sections.value = null
        _mappings.value = null
        _customFunctions.value = null
        _rawCustomFunctions.value = null
        _searchResults.value = null
    }

    private fun getPublicExchangeDir(): String {
        val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "R2Droid_Frida")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    fun loadCustomFunctions(force: Boolean = false) {
        if (!force && _customFunctions.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.getCustomFunctions(context.cacheDir.absolutePath, getPublicExchangeDir()).onSuccess { list ->
                _rawCustomFunctions.value = list
                _customFunctions.value = if (_autoDemangleCustomFunctions.value) {
                    applyDemangleToFunctions(list)
                } else {
                    list
                }
            }.onFailure {
                _rawCustomFunctions.value = emptyList()
                _customFunctions.value = emptyList()
            }
        }
    }

    private suspend fun applyDemangleToExports(items: List<FridaExport>): List<FridaExport> {
        return items.map { export ->
            val demangled = demangleNameCached(export.name, exportDemangleCache)
            if (demangled == export.name) export else export.copy(name = demangled)
        }
    }

    private suspend fun applyDemangleToFunctions(items: List<FridaFunction>): List<FridaFunction> {
        return items.map { function ->
            val demangled = demangleNameCached(function.name, functionDemangleCache)
            if (demangled == function.name) function else function.copy(name = demangled)
        }
    }

    private suspend fun demangleNameCached(
        original: String,
        cache: ConcurrentHashMap<String, String>
    ): String {
        if (original.isBlank()) return original
        cache[original]?.let { return it }

        val demangled = repo.demangleSymbol(original)
            .getOrElse { original }
            .ifBlank { original }

        cache[original] = demangled
        return demangled
    }

    /**
     * Unified search: supports exact, range, union (semicolon-separated), all types.
     * @param input raw user input (may contain semicolons for union search)
     * @param rangeMin min value for range mode (empty = not range)
     * @param rangeMax max value for range mode (empty = not range)
     */
    fun performSearch(input: String, rangeMin: String = "", rangeMax: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            _searchError.value = null
            val type = _searchValueType.value
            val compare = _searchCompare.value
            val typeStr = when (type) {
                SearchValueType.BYTE -> "u8"
                SearchValueType.SHORT -> "u16"
                SearchValueType.DWORD -> "u32"
                SearchValueType.QWORD -> "u64"
                SearchValueType.FLOAT -> "float"
                SearchValueType.DOUBLE -> "double"
                SearchValueType.UTF8 -> "utf8"
                SearchValueType.UTF16 -> "utf16"
                SearchValueType.HEX -> "hex"
            }
            val compareStr = when (compare) {
                SearchCompare.EQUAL -> "eq"
                SearchCompare.NOT_EQUAL -> "neq"
                SearchCompare.GREATER -> "gt"
                SearchCompare.LESS -> "lt"
                SearchCompare.GREATER_EQ -> "gte"
                SearchCompare.LESS_EQ -> "lte"
            }
            // Split by semicolon for union search
            val values = input.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val protection = buildProtectionString()
            repo.searchMemory(
                context.cacheDir.absolutePath, getPublicExchangeDir(),
                typeStr, values, compareStr, rangeMin, rangeMax, protection, "[]",
                _maxResults.value
            ).onSuccess {
                _searchResults.value = it
            }.onFailure {
                _searchResults.value = emptyList()
                _searchError.value = it.message
            }
            _isSearching.value = false
        }
    }

    /**
     * Refine existing results: exact, fuzzy (increased/decreased/unchanged), range, expression.
     */
    fun refineSearch(
        filterMode: String, targetVal: String = "",
        rangeMin: String = "", rangeMax: String = "",
        expression: String = ""
    ) {
        val current = _searchResults.value ?: return
        val addrs = current.map { it.address }
        val oldValues = current.map { it.value }
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            _searchError.value = null
            val typeStr = when (_searchValueType.value) {
                SearchValueType.BYTE -> "u8"
                SearchValueType.SHORT -> "u16"
                SearchValueType.DWORD -> "u32"
                SearchValueType.QWORD -> "u64"
                SearchValueType.FLOAT -> "float"
                SearchValueType.DOUBLE -> "double"
                SearchValueType.UTF8 -> "utf8"
                SearchValueType.UTF16 -> "utf16"
                SearchValueType.HEX -> "hex"
            }
            repo.filterMemory(
                context.cacheDir.absolutePath, getPublicExchangeDir(),
                addrs, oldValues, typeStr, filterMode, targetVal,
                rangeMin, rangeMax, expression
            ).onSuccess {
                _searchResults.value = it
            }.onFailure {
                _searchResults.value = emptyList()
                _searchError.value = it.message
            }
            _isSearching.value = false
        }
    }

    /** Write a new value to a single address. */
    fun writeValue(address: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val typeStr = when (_searchValueType.value) {
                SearchValueType.BYTE -> "u8"
                SearchValueType.SHORT -> "u16"
                SearchValueType.DWORD -> "u32"
                SearchValueType.QWORD -> "u64"
                SearchValueType.FLOAT -> "float"
                SearchValueType.DOUBLE -> "double"
                SearchValueType.UTF8 -> "utf8"
                SearchValueType.UTF16 -> "utf16"
                SearchValueType.HEX -> "u32"
            }
            repo.writeMemoryValue(context.cacheDir.absolutePath, address, typeStr, value)
            // Update the result list with new value
            _searchResults.value = _searchResults.value?.map {
                if (it.address == address) it.copy(value = value) else it
            }
        }
    }

    /** Batch write the same value to all current results. */
    fun batchWriteAll(value: String) {
        val addrs = _searchResults.value?.map { it.address } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val typeStr = when (_searchValueType.value) {
                SearchValueType.BYTE -> "u8"
                SearchValueType.SHORT -> "u16"
                SearchValueType.DWORD -> "u32"
                SearchValueType.QWORD -> "u64"
                SearchValueType.FLOAT -> "float"
                SearchValueType.DOUBLE -> "double"
                SearchValueType.UTF8 -> "utf8"
                SearchValueType.UTF16 -> "utf16"
                SearchValueType.HEX -> "u32"
            }
            repo.batchWriteMemory(context.cacheDir.absolutePath, addrs, typeStr, value)
            _searchResults.value = _searchResults.value?.map { it.copy(value = value) }
        }
    }

    /** Toggle freeze for an address. Frozen addresses are periodically re-written. */
    fun toggleFreeze(address: String, value: String) {
        val current = _frozenAddresses.value.toMutableMap()
        if (current.containsKey(address)) {
            current.remove(address)
        } else {
            current[address] = value
        }
        _frozenAddresses.value = current
        if (current.isNotEmpty() && freezeJob == null) {
            startFreezeLoop()
        } else if (current.isEmpty()) {
            freezeJob?.cancel()
            freezeJob = null
        }
    }

    fun clearAllFreezes() {
        _frozenAddresses.value = emptyMap()
        freezeJob?.cancel()
        freezeJob = null
    }

    private fun startFreezeLoop() {
        freezeJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val frozen = _frozenAddresses.value
                if (frozen.isEmpty()) break
                for ((addr, value) in frozen) {
                    writeValue(addr, value)
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun buildProtectionString(): String {
        val regions = _selectedRegions.value
        if (regions.contains(MemoryRegion.ALL)) return "r--"
        // Use the most permissive protection that covers selected regions
        val hasExec = regions.any { it.protection.contains('x') }
        val hasWrite = regions.any { it.protection.contains('w') }
        return when {
            hasExec && hasWrite -> "r--"
            hasExec -> "r-x"
            hasWrite -> "rw-"
            else -> "r--"
        }
    }

    /** Re-read current values at all result addresses. */
    fun refreshSearchValues() {
        val current = _searchResults.value ?: return
        if (current.isEmpty()) return
        val addrs = current.map { it.address }
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            val typeStr = when (_searchValueType.value) {
                SearchValueType.BYTE -> "u8"
                SearchValueType.SHORT -> "u16"
                SearchValueType.DWORD -> "u32"
                SearchValueType.QWORD -> "u64"
                SearchValueType.FLOAT -> "float"
                SearchValueType.DOUBLE -> "double"
                SearchValueType.UTF8 -> "utf8"
                SearchValueType.UTF16 -> "utf16"
                SearchValueType.HEX -> "u32"
            }
            repo.refreshValues(
                context.cacheDir.absolutePath, getPublicExchangeDir(),
                addrs, typeStr
            ).onSuccess {
                _searchResults.value = it
            }.onFailure {
                _searchError.value = it.message
            }
            _isSearching.value = false
        }
    }

    fun clearSearchResults() {
        _searchResults.value = null
    }

    fun addMonitor(address: String, size: Int) {
        val id = "mon_${System.currentTimeMillis()}"
        val instance = MonitorInstance(id = id, address = address, size = size)
        _monitors.value = _monitors.value + instance
    }

    fun removeMonitor(monitorId: String) {
        stopMonitor(monitorId)
        _monitors.value = _monitors.value.filter { it.id != monitorId }
    }

    fun updateMonitorFilter(monitorId: String, filter: MonitorFilter) {
        _monitors.value = _monitors.value.map {
            if (it.id == monitorId) it.copy(filter = filter) else it
        }
    }

    fun clearMonitorEvents(monitorId: String) {
        _monitors.value = _monitors.value.map {
            if (it.id == monitorId) it.copy(events = emptyList()) else it
        }
    }

    fun startMonitor(monitorId: String) {
        val mon = _monitors.value.find { it.id == monitorId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = repo.startMonitor(
                    context.cacheDir.absolutePath, getPublicExchangeDir(),
                    mon.address, mon.size, monitorId
                )
                val file = java.io.File(path)
                monitorFiles[monitorId] = file
                _monitors.value = _monitors.value.map {
                    if (it.id == monitorId) it.copy(isActive = true, events = emptyList()) else it
                }

                monitorJobs[monitorId]?.cancel()
                monitorJobs[monitorId] = launch(Dispatchers.IO) {
                    var lastPos = 0L
                    while (_monitors.value.any { it.id == monitorId && it.isActive }) {
                        val f = monitorFiles[monitorId]
                        if (f != null && f.exists() && f.length() > lastPos) {
                            java.io.RandomAccessFile(f, "r").use { raf ->
                                raf.seek(lastPos)
                                val newLines = mutableListOf<String>()
                                var line = raf.readLine()
                                while (line != null) { newLines.add(line); line = raf.readLine() }
                                lastPos = raf.filePointer

                                val newEvents = newLines.mapNotNull {
                                    try { FridaMonitorEvent.fromJson(org.json.JSONObject(it)) }
                                    catch (_: Exception) { null }
                                }
                                if (newEvents.isNotEmpty()) {
                                    _monitors.value = _monitors.value.map { m ->
                                        if (m.id == monitorId) m.copy(
                                            events = (m.events + newEvents).takeLast(1000)
                                        ) else m
                                    }
                                }
                            }
                        }
                        kotlinx.coroutines.delay(1000)
                    }
                }
            } catch (_: Exception) {
                _monitors.value = _monitors.value.map {
                    if (it.id == monitorId) it.copy(isActive = false) else it
                }
            }
        }
    }

    fun stopMonitor(monitorId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _monitors.value = _monitors.value.map {
                if (it.id == monitorId) it.copy(isActive = false) else it
            }
            try { repo.stopMonitor(monitorId) } catch (_: Exception) {}
            monitorJobs[monitorId]?.cancel()
            monitorJobs.remove(monitorId)
            monitorFiles.remove(monitorId)
        }
    }

    fun clearScriptLogs() = LogManager.clear()

    fun resetStaticProjectLoadState() {
        _staticProjectLoadState.value = StaticProjectLoadState.Idle
    }

    fun loadRebasedStaticProject(projectScriptPath: String, moduleBaseHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _staticProjectLoadState.value = StaticProjectLoadState.Loading
            runCatching {
                LogManager.log(LogType.INFO, "Loading static project script…")
                Log.i(TAG, "loadRebasedStaticProject start: script=$projectScriptPath base=$moduleBaseHex")
                val moduleBase = parseHexToULong(moduleBaseHex)
                    ?: error("Invalid module base address: $moduleBaseHex")
                val sourceFile = File(projectScriptPath)
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    error("Project script file not found")
                }
                if (!R2PipeManager.isConnected) {
                    error("R2 session is not connected")
                }

                val addressRegex = Regex("\\b0x[0-9a-fA-F]+\\b")
                var replacedCount = 0

                val tempFile = File(context.cacheDir, "frida_rebased_project_${System.currentTimeMillis()}.r2")
                sourceFile.bufferedReader().use { reader ->
                    tempFile.bufferedWriter().use { writer ->
                        reader.forEachLine { line ->
                            val transformedLine = addressRegex.replace(line) { match ->
                                val raw = match.value
                                val parsed = parseHexToULong(raw)
                                if (parsed == null) {
                                    raw
                                } else {
                                    replacedCount += 1
                                    "0x${(parsed + moduleBase).toString(16)}"
                                }
                            }
                            writer.appendLine(transformedLine)
                        }
                    }
                }

                LogManager.log(
                    LogType.INFO,
                    "Rebased project prepared (${replacedCount} addresses): ${tempFile.absolutePath}"
                )
                Log.i(TAG, "rebase finished: replacedCount=$replacedCount temp=${tempFile.absolutePath}")

                if (!tempFile.exists() || !tempFile.isFile) {
                    error("Rebased script file was not created")
                }
                LogManager.log(LogType.INFO, "Rebased script size: ${tempFile.length()} bytes")

                // r2 dot-command treats quotes as literal characters in script path.
                // Use plain absolute path to avoid ERROR: Cannot find script '"..."'
                val loadCmd = ". ${tempFile.absolutePath}"
                LogManager.log(LogType.COMMAND, loadCmd)

                withTimeout(90_000) {
                    R2PipeManager.execute(loadCmd).getOrThrow()
                }
                LogManager.log(LogType.INFO, "Static project loaded into current Frida session")
                Log.i(TAG, "loadRebasedStaticProject execute success")

                replacedCount
            }.onSuccess { count ->
                _staticProjectLoadState.value = StaticProjectLoadState.Success(count)
            }.onFailure { e ->
                LogManager.log(LogType.ERROR, "Static project load failed: ${e.message ?: "unknown error"}")
                Log.e(TAG, "loadRebasedStaticProject failed", e)
                _staticProjectLoadState.value = StaticProjectLoadState.Error(
                    e.message ?: "Failed to load rebased project"
                )
            }
        }
    }

    private fun parseHexToULong(text: String): ULong? {
        val normalized = text.trim().removePrefix("0x").removePrefix("0X")
        return normalized.toULongOrNull(16)
    }

    fun runScript(script: String) {
        _scriptContent.value = script
        viewModelScope.launch {
            _scriptRunning.value = true
            LogManager.clear()
            repo.executeScript(script, context.cacheDir.absolutePath)
            _scriptRunning.value = false
        }
    }
}

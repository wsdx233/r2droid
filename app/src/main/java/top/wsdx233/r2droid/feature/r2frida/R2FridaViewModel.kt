package top.wsdx233.r2droid.feature.r2frida

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.feature.r2frida.data.*
import top.wsdx233.r2droid.util.LogManager
import java.io.File
import javax.inject.Inject

@HiltViewModel
class R2FridaViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val repo = R2FridaRepository()

    private val _overview = MutableStateFlow<FridaInfo?>(null)
    val overview: StateFlow<FridaInfo?> = _overview.asStateFlow()

    private val _libraries = MutableStateFlow<List<FridaLibrary>?>(null)
    val libraries: StateFlow<List<FridaLibrary>?> = _libraries.asStateFlow()

    private val _entries = MutableStateFlow<List<FridaEntry>?>(null)
    val entries: StateFlow<List<FridaEntry>?> = _entries.asStateFlow()

    private val _exports = MutableStateFlow<List<FridaExport>?>(null)
    val exports: StateFlow<List<FridaExport>?> = _exports.asStateFlow()

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

    // --- Custom Functions ---
    private val _customFunctions = MutableStateFlow<List<FridaFunction>?>(null)
    val customFunctions: StateFlow<List<FridaFunction>?> = _customFunctions.asStateFlow()
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
        viewModelScope.launch {
            repo.getExports().onSuccess { _exports.value = it }
                .onFailure { _exports.value = emptyList() }
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
        _strings.value = null
        _symbols.value = null
        _sections.value = null
        _mappings.value = null
        _customFunctions.value = null
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
            repo.getCustomFunctions(context.cacheDir.absolutePath, getPublicExchangeDir()).onSuccess { _customFunctions.value = it }
                .onFailure { _customFunctions.value = emptyList() }
        }
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

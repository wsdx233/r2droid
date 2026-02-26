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

    // --- Monitor ---
    private val _monitorEvents = MutableStateFlow<List<FridaMonitorEvent>>(emptyList())
    val monitorEvents: StateFlow<List<FridaMonitorEvent>> = _monitorEvents.asStateFlow()
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    private var monitorJob: kotlinx.coroutines.Job? = null
    private var monitorFile: java.io.File? = null

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

    fun performSearch(pattern: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            repo.searchMemory(context.cacheDir.absolutePath, getPublicExchangeDir(), pattern, value).onSuccess {
                _searchResults.value = it
            }.onFailure { _searchResults.value = emptyList() }
            _isSearching.value = false
        }
    }

    fun refineSearch(type: String, value: String) {
        val currentAddrs = _searchResults.value?.map { it.address } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            repo.filterMemory(context.cacheDir.absolutePath, getPublicExchangeDir(), currentAddrs, type, value).onSuccess {
                _searchResults.value = it
            }.onFailure { _searchResults.value = emptyList() }
            _isSearching.value = false
        }
    }

    fun clearSearchResults() {
        _searchResults.value = null
    }

    fun startMonitor(address: String, size: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = repo.startMonitor(context.cacheDir.absolutePath, getPublicExchangeDir(), address, size)
            monitorFile = java.io.File(path)
            _isMonitoring.value = true
            _monitorEvents.value = emptyList() // clear previous events
            
            monitorJob?.cancel()
            monitorJob = launch(Dispatchers.IO) {
                var lastPos = 0L
                while (_isMonitoring.value) {
                    val file = monitorFile
                    if (file != null && file.exists()) {
                        val len = file.length()
                        if (len > lastPos) {
                            java.io.RandomAccessFile(file, "r").use { raf ->
                                raf.seek(lastPos)
                                val newLines = mutableListOf<String>()
                                var line = raf.readLine()
                                while (line != null) {
                                    newLines.add(line)
                                    line = raf.readLine()
                                }
                                lastPos = raf.filePointer
                                
                                val newEvents = newLines.mapNotNull { 
                                    try { FridaMonitorEvent.fromJson(org.json.JSONObject(it)) } 
                                    catch(e: Exception) { null } 
                                }
                                if (newEvents.isNotEmpty()) {
                                    _monitorEvents.value = (_monitorEvents.value + newEvents).takeLast(1000)
                                }
                            }
                        }
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    fun stopMonitor() {
        viewModelScope.launch(Dispatchers.IO) {
            _isMonitoring.value = false
            repo.stopMonitor()
            monitorJob?.cancel()
            monitorJob = null
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

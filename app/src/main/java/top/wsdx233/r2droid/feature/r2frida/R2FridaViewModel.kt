package top.wsdx233.r2droid.feature.r2frida

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.feature.r2frida.data.*
import top.wsdx233.r2droid.util.LogManager
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
    }

    fun clearScriptLogs() = LogManager.clear()

    fun runScript(script: String) {
        viewModelScope.launch {
            _scriptRunning.value = true
            LogManager.clear()
            repo.executeScript(script, context.cacheDir.absolutePath)
            _scriptRunning.value = false
        }
    }
}

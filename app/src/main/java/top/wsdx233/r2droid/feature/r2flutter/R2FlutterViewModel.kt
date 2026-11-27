package top.wsdx233.r2droid.feature.r2flutter

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterClass
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterComponent
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterDataState
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterFunction
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterInstruction
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterOverview
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterString
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterTargetStatus
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterXref
import top.wsdx233.r2droid.feature.r2flutter.data.R2FlutterRepository
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File
import javax.inject.Inject

@HiltViewModel
class R2FlutterViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val repository = R2FlutterRepository()

    private val _targetStatus = MutableStateFlow(FlutterTargetStatus.CHECKING)
    val targetStatus = _targetStatus.asStateFlow()

    private val _overview = MutableStateFlow<FlutterDataState<FlutterOverview>>(FlutterDataState.Idle)
    val overview = _overview.asStateFlow()

    private val _functions = MutableStateFlow<FlutterDataState<List<FlutterFunction>>>(FlutterDataState.Idle)
    val functions = _functions.asStateFlow()

    private val _classes = MutableStateFlow<FlutterDataState<List<FlutterClass>>>(FlutterDataState.Idle)
    val classes = _classes.asStateFlow()

    private val _strings = MutableStateFlow<FlutterDataState<List<FlutterString>>>(FlutterDataState.Idle)
    val strings = _strings.asStateFlow()

    private val _xrefs = MutableStateFlow<FlutterDataState<List<FlutterXref>>>(FlutterDataState.Idle)
    val xrefs = _xrefs.asStateFlow()

    private val _instructions = MutableStateFlow<FlutterDataState<List<FlutterInstruction>>>(FlutterDataState.Idle)
    val instructions = _instructions.asStateFlow()

    private val _components = MutableStateFlow<FlutterDataState<List<FlutterComponent>>>(FlutterDataState.Idle)
    val components = _components.asStateFlow()

    private val _analysisRunning = MutableStateFlow(false)
    val analysisRunning = _analysisRunning.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage = _operationMessage.asStateFlow()

    private val _fuzzyStrings = MutableStateFlow(false)
    val fuzzyStrings = _fuzzyStrings.asStateFlow()

    private val _namePool = MutableStateFlow(false)
    val namePool = _namePool.asStateFlow()

    private val _profile = MutableStateFlow("")
    val profile = _profile.asStateFlow()

    private val _mapFileName = MutableStateFlow<String?>(null)
    val mapFileName = _mapFileName.asStateFlow()

    private var overviewJob: Job? = null
    private var stringsJob: Job? = null

    fun probeTargetIfNeeded(shouldProbe: Boolean) {
        if (!shouldProbe) {
            if (_targetStatus.value == FlutterTargetStatus.CHECKING) {
                _targetStatus.value = FlutterTargetStatus.UNAVAILABLE
            }
            return
        }
        if (_overview.value is FlutterDataState.Idle) {
            loadOverview(force = false)
        }
    }

    fun loadOverview(force: Boolean = false) {
        if (!force && (_overview.value is FlutterDataState.Ready || _overview.value is FlutterDataState.Loading)) return
        val target = R2PipeManager.captureActiveTarget()
        if (target == null || target.isFridaSession) {
            _overview.value = FlutterDataState.Error("Flutter snapshot is unavailable in this session")
            _targetStatus.value = FlutterTargetStatus.UNAVAILABLE
            return
        }
        _overview.value = FlutterDataState.Loading
        overviewJob?.cancel()
        overviewJob = viewModelScope.launch(Dispatchers.IO) {
            repository.getOverview()
                .onSuccess {
                    if (!target.isCurrent()) return@onSuccess
                    _overview.value = FlutterDataState.Ready(it)
                    _targetStatus.value = FlutterTargetStatus.AVAILABLE
                }
                .onFailure {
                    if (!target.isCurrent()) return@onFailure
                    _overview.value = FlutterDataState.Error(it.message ?: "Flutter snapshot was not detected")
                    _targetStatus.value = FlutterTargetStatus.UNAVAILABLE
                }
        }
    }

    fun loadFunctions(force: Boolean = false) = loadList(_functions, force) { repository.getFunctions() }

    fun loadClasses(force: Boolean = false) = loadList(_classes, force, repository::getClasses)

    fun loadStrings(force: Boolean = false) {
        if (!force && (_strings.value is FlutterDataState.Ready || _strings.value is FlutterDataState.Loading)) return
        val target = R2PipeManager.captureActiveTarget() ?: return
        val fuzzy = _fuzzyStrings.value
        _strings.value = FlutterDataState.Loading
        stringsJob?.cancel()
        stringsJob = viewModelScope.launch(Dispatchers.IO) {
            repository.getStrings(fuzzy)
                .onSuccess { if (target.isCurrent()) _strings.value = FlutterDataState.Ready(it) }
                .onFailure { if (target.isCurrent()) _strings.value = FlutterDataState.Error(it.message ?: "Unable to load strings") }
        }
    }

    fun setFuzzyStrings(enabled: Boolean) {
        if (_fuzzyStrings.value == enabled) return
        _fuzzyStrings.value = enabled
        loadStrings(force = true)
    }

    fun loadXrefs(force: Boolean = false) = loadList(_xrefs, force, repository::getXrefs)

    fun loadInstructions(force: Boolean = false) = loadList(_instructions, force) { repository.getInstructions() }

    fun loadComponents(force: Boolean = false) = loadList(_components, force) { repository.getComponents() }

    fun runAnalysis(depth: Int, onApplied: () -> Unit) {
        if (_analysisRunning.value) return
        val target = R2PipeManager.captureActiveTarget() ?: return
        _analysisRunning.value = true
        _operationMessage.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.runAnalysis(depth)
                    .onSuccess {
                        if (!target.isCurrent()) return@onSuccess
                        _operationMessage.value = "Analysis A${"A".repeat((depth - 1).coerceAtLeast(0))} completed"
                        loadFunctions(force = true)
                        loadXrefs(force = true)
                        withContext(Dispatchers.Main) { onApplied() }
                    }
                    .onFailure {
                        if (target.isCurrent()) _operationMessage.value = it.message ?: "Analysis failed"
                    }
            } finally {
                _analysisRunning.value = false
            }
        }
    }

    fun applyClasses(onApplied: () -> Unit) {
        if (_analysisRunning.value) return
        val target = R2PipeManager.captureActiveTarget() ?: return
        _analysisRunning.value = true
        _operationMessage.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.applyClasses()
                    .onSuccess {
                        if (!target.isCurrent()) return@onSuccess
                        _operationMessage.value = "Dart classes and types applied"
                        withContext(Dispatchers.Main) { onApplied() }
                    }
                    .onFailure {
                        if (target.isCurrent()) _operationMessage.value = it.message ?: "Unable to apply Dart classes"
                    }
            } finally {
                _analysisRunning.value = false
            }
        }
    }

    fun setNamePool(enabled: Boolean) {
        val previous = _namePool.value
        val target = R2PipeManager.captureActiveTarget() ?: return
        _namePool.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            repository.setNamePool(enabled)
                .onFailure {
                    if (target.isCurrent()) {
                        _namePool.value = previous
                        _operationMessage.value = it.message
                    }
                }
        }
    }

    fun updateProfile(value: String) {
        _profile.value = value
    }

    fun applyProfile() {
        val target = R2PipeManager.captureActiveTarget() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.setProfile(_profile.value)
                .onSuccess {
                    if (!target.isCurrent()) return@onSuccess
                    _operationMessage.value = "Dart profile updated"
                    loadOverview(force = true)
                }
                .onFailure {
                    if (target.isCurrent()) _operationMessage.value = it.message ?: "Invalid Dart profile"
                }
        }
    }

    fun importObfuscationMap(uri: Uri) {
        val target = R2PipeManager.captureActiveTarget() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            var stagedFile: File? = null
            runCatching {
                val directory = File(context.filesDir, "r2flutter/maps").apply { mkdirs() }
                val file = File(directory, "obfuscation-${System.currentTimeMillis()}.json")
                stagedFile = file
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to read the obfuscation map")
                val map = JSONArray(file.readText())
                require(
                    map.length() > 0 && map.length() % 2 == 0 &&
                        (0 until map.length()).all { (map.opt(it) as? String)?.isNotBlank() == true }
                ) {
                    "The obfuscation map must contain original/obfuscated name pairs"
                }
                check(target.isCurrent()) { "Active session changed" }
                repository.setMapFile(file.absolutePath).getOrThrow()
                _mapFileName.value = file.name
            }.onSuccess {
                if (!target.isCurrent()) return@onSuccess
                _operationMessage.value = "Obfuscation map applied"
                loadOverview(force = true)
                loadFunctions(force = true)
                loadClasses(force = true)
            }.onFailure {
                stagedFile?.delete()
                if (target.isCurrent()) {
                    _operationMessage.value = it.message ?: "Unable to import the obfuscation map"
                }
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    private fun <T> loadList(
        state: MutableStateFlow<FlutterDataState<List<T>>>,
        force: Boolean,
        loader: suspend () -> Result<List<T>>
    ) {
        if (!force && (state.value is FlutterDataState.Ready || state.value is FlutterDataState.Loading)) return
        val target = R2PipeManager.captureActiveTarget() ?: return
        state.value = FlutterDataState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            loader()
                .onSuccess { if (target.isCurrent()) state.value = FlutterDataState.Ready(it) }
                .onFailure { if (target.isCurrent()) state.value = FlutterDataState.Error(it.message ?: "Unable to load Flutter data") }
        }
    }
}

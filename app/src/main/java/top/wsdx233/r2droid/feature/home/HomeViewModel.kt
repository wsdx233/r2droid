package top.wsdx233.r2droid.feature.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.SavedProject
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.project.data.SavedProjectRepository
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.util.UriUtils
import java.io.File
import java.io.FileOutputStream

sealed class HomeUiEvent {
    data object NavigateToProject : HomeUiEvent()
    data object NavigateToAbout : HomeUiEvent()
    data object NavigateToSettings : HomeUiEvent()
    data object NavigateToFeatures : HomeUiEvent()
    data object NavigateToProotSetup : HomeUiEvent()
    data class ShowError(val message: String) : HomeUiEvent()
    data class ShowMessage(val message: String) : HomeUiEvent()
}

private sealed interface RestorePrecheckResult {
    data class Ready(val project: SavedProject, val needsProotSetup: Boolean) : RestorePrecheckResult
    data object BinaryNotFound : RestorePrecheckResult
    data object ScriptNotFound : RestorePrecheckResult
    data class Failure(val error: Throwable) : RestorePrecheckResult
}

class HomeViewModel : ViewModel() {
    private val _uiEvent = Channel<HomeUiEvent>()
    val uiEvent: Flow<HomeUiEvent> = _uiEvent.receiveAsFlow()

    // Saved projects from repository
    var savedProjects by mutableStateOf<List<SavedProject>>(emptyList())
        private set

    // Precomputed off the main thread so SavedProjectCard avoids File I/O during composition.
    var projectBinaryAccessible by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    // Loading state
    var isLoadingProjects by mutableStateOf(false)
        private set

    // Non-null while a saved project is being prepared to open.
    var restoringProjectId by mutableStateOf<String?>(null)
        private set

    // True while resolving/copying a file selected from the system picker.
    var isOpeningFile by mutableStateOf(false)
        private set

    val isOpeningAnyTarget: Boolean
        get() = restoringProjectId != null || isOpeningFile

    // Repository instance (will be initialized with context)
    private var repository: SavedProjectRepository? = null

    /**
     * Initialize the repository with context and load projects
     */
    fun initialize(context: Context) {
        if (repository == null) {
            repository = SavedProjectRepository(context.applicationContext)
        }
        loadSavedProjects()
    }

    /**
     * Load all saved projects from repository
     */
    fun loadSavedProjects() {
        viewModelScope.launch {
            isLoadingProjects = true
            try {
                val projects = repository?.getAllProjects() ?: emptyList()
                val accessibility = withContext(Dispatchers.IO) {
                    projects.associate { it.id to it.isBinaryAccessible() }
                }
                savedProjects = projects
                projectBinaryAccessible = accessibility
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingProjects = false
            }
        }
    }

    fun onFileSelected(context: Context, uri: Uri) {
        if (isOpeningAnyTarget) return
        val appContext = context.applicationContext
        isOpeningFile = true
        viewModelScope.launch {
            try {
                // Determine file path off the main thread (content URIs may require copying to cache).
                val (filePath, needsProotSetup) = withContext(Dispatchers.IO) {
                    val path = resolvePath(appContext, uri)
                    val prootRequired = path != null &&
                        SettingsManager.useProotMode &&
                        !top.wsdx233.r2droid.util.ProotInstaller.isEnvironmentReady(appContext)
                    path to prootRequired
                }

                if (filePath != null) {
                    R2PipeManager.pendingFilePath = filePath
                    R2PipeManager.pendingProjectRestore = null
                    R2PipeManager.pendingAutoSaveProject = null
                    if (needsProotSetup) {
                        _uiEvent.send(HomeUiEvent.ShowMessage(appContext.getString(R.string.proot_setup_required_message)))
                        _uiEvent.send(HomeUiEvent.NavigateToProotSetup)
                    } else {
                        _uiEvent.send(HomeUiEvent.NavigateToProject)
                    }
                } else {
                    _uiEvent.send(HomeUiEvent.ShowError(appContext.getString(R.string.home_error_resolve_path)))
                }
            } catch (e: Exception) {
                _uiEvent.send(HomeUiEvent.ShowError(e.message ?: appContext.getString(R.string.home_error_unknown)))
            } finally {
                isOpeningFile = false
            }
        }
    }

    /**
     * Restore a saved project
     */
    fun onRestoreProject(context: Context, project: SavedProject) {
        if (isOpeningAnyTarget) return
        val appContext = context.applicationContext
        val repo = repository ?: SavedProjectRepository(appContext).also { repository = it }
        restoringProjectId = project.id
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val resolved = repo.resolveProjectForRestore(project.id).getOrElse { error ->
                        return@withContext if (!project.isBinaryAccessible()) {
                            RestorePrecheckResult.BinaryNotFound
                        } else if (!project.isScriptAccessible()) {
                            RestorePrecheckResult.ScriptNotFound
                        } else {
                            RestorePrecheckResult.Failure(error)
                        }
                    }
                    if (!resolved.isBinaryAccessible()) {
                        return@withContext RestorePrecheckResult.BinaryNotFound
                    }
                    if (!resolved.isScriptAccessible()) {
                        return@withContext RestorePrecheckResult.ScriptNotFound
                    }
                    val needsProotSetup = SettingsManager.useProotMode &&
                        !top.wsdx233.r2droid.util.ProotInstaller.isEnvironmentReady(appContext)
                    RestorePrecheckResult.Ready(resolved, needsProotSetup)
                }

                when (result) {
                    RestorePrecheckResult.BinaryNotFound -> {
                        projectBinaryAccessible = projectBinaryAccessible + (project.id to false)
                        _uiEvent.send(HomeUiEvent.ShowError(appContext.getString(R.string.home_error_binary_not_found)))
                    }
                    RestorePrecheckResult.ScriptNotFound -> {
                        _uiEvent.send(HomeUiEvent.ShowError(appContext.getString(R.string.home_error_script_not_found)))
                    }
                    is RestorePrecheckResult.Failure -> {
                        _uiEvent.send(
                            HomeUiEvent.ShowError(
                                result.error.message ?: appContext.getString(R.string.home_error_unknown)
                            )
                        )
                    }
                    is RestorePrecheckResult.Ready -> {
                        val prepared = result.project
                        R2PipeManager.pendingFilePath = prepared.binaryPath
                        R2PipeManager.pendingProjectRestore =
                            R2PipeManager.PendingProjectRestore(prepared.id, prepared.scriptPath, prepared.writeCacheEnabled)
                        R2PipeManager.pendingAutoSaveProject = null

                        if (result.needsProotSetup) {
                            _uiEvent.send(HomeUiEvent.ShowMessage(appContext.getString(R.string.proot_setup_required_message)))
                            _uiEvent.send(HomeUiEvent.NavigateToProotSetup)
                        } else {
                            _uiEvent.send(HomeUiEvent.NavigateToProject)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiEvent.send(HomeUiEvent.ShowError(
                    e.message ?: appContext.getString(R.string.home_error_unknown)
                ))
            } finally {
                if (restoringProjectId == project.id) {
                    restoringProjectId = null
                }
            }
        }
    }

    /**
     * Delete a saved project
     */
    fun onDeleteProject(context: Context, project: SavedProject) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                checkNotNull(repository).deleteProject(project.id).getOrThrow()
                loadSavedProjects() // Refresh list
                _uiEvent.send(HomeUiEvent.ShowMessage(
                    appContext.getString(R.string.home_project_deleted)
                ))
            } catch (e: Exception) {
                _uiEvent.send(HomeUiEvent.ShowError(
                    e.message ?: appContext.getString(R.string.home_error_unknown)
                ))
            }
        }
    }

    private suspend fun resolvePath(context: Context, uri: Uri): String? {
        // 1. Try to get the real path first
        try {
            val realPath = UriUtils.getPath(context, uri)
            if (realPath != null) {
                val file = File(realPath)
                if (file.exists() && file.canRead()) {
                    return realPath
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback: Copy to internal cache
        return copyContentUriToCache(context, uri)
    }

    private fun copyContentUriToCache(context: Context, uri: Uri): String? {
        var targetFile: File? = null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            // Use timestamp + random suffix to ensure unique path, forcing R2 to treat it as a new file
            val fileName = "r2_target_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"

            // Use custom project home if set, otherwise cache dir
            val baseDir = SettingsManager.projectHome?.let { File(it) }
                ?.takeIf { it.exists() && it.isDirectory && it.canWrite() }
                ?: context.cacheDir

            val file = File(baseDir, fileName)
            targetFile = file

            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            targetFile?.let { runCatching { it.delete() } }
            e.printStackTrace()
            null
        }
    }

    fun onSettingsClicked() {
        viewModelScope.launch {
            _uiEvent.send(HomeUiEvent.NavigateToSettings)
        }
    }

    fun onAboutClicked() {
        viewModelScope.launch {
            _uiEvent.send(HomeUiEvent.NavigateToAbout)
        }
    }

    fun onFeaturesClicked() {
        viewModelScope.launch {
            _uiEvent.send(HomeUiEvent.NavigateToFeatures)
        }
    }
}

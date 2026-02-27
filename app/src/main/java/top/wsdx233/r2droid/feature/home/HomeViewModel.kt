package top.wsdx233.r2droid.feature.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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
    data class ShowError(val message: String) : HomeUiEvent()
    data class ShowMessage(val message: String) : HomeUiEvent()
}

class HomeViewModel : ViewModel() {
    private val _uiEvent = Channel<HomeUiEvent>()
    val uiEvent: Flow<HomeUiEvent> = _uiEvent.receiveAsFlow()

    // Saved projects from repository
    var savedProjects by mutableStateOf<List<SavedProject>>(emptyList())
        private set
    
    // Loading state
    var isLoadingProjects by mutableStateOf(false)
        private set

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
                savedProjects = repository?.getAllProjects() ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingProjects = false
            }
        }
    }

    fun onFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // 如果之前的会话未关闭，先强制关闭（不等待 mutex，避免阻塞）
                if (R2PipeManager.isConnected) {
                    R2PipeManager.forceClose()
                }

                // Determine file path. 
                // Since R2Pipe likely requires a real file path (native access),
                // if it's a content URI, we might need to copy it to a temp file.
                val filePath = resolvePath(context, uri)

                if (filePath != null) {
                    R2PipeManager.pendingFilePath = filePath
                    R2PipeManager.pendingRestoreFlags = null // Regular open, no restore
                    _uiEvent.send(HomeUiEvent.NavigateToProject)
                } else {
                    _uiEvent.send(HomeUiEvent.ShowError(context.getString(R.string.home_error_resolve_path)))
                }
            } catch (e: Exception) {
                _uiEvent.send(HomeUiEvent.ShowError(e.message ?: context.getString(R.string.home_error_unknown)))
            }
        }
    }

    /**
     * Restore a saved project
     */
    fun onRestoreProject(context: Context, project: SavedProject) {
        viewModelScope.launch {
            try {
                // Check if binary is accessible
                if (!project.isBinaryAccessible()) {
                    _uiEvent.send(HomeUiEvent.ShowError(
                        context.getString(R.string.home_error_binary_not_found)
                    ))
                    return@launch
                }

                // Check if script is accessible
                if (!project.isScriptAccessible()) {
                    _uiEvent.send(HomeUiEvent.ShowError(
                        context.getString(R.string.home_error_script_not_found)
                    ))
                    return@launch
                }

                // Close existing session if any（强制关闭，不等待 mutex）
                if (R2PipeManager.isConnected) {
                    R2PipeManager.forceClose()
                }

                // Set pending file path and restore script path
                R2PipeManager.pendingFilePath = project.binaryPath
                R2PipeManager.pendingRestoreFlags = project.scriptPath  // Store script path only
                R2PipeManager.pendingProjectId = project.id

                _uiEvent.send(HomeUiEvent.NavigateToProject)
            } catch (e: Exception) {
                _uiEvent.send(HomeUiEvent.ShowError(
                    e.message ?: context.getString(R.string.home_error_unknown)
                ))
            }
        }
    }

    /**
     * Delete a saved project
     */
    fun onDeleteProject(context: Context, project: SavedProject) {
        viewModelScope.launch {
            try {
                repository?.deleteProject(project.id)
                loadSavedProjects() // Refresh list
                _uiEvent.send(HomeUiEvent.ShowMessage(
                    context.getString(R.string.home_project_deleted)
                ))
            } catch (e: Exception) {
                _uiEvent.send(HomeUiEvent.ShowError(
                    e.message ?: context.getString(R.string.home_error_unknown)
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
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            // Use timestamp to ensure unique path, forcing R2 to treat it as a new file
            // Also try to preserve extension if possible (though R2 detects by magic bytes usually)
            val fileName = "r2_target_${System.currentTimeMillis()}" 
            
            // Use custom project home if set, otherwise cache dir
            val baseDir = SettingsManager.projectHome?.let { File(it) }
                ?.takeIf { it.exists() && it.isDirectory && it.canWrite() } 
                ?: context.cacheDir
                
            val file = File(baseDir, fileName)
            
            // Cleanup old temp files to save space? (Optional)
            // context.cacheDir.listFiles()?.forEach { if (it.name.startsWith("r2_target")) it.delete() }
            
            val outputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
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

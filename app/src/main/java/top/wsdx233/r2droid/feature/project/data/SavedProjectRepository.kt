package top.wsdx233.r2droid.feature.project.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.SavedProject
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File
import java.util.UUID

/**
 * Repository for managing saved projects.
 * Projects are saved in: filesDir/projects/[projectId]/
 *   - project.r2: R2 script file (analysis data)
 *   - project.json: Metadata file
 */
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wsdx233.r2droid.core.data.prefs.SettingsManager

class SavedProjectRepository @Inject constructor(@ApplicationContext private val context: Context) {
    
    companion object {
        private const val TAG = "SavedProjectRepo"
        private const val PROJECTS_DIR = "projects"
        private const val SCRIPT_FILENAME = "project.r2"
        private const val METADATA_FILENAME = "project.json"
        private const val INDEX_FILENAME = "index.json"
    }

    private val projectsDir: File
        get() {
            val customHome = SettingsManager.projectHome
            val base = if (customHome != null) {
                val dir = File(customHome, PROJECTS_DIR)
                if (dir.exists() || dir.mkdirs()) dir
                else File(context.filesDir, PROJECTS_DIR)
            } else {
                File(context.filesDir, PROJECTS_DIR)
            }
            if (!base.exists()) base.mkdirs()
            return base
        }

    private val indexFile: File
        get() = File(projectsDir, INDEX_FILENAME)

    /**
     * Get all saved projects
     */
    suspend fun getAllProjects(): List<SavedProject> = withContext(Dispatchers.IO) {
        try {
            if (!indexFile.exists()) return@withContext emptyList()
            
            val jsonArray = JSONArray(indexFile.readText())
            val projects = mutableListOf<SavedProject>()
            
            var indexDirty = false
            for (i in 0 until jsonArray.length()) {
                try {
                    var project = SavedProject.fromJson(jsonArray.getJSONObject(i))
                    if (!project.isScriptAccessible()) {
                        // Try to fix by using current projectsDir
                        val fixedPath = File(projectsDir, "${project.id}/$SCRIPT_FILENAME").absolutePath
                        if (File(fixedPath).exists()) {
                            project = project.copy(scriptPath = fixedPath)
                            jsonArray.put(i, project.toJson())
                            indexDirty = true
                        }
                    }
                    if (project.isScriptAccessible()) {
                        projects.add(project)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse project at index $i", e)
                }
            }
            if (indexDirty) {
                try { indexFile.writeText(jsonArray.toString(2)) } catch (_: Exception) {}
            }
            
            // Sort by last modified, newest first
            projects.sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load projects", e)
            emptyList()
        }
    }

    /**
     * Save current analysis as a new project.
     * Uses R2 command "PS script_path" to export script file.
     * 
     * @param name Display name for the project
     * @param analysisLevel The analysis level used (e.g., "aaa", "aaaa")
     * @return Result containing the saved project or error
     */
    suspend fun saveCurrentProject(
        name: String,
        analysisLevel: String = ""
    ): Result<SavedProject> = withContext(Dispatchers.IO) {
        try {
            val binaryPath = R2PipeManager.currentFilePath 
                ?: return@withContext Result.failure(IllegalStateException("No file is currently open"))

            // Generate unique ID
            val projectId = UUID.randomUUID().toString().take(8)
            val projectDir = File(projectsDir, projectId)
            if (!projectDir.exists()) projectDir.mkdirs()

            val scriptFile = File(projectDir, SCRIPT_FILENAME)
            val metadataFile = File(projectDir, METADATA_FILENAME)

            // Get binary info from R2
            val fileSize = try {
                File(binaryPath).length()
            } catch (e: Exception) {
                0L
            }

            // Get arch and bin type from R2
            val (archType, binType) = getFileInfo()

            // Save analysis using R2 command: PS script_path
            // This exports all analysis data (flags, comments, functions, etc.)
            val saveCmd = "PS ${scriptFile.absolutePath}"
            val result = R2PipeManager.execute(saveCmd)
            
            if (result.isFailure) {
                return@withContext Result.failure(
                    result.exceptionOrNull() ?: Exception("Failed to save analysis")
                )
            }

            // Create project metadata
            val now = System.currentTimeMillis()
            val project = SavedProject(
                id = projectId,
                name = name.ifBlank { File(binaryPath).name },
                binaryPath = binaryPath,
                scriptPath = scriptFile.absolutePath,
                createdAt = now,
                lastModified = now,
                fileSize = fileSize,
                archType = archType,
                binType = binType,
                analysisLevel = analysisLevel
            )

            // Save metadata
            metadataFile.writeText(project.toJson().toString(2))

            // Update index
            updateIndex(project)

            Log.d(TAG, "Project saved: ${project.name} at ${project.scriptPath}")
            Result.success(project)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save project", e)
            Result.failure(e)
        }
    }

    /**
     * Update existing project with current analysis
     */
    suspend fun updateProject(projectId: String): Result<SavedProject> = withContext(Dispatchers.IO) {
        try {
            val existingProject = getProjectById(projectId)
                ?: return@withContext Result.failure(IllegalArgumentException("Project not found: $projectId"))

            val binaryPath = R2PipeManager.currentFilePath
                ?: return@withContext Result.failure(IllegalStateException("No file is currently open"))

            // Verify it's the same binary
            if (existingProject.binaryPath != binaryPath) {
                return@withContext Result.failure(
                    IllegalStateException("Current file doesn't match project binary")
                )
            }

            val scriptFile = File(existingProject.scriptPath)

            // Save updated analysis using PS command
            val saveCmd = "PS ${scriptFile.absolutePath}"
            val result = R2PipeManager.execute(saveCmd)

            if (result.isFailure) {
                return@withContext Result.failure(
                    result.exceptionOrNull() ?: Exception("Failed to update analysis")
                )
            }

            // Update metadata
            val updatedProject = existingProject.copy(lastModified = System.currentTimeMillis())
            val metadataFile = File(scriptFile.parentFile, METADATA_FILENAME)
            metadataFile.writeText(updatedProject.toJson().toString(2))

            // Update index
            updateIndex(updatedProject)

            Result.success(updatedProject)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update project", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a saved project
     */
    suspend fun deleteProject(projectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(projectsDir, projectId)
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            }

            // Update index
            removeFromIndex(projectId)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete project", e)
            Result.failure(e)
        }
    }

    /**
     * Get project by ID
     */
    suspend fun getProjectById(projectId: String): SavedProject? = withContext(Dispatchers.IO) {
        getAllProjects().find { it.id == projectId }
    }

    /**
     * Get the R2 command flags to restore a project.
     * Returns: "-i scriptPath binaryPath"
     */
    fun getRestoreCommand(project: SavedProject): String {
        return "-i \"${project.scriptPath}\" \"${project.binaryPath}\""
    }

    /**
     * Get file info from R2 (arch and bin type)
     */
    private suspend fun getFileInfo(): Pair<String, String> {
        return try {
            val result = R2PipeManager.executeJson("ij")
            if (result.isSuccess) {
                val json = JSONObject(result.getOrDefault("{}"))
                val bin = json.optJSONObject("bin") ?: JSONObject()
                val arch = bin.optString("arch", "unknown")
                val bintype = bin.optString("bintype", "unknown")
                Pair(arch, bintype)
            } else {
                Pair("unknown", "unknown")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file info", e)
            Pair("unknown", "unknown")
        }
    }

    /**
     * Update the index file with a project
     */
    private fun updateIndex(project: SavedProject) {
        try {
            val projects = if (indexFile.exists()) {
                val jsonArray = JSONArray(indexFile.readText())
                val list = mutableListOf<JSONObject>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.optString("id") != project.id) {
                        list.add(obj)
                    }
                }
                list
            } else {
                mutableListOf()
            }

            // Add updated project
            projects.add(0, project.toJson())

            // Write back
            val jsonArray = JSONArray()
            projects.forEach { jsonArray.put(it) }
            indexFile.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update index", e)
        }
    }

    /**
     * Remove a project from the index
     */
    private fun removeFromIndex(projectId: String) {
        try {
            if (!indexFile.exists()) return

            val jsonArray = JSONArray(indexFile.readText())
            val newArray = JSONArray()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optString("id") != projectId) {
                    newArray.put(obj)
                }
            }

            indexFile.writeText(newArray.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove from index", e)
        }
    }
}

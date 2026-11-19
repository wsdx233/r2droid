package top.wsdx233.r2droid.feature.project.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.SavedProject
import top.wsdx233.r2droid.util.ProjectFileUtils
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.BufferedReader
import java.io.File
import java.io.Writer
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

    private fun writeIndexAtomically(content: String) {
        val atomicFile = android.util.AtomicFile(indexFile)
        val fos = atomicFile.startWrite()
        try {
            fos.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            atomicFile.failWrite(fos)
            throw e
        }
    }

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
                try { writeIndexAtomically(jsonArray.toString(2)) } catch (_: Exception) {}
            }
            
            // Sort by last modified, newest first
            projects.sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load projects", e)
            emptyList()
        }
    }

    internal suspend fun saveAnalysisScript(session: R2PipeManager.R2Session, scriptFile: File): Result<Unit> {
        if (!session.writeCacheEnabled) {
            return R2PipeManager.execute(session, "PS ${scriptFile.absolutePath}").map { Unit }
        }
        return R2PipeManager.executeStream(session, "PS /dev/stdout") { input ->
            val outputFile = AtomicFile(scriptFile)
            val output = outputFile.startWrite()
            try {
                val writer = output.bufferedWriter()
                writeUnpatchedAnalysisScript(input.bufferedReader(), writer)
                writer.flush()
                outputFile.finishWrite(output)
            } catch (e: Exception) {
                outputFile.failWrite(output)
                throw e
            }
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
        session: R2PipeManager.R2Session,
        name: String,
        analysisLevel: String = "",
        copyBinary: Boolean = false,
        ownsCurrentBinaryCopy: Boolean = false,
        currentOwnedBinaryPath: String? = null
    ): Result<SavedProject> = withContext(Dispatchers.IO) {
        try {
            val currentBinaryPath = session.projectPath ?: session.customCommand
                ?: return@withContext Result.failure(IllegalStateException("No file is currently open"))
            // Generate unique ID first so optional binary copies can live inside the project folder.
            val projectId = UUID.randomUUID().toString().take(8)
            val projectDir = File(projectsDir, projectId)
            if (!projectDir.exists()) projectDir.mkdirs()

            val binaryPath = when {
                copyBinary -> copyBinaryIntoProjectDir(projectDir, currentBinaryPath)
                // Analysis startup already opened an app-owned copy. Keep its path and descriptor.
                ownsCurrentBinaryCopy && session.writeCacheEnabled -> currentOwnedBinaryPath ?: currentBinaryPath
                ownsCurrentBinaryCopy -> moveOwnedBinaryIntoProjectDir(projectDir, currentOwnedBinaryPath ?: currentBinaryPath)
                else -> currentBinaryPath
            }
            if (!session.writeCacheEnabled && binaryPath != currentBinaryPath && R2PipeManager.activeSessionPipe === session.pipe) {
                R2PipeManager.updateCurrentFilePath(binaryPath)
            }

            val scriptFile = File(projectDir, SCRIPT_FILENAME)
            val metadataFile = File(projectDir, METADATA_FILENAME)

            // Get binary info from R2
            val fileSize = try {
                File(binaryPath).length()
            } catch (e: Exception) {
                0L
            }

            // Get arch and bin type from R2
            val (archType, binType) = getFileInfo(session)

            val result = saveAnalysisScript(session, scriptFile)
            
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
                analysisLevel = analysisLevel,
                ownsBinaryCopy = copyBinary || ownsCurrentBinaryCopy,
                writeCacheEnabled = session.writeCacheEnabled
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
    suspend fun updateProject(session: R2PipeManager.R2Session, projectId: String): Result<SavedProject> = withContext(Dispatchers.IO) {
        try {
            val existingProject = getProjectById(projectId)
                ?: return@withContext Result.failure(IllegalArgumentException("Project not found: $projectId"))

            val binaryPath = session.projectPath ?: session.customCommand
                ?: return@withContext Result.failure(IllegalStateException("No file is currently open"))

            // A cached Save As may associate the session with a project-owned copy while leaving
            // the open descriptor attached to the original. Allow analysis updates from that session.
            val isAssociatedCacheProject = session.writeCacheEnabled && session.currentProjectId == projectId &&
                existingProject.ownsBinaryCopy
            if (existingProject.binaryPath != binaryPath && !isAssociatedCacheProject) {
                return@withContext Result.failure(
                    IllegalStateException("Current file doesn't match project binary")
                )
            }

            val scriptFile = File(existingProject.scriptPath)

            val result = saveAnalysisScript(session, scriptFile)

            if (result.isFailure) {
                return@withContext Result.failure(
                    result.exceptionOrNull() ?: Exception("Failed to update analysis")
                )
            }

            // Update metadata
            val updatedProject = existingProject.copy(
                lastModified = System.currentTimeMillis(),
                writeCacheEnabled = session.writeCacheEnabled
            )
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

    private fun copyBinaryIntoProjectDir(projectDir: File, sourcePath: String): String {
        val source = File(sourcePath)
        require(source.exists() && source.canRead()) { "Source file is not readable: $sourcePath" }
        val binDir = File(projectDir, "binary").apply { mkdirs() }
        val target = ProjectFileUtils.uniqueFile(binDir, source.name.ifBlank { "binary" })
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.setReadable(true, true)
        target.setWritable(true, true)
        return target.absolutePath
    }

    private fun moveOwnedBinaryIntoProjectDir(projectDir: File, sourcePath: String): String {
        val source = File(sourcePath)
        require(source.exists() && source.canRead()) { "Source file is not readable: $sourcePath" }
        val binDir = File(projectDir, "binary").apply { mkdirs() }
        val target = ProjectFileUtils.uniqueFile(binDir, source.name.ifBlank { "binary" })
        val moved = runCatching { source.renameTo(target) }.getOrDefault(false)
        if (!moved) {
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            runCatching { source.delete() }
        }
        target.setReadable(true, true)
        target.setWritable(true, true)
        return target.absolutePath
    }

    /**
     * Delete a saved project
     */
    suspend fun deleteProject(projectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(projectsDir, projectId)
            val project = getProjectById(projectId)
            val copiedBinaryPath = project?.binaryPath
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            }
            if (project?.ownsBinaryCopy == true && !copiedBinaryPath.isNullOrBlank()) {
                runCatching { File(copiedBinaryPath).delete() }
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
    private suspend fun getFileInfo(session: R2PipeManager.R2Session): Pair<String, String> {
        return try {
            val result = R2PipeManager.execute(session, "ij", markDirty = false)
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
            writeIndexAtomically(jsonArray.toString(2))
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

            writeIndexAtomically(newArray.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove from index", e)
        }
    }
}

private val CACHE_OPTION = Regex("^['\"]?e\\s+io\\.cache(?:\\.[a-z]+)?\\s*=.*$")
private val IO_OPEN = Regex("^(?:(?:o|onnu)\\s+.+|o=\\d+)$")

internal fun writeUnpatchedAnalysisScript(reader: BufferedReader, writer: Writer) {
    val header = reader.readLine()
    check(header?.startsWith("# r2 rdb project file") == true) { "Failed to save analysis script" }
    writer.appendLine(header)
    var beforeAnalysis = true
    reader.lineSequence().forEach { line ->
        if (line == "# functions") beforeAnalysis = false
        val command = line.trimStart()
        // PS emits both config and a wc* dump; the saved project restores neither cache contents nor settings from the script.
        if (command.startsWith("wx ") || CACHE_OPTION.matches(command)) return@forEach
        // The app already opened the target, which might be a project-owned copy at a new path.
        if (beforeAnalysis && IO_OPEN.matches(command)) return@forEach
        writer.appendLine(line)
    }
}

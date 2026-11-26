package top.wsdx233.r2droid.feature.project.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.SavedProject
import top.wsdx233.r2droid.feature.annotations.replayableFormatDeclarations
import top.wsdx233.r2droid.util.ProjectFileUtils
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
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
        internal const val LEGACY_SCRIPT_BACKUP_FILENAME = "project.legacy.r2"
        private const val RESTORE_TEMP_DIR = "r2-restore"
        private const val METADATA_FILENAME = "project.json"
        private const val INDEX_FILENAME = "index.json"
        // Home and Project may each have a repository instance; serialize index mutations.
        private val projectFilesMutex = Mutex()
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

    /**
     * Get all saved projects
     */
    suspend fun getAllProjects(): List<SavedProject> = withContext(Dispatchers.IO) {
        projectFilesMutex.withLock {
            try {
                val root = projectsDir
                readIndex(root).map { resolveScriptPath(root, it) }
                    .filter { it.isScriptAccessible() }
                    .sortedByDescending { it.lastModified }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to load projects", e)
                emptyList()
            }
        }
    }

    internal suspend fun saveAnalysisScript(session: R2PipeManager.R2Session, scriptFile: File): Result<Unit> =
        R2PipeManager.exportProjectReplay(session) { replay, fd, formats ->
            writeAtomically(scriptFile) { writeProjectReplay(replay.bufferedReader(), it, fd, formats) }
        }

    /**
     * Save current analysis as a new project.
     * Snapshot the open binary and its analysis under the same session lock.
     * 
     * @param name Display name for the project
     * @param analysisLevel The analysis level used (e.g., "aaa", "aaaa")
     * @return Result containing the saved project or error
     */
    internal suspend fun saveCurrentProject(
        session: R2PipeManager.R2Session,
        name: String,
        analysisLevel: String = "",
        copyBinary: Boolean = false,
        ownsCurrentBinaryCopy: Boolean = false,
        currentOwnedBinaryPath: String? = null
    ): Result<ProjectSave> = withContext(Dispatchers.IO) {
        projectFilesMutex.withLock {
            val root = projectsDir
            val projectDir = File(root, UUID.randomUUID().toString().take(8))
            var created = false
            var published = false
            try {
                val sourcePath = session.projectPath ?: session.customCommand
                    ?: error("No file is currently open")
                val source = File(sourcePath)
                val knownProjects = readIndex(root)
                val startupCopies = ProjectFileUtils.projectBinariesDir(context)
                val hasSavedReference = knownProjects.any { sameBinary(it.binaryPath, source) }
                // A project-home change may hide the source's ownership record. Do not make
                // the new project depend on that unknown file's lifecycle.
                val sourceProjectId = session.currentProjectId
                val missingSourceRecord = sourceProjectId != null && knownProjects.none { it.id == sourceProjectId }
                val inStartupCopies = canContain(startupCopies, source)
                val inProjectStorage = canContain(root, source) || canContain(File(context.filesDir, PROJECTS_DIR), source)
                val managed = missingSourceRecord || inStartupCopies || inProjectStorage ||
                    canContain(context.cacheDir, source) ||
                    context.externalCacheDirs.filterNotNull().any { canContain(it, source) } ||
                    (source.name.startsWith("r2_target_") && SettingsManager.projectHome?.let {
                        source.parentFile?.canonicalFile == File(it).canonicalFile
                    } == true) || knownProjects.any { it.ownsBinaryCopy && sameBinary(it.binaryPath, source) }
                val startupCopy = ownsCurrentBinaryCopy && sourcePath == currentOwnedBinaryPath
                val plan = planProjectSave(
                    hasSourceProject = sourceProjectId != null,
                    copyRequested = copyBinary,
                    managed = managed || startupCopy,
                    // A saved project must survive Android evicting the file-picker cache.
                    canAdopt = !hasSavedReference && (startupCopy || inStartupCopies)
                )
                check(projectDir.mkdir()) { "Could not create project directory" }
                created = true
                val script = File(projectDir, SCRIPT_FILENAME)
                val (arch, binType) = getFileInfo(session)

                R2PipeManager.exportProjectReplay(session) { replay, fd, formats ->
                    writeAtomically(script) { writeProjectReplay(replay.bufferedReader(), it, fd, formats) }
                    // Hold the pipe mutex through the snapshot so no queued r2 write can slip
                    // between the analysis dump and the copy of the bytes on disk.
                    val binaryPath = if (plan.copyBinary) {
                        copyBinaryIntoProjectDir(projectDir, sourcePath)
                    } else {
                        sourcePath
                    }
                    val now = System.currentTimeMillis()
                    val project = SavedProject(
                        id = projectDir.name,
                        name = name.ifBlank { source.name },
                        binaryPath = binaryPath,
                        scriptPath = script.absolutePath,
                        createdAt = now,
                        lastModified = now,
                        fileSize = File(binaryPath).length(),
                        archType = arch,
                        binType = binType,
                        analysisLevel = analysisLevel,
                        ownsBinaryCopy = plan.ownsBinary,
                        writeCacheEnabled = session.writeCacheEnabled
                    )
                    writeProject(root, project, knownProjects)
                    published = true
                    if (plan.attachToSession) R2PipeManager.recordProjectSave(session, project.id, binaryPath)
                    ProjectSave(plan.attachToSession, project.writeCacheEnabled)
                }.getOrThrow().let { Result.success(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to save project", e)
                Result.failure(e)
            } finally {
                // The only files we may remove on failure are files in the new project directory.
                if (created && !published) projectDir.deleteRecursively()
            }
        }
    }

    /**
     * Update existing project with current analysis
     */
    suspend fun updateProject(session: R2PipeManager.R2Session, projectId: String): Result<SavedProject> =
        withContext(Dispatchers.IO) {
            projectFilesMutex.withLock {
                try {
                    val root = projectsDir
                    val knownProjects = readIndex(root)
                    val project = knownProjects.find { it.id == projectId }?.let { resolveScriptPath(root, it) }
                        ?: error("Project not found: $projectId")
                    val binaryPath = session.projectPath ?: session.customCommand
                    check(session.currentProjectId == projectId && project.binaryPath == binaryPath) {
                        "Current file doesn't match project binary"
                    }
                    val scriptFile = File(project.scriptPath)
                    R2PipeManager.exportProjectReplay(session) { replay, fd, formats ->
                        updateProjectScriptWithLegacyBackup(scriptFile) { target ->
                            writeAtomically(target) { writeProjectReplay(replay.bufferedReader(), it, fd, formats) }
                        }
                        val updated = project.copy(
                            lastModified = System.currentTimeMillis(),
                            writeCacheEnabled = session.writeCacheEnabled
                        )
                        writeProject(root, updated, knownProjects)
                        R2PipeManager.recordProjectSave(session, projectId, project.binaryPath)
                        updated
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Failed to update project", e)
                    Result.failure(e)
                }
            }
        }

    private fun copyBinaryIntoProjectDir(projectDir: File, sourcePath: String): String {
        val source = File(sourcePath)
        require(source.isFile && source.canRead()) { "Source file is not readable: $sourcePath" }
        val binDir = File(projectDir, "binary").apply { mkdirs() }
        val target = ProjectFileUtils.uniqueFile(binDir, source.name.ifBlank { "binary" })
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.setReadable(true, true)
        target.setWritable(true, true)
        return target.absolutePath
    }

    /**
     * Delete a saved project
     */
    suspend fun deleteProject(projectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        projectFilesMutex.withLock {
            try {
                val root = projectsDir
                val projectDir = File(root, projectId)
                val projects = readIndex(root)
                val project = projects.find { it.id == projectId } ?: return@withLock Result.success(Unit)
                val remaining = projects.filterNot { it.id == projectId }
                val protectedPaths = remaining.flatMap { listOf(it.binaryPath, it.scriptPath) } +
                    R2PipeManager.openProjectBinaryPaths()
                val binary = File(project.binaryPath)

                // Commit removal from the index before touching files. A legacy project or an
                // open descriptor may still use a file inside this directory. Keep its path intact.
                writeIndexAtomically(root, JSONArray(remaining.map { it.toJson() }))
                if (canDeleteProjectDirectory(projectDir, protectedPaths)) projectDir.deleteRecursively()
                if (project.ownsBinaryCopy && !canContain(projectDir, binary) &&
                    isOwnedStorage(root, binary) && protectedPaths.none { sameBinary(it, binary) }
                ) {
                    binary.delete()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to delete project", e)
                Result.failure(e)
            }
        }
    }

    private fun isOwnedStorage(root: File, file: File): Boolean = canContain(root, file) ||
        canContain(ProjectFileUtils.projectBinariesDir(context), file)

    /**
     * Get project by ID
     */
    suspend fun getProjectById(projectId: String): SavedProject? = withContext(Dispatchers.IO) {
        getAllProjects().find { it.id == projectId }
    }

    /** Validate a saved project script without modifying the stored project.r2 or index. */
    suspend fun validateProjectForRestore(projectId: String): Result<SavedProject> = withContext(Dispatchers.IO) {
        projectFilesMutex.withLock {
            try {
                val root = projectsDir
                val project = readIndex(root).find { it.id == projectId }?.let { resolveScriptPath(root, it) }
                    ?: error("Project not found: $projectId")
                val script = File(project.scriptPath)
                script.bufferedReader().use { reader ->
                    writeProjectReplay(reader, NullWriter)
                }
                Result.success(project)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to validate project replay", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Generate a temporary filtered replay script from the original project.r2 without mutating
     * the stored project archive, run [block], and delete the temporary file afterwards.
     */
    internal suspend fun <T> withTemporaryReplayScript(
        projectId: String,
        block: suspend (tempScript: File, report: ProjectReplayReport, project: SavedProject) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        val (project, prepared) = projectFilesMutex.withLock {
            try {
                val root = projectsDir
                val found = readIndex(root).find { it.id == projectId }?.let { resolveScriptPath(root, it) }
                    ?: error("Project not found: $projectId")
                val script = File(found.scriptPath)
                val tempDir = File(context.cacheDir, RESTORE_TEMP_DIR)
                found to createTemporaryReplayScript(script, tempDir)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to prepare temporary project replay", e)
                return@withContext Result.failure(e)
            }
        }
        try {
            Result.success(block(prepared.file, prepared.report, project))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to replay project script", e)
            Result.failure(e)
        } finally {
            runCatching { prepared.file.delete() }
        }
    }

    /** Returns true if an original legacy project script (either untouched or backed up) is available. */
    suspend fun hasOriginalLegacyScript(projectId: String): Boolean = withContext(Dispatchers.IO) {
        projectFilesMutex.withLock {
            try {
                val root = projectsDir
                val project = readIndex(root).find { it.id == projectId }?.let { resolveScriptPath(root, it) }
                    ?: return@withLock false
                resolveOriginalLegacyScript(File(project.scriptPath)) != null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to inspect legacy project script", e)
                false
            }
        }
    }

    /** Export the untouched original legacy project script byte-for-byte without modifying storage. */
    suspend fun exportOriginalLegacyScript(
        projectId: String,
        openOutput: () -> OutputStream?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        projectFilesMutex.withLock {
            try {
                val root = projectsDir
                val project = readIndex(root).find { it.id == projectId }?.let { resolveScriptPath(root, it) }
                    ?: error("Project not found: $projectId")
                val source = resolveOriginalLegacyScript(File(project.scriptPath))
                    ?: error("Original legacy script is not available for this project")
                val output = openOutput() ?: error("Unable to open destination file")
                output.use { out ->
                    source.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to export original legacy script", e)
                Result.failure(e)
            }
        }
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
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to get file info", e)
            Pair("unknown", "unknown")
        }
    }

    /**
     * Update the index file with a project
     */
    private fun writeProject(root: File, project: SavedProject, knownProjects: List<SavedProject>) {
        val index = JSONArray()
        index.put(project.toJson())
        knownProjects.filterNot { it.id == project.id }.forEach { index.put(it.toJson()) }
        val metadata = File(File(project.scriptPath).parentFile, METADATA_FILENAME)
        writeAtomically(metadata) { it.write(project.toJson().toString(2)) }
        writeIndexAtomically(root, index)
    }

    private fun writeIndexAtomically(root: File, index: JSONArray) {
        writeAtomically(File(root, INDEX_FILENAME)) { it.write(index.toString(2)) }
    }

    private fun writeAtomically(file: File, write: (Writer) -> Unit) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            val writer = output.bufferedWriter()
            write(writer)
            writer.flush()
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
    }

    private fun readIndex(root: File): List<SavedProject> {
        val atomic = AtomicFile(File(root, INDEX_FILENAME))
        val json = try {
            JSONArray(atomic.openRead().bufferedReader().use { it.readText() })
        } catch (e: java.io.FileNotFoundException) {
            if (atomic.baseFile.exists()) throw e
            return emptyList()
        }
        return (0 until json.length()).mapNotNull { index -> json.optJSONObject(index)?.let { SavedProject.fromJson(it) } }
    }

    private fun resolveScriptPath(root: File, project: SavedProject): SavedProject {
        if (project.isScriptAccessible()) return project
        val localScript = File(root, "${project.id}/$SCRIPT_FILENAME")
        return if (localScript.isFile) project.copy(scriptPath = localScript.absolutePath) else project
    }
}

internal data class ProjectSave(val attachedToSession: Boolean, val writeCacheEnabled: Boolean)

internal data class ProjectSavePlan(val copyBinary: Boolean, val ownsBinary: Boolean, val attachToSession: Boolean)

internal fun planProjectSave(
    hasSourceProject: Boolean,
    copyRequested: Boolean,
    managed: Boolean,
    canAdopt: Boolean
): ProjectSavePlan {
    val adopt = !hasSourceProject && managed && canAdopt && !copyRequested
    val copy = copyRequested || (managed && !adopt)
    return ProjectSavePlan(copy, copy || adopt, !hasSourceProject && !copy)
}

private fun sameBinary(path: String, binary: File): Boolean =
    path.isNotEmpty() && File(path).canonicalFile == binary.canonicalFile

private fun canContain(directory: File, file: File): Boolean =
    file.canonicalPath.startsWith(directory.canonicalPath + File.separator)

internal fun canDeleteProjectDirectory(directory: File, protectedPaths: List<String>): Boolean =
    protectedPaths.none { path ->
        if (path.isBlank()) return@none false
        val file = File(path)
        canContain(directory, file) || file.absoluteFile.normalize().path
            .startsWith(directory.absoluteFile.normalize().path + File.separator)
    }

private const val PS_HEADER = "# r2 rdb project file"
private const val DESCRIPTOR_NEUTRAL = "# r2droid project replay: uses the already-open binary"
private val FD_SELECTION = Regex("^o\\s*=\\s*(\\d+)$")
private val FD_MAP = Regex("^omu\\s+(\\d+)\\s+(.+)$")
private val REPLAY_OPEN = Regex("^(?:o|on|onn|onnu|on\\+|oo|oo\\+)\\s+.+$")
// Preserve ordinary cmd options, but not command strings that can run during or after replay.
private val REPLAY_HOOK = Regex(
    "^['\"]?e\\s+(?:cmd\\.(?!(?:depth|repeat|hitinfo|undo)\\s*=)[^\\s=]+|cfg\\.debug)\\s*=.*$"
)

internal data class ProjectReplayReport(
    val isLegacyScript: Boolean,
    val filteredLegacyPatches: Boolean,
    val filteredLegacyCacheOptions: Boolean
)

internal data class PreparedReplayScript(
    val file: File,
    val report: ProjectReplayReport
)

private object NullWriter : Writer() {
    override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
    override fun flush() = Unit
    override fun close() = Unit
}

internal fun legacyScriptBackupFile(scriptFile: File): File {
    val parent = checkNotNull(scriptFile.absoluteFile.parentFile) {
        "Project script has no parent directory: ${scriptFile.absolutePath}"
    }
    return File(parent, SavedProjectRepository.LEGACY_SCRIPT_BACKUP_FILENAME)
}

internal fun isLegacyProjectScript(scriptFile: File): Boolean {
    if (!scriptFile.isFile || !scriptFile.canRead()) return false
    return scriptFile.bufferedReader().use { reader ->
        val header = reader.readLine()
        header?.startsWith(PS_HEADER) == true && reader.readLine()?.trim() != DESCRIPTOR_NEUTRAL
    }
}

internal fun resolveOriginalLegacyScript(scriptFile: File): File? {
    val backup = legacyScriptBackupFile(scriptFile)
    if (backup.isFile && backup.canRead()) return backup
    if (isLegacyProjectScript(scriptFile)) return scriptFile
    return null
}

/**
 * Copy an unmigrated legacy project script byte-for-byte into [legacyScriptBackupFile] before the
 * first explicit overwrite. Fails without touching [scriptFile] if backup cannot be verified.
 */
internal fun backupLegacyScriptBeforeOverwrite(scriptFile: File): File? {
    if (!scriptFile.exists()) return null
    require(scriptFile.isFile && scriptFile.canRead()) {
        "Cannot inspect existing project script before update: ${scriptFile.absolutePath}"
    }
    if (!isLegacyProjectScript(scriptFile)) return null
    val backup = legacyScriptBackupFile(scriptFile)
    val expectedBytes = scriptFile.length()
    if (backup.exists()) {
        check(backup.isFile && backup.canRead() && backup.length() == expectedBytes) {
            "Existing legacy script backup does not match the original script: ${backup.absolutePath}"
        }
        return backup
    }
    val parent = checkNotNull(backup.absoluteFile.parentFile) {
        "Legacy script backup has no parent directory: ${backup.absolutePath}"
    }
    check(parent.exists() || parent.mkdirs()) {
        "Could not create directory for legacy script backup: ${parent.absolutePath}"
    }
    val temp = File(parent, ".${backup.name}.${UUID.randomUUID()}.tmp")
    try {
        var copied = 0L
        scriptFile.inputStream().use { input ->
            FileOutputStream(temp).use { output ->
                copied = input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
        check(copied == expectedBytes && temp.length() == expectedBytes) {
            "Incomplete legacy script backup ($copied of $expectedBytes bytes)."
        }
        check(temp.renameTo(backup)) {
            "Could not commit legacy script backup to ${backup.absolutePath}"
        }
        check(backup.isFile && backup.length() == expectedBytes) {
            "Could not verify legacy script backup at ${backup.absolutePath}"
        }
        return backup
    } catch (e: Exception) {
        runCatching { temp.delete() }
        throw e
    }
}

internal fun updateProjectScriptWithLegacyBackup(
    scriptFile: File,
    writeNewScript: (File) -> Unit
): File? {
    val backup = backupLegacyScriptBeforeOverwrite(scriptFile)
    writeNewScript(scriptFile)
    return backup
}

internal fun createTemporaryReplayScript(sourceScript: File, tempDir: File): PreparedReplayScript {
    require(sourceScript.isFile && sourceScript.canRead()) {
        "Project script is not readable: ${sourceScript.absolutePath}"
    }
    check(tempDir.exists() || tempDir.mkdirs()) {
        "Could not create temporary replay directory: ${tempDir.absolutePath}"
    }
    val tempFile = File(tempDir, "restore-${UUID.randomUUID()}.r2")
    try {
        val report = sourceScript.bufferedReader().use { reader ->
            tempFile.bufferedWriter().use { writer ->
                writeProjectReplay(reader, writer)
            }
        }
        return PreparedReplayScript(tempFile, report)
    } catch (e: Exception) {
        runCatching { tempFile.delete() }
        throw e
    }
}

internal fun writeProjectReplay(
    reader: BufferedReader,
    writer: Writer,
    sourceFd: Int? = null,
    formats: String = ""
): ProjectReplayReport {
    val header = reader.readLine()
    check(header?.startsWith(PS_HEADER) == true) { "Failed to save analysis script" }
    writer.appendLine(header)
    writer.appendLine(DESCRIPTOR_NEUTRAL)
    replayableFormatDeclarations(formats).forEach { writer.appendLine(it) }

    val maps = mutableListOf<MatchResult>()
    fun writeMaps(fd: Int?) {
        // Older cached saves omitted o= but retained numeric map lines. If every map uses the
        // same fd, rebind it to the already-open binary; don't guess across multiple fds.
        val onlyFd = maps.firstOrNull()?.groupValues?.get(1)
        val mappedFd = fd?.toString() ?: onlyFd?.takeIf { candidate -> maps.all { it.groupValues[1] == candidate } }
        // Startup has already opened the destination. Replay only maps of the primary binary,
        // using `.` (the current fd) rather than a descriptor number from the other process.
        maps.forEach { if (it.groupValues[1] == mappedFd) writer.appendLine("omu . ${it.groupValues[2]}") }
        maps.clear()
    }

    var firstBodyLine = true
    var isLegacyScript = true
    var filteredLegacyPatches = false
    var filteredLegacyCacheOptions = false
    var beforeAnalysis = true
    reader.lineSequence().forEach { line ->
        val command = line.trim()
        if (firstBodyLine) {
            firstBodyLine = false
            if (command == DESCRIPTOR_NEUTRAL) {
                isLegacyScript = false
                return@forEach
            }
        } else if (command == DESCRIPTOR_NEUTRAL) {
            return@forEach
        }
        // PS includes a wc* dump even outside supported write cache mode. Neither cache
        // settings nor cached bytes should be replayed onto a file; the mode is in metadata.
        if (CACHE_PATCH_COMMAND.matches(command)) {
            filteredLegacyPatches = true
            return@forEach
        }
        if (CACHE_OPTION.matches(command)) {
            filteredLegacyCacheOptions = true
            return@forEach
        }
        if (command == "# functions") {
            writeMaps(sourceFd)
            beforeAnalysis = false
        }
        if (beforeAnalysis) {
            val selectedFd = FD_SELECTION.matchEntire(command)
            if (selectedFd != null) {
                writeMaps(sourceFd ?: selectedFd.groupValues[1].toIntOrNull())
                return@forEach
            }
            val map = FD_MAP.matchEntire(command)
            if (map != null) {
                maps += map
                return@forEach
            }
            if (REPLAY_OPEN.matches(command) || REPLAY_HOOK.matches(command) ||
                command.startsWith("om \$d ") || command.startsWith("pushd ") || command == "popd"
            ) return@forEach
        } else {
            check(!REPLAY_OPEN.matches(command) && !FD_SELECTION.matches(command)) {
                "Project replay attempts to change descriptors after its analysis section starts"
            }
        }
        writer.appendLine(line)
    }
    writeMaps(sourceFd)
    return ProjectReplayReport(
        isLegacyScript = isLegacyScript,
        filteredLegacyPatches = filteredLegacyPatches,
        filteredLegacyCacheOptions = filteredLegacyCacheOptions
    )
}

private val CACHE_PATCH_COMMAND = Regex("^['\"]?wx\\s+.*$")
private val CACHE_OPTION = Regex("^['\"]?e\\s+io\\.cache(?:\\.[a-z]+)?\\s*=.*$")

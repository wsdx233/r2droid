package top.wsdx233.r2droid.util

import android.content.Context
import org.json.JSONArray
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import java.io.File

object AppCacheCleaner {

    data class Result(
        val deletedEntries: Int = 0,
        val freedBytes: Long = 0,
        val sharedCacheCleared: Boolean = true
    )

    private const val PROJECTS_DIR = "projects"
    private const val INDEX_FILENAME = "index.json"

    private val tempFilePrefixes = listOf(
        "r2_target_",
        "frida_rebased_project_",
        "plugin_pick_"
    )

    private val tempFileNames = setOf(
        "r2frida_temp.zip",
        "frida_script.js",
        "frida_write.js",
        "frida_batch_write.js"
    )

    fun clearAppCache(context: Context): Result {
        val hasOpenSessions = R2PipeManager.sessions.value.isNotEmpty()
        val protectedPaths = protectedPaths(context)
        val protectedCacheEntries = hasProtectedCacheEntries(context, protectedPaths)
        val stats = Stats()

        if (!hasOpenSessions) {
            clearDirectoryContents(context.cacheDir, protectedPaths, stats)
            context.externalCacheDirs.filterNotNull().forEach {
                clearDirectoryContents(it, protectedPaths, stats)
            }

            val xdgCacheDir = File(context.filesDir, ".cache")
            clearDirectoryContents(xdgCacheDir, protectedPaths, stats)
            runCatching { xdgCacheDir.mkdirs() }
        } else {
            clearUnusedTempFilesInDirectory(context.cacheDir, protectedPaths, stats)
            context.externalCacheDirs.filterNotNull().forEach {
                clearUnusedTempFilesInDirectory(it, protectedPaths, stats)
            }
        }

        return Result(
            deletedEntries = stats.deletedEntries,
            freedBytes = stats.freedBytes,
            sharedCacheCleared = !hasOpenSessions && !protectedCacheEntries
        )
    }

    fun clearAfterProjectExit(context: Context): Result = clearAppCache(context)

    private fun protectedPaths(context: Context): Set<String> {
        return buildSet {
            R2PipeManager.sessions.value.values
                .asSequence()
                .mapNotNull { it.projectPath }
                .map(::normalizePath)
                .forEach(::add)

            savedProjectBinaryPaths(context)
                .map(::normalizePath)
                .forEach(::add)

            add(normalizePath(ProjectFileUtils.projectBinariesDir(context).absolutePath))
        }
    }

    private fun savedProjectBinaryPaths(context: Context): Set<String> {
        val indexFile = File(projectsDir(context), INDEX_FILENAME)
        if (!indexFile.exists()) return emptySet()

        return runCatching {
            val jsonArray = JSONArray(indexFile.readText())
            buildSet {
                for (i in 0 until jsonArray.length()) {
                    val binaryPath = jsonArray.optJSONObject(i)
                        ?.optString("binaryPath")
                        ?.trim()
                        .orEmpty()
                    if (binaryPath.isNotEmpty()) {
                        add(binaryPath)
                    }
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun projectsDir(context: Context): File {
        val customHome = SettingsManager.projectHome?.takeIf { it.isNotBlank() }
        return if (customHome != null) {
            File(customHome, PROJECTS_DIR)
        } else {
            File(context.filesDir, PROJECTS_DIR)
        }
    }

    private fun hasProtectedCacheEntries(context: Context, protectedPaths: Set<String>): Boolean {
        val cacheRoots = buildList {
            add(context.cacheDir)
            addAll(context.externalCacheDirs.filterNotNull())
            add(File(context.filesDir, ".cache"))
        }.map { normalizePath(it.absolutePath) }

        return protectedPaths.any { protectedPath ->
            cacheRoots.any { root ->
                protectedPath == root || protectedPath.startsWith("$root${File.separator}")
            }
        }
    }

    private fun clearUnusedTempFilesInDirectory(
        directory: File?,
        protectedPaths: Set<String>,
        stats: Stats
    ) {
        if (directory == null || !directory.exists() || !directory.isDirectory) return

        directory.listFiles()?.forEach { file ->
            val normalized = normalizePath(file.absolutePath)
            if (file.isFile && isAppManagedTempFile(file) && normalized !in protectedPaths) {
                deleteTarget(file, stats)
            }
        }
    }

    private fun clearDirectoryContents(
        directory: File?,
        protectedPaths: Set<String>,
        stats: Stats
    ) {
        if (directory == null || !directory.exists() || !directory.isDirectory) return
        directory.listFiles()?.forEach { clearPath(it, protectedPaths, stats) }
    }

    private fun clearPath(
        target: File,
        protectedPaths: Set<String>,
        stats: Stats
    ) {
        val normalized = normalizePath(target.absolutePath)
        if (normalized in protectedPaths) return

        if (!SafeFileOps.isSymlink(target) && target.isDirectory) {
            target.listFiles()?.forEach { child ->
                clearPath(child, protectedPaths, stats)
            }
            if (target.listFiles().isNullOrEmpty()) {
                deleteTarget(target, stats)
            }
        } else {
            deleteTarget(target, stats)
        }
    }

    private fun deleteTarget(target: File, stats: Stats) {
        if (!SafeFileOps.existsOrSymlink(target)) return

        val bytes = SafeFileOps.safeSizeNoFollow(target)
        val entries = SafeFileOps.safeEntryCountNoFollow(target)
        val deleted = runCatching { SafeFileOps.deleteRecursivelyNoFollow(target) }.getOrDefault(false)

        if (deleted) {
            stats.freedBytes += bytes
            stats.deletedEntries += entries
        }
    }

    private fun isAppManagedTempFile(file: File): Boolean {
        val name = file.name
        return tempFilePrefixes.any { name.startsWith(it) } || name in tempFileNames
    }

    private fun normalizePath(path: String): String {
        return runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }
    }

    private class Stats {
        var deletedEntries: Int = 0
        var freedBytes: Long = 0L
    }
}

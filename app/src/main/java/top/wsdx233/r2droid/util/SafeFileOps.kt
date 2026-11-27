package top.wsdx233.r2droid.util

import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.nio.file.Files

/**
 * Filesystem helpers that never follow symbolic links during recursive deletion or traversal.
 *
 * Kotlin's [File.deleteRecursively] and [File.isDirectory] use stat() rather than lstat(),
 * so a symlink pointing to external storage (e.g. /sdcard inside a proot rootfs) would be
 * traversed and its target contents deleted.
 */
object SafeFileOps {

    fun isSymlink(file: File): Boolean {
        if (Build.VERSION.SDK_INT == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nioResult = runCatching { Files.isSymbolicLink(file.toPath()) }.getOrNull()
            if (nioResult != null) return nioResult
        }
        val lstatResult = runCatching {
            OsConstants.S_ISLNK(Os.lstat(file.path).st_mode)
        }.getOrNull()
        if (lstatResult != null) return lstatResult

        return runCatching {
            val parent = file.parentFile ?: return@runCatching false
            val fileInCanonicalDir = File(parent.canonicalFile, file.name)
            fileInCanonicalDir.canonicalFile != fileInCanonicalDir.absoluteFile
        }.getOrDefault(false)
    }

    fun existsOrSymlink(file: File): Boolean = isSymlink(file) || file.exists()

    /**
     * Deletes [target] (and its children if it is a real directory) without following symlinks.
     * Symlinks themselves are unlinked via [File.delete] without descending into their target.
     */
    fun deleteRecursivelyNoFollow(target: File): Boolean {
        if (!existsOrSymlink(target)) return true
        if (isSymlink(target)) {
            return target.delete()
        }
        if (target.isDirectory) {
            val stack = ArrayDeque<Pair<File, Boolean>>()
            stack.addLast(target to false)
            var allDeleted = true
            while (stack.isNotEmpty()) {
                val (current, visited) = stack.removeLast()
                if (isSymlink(current) || !current.isDirectory || visited) {
                    if (!current.delete() && existsOrSymlink(current)) {
                        allDeleted = false
                    }
                } else {
                    stack.addLast(current to true)
                    current.listFiles()?.forEach { child ->
                        stack.addLast(child to false)
                    }
                }
            }
            return allDeleted && !existsOrSymlink(target)
        }
        return target.delete() || !existsOrSymlink(target)
    }

    /** Computes total byte size without following symlinks and without recursion (cycle-safe). */
    fun safeSizeNoFollow(target: File): Long {
        if (!existsOrSymlink(target) || isSymlink(target)) return 0L
        if (!target.isDirectory) return target.length()
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(target)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (isSymlink(current)) continue
            if (current.isDirectory) {
                current.listFiles()?.forEach { stack.addLast(it) }
            } else {
                total += current.length()
            }
        }
        return total
    }

    /** Counts entries without following symlinks and without recursion (cycle-safe). */
    fun safeEntryCountNoFollow(target: File): Int {
        if (!existsOrSymlink(target)) return 0
        if (isSymlink(target) || !target.isDirectory) return 1
        var count = 0
        val stack = ArrayDeque<File>()
        stack.addLast(target)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            count++
            if (!isSymlink(current) && current.isDirectory) {
                current.listFiles()?.forEach { stack.addLast(it) }
            }
        }
        return count
    }
}

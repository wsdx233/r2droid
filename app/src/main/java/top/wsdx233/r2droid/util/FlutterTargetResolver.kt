package top.wsdx233.r2droid.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** Resolves APK or direct libapp.so input into a local ARM64 analysis target. */
object FlutterTargetResolver {
    data class Target(
        val path: String,
        val displayName: String,
        val extractedFromApk: Boolean
    )

    suspend fun resolve(context: Context, uri: Uri): Result<Target> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(context, uri) ?: "flutter-target"
            val mimeType = context.contentResolver.getType(uri).orEmpty()
            val isArchive = displayName.endsWith(".apk", ignoreCase = true) ||
                displayName.endsWith(".zip", ignoreCase = true) ||
                mimeType == "application/vnd.android.package-archive" ||
                mimeType == "application/zip"

            if (isArchive) {
                extractArm64LibApp(context, uri, displayName)
            } else {
                val path = IntentFileResolver.resolve(context, uri)
                    ?: error("Unable to read the selected file")
                validateArm64Elf(File(path))
                Target(path = path, displayName = displayName, extractedFromApk = false)
            }
        }
    }

    private fun extractArm64LibApp(context: Context, uri: Uri, displayName: String): Target {
        val targetDir = File(context.cacheDir, "r2flutter/targets/${System.currentTimeMillis()}")
            .apply { mkdirs() }
        val target = File(targetDir, "libapp.so")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Unable to open the selected APK")
            var found = false

            input.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val normalized = entry.name.replace('\\', '/')
                        if (!entry.isDirectory && normalized == "lib/arm64-v8a/libapp.so") {
                            FileOutputStream(target).use { output -> zip.copyTo(output) }
                            found = true
                            break
                        }
                        entry = zip.nextEntry
                    }
                }
            }

            if (!found) {
                error("The APK does not contain lib/arm64-v8a/libapp.so")
            }
            validateArm64Elf(target)
            return Target(
                path = target.absolutePath,
                displayName = displayName.removeSuffix(".apk") + " / libapp.so",
                extractedFromApk = true
            )
        } catch (t: Throwable) {
            SafeFileOps.deleteRecursivelyNoFollow(targetDir)
            throw t
        }
    }

    internal fun validateArm64Elf(file: File) {
        require(file.isFile && file.length() >= 20L) { "The selected target is empty or unreadable" }
        val header = ByteArray(20)
        file.inputStream().use { input ->
            require(input.read(header) == header.size) { "Unable to read the target header" }
        }
        require(header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()) {
            "The selected target is not an ELF binary"
        }
        require(header[4].toInt() == 2) { "Only 64-bit Flutter AOT targets are supported" }
        val littleEndian = header[5].toInt() == 1
        val machine = if (littleEndian) {
            (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
        } else {
            ((header[18].toInt() and 0xff) shl 8) or (header[19].toInt() and 0xff)
        }
        require(machine == 183) { "Only ARM64/AArch64 Flutter AOT targets are supported" }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}

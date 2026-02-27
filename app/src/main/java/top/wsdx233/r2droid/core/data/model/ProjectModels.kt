package top.wsdx233.r2droid.core.data.model

import android.annotation.SuppressLint
import org.json.JSONObject
import java.io.File

/**
 * Saved project metadata.
 * Stored as JSON file alongside the .r2 script file.
 */
data class SavedProject(
    val id: String,                     // Unique identifier (UUID or hash)
    val name: String,                   // Display name (usually filename)
    val binaryPath: String,             // Path to the original binary file
    val scriptPath: String,             // Path to the .r2 script file
    val createdAt: Long,                // Creation timestamp
    val lastModified: Long,             // Last modification timestamp
    val fileSize: Long,                 // Original binary file size
    val archType: String,               // Architecture type (e.g., arm64, x86)
    val binType: String,                // Binary type (e.g., elf, pe, mach0)
    val analysisLevel: String = ""      // Analysis level used (aaa, aaaa, etc.)
) {
    companion object {
        fun fromJson(json: JSONObject): SavedProject {
            return SavedProject(
                id = json.optString("id", ""),
                name = json.optString("name", ""),
                binaryPath = json.optString("binaryPath", ""),
                scriptPath = json.optString("scriptPath", ""),
                createdAt = json.optLong("createdAt", 0L),
                lastModified = json.optLong("lastModified", 0L),
                fileSize = json.optLong("fileSize", 0L),
                archType = json.optString("archType", ""),
                binType = json.optString("binType", ""),
                analysisLevel = json.optString("analysisLevel", "")
            )
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("binaryPath", binaryPath)
            put("scriptPath", scriptPath)
            put("createdAt", createdAt)
            put("lastModified", lastModified)
            put("fileSize", fileSize)
            put("archType", archType)
            put("binType", binType)
            put("analysisLevel", analysisLevel)
        }
    }

    /**
     * Check if the binary file still exists
     */
    fun isBinaryAccessible(): Boolean {
        return try {
            File(binaryPath).exists() && File(binaryPath).canRead()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if the script file exists
     */
    fun isScriptAccessible(): Boolean {
        return try {
            File(scriptPath).exists() && File(scriptPath).canRead()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Get formatted last modified time
     */
    fun getFormattedLastModified(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(lastModified))
    }

    /**
     * Get formatted file size
     */
    @SuppressLint("DefaultLocale")
    fun getFormattedFileSize(): String {
        return when {
            fileSize >= 1024 * 1024 * 1024 -> String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0))
            fileSize >= 1024 * 1024 -> String.format("%.2f MB", fileSize / (1024.0 * 1024.0))
            fileSize >= 1024 -> String.format("%.2f KB", fileSize / 1024.0)
            else -> "$fileSize B"
        }
    }
}

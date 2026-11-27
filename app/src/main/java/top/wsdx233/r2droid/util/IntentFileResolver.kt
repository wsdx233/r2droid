package top.wsdx233.r2droid.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import java.io.File
import java.io.FileOutputStream

/**
 * 将 content:// 或 file:// URI 解析为 R2 可用的本地文件路径。
 * 逻辑与 HomeViewModel.resolvePath 一致，提取为公共工具以便 Intent 入口复用。
 */
object IntentFileResolver {

    suspend fun resolve(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        // 1. 尝试直接获取真实路径（禁止通过外部 Intent 访问敏感私有配置/数据库目录）
        try {
            val realPath = UriUtils.getPath(appContext, uri)
            if (realPath != null) {
                val file = File(realPath)
                if (!isSensitiveAppInternalFile(appContext, file) && file.exists() && file.canRead()) {
                    return@withContext realPath
                }
            }
        } catch (_: Exception) {
        }

        // 2. 回退：复制到缓存目录
        copyToLocal(appContext, uri)
    }

    private fun isSensitiveAppInternalFile(context: Context, file: File): Boolean {
        return runCatching {
            val canonical = file.canonicalPath
            val dataDir = File(context.applicationInfo.dataDir).canonicalFile
            val blockedRoots = listOf(
                File(dataDir, "shared_prefs").canonicalPath,
                File(dataDir, "databases").canonicalPath
            )
            blockedRoots.any { root -> canonical == root || canonical.startsWith("$root${File.separator}") }
        }.getOrDefault(false)
    }

    private fun copyToLocal(context: Context, uri: Uri): String? {
        var outFile: File? = null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use { input ->
                val fileName = "r2_target_${System.currentTimeMillis()}"
                val baseDir = SettingsManager.projectHome?.let { File(it) }
                    ?.takeIf { it.exists() && it.isDirectory && it.canWrite() }
                    ?: context.cacheDir
                val file = File(baseDir, fileName)
                outFile = file
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
                file.absolutePath
            }
        } catch (_: Exception) {
            outFile?.delete()
            null
        }
    }
}

package top.wsdx233.r2droid.util

import android.content.Context
import android.net.Uri
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import java.io.File
import java.io.FileOutputStream

/**
 * 将 content:// 或 file:// URI 解析为 R2 可用的本地文件路径。
 * 逻辑与 HomeViewModel.resolvePath 一致，提取为公共工具以便 Intent 入口复用。
 */
object IntentFileResolver {

    suspend fun resolve(context: Context, uri: Uri): String? {
        // 1. 尝试直接获取真实路径
        try {
            val realPath = UriUtils.getPath(context, uri)
            if (realPath != null) {
                val file = File(realPath)
                if (file.exists() && file.canRead()) {
                    return realPath
                }
            }
        } catch (_: Exception) {
        }

        // 2. 回退：复制到缓存目录
        return copyToLocal(context, uri)
    }

    private fun copyToLocal(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "r2_target_${System.currentTimeMillis()}"
            val baseDir = SettingsManager.projectHome?.let { File(it) }
                ?.takeIf { it.exists() && it.isDirectory && it.canWrite() }
                ?: context.cacheDir
            val file = File(baseDir, fileName)
            FileOutputStream(file).use { output ->
                inputStream.use { input -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}


package top.wsdx233.randroid.util

import android.content.Context
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// 定义安装状态数据类
data class InstallState(
    val isInstalling: Boolean = false, // 是否正在安装
    val statusText: String = "",       // 当前状态文字 (如: "正在解压核心组件...")
    val progress: Float = 0f           // 进度 (0.0 - 1.0)
)

object R2Installer {
    private const val TAG = "R2Installer"
    private const val R2_DIR_NAME = "radare2"
    private const val R2_DATA_DIR_NAME = "r2work"
    private const val ASSET_FILENAME = "r2.tar"
    private const val R2_DATA_ASSET_FILENAME = "r2dir.tar"

    // 使用 StateFlow 暴露当前状态给 UI
    private val _installState = MutableStateFlow(InstallState())
    val installState = _installState.asStateFlow()

    var initialized = false

    /**
     * 检查并安装 Radare2
     */
    suspend fun checkAndInstall(context: Context) = withContext(Dispatchers.IO) {
        initialized = false
        val targetDir = File(context.filesDir, R2_DIR_NAME)

        // 检查目录是否存在
        if (targetDir.exists()) {
            Log.d(TAG, "Radare2 directory exists. Skipping installation.")
            _installState.value = InstallState(isInstalling = false)
            initialized = true
            return@withContext
        }

        // --- 开始安装流程 ---
        Log.d(TAG, "Radare2 not found. Starting extraction...")

        try {
            // 阶段 1: 解压 r2.tar (占进度的 0% - 50%)
            installFromAssets(
                context,
                ASSET_FILENAME,
                context.filesDir,
                progressStart = 0f,
                progressEnd = 0.5f,
                taskName = "正在安装核心组件..."
            )

            // 阶段 2: 解压 r2dir.tar (占进度的 50% - 90%)
            installFromAssets(
                context,
                R2_DATA_ASSET_FILENAME,
                File(context.filesDir, "$R2_DATA_DIR_NAME/radare2"),
                progressStart = 0.5f,
                progressEnd = 0.9f,
                taskName = "正在配置依赖环境..."
            )

            // 阶段 3: 复制 libs 和配置 (占进度的 90% - 100%)
            _installState.value = InstallState(true, "正在完成最后设置...", 0.95f)
            copyAssetFolder(context, "libs", context.filesDir)

            writeFile(context, "e r2ghidra.sleighhome = ${File(context.filesDir,"r2work/radare2/plugins/r2ghidra_sleigh")}", File(context.filesDir,"radare2/bin/.radare2rc"))

            Log.d(TAG, "Radare2 installation completed successfully.")

            // 完成
            _installState.value = InstallState(isInstalling = false)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to install Radare2", e)
            _installState.value = InstallState(true, "安装失败: ${e.message}", 0f)
            targetDir.deleteRecursively()
            // 注意：实际项目中可能需要处理错误状态，这里简单处理为留在失败界面或重试
        }

        initialized = true
    }

    private fun installFromAssets(
        context: Context,
        assetName: String,
        outputDir: File,
        progressStart: Float,
        progressEnd: Float,
        taskName: String
    ) {
        // 获取 Asset 总大小用于计算进度
        val totalBytes = try {
            context.assets.openFd(assetName).length
        } catch (e: Exception) {
            -1L // 无法获取大小时
        }

        var bytesReadTotal = 0L
        val progressRange = progressEnd - progressStart

        // 创建一个包装流来统计读取字节数
        val rawInputStream = context.assets.open(assetName)
        val progressInputStream = object : InputStream() {
            override fun read(): Int {
                val b = rawInputStream.read()
                if (b != -1) updateProgress(1)
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val read = rawInputStream.read(b, off, len)
                if (read != -1) updateProgress(read.toLong())
                return read
            }

            // 更新进度的限流逻辑，避免过于频繁更新 StateFlow
            private var lastUpdateBytes = 0L
            private fun updateProgress(bytesRead: Long) {
                bytesReadTotal += bytesRead
                // 每读取 100KB 更新一次 UI，或者总大小未知时
                if (bytesReadTotal - lastUpdateBytes > 1024 * 100 || bytesReadTotal == totalBytes) {
                    lastUpdateBytes = bytesReadTotal
                    if (totalBytes > 0) {
                        val currentPercent = bytesReadTotal.toFloat() / totalBytes
                        val globalProgress = progressStart + (currentPercent * progressRange)
                        _installState.value = InstallState(true, taskName, globalProgress)
                    } else {
                        // 无法获取大小时显示 indeterminate 状态或仅显示文字
                        _installState.value = InstallState(true, taskName, progressStart)
                    }
                }
            }

            override fun close() {
                rawInputStream.close()
            }
        }

        val tarIn = TarArchiveInputStream(progressInputStream)
        var entry: TarArchiveEntry?

        while (tarIn.nextEntry.also { entry = it } != null) {
            val currentEntry = entry!!
            val outputFile = File(outputDir, currentEntry.name)

            if (!outputFile.canonicalPath.startsWith(outputDir.canonicalPath)) {
                throw SecurityException("Zip Slip vulnerability detected: ${currentEntry.name}")
            }

            if (currentEntry.isDirectory) {
                if (!outputFile.exists()) outputFile.mkdirs()
            } else if (currentEntry.isSymbolicLink) {
                handleSymlink(outputFile, currentEntry.linkName)
            } else {
                handleRegularFile(tarIn, outputFile)
            }

            if (!currentEntry.isSymbolicLink) {
                setFilePermissions(outputFile, currentEntry.mode)
            }
        }

        tarIn.close()
    }

    fun copyAssetFolder(context: Context, assetPath: String, targetParentDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        val targetFile = File(targetParentDir, assetPath)

        if (assets.isEmpty()) {
            try {
                copyAssetFile(context, assetPath, targetFile)
            } catch (e: Exception) {
                targetFile.mkdirs()
            }
        } else {
            targetFile.mkdirs()
            for (asset in assets) {
                val subPath = if (assetPath.isEmpty()) asset else "$assetPath/$asset"
                copyAssetFolder(context, subPath, targetParentDir)
            }
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, targetFile: File) {
        context.assets.open(assetPath).use { input ->
            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun writeFile(context: Context, content: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { output ->
            output.write(content.toByteArray())
        }
    }

    private fun handleSymlink(linkFile: File, targetPath: String) {
        try {
            linkFile.parentFile?.mkdirs()
            linkFile.delete()
            Os.symlink(targetPath, linkFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create symlink: ${linkFile.absolutePath} -> $targetPath", e)
        }
    }

    private fun handleRegularFile(tarIn: TarArchiveInputStream, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
            val buffer = ByteArray(4096)
            var len: Int
            while (tarIn.read(buffer).also { len = it } != -1) {
                out.write(buffer, 0, len)
            }
        }
    }

    private fun setFilePermissions(file: File, mode: Int) {
        try {
            val permissions = mode and 0b111111111
            if (permissions > 0) {
                Os.chmod(file.absolutePath, permissions)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set permissions for ${file.absolutePath}")
        }
    }
}

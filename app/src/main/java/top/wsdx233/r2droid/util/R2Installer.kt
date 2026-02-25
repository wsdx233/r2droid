package top.wsdx233.r2droid.util

import android.content.Context
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import top.wsdx233.r2droid.R
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

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
    private const val EXPECTED_R2_VERSION = "6.1.0"

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
        var isUpdate = false

        if (targetDir.exists()) {
            // 检测已安装的 r2 版本
            _installState.value = InstallState(true, context.getString(R.string.install_checking_version), 0f)
            val installedVersion = getInstalledR2Version(context)

            if (installedVersion != null && compareVersions(installedVersion, EXPECTED_R2_VERSION) >= 0) {
                Log.d(TAG, "Radare2 is up to date (v$installedVersion)")
                _installState.value = InstallState(isInstalling = false)
//                ensureR2decPlugin(context)
                initialized = true
                return@withContext
            }

            // 版本过旧或无法读取，需要覆盖更新
            Log.d(TAG, "Radare2 outdated (installed=$installedVersion, expected=$EXPECTED_R2_VERSION). Updating...")
            isUpdate = true
            targetDir.deleteRecursively()
            File(context.filesDir, R2_DATA_DIR_NAME).deleteRecursively()
            File(context.filesDir, "libs").deleteRecursively()
        }

        // --- 开始安装/更新流程 ---
        Log.d(TAG, if (isUpdate) "Starting radare2 update..." else "Radare2 not found. Starting extraction...")

        try {
            // 阶段 1: 解压 r2.tar (占进度的 0% - 50%)
            installFromAssets(
                context,
                ASSET_FILENAME,
                context.filesDir,
                progressStart = 0f,
                progressEnd = 0.5f,
                taskName = if (isUpdate) context.getString(R.string.install_updating_core)
                           else "正在安装核心组件..."
            )

            // 阶段 2: 解压 r2dir.tar (占进度的 50% - 90%)
            installFromAssets(
                context,
                R2_DATA_ASSET_FILENAME,
                File(context.filesDir, "$R2_DATA_DIR_NAME/radare2"),
                progressStart = 0.5f,
                progressEnd = 0.9f,
                taskName = if (isUpdate) context.getString(R.string.install_updating_deps)
                           else "正在配置依赖环境..."
            )

            // 阶段 3: 复制 libs 和配置 (占进度的 90% - 100%)
            _installState.value = InstallState(
                true,
                if (isUpdate) context.getString(R.string.install_updating_finish)
                else "正在完成最后设置...",
                0.95f
            )
            copyAssetFolder(context, "libs", context.filesDir)

            writeFile(context, "e scr.interactive = false\ne r2ghidra.sleighhome = ${File(context.filesDir,"r2work/radare2/plugins/r2ghidra_sleigh")}", File(context.filesDir,"radare2/bin/.radare2rc"))

            Log.d(TAG, if (isUpdate) "Radare2 update completed." else "Radare2 installation completed successfully.")

            // 完成
            _installState.value = InstallState(isInstalling = false)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to ${if (isUpdate) "update" else "install"} Radare2", e)
            _installState.value = InstallState(
                true,
                if (isUpdate) context.getString(R.string.install_update_failed, e.message ?: "unknown")
                else "安装失败: ${e.message}",
                0f
            )
            targetDir.deleteRecursively()
        }

//        ensureR2decPlugin(context)
        initialized = true
    }

    /**
     * 运行已安装的 r2 -v 获取版本号
     */
    private fun getInstalledR2Version(context: Context): String? {
        return try {
            val workDir = File(context.filesDir, "radare2/bin")
            val r2Binary = File(workDir, "r2").absolutePath

            val envMap = mutableMapOf<String, String>()
            envMap["LD_LIBRARY_PATH"] =
                "${File(context.filesDir, "radare2/lib")}:${File(context.filesDir, "libs")}"

            val pb = ProcessBuilder(listOf("/system/bin/sh", "-c", "$r2Binary -v"))
            pb.directory(workDir)
            pb.environment().putAll(envMap)
            pb.redirectErrorStream(true)

            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(5, TimeUnit.SECONDS)
            process.destroy()

            parseR2Version(output)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get installed r2 version", e)
            null
        }
    }

    /**
     * 从 r2 -v 输出中解析版本号，如 "radare2 5.9.8 0 @ linux-arm-64" → "5.9.8"
     */
    private fun parseR2Version(output: String): String? {
        val regex = Regex("""radare2\s+(\d+\.\d+\.\d+)""")
        return regex.find(output)?.groupValues?.get(1)
    }

    /**
     * 语义化版本比较: 返回负数(v1<v2)、0(相等)、正数(v1>v2)
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    private fun ensureR2decPlugin(context: Context) {
        try {
            val pluginsDir = File(context.filesDir, "r2work/radare2/plugins")
            val target = File(pluginsDir, "libcore_pdd.so")
            if (!target.exists()) {
                pluginsDir.mkdirs()
                context.assets.open("libcore_pdd.so").use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                Os.chmod(target.absolutePath, 493) // 0755
                Log.d(TAG, "r2dec plugin installed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to install r2dec plugin", e)
        }
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

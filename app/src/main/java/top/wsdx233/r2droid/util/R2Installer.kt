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
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import top.wsdx233.r2droid.R
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
    private const val R2_MARKER_NAME = ".r2-runtime-manifest"
    private val CORE_ASSET_CANDIDATES = listOf("r2.tar.gz", "r2.tar")
    private val DATA_ASSET_CANDIDATES = listOf("r2dir.tar.gz", "r2dir.tar")

    // 使用 StateFlow 暴露当前状态给 UI
    private val _installState = MutableStateFlow(InstallState())
    val installState = _installState.asStateFlow()

    var initialized = false

    /**
     * Checks and installs the bundled Radare2 runtime.
     *
     * Runtime metadata is mandatory for full builds. A successful marker is
     * written only after both archives, libraries, and configuration exist.
     */
    suspend fun checkAndInstall(context: Context) = withContext(Dispatchers.IO) {
        initialized = false
        val targetDir = File(context.filesDir, R2_DIR_NAME)
        var isUpdate = false
        var installationStarted = false
        val configBackup = File(context.filesDir, ".radare2rc-refresh")
        configBackup.delete()
        try {
            ProotInstaller.ensureRuntimeBinary(context)
            if (!AppVariant.bundledR2Available) {
                Log.d(TAG, "Bundled radare2 assets are disabled for this build variant; skipping host r2 installation")
                _installState.value = InstallState(isInstalling = false)
                return@withContext
            }
            val manifest = BundledRuntimeManifest.fromAssets(context.assets, requireRadare2 = true)
            val radare2 = manifest.radare2
                ?: throw IllegalStateException("${BundledRuntimeManifest.ASSET_NAME} has no radare2 identity")
            val bundleId = radare2.bundleId
            val installedBundleId = readInstalledBundleId(context)
            if (installedBundleId == bundleId && hasCompleteInstallation(context)) {
                Log.d(TAG, "Radare2 bundle is up to date (${radare2.version})")
                _installState.value = InstallState(isInstalling = false)
                initialized = true
                return@withContext
            }

            isUpdate = targetDir.exists() || installedBundleId != null
            val existingR2rc = File(context.filesDir, "radare2/bin/.radare2rc")
            if (existingR2rc.isFile) existingR2rc.copyTo(configBackup, overwrite = true)
            if (isUpdate) Log.d(TAG, "Radare2 bundle changed or is incomplete; reinstalling")
            installationStarted = true
            getR2Marker(context).delete()
            targetDir.deleteRecursively()
            File(context.filesDir, R2_DATA_DIR_NAME).deleteRecursively()
            File(context.filesDir, "libs").deleteRecursively()

            _installState.value = InstallState(true, context.getString(R.string.install_checking_version), 0f)
            installFromAssets(
                context,
                resolveAssetName(context, CORE_ASSET_CANDIDATES),
                context.filesDir,
                progressStart = 0f,
                progressEnd = 0.5f,
                taskName = if (isUpdate) context.getString(R.string.install_updating_core)
                           else "正在安装核心组件..."
            )
            installFromAssets(
                context,
                resolveAssetName(context, DATA_ASSET_CANDIDATES),
                File(context.filesDir, "$R2_DATA_DIR_NAME/radare2"),
                progressStart = 0.5f,
                progressEnd = 0.9f,
                taskName = if (isUpdate) context.getString(R.string.install_updating_deps)
                           else "正在配置依赖环境..."
            )

            _installState.value = InstallState(
                true,
                if (isUpdate) context.getString(R.string.install_updating_finish)
                else "正在完成最后设置...",
                0.95f
            )
            copyAssetFolder(context, "libs", context.filesDir)
            val config = File(context.filesDir, "radare2/bin/.radare2rc")
            if (configBackup.isFile) {
                config.parentFile?.mkdirs()
                configBackup.copyTo(config, overwrite = true)
            } else {
                writeFile(
                    context,
                    "e scr.interactive = false\ne r2ghidra.sleighhome = ${File(context.filesDir, "r2work/radare2/plugins/r2ghidra_sleigh")}",
                    config
                )
            }
            checkCompleteInstallation(context)
            writeInstalledBundleId(context, bundleId)
            configBackup.delete()
            Log.d(TAG, if (isUpdate) "Radare2 update completed." else "Radare2 installation completed successfully.")
            _installState.value = InstallState(isInstalling = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ${if (isUpdate) "update" else "install"} Radare2", e)
            _installState.value = InstallState(
                true,
                if (isUpdate) context.getString(R.string.install_update_failed, e.message ?: "unknown")
                else "安装失败: ${e.message}",
                0f
            )
            if (installationStarted) {
                getR2Marker(context).delete()
                targetDir.deleteRecursively()
                if (configBackup.isFile) {
                    val restored = File(context.filesDir, "radare2/bin/.radare2rc")
                    restored.parentFile?.mkdirs()
                    configBackup.copyTo(restored, overwrite = true)
                }
            }
        } finally {
            configBackup.delete()
            initialized = true
        }
    }

    private fun getR2Marker(context: Context): File = File(context.filesDir, R2_MARKER_NAME)

    private fun readInstalledBundleId(context: Context): String? {
        val marker = getR2Marker(context)
        if (!marker.isFile) return null
        return marker.readLines().firstOrNull { it.startsWith("bundleId=") }
            ?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun writeInstalledBundleId(context: Context, bundleId: String) {
        val marker = getR2Marker(context)
        val temporary = File(marker.parentFile, "${marker.name}.tmp")
        temporary.writeText("bundleId=$bundleId\n")
        try {
            Os.rename(temporary.absolutePath, marker.absolutePath)
        } finally {
            temporary.delete()
        }
    }

    private fun hasCompleteInstallation(context: Context): Boolean = try {
        checkCompleteInstallation(context)
        true
    } catch (_: Exception) {
        false
    }

    private fun checkCompleteInstallation(context: Context) {
        val requiredFiles = listOf(
            File(context.filesDir, "radare2/bin/r2"),
            File(context.filesDir, "libs/libc++_shared.so"),
            File(context.filesDir, "radare2/bin/.radare2rc")
        )
        requiredFiles.forEach { file ->
            require(file.isFile) { "Radare2 installation is incomplete: ${file.path}" }
        }
        require(File(context.filesDir, "r2work/radare2").isDirectory) {
            "Radare2 installation is incomplete: ${File(context.filesDir, "r2work/radare2").path}"
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
        val rawInputStream = context.assets.open(assetName)
        val countingInput = object : InputStream() {
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
            private var lastUpdateBytes = 0L
            private fun updateProgress(bytesRead: Long) {
                bytesReadTotal += bytesRead
                if (bytesReadTotal - lastUpdateBytes > 1024 * 100 || bytesReadTotal == totalBytes) {
                    lastUpdateBytes = bytesReadTotal
                    if (totalBytes > 0) {
                        val currentPercent = bytesReadTotal.toFloat() / totalBytes
                        val globalProgress = progressStart + (currentPercent * progressRange)
                        _installState.value = InstallState(true, taskName, globalProgress.coerceAtMost(progressEnd))
                    } else {
                        _installState.value = InstallState(true, taskName, progressStart)
                    }
                }
            }
            override fun close() = rawInputStream.close()
        }
        val tarSource = if (assetName.endsWith(".gz")) GzipCompressorInputStream(countingInput) else countingInput
        val tarIn = TarArchiveInputStream(tarSource)
        var entry: TarArchiveEntry?
        while (tarIn.nextEntry.also { entry = it } != null) {
            val currentEntry = entry!!
            val outputFile = File(outputDir, currentEntry.name)
            val baseCanonical = outputDir.canonicalFile
            val outCanonical = outputFile.canonicalFile
            val basePath = baseCanonical.path
            val outPath = outCanonical.path
            if (!(outPath == basePath || outPath.startsWith(basePath + File.separator))) {
                throw SecurityException("Zip Slip vulnerability detected: ${currentEntry.name}")
            }
            if (currentEntry.isDirectory) {
                if (!outputFile.exists()) outputFile.mkdirs()
            } else if (currentEntry.isSymbolicLink) {
                handleSymlink(outputFile, currentEntry.linkName)
            } else {
                handleRegularFile(tarIn, outputFile)
            }
            if (!currentEntry.isSymbolicLink) setFilePermissions(outputFile, currentEntry.mode)
        }
        tarIn.close()
    }

    private fun resolveAssetName(context: Context, candidates: List<String>): String {
        val availableAssets = context.assets.list("")?.toSet().orEmpty()
        return candidates.firstOrNull(availableAssets::contains)
            ?: throw IllegalStateException("Missing asset: ${candidates.joinToString(" or ")}")
    }

    fun copyAssetFolder(context: Context, assetPath: String, targetParentDir: File) {
        val assets = context.assets.list(assetPath)
            ?: throw IllegalStateException("Missing bundled asset: $assetPath")
        val targetFile = File(targetParentDir, assetPath)

        if (assets.isEmpty()) {
            copyAssetFile(context, assetPath, targetFile)
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
        linkFile.parentFile?.mkdirs()
        linkFile.delete()
        Os.symlink(targetPath, linkFile.absolutePath)
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
        val permissions = mode and 0b111111111
        if (permissions > 0) Os.chmod(file.absolutePath, permissions)
    }
}

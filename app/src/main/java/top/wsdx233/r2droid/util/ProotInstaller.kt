package top.wsdx233.r2droid.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val DEFAULT_HOST_R2RC = "e scr.interactive = false\ne r2ghidra.sleighhome = %s"
private const val DEFAULT_PROOT_R2RC = "e scr.interactive = false\ne r2ghidra.sleighhome = /root/.local/share/radare2/plugins/r2ghidra_sleigh"

data class ProotInstallState(
    val status: Status = Status.IDLE,
    val progress: Float = 0f,
    val message: String = "",
    val logs: List<String> = emptyList(),
    val canRetryCurrentStage: Boolean = false
) {
    enum class Status {
        IDLE,
        PREPARING,
        DOWNLOADING,
        EXTRACTING,
        CONFIGURING,
        INSTALLING_PACKAGES,
        INSTALLING_PLUGINS,
        DONE,
        ERROR
    }

    val isWorking: Boolean
        get() = status in setOf(
            Status.PREPARING,
            Status.DOWNLOADING,
            Status.EXTRACTING,
            Status.CONFIGURING,
            Status.INSTALLING_PACKAGES,
            Status.INSTALLING_PLUGINS
        )
}

object ProotInstaller {
    private const val TAG = "ProotInstaller"
    private const val PROOT_ASSET_NAME = "proot"
    private const val RUNTIME_MARKER_NAME = ".proot-runtime"
    private const val READY_MARKER_NAME = ".setup-complete"
    private const val EXTRACT_MARKER_NAME = ".rootfs-extracted"
    private const val STAGE_MARKER_DIR = ".setup-stages"
    private const val MAX_LOG_LINES = 160

    private val installMutex = Mutex()
    private val runtimeInstallLock = Any()

    private data class StageCommand(
        val id: String,
        val progress: Float,
        val message: String,
        val command: String
    )

    private val _state = MutableStateFlow(ProotInstallState())
    val state = _state.asStateFlow()

    fun getRuntimeDir(context: Context): File = File(context.filesDir, "proot")

    fun getBinDir(context: Context): File = File(getRuntimeDir(context), "bin")

    fun getProotBinary(context: Context): File = File(getBinDir(context), "proot")

    fun getRootfsDir(context: Context): File = File(getRuntimeDir(context), "ubuntu")

    fun getHostTmpDir(context: Context): File = File(getRuntimeDir(context), "tmp")

    fun getReadyMarker(context: Context): File = File(getRuntimeDir(context), READY_MARKER_NAME)

    private fun getExtractMarker(context: Context): File = File(getRuntimeDir(context), EXTRACT_MARKER_NAME)

    private fun getStageMarkerDir(context: Context): File = File(getRuntimeDir(context), STAGE_MARKER_DIR)

    fun getR2rcFile(context: Context): File = File(getRootfsDir(context), "root/.radare2rc")

    fun getInstalledRootfsAlias(context: Context): String? = readReadyMetadata(context)["alias"]

    fun isEnvironmentReady(context: Context): Boolean {
        val expectedSha = runCatching {
            BundledRuntimeManifest.fromAssets(context.assets).proot.sha256
        }.getOrNull() ?: return false
        val markerSha = readMarkerMetadata(getRuntimeMarker(context))["sha256"]
        return getProotBinary(context).exists() &&
            markerSha == expectedSha &&
            getReadyMarker(context).exists() &&
            getRootfsDir(context).isDirectory
    }

    fun isR2FridaInstalled(context: Context): Boolean {
        val rootfsDir = getRootfsDir(context)
        if (!rootfsDir.isDirectory) return false
        val pluginCandidates = listOf(
            File(rootfsDir, "root/.local/share/radare2/plugins"),
            File(rootfsDir, "usr/local/lib/radare2"),
            File(rootfsDir, "usr/local/share/radare2/plugins")
        )
        return pluginCandidates.any { dir ->
            dir.exists() && dir.walkTopDown().maxDepth(3).any { file ->
                file.isFile && file.name.startsWith("io_frida") && (file.extension == "so" || file.extension == "dylib" || file.extension == "dll")
            }
        }
    }

    fun resetState() {
        _state.value = ProotInstallState()
    }

    fun ensureRuntimeBinary(context: Context) = synchronized(runtimeInstallLock) {
        val target = getProotBinary(context)
        val runtimeDir = getRuntimeDir(context)
        val assets = context.assets
        val expectedSha = BundledRuntimeManifest.fromAssets(assets).proot.sha256

        runtimeDir.mkdirs()
        target.parentFile?.mkdirs()
        val markerSha = readMarkerMetadata(getRuntimeMarker(context))["sha256"]
        if (target.isFile && markerSha == expectedSha) {
            Os.chmod(target.absolutePath, 493)
            return@synchronized
        }

        val temporary = File(runtimeDir, ".proot.new")
        temporary.delete()
        try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            assets.open(PROOT_ASSET_NAME).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha == expectedSha) {
                "Bundled proot hash mismatch: expected $expectedSha, got $actualSha"
            }
            Os.chmod(temporary.absolutePath, 493)
            Os.rename(temporary.absolutePath, target.absolutePath)
            writeRuntimeMetadata(context, expectedSha)
        } finally {
            temporary.delete()
        }
    }

    private fun getRuntimeMarker(context: Context): File = File(getRuntimeDir(context), RUNTIME_MARKER_NAME)

    private fun writeRuntimeMetadata(context: Context, sha256: String) {
        val marker = getRuntimeMarker(context)
        val temporary = File(getRuntimeDir(context), "$RUNTIME_MARKER_NAME.new")
        temporary.writeText("sha256=$sha256\n")
        try {
            Os.rename(temporary.absolutePath, marker.absolutePath)
        } finally {
            temporary.delete()
        }
    }

    private fun readReadyMetadata(context: Context): Map<String, String> = readMarkerMetadata(getReadyMarker(context))

    private fun readExtractMetadata(context: Context): Map<String, String> = readMarkerMetadata(getExtractMarker(context))

    private fun readMarkerMetadata(marker: File): Map<String, String> {
        if (!marker.exists()) return emptyMap()
        return marker.readLines()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
    }

    private fun writeReadyMetadata(context: Context, option: ProotRootfsOption, mode: String) {
        getReadyMarker(context).apply {
            parentFile?.mkdirs()
            writeText(
                buildString {
                    appendLine("alias=${option.alias}")
                    appendLine("url=${option.tarballUrl}")
                    appendLine("mode=$mode")
                    appendLine("strip=${option.tarballStripOpt}")
                    option.sha256?.let { appendLine("sha256=$it") }
                }
            )
        }
    }

    private fun writeExtractMetadata(context: Context, option: ProotRootfsOption) {
        getExtractMarker(context).apply {
            parentFile?.mkdirs()
            writeText(
                buildString {
                    appendLine("alias=${option.alias}")
                    appendLine("url=${option.tarballUrl}")
                    appendLine("strip=${option.tarballStripOpt}")
                    appendLine("completedAt=${System.currentTimeMillis()}")
                    option.sha256?.let { appendLine("sha256=$it") }
                }
            )
        }
    }

    private fun stageMarker(context: Context, stageId: String): File {
        val safe = stageId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return File(getStageMarkerDir(context), "$safe.complete")
    }

    private fun isStageComplete(context: Context, stageId: String): Boolean = stageMarker(context, stageId).exists()

    private fun markStageComplete(context: Context, stageId: String) {
        stageMarker(context, stageId).apply {
            parentFile?.mkdirs()
            writeText("completedAt=${System.currentTimeMillis()}\n")
        }
    }

    private fun clearStageMarkers(context: Context) {
        getStageMarkerDir(context).deleteRecursively()
    }

    private fun resolveAutoRootfsOption(context: Context, rootfsAlias: String): ProotRootfsOption {
        return ProotRootfsCatalog.resolve(context, rootfsAlias)
    }

    suspend fun install(
        context: Context,
        rootfsAlias: String = "ubuntu",
        forceReinstall: Boolean = false
    ): Result<Unit> {
        val appContext = context.applicationContext
        val rootfsOption = resolveAutoRootfsOption(appContext, rootfsAlias)
        return installMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    _state.value = ProotInstallState()
                    val installedAlias = getInstalledRootfsAlias(appContext)
                    if (!forceReinstall && isEnvironmentReady(appContext) && installedAlias == rootfsOption.alias) {
                        _state.value = ProotInstallState(
                            status = ProotInstallState.Status.DONE,
                            progress = 1f,
                            message = "Proot environment is ready."
                        )
                        return@runCatching
                    }

                    ensureRuntimeBinary(appContext)
                    prepareRuntime(appContext, forceReinstall || (installedAlias != null && installedAlias != rootfsOption.alias))
                    downloadRootfsIfNeeded(appContext, rootfsOption)
                    extractRootfsIfNeeded(appContext, rootfsOption)
                    configureRootfs(appContext)
                    installPackages(appContext)
                    installPlugins(appContext)
                    syncR2rc(appContext)
                    writeReadyMetadata(appContext, rootfsOption, mode = "auto")
                    _state.value = _state.value.copy(
                        status = ProotInstallState.Status.DONE,
                        progress = 1f,
                        message = "Proot environment is ready."
                    )
                    appendLog("Environment setup completed (${rootfsOption.displayName}).")
                }.onFailure { error ->
                    Log.e(TAG, "Failed to install proot environment", error)
                    val reason = buildInstallFailureReason(appContext, error, rootfsOption.tarballUrl)
                    appendLog("INSTALL FAILED: $reason")
                    _state.value = _state.value.copy(
                        status = ProotInstallState.Status.ERROR,
                        message = reason,
                        canRetryCurrentStage = true
                    )
                }
            }
        }
    }

    suspend fun installManual(
        context: Context,
        rootfsAlias: String = "ubuntu",
        forceReinstall: Boolean = false
    ): Result<Unit> {
        val appContext = context.applicationContext
        val rootfsOption = ProotRootfsCatalog.resolve(appContext, rootfsAlias)
        return installMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    _state.value = ProotInstallState()
                    val installedAlias = getInstalledRootfsAlias(appContext)
                    if (!forceReinstall && isEnvironmentReady(appContext) && installedAlias == rootfsOption.alias) {
                        _state.value = ProotInstallState(
                            status = ProotInstallState.Status.DONE,
                            progress = 1f,
                            message = "Proot environment is ready."
                        )
                        return@runCatching
                    }

                    ensureRuntimeBinary(appContext)
                    prepareRuntime(appContext, forceReinstall || (installedAlias != null && installedAlias != rootfsOption.alias))
                    downloadRootfsIfNeeded(appContext, rootfsOption)
                    extractRootfsIfNeeded(appContext, rootfsOption)
                    configureRootfs(appContext)
                    writeReadyMetadata(appContext, rootfsOption, mode = "manual")
                    _state.value = _state.value.copy(
                        status = ProotInstallState.Status.DONE,
                        progress = 1f,
                        message = "${rootfsOption.displayName} proot environment is ready. Please configure r2 and plugins manually via the proot terminal."
                    )
                    appendLog("Manual mode: ${rootfsOption.displayName} proot setup completed. Use the proot terminal to install r2 and plugins.")
                }.onFailure { error ->
                    Log.e(TAG, "Failed to install proot environment (manual)", error)
                    val reason = buildInstallFailureReason(appContext, error, rootfsOption.tarballUrl)
                    appendLog("INSTALL FAILED: $reason")
                    _state.value = _state.value.copy(
                        status = ProotInstallState.Status.ERROR,
                        message = reason,
                        canRetryCurrentStage = true
                    )
                }
            }
        }
    }

    private fun prepareRuntime(context: Context, forceReinstall: Boolean) {
        updateState(ProotInstallState.Status.PREPARING, 0.05f, "Preparing proot runtime...")
        appendLog("Ensuring proot binary is installed.")
        getRuntimeDir(context).mkdirs()
        getRootfsDir(context).mkdirs()
        getHostTmpDir(context).mkdirs()
        runCatching { Os.chmod(getHostTmpDir(context).absolutePath, 511) }
        getReadyMarker(context).delete()

        if (forceReinstall) {
            appendLog("Force reinstall requested, clearing existing rootfs and completed-stage markers.")
            getRootfsDir(context).deleteRecursively()
            getExtractMarker(context).delete()
            clearStageMarkers(context)
            getRootfsDir(context).mkdirs()
        }
    }

    private fun downloadRootfsIfNeeded(context: Context, option: ProotRootfsOption, forceRedownload: Boolean = false) {
        val archive = getArchiveFile(context, option)
        if (!forceRedownload && archive.exists() && archive.length() > 0L) {
            appendLog("Using cached rootfs archive: ${archive.absolutePath}")
            return
        }
        if (forceRedownload) {
            appendLog("Discarding cached rootfs archive and downloading it again.")
            archive.delete()
        }

        updateState(ProotInstallState.Status.DOWNLOADING, 0.1f, "Downloading ${option.displayName} rootfs...")
        appendLog("Downloading ${option.tarballUrl}")

        val archiveDir = archive.parentFile ?: File(context.cacheDir, "proot")
        val tempArchive = File(archiveDir, "${archive.name}.part")
        archiveDir.mkdirs()
        tempArchive.delete()

        val conn = java.net.URL(option.tarballUrl).openConnection() as java.net.HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.connect()

            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Failed to download rootfs: HTTP ${conn.responseCode}")
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            var downloaded = 0L
            var lastReported = 0L

            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(tempArchive).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                        downloaded += len
                        if (downloaded - lastReported >= 256 * 1024 || (totalBytes > 0 && downloaded == totalBytes)) {
                            lastReported = downloaded
                            if (totalBytes > 0) {
                                val stageProgress = 0.1f + (downloaded.toFloat() / totalBytes.toFloat()) * 0.25f
                                updateState(
                                    ProotInstallState.Status.DOWNLOADING,
                                    stageProgress.coerceAtMost(0.35f),
                                    "Downloading ${option.displayName} rootfs..."
                                )
                            }
                        }
                    }
                }
            }

            if (totalBytes > 0 && tempArchive.length() != totalBytes) {
                throw IllegalStateException("Rootfs download incomplete: ${tempArchive.length()} / $totalBytes bytes")
            }
            archive.delete()
            if (!tempArchive.renameTo(archive)) {
                tempArchive.copyTo(archive, overwrite = true)
                tempArchive.delete()
            }
            appendLog("Download finished (${archive.length()} bytes).")
        } catch (error: Throwable) {
            tempArchive.delete()
            throw error
        } finally {
            conn.disconnect()
        }
    }

    private fun extractRootfsIfNeeded(context: Context, option: ProotRootfsOption) {
        val rootfsDir = getRootfsDir(context)
        val readyAlias = readReadyMetadata(context)["alias"]
        val extractedAlias = readExtractMetadata(context)["alias"]
        if ((readyAlias == option.alias || extractedAlias == option.alias) && rootfsDir.resolve("bin").exists()) {
            appendLog("Existing ${option.displayName} rootfs is already extracted, skipping extraction.")
            return
        }

        runCatching {
            extractRootfsArchive(context, option)
        }.onFailure { firstError ->
            appendLog("Rootfs extraction failed: ${firstError.message ?: firstError::class.java.simpleName}")
            appendLog("The cached archive may be incomplete or corrupted; re-downloading rootfs and retrying extraction once.")
            getArchiveFile(context, option).delete()
            rootfsDir.deleteRecursively()
            getExtractMarker(context).delete()
            clearStageMarkers(context)
            downloadRootfsIfNeeded(context, option, forceRedownload = true)
            extractRootfsArchive(context, option)
        }.getOrThrow()
    }

    private fun extractRootfsArchive(context: Context, option: ProotRootfsOption) {
        val rootfsDir = getRootfsDir(context)
        val archive = getArchiveFile(context, option)
        if (!archive.exists()) {
            throw IllegalStateException("Rootfs archive is missing.")
        }

        updateState(ProotInstallState.Status.EXTRACTING, 0.38f, "Extracting ${option.displayName} rootfs...")
        appendLog("Extracting rootfs to ${rootfsDir.absolutePath}")

        rootfsDir.deleteRecursively()
        rootfsDir.mkdirs()
        getExtractMarker(context).delete()
        clearStageMarkers(context)

        val totalBytes = archive.length().takeIf { it > 0 } ?: -1L
        var bytesReadTotal = 0L
        var lastReported = 0L
        val rootCanonical = rootfsDir.canonicalPath

        archive.inputStream().buffered().use { fileInput ->
            val countingInput = object : InputStream() {
                override fun read(): Int {
                    val value = fileInput.read()
                    if (value != -1) updateProgress(1)
                    return value
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val read = fileInput.read(b, off, len)
                    if (read > 0) updateProgress(read.toLong())
                    return read
                }

                private fun updateProgress(bytesRead: Long) {
                    bytesReadTotal += bytesRead
                    if (bytesReadTotal - lastReported >= 256 * 1024 || (totalBytes > 0 && bytesReadTotal >= totalBytes)) {
                        lastReported = bytesReadTotal
                        if (totalBytes > 0) {
                            val stageProgress = 0.38f + (bytesReadTotal.toFloat() / totalBytes.toFloat()) * 0.22f
                            updateState(
                                ProotInstallState.Status.EXTRACTING,
                                stageProgress.coerceAtMost(0.6f),
                                "Extracting ${option.displayName} rootfs..."
                            )
                        }
                    }
                }
            }

            createTarInputStream(archive, countingInput).use { tarInput ->
                var entry: TarArchiveEntry?
                while (tarInput.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    val normalizedEntryName = stripArchivePath(currentEntry.name, option.tarballStripOpt) ?: continue
                    val outputFile = File(rootfsDir, normalizedEntryName)
                    if (!outputFile.canonicalPath.startsWith(rootCanonical)) {
                        throw SecurityException("Invalid archive entry: ${currentEntry.name}")
                    }
                    when {
                        currentEntry.isDirectory -> outputFile.mkdirs()
                        currentEntry.isSymbolicLink -> handleSymlink(outputFile, currentEntry.linkName)
                        currentEntry.isLink -> handleHardLink(rootfsDir, outputFile, stripLinkTarget(currentEntry.linkName, option.tarballStripOpt))
                        currentEntry.isFile -> handleRegularFile(tarInput, outputFile)
                        else -> appendLog("Skipping unsupported tar entry: ${currentEntry.name}")
                    }
                    if (!currentEntry.isSymbolicLink && !currentEntry.isLink) {
                        setFilePermissions(outputFile, currentEntry.mode)
                    }
                }
            }
        }
        writeExtractMetadata(context, option)
        appendLog("Extraction finished.")
    }

    private fun configureRootfs(context: Context) {
        updateState(ProotInstallState.Status.CONFIGURING, 0.64f, "Configuring proot environment...")
        val rootfsDir = getRootfsDir(context)
        val dnsServers = queryDnsServers().ifEmpty { listOf("1.1.1.1", "8.8.8.8") }
        val resolvConf = File(rootfsDir, "etc/resolv.conf")
        resolvConf.parentFile?.mkdirs()
        resolvConf.writeText(dnsServers.joinToString(separator = "\n") { "nameserver $it" } + "\n")

        // Disable apt sandbox to avoid setresuid failures under proot fake-root
        val aptConfDir = File(rootfsDir, "etc/apt/apt.conf.d")
        aptConfDir.mkdirs()
        File(aptConfDir, "99proot-nosandbox").writeText("APT::Sandbox::User \"root\";\n")

        // Force dpkg to skip fsync/sync calls that fail under proot
        val dpkgConfDir = File(rootfsDir, "etc/dpkg/dpkg.cfg.d")
        dpkgConfDir.mkdirs()
        File(dpkgConfDir, "force-unsafe-io").writeText("force-unsafe-io\n")

        val hostsFile = File(rootfsDir, "etc/hosts")
        if (!hostsFile.exists()) {
            hostsFile.parentFile?.mkdirs()
            hostsFile.writeText("127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n")
        }

        // Some Android devices do not expose a host /tmp. Provide both container and host temp dirs.
        val containerTmpDir = File(rootfsDir, "tmp")
        containerTmpDir.mkdirs()
        runCatching { Os.chmod(containerTmpDir.absolutePath, 511) }
        val containerVarTmpDir = File(rootfsDir, "var/tmp")
        containerVarTmpDir.mkdirs()
        runCatching { Os.chmod(containerVarTmpDir.absolutePath, 511) }
        val hostTmpDir = getHostTmpDir(context)
        hostTmpDir.mkdirs()
        runCatching { Os.chmod(hostTmpDir.absolutePath, 511) }

        // Provide a fake /proc/sys/crypto/fips_enabled so libgcrypt doesn't crash
        val fakeProcCrypto = File(rootfsDir, "proc/sys/crypto")
        fakeProcCrypto.mkdirs()
        File(fakeProcCrypto, "fips_enabled").writeText("0\n")

        appendLog("Configured DNS: ${dnsServers.joinToString(", ")}")
    }

    private fun installPackages(context: Context) {
        val setupCommands = listOf(
            StageCommand("apt-update", 0.7f, "Updating apt sources...", """
                set -e
                if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then
                    sed -i 's/^Components: .*/Components: main restricted universe multiverse/' /etc/apt/sources.list.d/ubuntu.sources
                fi
                apt-get update
            """.trimIndent()),
            StageCommand("build-toolchain", 0.78f, "Installing build toolchain...", """
                set -e
                export DEBIAN_FRONTEND=noninteractive
                apt-get install -y --no-install-recommends \
                    ca-certificates \
                    cmake \
                    curl \
                    file \
                    git \
                    make \
                    meson \
                    ninja-build \
                    patch \
                    pkg-config \
                    pkgconf \
                    unzip \
                    wget \
                    xz-utils \
                    build-essential \
                    libssl-dev \
                    libzip-dev
            """.trimIndent()),
            StageCommand("radare2-source", 0.86f, "Installing radare2 from source...", """
                set -e
                export PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
                export MAKE_JOBS=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1)
                mkdir -p /opt/src
                if [ ! -d /opt/src/radare2/.git ]; then
                    rm -rf /opt/src/radare2
                    git clone --depth 1 https://github.com/radareorg/radare2 /opt/src/radare2
                else
                    cd /opt/src/radare2
                    git fetch --depth 1 origin
                    git reset --hard origin/master
                fi
                cd /opt/src/radare2
                rm -rf build
                sys/install.sh
                hash -r
                command -v r2 >/dev/null
                command -v r2pm >/dev/null
                r2 -v
                r2pm -h >/dev/null
                r2pm -U
                ldconfig || true
                ls -l /usr/local/lib/pkgconfig/r_core.pc || true
            """.trimIndent())
        )

        setupCommands.forEach { stage ->
            if (isStageComplete(context, stage.id)) {
                appendLog("Skipping completed stage: ${stage.message}")
                return@forEach
            }
            updateState(ProotInstallState.Status.INSTALLING_PACKAGES, stage.progress, stage.message)
            runProotCommand(context, stage.command)
            markStageComplete(context, stage.id)
        }
    }

    private fun installPlugins(context: Context) {
        val pluginCommands = listOf(
            StageCommand("r2pm-r2dec", 0.9f, "Installing r2dec with r2pm...", "r2dec"),
            StageCommand("r2pm-r2ghidra", 0.95f, "Installing r2ghidra with r2pm...", "r2ghidra"),
            StageCommand("r2pm-r2frida", 0.99f, "Installing r2frida with r2pm...", "r2frida")
        )

        pluginCommands.forEach { stage ->
            if (isStageComplete(context, stage.id)) {
                appendLog("Skipping completed stage: ${stage.message}")
                return@forEach
            }
            updateState(ProotInstallState.Status.INSTALLING_PLUGINS, stage.progress, stage.message)
            runProotCommand(context, buildR2pmInstallScript(stage.command))
            markStageComplete(context, stage.id)
        }
    }

    private fun buildR2pmInstallScript(pluginName: String): String = """
        set -e
        export PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
        export LD_LIBRARY_PATH=/usr/local/lib:/usr/lib:/usr/lib/aarch64-linux-gnu:${'$'}{LD_LIBRARY_PATH:-}
        export LIBRARY_PATH=/usr/local/lib:/usr/lib:/usr/lib/aarch64-linux-gnu:${'$'}{LIBRARY_PATH:-}
        export PKG_CONFIG_PATH=/usr/local/lib/pkgconfig:/usr/lib/pkgconfig:/usr/lib/aarch64-linux-gnu/pkgconfig:${'$'}{PKG_CONFIG_PATH:-}
        R2PM_BIN="$(command -v r2pm || true)"
        [ -n "${'$'}R2PM_BIN" ] || R2PM_BIN=/usr/local/bin/r2pm
        [ -x "${'$'}R2PM_BIN" ] || { echo "r2pm not found"; exit 127; }
        "${'$'}R2PM_BIN" -U
        hash -r
        "${'$'}R2PM_BIN" -ci $pluginName
    """.trimIndent()

    private fun syncR2rc(context: Context) {
        val target = getR2rcFile(context)
        target.parentFile?.mkdirs()

        val current = target.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        if (current.isNotBlank()) {
            appendLog("Keeping existing proot .radare2rc")
            return
        }

        val hostR2rc = File(context.filesDir, "radare2/bin/.radare2rc")
        val content = when {
            hostR2rc.exists() && hostR2rc.readText().isNotBlank() -> {
                val hostSleighPath = String.format(
                    DEFAULT_HOST_R2RC,
                    File(context.filesDir, "r2work/radare2/plugins/r2ghidra_sleigh").absolutePath
                ).substringAfter('\n')
                hostR2rc.readText().replace(hostSleighPath, DEFAULT_PROOT_R2RC.substringAfter('\n'))
            }
            else -> DEFAULT_PROOT_R2RC
        }
        target.writeText(content)
        appendLog("Wrote default proot .radare2rc")
    }

    fun runProotCommand(context: Context, script: String, logger: ((String) -> Unit)? = ::appendLog): String {
        logger?.invoke("$ ${script.lineSequence().firstOrNull() ?: "command"}")

        val wrappedScript = "cd /root 2>/dev/null; $script"

        val spec = R2Runtime.buildProotShellSpec(
            context = context,
            shellCommand = wrappedScript,
            term = "dumb",
            extraBindPaths = emptySet()
        )
        val processBuilder = ProcessBuilder(spec.command)
        processBuilder.directory(spec.workingDirectory)
        processBuilder.environment().putAll(spec.environment)
        processBuilder.redirectErrorStream(true)

        val output = StringBuilder()
        val process = processBuilder.start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    output.appendLine(line)
                    logger?.invoke(line)
                }
            }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException("Command failed with exit code $exitCode")
        }
        return output.toString()
    }

    fun buildInstallFailureReason(context: Context, error: Throwable, sourceUrl: String? = null): String {
        val base = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
        val network = describeNetworkConnectivity(context)
        val source = sourceUrl?.takeIf { it.isNotBlank() }?.let { " Source: $it." }.orEmpty()
        return "$base. Network: $network.$source"
    }

    private fun describeNetworkConnectivity(context: Context): String {
        return runCatching {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching "unknown (connectivity service unavailable)"
            val activeNetwork = connectivityManager.activeNetwork
                ?: return@runCatching "offline (no active network)"
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return@runCatching "unknown (no network capabilities)"
            when {
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "connected, but Android reports no internet capability"
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "online"
                else -> "network present, but internet connectivity is not validated"
            }
        }.getOrElse { "unknown (${it.message ?: it::class.java.simpleName})" }
    }

    private fun queryDnsServers(): List<String> {
        val keys = listOf(
            "net.dns1",
            "net.dns2",
            "net.dns3",
            "net.dns4",
            "dhcp.wlan0.dns1",
            "dhcp.wlan0.dns2",
            "dhcp.rmnet0.dns1",
            "dhcp.rmnet0.dns2"
        )

        return keys.mapNotNull { key ->
            val value = runCatching {
                ProcessBuilder("/system/bin/getprop", key)
                    .redirectErrorStream(true)
                    .start()
                    .inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            }.getOrNull()
            value?.takeIf { it.matches(Regex("^[0-9a-fA-F:.]+$")) }
        }.distinct()
    }

    private fun updateState(status: ProotInstallState.Status, progress: Float, message: String) {
        _state.value = _state.value.copy(
            status = status,
            progress = progress,
            message = message,
            canRetryCurrentStage = false
        )
    }

    private fun appendLog(line: String) {
        val logs = (_state.value.logs + line).takeLast(MAX_LOG_LINES)
        _state.value = _state.value.copy(logs = logs)
    }

    private fun getArchiveFile(context: Context, option: ProotRootfsOption): File {
        val safeName = option.archiveFileName.ifBlank { "${option.alias}.tar" }
        return File(context.cacheDir, "proot/$safeName")
    }

    private fun createTarInputStream(archive: File, countingInput: InputStream): TarArchiveInputStream {
        val lowerName = archive.name.lowercase()
        val archiveInput = when {
            lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> GzipCompressorInputStream(countingInput)
            lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") -> XZCompressorInputStream(countingInput)
            lowerName.endsWith(".tar") -> countingInput
            else -> throw IllegalStateException("Unsupported rootfs archive format: ${archive.name}")
        }
        return TarArchiveInputStream(archiveInput)
    }

    private fun stripArchivePath(path: String, stripComponents: Int): String? {
        val normalized = path.trim('/').removePrefix("./")
        if (normalized.isBlank()) return null
        if (stripComponents <= 0) return normalized
        val parts = normalized.split('/').filter { it.isNotBlank() }
        if (parts.size <= stripComponents) return null
        return parts.drop(stripComponents).joinToString("/")
    }

    private fun stripLinkTarget(targetPath: String, stripComponents: Int): String {
        if (targetPath.startsWith("/")) return targetPath
        return stripArchivePath(targetPath, stripComponents) ?: targetPath
    }

    private fun handleSymlink(linkFile: File, targetPath: String) {
        linkFile.parentFile?.mkdirs()
        runCatching { linkFile.delete() }
        Os.symlink(targetPath, linkFile.absolutePath)
    }

    private fun handleHardLink(rootfsDir: File, linkFile: File, targetPath: String) {
        linkFile.parentFile?.mkdirs()
        runCatching { linkFile.delete() }

        val normalizedTarget = targetPath.removePrefix("/")
        val targetFile = File(rootfsDir, normalizedTarget)
        val linkParent = linkFile.parentFile ?: rootfsDir
        val symlinkTarget = runCatching {
            if (targetPath.startsWith("/")) {
                "/$normalizedTarget"
            } else {
                targetFile.relativeTo(linkParent).path
            }
        }.getOrDefault(targetPath)

        Os.symlink(symlinkTarget, linkFile.absolutePath)
    }

    private fun handleRegularFile(tarInput: TarArchiveInputStream, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
            val buffer = ByteArray(8192)
            var len: Int
            while (tarInput.read(buffer).also { len = it } != -1) {
                output.write(buffer, 0, len)
            }
        }
    }

    private fun setFilePermissions(file: File, mode: Int) {
        var permissions = mode and 0b111111111
        if (permissions > 0) {
            // Force owner read+write (and execute for dirs) so dpkg can
            // modify/delete files under proot's fake-root where the host
            // kernel still enforces real UID permission checks.
            permissions = if (file.isDirectory) {
                permissions or 448  // 0700
            } else {
                permissions or 384  // 0600
            }
            runCatching { Os.chmod(file.absolutePath, permissions) }
        }
    }
}

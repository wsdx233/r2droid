package top.wsdx233.r2droid.util

import android.content.Context
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
    val logs: List<String> = emptyList()
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
    private const val READY_MARKER_NAME = ".setup-complete"
    private const val MAX_LOG_LINES = 160

    private val installMutex = Mutex()

    private val _state = MutableStateFlow(ProotInstallState())
    val state = _state.asStateFlow()

    fun getRuntimeDir(context: Context): File = File(context.filesDir, "proot")

    fun getBinDir(context: Context): File = File(getRuntimeDir(context), "bin")

    fun getProotBinary(context: Context): File = File(getBinDir(context), "proot")

    fun getRootfsDir(context: Context): File = File(getRuntimeDir(context), "ubuntu")

    fun getHostTmpDir(context: Context): File = File(getRuntimeDir(context), "tmp")

    fun getReadyMarker(context: Context): File = File(getRuntimeDir(context), READY_MARKER_NAME)

    fun getR2rcFile(context: Context): File = File(getRootfsDir(context), "root/.radare2rc")

    fun getInstalledRootfsAlias(context: Context): String? = readReadyMetadata(context)["alias"]

    fun isEnvironmentReady(context: Context): Boolean {
        return getProotBinary(context).exists() && getReadyMarker(context).exists() && getRootfsDir(context).isDirectory
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

    fun ensureRuntimeBinary(context: Context) {
        val target = getProotBinary(context)
        val runtimeDir = getRuntimeDir(context)
        val assets = context.assets

        runtimeDir.mkdirs()
        target.parentFile?.mkdirs()

        val assetSize = runCatching { assets.openFd(PROOT_ASSET_NAME).length }.getOrNull()
        val needsCopy = !target.exists() || assetSize == null || target.length() != assetSize

        if (!needsCopy) {
            runCatching { Os.chmod(target.absolutePath, 493) }
            return
        }

        assets.open(PROOT_ASSET_NAME).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
        Os.chmod(target.absolutePath, 493)
    }

    private fun readReadyMetadata(context: Context): Map<String, String> {
        val marker = getReadyMarker(context)
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
                    appendLog("ERROR: ${error.message ?: "unknown error"}")
                    _state.value = _state.value.copy(
                        status = ProotInstallState.Status.ERROR,
                        message = error.message ?: "Unknown error"
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
                    appendLog("ERROR: ${error.message ?: "unknown error"}")
                    _state.value = _state.value.copy(
                        status = ProotInstallState.Status.ERROR,
                        message = error.message ?: "Unknown error"
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
            appendLog("Force reinstall requested, clearing existing rootfs.")
            getRootfsDir(context).deleteRecursively()
            getRootfsDir(context).mkdirs()
        }
    }

    private fun downloadRootfsIfNeeded(context: Context, option: ProotRootfsOption) {
        val archive = getArchiveFile(context, option)
        if (archive.exists() && archive.length() > 0L) {
            appendLog("Using cached rootfs archive: ${archive.absolutePath}")
            return
        }

        updateState(ProotInstallState.Status.DOWNLOADING, 0.1f, "Downloading ${option.displayName} rootfs...")
        appendLog("Downloading ${option.tarballUrl}")

        val conn = java.net.URL(option.tarballUrl).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.requestMethod = "GET"
        conn.connect()

        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("Failed to download rootfs: HTTP ${conn.responseCode}")
        }

        val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
        var downloaded = 0L
        var lastReported = 0L

        archive.parentFile?.mkdirs()
        BufferedInputStream(conn.inputStream).use { input ->
            FileOutputStream(archive).use { output ->
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
        conn.disconnect()
        appendLog("Download finished (${archive.length()} bytes).")
    }

    private fun extractRootfsIfNeeded(context: Context, option: ProotRootfsOption) {
        val rootfsDir = getRootfsDir(context)
        val readyMarker = getReadyMarker(context)
        val installedAlias = readReadyMetadata(context)["alias"]
        if (readyMarker.exists() && installedAlias == option.alias && rootfsDir.resolve("bin").exists()) {
            appendLog("Existing rootfs looks ready, skipping extraction.")
            return
        }

        val archive = getArchiveFile(context, option)
        if (!archive.exists()) {
            throw IllegalStateException("Rootfs archive is missing.")
        }

        updateState(ProotInstallState.Status.EXTRACTING, 0.38f, "Extracting ${option.displayName} rootfs...")
        appendLog("Extracting rootfs to ${rootfsDir.absolutePath}")

        rootfsDir.deleteRecursively()
        rootfsDir.mkdirs()

        val totalBytes = archive.length().takeIf { it > 0 } ?: -1L
        var bytesReadTotal = 0L
        var lastReported = 0L

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
                    if (!outputFile.canonicalPath.startsWith(rootfsDir.canonicalPath)) {
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
            Triple(0.7f, "Updating apt sources...", """
                set -e
                if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then
                    sed -i 's/^Components: .*/Components: main restricted universe multiverse/' /etc/apt/sources.list.d/ubuntu.sources
                fi
                apt-get update
            """.trimIndent()),
            Triple(0.78f, "Installing build toolchain...", """
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
            Triple(0.86f, "Installing radare2 from source...", """
                set -e
                export PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
                export MAKE_JOBS=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1)
                mkdir -p /opt/src
                if [ ! -d /opt/src/radare2/.git ]; then
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

        setupCommands.forEach { (progress, message, command) ->
            updateState(ProotInstallState.Status.INSTALLING_PACKAGES, progress, message)
            runProotCommand(context, command)
        }
    }

    private fun installPlugins(context: Context) {
        val pluginCommands = listOf(
            Triple(0.9f, "Installing r2dec with r2pm...", "r2dec"),
            Triple(0.95f, "Installing r2ghidra with r2pm...", "r2ghidra"),
            Triple(0.99f, "Installing r2frida with r2pm...", "r2frida")
        )

        pluginCommands.forEach { (progress, message, pluginName) ->
            updateState(ProotInstallState.Status.INSTALLING_PLUGINS, progress, message)
            runProotCommand(context, buildR2pmInstallScript(pluginName))
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
        _state.value = _state.value.copy(status = status, progress = progress, message = message)
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

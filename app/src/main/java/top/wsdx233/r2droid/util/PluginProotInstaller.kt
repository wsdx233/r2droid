package top.wsdx233.r2droid.util

import android.content.Context
import android.system.Os
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Dedicated Ubuntu proot used by app plugins that need a fuller Linux userspace
 * (Python venvs, build tools, project-specific native helpers, etc.).
 *
 * It intentionally lives outside the main r2 proot so heavy plugin dependencies
 * do not pollute the radare2 runtime. Host app files/cache are bind-mounted, so
 * plugin directories keep their normal absolute paths inside this environment.
 */
object PluginProotInstaller {
    private const val READY_MARKER_NAME = ".setup-complete"
    private const val EXTRACT_MARKER_NAME = ".rootfs-extracted"
    private const val MAX_LOG_LINES = 180

    private val _state = MutableStateFlow(ProotInstallState())
    val state = _state.asStateFlow()

    fun getRuntimeDir(context: Context): File = File(context.filesDir, "plugin-proot")

    fun getRootfsDir(context: Context): File = File(getRuntimeDir(context), "ubuntu")

    fun getHostTmpDir(context: Context): File = File(getRuntimeDir(context), "tmp")

    fun getReadyMarker(context: Context): File = File(getRuntimeDir(context), READY_MARKER_NAME)

    private fun getExtractMarker(context: Context): File = File(getRuntimeDir(context), EXTRACT_MARKER_NAME)

    fun getVenvDir(context: Context, name: String): File {
        val safe = name.trim().ifBlank { "default" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return File(getRootfsDir(context), "opt/r2droid-plugin-venvs/$safe")
    }

    fun isReady(context: Context): Boolean {
        return ProotInstaller.getProotBinary(context).exists() &&
            getReadyMarker(context).exists() &&
            getRootfsDir(context).isDirectory
    }

    fun resetState() {
        _state.value = ProotInstallState()
    }

    fun updateState(status: ProotInstallState.Status, progress: Float, message: String) {
        _state.update { current ->
            current.copy(
                status = status,
                progress = progress.coerceIn(0f, 1f),
                message = message,
                canRetryCurrentStage = false
            )
        }
    }

    fun appendLog(line: String) {
        _state.update { current ->
            current.copy(logs = (current.logs + line).takeLast(MAX_LOG_LINES))
        }
    }

    fun markDone(message: String = "Plugin proot environment is ready.") {
        _state.update { current ->
            current.copy(
                status = ProotInstallState.Status.DONE,
                progress = 1f,
                message = message
            )
        }
    }

    fun markError(error: Throwable, context: Context? = null, sourceUrl: String? = null) {
        val reason = if (context != null) {
            ProotInstaller.buildInstallFailureReason(context, error, sourceUrl)
        } else {
            error.message ?: "Unknown error"
        }
        appendLog("INSTALL FAILED: $reason")
        _state.update { current ->
            current.copy(
                status = ProotInstallState.Status.ERROR,
                message = reason,
                canRetryCurrentStage = true
            )
        }
    }

    @Synchronized
    fun ensureReady(
        context: Context,
        rootfsAlias: String = "ubuntu",
        forceReinstall: Boolean = false,
        logger: ((String) -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val option = ProotRootfsCatalog.resolve(appContext, rootfsAlias.ifBlank { "ubuntu" })
        val installedAlias = readReadyMetadata(appContext)["alias"]
        if (!forceReinstall && isReady(appContext) && installedAlias == option.alias) {
            val message = "Plugin proot is ready (${option.displayName})."
            appendLog(message)
            logger?.invoke(message)
            return
        }

        updateState(ProotInstallState.Status.PREPARING, 0.02f, "Preparing plugin proot runtime...")
        appendLog("Preparing plugin proot runtime...")
        logger?.invoke("Preparing plugin proot runtime...")
        ProotInstaller.ensureRuntimeBinary(appContext)
        prepareRuntime(appContext, forceReinstall || (installedAlias != null && installedAlias != option.alias))
        downloadRootfsIfNeeded(appContext, option, logger)
        extractRootfs(appContext, option, logger)
        configureRootfs(appContext)
        writeReadyMetadata(appContext, option)
        markDone("Plugin proot setup completed (${option.displayName}).")
        logger?.invoke("Plugin proot setup completed (${option.displayName}).")
    }

    fun runCommand(
        context: Context,
        script: String,
        term: String = "dumb",
        workingDir: String = "/root",
        extraBindPaths: Set<String> = emptySet(),
        logger: ((String) -> Unit)? = null
    ): String {
        check(isReady(context)) { "Plugin proot environment is not ready." }
        logger?.invoke("$ ${script.lineSequence().firstOrNull()?.take(120) ?: "command"}")
        val spec = buildShellSpec(context, script, term, workingDir, extraBindPaths)
        val processBuilder = ProcessBuilder(spec.command)
            .directory(spec.workingDirectory)
            .redirectErrorStream(true)
        processBuilder.environment().putAll(spec.environment)

        val output = StringBuilder()
        val process = processBuilder.start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                output.appendLine(line)
                if (line.isNotBlank()) logger?.invoke(line)
            }
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException("Plugin proot command failed with exit code $exitCode")
        }
        return output.toString()
    }

    fun buildShellSpec(
        context: Context,
        shellCommand: String,
        term: String = "dumb",
        workingDir: String = "/root",
        extraBindPaths: Set<String> = emptySet()
    ): R2Runtime.LaunchSpec {
        val appContext = context.applicationContext
        val guestShell = resolveGuestShell(appContext)
        val command = buildProotPrefix(appContext, term, workingDir, extraBindPaths).apply {
            addAll(listOf(guestShell, "-l", "-c", shellCommand))
        }
        val hostTmpDir = getHostTmpDir(appContext).absolutePath
        return R2Runtime.LaunchSpec(
            command = command,
            workingDirectory = getRuntimeDir(appContext),
            environment = mapOf(
                "TMPDIR" to hostTmpDir,
                "PROOT_TMP_DIR" to hostTmpDir
            )
        )
    }

    fun installAptPackages(context: Context, packages: List<String>, logger: ((String) -> Unit)? = null): String {
        val normalized = packages.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return ""
        val joined = normalized.joinToString(" ") { shellEscape(it) }
        return runCommand(
            context = context,
            script = """
                set -e
                export DEBIAN_FRONTEND=noninteractive
                apt-get update
                apt-get install -y --no-install-recommends $joined
            """.trimIndent(),
            logger = logger
        )
    }

    fun createPythonVenv(
        context: Context,
        name: String,
        packages: List<String> = emptyList(),
        requirementsPath: String? = null,
        logger: ((String) -> Unit)? = null
    ): String {
        val safe = name.trim().ifBlank { "default" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val venvGuestPath = "/opt/r2droid-plugin-venvs/$safe"
        val packageArgs = packages.map { it.trim() }.filter { it.isNotBlank() }
            .joinToString(" ") { shellEscape(it) }
        val reqArg = requirementsPath?.trim()?.takeIf { it.isNotBlank() }?.let { " -r ${shellEscape(it)}" }.orEmpty()
        runCommand(
            context = context,
            script = """
                set -e
                export DEBIAN_FRONTEND=noninteractive
                apt-get update
                apt-get install -y --no-install-recommends python3 python3-venv python3-pip ca-certificates
                mkdir -p /opt/r2droid-plugin-venvs
                python3 -m venv $venvGuestPath
                $venvGuestPath/bin/python -m pip install --upgrade pip setuptools wheel
                if [ -n ${shellEscape(packageArgs + reqArg)} ]; then
                    $venvGuestPath/bin/pip install $packageArgs$reqArg
                fi
            """.trimIndent(),
            logger = logger
        )
        return venvGuestPath
    }

    fun pathInfo(context: Context): String {
        val root = getRootfsDir(context)
        val tmp = getHostTmpDir(context)
        return "{" +
            "\"runtimeDir\":\"${escapeJson(getRuntimeDir(context).absolutePath)}\"," +
            "\"rootfsDir\":\"${escapeJson(root.absolutePath)}\"," +
            "\"tmpDir\":\"${escapeJson(tmp.absolutePath)}\"," +
            "\"venvRoot\":\"/opt/r2droid-plugin-venvs\"" +
            "}"
    }

    private fun prepareRuntime(context: Context, forceReinstall: Boolean) {
        getRuntimeDir(context).mkdirs()
        getHostTmpDir(context).mkdirs()
        runCatching { Os.chmod(getHostTmpDir(context).absolutePath, 511) }
        getReadyMarker(context).delete()
        if (forceReinstall) {
            SafeFileOps.deleteRecursivelyNoFollow(getRootfsDir(context))
            getExtractMarker(context).delete()
        }
        getRootfsDir(context).mkdirs()
    }

    private fun downloadRootfsIfNeeded(context: Context, option: ProotRootfsOption, logger: ((String) -> Unit)?, forceRedownload: Boolean = false) {
        val archive = getArchiveFile(context, option)
        if (!forceRedownload && archive.exists() && archive.length() > 0L) {
            val message = "Using cached plugin proot rootfs: ${archive.absolutePath}"
            updateState(ProotInstallState.Status.DOWNLOADING, 0.45f, message)
            appendLog(message)
            logger?.invoke(message)
            return
        }
        if (forceRedownload) {
            appendLog("Discarding cached plugin proot rootfs and downloading it again.")
            logger?.invoke("Discarding cached plugin proot rootfs and downloading it again.")
            archive.delete()
        }
        updateState(ProotInstallState.Status.DOWNLOADING, 0.05f, "Downloading plugin proot rootfs...")
        appendLog("Downloading plugin proot rootfs: ${option.tarballUrl}")
        logger?.invoke("Downloading plugin proot rootfs: ${option.tarballUrl}")
        val archiveDir = archive.parentFile ?: File(context.cacheDir, "plugin-proot")
        archiveDir.mkdirs()
        val tempArchive = File(archiveDir, "${archive.name}.part")
        tempArchive.delete()
        val conn = java.net.URL(option.tarballUrl).openConnection() as java.net.HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.connect()
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Failed to download plugin proot rootfs: HTTP ${conn.responseCode}")
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
                        if (downloaded - lastReported >= 512 * 1024 || (totalBytes > 0 && downloaded == totalBytes)) {
                            lastReported = downloaded
                            if (totalBytes > 0) {
                                val progress = 0.05f + (downloaded.toFloat() / totalBytes.toFloat()) * 0.40f
                                updateState(ProotInstallState.Status.DOWNLOADING, progress, "Downloading plugin proot rootfs...")
                            } else {
                                updateState(ProotInstallState.Status.DOWNLOADING, 0.1f, "Downloading plugin proot rootfs... ${downloaded / 1024 / 1024} MB")
                            }
                        }
                    }
                }
            }
            if (totalBytes > 0 && tempArchive.length() != totalBytes) {
                throw IllegalStateException("Plugin proot rootfs download incomplete: ${tempArchive.length()} / $totalBytes bytes")
            }
            archive.delete()
            if (!tempArchive.renameTo(archive)) {
                tempArchive.copyTo(archive, overwrite = true)
                tempArchive.delete()
            }
            updateState(ProotInstallState.Status.DOWNLOADING, 0.45f, "Plugin proot rootfs downloaded.")
            appendLog("Plugin proot rootfs downloaded (${archive.length()} bytes).")
            logger?.invoke("Plugin proot rootfs downloaded (${archive.length()} bytes).")
        } catch (error: Throwable) {
            tempArchive.delete()
            throw error
        } finally {
            conn.disconnect()
        }
    }

    private fun extractRootfs(context: Context, option: ProotRootfsOption, logger: ((String) -> Unit)?) {
        val rootfsDir = getRootfsDir(context)
        val readyAlias = readReadyMetadata(context)["alias"]
        val extractedAlias = readMarkerMetadata(getExtractMarker(context))["alias"]
        if ((readyAlias == option.alias || extractedAlias == option.alias) && rootfsDir.resolve("bin").exists()) {
            val message = "Plugin proot rootfs is already extracted; skipping extraction."
            appendLog(message)
            logger?.invoke(message)
            return
        }
        runCatching {
            extractRootfsArchive(context, option, logger)
        }.onFailure { firstError ->
            appendLog("Plugin proot extraction failed: ${firstError.message ?: firstError::class.java.simpleName}")
            logger?.invoke("Plugin proot extraction failed; re-downloading rootfs and retrying extraction once.")
            getArchiveFile(context, option).delete()
            SafeFileOps.deleteRecursivelyNoFollow(rootfsDir)
            getExtractMarker(context).delete()
            downloadRootfsIfNeeded(context, option, logger, forceRedownload = true)
            extractRootfsArchive(context, option, logger)
        }.getOrThrow()
    }

    private fun extractRootfsArchive(context: Context, option: ProotRootfsOption, logger: ((String) -> Unit)?) {
        val archive = getArchiveFile(context, option)
        check(archive.exists()) { "Plugin proot rootfs archive is missing." }
        val rootfsDir = getRootfsDir(context)
        updateState(ProotInstallState.Status.EXTRACTING, 0.46f, "Extracting plugin proot rootfs...")
        appendLog("Extracting plugin proot rootfs to ${rootfsDir.absolutePath}")
        logger?.invoke("Extracting plugin proot rootfs to ${rootfsDir.absolutePath}")
        SafeFileOps.deleteRecursivelyNoFollow(rootfsDir)
        rootfsDir.mkdirs()
        getExtractMarker(context).delete()
        val rootCanonical = rootfsDir.canonicalPath
        var entryCount = 0
        archive.inputStream().buffered().use { fileInput ->
            createTarInputStream(archive, fileInput).use { tarInput ->
                var entry: TarArchiveEntry?
                while (tarInput.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    val normalizedName = stripArchivePath(currentEntry.name, option.tarballStripOpt) ?: continue
                    val outputFile = File(rootfsDir, normalizedName)
                    if (!outputFile.canonicalPath.startsWith(rootCanonical)) {
                        throw SecurityException("Invalid archive entry: ${currentEntry.name}")
                    }
                    when {
                        currentEntry.isDirectory -> outputFile.mkdirs()
                        currentEntry.isSymbolicLink -> handleSymlink(outputFile, currentEntry.linkName)
                        currentEntry.isLink -> handleHardLink(rootfsDir, outputFile, stripLinkTarget(currentEntry.linkName, option.tarballStripOpt))
                        currentEntry.isFile -> handleRegularFile(tarInput, outputFile)
                    }
                    if (!currentEntry.isSymbolicLink && !currentEntry.isLink) {
                        setFilePermissions(outputFile, currentEntry.mode)
                    }
                    entryCount++
                    if (entryCount % 250 == 0) {
                        val progress = (0.46f + (entryCount.coerceAtMost(5000) / 5000f) * 0.29f).coerceAtMost(0.75f)
                        updateState(ProotInstallState.Status.EXTRACTING, progress, "Extracting plugin proot rootfs... ($entryCount files)")
                    }
                }
            }
        }
        writeExtractMetadata(context, option)
        updateState(ProotInstallState.Status.EXTRACTING, 0.75f, "Plugin proot extraction finished.")
        appendLog("Plugin proot extraction finished.")
        logger?.invoke("Plugin proot extraction finished.")
    }

    private fun configureRootfs(context: Context) {
        updateState(ProotInstallState.Status.CONFIGURING, 0.78f, "Configuring plugin proot environment...")
        val rootfsDir = getRootfsDir(context)
        val resolvConf = File(rootfsDir, "etc/resolv.conf")
        resolvConf.parentFile?.mkdirs()
        resolvConf.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")

        File(rootfsDir, "etc/apt/apt.conf.d").apply { mkdirs() }
            .resolve("99proot-nosandbox")
            .writeText("APT::Sandbox::User \"root\";\n")
        File(rootfsDir, "etc/dpkg/dpkg.cfg.d").apply { mkdirs() }
            .resolve("force-unsafe-io")
            .writeText("force-unsafe-io\n")

        File(rootfsDir, "etc/hosts").apply {
            parentFile?.mkdirs()
            if (!exists()) writeText("127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n")
        }
        listOf(File(rootfsDir, "tmp"), File(rootfsDir, "var/tmp"), getHostTmpDir(context)).forEach {
            it.mkdirs()
            runCatching { Os.chmod(it.absolutePath, 511) }
        }
        val fakeProcCrypto = File(rootfsDir, "proc/sys/crypto")
        fakeProcCrypto.mkdirs()
        File(fakeProcCrypto, "fips_enabled").writeText("0\n")
    }

    private fun buildProotPrefix(
        context: Context,
        term: String,
        workingDir: String,
        extraBindPaths: Set<String>
    ): MutableList<String> {
        val prootBinary = ProotInstaller.getProotBinary(context)
        val rootfsDir = getRootfsDir(context)
        val hostTmpDir = getHostTmpDir(context).apply { mkdirs() }
        val command = mutableListOf(
            prootBinary.absolutePath,
            "-L",
            "--link2symlink",
            "--kill-on-exit",
            "--root-id",
            "-r",
            rootfsDir.absolutePath
        )

        collectBindPaths(context, extraBindPaths).filter { File(it).exists() }.forEach { path ->
            command += listOf("-b", path)
        }
        listOf(
            "/dev/urandom" to "/dev/random",
            "/proc/self/fd" to "/dev/fd",
            hostTmpDir.absolutePath to "/tmp",
            hostTmpDir.absolutePath to "/var/tmp"
        ).forEach { (hostPath, guestPath) ->
            if (File(hostPath).exists()) command += listOf("-b", "$hostPath:$guestPath")
        }
        val devShmSource = File(rootfsDir, "tmp")
        if (devShmSource.exists()) command += listOf("-b", "${devShmSource.absolutePath}:/dev/shm")
        val fakeFips = File(rootfsDir, "proc/sys/crypto/fips_enabled")
        if (fakeFips.exists()) command += listOf("-b", "${fakeFips.absolutePath}:/proc/sys/crypto/fips_enabled")

        command += listOf(
            "-w", workingDir.ifBlank { "/root" },
            "/usr/bin/env", "-i",
            "HOME=/root",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "XDG_DATA_HOME=/root/.local/share",
            "XDG_CACHE_HOME=/root/.cache",
            "TMPDIR=/tmp",
            "R2_NOCOLOR=1",
            "TERM=$term"
        )
        return command
    }

    private fun collectBindPaths(context: Context, extraBindPaths: Set<String>): Set<String> {
        val filesDir = context.filesDir
        val cacheDir = context.cacheDir
        return linkedSetOf<String>().apply {
            add("/dev")
            add("/proc")
            add("/sys")
            add(filesDir.absolutePath)
            add(cacheDir.absolutePath)
            add("/data/data/${context.packageName}/files")
            add("/data/data/${context.packageName}/cache")
            add("/storage")
            add("/sdcard")
            add("/mnt")
            addAll(extraBindPaths.filter { it.isNotBlank() })
        }
    }

    private fun resolveGuestShell(context: Context): String {
        val rootfsDir = getRootfsDir(context)
        return when {
            File(rootfsDir, "bin/bash").exists() -> "/bin/bash"
            File(rootfsDir, "usr/bin/bash").exists() -> "/usr/bin/bash"
            else -> "/bin/sh"
        }
    }

    private fun readReadyMetadata(context: Context): Map<String, String> = readMarkerMetadata(getReadyMarker(context))

    private fun readMarkerMetadata(marker: File): Map<String, String> {
        if (!marker.exists()) return emptyMap()
        return marker.readLines().mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }.toMap()
    }

    private fun writeReadyMetadata(context: Context, option: ProotRootfsOption) {
        getReadyMarker(context).apply {
            parentFile?.mkdirs()
            writeText(
                buildString {
                    appendLine("alias=${option.alias}")
                    appendLine("url=${option.tarballUrl}")
                    appendLine("mode=plugin")
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

    private fun getArchiveFile(context: Context, option: ProotRootfsOption): File {
        val safeName = option.archiveFileName.ifBlank { "${option.alias}.tar" }
        return File(context.cacheDir, "plugin-proot/$safeName")
    }

    private fun createTarInputStream(archive: File, input: InputStream): TarArchiveInputStream {
        val archiveInput = when {
            archive.name.endsWith(".tar.gz", true) || archive.name.endsWith(".tgz", true) -> GzipCompressorInputStream(input)
            archive.name.endsWith(".tar.xz", true) || archive.name.endsWith(".txz", true) -> XZCompressorInputStream(input)
            archive.name.endsWith(".tar", true) -> input
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
            if (targetPath.startsWith("/")) "/$normalizedTarget" else targetFile.relativeTo(linkParent).path
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
            permissions = if (file.isDirectory) permissions or 448 else permissions or 384
            runCatching { Os.chmod(file.absolutePath, permissions) }
        }
    }

    private fun shellEscape(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

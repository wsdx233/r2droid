package top.wsdx233.r2droid.feature.plugin

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

object PluginManager {
    private const val TAG = "PluginManager"
    private const val DEFAULT_REMOTE_INDEX = "https://raw.githubusercontent.com/wsdx233/r2droid-plugins/refs/heads/main/index.json"
    private const val DEVELOPER_SOURCE_PREFIX = "developer://"

    private val initialized = AtomicBoolean(false)
    private lateinit var appContext: Context

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _catalog = MutableStateFlow<List<PluginCatalogItem>>(emptyList())
    val catalog = _catalog.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installed = _installed.asStateFlow()

    private val _projectTabs = MutableStateFlow<List<PluginProjectTabDescriptor>>(emptyList())
    val projectTabs = _projectTabs.asStateFlow()

    private val _screenTabs = MutableStateFlow<List<PluginScreenTabDescriptor>>(emptyList())
    val screenTabs = _screenTabs.asStateFlow()

    private val _repositorySources = MutableStateFlow<List<String>>(emptyList())
    val repositorySources = _repositorySources.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking = _isWorking.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _installProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val installProgress = _installProgress.asStateFlow()

    private val _developerConfig = MutableStateFlow(PluginDeveloperConfig())
    val developerConfig = _developerConfig.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        ioScope.launch {
            ensureStructure()
            loadRepositorySources()
            loadDeveloperConfig()
            installBundledPluginsIfNeeded()
            syncDeveloperWorkspacePlugins()
            reloadInstalledFromDisk()
            runStartupEntryScripts()
            refreshCatalog()
        }
    }

    suspend fun refreshCatalog() = withContext(Dispatchers.IO) {
        ensureInitialized()
        syncDeveloperWorkspacePlugins()
        reloadInstalledFromDisk()
        _isWorking.value = true
        _status.value = "Refreshing plugin index..."
        try {
            val merged = linkedMapOf<String, PluginIndexEntry>()
            _repositorySources.value.forEach { source ->
                runCatching { loadIndexFromSource(source) }
                    .onSuccess { index ->
                        index.plugins.forEach { entry ->
                            val existing = merged[entry.id]
                            if (existing == null || compareVersion(entry.version, existing.version) > 0) {
                                merged[entry.id] = entry
                            }
                        }
                    }
                    .onFailure { e ->
                        appendLog("[catalog] source=$source failed: ${e.message}")
                    }
            }

            val installedMap = _installed.value.associateBy { it.state.id }
            _catalog.value = merged.values
                .sortedBy { it.name.lowercase() }
                .map { entry ->
                    val installedPlugin = installedMap[entry.id]
                    val hasUpgrade = installedPlugin?.let {
                        compareVersion(entry.version, it.state.version) > 0
                    } ?: false
                    PluginCatalogItem(
                        indexEntry = entry,
                        installed = installedPlugin,
                        hasUpgrade = hasUpgrade
                    )
                }
            _status.value = "Plugin index refreshed"
        } finally {
            _isWorking.value = false
        }
    }

    suspend fun install(entry: PluginIndexEntry): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        _isWorking.value = true
        _status.value = "Installing ${entry.name}..."
        updateInstallProgress(entry.id, 0.05f)
        val tempZip = File(tempDir(), "${entry.id}-${System.currentTimeMillis()}.zip")
        val stagedDir = File(tempDir(), "${entry.id}-staged")
        val targetDir = File(packagesDir(), entry.id)
        val backupDir = File(packagesDir(), "${entry.id}-backup")

        runCatching {
            stagedDir.deleteRecursively()
            backupDir.deleteRecursively()

            downloadFile(entry.downloadUrl, tempZip) { progress ->
                updateInstallProgress(entry.id, 0.05f + progress * 0.55f)
            }
            updateInstallProgress(entry.id, 0.65f)
            val digest = sha256(tempZip)
            if (!digest.equals(entry.sha256, ignoreCase = true)) {
                throw IllegalStateException("sha256 mismatch: expected=${entry.sha256}, actual=$digest")
            }

            stagedDir.mkdirs()
            unzipSafely(tempZip, stagedDir)
            updateInstallProgress(entry.id, 0.8f)

            val manifestFile = File(stagedDir, entry.manifestPath)
            if (!manifestFile.exists()) {
                throw IllegalStateException("manifest not found: ${entry.manifestPath}")
            }
            val manifest = json.decodeFromString(PluginManifest.serializer(), manifestFile.readText())
            if (manifest.id != entry.id) {
                throw IllegalStateException("manifest.id mismatch: ${manifest.id} != ${entry.id}")
            }

            if (targetDir.exists()) {
                if (!targetDir.renameTo(backupDir)) {
                    throw IllegalStateException("failed to backup current plugin directory")
                }
            }

            if (targetDir.exists()) targetDir.deleteRecursively()
            if (!stagedDir.renameTo(targetDir)) {
                throw IllegalStateException("failed to move staged plugin to target")
            }

            backupDir.deleteRecursively()

            val states = readInstalledStates().toMutableList()
            states.removeAll { it.id == entry.id }
            states += InstalledPluginState(
                id = entry.id,
                version = entry.version,
                installDir = targetDir.absolutePath,
                enabled = true,
                sourceUrl = entry.downloadUrl,
                sha256 = entry.sha256,
                manifestPath = entry.manifestPath,
                installedAt = System.currentTimeMillis()
            )
            writeInstalledStates(states)
            updateInstallProgress(entry.id, 0.95f)
            reloadInstalledFromDisk()
            runInstallScriptIfPresent(entry.id, reason = "install")
            refreshCatalog()
            appendLog("[install] ${entry.id}@${entry.version} success")
        }.recoverCatching { throwable ->
            appendLog("[install] ${entry.id} failed: ${throwable.message}")
            if (!targetDir.exists() && backupDir.exists()) {
                backupDir.renameTo(targetDir)
            }
            stagedDir.deleteRecursively()
            tempZip.delete()
            throw throwable
        }.onSuccess {
            _status.value = "Installed ${entry.name}"
        }.onFailure {
            _status.value = "Install failed: ${it.message}"
        }.also {
            tempZip.delete()
            stagedDir.deleteRecursively()
            clearInstallProgress(entry.id)
            _isWorking.value = false
        }
    }

    suspend fun uninstall(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        _isWorking.value = true
        _status.value = "Uninstalling $pluginId..."
        runCatching {
            val states = readInstalledStates().toMutableList()
            val target = states.find { it.id == pluginId }
            states.removeAll { it.id == pluginId }
            target?.let {
                PluginRuntime.stopAllForPlugin(pluginId)
                File(it.installDir).deleteRecursively()
            }
            writeInstalledStates(states)
            reloadInstalledFromDisk()
            refreshCatalog()
            appendLog("[uninstall] $pluginId success")
            _status.value = "Uninstalled $pluginId"
        }.onFailure {
            appendLog("[uninstall] $pluginId failed: ${it.message}")
            _status.value = "Uninstall failed: ${it.message}"
        }.also {
            _isWorking.value = false
        }
    }

    suspend fun delete(pluginId: String): Result<Unit> = uninstall(pluginId)

    suspend fun update(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val entry = _catalog.value.firstOrNull { it.indexEntry.id == pluginId }?.indexEntry
            ?: return@withContext Result.failure(IllegalStateException("plugin not found in catalog: $pluginId"))
        install(entry)
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        runCatching {
            val updated = readInstalledStates().map {
                if (it.id == pluginId) it.copy(enabled = enabled) else it
            }
            writeInstalledStates(updated)
            reloadInstalledFromDisk()
            if (enabled) {
                runInstallScriptIfPresent(pluginId, reason = "enable")
            }
            refreshCatalog()
            appendLog("[enable] $pluginId => $enabled")
        }
    }

    suspend fun addRepositorySource(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        runCatching {
            val normalized = normalizeRepositorySource(url)
            if (normalized.isBlank()) error("empty source url")
            val current = _repositorySources.value.toMutableList()
            if (current.none { it.equals(normalized, ignoreCase = true) }) {
                current += normalized
            }
            writeRepositorySources(current)
            _repositorySources.value = current
            refreshCatalog()
        }
    }

    suspend fun removeRepositorySource(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        runCatching {
            val normalized = normalizeRepositorySource(url)
            val current = _repositorySources.value.filterNot { it.equals(normalized, ignoreCase = true) }
            writeRepositorySources(current)
            _repositorySources.value = current
            refreshCatalog()
        }
    }

    suspend fun installFromZipUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        _isWorking.value = true
        _status.value = "Installing plugin from ZIP..."

        val tempZip = File(tempDir(), "import-${System.currentTimeMillis()}.zip")
        val stagedDir = File(tempDir(), "import-${System.currentTimeMillis()}-staged")
        var targetDir: File? = null
        var backupDir: File? = null

        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                tempZip.parentFile?.mkdirs()
                FileOutputStream(tempZip).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("failed to open zip uri")

            stagedDir.deleteRecursively()
            stagedDir.mkdirs()
            unzipSafely(tempZip, stagedDir)

            val manifestFile = File(stagedDir, "manifest.json")
            if (!manifestFile.exists()) {
                throw IllegalStateException("manifest not found: manifest.json")
            }

            val manifest = json.decodeFromString(PluginManifest.serializer(), manifestFile.readText())
            val normalizedId = normalizePluginId(manifest.id)
            if (normalizedId.isBlank()) {
                throw IllegalStateException("invalid manifest.id")
            }

            targetDir = File(packagesDir(), normalizedId)
            backupDir = File(packagesDir(), "$normalizedId-backup")

            backupDir?.deleteRecursively()
            if (targetDir?.exists() == true) {
                if (targetDir?.renameTo(backupDir) != true) {
                    throw IllegalStateException("failed to backup current plugin directory")
                }
            }

            if (targetDir?.exists() == true) targetDir?.deleteRecursively()
            if (stagedDir.renameTo(targetDir) != true) {
                throw IllegalStateException("failed to move staged plugin to target")
            }
            backupDir?.deleteRecursively()

            val digest = sha256(tempZip)
            val states = readInstalledStates().toMutableList().apply {
                removeAll { it.id == normalizedId }
                add(
                    InstalledPluginState(
                        id = normalizedId,
                        version = manifest.version,
                        installDir = targetDir?.absolutePath ?: "",
                        enabled = true,
                        sourceUrl = uri.toString(),
                        sha256 = digest,
                        manifestPath = "manifest.json",
                        installedAt = System.currentTimeMillis()
                    )
                )
            }
            writeInstalledStates(states)
            reloadInstalledFromDisk()
            runInstallScriptIfPresent(normalizedId, reason = "install")
            refreshCatalog()
            appendLog("[install] $normalizedId@${manifest.version} from zip success")
            _status.value = "Installed ${manifest.name}"
        }.recoverCatching { throwable ->
            val rollbackTarget = targetDir
            val rollbackBackup = backupDir
            if (rollbackTarget != null && rollbackBackup != null && !rollbackTarget.exists() && rollbackBackup.exists()) {
                rollbackBackup.renameTo(rollbackTarget)
            }
            appendLog("[install] zip failed: ${throwable.message}")
            throw throwable
        }.onFailure {
            _status.value = "Install failed: ${it.message}"
        }.also {
            tempZip.delete()
            stagedDir.deleteRecursively()
            _isWorking.value = false
        }
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        runCatching {
            val next = _developerConfig.value.copy(enabled = enabled)
            writeDeveloperConfig(next)
            _developerConfig.value = next
            refreshCatalog()
        }
    }

    suspend fun setDeveloperWorkspaceDir(path: String?): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        runCatching {
            val normalizedPath = path?.trim()?.ifBlank { null }
            val next = _developerConfig.value.copy(workspaceDir = normalizedPath)
            writeDeveloperConfig(next)
            _developerConfig.value = next
            refreshCatalog()
        }
    }

    suspend fun createDeveloperPlugin(request: DeveloperPluginCreateRequest): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        _isWorking.value = true
        _status.value = "Creating plugin ${request.id}..."

        val pluginId = normalizePluginId(request.id)
        var pluginDir: File? = null
        var created = false

        runCatching {
            require(pluginId.isNotBlank()) { "invalid plugin id" }
            require(request.name.isNotBlank()) { "plugin name is required" }
            require(request.version.isNotBlank()) { "plugin version is required" }

            val config = _developerConfig.value
            require(config.enabled) { "developer mode is disabled" }
            val workspacePath = config.workspaceDir?.trim().orEmpty()
            require(workspacePath.isNotBlank()) { "developer workspace is not set" }

            val workspaceDir = File(workspacePath)
            require(workspaceDir.exists() && workspaceDir.isDirectory) { "developer workspace not found" }

            pluginDir = File(workspaceDir, pluginId)
            require(pluginDir?.exists() != true) { "plugin already exists: $pluginId" }
            pluginDir?.mkdirs()

            val entry = when (request.type) {
                DeveloperPluginType.WEBVIEW -> {
                    val uiDir = File(pluginDir, "ui").apply { mkdirs() }
                    File(uiDir, "index.html").writeText(defaultWebviewHtml(request.name))
                    PluginEntry(page = PluginPage(type = "webview", path = "ui/index.html"))
                }

                DeveloperPluginType.SCHEMA -> {
                    File(pluginDir, "schema.json").writeText(defaultSchemaJson(request.name))
                    PluginEntry(page = PluginPage(type = "schema", path = "schema.json"))
                }

                DeveloperPluginType.TERMINAL -> {
                    PluginEntry(terminal = PluginTerminalEntry(command = "r2 -v", title = request.name))
                }
            }

            val manifest = PluginManifest(
                id = pluginId,
                name = request.name.trim(),
                version = request.version.trim(),
                description = request.description.trim(),
                author = request.author.trim(),
                permissions = emptyList(),
                entry = entry,
                ui = PluginUiOptions(),
                tabs = emptyList(),
                projectTabs = emptyList()
            )
            File(pluginDir, "manifest.json").writeText(
                json.encodeToString(PluginManifest.serializer(), manifest)
            )

            created = true
            appendLog("[developer] created plugin $pluginId (${request.type.key})")
            runCatching { refreshCatalog() }
                .onFailure { appendLog("[developer] refresh failed after create: ${it.message}") }
            _status.value = "Created plugin $pluginId"
        }.recoverCatching { throwable ->
            if (!created && pluginDir?.exists() == true) {
                pluginDir?.deleteRecursively()
            }
            appendLog("[developer] create failed: ${throwable.message}")
            throw throwable
        }.onFailure {
            _status.value = "Create plugin failed: ${it.message}"
        }.also {
            _isWorking.value = false
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun findInstalledPlugin(pluginId: String): InstalledPlugin? {
        return _installed.value.firstOrNull { it.state.id == pluginId }
    }

    fun resolvePluginFile(pluginId: String, relativePath: String): File? {
        val plugin = findInstalledPlugin(pluginId) ?: return null
        return resolvePathUnder(File(plugin.state.installDir), relativePath, mustExist = true)
    }

    fun resolvePluginPath(pluginId: String, relativePath: String, mustExist: Boolean = false): File? {
        val plugin = findInstalledPlugin(pluginId) ?: return null
        return resolvePathUnder(File(plugin.state.installDir), relativePath, mustExist = mustExist)
    }

    fun resolvePluginDataFile(pluginId: String, relativePath: String, mustExist: Boolean = false): File? {
        val plugin = findInstalledPlugin(pluginId) ?: return null
        val dataRoot = File(plugin.state.installDir, "data").apply { mkdirs() }
        return resolvePathUnder(dataRoot, relativePath, mustExist = mustExist)
    }

    fun getPluginPermissions(pluginId: String): Set<String> {
        val plugin = findInstalledPlugin(pluginId) ?: return emptySet()
        return plugin.manifest?.permissions?.toSet().orEmpty()
    }

    fun appendRuntimeLog(pluginId: String, line: String) {
        appendLog("[runtime][$pluginId] $line")
    }

    private fun ensureInitialized() {
        check(initialized.get()) { "PluginManager not initialized" }
    }

    private fun ensureStructure() {
        rootDir().mkdirs()
        packagesDir().mkdirs()
        tempDir().mkdirs()
    }

    private suspend fun loadRepositorySources() {
        val repoFile = repositoriesFile()
        val sources = runCatching {
            val raw = if (!repoFile.exists()) {
                listOf(DEFAULT_REMOTE_INDEX)
            } else {
                json.decodeFromString(ListSerializer(String.serializer()), repoFile.readText())
            }

            raw.map { normalizeRepositorySource(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf(DEFAULT_REMOTE_INDEX) }
        }.getOrElse {
            appendLog("[repo] read failed: ${it.message}")
            listOf(DEFAULT_REMOTE_INDEX)
        }

        if (!repoFile.exists() || runCatching { repoFile.readText() }.getOrDefault("") != json.encodeToString(ListSerializer(String.serializer()), sources)) {
            writeRepositorySources(sources)
        }
        _repositorySources.value = sources
    }

    private suspend fun loadDeveloperConfig() {
        val file = developerConfigFile()
        val config = runCatching {
            if (!file.exists()) {
                PluginDeveloperConfig()
            } else {
                json.decodeFromString(PluginDeveloperConfig.serializer(), file.readText())
            }
        }.getOrElse {
            appendLog("[developer] read config failed: ${it.message}")
            PluginDeveloperConfig()
        }

        val normalized = config.copy(
            workspaceDir = config.workspaceDir?.trim()?.ifBlank { null }
        )

        _developerConfig.value = normalized
        if (!file.exists() || runCatching { file.readText() }.getOrDefault("") != json.encodeToString(PluginDeveloperConfig.serializer(), normalized)) {
            writeDeveloperConfig(normalized)
        }
    }

    private fun writeDeveloperConfig(config: PluginDeveloperConfig) {
        developerConfigFile().writeText(
            json.encodeToString(PluginDeveloperConfig.serializer(), config)
        )
    }

    private suspend fun syncDeveloperWorkspacePlugins() = withContext(Dispatchers.IO) {
        val config = _developerConfig.value
        val previous = readInstalledStates()
        val next = previous
            .filterNot { it.sourceUrl.startsWith(DEVELOPER_SOURCE_PREFIX) }
            .toMutableList()

        if (!config.enabled) {
            val sortedPrev = previous.sortedBy { it.id }
            val sortedNext = next.sortedBy { it.id }
            if (sortedPrev != sortedNext) {
                writeInstalledStates(sortedNext)
            }
            return@withContext
        }

        val workspacePath = config.workspaceDir?.trim().orEmpty()
        if (workspacePath.isBlank()) {
            val sortedPrev = previous.sortedBy { it.id }
            val sortedNext = next.sortedBy { it.id }
            if (sortedPrev != sortedNext) {
                writeInstalledStates(sortedNext)
            }
            return@withContext
        }

        val workspaceDir = File(workspacePath)
        if (!workspaceDir.exists() || !workspaceDir.isDirectory) {
            appendLog("[developer] workspace not found: $workspacePath")
            val sortedPrev = previous.sortedBy { it.id }
            val sortedNext = next.sortedBy { it.id }
            if (sortedPrev != sortedNext) {
                writeInstalledStates(sortedNext)
            }
            return@withContext
        }

        val previousById = previous.associateBy { it.id }
        val candidates = workspaceDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

        candidates.forEach { pluginDir ->
            val manifestFile = File(pluginDir, "manifest.json")
            if (!manifestFile.exists()) return@forEach

            val manifest = runCatching {
                json.decodeFromString(PluginManifest.serializer(), manifestFile.readText())
            }.getOrElse {
                appendLog("[developer] skip ${pluginDir.name}: manifest parse failed (${it.message})")
                return@forEach
            }

            val pluginId = normalizePluginId(manifest.id)
            if (pluginId.isBlank()) {
                appendLog("[developer] skip ${pluginDir.name}: invalid manifest.id")
                return@forEach
            }

            val previousState = previousById[pluginId]
            next.removeAll { it.id == pluginId }
            next += InstalledPluginState(
                id = pluginId,
                version = manifest.version,
                installDir = pluginDir.absolutePath,
                enabled = previousState?.enabled ?: true,
                sourceUrl = "$DEVELOPER_SOURCE_PREFIX${pluginDir.absolutePath}",
                sha256 = runCatching { sha256Directory(pluginDir) }.getOrDefault(""),
                manifestPath = "manifest.json",
                installedAt = previousState?.installedAt ?: System.currentTimeMillis()
            )
        }

        val sortedPrev = previous.sortedBy { it.id }
        val sortedNext = next.sortedBy { it.id }
        if (sortedPrev != sortedNext) {
            writeInstalledStates(sortedNext)
        }
    }

    private fun normalizeRepositorySource(source: String): String {
        val normalized = source.trim()
        return when (normalized.lowercase()) {
            "asset://plugins/index.json",
            "plugin/index.json",
            "./plugin/index.json",
            "https://raw.githubusercontent.com/wsdx233/r2droid-plugins/refs/heads/main/index.json" -> DEFAULT_REMOTE_INDEX
            else -> normalized
        }
    }

    private suspend fun installBundledPluginsIfNeeded() {
        val assets = runCatching {
            appContext.assets.list("plugins/packages")
                .orEmpty()
                .sorted()
        }.getOrElse {
            appendLog("[bundled] list failed: ${it.message}")
            emptyList()
        }
        if (assets.isEmpty()) return

        val states = readInstalledStates().toMutableList()
        var changed = false

        assets.forEach { assetName ->
            val assetPath = "plugins/packages/$assetName"

            if (assetName.endsWith(".zip", ignoreCase = true)) {
                val fallbackId = assetName.removeSuffix(".zip")
                val tempZip = File(tempDir(), "bundled-$assetName-${System.currentTimeMillis()}.zip")
                val stagedDir = File(tempDir(), "bundled-${fallbackId}-${System.currentTimeMillis()}-staged")

                runCatching {
                    downloadFile("asset://$assetPath", tempZip)
                    val digest = sha256(tempZip)

                    stagedDir.mkdirs()
                    unzipSafely(tempZip, stagedDir)

                    val manifestFile = File(stagedDir, "manifest.json")
                    if (!manifestFile.exists()) {
                        throw IllegalStateException("manifest not found in bundled plugin: $assetPath")
                    }
                    val manifest = json.decodeFromString(PluginManifest.serializer(), manifestFile.readText())
                    val pluginId = manifest.id.ifBlank { fallbackId }

                    val existing = states.firstOrNull { it.id == pluginId }
                    if (existing != null && existing.sha256.equals(digest, ignoreCase = true) && File(existing.installDir).exists()) {
                        return@runCatching
                    }

                    val targetDir = File(packagesDir(), pluginId)
                    val backupDir = File(packagesDir(), "$pluginId-backup")
                    backupDir.deleteRecursively()

                    if (targetDir.exists()) {
                        PluginRuntime.stopAllForPlugin(pluginId)
                        if (!targetDir.renameTo(backupDir)) {
                            throw IllegalStateException("failed to backup current plugin directory")
                        }
                    }

                    if (targetDir.exists()) targetDir.deleteRecursively()
                    if (!stagedDir.renameTo(targetDir)) {
                        throw IllegalStateException("failed to move bundled plugin to target")
                    }

                    backupDir.deleteRecursively()
                    states.removeAll { it.id == pluginId }
                    states += InstalledPluginState(
                        id = pluginId,
                        version = manifest.version,
                        installDir = targetDir.absolutePath,
                        enabled = existing?.enabled ?: true,
                        sourceUrl = "asset://$assetPath",
                        sha256 = digest,
                        manifestPath = "manifest.json",
                        installedAt = System.currentTimeMillis()
                    )
                    appendLog("[bundled] synced $pluginId@${manifest.version}")
                    changed = true
                }.onFailure {
                    appendLog("[bundled] sync failed ($assetPath): ${it.message}")
                }.also {
                    stagedDir.deleteRecursively()
                    tempZip.delete()
                }
                return@forEach
            }

            val assetChildren = runCatching { appContext.assets.list(assetPath).orEmpty() }
                .getOrElse {
                    appendLog("[bundled] inspect failed ($assetPath): ${it.message}")
                    emptyArray()
                }
            if (assetChildren.isEmpty()) {
                appendLog("[bundled] skip unsupported asset entry: $assetPath")
                return@forEach
            }

            val stagedDir = File(tempDir(), "bundled-${assetName}-${System.currentTimeMillis()}-staged")
            runCatching {
                stagedDir.mkdirs()
                copyAssetTree(assetPath, stagedDir)

                val manifestFile = File(stagedDir, "manifest.json")
                if (!manifestFile.exists()) {
                    appendLog("[bundled] skip missing manifest: $assetPath")
                    return@runCatching
                }

                val manifest = json.decodeFromString(PluginManifest.serializer(), manifestFile.readText())
                val pluginId = manifest.id.ifBlank { assetName }
                val digest = sha256Directory(stagedDir)

                val existing = states.firstOrNull { it.id == pluginId }
                if (existing != null && existing.sha256.equals(digest, ignoreCase = true) && File(existing.installDir).exists()) {
                    return@runCatching
                }

                val targetDir = File(packagesDir(), pluginId)
                val backupDir = File(packagesDir(), "$pluginId-backup")
                backupDir.deleteRecursively()

                if (targetDir.exists()) {
                    PluginRuntime.stopAllForPlugin(pluginId)
                    if (!targetDir.renameTo(backupDir)) {
                        throw IllegalStateException("failed to backup current plugin directory")
                    }
                }

                if (targetDir.exists()) targetDir.deleteRecursively()
                if (!stagedDir.renameTo(targetDir)) {
                    throw IllegalStateException("failed to move bundled plugin to target")
                }

                backupDir.deleteRecursively()
                states.removeAll { it.id == pluginId }
                states += InstalledPluginState(
                    id = pluginId,
                    version = manifest.version,
                    installDir = targetDir.absolutePath,
                    enabled = existing?.enabled ?: true,
                    sourceUrl = "asset://$assetPath",
                    sha256 = digest,
                    manifestPath = "manifest.json",
                    installedAt = System.currentTimeMillis()
                )
                appendLog("[bundled] synced $pluginId@${manifest.version}")
                changed = true
            }.onFailure {
                appendLog("[bundled] sync failed ($assetPath): ${it.message}")
            }.also {
                stagedDir.deleteRecursively()
            }
        }

        if (changed) {
            writeInstalledStates(states)
        }
    }

    private suspend fun runStartupEntryScripts() {
        _installed.value
            .filter { it.state.enabled }
            .forEach { plugin ->
                runInstallScriptIfPresent(plugin.state.id, reason = "startup")
            }
    }

    private suspend fun runInstallScriptIfPresent(pluginId: String, reason: String) {
        val plugin = findInstalledPlugin(pluginId) ?: return
        val scriptRef = plugin.manifest?.entry?.script?.trim().orEmpty()
        if (scriptRef.isBlank()) return

        val scriptCode = resolvePluginFile(pluginId, scriptRef)
            ?.takeIf { it.exists() && it.isFile }
            ?.let { runCatching { it.readText() }.getOrNull() }
            ?: scriptRef

        PluginRuntime.runPluginScript(pluginId, scriptCode)
            .onSuccess { result ->
                appendLog("[entry][$reason] $pluginId => ${result.lineSequence().firstOrNull().orEmpty()}")
            }
            .onFailure { e ->
                appendLog("[entry][$reason] $pluginId failed: ${e.message}")
            }
    }

    private fun writeRepositorySources(sources: List<String>) {
        repositoriesFile().writeText(json.encodeToString(ListSerializer(String.serializer()), sources))
    }

    private fun reloadInstalledFromDisk() {
        val installedStates = readInstalledStates()
        val installedPlugins = installedStates.mapNotNull { state ->
            val dir = File(state.installDir)
            if (!dir.exists()) {
                appendLog("[state] missing plugin dir: ${state.installDir}")
                return@mapNotNull null
            }
            val manifestFile = File(dir, state.manifestPath)
            val manifest = runCatching {
                json.decodeFromString(PluginManifest.serializer(), manifestFile.readText())
            }.getOrElse {
                appendLog("[state] manifest parse failed (${state.id}): ${it.message}")
                null
            }
            InstalledPlugin(state = state, manifest = manifest)
        }.sortedBy { it.state.id }

        _installed.value = installedPlugins
        _projectTabs.value = installedPlugins
            .filter { it.state.enabled }
            .flatMap { plugin ->
                val manifest = plugin.manifest ?: return@flatMap emptyList()
                manifest.projectTabs.map { tab ->
                    PluginProjectTabDescriptor(
                        pluginId = plugin.state.id,
                        pluginName = manifest.name,
                        tab = tab
                    )
                }
            }
        _screenTabs.value = installedPlugins
            .filter { it.state.enabled }
            .flatMap { plugin ->
                val manifest = plugin.manifest ?: return@flatMap emptyList()
                manifest.tabs.map { tab ->
                    PluginScreenTabDescriptor(
                        pluginId = plugin.state.id,
                        pluginName = manifest.name,
                        target = tab.target,
                        tab = tab
                    )
                }
            }
    }

    private fun readInstalledStates(): List<InstalledPluginState> {
        val file = installedStateFile()
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(InstalledPluginState.serializer()), file.readText())
        }.getOrElse {
            appendLog("[state] read failed: ${it.message}")
            emptyList()
        }
    }

    private fun writeInstalledStates(states: List<InstalledPluginState>) {
        installedStateFile().writeText(json.encodeToString(ListSerializer(InstalledPluginState.serializer()), states))
    }

    private fun loadIndexFromSource(source: String): PluginIndex {
        return when {
            source.startsWith("asset://") -> {
                val path = source.removePrefix("asset://")
                appContext.assets.open(path).bufferedReader().use { reader ->
                    json.decodeFromString(PluginIndex.serializer(), reader.readText())
                }
            }

            source.startsWith("file://") -> {
                val path = source.removePrefix("file://")
                json.decodeFromString(PluginIndex.serializer(), File(path).readText())
            }

            source.startsWith("http://") || source.startsWith("https://") -> {
                val text = downloadText(source)
                json.decodeFromString(PluginIndex.serializer(), text)
            }

            else -> {
                json.decodeFromString(PluginIndex.serializer(), File(source).readText())
            }
        }
    }

    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split('.', '-', '_')
        val pb = b.split('.', '-', '_')
        val max = maxOf(pa.size, pb.size)
        repeat(max) { idx ->
            val xa = pa.getOrNull(idx)?.toIntOrNull() ?: 0
            val xb = pb.getOrNull(idx)?.toIntOrNull() ?: 0
            if (xa != xb) return xa.compareTo(xb)
        }
        return 0
    }

    private fun downloadText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP $code")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFile(url: String, target: File, onProgress: (Float) -> Unit = {}) {
        when {
            url.startsWith("asset://") -> {
                val path = url.removePrefix("asset://")
                target.parentFile?.mkdirs()
                appContext.assets.open(path).use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
                onProgress(1f)
            }

            url.startsWith("file://") -> {
                val path = url.removePrefix("file://")
                val source = File(path)
                require(source.exists()) { "source file not found: $path" }
                target.parentFile?.mkdirs()
                val total = source.length().coerceAtLeast(1L)
                source.inputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read: Int
                        var copied = 0L
                        while (input.read(buffer).also { read = it } >= 0) {
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
                onProgress(1f)
            }

            else -> {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                try {
                    val code = conn.responseCode
                    if (code != HttpURLConnection.HTTP_OK) {
                        throw IllegalStateException("download failed: HTTP $code")
                    }
                    target.parentFile?.mkdirs()
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                    conn.inputStream.use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read: Int
                            var copied = 0L
                            while (input.read(buffer).also { read = it } >= 0) {
                                if (read == 0) continue
                                output.write(buffer, 0, read)
                                copied += read
                                if (total > 0L) {
                                    onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                    onProgress(1f)
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzipSafely(zipFile: File, outputDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val normalizedName = entry.name
                    .replace('\\', '/')
                    .trimStart('/')
                if (normalizedName.isNotBlank()) {
                    val outFile = File(outputDir, normalizedName)
                    val baseCanonical = outputDir.canonicalFile
                    val outCanonical = outFile.canonicalFile
                    val basePath = baseCanonical.path
                    val outPath = outCanonical.path
                    if (!(outPath == basePath || outPath.startsWith(basePath + File.separator))) {
                        throw SecurityException("zip slip blocked: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        target.mkdirs()
        children.forEach { child ->
            val childAssetPath = "$assetPath/$child"
            val childTarget = File(target, child)
            val nested = appContext.assets.list(childAssetPath).orEmpty()
            if (nested.isEmpty()) {
                runCatching {
                    childTarget.parentFile?.mkdirs()
                    appContext.assets.open(childAssetPath).use { input ->
                        FileOutputStream(childTarget).use { output ->
                            input.copyTo(output)
                        }
                    }
                }.onFailure {
                    childTarget.mkdirs()
                }
            } else {
                copyAssetTree(childAssetPath, childTarget)
            }
        }
    }

    private fun sha256Directory(directory: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val base = directory.canonicalFile
        val files = base.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(base).path.replace('\\', '/') }
            .toList()

        files.forEach { file ->
            val relativePath = file.relativeTo(base).path.replace('\\', '/')
            digest.update(relativePath.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            FileInputStream(file).use { input ->
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
            }
            digest.update(0.toByte())
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun defaultWebviewHtml(name: String): String {
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>$name</title>
              <style>
                body { font-family: sans-serif; padding: 16px; background: #0d1117; color: #e6edf3; }
                .card { border: 1px solid #30363d; border-radius: 8px; padding: 12px; }
              </style>
            </head>
            <body>
              <div class="card">
                <h3>$name</h3>
                <p>Developer plugin scaffold created successfully.</p>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun defaultSchemaJson(name: String): String {
        return """
            {
              "widgets": [
                {
                  "type": "text",
                  "text": "$name"
                },
                {
                  "type": "button",
                  "text": "Run ?V",
                  "action": "r2",
                  "command": "?V"
                }
              ]
            }
        """.trimIndent()
    }

    private fun normalizePluginId(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "-")
            .trim('-')
    }

    private fun appendLog(line: String) {
        Log.d(TAG, line)
        val next = _logs.value.toMutableList()
        next += "${System.currentTimeMillis()} | $line"
        if (next.size > 300) {
            _logs.value = next.takeLast(300)
        } else {
            _logs.value = next
        }
    }

    private fun updateInstallProgress(pluginId: String, progress: Float) {
        _installProgress.update { current ->
            current + (pluginId to progress.coerceIn(0f, 1f))
        }
    }

    private fun clearInstallProgress(pluginId: String) {
        _installProgress.update { current ->
            if (pluginId !in current) current else current - pluginId
        }
    }

    private fun resolvePathUnder(base: File, relativePath: String, mustExist: Boolean): File? {
        if (relativePath.isBlank()) return null
        return runCatching {
            val baseCanonical = base.canonicalFile
            val candidates = linkedSetOf(
                relativePath,
                relativePath.replace('\\', '/'),
                relativePath.replace('/', '\\')
            )

            candidates.firstNotNullOfOrNull { candidate ->
                val file = File(base, candidate)
                val fileCanonical = file.canonicalFile
                val basePath = baseCanonical.path
                val filePath = fileCanonical.path
                val inScope = filePath == basePath || filePath.startsWith(basePath + File.separator)
                if (!inScope) return@firstNotNullOfOrNull null
                if (mustExist && !file.exists()) return@firstNotNullOfOrNull null
                file
            }
        }.getOrNull()
    }

    private fun rootDir(): File = File(appContext.filesDir, "plugins")
    private fun packagesDir(): File = File(rootDir(), "packages")
    private fun tempDir(): File = File(rootDir(), "tmp")
    private fun repositoriesFile(): File = File(rootDir(), "repositories.json")
    private fun developerConfigFile(): File = File(rootDir(), "developer.json")
    private fun installedStateFile(): File = File(rootDir(), "installed.json")
}

package top.wsdx233.r2droid.feature.plugin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import top.wsdx233.r2droid.core.data.model.SavedProject
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.project.data.SavedProjectRepository
import top.wsdx233.r2droid.util.PluginProotInstaller
import top.wsdx233.r2droid.util.ProotInstaller
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.util.R2Runtime
import java.io.File
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object PluginRuntime {

    enum class Permission(val key: String) {
        FILE_READ("file.read"),
        FILE_WRITE("file.write"),
        NETWORK("network"),
        PROCESS("process"),
        R2("r2"),
        PROOT("proot"),
        TERMINAL("terminal"),
        FRIDA("frida"),
        SETTINGS_READ("settings.read"),
        SETTINGS_WRITE("settings.write"),
        SAF_PICKER("saf.picker"),
        PROJECT_READ("project.read"),
        ANDROID_CLASS("android.class")
    }

    private data class RunContext(
        val pluginId: String,
        val installDir: File,
        val permissions: Set<String>
    )

    private data class ProcessSession(
        val key: String,
        val process: Process,
        val outputQueue: LinkedBlockingQueue<String>
    )

    private const val MAX_PROCESS_OUTPUT_QUEUE = 4096
    private const val MAX_PLUGIN_LOG_CHARS = 512 * 1024
    private const val PICKER_TIMEOUT_MS = 5 * 60_000L

    private val processSessions = ConcurrentHashMap<String, ProcessSession>()
    private val appContextRef = java.util.concurrent.atomic.AtomicReference<Context?>(null)
    private val currentActivityRef = java.util.concurrent.atomic.AtomicReference<WeakReference<Activity>?>(null)
    private val pendingFilePickers = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val pendingDirPickers = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val activityLifecycleRegistered = AtomicBoolean(false)

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        appContextRef.set(appContext)
        val application = appContext as? Application ?: return
        if (activityLifecycleRegistered.compareAndSet(false, true)) {
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) {
                    currentActivityRef.set(WeakReference(activity))
                }

                override fun onActivityResumed(activity: Activity) {
                    currentActivityRef.set(WeakReference(activity))
                }

                override fun onActivityPaused(activity: Activity) {
                    clearCurrentActivityIfMatch(activity)
                }

                override fun onActivityStopped(activity: Activity) {
                    clearCurrentActivityIfMatch(activity)
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    clearCurrentActivityIfMatch(activity)
                }
            })
        }
    }

    suspend fun runPluginScript(pluginId: String, code: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val context = createContext(pluginId)
            executePluginScript(context, code)
        }
    }

    suspend fun runPluginScriptFunction(pluginId: String, script: String, functionName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val context = createContext(pluginId)
            val normalized = functionName.trim()
            require(normalized.matches(Regex("""[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*"""))) {
                "invalid function name: $functionName"
            }
            val invokeCode = buildString {
                append(script)
                append('\n')
                append(';')
                append(normalized)
                append("();")
            }
            executePluginScript(context, invokeCode)
        }
    }

    fun stopAllForPlugin(pluginId: String) {
        val prefix = "$pluginId#"
        processSessions.keys
            .filter { it.startsWith(prefix) }
            .forEach { key -> stopProcessByKey(key) }
        PluginAndroidBridge.releaseAll(pluginId)
    }

    fun getPluginDir(pluginId: String): Result<String> = runCatching {
        createContext(pluginId).installDir.absolutePath
    }

    fun getPluginDataDir(pluginId: String): Result<String> = runCatching {
        File(createContext(pluginId).installDir, "data").apply { mkdirs() }.absolutePath
    }

    fun requestNetwork(
        pluginId: String,
        method: String,
        url: String,
        body: String = "",
        headersJson: String = ""
    ): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.NETWORK)
        httpRequest(method, url, body, headersJson)
    }

    fun downloadToPluginData(pluginId: String, url: String, relativePath: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.NETWORK)
        requirePermission(context, Permission.FILE_WRITE)
        val target = resolvePluginDataFile(context, relativePath)
        target.parentFile?.mkdirs()
        downloadTo(url, target)
        target.absolutePath
    }

    fun processStart(pluginId: String, sessionId: String, commandLine: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROCESS)
        startProcess(context, sessionId, commandLine)
    }

    fun processWrite(pluginId: String, sessionId: String, input: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROCESS)
        writeProcess(context, sessionId, input)
    }

    fun processRead(pluginId: String, sessionId: String, timeoutMs: Long = 30L, maxLines: Int = 200): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROCESS)
        readProcess(context, sessionId, timeoutMs, maxLines)
    }

    fun processStop(pluginId: String, sessionId: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROCESS)
        stopProcess(context, sessionId)
    }

    fun processAlive(pluginId: String, sessionId: String): Result<Boolean> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROCESS)
        isProcessAlive(context, sessionId)
    }

    fun prootIsReady(pluginId: String, environment: String = "plugin"): Result<Boolean> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROOT)
        val appCtx = getAppContext()
        when (normalizeProotEnvironment(environment)) {
            ProotEnvironment.MAIN -> ProotInstaller.isEnvironmentReady(appCtx)
            ProotEnvironment.PLUGIN -> PluginProotInstaller.isReady(appCtx)
        }
    }

    fun prootEnsure(pluginId: String, environment: String = "plugin", rootfsAlias: String = "ubuntu"): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROOT)
        val appCtx = getAppContext()
        when (normalizeProotEnvironment(environment)) {
            ProotEnvironment.MAIN -> {
                if (!ProotInstaller.isEnvironmentReady(appCtx)) {
                    runBlocking {
                        ProotInstaller.installManual(appCtx, rootfsAlias = rootfsAlias.ifBlank { "ubuntu" }).getOrThrow()
                    }
                }
                "ok"
            }
            ProotEnvironment.PLUGIN -> {
                PluginProotInstaller.ensureReady(appCtx, rootfsAlias = rootfsAlias.ifBlank { "ubuntu" }) { line ->
                    PluginManager.appendRuntimeLog(pluginId, "[proot] $line")
                }
                "ok"
            }
        }
    }

    fun prootRun(pluginId: String, environment: String = "plugin", script: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROOT)
        runProotCommandForEnvironment(normalizeProotEnvironment(environment), script) { line ->
            PluginManager.appendRuntimeLog(pluginId, "[proot] $line")
        }
    }

    fun prootInstallApt(pluginId: String, environment: String = "plugin", packagesJson: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROOT)
        val packages = parseStringList(packagesJson)
        when (normalizeProotEnvironment(environment)) {
            ProotEnvironment.MAIN -> ProotInstaller.runProotCommand(getAppContext(), buildAptInstallScript(packages)) { line ->
                PluginManager.appendRuntimeLog(pluginId, "[proot] $line")
            }
            ProotEnvironment.PLUGIN -> PluginProotInstaller.installAptPackages(getAppContext(), packages) { line ->
                PluginManager.appendRuntimeLog(pluginId, "[proot] $line")
            }
        }
    }

    fun prootCreatePythonVenv(
        pluginId: String,
        environment: String = "plugin",
        name: String = "default",
        packagesJson: String = "[]",
        requirementsPath: String = ""
    ): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROOT)
        val packages = parseStringList(packagesJson)
        val req = requirementsPath.trim().takeIf { it.isNotBlank() }
        when (normalizeProotEnvironment(environment)) {
            ProotEnvironment.MAIN -> {
                val safe = name.trim().ifBlank { context.pluginId }.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                runProotCommandForEnvironment(ProotEnvironment.MAIN, buildPythonVenvScript(safe, packages, req)) { line ->
                    PluginManager.appendRuntimeLog(pluginId, "[proot] $line")
                }
                "/opt/r2droid-plugin-venvs/$safe"
            }
            ProotEnvironment.PLUGIN -> PluginProotInstaller.createPythonVenv(
                context = getAppContext(),
                name = name.ifBlank { context.pluginId },
                packages = packages,
                requirementsPath = req,
                logger = { line -> PluginManager.appendRuntimeLog(pluginId, "[proot] $line") }
            )
        }
    }

    fun prootR2pmInstall(pluginId: String, environment: String = "main", packageName: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROOT)
        runProotCommandForEnvironment(normalizeProotEnvironment(environment), buildR2pmInstallScript(packageName)) { line ->
            PluginManager.appendRuntimeLog(pluginId, "[proot] $line")
        }
    }

    fun prootR2(pluginId: String, environment: String = "main", r2Command: String, filePath: String = ""): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROOT)
        val fileArg = filePath.trim().takeIf { it.isNotBlank() }?.let { " ${shellEscape(it)}" }.orEmpty()
        runProotCommandForEnvironment(
            normalizeProotEnvironment(environment),
            "r2 -q -c ${shellEscape(r2Command)}$fileArg"
        ) { line ->
            PluginManager.appendRuntimeLog(pluginId, "[proot] $line")
        }
    }

    fun prootPathInfo(pluginId: String, environment: String = "plugin"): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROOT)
        when (normalizeProotEnvironment(environment)) {
            ProotEnvironment.MAIN -> mainProotPathInfo(getAppContext())
            ProotEnvironment.PLUGIN -> PluginProotInstaller.pathInfo(getAppContext())
        }
    }

    fun prootProcessStart(pluginId: String, sessionId: String, environment: String = "plugin", commandLine: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROOT)
        requirePermission(context, Permission.PROCESS)
        startProotProcess(context, sessionId, normalizeProotEnvironment(environment), commandLine)
    }

    fun runFridaScript(pluginId: String, script: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.FRIDA)
        executeFridaScript(context, script)
    }

    fun getSystemLanguageTag(): String {
        val locale = Locale.getDefault()
        return locale.toLanguageTag()
            .ifBlank { locale.toString().replace('_', '-') }
            .ifBlank { "en" }
    }

    fun resolveTerminalStartupCommand(pluginId: String, rawCommand: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        applyTerminalCommandPlaceholders(rawCommand, context.installDir)
    }

    suspend fun pickFile(pluginId: String, requestId: String): Result<String> = withContext(Dispatchers.IO) {
        val key = pickerKey(pluginId, requestId)
        val deferred = CompletableDeferred<String>()
        runCatching {
            val context = createContext(pluginId)
            requirePermission(context, Permission.SAF_PICKER)
            check(pendingFilePickers.putIfAbsent(key, deferred) == null) { "picker request already pending" }
            withTimeout(PICKER_TIMEOUT_MS) { deferred.await() }
        }.also {
            pendingFilePickers.remove(key, deferred)
        }
    }

    suspend fun pickDirectory(pluginId: String, requestId: String): Result<String> = withContext(Dispatchers.IO) {
        val key = pickerKey(pluginId, requestId)
        val deferred = CompletableDeferred<String>()
        runCatching {
            val context = createContext(pluginId)
            requirePermission(context, Permission.SAF_PICKER)
            check(pendingDirPickers.putIfAbsent(key, deferred) == null) { "picker request already pending" }
            withTimeout(PICKER_TIMEOUT_MS) { deferred.await() }
        }.also {
            pendingDirPickers.remove(key, deferred)
        }
    }

    fun cancelPendingPickers(pluginId: String) {
        val prefix = "$pluginId#"
        pendingFilePickers.entries.removeAll { (key, deferred) ->
            if (key.startsWith(prefix)) {
                deferred.completeExceptionally(IllegalStateException("file pick cancelled"))
                true
            } else false
        }
        pendingDirPickers.entries.removeAll { (key, deferred) ->
            if (key.startsWith(prefix)) {
                deferred.completeExceptionally(IllegalStateException("directory pick cancelled"))
                true
            } else false
        }
    }

    fun completeFilePicker(pluginId: String, requestId: String, absolutePath: String?): Boolean {
        val key = pickerKey(pluginId, requestId)
        val deferred = pendingFilePickers.remove(key) ?: return false
        if (absolutePath.isNullOrBlank()) {
            deferred.completeExceptionally(IllegalStateException("file pick cancelled"))
        } else {
            deferred.complete(absolutePath)
        }
        return true
    }

    fun completeDirPicker(pluginId: String, requestId: String, absolutePath: String?): Boolean {
        val key = pickerKey(pluginId, requestId)
        val deferred = pendingDirPickers.remove(key) ?: return false
        if (absolutePath.isNullOrBlank()) {
            deferred.completeExceptionally(IllegalStateException("directory pick cancelled"))
        } else {
            deferred.complete(absolutePath)
        }
        return true
    }

    fun getProjectsRootDir(pluginId: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROJECT_READ)
        val root = resolveProjectsRootDir(getAppContext())
        root.mkdirs()
        root.absolutePath
    }

    fun listProjectsInfo(pluginId: String): Result<String> = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.PROJECT_READ)
        val appCtx = getAppContext()
        val repo = SavedProjectRepository(appCtx)
        val projects = runBlocking { repo.getAllProjects() }
        projectsToJson(projects)
    }

    fun androidBridgeJavascript(hostObjectName: String, includeCurrentActivity: Boolean = false): String {
        return PluginAndroidBridge.javascriptBridge(hostObjectName, includeCurrentActivity)
    }

    fun androidImportClassPayload(pluginId: String, className: String): String = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.ANDROID_CLASS)
        PluginAndroidBridge.importClassPayload(className)
    }.getOrElse(PluginAndroidBridge::errorPayload)

    fun androidNewPayload(pluginId: String, className: String, argsJson: String = "[]"): String = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.ANDROID_CLASS)
        PluginAndroidBridge.newPayload(pluginId, className, argsJson)
    }.getOrElse(PluginAndroidBridge::errorPayload)

    fun androidCallPayload(pluginId: String, refId: String, methodName: String, argsJson: String = "[]"): String = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.ANDROID_CLASS)
        PluginAndroidBridge.callPayload(pluginId, refId, methodName, argsJson)
    }.getOrElse(PluginAndroidBridge::errorPayload)

    fun androidCallStaticPayload(pluginId: String, className: String, methodName: String, argsJson: String = "[]"): String = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.ANDROID_CLASS)
        PluginAndroidBridge.callStaticPayload(pluginId, className, methodName, argsJson)
    }.getOrElse(PluginAndroidBridge::errorPayload)

    fun androidGetStaticFieldPayload(pluginId: String, className: String, fieldName: String): String = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.ANDROID_CLASS)
        PluginAndroidBridge.getStaticFieldPayload(pluginId, className, fieldName)
    }.getOrElse(PluginAndroidBridge::errorPayload)

    fun androidStartActivityPayload(pluginId: String, refId: String): String = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.ANDROID_CLASS)
        PluginAndroidBridge.startActivityPayload(getAppContext(), pluginId, refId)
    }.getOrElse(PluginAndroidBridge::errorPayload)

    fun androidGetLaunchIntentForPackagePayload(pluginId: String, packageName: String): String = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.ANDROID_CLASS)
        PluginAndroidBridge.getLaunchIntentForPackagePayload(getAppContext(), pluginId, packageName)
    }.getOrElse(PluginAndroidBridge::errorPayload)

    fun androidGetCurrentActivityPayload(pluginId: String): String = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.ANDROID_CLASS)
        PluginAndroidBridge.getCurrentActivityPayload(pluginId, currentActivityRef.get()?.get())
    }.getOrElse(PluginAndroidBridge::errorPayload)

    fun androidReleasePayload(pluginId: String, refId: String): String = runCatching {
        val context = createContext(pluginId)
        requirePermission(context, Permission.ANDROID_CLASS)
        PluginAndroidBridge.releasePayload(pluginId, refId)
    }.getOrElse(PluginAndroidBridge::errorPayload)

    private fun createContext(pluginId: String): RunContext {
        val installed = PluginManager.findInstalledPlugin(pluginId)
            ?: error("plugin not installed: $pluginId")
        val manifest = installed.manifest ?: error("manifest unavailable for $pluginId")
        return RunContext(
            pluginId = pluginId,
            installDir = File(installed.state.installDir),
            permissions = manifest.permissions.toSet()
        )
    }

    private fun requirePermission(context: RunContext, permission: Permission) {
        check(context.permissions.contains(permission.key)) {
            "permission denied: ${permission.key}"
        }
    }

    private fun resolvePluginDataFile(context: RunContext, relativePath: String): File {
        val dataRoot = File(context.installDir, "data").apply { mkdirs() }
        val file = File(dataRoot, relativePath)
        val basePath = dataRoot.canonicalPath
        val filePath = file.canonicalPath
        check(filePath.startsWith(basePath)) { "invalid path" }
        return file
    }

    private fun executeFridaScript(context: RunContext, script: String): String {
        check(R2PipeManager.isR2FridaSession) { "Frida script execution requires an active r2frida session" }
        val scriptDir = File(context.installDir, "data").apply { mkdirs() }
        val scriptFile = File(scriptDir, "frida_plugin_${System.currentTimeMillis()}.js")
        return try {
            scriptFile.writeText(script)
            runBlocking {
                R2PipeManager.execute(":. ${scriptFile.absolutePath}", markDirty = false)
            }.getOrThrow()
        } finally {
            scriptFile.delete()
        }
    }

    private fun executePluginScript(context: RunContext, code: String): String {
        val logs = StringBuilder()
        var logsTruncated = false
        fun appendLog(line: String) {
            if (logsTruncated) return
            val extra = line.length + if (logs.isNotEmpty()) 1 else 0
            if (logs.length + extra > MAX_PLUGIN_LOG_CHARS) {
                if (logs.isNotEmpty()) logs.append('\n')
                logs.append("[plugin log truncated]")
                logsTruncated = true
                return
            }
            if (logs.isNotEmpty()) logs.append('\n')
            logs.append(line)
        }
        val quickJs = QuickJs.create(Dispatchers.IO)
        quickJs.use { qjs ->
            qjs.define("console") {
                function("log") { args ->
                    appendLog(args.joinToString(" "))
                    Unit
                }
                function("error") { args ->
                    appendLog("ERROR: " + args.joinToString(" "))
                    Unit
                }
            }

            qjs.define("host") {
                function<String, String>("r2") { command ->
                    requirePermission(context, Permission.R2)
                    val result = runBlocking { R2PipeManager.execute(command) }
                    result.getOrElse { "Error: ${it.message}" }
                }

                function<String, String>("frida") { script ->
                    requirePermission(context, Permission.FRIDA)
                    executeFridaScript(context, script)
                }

                function<String, String>("readText") { relativePath ->
                    requirePermission(context, Permission.FILE_READ)
                    val file = resolvePluginDataFile(context, relativePath)
                    if (!file.exists()) return@function ""
                    file.readText()
                }

                function<String>("writeText") { args ->
                    requirePermission(context, Permission.FILE_WRITE)
                    val relativePath = args.getOrNull(0)?.toString() ?: ""
                    val content = args.getOrNull(1)?.toString() ?: ""
                    val file = resolvePluginDataFile(context, relativePath)
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    "ok"
                }

                function<String>("pluginDir") { _ ->
                    context.installDir.absolutePath
                }

                function<String>("dataDir") { _ ->
                    File(context.installDir, "data").apply { mkdirs() }.absolutePath
                }

                function<String>("systemLanguage") { _ ->
                    getSystemLanguageTag()
                }

                function<String, String>("httpGet") { url ->
                    requirePermission(context, Permission.NETWORK)
                    httpRequest("GET", url, "", "")
                }

                function<String>("httpRequest") { args ->
                    requirePermission(context, Permission.NETWORK)
                    val method = args.getOrNull(0)?.toString()?.ifBlank { "GET" } ?: "GET"
                    val url = args.getOrNull(1)?.toString() ?: ""
                    val body = args.getOrNull(2)?.toString() ?: ""
                    val headersJson = args.getOrNull(3)?.toString() ?: ""
                    httpRequest(method, url, body, headersJson)
                }

                function<String>("download") { args ->
                    requirePermission(context, Permission.NETWORK)
                    requirePermission(context, Permission.FILE_WRITE)
                    val url = args.getOrNull(0)?.toString() ?: ""
                    val relativePath = args.getOrNull(1)?.toString() ?: ""
                    val target = resolvePluginDataFile(context, relativePath)
                    target.parentFile?.mkdirs()
                    downloadTo(url, target)
                    target.absolutePath
                }

                function<String>("procStart") { args ->
                    requirePermission(context, Permission.PROCESS)
                    val sessionId = args.getOrNull(0)?.toString()?.ifBlank { "default" } ?: "default"
                    val commandLine = args.getOrNull(1)?.toString() ?: ""
                    startProcess(context, sessionId, commandLine)
                }

                function<String>("procWrite") { args ->
                    requirePermission(context, Permission.PROCESS)
                    val sessionId = args.getOrNull(0)?.toString()?.ifBlank { "default" } ?: "default"
                    val input = args.getOrNull(1)?.toString() ?: ""
                    writeProcess(context, sessionId, input)
                }

                function<String>("procRead") { args ->
                    requirePermission(context, Permission.PROCESS)
                    val sessionId = args.getOrNull(0)?.toString()?.ifBlank { "default" } ?: "default"
                    val timeoutMs = args.getOrNull(1)?.toString()?.toLongOrNull() ?: 30L
                    val maxLines = args.getOrNull(2)?.toString()?.toIntOrNull() ?: 200
                    readProcess(context, sessionId, timeoutMs, maxLines)
                }

                function<String, String>("procStop") { sessionId ->
                    requirePermission(context, Permission.PROCESS)
                    stopProcess(context, sessionId)
                }

                function<String, String>("procAlive") { sessionId ->
                    requirePermission(context, Permission.PROCESS)
                    if (isProcessAlive(context, sessionId)) "true" else "false"
                }

                function<String, String>("prootIsReady") { environment ->
                    prootIsReady(context.pluginId, environment).map { it.toString() }.getOrElse { "Error: ${it.message}" }
                }

                function<String>("prootEnsure") { args ->
                    val environment = args.getOrNull(0)?.toString() ?: "plugin"
                    val rootfsAlias = args.getOrNull(1)?.toString() ?: "ubuntu"
                    prootEnsure(context.pluginId, environment, rootfsAlias).getOrElse { "Error: ${it.message}" }
                }

                function<String>("prootRun") { args ->
                    val environment = args.getOrNull(0)?.toString() ?: "plugin"
                    val script = args.getOrNull(1)?.toString() ?: ""
                    prootRun(context.pluginId, environment, script).getOrElse { "Error: ${it.message}" }
                }

                function<String>("prootApt") { args ->
                    val environment = args.getOrNull(0)?.toString() ?: "plugin"
                    val packagesJson = args.getOrNull(1)?.toString() ?: "[]"
                    prootInstallApt(context.pluginId, environment, packagesJson).getOrElse { "Error: ${it.message}" }
                }

                function<String>("prootVenv") { args ->
                    val environment = args.getOrNull(0)?.toString() ?: "plugin"
                    val name = args.getOrNull(1)?.toString() ?: context.pluginId
                    val packagesJson = args.getOrNull(2)?.toString() ?: "[]"
                    val requirements = args.getOrNull(3)?.toString() ?: ""
                    prootCreatePythonVenv(context.pluginId, environment, name, packagesJson, requirements).getOrElse { "Error: ${it.message}" }
                }

                function<String>("prootR2pmInstall") { args ->
                    val environment = args.getOrNull(0)?.toString() ?: "main"
                    val packageName = args.getOrNull(1)?.toString() ?: ""
                    prootR2pmInstall(context.pluginId, environment, packageName).getOrElse { "Error: ${it.message}" }
                }

                function<String>("prootR2") { args ->
                    val environment = args.getOrNull(0)?.toString() ?: "main"
                    val command = args.getOrNull(1)?.toString() ?: ""
                    val filePath = args.getOrNull(2)?.toString() ?: ""
                    prootR2(context.pluginId, environment, command, filePath).getOrElse { "Error: ${it.message}" }
                }

                function<String, String>("prootPathInfo") { environment ->
                    prootPathInfo(context.pluginId, environment).getOrElse { "Error: ${it.message}" }
                }

                function<String>("prootProcStart") { args ->
                    val sessionId = args.getOrNull(0)?.toString()?.ifBlank { "default" } ?: "default"
                    val environment = args.getOrNull(1)?.toString() ?: "plugin"
                    val commandLine = args.getOrNull(2)?.toString() ?: ""
                    prootProcessStart(context.pluginId, sessionId, environment, commandLine).getOrElse { "Error: ${it.message}" }
                }

                function<String>("projectsRootDir") { _ ->
                    getProjectsRootDir(context.pluginId).getOrElse { "Error: ${it.message}" }
                }

                function<String>("projectsInfo") { _ ->
                    listProjectsInfo(context.pluginId).getOrElse { "Error: ${it.message}" }
                }

                function<String, String>("androidImportClass") { className ->
                    androidImportClassPayload(context.pluginId, className)
                }

                function<String>("androidNew") { args ->
                    val className = args.getOrNull(0)?.toString() ?: ""
                    val argsJson = args.getOrNull(1)?.toString() ?: "[]"
                    androidNewPayload(context.pluginId, className, argsJson)
                }

                function<String>("androidCall") { args ->
                    val refId = args.getOrNull(0)?.toString() ?: ""
                    val methodName = args.getOrNull(1)?.toString() ?: ""
                    val argsJson = args.getOrNull(2)?.toString() ?: "[]"
                    androidCallPayload(context.pluginId, refId, methodName, argsJson)
                }

                function<String>("androidCallStatic") { args ->
                    val className = args.getOrNull(0)?.toString() ?: ""
                    val methodName = args.getOrNull(1)?.toString() ?: ""
                    val argsJson = args.getOrNull(2)?.toString() ?: "[]"
                    androidCallStaticPayload(context.pluginId, className, methodName, argsJson)
                }

                function<String>("androidGetStaticField") { args ->
                    val className = args.getOrNull(0)?.toString() ?: ""
                    val fieldName = args.getOrNull(1)?.toString() ?: ""
                    androidGetStaticFieldPayload(context.pluginId, className, fieldName)
                }

                function<String, String>("androidStartActivity") { refId ->
                    androidStartActivityPayload(context.pluginId, refId)
                }

                function<String, String>("androidGetLaunchIntentForPackage") { packageName ->
                    androidGetLaunchIntentForPackagePayload(context.pluginId, packageName)
                }

                function<String>("androidGetCurrentActivity") { _ ->
                    androidGetCurrentActivityPayload(context.pluginId)
                }

                function<String, String>("androidRelease") { refId ->
                    androidReleasePayload(context.pluginId, refId)
                }

                function("emit") { args ->
                    val event = args.firstOrNull()?.toString() ?: "event"
                    val payload = args.drop(1).joinToString(" ")
                    appendLog("[emit] $event $payload")
                    Unit
                }
            }

            runBlocking {
                qjs.evaluate<Any?>(
                    PluginAndroidBridge.javascriptBridge(
                        hostObjectName = "host",
                        includeCurrentActivity = context.permissions.contains(Permission.ANDROID_CLASS.key)
                    )
                )
            }

            val result = runBlocking { qjs.evaluate<Any?>(code) }?.toString().orEmpty()
            val logText = logs.toString()
            return when {
                logText.isNotBlank() && result.isNotBlank() -> "$logText\n$result"
                logText.isNotBlank() -> logText
                result.isNotBlank() -> result
                else -> "(plugin script executed)"
            }
        }
    }

    private fun startProcess(context: RunContext, sessionId: String, commandLine: String): String {
        val resolvedCommandLine = applyTerminalCommandPlaceholders(commandLine, context.installDir)
        val parts = splitCommandLine(resolvedCommandLine)
        require(parts.isNotEmpty()) { "empty command" }

        val key = processKey(context.pluginId, sessionId)
        stopProcessByKey(key)

        val dataDir = File(context.installDir, "data").apply { mkdirs() }
        val tmpDir = File(dataDir, "tmp").apply { mkdirs() }

        val processBuilder = ProcessBuilder(parts)
            .directory(context.installDir)
            .redirectErrorStream(true)

        val env = processBuilder.environment()
        // 对齐 r2pipe 关键运行环境，保证插件进程可找到 radare2 依赖库与配置
        val appFilesDir = appContextRef.get()?.filesDir ?: context.installDir
        configureProcessEnvironment(
            env = env,
            appFilesDir = appFilesDir,
            installDir = context.installDir,
            dataDir = dataDir,
            tmpDir = tmpDir
        )

        val process = processBuilder.start()
        registerProcessReader(context.pluginId, sessionId, key, process)
        return "ok"
    }

    internal fun configureProcessEnvironment(
        env: MutableMap<String, String>,
        appFilesDir: File,
        installDir: File,
        dataDir: File,
        tmpDir: File
    ) {
        val pathSep = File.pathSeparator
        val r2PrefixDir = File(appFilesDir, "radare2")
        val r2LibDir = File(r2PrefixDir, "lib")
        val libsDir = File(appFilesDir, "libs")
        val existingLd = env["LD_LIBRARY_PATH"]?.takeIf { it.isNotBlank() }
        val myLd = "${r2LibDir.absolutePath}:${libsDir.absolutePath}"
        env["LD_LIBRARY_PATH"] = if (existingLd != null) "$myLd:$existingLd" else myLd
        // Resolve bundled radare2 data/calling-convention files instead of build-time prefix.
        env["R2_PREFIX"] = r2PrefixDir.absolutePath

        val xdgDataHome = File(appFilesDir, "r2work")
        val xdgCacheHome = File(appFilesDir, ".cache")
        xdgDataHome.mkdirs()
        xdgCacheHome.mkdirs()
        env["XDG_DATA_HOME"] = xdgDataHome.absolutePath
        env["XDG_CACHE_HOME"] = xdgCacheHome.absolutePath

        val r2BinDir = File(r2PrefixDir, "bin")
        // Align HOME with R2Runtime so bundled radare2/r2mcp loads radare2/bin/.radare2rc
        // instead of inheriting an unwritable system HOME.
        env["HOME"] = if (r2BinDir.exists()) r2BinDir.absolutePath else dataDir.absolutePath
        env["PWD"] = installDir.absolutePath
        env["TMPDIR"] = tmpDir.absolutePath
        env["TMP"] = tmpDir.absolutePath
        env["TEMP"] = tmpDir.absolutePath
        env["TERM"] = env["TERM"].takeUnless { it.isNullOrBlank() } ?: "dumb"
        env["R2_NOCOLOR"] = env["R2_NOCOLOR"].takeUnless { it.isNullOrBlank() } ?: "1"

        val systemPath = env["PATH"]?.takeIf { it.isNotBlank() }
            ?: "/system/bin:/system/xbin"
        val extraPath = listOf(
            installDir.absolutePath,
            dataDir.absolutePath,
            r2BinDir.absolutePath
        ).joinToString(pathSep)
        env["PATH"] = "$extraPath$pathSep$systemPath"
    }

    private fun writeProcess(context: RunContext, sessionId: String, input: String): String {
        val key = processKey(context.pluginId, sessionId)
        val session = processSessions[key] ?: return "process not started"
        if (!session.process.isAlive) return "process exited"
        session.process.outputStream.write(input.toByteArray())
        session.process.outputStream.flush()
        return "ok"
    }

    private fun readProcess(context: RunContext, sessionId: String, timeoutMs: Long, maxLines: Int): String {
        val key = processKey(context.pluginId, sessionId)
        val session = processSessions[key] ?: return ""
        val lines = mutableListOf<String>()

        if (lines.size < maxLines) {
            val first = if (timeoutMs > 0) {
                session.outputQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
            } else {
                session.outputQueue.poll()
            }
            if (first != null) lines += first
        }

        while (lines.size < maxLines) {
            val next = session.outputQueue.poll() ?: break
            lines += next
        }
        if (!session.process.isAlive && session.outputQueue.isEmpty()) {
            processSessions.remove(key, session)
        }
        return lines.joinToString("\n")
    }

    private fun stopProcess(context: RunContext, sessionId: String): String {
        val key = processKey(context.pluginId, sessionId)
        return if (stopProcessByKey(key)) "ok" else "not running"
    }

    private fun isProcessAlive(context: RunContext, sessionId: String): Boolean {
        val key = processKey(context.pluginId, sessionId)
        return processSessions[key]?.process?.isAlive == true
    }

    private fun startProotProcess(
        context: RunContext,
        sessionId: String,
        environment: ProotEnvironment,
        commandLine: String
    ): String {
        val resolved = applyTerminalCommandPlaceholders(commandLine, context.installDir)
        require(resolved.isNotBlank()) { "empty command" }

        val key = processKey(context.pluginId, sessionId)
        stopProcessByKey(key)

        val spec = when (environment) {
            ProotEnvironment.MAIN -> R2Runtime.buildProotShellSpec(
                context = getAppContext(),
                shellCommand = resolved,
                term = "dumb",
                extraBindPaths = setOf(context.installDir.absolutePath, File(context.installDir, "data").absolutePath)
            )
            ProotEnvironment.PLUGIN -> PluginProotInstaller.buildShellSpec(
                context = getAppContext(),
                shellCommand = resolved,
                term = "dumb",
                extraBindPaths = setOf(context.installDir.absolutePath, File(context.installDir, "data").absolutePath)
            )
        }
        val processBuilder = ProcessBuilder(spec.command)
            .directory(spec.workingDirectory)
            .redirectErrorStream(true)
        processBuilder.environment().putAll(spec.environment)
        val process = processBuilder.start()
        registerProcessReader(context.pluginId, sessionId, key, process)
        return "ok"
    }

    private fun registerProcessReader(pluginId: String, sessionId: String, key: String, process: Process) {
        val queue = LinkedBlockingQueue<String>(MAX_PROCESS_OUTPUT_QUEUE)
        fun enqueueBounded(line: String) {
            while (!queue.offer(line)) {
                queue.poll()
            }
        }
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { enqueueBounded(it) }
                }
            }
            enqueueBounded("[[exit:${process.exitValueOrMinusOne()}]]")
        }
        reader.isDaemon = true
        reader.name = "PluginProc-$pluginId-$sessionId"
        reader.start()

        processSessions[key] = ProcessSession(
            key = key,
            process = process,
            outputQueue = queue
        )
    }

    private fun stopProcessByKey(key: String): Boolean {
        val session = processSessions.remove(key) ?: return false
        runCatching { session.process.outputStream.close() }
        runCatching { session.process.inputStream.close() }
        runCatching { session.process.errorStream.close() }
        runCatching {
            if (session.process.isAlive) {
                session.process.destroy()
                Thread.sleep(80)
                if (session.process.isAlive && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    session.process.destroyForcibly()
                }
            }
        }
        return true
    }

    private fun processKey(pluginId: String, sessionId: String): String = "$pluginId#$sessionId"

    private fun pickerKey(pluginId: String, requestId: String): String {
        val normalized = requestId.trim().ifBlank { "default" }
        return "$pluginId#$normalized"
    }

    private fun applyTerminalCommandPlaceholders(commandLine: String, installDir: File): String {
        val pluginDir = installDir.absolutePath
        return commandLine
            .replace("{{plugin_dir}}", pluginDir, ignoreCase = true)
            .replace("${'$'}{plugin_dir}", pluginDir, ignoreCase = true)
            .replace("%PLUGIN_DIR%", pluginDir, ignoreCase = true)
            .replace("${'$'}PLUGIN_DIR", pluginDir)
    }

    private fun splitCommandLine(commandLine: String): List<String> {
        val regex = Regex("""[^"]\S*|".+?"|'[^']*'""")
        return regex.findAll(commandLine)
            .map { it.value.trim().trim('"').trim('\'') }
            .filter { it.isNotBlank() }
            .toList()
    }

    private enum class ProotEnvironment { MAIN, PLUGIN }

    private fun normalizeProotEnvironment(environment: String): ProotEnvironment {
        return when (environment.trim().lowercase()) {
            "main", "r2", "radare2" -> ProotEnvironment.MAIN
            else -> ProotEnvironment.PLUGIN
        }
    }

    private fun runProotCommandForEnvironment(
        environment: ProotEnvironment,
        script: String,
        logger: ((String) -> Unit)? = null
    ): String {
        val appCtx = getAppContext()
        return when (environment) {
            ProotEnvironment.MAIN -> {
                check(ProotInstaller.isEnvironmentReady(appCtx)) { "Main proot environment is not ready." }
                ProotInstaller.runProotCommand(appCtx, script, logger)
            }
            ProotEnvironment.PLUGIN -> {
                check(PluginProotInstaller.isReady(appCtx)) { "Plugin proot environment is not ready. Call prootEnsure('plugin') first." }
                PluginProotInstaller.runCommand(appCtx, script, logger = logger)
            }
        }
    }

    private fun buildAptInstallScript(packages: List<String>): String {
        val joined = packages.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            .joinToString(" ") { shellEscape(it) }
        if (joined.isBlank()) return "true"
        return """
            set -e
            export DEBIAN_FRONTEND=noninteractive
            apt-get update
            apt-get install -y --no-install-recommends $joined
        """.trimIndent()
    }

    private fun buildPythonVenvScript(name: String, packages: List<String>, requirementsPath: String?): String {
        val packagesArgs = packages.map { it.trim() }.filter { it.isNotBlank() }
            .joinToString(" ") { shellEscape(it) }
        val reqArg = requirementsPath?.trim()?.takeIf { it.isNotBlank() }?.let { " -r ${shellEscape(it)}" }.orEmpty()
        return """
            set -e
            export DEBIAN_FRONTEND=noninteractive
            apt-get update
            apt-get install -y --no-install-recommends python3 python3-venv python3-pip ca-certificates
            mkdir -p /opt/r2droid-plugin-venvs
            python3 -m venv /opt/r2droid-plugin-venvs/$name
            /opt/r2droid-plugin-venvs/$name/bin/python -m pip install --upgrade pip setuptools wheel
            if [ -n ${shellEscape(packagesArgs + reqArg)} ]; then
                /opt/r2droid-plugin-venvs/$name/bin/pip install $packagesArgs$reqArg
            fi
        """.trimIndent()
    }

    private fun buildR2pmInstallScript(packageName: String): String {
        val safePackage = packageName.trim()
        require(safePackage.matches(Regex("[A-Za-z0-9_.:+@/-]+"))) { "invalid r2pm package: $packageName" }
        return """
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
            "${'$'}R2PM_BIN" -ci $safePackage
        """.trimIndent()
    }

    private fun mainProotPathInfo(context: Context): String {
        return "{" +
            "\"runtimeDir\":\"${escapeJson(ProotInstaller.getRuntimeDir(context).absolutePath)}\"," +
            "\"rootfsDir\":\"${escapeJson(ProotInstaller.getRootfsDir(context).absolutePath)}\"," +
            "\"tmpDir\":\"${escapeJson(ProotInstaller.getHostTmpDir(context).absolutePath)}\"," +
            "\"venvRoot\":\"/opt/r2droid-plugin-venvs\"" +
            "}"
    }

    private fun parseStringList(value: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return emptyList()
        if (!trimmed.startsWith("[")) return trimmed.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotBlank() }
        return runCatching {
            Json.parseToJsonElement(trimmed)
                .let { element -> element as? kotlinx.serialization.json.JsonArray }
                ?.mapNotNull { item -> item.toString().trim().trim('"').takeIf { it.isNotBlank() } }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun shellEscape(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun httpRequest(method: String, url: String, body: String, headersJson: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method.uppercase()
            connectTimeout = 15000
            readTimeout = 30000
            doInput = true
        }

        val headers = parseHeaders(headersJson)
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

        if (body.isNotEmpty() && method.uppercase() !in setOf("GET", "HEAD")) {
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { writer ->
                writer.write(body)
                writer.flush()
            }
        }

        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadTo(url: String, target: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("HTTP $code")
            }
            conn.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun getAppContext(): Context {
        return appContextRef.get() ?: error("PluginRuntime not initialized")
    }

    private fun clearCurrentActivityIfMatch(activity: Activity) {
        val current = currentActivityRef.get()?.get() ?: return
        if (current === activity) {
            currentActivityRef.set(null)
        }
    }

    private fun resolveProjectsRootDir(context: Context): File {
        val customHome = SettingsManager.projectHome
        val base = if (!customHome.isNullOrBlank()) {
            val dir = File(customHome, "projects")
            if (dir.exists() || dir.mkdirs()) dir else File(context.filesDir, "projects")
        } else {
            File(context.filesDir, "projects")
        }
        if (!base.exists()) base.mkdirs()
        return base
    }

    private fun projectsToJson(projects: List<SavedProject>): String {
        val escaped = projects.joinToString(separator = ",", prefix = "[", postfix = "]") { p ->
            "{" +
                "\"id\":\"${escapeJson(p.id)}\"," +
                "\"name\":\"${escapeJson(p.name)}\"," +
                "\"binaryPath\":\"${escapeJson(p.binaryPath)}\"," +
                "\"scriptPath\":\"${escapeJson(p.scriptPath)}\"," +
                "\"createdAt\":${p.createdAt}," +
                "\"lastModified\":${p.lastModified}," +
                "\"fileSize\":${p.fileSize}," +
                "\"archType\":\"${escapeJson(p.archType)}\"," +
                "\"binType\":\"${escapeJson(p.binType)}\"," +
                "\"analysisLevel\":\"${escapeJson(p.analysisLevel)}\"" +
            "}"
        }
        return escaped
    }

    private fun escapeJson(value: String): String {
        return buildString(value.length + 8) {
            value.forEach { ch ->
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun parseHeaders(headersJson: String): Map<String, String> {
        if (headersJson.isBlank()) return emptyMap()
        return runCatching {
            Json.parseToJsonElement(headersJson)
                .jsonObject
                .mapValues { it.value.toString().trim('"') }
        }.getOrDefault(emptyMap())
    }

    private fun Process.exitValueOrMinusOne(): Int {
        return runCatching { exitValue() }.getOrDefault(-1)
    }
}

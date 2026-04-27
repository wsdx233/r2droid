package top.wsdx233.r2droid.feature.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import top.wsdx233.r2droid.activity.TerminalActivity
import top.wsdx233.r2droid.core.ui.dialogs.ProotInstallDialog
import top.wsdx233.r2droid.util.DocumentsUiOpenDocumentTreeContract
import top.wsdx233.r2droid.util.PluginProotInstaller
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.util.UriUtils
import java.io.File
import java.io.FileOutputStream

@Composable
fun PluginPageRenderer(
    pluginId: String,
    page: PluginPage,
    modifier: Modifier = Modifier,
    onJumpToDisasm: ((Long) -> Unit)? = null
) {
    when (page.type.lowercase()) {
        "schema" -> SchemaPluginPage(pluginId = pluginId, path = page.path, modifier = modifier)
        "native" -> SchemaPluginPage(pluginId = pluginId, path = page.path, modifier = modifier)
        "terminal" -> TerminalCommandPluginPage(pluginId = pluginId, modifier = modifier)
        else -> WebViewPluginPage(
            pluginId = pluginId,
            path = page.path,
            modifier = modifier,
            onJumpToDisasm = onJumpToDisasm
        )
    }
}

@Composable
private fun TerminalCommandPluginPage(
    pluginId: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val plugin = remember(pluginId) { PluginManager.findInstalledPlugin(pluginId) }
    val terminal = plugin?.manifest?.entry?.terminal

    if (terminal == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Terminal entry not configured", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = terminal.title ?: plugin?.manifest?.name ?: pluginId,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = terminal.command,
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(
            onClick = {
                val startupCommand = PluginRuntime
                    .resolveTerminalStartupCommand(pluginId, terminal.command)
                    .getOrElse { terminal.command }
                val intent = Intent(context, TerminalActivity::class.java)
                    .putExtra("startup_command", startupCommand)
                context.startActivity(intent)
            }
        ) {
            Text("Open Advanced Terminal")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewPluginPage(
    pluginId: String,
    path: String,
    modifier: Modifier = Modifier,
    onJumpToDisasm: ((Long) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pageFile = PluginManager.resolvePluginFile(pluginId, path)

    if (pageFile == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Plugin page not found: $path", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    var pendingFileRequestId by remember(pluginId) { mutableStateOf<String?>(null) }
    var pendingDirRequestId by remember(pluginId) { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val requestId = pendingFileRequestId
        if (requestId != null) {
            val pathResult = uri?.let { resolveUriToAbsolutePath(context, it) }
            PluginRuntime.completeFilePicker(pluginId, requestId, pathResult)
            pendingFileRequestId = null
        }
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(DocumentsUiOpenDocumentTreeContract()) { uri ->
        val requestId = pendingDirRequestId
        if (requestId != null) {
            val pathResult = uri?.let { UriUtils.getTreePath(context, it) }
            PluginRuntime.completeDirPicker(pluginId, requestId, pathResult)
            pendingDirRequestId = null
        }
    }

    val scope = rememberCoroutineScope()
    var showPluginProotDialog by remember(pluginId) { mutableStateOf(false) }
    val bridge = remember(pluginId, context, onJumpToDisasm) {
        PluginWebBridge(
            context = context,
            pluginId = pluginId,
            onPickFileRequest = { requestId ->
                pendingFileRequestId = requestId
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            onPickDirectoryRequest = { requestId ->
                pendingDirRequestId = requestId
                dirPickerLauncher.launch(null)
            },
            onPrepareProotRequest = { force ->
                showPluginProotDialog = true
                scope.launch { PluginManager.prepareProotForPlugin(pluginId, force = force) }
            },
            onJumpToDisasmRequest = onJumpToDisasm
        )
    }
    val includeCurrentActivity = remember(pluginId) {
        PluginManager.getPluginPermissions(pluginId).contains(PluginRuntime.Permission.ANDROID_CLASS.key)
    }
    val plugin = remember(pluginId) { PluginManager.findInstalledPlugin(pluginId) }
    val prootConfig = plugin?.manifest?.proot
    val pluginProotState by PluginProotInstaller.state.collectAsState()

    LaunchedEffect(pluginId, prootConfig) {
        val config = prootConfig ?: return@LaunchedEffect
        val environment = config.environment.trim().lowercase().ifBlank { "plugin" }
        if (config.enabled && environment !in setOf("main", "r2", "radare2") && !PluginManager.isProotPreparedForPlugin(pluginId)) {
            showPluginProotDialog = true
            PluginManager.prepareProotForPlugin(pluginId, force = false)
        }
    }

    val targetUrl = remember(pageFile.absolutePath) { pageFile.toURI().toString() }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(
                                PluginRuntime.androidBridgeJavascript(
                                    hostObjectName = "R2PluginHost",
                                    includeCurrentActivity = includeCurrentActivity
                                ),
                                null
                            )
                        }
                    }
                    webChromeClient = WebChromeClient()
                    addJavascriptInterface(bridge, "R2PluginHost")
                    loadUrl(targetUrl)
                }
            },
            update = { webView ->
                if (webView.url != targetUrl) {
                    webView.loadUrl(targetUrl)
                }
            }
        )

        if (showPluginProotDialog) {
            ProotInstallDialog(
                state = pluginProotState,
                onClose = {
                    showPluginProotDialog = false
                    PluginProotInstaller.resetState()
                }
            )
        }
    }
}

private fun resolveUriToAbsolutePath(context: Context, uri: Uri): String? {
    return try {
        val direct = UriUtils.getPath(context, uri)
        if (!direct.isNullOrBlank()) {
            val file = File(direct)
            if (file.exists() && file.canRead()) {
                return direct
            }
        }

        val input = context.contentResolver.openInputStream(uri) ?: return null
        val target = File(context.cacheDir, "plugin_pick_${System.currentTimeMillis()}")
        FileOutputStream(target).use { output ->
            input.use { it.copyTo(output) }
        }
        target.absolutePath
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun SchemaPluginPage(
    pluginId: String,
    path: String,
    modifier: Modifier = Modifier
) {
    val schemaFile = PluginManager.resolvePluginFile(pluginId, path)
    if (schemaFile == null || !schemaFile.exists()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Schema not found: $path", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val schema = remember(schemaFile.absolutePath) {
        runCatching {
            Json { ignoreUnknownKeys = true }
                .decodeFromString(PluginSchemaPage.serializer(), schemaFile.readText())
        }.getOrNull()
    }

    if (schema == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Schema parse failed", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    var output by remember { mutableStateOf("") }

    val rootModifier = applyScrollModifier(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        scroll = schema.scroll
    )

    Column(
        modifier = rootModifier,
        verticalArrangement = Arrangement.spacedBy(schema.spacing.dp),
        horizontalAlignment = parseHorizontalAlignment(schema.align)
    ) {
        val schemaScriptCode = remember(schemaFile.absolutePath, schema.script) {
            PluginManager.resolvePluginTextReference(pluginId, schema.script)
        }

        schema.widgets.forEach { widget ->
            RenderSchemaWidget(
                pluginId = pluginId,
                widget = widget,
                script = schemaScriptCode,
                onOutput = { output = it }
            )
        }

        if (output.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = output,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun RenderSchemaWidget(
    pluginId: String,
    widget: PluginSchemaWidget,
    script: String?,
    onOutput: (String) -> Unit
) {
    val baseModifier = applyScrollModifier(
        modifier = applySizeModifier(
            modifier = Modifier,
            widthSpec = widget.width,
            heightSpec = widget.height
        ),
        scroll = widget.scroll
    )

    when (widget.type.lowercase()) {
        "text" -> {
            Text(
                text = widget.text ?: "",
                style = MaterialTheme.typography.bodyLarge,
                modifier = baseModifier
            )
        }

        "button" -> {
            val label = widget.text ?: "Run"
            Button(
                onClick = {
                    onOutput(runSchemaAction(pluginId, widget, script))
                },
                modifier = baseModifier
            ) {
                Text(label)
            }
        }

        "outlined_button", "outlinedbutton" -> {
            val label = widget.text ?: "Run"
            OutlinedButton(
                onClick = {
                    onOutput(runSchemaAction(pluginId, widget, script))
                },
                modifier = baseModifier
            ) {
                Text(label)
            }
        }

        "card" -> {
            Card(modifier = baseModifier) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(widget.spacing.dp),
                    horizontalAlignment = parseHorizontalAlignment(widget.align)
                ) {
                    widget.children.forEach { child ->
                        RenderSchemaWidget(pluginId = pluginId, widget = child, script = script, onOutput = onOutput)
                    }
                }
            }
        }

        "divider" -> {
            androidx.compose.material3.HorizontalDivider(modifier = baseModifier.fillMaxWidth())
        }

        "row" -> {
            Row(
                modifier = baseModifier,
                horizontalArrangement = Arrangement.spacedBy(widget.spacing.dp),
                verticalAlignment = parseVerticalAlignment(widget.align)
            ) {
                widget.children.forEach { child ->
                    RenderSchemaWidget(pluginId = pluginId, widget = child, script = script, onOutput = onOutput)
                }
            }
        }

        "box" -> {
            Box(
                modifier = baseModifier,
                contentAlignment = parseContentAlignment(widget.align)
            ) {
                widget.children.forEach { child ->
                    RenderSchemaWidget(pluginId = pluginId, widget = child, script = script, onOutput = onOutput)
                }
            }
        }

        "spacer" -> {
            val spacerWidth = parseDp(widget.width) ?: 1f
            val spacerHeight = parseDp(widget.height) ?: 8f
            Spacer(
                modifier = Modifier
                    .width(spacerWidth.dp)
                    .height(spacerHeight.dp)
            )
        }

        else -> {
            Column(
                modifier = baseModifier,
                verticalArrangement = Arrangement.spacedBy(widget.spacing.dp),
                horizontalAlignment = parseHorizontalAlignment(widget.align)
            ) {
                widget.children.forEach { child ->
                    RenderSchemaWidget(pluginId = pluginId, widget = child, script = script, onOutput = onOutput)
                }
            }
        }
    }
}

private fun runSchemaAction(pluginId: String, widget: PluginSchemaWidget, script: String?): String {
    val action = widget.action
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }
        ?: widget.function?.takeIf { it.isNotBlank() }?.let { "script.call" }
        ?: return "Unsupported action"
    return when (action) {
        "r2" -> {
            val cmd = widget.command ?: ""
            runBlocking { R2PipeManager.execute(cmd).getOrElse { "Error: ${it.message}" } }
        }

        "script" -> {
            val code = PluginManager.resolvePluginTextReference(pluginId, widget.script) ?: ""
            runBlocking { PluginRuntime.runPluginScript(pluginId, code).getOrElse { "Error: ${it.message}" } }
        }

        "script.call" -> {
            val fn = widget.function ?: widget.command ?: ""
            val scriptCode = PluginManager.resolvePluginTextReference(pluginId, widget.script)
                ?.takeIf { it.isNotBlank() }
                ?: script.orEmpty()
            runBlocking { PluginRuntime.runPluginScriptFunction(pluginId, scriptCode, fn).getOrElse { "Error: ${it.message}" } }
        }

        "frida" -> {
            val code = widget.script ?: ""
            PluginRuntime.runFridaScript(
                pluginId = pluginId,
                script = code
            ).getOrElse { "Error: ${it.message}" }
        }

        "http", "request" -> {
            PluginRuntime.requestNetwork(
                pluginId = pluginId,
                method = widget.method ?: "GET",
                url = widget.url ?: widget.command.orEmpty(),
                body = widget.body.orEmpty(),
                headersJson = widget.headers.orEmpty()
            ).getOrElse { "Error: ${it.message}" }
        }

        "download" -> {
            PluginRuntime.downloadToPluginData(
                pluginId = pluginId,
                url = widget.url ?: "",
                relativePath = widget.path ?: "download.bin"
            ).getOrElse { "Error: ${it.message}" }
        }

        "proc.start" -> {
            PluginRuntime.processStart(
                pluginId = pluginId,
                sessionId = widget.sessionId ?: "default",
                commandLine = widget.command ?: ""
            ).getOrElse { "Error: ${it.message}" }
        }

        "proc.write" -> {
            PluginRuntime.processWrite(
                pluginId = pluginId,
                sessionId = widget.sessionId ?: "default",
                input = widget.body.orEmpty()
            ).getOrElse { "Error: ${it.message}" }
        }

        "proc.read" -> {
            PluginRuntime.processRead(
                pluginId = pluginId,
                sessionId = widget.sessionId ?: "default",
                timeoutMs = widget.timeoutMs,
                maxLines = widget.maxLines
            ).getOrElse { "Error: ${it.message}" }
        }

        "proc.stop" -> {
            PluginRuntime.processStop(
                pluginId = pluginId,
                sessionId = widget.sessionId ?: "default"
            ).getOrElse { "Error: ${it.message}" }
        }

        "proc.alive" -> {
            PluginRuntime.processAlive(
                pluginId = pluginId,
                sessionId = widget.sessionId ?: "default"
            ).map { it.toString() }.getOrElse { "Error: ${it.message}" }
        }

        "proot.run" -> {
            PluginRuntime.prootRun(
                pluginId = pluginId,
                environment = widget.environment ?: "plugin",
                script = widget.command ?: widget.body.orEmpty()
            ).getOrElse { "Error: ${it.message}" }
        }

        "proot.ensure" -> {
            PluginRuntime.prootEnsure(
                pluginId = pluginId,
                environment = widget.environment ?: "plugin",
                rootfsAlias = widget.rootfsAlias ?: "ubuntu"
            ).getOrElse { "Error: ${it.message}" }
        }

        "proot.proc.start" -> {
            PluginRuntime.prootProcessStart(
                pluginId = pluginId,
                sessionId = widget.sessionId ?: "default",
                environment = widget.environment ?: "plugin",
                commandLine = widget.command ?: ""
            ).getOrElse { "Error: ${it.message}" }
        }

        "proot.r2" -> {
            PluginRuntime.prootR2(
                pluginId = pluginId,
                environment = widget.environment ?: "main",
                r2Command = widget.command ?: "",
                filePath = widget.path.orEmpty()
            ).getOrElse { "Error: ${it.message}" }
        }

        "plugin.dir" -> PluginRuntime.getPluginDir(pluginId).getOrElse { "Error: ${it.message}" }
        "data.dir" -> PluginRuntime.getPluginDataDir(pluginId).getOrElse { "Error: ${it.message}" }
        else -> "Unsupported action: $action"
    }
}

@Composable
private fun applyScrollModifier(modifier: Modifier, scroll: String?): Modifier {
    return when (scroll?.lowercase()) {
        "vertical" -> modifier.verticalScroll(rememberScrollState())
        "horizontal" -> modifier.horizontalScroll(rememberScrollState())
        "both" -> modifier
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
        else -> modifier
    }
}

private fun applySizeModifier(modifier: Modifier, widthSpec: String?, heightSpec: String?): Modifier {
    var result = modifier
    when (widthSpec?.trim()?.lowercase()) {
        "fill", "fillmaxwidth", "match" -> result = result.fillMaxWidth()
        else -> {
            parseDp(widthSpec)?.let { result = result.width(it.dp) }
        }
    }
    when (heightSpec?.trim()?.lowercase()) {
        "fill", "fillmaxheight", "match" -> result = result.fillMaxHeight()
        else -> {
            parseDp(heightSpec)?.let { result = result.height(it.dp) }
        }
    }
    return result
}

private fun parseDp(spec: String?): Float? {
    if (spec.isNullOrBlank()) return null
    return spec.trim().removeSuffix("dp").toFloatOrNull()
}

private fun parseHorizontalAlignment(value: String?): Alignment.Horizontal {
    return when (value?.lowercase()) {
        "center", "center_horizontal" -> Alignment.CenterHorizontally
        "end", "right" -> Alignment.End
        else -> Alignment.Start
    }
}

private fun parseVerticalAlignment(value: String?): Alignment.Vertical {
    return when (value?.lowercase()) {
        "top" -> Alignment.Top
        "bottom" -> Alignment.Bottom
        else -> Alignment.CenterVertically
    }
}

private fun parseContentAlignment(value: String?): Alignment {
    return when (value?.lowercase()) {
        "top", "top_center" -> Alignment.TopCenter
        "top_end", "top_right" -> Alignment.TopEnd
        "center", "center_center" -> Alignment.Center
        "end", "right", "center_end", "center_right" -> Alignment.CenterEnd
        "bottom_start", "bottom_left" -> Alignment.BottomStart
        "bottom", "bottom_center" -> Alignment.BottomCenter
        "bottom_end", "bottom_right" -> Alignment.BottomEnd
        else -> Alignment.CenterStart
    }
}

private class PluginWebBridge(
    private val context: Context,
    private val pluginId: String,
    private val onPickFileRequest: (String) -> Unit,
    private val onPickDirectoryRequest: (String) -> Unit,
    private val onPrepareProotRequest: (Boolean) -> Unit,
    private val onJumpToDisasmRequest: ((Long) -> Unit)? = null
) {
    @JavascriptInterface
    fun r2(command: String): String {
        return runBlocking {
            R2PipeManager.execute(command).getOrElse { "Error: ${it.message}" }
        }
    }

    @JavascriptInterface
    fun readText(path: String): String {
        val file = PluginManager.resolvePluginFile(pluginId, path) ?: return ""
        return runCatching { file.readText() }.getOrDefault("")
    }

    @JavascriptInterface
    fun writeData(path: String, content: String): String {
        val file = PluginManager.resolvePluginDataFile(pluginId, path, mustExist = false)
            ?: return "invalid path"
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(content)
            "ok"
        }.getOrElse { "error: ${it.message}" }
    }

    @JavascriptInterface
    fun runScript(code: String): String {
        return runBlocking {
            PluginRuntime.runPluginScript(pluginId, code)
                .getOrElse { "Error: ${it.message}" }
        }
    }

    @JavascriptInterface
    fun pluginDir(): String {
        return PluginRuntime.getPluginDir(pluginId).getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun dataDir(): String {
        return PluginRuntime.getPluginDataDir(pluginId).getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun httpGet(url: String): String {
        return PluginRuntime.requestNetwork(pluginId, "GET", url)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun httpRequest(method: String, url: String, body: String, headersJson: String): String {
        return PluginRuntime.requestNetwork(pluginId, method, url, body, headersJson)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun download(url: String, relativePath: String): String {
        return PluginRuntime.downloadToPluginData(pluginId, url, relativePath)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun procStart(sessionId: String, commandLine: String): String {
        return PluginRuntime.processStart(pluginId, sessionId, commandLine)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun procWrite(sessionId: String, input: String): String {
        return PluginRuntime.processWrite(pluginId, sessionId, input)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun procRead(sessionId: String, timeoutMs: Long, maxLines: Int): String {
        return PluginRuntime.processRead(pluginId, sessionId, timeoutMs, maxLines)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun procStop(sessionId: String): String {
        return PluginRuntime.processStop(pluginId, sessionId)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun procAlive(sessionId: String): String {
        return PluginRuntime.processAlive(pluginId, sessionId)
            .map { it.toString() }
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun prepareProot(force: Boolean): String {
        return runBlocking {
            withContext(Dispatchers.Main) {
                onPrepareProotRequest(force)
            }
            "started"
        }
    }

    @JavascriptInterface
    fun isProotPrepared(): String {
        return PluginManager.isProotPreparedForPlugin(pluginId).toString()
    }

    @JavascriptInterface
    fun prootIsReady(environment: String): String {
        return PluginRuntime.prootIsReady(pluginId, environment)
            .map { it.toString() }
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun prootEnsure(environment: String, rootfsAlias: String): String {
        return PluginRuntime.prootEnsure(pluginId, environment, rootfsAlias)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun prootRun(environment: String, script: String): String {
        return PluginRuntime.prootRun(pluginId, environment, script)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun prootApt(environment: String, packagesJson: String): String {
        return PluginRuntime.prootInstallApt(pluginId, environment, packagesJson)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun prootVenv(environment: String, name: String, packagesJson: String, requirementsPath: String): String {
        return PluginRuntime.prootCreatePythonVenv(pluginId, environment, name, packagesJson, requirementsPath)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun prootR2pmInstall(environment: String, packageName: String): String {
        return PluginRuntime.prootR2pmInstall(pluginId, environment, packageName)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun prootR2(environment: String, command: String, filePath: String): String {
        return PluginRuntime.prootR2(pluginId, environment, command, filePath)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun prootPathInfo(environment: String): String {
        return PluginRuntime.prootPathInfo(pluginId, environment)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun prootProcStart(sessionId: String, environment: String, commandLine: String): String {
        return PluginRuntime.prootProcessStart(pluginId, sessionId, environment, commandLine)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun frida(script: String): String {
        return PluginRuntime.runFridaScript(pluginId, script)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun systemLanguage(): String {
        return PluginRuntime.getSystemLanguageTag()
    }

    @JavascriptInterface
    fun pickFile(requestId: String): String {
        val normalized = requestId.trim().ifBlank { "default" }
        return runBlocking {
            withContext(Dispatchers.Main) {
                onPickFileRequest(normalized)
            }
            PluginRuntime.pickFile(pluginId, normalized)
                .getOrElse { "Error: ${it.message}" }
        }
    }

    @JavascriptInterface
    fun pickDirectory(requestId: String): String {
        val normalized = requestId.trim().ifBlank { "default" }
        return runBlocking {
            withContext(Dispatchers.Main) {
                onPickDirectoryRequest(normalized)
            }
            PluginRuntime.pickDirectory(pluginId, normalized)
                .getOrElse { "Error: ${it.message}" }
        }
    }

    @JavascriptInterface
    fun projectsRootDir(): String {
        return PluginRuntime.getProjectsRootDir(pluginId)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun projectsInfo(): String {
        return PluginRuntime.listProjectsInfo(pluginId)
            .getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun androidImportClass(className: String): String {
        return PluginRuntime.androidImportClassPayload(pluginId, className)
    }

    @JavascriptInterface
    fun androidNew(className: String, argsJson: String): String {
        return PluginRuntime.androidNewPayload(pluginId, className, argsJson)
    }

    @JavascriptInterface
    fun androidCall(refId: String, methodName: String, argsJson: String): String {
        return PluginRuntime.androidCallPayload(pluginId, refId, methodName, argsJson)
    }

    @JavascriptInterface
    fun androidCallStatic(className: String, methodName: String, argsJson: String): String {
        return PluginRuntime.androidCallStaticPayload(pluginId, className, methodName, argsJson)
    }

    @JavascriptInterface
    fun androidGetStaticField(className: String, fieldName: String): String {
        return PluginRuntime.androidGetStaticFieldPayload(pluginId, className, fieldName)
    }

    @JavascriptInterface
    fun androidStartActivity(refId: String): String {
        return PluginRuntime.androidStartActivityPayload(pluginId, refId)
    }

    @JavascriptInterface
    fun androidGetLaunchIntentForPackage(packageName: String): String {
        return PluginRuntime.androidGetLaunchIntentForPackagePayload(pluginId, packageName)
    }

    @JavascriptInterface
    fun androidGetCurrentActivity(): String {
        return PluginRuntime.androidGetCurrentActivityPayload(pluginId)
    }

    @JavascriptInterface
    fun androidRelease(refId: String): String {
        return PluginRuntime.androidReleasePayload(pluginId, refId)
    }

    @JavascriptInterface
    fun projectDir(): String = projectsRootDir()

    @JavascriptInterface
    fun projectInfo(): String = projectsInfo()

    @JavascriptInterface
    fun openPath(path: String): String {
        return runCatching {
            val file = File(path)
            require(file.exists()) { "path not found: $path" }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mime = if (file.isDirectory) {
                "resource/folder"
            } else {
                android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "application/octet-stream"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, file.name.ifBlank { path }).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "ok"
        }.getOrElse { "Error: ${it.message}" }
    }

    @JavascriptInterface
    fun jumpToDisasm(address: String): String {
        val addr = parseAddress(address)
            ?: return "Error: invalid address: $address"
        val callback = onJumpToDisasmRequest
            ?: return "Error: jumpToDisasm is not available in this context"
        return runBlocking {
            withContext(Dispatchers.Main) {
                callback(addr)
            }
            "ok"
        }
    }

    @JavascriptInterface
    fun jumpToAddress(address: String): String = jumpToDisasm(address)

    private fun parseAddress(value: String): Long? {
        val trimmed = value.trim().removePrefix("+")
        if (trimmed.isBlank()) return null
        return runCatching {
            when {
                trimmed.startsWith("0x", ignoreCase = true) -> trimmed.substring(2).toLong(16)
                else -> trimmed.toLong()
            }
        }.getOrNull()
    }
}

@Serializable
data class PluginSchemaPage(
    @SerialName("widgets") val widgets: List<PluginSchemaWidget> = emptyList(),
    @SerialName("script") val script: String? = null,
    @SerialName("scroll") val scroll: String? = "vertical",
    @SerialName("spacing") val spacing: Float = 12f,
    @SerialName("align") val align: String? = null
)

@Serializable
data class PluginSchemaWidget(
    @SerialName("type") val type: String,
    @SerialName("text") val text: String? = null,
    @SerialName("action") val action: String? = null,
    @SerialName("command") val command: String? = null,
    @SerialName("script") val script: String? = null,
    @SerialName("children") val children: List<PluginSchemaWidget> = emptyList(),
    @SerialName("width") val width: String? = null,
    @SerialName("height") val height: String? = null,
    @SerialName("align") val align: String? = null,
    @SerialName("scroll") val scroll: String? = null,
    @SerialName("spacing") val spacing: Float = 8f,
    @SerialName("url") val url: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("headers") val headers: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("method") val method: String? = null,
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("timeoutMs") val timeoutMs: Long = 30L,
    @SerialName("maxLines") val maxLines: Int = 200,
    @SerialName("function") val function: String? = null,
    @SerialName("environment") val environment: String? = null,
    @SerialName("rootfsAlias") val rootfsAlias: String? = null
)

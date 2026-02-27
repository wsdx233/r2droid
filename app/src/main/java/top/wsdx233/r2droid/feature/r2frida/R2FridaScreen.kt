package top.wsdx233.r2droid.feature.r2frida

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.util.R2FridaInstallState
import top.wsdx233.r2droid.util.R2FridaInstaller
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R2FridaScreen(onBack: () -> Unit, onConnect: (String) -> Unit = {}) {
    val context = LocalContext.current
    var installed by remember { mutableStateOf(R2FridaInstaller.isInstalled(context)) }
    val installState by R2FridaInstaller.state.collectAsState()

    LaunchedEffect(installState.status) {
        if (installState.status == R2FridaInstallState.Status.DONE) {
            installed = R2FridaInstaller.isInstalled(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose { R2FridaInstaller.resetState() }
    }

    if (installed && installState.status != R2FridaInstallState.Status.DONE) {
        R2FridaFeatureScreen(onBack = onBack, onConnect = onConnect, onReinstall = { installed = false })
    } else {
        R2FridaInstallScreen(onBack = onBack, installState = installState, onInstalled = {
            R2FridaInstaller.resetState()
            installed = true
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun R2FridaInstallScreen(
    onBack: () -> Unit,
    installState: R2FridaInstallState,
    onInstalled: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var useChinaSource by remember { mutableStateOf(SettingsManager.language == "zh") }
    val isWorking = installState.status in listOf(
        R2FridaInstallState.Status.FETCHING,
        R2FridaInstallState.Status.DOWNLOADING,
        R2FridaInstallState.Status.EXTRACTING
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.r2frida_install_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isWorking) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header icon
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Description card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(
                        stringResource(R.string.r2frida_install_what),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.r2frida_install_what_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Status area
            AnimatedContent(
                targetState = installState.status,
                label = "install_status"
            ) { status ->
                when (status) {
                    R2FridaInstallState.Status.IDLE -> {
                        // Ready to install
                    }
                    R2FridaInstallState.Status.FETCHING -> {
                        StatusCard(
                            icon = Icons.Default.CloudDownload,
                            text = stringResource(R.string.r2frida_install_fetching),
                            showProgress = true
                        )
                    }
                    R2FridaInstallState.Status.DOWNLOADING -> {
                        StatusCard(
                            icon = Icons.Default.Download,
                            text = stringResource(R.string.r2frida_install_downloading),
                            progress = installState.progress
                        )
                    }
                    R2FridaInstallState.Status.EXTRACTING -> {
                        StatusCard(
                            icon = Icons.Default.FolderZip,
                            text = stringResource(R.string.r2frida_install_extracting),
                            showProgress = true
                        )
                    }
                    R2FridaInstallState.Status.DONE -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.r2frida_install_done),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (installState.version.isNotEmpty()) {
                                        Text(
                                            "v${installState.version}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                    R2FridaInstallState.Status.ERROR -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        installState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.r2frida_install_switch_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Action buttons
            if (installState.status == R2FridaInstallState.Status.DONE) {
                Button(
                    onClick = onInstalled,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.r2frida_install_continue))
                }
            } else {
                Button(
                    onClick = {
                        scope.launch { R2FridaInstaller.install(context, useChinaSource) }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isWorking
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.r2frida_install_installing))
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (installState.status == R2FridaInstallState.Status.ERROR)
                                stringResource(R.string.common_retry)
                            else
                                stringResource(R.string.r2frida_install_btn)
                        )
                    }
                }

                // Clickable source toggle
                TextButton(
                    onClick = { useChinaSource = !useChinaSource },
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(
                            if (useChinaSource) R.string.r2frida_source_gitee
                            else R.string.r2frida_source_github
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    progress: Float? = null,
    showProgress: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (showProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

private data class FridaProcess(val pid: String, val name: String)

private suspend fun fetchFridaProcesses(context: android.content.Context, host: String, port: String): Result<List<FridaProcess>> {
    return withContext(Dispatchers.IO) {
        try {
            val workDir = File(context.filesDir, "radare2/bin")
            val r2Binary = File(workDir, "r2").absolutePath
            val uri = "frida://attach/remote/$host:$port/"

            val envMap = mutableMapOf<String, String>()
            val myLd = "${File(context.filesDir, "radare2/lib")}:${File(context.filesDir, "libs")}"
            val existingLd = System.getenv("LD_LIBRARY_PATH")
            envMap["LD_LIBRARY_PATH"] = "$myLd${if (existingLd != null) ":$existingLd" else ""}"
            envMap["XDG_DATA_HOME"] = File(context.filesDir, "r2work").absolutePath
            envMap["XDG_CACHE_HOME"] = File(context.filesDir, ".cache").absolutePath
            envMap["HOME"] = workDir.absolutePath
            envMap["TERM"] = "dumb"
            envMap["R2_NOCOLOR"] = "1"
            val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
            envMap["PATH"] = "${workDir.absolutePath}:$systemPath"

            val pb = ProcessBuilder("/system/bin/sh", "-c", "$r2Binary \"$uri\"")
            pb.directory(workDir)
            pb.environment().putAll(envMap)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroyForcibly()

            val processes = output.lines()
                .map { it.trim() }
                .filter { line -> line.isNotEmpty() && line[0].isDigit() }
                .mapNotNull { line ->
                    val parts = line.split(Regex("\\s+"), limit = 2)
                    if (parts.size == 2) FridaProcess(parts[0], parts[1]) else null
                }
            Result.success(processes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun R2FridaFeatureScreen(onBack: () -> Unit, onConnect: (String) -> Unit, onReinstall: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf(SettingsManager.fridaHost) }
    var port by remember { mutableStateOf(SettingsManager.fridaPort) }

    // Remote process list state
    var processes by remember { mutableStateOf<List<FridaProcess>>(emptyList()) }
    var processLoading by remember { mutableStateOf(false) }
    var processError by remember { mutableStateOf<String?>(null) }
    var processesExpanded by remember { mutableStateOf(false) }

    // Local apps state
    var localApps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var appSearchQuery by remember { mutableStateOf("") }
    var appsExpanded by remember { mutableStateOf(false) }

    // Help & Settings state
    var showHelpSheet by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showReinstallConfirm by remember { mutableStateOf(false) }

    val pm = context.packageManager

    // Load local apps once
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            localApps = pm.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        }
    }

    fun refreshProcesses() {
        scope.launch {
            processLoading = true
            processError = null
            SettingsManager.fridaHost = host
            SettingsManager.fridaPort = port
            val result = fetchFridaProcesses(context, host, port)
            result.onSuccess { processes = it }
            result.onFailure { processError = it.message }
            processLoading = false
        }
    }

    // Auto-fetch on first load
    LaunchedEffect(Unit) { refreshProcesses() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_r2frida_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.frida_help))
                    }
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.frida_config))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Connection Settings Card
            item { ConnectionSettingsCard(host, port, { host = it }, { port = it }) }

            // Quick Connect Gadget
            item {
                Button(
                    onClick = {
                        SettingsManager.fridaHost = host
                        SettingsManager.fridaPort = port
                        onConnect("\"frida://attach/remote/$host:$port/Gadget\"")
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.frida_quick_connect_gadget))
                }
            }

            // Remote Processes Section
            item {
                RemoteProcessesHeader(processLoading) { refreshProcesses() }
            }

            if (processLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            } else if (processError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.frida_fetch_error, processError ?: ""),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (processes.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.frida_no_processes),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxCollapsed = 5
                val visibleProcesses = if (processesExpanded || processes.size <= maxCollapsed)
                    processes else processes.take(maxCollapsed)
                items(visibleProcesses, key = { it.pid }) { proc ->
                    ProcessItem(proc) {
                        onConnect("\"frida://attach/remote/$host:$port/${proc.name}\"")
                    }
                }
                if (processes.size > maxCollapsed) {
                    item {
                        TextButton(
                            onClick = { processesExpanded = !processesExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (processesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (processesExpanded) stringResource(R.string.frida_collapse)
                                else stringResource(R.string.frida_show_all, processes.size)
                            )
                        }
                    }
                }
            }

            // Local Apps Section
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.frida_local_apps),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.frida_search_apps)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            val filtered = localApps.filter {
                val label = pm.getApplicationLabel(it).toString()
                val pkg = it.packageName
                label.contains(appSearchQuery, true) || pkg.contains(appSearchQuery, true)
            }

            if (filtered.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.frida_no_apps),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxCollapsedApps = 5
                val visibleApps = if (appsExpanded || filtered.size <= maxCollapsedApps)
                    filtered else filtered.take(maxCollapsedApps)
                items(visibleApps, key = { it.packageName }) { app ->
                    AppItem(app, pm,
                        onAttach = {
                            onConnect("\"frida://attach/remote/$host:$port/${app.packageName}\"")
                        },
                        onSpawn = {
                            onConnect("\"frida://spawn/remote/$host:$port/${app.packageName}\"")
                        }
                    )
                }
                if (filtered.size > maxCollapsedApps) {
                    item {
                        TextButton(
                            onClick = { appsExpanded = !appsExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (appsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (appsExpanded) stringResource(R.string.frida_collapse)
                                else stringResource(R.string.frida_show_all, filtered.size)
                            )
                        }
                    }
                }
            }
        }
    }

    // Help Bottom Sheet
    if (showHelpSheet) {
        FridaHelpSheet(onDismiss = { showHelpSheet = false })
    }

    // Config Dialog
    if (showConfigDialog) {
        FridaConfigDialog(
            onDismiss = { showConfigDialog = false },
            onReinstall = {
                showConfigDialog = false
                showReinstallConfirm = true
            }
        )
    }

    // Reinstall Confirmation
    if (showReinstallConfirm) {
        AlertDialog(
            onDismissRequest = { showReinstallConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.frida_reinstall_confirm_title)) },
            text = { Text(stringResource(R.string.frida_reinstall_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showReinstallConfirm = false
                    val pluginsDir = R2FridaInstaller.getPluginsDir(context)
                    pluginsDir.listFiles()?.filter { it.name.startsWith("io_frida") }?.forEach { it.delete() }
                    onReinstall()
                }) { Text(stringResource(R.string.common_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showReinstallConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun ConnectionSettingsCard(
    host: String, port: String,
    onHostChange: (String) -> Unit, onPortChange: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.frida_connection_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host, onValueChange = onHostChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.frida_host)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = port, onValueChange = onPortChange,
                    modifier = Modifier.width(100.dp),
                    label = { Text(stringResource(R.string.frida_port)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun RemoteProcessesHeader(loading: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.frida_remote_processes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onRefresh, enabled = !loading) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
        }
    }
}

@Composable
private fun ProcessItem(proc: FridaProcess, onAttach: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAttach),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(proc.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("PID: ${proc.pid}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onAttach, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.frida_attach))
            }
        }
    }
}

@Composable
private fun AppItem(
    app: ApplicationInfo,
    pm: android.content.pm.PackageManager,
    onAttach: () -> Unit,
    onSpawn: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    pm.getApplicationLabel(app).toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onAttach, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.frida_attach))
            }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = onSpawn, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.frida_spawn))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FridaHelpSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        FridaHelpSheetContent(context, onDismiss)
    }
}

@Composable
private fun FridaHelpSheetContent(context: android.content.Context, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            stringResource(R.string.frida_help_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Server mode card
        FridaHelpModeCard(
            icon = Icons.Default.Dns,
            title = stringResource(R.string.frida_help_server_title),
            description = stringResource(R.string.frida_help_server_desc),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )

        // Gadget mode card
        FridaHelpModeCard(
            icon = Icons.Default.Extension,
            title = stringResource(R.string.frida_help_gadget_title),
            description = stringResource(R.string.frida_help_gadget_desc),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )

        Spacer(Modifier.height(4.dp))

        // Link cards
        FridaLinkCard(
            title = stringResource(R.string.frida_help_releases),
            description = stringResource(R.string.frida_help_releases_desc),
            url = "https://github.com/frida/frida/releases",
            icon = Icons.Default.NewReleases,
            context = context
        )

        FridaLinkCard(
            title = stringResource(R.string.frida_help_gadgeter),
            description = stringResource(R.string.frida_help_gadgeter_desc),
            url = "https://github.com/wsdx233/Gadgeter",
            icon = Icons.Default.PhoneAndroid,
            context = context
        )
    }
}

@Composable
private fun FridaHelpModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun FridaLinkCard(
    title: String,
    description: String,
    url: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    context: android.content.Context
) {
    OutlinedCard(
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FridaConfigDialog(onDismiss: () -> Unit, onReinstall: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        title = { Text(stringResource(R.string.frida_config_title)) },
        text = {
            Column {
                OutlinedCard(
                    onClick = onReinstall,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.frida_reinstall),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                stringResource(R.string.frida_reinstall_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.func_close))
            }
        }
    )
}

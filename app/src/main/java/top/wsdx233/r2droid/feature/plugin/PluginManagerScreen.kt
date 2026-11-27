package top.wsdx233.r2droid.feature.plugin

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.DocumentsUiOpenDocumentTreeContract
import top.wsdx233.r2droid.util.TerminalLauncher
import top.wsdx233.r2droid.util.UriUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val catalog by PluginManager.catalog.collectAsState()
    val installed by PluginManager.installed.collectAsState()
    val installableCatalog = remember(catalog) {
        catalog.filter { item ->
            item.installed == null || item.hasUpgrade
        }
    }
    val repositories by PluginManager.repositorySources.collectAsState()
    val status by PluginManager.status.collectAsState()
    val isWorking by PluginManager.isWorking.collectAsState()
    val logs by PluginManager.logs.collectAsState()
    val installProgress by PluginManager.installProgress.collectAsState()
    val developerConfig by PluginManager.developerConfig.collectAsState()

    val zipPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            PluginManager.installFromZipUri(uri)
        }
    }

    val workspacePickerLauncher = rememberLauncherForActivityResult(DocumentsUiOpenDocumentTreeContract()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val path = UriUtils.getTreePath(context, uri) ?: return@rememberLauncherForActivityResult
        scope.launch {
            PluginManager.setDeveloperWorkspaceDir(path)
        }
    }

    var sourceInput by remember { mutableStateOf("") }
    var selectedPage by remember { mutableStateOf<Pair<String, PluginPage>?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        PluginManager.initialize(context)
    }

    selectedPage?.let { (pluginId, page) ->
        BackHandler(enabled = true) {
            selectedPage = null
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("${stringResource(R.string.plugin_open_page)}: $pluginId") },
                    navigationIcon = {
                        IconButton(onClick = { selectedPage = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            PluginPageRenderer(
                pluginId = pluginId,
                page = page,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_plugins_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { PluginManager.refreshCatalog() } },
                        enabled = !isWorking
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val tabs = listOf(
                stringResource(R.string.plugin_tab_plugins),
                stringResource(R.string.plugin_tab_sources),
                stringResource(R.string.plugin_tab_logs)
            )
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> PluginListTab(
                    installableCatalog = installableCatalog,
                    installed = installed,
                    isWorking = isWorking,
                    installProgress = installProgress,
                    onInstall = { entry -> scope.launch { PluginManager.install(entry) } },
                    onUpdate = { pluginId -> scope.launch { PluginManager.update(pluginId) } },
                    onDelete = { pluginId -> scope.launch { PluginManager.delete(pluginId) } },
                    onSetEnabled = { pluginId, enabled -> scope.launch { PluginManager.setEnabled(pluginId, enabled) } },
                    onOpenPage = { pluginId, page -> selectedPage = pluginId to page }
                )

                1 -> SourceManageTab(
                    repositories = repositories,
                    sourceInput = sourceInput,
                    onSourceInputChange = { sourceInput = it },
                    isWorking = isWorking,
                    developerConfig = developerConfig,
                    onAdd = {
                        scope.launch {
                            PluginManager.addRepositorySource(sourceInput)
                            sourceInput = ""
                        }
                    },
                    onEdit = { oldRepo, newRepo ->
                        scope.launch {
                            PluginManager.updateRepositorySource(oldRepo, newRepo)
                            if (sourceInput == oldRepo) {
                                sourceInput = newRepo
                            }
                        }
                    },
                    onRemove = { repo -> scope.launch { PluginManager.removeRepositorySource(repo) } },
                    onResetDefault = { scope.launch { PluginManager.resetRepositorySourcesToDefault() } },
                    onInstallZip = { zipPickerLauncher.launch("application/zip") },
                    onSetDeveloperMode = { enabled ->
                        scope.launch { PluginManager.setDeveloperModeEnabled(enabled) }
                    },
                    onPickDeveloperWorkspace = { workspacePickerLauncher.launch(null) },
                    onCreateDeveloperPlugin = { request ->
                        scope.launch { PluginManager.createDeveloperPlugin(request) }
                    }
                )

                else -> LogsTab(
                    status = status,
                    logs = logs,
                )
            }
        }
    }
}

@Composable
private fun PluginListTab(
    installableCatalog: List<PluginCatalogItem>,
    installed: List<InstalledPlugin>,
    isWorking: Boolean,
    installProgress: Map<String, Float>,
    onInstall: (PluginIndexEntry) -> Unit,
    onUpdate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onOpenPage: (String, PluginPage) -> Unit
) {
    val context = LocalContext.current

    val expandedDescriptions = remember { mutableStateMapOf<String, Boolean>() }
    val expandableDescriptions = remember { mutableStateMapOf<String, Boolean>() }
    var pendingDeletePlugin by remember { mutableStateOf<InstalledPlugin?>(null) }
    var selectedPlugin by remember { mutableStateOf<InstalledPlugin?>(null) }
    var pendingIncompatibleInstall by remember {
        mutableStateOf<Pair<PluginIndexEntry, PluginVersionCompatibility>?>(null)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (installed.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.plugin_installed_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        items(installed, key = { "installed_${it.state.id}" }) { plugin ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPlugin = plugin }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val uiOptions = plugin.manifest?.ui ?: PluginUiOptions()
                    val descriptionText = plugin.manifest?.description
                        ?.takeIf { it.isNotBlank() }
                        ?: plugin.manifest?.author?.takeIf { it.isNotBlank() }
                        ?: plugin.state.sourceUrl
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = plugin.manifest?.name ?: plugin.state.id,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${plugin.state.id} @ ${plugin.state.version}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiOptions.showEnableStatus) {
                            Text(
                                text = if (plugin.state.enabled) {
                                    stringResource(R.string.plugin_state_enabled)
                                } else {
                                    stringResource(R.string.plugin_state_disabled)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (plugin.state.enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Text(
                            text = descriptionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val isBundledAsset = plugin.state.sourceUrl.startsWith("asset://plugins/packages/", ignoreCase = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (uiOptions.showEnableToggle) {
                            IconButton(
                                onClick = { onSetEnabled(plugin.state.id, !plugin.state.enabled) },
                                enabled = !isWorking
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = stringResource(R.string.plugin_enabled),
                                    tint = if (plugin.state.enabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        if (!isBundledAsset) {
                            IconButton(
                                onClick = { pendingDeletePlugin = plugin },
                                enabled = !isWorking
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.plugin_uninstall)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val entry = plugin.manifest?.entry
                                val openPage = entry?.page
                                val terminal = entry?.terminal
                                when {
                                    openPage != null -> onOpenPage(plugin.state.id, openPage)
                                    terminal != null -> {
                                        val startupCommand = PluginRuntime
                                            .resolveTerminalStartupCommand(plugin.state.id, terminal.command)
                                            .getOrElse { terminal.command }
                                        TerminalLauncher.start(context, startupCommand = startupCommand)
                                    }
                                }
                            },
                            enabled = plugin.manifest?.entry?.page != null || plugin.manifest?.entry?.terminal != null
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = stringResource(R.string.plugin_open_page)
                            )
                        }
                    }
                }
            }
        }

        if (installableCatalog.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.plugin_catalog_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        items(installableCatalog, key = { "catalog_${it.indexEntry.id}" }) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.indexEntry.name, style = MaterialTheme.typography.titleMedium)
                        val pluginId = item.indexEntry.id
                        val isExpanded = expandedDescriptions[pluginId] == true
                        val showToggle = expandableDescriptions[pluginId] == true
                        Text(
                            text = item.indexEntry.description.ifBlank { pluginId },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { textLayoutResult ->
                                if (!isExpanded) {
                                    expandableDescriptions[pluginId] = textLayoutResult.hasVisualOverflow
                                }
                            }
                        )
                        if (showToggle) {
                            TextButton(
                                onClick = {
                                    expandedDescriptions[pluginId] = !isExpanded
                                },
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = if (isExpanded) {
                                        stringResource(R.string.plugin_desc_collapse)
                                    } else {
                                        stringResource(R.string.plugin_desc_expand)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val installedVersion = item.installed?.state?.version
                    val shouldUpdate = item.hasUpgrade
                    val actionText = when {
                        installedVersion == null -> stringResource(R.string.plugin_install)
                        shouldUpdate -> stringResource(R.string.plugin_update)
                        else -> stringResource(R.string.plugin_reinstall)
                    }
                    val progress = installProgress[item.indexEntry.id]
                    if (progress != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                val compatibility = PluginManager.getVersionCompatibility(item.indexEntry)
                                if (!compatibility.compatible) {
                                    pendingIncompatibleInstall = item.indexEntry to compatibility
                                    return@Button
                                }
                                if (shouldUpdate) {
                                    onUpdate(item.indexEntry.id)
                                } else {
                                    onInstall(item.indexEntry)
                                }
                            },
                            enabled = !isWorking
                        ) {
                            Text(actionText)
                        }
                    }
                }
            }
        }
    }

    val pluginToDelete = pendingDeletePlugin
    if (pluginToDelete != null) {
        val displayName = pluginToDelete.manifest?.name?.ifBlank { pluginToDelete.state.id } ?: pluginToDelete.state.id
        AlertDialog(
            onDismissRequest = { pendingDeletePlugin = null },
            title = { Text(stringResource(R.string.plugin_uninstall_confirm_title)) },
            text = { Text(stringResource(R.string.plugin_uninstall_confirm_message, displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(pluginToDelete.state.id)
                        pendingDeletePlugin = null
                    },
                    enabled = !isWorking
                ) {
                    Text(stringResource(R.string.plugin_uninstall))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePlugin = null }) {
                    Text(stringResource(R.string.plugin_developer_create_cancel))
                }
            }
        )
    }
    val incompatibleInstall = pendingIncompatibleInstall
    if (incompatibleInstall != null) {
        val (entry, compatibility) = incompatibleInstall
        val minVersion = compatibility.minVersion.orEmpty().ifBlank { "?" }
        AlertDialog(
            onDismissRequest = { pendingIncompatibleInstall = null },
            title = { Text(stringResource(R.string.plugin_incompatible_version_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.plugin_incompatible_version_message,
                        entry.name,
                        minVersion,
                        compatibility.currentVersion
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingIncompatibleInstall = null }) {
                    Text(stringResource(R.string.plugin_incompatible_version_confirm))
                }
            }
        )
    }

    selectedPlugin?.let { plugin ->
        PluginDetailDialog(
            plugin = plugin,
            onDismiss = { selectedPlugin = null }
        )
    }
}

@Composable
private fun PluginPermissionSummary(
    permissions: List<String>,
    modifier: Modifier = Modifier,
    emptyText: String? = null
) {
    val labels = permissions.distinct().map { permission ->
        pluginPermissionLabel(permission)?.let { stringResource(it) } ?: permission
    }
    val content = if (labels.isNotEmpty()) {
        labels.joinToString(", ")
    } else {
        emptyText ?: return
    }

    Text(
        text = stringResource(R.string.plugin_permissions_prefix, content),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun PluginDetailDialog(
    plugin: InstalledPlugin,
    onDismiss: () -> Unit
) {
    val manifest = plugin.manifest
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(manifest?.name?.ifBlank { plugin.state.id } ?: plugin.state.id)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PluginDetailField(stringResource(R.string.plugin_developer_plugin_id), plugin.state.id)
                PluginDetailField(stringResource(R.string.plugin_developer_plugin_version), plugin.state.version)
                PluginDetailField(
                    stringResource(R.string.plugin_enabled),
                    if (plugin.state.enabled) stringResource(R.string.plugin_state_enabled) else stringResource(R.string.plugin_state_disabled)
                )
                PluginDetailField(
                    stringResource(R.string.plugin_developer_plugin_author),
                    manifest?.author.orEmpty().ifBlank { "-" }
                )
                PluginDetailField(
                    stringResource(R.string.plugin_developer_plugin_description),
                    manifest?.description.orEmpty().ifBlank { "-" }
                )
                PluginPermissionSummary(
                    permissions = manifest?.permissions.orEmpty(),
                    emptyText = stringResource(R.string.plugin_permissions_none)
                )
                PluginDetailField(
                    stringResource(R.string.plugin_detail_entry),
                    describePluginEntry(manifest?.entry)
                )
                PluginDetailField(stringResource(R.string.plugin_detail_source_url), plugin.state.sourceUrl)
                PluginDetailField(stringResource(R.string.plugin_detail_install_dir), plugin.state.installDir)
                PluginDetailField(stringResource(R.string.plugin_detail_manifest_path), plugin.state.manifestPath)
                PluginDetailField(stringResource(R.string.plugin_detail_sha256), plugin.state.sha256)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_ok))
            }
        }
    )
}

@Composable
private fun PluginDetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun describePluginEntry(entry: PluginEntry?): String {
    entry ?: return "-"
    val parts = buildList {
        entry.script?.takeIf { it.isNotBlank() }?.let { add("script: $it") }
        entry.page?.let { add("page(${it.type}): ${it.path}") }
        entry.terminal?.let { add("terminal: ${it.command}") }
    }
    return parts.joinToString("\n").ifBlank { "-" }
}

private fun pluginPermissionLabel(permission: String): Int? {
    return when (permission) {
        PluginRuntime.Permission.FILE_READ.key -> R.string.plugin_permission_file_read
        PluginRuntime.Permission.FILE_WRITE.key -> R.string.plugin_permission_file_write
        PluginRuntime.Permission.NETWORK.key -> R.string.plugin_permission_network
        PluginRuntime.Permission.PROCESS.key -> R.string.plugin_permission_process
        PluginRuntime.Permission.R2.key -> R.string.plugin_permission_r2
        PluginRuntime.Permission.PROOT.key -> R.string.plugin_permission_proot
        PluginRuntime.Permission.TERMINAL.key -> R.string.plugin_permission_terminal
        PluginRuntime.Permission.FRIDA.key -> R.string.plugin_permission_frida
        PluginRuntime.Permission.SETTINGS_READ.key -> R.string.plugin_permission_settings_read
        PluginRuntime.Permission.SETTINGS_WRITE.key -> R.string.plugin_permission_settings_write
        PluginRuntime.Permission.SAF_PICKER.key -> R.string.plugin_permission_saf_picker
        PluginRuntime.Permission.PROJECT_READ.key -> R.string.plugin_permission_project_read
        PluginRuntime.Permission.ANDROID_CLASS.key -> R.string.plugin_permission_android_class
        else -> null
    }
}

@Composable
private fun SourceManageTab(
    repositories: List<String>,
    sourceInput: String,
    onSourceInputChange: (String) -> Unit,
    isWorking: Boolean,
    developerConfig: PluginDeveloperConfig,
    onAdd: () -> Unit,
    onEdit: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onResetDefault: () -> Unit,
    onInstallZip: () -> Unit,
    onSetDeveloperMode: (Boolean) -> Unit,
    onPickDeveloperWorkspace: () -> Unit,
    onCreateDeveloperPlugin: (DeveloperPluginCreateRequest) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingRepo by remember { mutableStateOf<String?>(null) }
    var editingSourceInput by remember { mutableStateOf("") }
    var pendingRemoveRepo by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.plugin_repositories_title),
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = sourceInput,
                    onValueChange = onSourceInputChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.plugin_add_source_hint)) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAdd,
                    enabled = sourceInput.isNotBlank() && !isWorking
                ) {
                    Text(stringResource(R.string.plugin_add_source))
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onInstallZip,
                    enabled = !isWorking
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.plugin_install_zip))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onResetDefault,
                    enabled = !isWorking
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.plugin_restore_default_sources))
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.plugin_developer_mode),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = developerConfig.enabled,
                            onCheckedChange = onSetDeveloperMode,
                            enabled = !isWorking
                        )
                    }

                    if (developerConfig.enabled) {
                        Text(
                            text = developerConfig.workspaceDir
                                ?: stringResource(R.string.plugin_developer_workspace_empty),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onPickDeveloperWorkspace,
                                enabled = !isWorking
                            ) {
                                Text(stringResource(R.string.plugin_developer_select_workspace))
                            }
                            Button(
                                onClick = { showCreateDialog = true },
                                enabled = !isWorking && !developerConfig.workspaceDir.isNullOrBlank()
                            ) {
                                Text(stringResource(R.string.plugin_developer_create_plugin))
                            }
                        }
                    }
                }
            }
        }

        items(repositories, key = { "repo_$it" }) { repo ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = repo,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row {
                        IconButton(
                            onClick = {
                                editingRepo = repo
                                editingSourceInput = repo
                            },
                            enabled = !isWorking
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.plugin_edit_source))
                        }
                        IconButton(onClick = { pendingRemoveRepo = repo }, enabled = !isWorking) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.plugin_remove_source))
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateDeveloperPluginDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { request ->
                onCreateDeveloperPlugin(request)
                showCreateDialog = false
            }
        )
    }

    val currentEditingRepo = editingRepo
    if (currentEditingRepo != null) {
        AlertDialog(
            onDismissRequest = {
                editingRepo = null
                editingSourceInput = ""
            },
            title = { Text(stringResource(R.string.plugin_edit_source)) },
            text = {
                OutlinedTextField(
                    value = editingSourceInput,
                    onValueChange = { editingSourceInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.plugin_add_source_hint)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEdit(currentEditingRepo, editingSourceInput)
                        editingRepo = null
                        editingSourceInput = ""
                    },
                    enabled = editingSourceInput.isNotBlank() && !isWorking
                ) {
                    Text(stringResource(R.string.plugin_developer_create_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    editingRepo = null
                    editingSourceInput = ""
                }) {
                    Text(stringResource(R.string.plugin_developer_create_cancel))
                }
            }
        )
    }

    val currentRemoveRepo = pendingRemoveRepo
    if (currentRemoveRepo != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoveRepo = null },
            title = { Text(stringResource(R.string.plugin_remove_source)) },
            text = { Text(currentRemoveRepo) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(currentRemoveRepo)
                        pendingRemoveRepo = null
                    },
                    enabled = !isWorking
                ) {
                    Text(stringResource(R.string.plugin_remove_source))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveRepo = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun CreateDeveloperPluginDialog(
    onDismiss: () -> Unit,
    onConfirm: (DeveloperPluginCreateRequest) -> Unit
) {
    var type by remember { mutableStateOf(DeveloperPluginType.WEBVIEW) }
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0.0") }
    var description by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val selectedPermissions = remember {
        mutableStateMapOf<String, Boolean>().apply {
            PluginRuntime.Permission.entries.forEach { put(it.key, false) }
        }
    }

    val typeLabel = when (type) {
        DeveloperPluginType.WEBVIEW -> stringResource(R.string.plugin_developer_type_webview)
        DeveloperPluginType.SCHEMA -> stringResource(R.string.plugin_developer_type_schema)
        DeveloperPluginType.TERMINAL -> stringResource(R.string.plugin_developer_type_terminal)
        DeveloperPluginType.NATIVE -> stringResource(R.string.plugin_developer_type_native)
    }

    val canConfirm = id.isNotBlank() && name.isNotBlank() && version.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_developer_create_plugin)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.plugin_developer_plugin_type, typeLabel),
                    style = MaterialTheme.typography.bodyMedium
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(typeLabel)
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        DeveloperPluginType.entries.forEach { candidate ->
                            val candidateLabel = when (candidate) {
                                DeveloperPluginType.WEBVIEW -> stringResource(R.string.plugin_developer_type_webview)
                                DeveloperPluginType.SCHEMA -> stringResource(R.string.plugin_developer_type_schema)
                                DeveloperPluginType.TERMINAL -> stringResource(R.string.plugin_developer_type_terminal)
                                DeveloperPluginType.NATIVE -> stringResource(R.string.plugin_developer_type_native)
                            }
                            DropdownMenuItem(
                                text = { Text(candidateLabel) },
                                onClick = {
                                    type = candidate
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.plugin_developer_plugin_id)) }
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.plugin_developer_plugin_name)) }
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.plugin_developer_plugin_version)) }
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.plugin_developer_plugin_author)) }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.plugin_developer_plugin_description)) }
                )

                Text(
                    text = stringResource(R.string.plugin_developer_plugin_permissions),
                    style = MaterialTheme.typography.titleSmall
                )
                PluginRuntime.Permission.entries.forEach { permission ->
                    val checked = selectedPermissions[permission.key] == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPermissions[permission.key] = !checked },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selectedPermissions[permission.key] = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pluginPermissionLabel(permission.key)
                                    ?.let { stringResource(it) }
                                    ?: permission.key,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = permission.key,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        DeveloperPluginCreateRequest(
                            type = type,
                            id = id,
                            name = name,
                            version = version,
                            description = description,
                            author = author,
                            permissions = PluginRuntime.Permission.entries
                                .mapNotNull { permission ->
                                    permission.key.takeIf { selectedPermissions[it] == true }
                                }
                        )
                    )
                },
                enabled = canConfirm
            ) {
                Text(stringResource(R.string.plugin_developer_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.plugin_developer_create_cancel))
            }
        }
    )
}

@Composable
private fun LogsTab(
    status: String?,
    logs: List<String>,
) {
    val (titleStr, icon, color) = when {
        status == null -> Triple(stringResource(R.string.plugin_status_title_idle), Icons.Default.Info, MaterialTheme.colorScheme.onSurface)
        status.contains("fail", ignoreCase = true) || status.contains("error", ignoreCase = true) || status.contains("错误") -> 
            Triple(stringResource(R.string.plugin_status_title_error), Icons.Default.Error, MaterialTheme.colorScheme.error)
        status.endsWith("...") || status.endsWith("…") || status.contains("ing") || status.contains("中") -> 
            Triple(stringResource(R.string.plugin_status_title_processing), Icons.Default.Pending, MaterialTheme.colorScheme.primary)
        else -> 
            Triple(stringResource(R.string.plugin_status_title_success), Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = titleStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        Text(
                            text = status ?: stringResource(R.string.common_idle),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.plugin_logs_title),
                    style = MaterialTheme.typography.titleMedium
                )
//                TextButton(onClick = onClear) {
//                    Text(stringResource(R.string.common_clear))
//                }
            }
        }

        val recentLogs = logs.takeLast(120)
        items(recentLogs.size, key = { idx -> "log_$idx" }) { idx ->
            val line = recentLogs[idx]
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

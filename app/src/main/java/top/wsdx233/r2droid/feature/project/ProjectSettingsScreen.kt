package top.wsdx233.r2droid.feature.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.wsdx233.r2droid.core.data.model.SavedProject
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.manual.R2ManualScreen
import top.wsdx233.r2droid.feature.r2frida.R2FridaViewModel
import top.wsdx233.r2droid.feature.r2frida.StaticProjectLoadState
import top.wsdx233.r2droid.feature.r2frida.data.FridaMapping
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.util.TerminalLauncher
import kotlinx.coroutines.launch

@Composable
fun ProjectSettingsScreen(
    viewModel: ProjectViewModel,
    r2fridaViewModel: R2FridaViewModel,
    onReconnect: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveState by viewModel.saveProjectState.collectAsState()
    val exportBinaryState by viewModel.exportBinaryState.collectAsState()
    val reconnectState by viewModel.reconnectState.collectAsState()
    val staticProjectLoadState by r2fridaViewModel.staticProjectLoadState.collectAsState()
    val fridaMappings by r2fridaViewModel.mappings.collectAsState()
    val isFridaSession = R2PipeManager.isR2FridaSession
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showLoadStaticProjectDialog by remember { mutableStateOf(false) }
    var savedProjects by remember { mutableStateOf<List<SavedProject>>(emptyList()) }
    var loadingSavedProjects by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showExportReport by remember { mutableStateOf(false) }
    var showAnalysis by remember { mutableStateOf(false) }
    var showSwitchArch by remember { mutableStateOf(false) }

    val binaryExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.onEvent(ProjectEvent.ExportBinaryCopy(it)) }
    }

    if (showExportReport) {
        ExportReportScreen(onDismiss = { showExportReport = false })
    }
    if (showAnalysis) {
        AnalysisBottomSheet(onDismiss = { showAnalysis = false })
    }
    if (showSwitchArch) {
        SwitchArchBottomSheet(onDismiss = { showSwitchArch = false })
    }

    if (showLoadStaticProjectDialog) {
        LoadStaticProjectDialog(
            mappings = fridaMappings ?: emptyList(),
            loading = staticProjectLoadState is StaticProjectLoadState.Loading,
            projects = savedProjects,
            projectsLoading = loadingSavedProjects,
            onDismiss = { showLoadStaticProjectDialog = false },
            onLoad = { projectFilePath, moduleBaseHex ->
                r2fridaViewModel.loadRebasedStaticProject(projectFilePath, moduleBaseHex)
            }
        )
    }

    // Manual dialog
    if (showManual) {
        Dialog(
            onDismissRequest = { showManual = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            R2ManualScreen(
                onBack = { showManual = false },
                reconnecting = reconnectState is ReconnectState.Reconnecting,
                onReconnect = onReconnect
            )
        }
    }

    // Initialize repository
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.initializeSavedProjectRepository(context)
    }
    
    // Handle save state changes
    androidx.compose.runtime.LaunchedEffect(saveState) {
        when (saveState) {
            is SaveProjectState.Success -> {
                android.widget.Toast.makeText(
                    context, 
                    (saveState as SaveProjectState.Success).message, 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.onEvent(ProjectEvent.ResetSaveState)
            }
            is SaveProjectState.Error -> {
                android.widget.Toast.makeText(
                    context, 
                    (saveState as SaveProjectState.Error).message, 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.onEvent(ProjectEvent.ResetSaveState)
            }
            else -> {}
        }
    }

    androidx.compose.runtime.LaunchedEffect(exportBinaryState) {
        when (exportBinaryState) {
            is ExportBinaryState.Success -> {
                android.widget.Toast.makeText(
                    context,
                    (exportBinaryState as ExportBinaryState.Success).message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.resetExportBinaryState()
            }
            is ExportBinaryState.Error -> {
                android.widget.Toast.makeText(
                    context,
                    (exportBinaryState as ExportBinaryState.Error).message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.resetExportBinaryState()
            }
            else -> Unit
        }
    }

    androidx.compose.runtime.LaunchedEffect(reconnectState) {
        when (reconnectState) {
            is ReconnectState.Success -> {
                android.widget.Toast.makeText(
                    context,
                    (reconnectState as ReconnectState.Success).message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.onEvent(ProjectEvent.ResetReconnectState)
            }
            is ReconnectState.Error -> {
                android.widget.Toast.makeText(
                    context,
                    (reconnectState as ReconnectState.Error).message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.onEvent(ProjectEvent.ResetReconnectState)
            }
            else -> Unit
        }
    }

    androidx.compose.runtime.LaunchedEffect(staticProjectLoadState) {
        when (val state = staticProjectLoadState) {
            is StaticProjectLoadState.Success -> {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.frida_load_static_project_success, state.replacedCount),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                showLoadStaticProjectDialog = false
                r2fridaViewModel.resetStaticProjectLoadState()
            }
            is StaticProjectLoadState.Error -> {
                android.widget.Toast.makeText(
                    context,
                    state.message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                r2fridaViewModel.resetStaticProjectLoadState()
            }
            else -> Unit
        }
    }
    
    // Save dialog
    if (showSaveDialog && !isFridaSession) {
        SaveProjectDialog(
            existingProjectId = viewModel.getCurrentProjectId(),
            onDismiss = { showSaveDialog = false },
            onSaveNew = { name, copyBinary ->
                viewModel.onEvent(ProjectEvent.SaveProject(name, copyBinary = copyBinary))
                showSaveDialog = false
            },
            onUpdate = { projectId ->
                viewModel.onEvent(ProjectEvent.UpdateProject(projectId))
                showSaveDialog = false
            }
        )
    }

    // Save as dialog: always creates a new saved project
    if (showSaveAsDialog && !isFridaSession) {
        SaveProjectDialog(
            existingProjectId = null,
            onDismiss = { showSaveAsDialog = false },
            onSaveNew = { name, copyBinary ->
                viewModel.onEvent(ProjectEvent.SaveProject(name, copyBinary = copyBinary))
                showSaveAsDialog = false
            },
            onUpdate = {}
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // File Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.proj_info_current_file),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = R2PipeManager.currentFilePath ?: stringResource(R.string.proj_info_no_file),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                if (R2PipeManager.currentProjectId != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.proj_info_project_id, R2PipeManager.currentProjectId ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        if (!isFridaSession) {
            // Save Project Section
            Text(
                text = stringResource(top.wsdx233.r2droid.R.string.project_save),
                style = MaterialTheme.typography.titleMedium
            )

            ProjectActionCard(
                title = if (R2PipeManager.currentProjectId != null)
                    stringResource(R.string.proj_save_update_title)
                else
                    stringResource(top.wsdx233.r2droid.R.string.project_save_title),
                description = if (R2PipeManager.currentProjectId != null)
                    stringResource(R.string.proj_save_update_desc)
                else
                    stringResource(R.string.proj_save_new_desc),
                icon = Icons.Default.Build,
                loading = saveState is SaveProjectState.Saving,
                enabled = saveState !is SaveProjectState.Saving,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { showSaveDialog = true }
            )

            ProjectActionCard(
                title = stringResource(R.string.proj_save_as_title),
                description = stringResource(R.string.proj_save_as_desc),
                icon = Icons.Default.ContentCopy,
                loading = saveState is SaveProjectState.Saving,
                enabled = saveState !is SaveProjectState.Saving,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { showSaveAsDialog = true }
            )

            val currentBinaryPath = R2PipeManager.currentFilePath
            ProjectActionCard(
                title = stringResource(R.string.proj_export_binary_title),
                description = stringResource(R.string.proj_export_binary_desc),
                icon = Icons.Default.Download,
                loading = exportBinaryState is ExportBinaryState.Exporting,
                enabled = currentBinaryPath != null && exportBinaryState !is ExportBinaryState.Exporting,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {
                    val name = currentBinaryPath?.let { java.io.File(it).name.ifBlank { "binary" } } ?: "binary"
                    binaryExportLauncher.launch(name)
                }
            )
        } else {
            Text(
                text = stringResource(R.string.frida_load_static_project_title),
                style = MaterialTheme.typography.titleMedium
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = staticProjectLoadState !is StaticProjectLoadState.Loading) {
                        r2fridaViewModel.loadMappings(force = true)
                        scope.launch {
                            loadingSavedProjects = true
                            savedProjects = viewModel.getAllSavedProjects()
                            loadingSavedProjects = false
                            showLoadStaticProjectDialog = true
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (staticProjectLoadState is StaticProjectLoadState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.frida_load_static_project_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.frida_load_static_project_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        
        // Startup Flags Section (for saved projects)
        if (!isFridaSession && R2PipeManager.currentProjectId != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(top.wsdx233.r2droid.R.string.project_startup_flags),
                style = MaterialTheme.typography.titleMedium
            )
            
            var startupFlags by remember { mutableStateOf("") }
            
            OutlinedTextField(
                value = startupFlags,
                onValueChange = { startupFlags = it },
                label = { Text(stringResource(top.wsdx233.r2droid.R.string.project_startup_flags_hint)) },
                placeholder = { Text("-w -n") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Text(
                text = stringResource(top.wsdx233.r2droid.R.string.project_startup_flags_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Analysis Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAnalysis = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.proj_analysis_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.proj_analysis_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Terminal Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { TerminalLauncher.start(context) },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(top.wsdx233.r2droid.R.string.terminal),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(R.string.proj_term_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Export Report Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showExportReport = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.export_report_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(R.string.export_report_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Switch Arch Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSwitchArch = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Architecture,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.switch_arch_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.switch_arch_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Manual Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showManual = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.manual_open),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.manual_open_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Session Info
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.proj_session_info),
            style = MaterialTheme.typography.titleMedium
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.proj_session_status), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (R2PipeManager.isConnected) stringResource(R.string.proj_session_connected) else stringResource(R.string.proj_session_disconnected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (R2PipeManager.isConnected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.proj_session_saved), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (R2PipeManager.currentProjectId != null) stringResource(R.string.common_yes) else stringResource(R.string.common_no),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (R2PipeManager.currentProjectId != null) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = reconnectState !is ReconnectState.Reconnecting, onClick = onReconnect),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (reconnectState is ReconnectState.Reconnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.proj_reconnect_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(R.string.proj_reconnect_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    loading: Boolean = false,
    enabled: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(8.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = if (enabled) 0.7f else 0.45f)
                )
            }
        }
    }
}

@Composable
private fun LoadStaticProjectDialog(
    mappings: List<FridaMapping>,
    loading: Boolean,
    projects: List<SavedProject>,
    projectsLoading: Boolean,
    onDismiss: () -> Unit,
    onLoad: (projectFilePath: String, moduleBaseHex: String) -> Unit
) {
    var selectedMappingBase by remember { mutableStateOf<String?>(null) }
    var selectedMappingLabel by remember { mutableStateOf("") }
    var selectedProjectPath by remember { mutableStateOf<String?>(null) }
    var selectedProjectLabel by remember { mutableStateOf("") }
    var showMappingPickerDialog by remember { mutableStateOf(false) }
    var showProjectPickerDialog by remember { mutableStateOf(false) }
    var mappingSearchQuery by remember { mutableStateOf("") }
    var projectSearchQuery by remember { mutableStateOf("") }

    val filteredMappings = remember(mappings, mappingSearchQuery) {
        if (mappingSearchQuery.isBlank()) {
            mappings
        } else {
            mappings.filter { mapping ->
                val target = "${mapping.filePath ?: ""} ${mapping.base} ${mapping.protection}".lowercase()
                target.contains(mappingSearchQuery.lowercase())
            }
        }
    }

    val filteredProjects = remember(projects, projectSearchQuery) {
        if (projectSearchQuery.isBlank()) {
            projects
        } else {
            projects.filter { project ->
                val target = "${project.name} ${project.binaryPath} ${project.scriptPath}".lowercase()
                target.contains(projectSearchQuery.lowercase())
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(mappings) {
        if (selectedMappingBase == null && mappings.isNotEmpty()) {
            val first = mappings.first()
            selectedMappingBase = first.base
            selectedMappingLabel = first.filePath ?: first.base
        }
    }

    if (showMappingPickerDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMappingPickerDialog = false },
            title = { Text(stringResource(R.string.frida_load_static_project_mapping_label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mappingSearchQuery,
                        onValueChange = { mappingSearchQuery = it },
                        label = { Text(stringResource(R.string.common_search)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        items(filteredMappings, key = { "${it.base}_${it.filePath ?: ""}" }) { mapping ->
                            val label = buildString {
                                append(mapping.filePath ?: stringResource(R.string.frida_load_static_project_mapping_unknown_file))
                                append(" @ ")
                                append(mapping.base)
                            }
                            Text(
                                text = label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedMappingBase = mapping.base
                                        selectedMappingLabel = label
                                        showMappingPickerDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMappingPickerDialog = false }) {
                    Text(stringResource(R.string.fsearch_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMappingPickerDialog = false }) {
                    Text(stringResource(R.string.fsearch_cancel))
                }
            }
        )
    }

    if (showProjectPickerDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showProjectPickerDialog = false },
            title = { Text(stringResource(R.string.frida_load_static_project_select_project)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = projectSearchQuery,
                        onValueChange = { projectSearchQuery = it },
                        label = { Text(stringResource(R.string.common_search)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (projectsLoading) {
                        CircularProgressIndicator()
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                        ) {
                            items(filteredProjects, key = { it.id }) { project ->
                                val label = "${project.name} (${project.getFormattedLastModified()})"
                                Text(
                                    text = label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedProjectPath = project.scriptPath
                                            selectedProjectLabel = label
                                            showProjectPickerDialog = false
                                        }
                                        .padding(vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProjectPickerDialog = false }) {
                    Text(stringResource(R.string.fsearch_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showProjectPickerDialog = false }) {
                    Text(stringResource(R.string.fsearch_cancel))
                }
            }
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.frida_load_static_project_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = if (selectedMappingLabel.isBlank()) stringResource(R.string.frida_load_static_project_select_mapping) else selectedMappingLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.frida_load_static_project_mapping_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        disabledIndicatorColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                androidx.compose.material3.Button(
                    onClick = { showMappingPickerDialog = true },
                    enabled = mappings.isNotEmpty() && !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.frida_load_static_project_select_mapping))
                }

                OutlinedTextField(
                    value = selectedProjectLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.frida_load_static_project_file_label)) },
                    placeholder = { Text(stringResource(R.string.frida_load_static_project_select_project)) },
                    modifier = Modifier.fillMaxWidth()
                )

                androidx.compose.material3.Button(
                    onClick = { showProjectPickerDialog = true },
                    enabled = !loading && !projectsLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.frida_load_static_project_choose_from_projects))
                }
            }
        },
        confirmButton = {
                TextButton(
                    onClick = {
                        val filePath = selectedProjectPath
                        val base = selectedMappingBase
                        if (!filePath.isNullOrBlank() && !base.isNullOrBlank()) {
                            projectSearchQuery = ""
                            mappingSearchQuery = ""
                            onLoad(filePath, base)
                        }
                    },
                enabled = !loading && !selectedProjectPath.isNullOrBlank() && !selectedMappingBase.isNullOrBlank()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(stringResource(R.string.frida_load_static_project_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.home_delete_cancel))
            }
        }
    )
}

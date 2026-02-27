package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.manual.R2ManualScreen
import top.wsdx233.r2droid.util.R2PipeManager

@Composable
fun ProjectSettingsScreen(viewModel: ProjectViewModel) {
    val context = LocalContext.current
    val saveState by viewModel.saveProjectState.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showExportReport by remember { mutableStateOf(false) }
    var showAnalysis by remember { mutableStateOf(false) }
    var showSwitchArch by remember { mutableStateOf(false) }

    if (showExportReport) {
        ExportReportScreen(onDismiss = { showExportReport = false })
    }
    if (showAnalysis) {
        AnalysisBottomSheet(onDismiss = { showAnalysis = false })
    }
    if (showSwitchArch) {
        SwitchArchBottomSheet(onDismiss = { showSwitchArch = false })
    }

    // Manual dialog
    if (showManual) {
        Dialog(
            onDismissRequest = { showManual = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            R2ManualScreen(onBack = { showManual = false })
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
    
    // Save dialog
    if (showSaveDialog) {
        SaveProjectDialog(
            existingProjectId = viewModel.getCurrentProjectId(),
            onDismiss = { showSaveDialog = false },
            onSaveNew = { name ->
                viewModel.onEvent(ProjectEvent.SaveProject(name))
                showSaveDialog = false
            },
            onUpdate = { projectId ->
                viewModel.onEvent(ProjectEvent.UpdateProject(projectId))
                showSaveDialog = false
            }
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
        
        // Save Project Section
        Text(
            text = stringResource(top.wsdx233.r2droid.R.string.project_save),
            style = MaterialTheme.typography.titleMedium
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = saveState !is SaveProjectState.Saving) { 
                    showSaveDialog = true 
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
                if (saveState is SaveProjectState.Saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (R2PipeManager.currentProjectId != null) 
                            stringResource(R.string.proj_save_update_title) 
                        else 
                            stringResource(top.wsdx233.r2droid.R.string.project_save_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (R2PipeManager.currentProjectId != null)
                            stringResource(R.string.proj_save_update_desc)
                        else
                            stringResource(R.string.proj_save_new_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Startup Flags Section (for saved projects)
        if (R2PipeManager.currentProjectId != null) {
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
                .clickable {
                    val intent = android.content.Intent(context, top.wsdx233.r2droid.activity.TerminalActivity::class.java)
                    context.startActivity(intent)
                },
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
    }
}

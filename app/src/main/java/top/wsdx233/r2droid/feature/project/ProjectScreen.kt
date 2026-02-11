package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: ProjectViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val saveState by viewModel.saveProjectState.collectAsState()

    // State for exit confirmation dialog
    var showExitDialog by remember { mutableStateOf(false) }
    var showSaveBeforeExitDialog by remember { mutableStateOf(false) }
    var exitProjectName by remember { mutableStateOf("") }

    // Initialize intent
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.onEvent(ProjectEvent.Initialize)
    }

    // Handle project restoration
    androidx.compose.runtime.LaunchedEffect(uiState) {
        if (uiState is ProjectUiState.Analyzing && R2PipeManager.pendingRestoreFlags != null) {
            viewModel.onEvent(ProjectEvent.StartRestoreSession)
        }
    }

    // Handle back press with save confirmation
    androidx.activity.compose.BackHandler(
        enabled = uiState is ProjectUiState.Success && R2PipeManager.currentProjectId == null
    ) {
        showExitDialog = true
    }

    // Handle save completion when exiting
    androidx.compose.runtime.LaunchedEffect(saveState) {
        if (showSaveBeforeExitDialog && saveState is SaveProjectState.Success) {
            showSaveBeforeExitDialog = false
            onNavigateBack()
        }
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(top.wsdx233.r2droid.R.string.project_exit_title)) },
            text = { Text(stringResource(top.wsdx233.r2droid.R.string.project_exit_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        showSaveBeforeExitDialog = true
                        exitProjectName = R2PipeManager.currentFilePath?.let {
                            java.io.File(it).name
                        } ?: "Project"
                    }
                ) {
                    Text(stringResource(top.wsdx233.r2droid.R.string.project_exit_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(top.wsdx233.r2droid.R.string.project_exit_discard))
                }
            }
        )
    }

    // Save before exit dialog
    if (showSaveBeforeExitDialog) {
        AlertDialog(
            onDismissRequest = { showSaveBeforeExitDialog = false },
            title = { Text(stringResource(top.wsdx233.r2droid.R.string.project_save_title)) },
            text = {
                OutlinedTextField(
                    value = exitProjectName,
                    onValueChange = { exitProjectName = it },
                    label = { Text(stringResource(top.wsdx233.r2droid.R.string.project_save_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(ProjectEvent.SaveProject(exitProjectName))
                    },
                    enabled = saveState !is SaveProjectState.Saving
                ) {
                    if (saveState is SaveProjectState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(stringResource(top.wsdx233.r2droid.R.string.project_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveBeforeExitDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(top.wsdx233.r2droid.R.string.home_delete_cancel))
                }
            }
        )
    }

    when (val state = uiState) {
        is ProjectUiState.Configuring -> {
            AnalysisConfigScreen(
                filePath = state.filePath,
                onStartAnalysis = { cmd, writable, flags ->
                    viewModel.onEvent(ProjectEvent.StartAnalysisSession(cmd, writable, flags))
                }
            )
        }
        is ProjectUiState.Analyzing -> {
            AnalysisProgressScreen(logs = logs)
        }
        else -> {
            ProjectScaffold(
                viewModel = viewModel,
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

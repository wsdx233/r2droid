package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager

@Composable
fun SaveProjectDialog(
    existingProjectId: String?,
    onDismiss: () -> Unit,
    onSaveNew: (name: String, copyBinary: Boolean) -> Unit,
    onUpdate: (projectId: String) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var saveAsNew by remember { mutableStateOf(existingProjectId == null) }
    var copyBinary by remember { mutableStateOf(false) }
    val session = R2PipeManager.activeSession()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(top.wsdx233.r2droid.R.string.project_save_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).focusable(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (existingProjectId != null) {
                    // Options for existing project
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveAsNew = false }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !saveAsNew,
                            onClick = { saveAsNew = false }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.dialog_save_update_option))
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveAsNew = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = saveAsNew,
                            onClick = { saveAsNew = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.dialog_save_new_option))
                    }
                }
                
                if (saveAsNew || existingProjectId == null) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text(stringResource(top.wsdx233.r2droid.R.string.project_save_name_hint)) },
                        placeholder = { 
                            val fileName = R2PipeManager.currentFilePath?.let { 
                                java.io.File(it).name 
                            } ?: "Project"
                            Text(fileName)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { copyBinary = !copyBinary }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = copyBinary,
                            onCheckedChange = { copyBinary = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.analysis_copy_binary))
                            Text(
                                text = stringResource(R.string.project_save_copy_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (copyBinary || session?.currentProjectId != null) {
                        Text(
                            text = stringResource(R.string.project_save_separate_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (session?.writeCacheEnabled == true) {
                    Text(
                        text = stringResource(R.string.project_save_cache_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (saveAsNew || existingProjectId == null) {
                        val name = projectName.ifBlank {
                            R2PipeManager.currentFilePath?.let { java.io.File(it).name } ?: "Project"
                        }
                        onSaveNew(name, copyBinary)
                    } else {
                        onUpdate(existingProjectId)
                    }
                }
            ) {
                Text(stringResource(top.wsdx233.r2droid.R.string.project_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(top.wsdx233.r2droid.R.string.home_delete_cancel))
            }
        }
    )
}

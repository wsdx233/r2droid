package top.wsdx233.r2droid.core.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jeziellago.compose.markdowntext.MarkdownText
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.UpdateInfo
import top.wsdx233.r2droid.util.UpdateManager

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onDisableAutoCheck: () -> Unit
) {
    val context = LocalContext.current
    val showDisableConfirm = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.update_available_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(
                        R.string.update_version_info,
                        updateInfo.currentVersion,
                        updateInfo.latestVersion
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!updateInfo.releaseNotes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.update_release_notes),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownText(
                        markdown = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        syntaxHighlightColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showDisableConfirm.value = true }) {
                    Text(stringResource(R.string.update_disable_auto_check))
                }
                TextButton(
                    onClick = {
                        val uri = Uri.parse(updateInfo.downloadUrl.trim())
                        val scheme = uri.scheme?.lowercase()
                        if (scheme == "https" || scheme == "http") {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    context,
                                    it.message ?: "No browser available",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.update_download))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        }
    )

    if (showDisableConfirm.value) {
        AlertDialog(
            onDismissRequest = { showDisableConfirm.value = false },
            title = { Text(stringResource(R.string.update_disable_confirm_title)) },
            text = { Text(stringResource(R.string.update_disable_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisableConfirm.value = false
                        onDisableAutoCheck()
                    }
                ) {
                    Text(stringResource(R.string.update_disable_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableConfirm.value = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}

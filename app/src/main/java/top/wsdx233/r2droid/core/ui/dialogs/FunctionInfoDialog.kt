package top.wsdx233.r2droid.core.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.FunctionDetailInfo

@Composable
fun FunctionInfoDialog(
    functionInfo: FunctionDetailInfo?,
    isLoading: Boolean,
    targetAddress: Long,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onJump: (Long) -> Unit
) {
    var isRenaming by remember { mutableStateOf(false) }
    var newName by remember(functionInfo?.name) { mutableStateOf(functionInfo?.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${stringResource(R.string.func_info_title)} @ 0x${targetAddress.toString(16).uppercase()}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center)
                        )
                    }
                    functionInfo == null -> {
                        Text(
                            text = stringResource(R.string.func_info_no_function),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        FunctionInfoContent(
                            info = functionInfo,
                            isRenaming = isRenaming,
                            newName = newName,
                            onNewNameChange = { newName = it },
                            onStartRename = { isRenaming = true },
                            onConfirmRename = {
                                if (newName.isNotBlank() && newName != functionInfo.name) {
                                    onRename(newName)
                                }
                                isRenaming = false
                            },
                            onCancelRename = {
                                newName = functionInfo.name
                                isRenaming = false
                            },
                            onJump = onJump
                        )
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

@Composable
private fun FunctionInfoContent(
    info: FunctionDetailInfo,
    isRenaming: Boolean,
    newName: String,
    onNewNameChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onJump: (Long) -> Unit
) {
    SelectionContainer {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Name row with rename support
        if (isRenaming) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = onNewNameChange,
                    label = { Text(stringResource(R.string.func_info_rename_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onConfirmRename) {
                    Text(stringResource(R.string.func_confirm))
                }
                TextButton(onClick = onCancelRename) {
                    Text(stringResource(R.string.func_cancel))
                }
            }
        } else {
            InfoRowWithAction(
                label = stringResource(R.string.func_info_name),
                value = info.name,
                action = {
                    IconButton(onClick = onStartRename, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.func_rename),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Address (clickable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onJump(info.addr) }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.func_info_address),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "0x${info.addr.toString(16).uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (info.signature.isNotBlank()) {
            InfoRow(stringResource(R.string.func_info_signature), info.signature)
        }
        InfoRow(stringResource(R.string.func_info_size), "${info.size} (real: ${info.realSize})")
        InfoRow(stringResource(R.string.func_info_range), "0x${info.minAddr.toString(16).uppercase()} - 0x${info.maxAddr.toString(16).uppercase()}")
        InfoRow(stringResource(R.string.func_info_call_type), info.callType)
        InfoRow(stringResource(R.string.func_info_basic_blocks), "${info.nbbs}")
        InfoRow(stringResource(R.string.func_info_instructions), "${info.ninstrs}")
        InfoRow(stringResource(R.string.func_info_edges), "${info.edges}")
        InfoRow(stringResource(R.string.func_info_complexity), "${info.cc}")
        InfoRow(stringResource(R.string.func_info_stack_frame), "${info.stackFrame}")
        InfoRow(stringResource(R.string.func_info_locals), "${info.nlocals}")
        InfoRow(stringResource(R.string.func_info_args), "${info.nargs}")
        InfoRow(stringResource(R.string.func_info_indegree), "${info.indegree}")
        InfoRow(stringResource(R.string.func_info_outdegree), "${info.outdegree}")
        InfoRow(stringResource(R.string.func_info_noreturn), if (info.noReturn) "Yes" else "No")
        InfoRow(stringResource(R.string.func_info_pure), if (info.isPure) "Yes" else "No")
    }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun InfoRowWithAction(
    label: String,
    value: String,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.6f)
        )
        action()
    }
}

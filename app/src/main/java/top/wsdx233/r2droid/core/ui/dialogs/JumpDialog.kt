package top.wsdx233.r2droid.core.ui.dialogs

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R

@Composable
fun JumpDialog(
    initialAddress: String,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit,
    onResolveExpression: suspend (String) -> Result<Long>
) {
    var text by remember { mutableStateOf(initialAddress) }
    var error by remember { mutableStateOf<String?>(null) }
    var isResolving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val title = stringResource(R.string.dialog_jump_title)
    val addressLabel = stringResource(R.string.dialog_jump_address_label)
    val addressHint = stringResource(R.string.dialog_jump_address_hint)
    val errorEmpty = stringResource(R.string.dialog_jump_error_empty)
    val errorInvalid = stringResource(R.string.dialog_jump_error_invalid)
    val errorResolve = stringResource(R.string.dialog_jump_error_resolve)
    val goLabel = stringResource(R.string.dialog_jump_go)
    val cancelLabel = stringResource(R.string.dialog_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.focusable()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = { Text(addressLabel) },
                    placeholder = { Text(addressHint) },
                    isError = error != null,
                    singleLine = true
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val input = text.trim()
                    if (input.isBlank()) {
                        error = errorEmpty
                        return@TextButton
                    }

                    scope.launch {
                        isResolving = true
                        error = null
                        try {
                            // Explicit 0x/0X prefix: parse directly as unsigned 64-bit hex address.
                            if (input.startsWith("0x", ignoreCase = true)) {
                                val parsed = input.drop(2).trim().toULongOrNull(16)?.toLong()
                                if (parsed != null) {
                                    onJump(parsed)
                                    onDismiss()
                                    return@launch
                                }
                            }

                            // Try resolving symbol/expression first so names like `add` or `dead` work properly.
                            val result = onResolveExpression(input)
                            if (result.isSuccess) {
                                onJump(result.getOrThrow())
                                onDismiss()
                                return@launch
                            }

                            // Fallback to bare unsigned hex address (e.g. "401000").
                            val bareHex = input.toULongOrNull(16)?.toLong()
                            if (bareHex != null) {
                                onJump(bareHex)
                                onDismiss()
                            } else {
                                error = errorResolve
                            }
                        } finally {
                            isResolving = false
                        }
                    }
                },
                enabled = !isResolving
            ) {
                Text(if (isResolving) stringResource(R.string.dialog_jump_resolving) else goLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        }
    )
}

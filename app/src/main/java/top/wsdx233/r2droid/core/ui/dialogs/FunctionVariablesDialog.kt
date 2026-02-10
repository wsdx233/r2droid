package top.wsdx233.r2droid.core.ui.dialogs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.FunctionVariable
import top.wsdx233.r2droid.core.data.model.FunctionVariablesData

@Composable
fun FunctionVariablesDialog(
    variables: FunctionVariablesData,
    isLoading: Boolean,
    targetAddress: Long,
    onDismiss: () -> Unit,
    onRename: (oldName: String, newName: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${stringResource(R.string.func_vars_title)} @ 0x${targetAddress.toString(16).uppercase()}",
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
                    variables.isEmpty -> {
                        Text(
                            text = stringResource(R.string.func_vars_no_found),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        FunctionVariablesContent(
                            variables = variables,
                            onRename = onRename
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
private fun FunctionVariablesContent(
    variables: FunctionVariablesData,
    onRename: (oldName: String, newName: String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        if (variables.reg.isNotEmpty()) {
            item {
                VariableSectionHeader(
                    text = "${stringResource(R.string.func_vars_section_reg)} (${variables.reg.size})",
                    color = Color(0xFFAB47BC)
                )
                Spacer(Modifier.height(4.dp))
            }
            items(variables.reg) { variable ->
                VariableItem(variable = variable, onRename = onRename)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (variables.sp.isNotEmpty()) {
            item {
                VariableSectionHeader(
                    text = "${stringResource(R.string.func_vars_section_sp)} (${variables.sp.size})",
                    color = Color(0xFF42A5F5)
                )
                Spacer(Modifier.height(4.dp))
            }
            items(variables.sp) { variable ->
                VariableItem(variable = variable, onRename = onRename)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (variables.bp.isNotEmpty()) {
            item {
                VariableSectionHeader(
                    text = "${stringResource(R.string.func_vars_section_bp)} (${variables.bp.size})",
                    color = Color(0xFF66BB6A)
                )
                Spacer(Modifier.height(4.dp))
            }
            items(variables.bp) { variable ->
                VariableItem(variable = variable, onRename = onRename)
            }
        }
    }
}

@Composable
private fun VariableSectionHeader(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun VariableItem(
    variable: FunctionVariable,
    onRename: (oldName: String, newName: String) -> Unit
) {
    var isRenaming by remember { mutableStateOf(false) }
    var newName by remember(variable.name) { mutableStateOf(variable.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        if (isRenaming) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.func_vars_rename_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName != variable.name) {
                        onRename(variable.name, newName)
                    }
                    isRenaming = false
                }) {
                    Text(stringResource(R.string.func_confirm), fontSize = 12.sp)
                }
                TextButton(onClick = {
                    newName = variable.name
                    isRenaming = false
                }) {
                    Text(stringResource(R.string.func_cancel), fontSize = 12.sp)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Variable name
                    Text(
                        text = variable.name,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    // Kind + Type
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val kindColor = if (variable.kind == "arg")
                            Color(0xFF66BB6A) else Color(0xFF42A5F5)
                        Surface(
                            color = kindColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = variable.kind,
                                fontSize = 10.sp,
                                color = kindColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = variable.type,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Rename button
                IconButton(
                    onClick = { isRenaming = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.func_rename),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

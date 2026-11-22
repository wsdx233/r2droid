package top.wsdx233.r2droid.core.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.FunctionVariable
import top.wsdx233.r2droid.core.data.model.FunctionVariablesData
import top.wsdx233.r2droid.core.ui.components.rememberHighlightedCCode
import top.wsdx233.r2droid.feature.types.data.LocalTypesSnapshot
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme

private enum class VarEditMode { NONE, RENAME, RETYPE }

@Composable
fun FunctionVariablesDialog(
    variables: FunctionVariablesData,
    isLoading: Boolean,
    targetAddress: Long,
    onDismiss: () -> Unit,
    onRename: (oldName: String, newName: String) -> Unit,
    onRetype: (varName: String, newType: String) -> Unit = { _, _ -> },
    typeSuggestions: List<String> = LocalTypesSnapshot.COMMON_C_TYPES,
    isMutating: Boolean = false,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onOpenTypesManager: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = { if (!isMutating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FunctionVariablesDialogCard(
            variables = variables,
            isLoading = isLoading,
            targetAddress = targetAddress,
            onDismiss = onDismiss,
            onRename = onRename,
            onRetype = onRetype,
            typeSuggestions = typeSuggestions,
            isMutating = isMutating,
            errorMessage = errorMessage,
            onDismissError = onDismissError,
            onOpenTypesManager = onOpenTypesManager,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
        )
    }
}

@Composable
internal fun FunctionVariablesDialogCard(
    variables: FunctionVariablesData,
    isLoading: Boolean,
    targetAddress: Long,
    onDismiss: () -> Unit,
    onRename: (oldName: String, newName: String) -> Unit,
    onRetype: (varName: String, newType: String) -> Unit = { _, _ -> },
    typeSuggestions: List<String> = LocalTypesSnapshot.COMMON_C_TYPES,
    isMutating: Boolean = false,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onOpenTypesManager: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val allVars = variables.all
    val argsCount = remember(allVars) { allVars.count { it.kind == "arg" || it.kind == "reg" } }
    val localsCount = remember(allVars) { (allVars.size - argsCount).coerceAtLeast(0) }
    var searchQuery by remember { mutableStateOf("") }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.func_vars_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "0x${targetAddress.toULong().toString(16).uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = LocalAppFont.current,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (!isLoading && !variables.isEmpty) {
                                Text(
                                    text = stringResource(R.string.func_vars_summary, argsCount, localsCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, enabled = !isMutating) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.func_close)
                        )
                    }
                }

                AnimatedVisibility(visible = isMutating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Error Banner
                AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissError,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Filter bar for larger functions
                if (!isLoading && allVars.size > 4) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.func_vars_filter_hint), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 460.dp)
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(40.dp)
                                    .align(Alignment.Center)
                            )
                        }
                        variables.isEmpty -> {
                            Text(
                                text = stringResource(R.string.func_vars_no_found),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            FunctionVariablesContent(
                                variables = variables,
                                query = searchQuery,
                                typeSuggestions = typeSuggestions,
                                busy = isMutating,
                                onRename = onRename,
                                onRetype = onRetype
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenTypesManager != null) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onOpenTypesManager()
                            },
                            enabled = !isMutating
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Category,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.func_vars_open_types))
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    TextButton(onClick = onDismiss, enabled = !isMutating) {
                        Text(stringResource(R.string.func_close))
                    }
                }
            }
    }
}

@Composable
private fun FunctionVariablesContent(
    variables: FunctionVariablesData,
    query: String,
    typeSuggestions: List<String>,
    busy: Boolean,
    onRename: (oldName: String, newName: String) -> Unit,
    onRetype: (varName: String, newType: String) -> Unit
) {
    val term = query.trim()
    fun List<FunctionVariable>.filtered(): List<FunctionVariable> =
        if (term.isEmpty()) this
        else filter {
            it.name.contains(term, ignoreCase = true) ||
                it.type.contains(term, ignoreCase = true) ||
                it.refDisplay.contains(term, ignoreCase = true)
        }

    val reg = remember(variables.reg, term) { variables.reg.filtered() }
    val sp = remember(variables.sp, term) { variables.sp.filtered() }
    val bp = remember(variables.bp, term) { variables.bp.filtered() }
    val isDark = LocalDarkTheme.current

    val regColor = if (isDark) Color(0xFFCE93D8) else Color(0xFF7B1FA2)
    val spColor = if (isDark) Color(0xFF64B5F6) else Color(0xFF1565C0)
    val bpColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

    if (reg.isEmpty() && sp.isEmpty() && bp.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.search_no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (reg.isNotEmpty()) {
            item(key = "hdr_reg") {
                VariableSectionHeader(
                    text = stringResource(R.string.func_vars_section_reg),
                    count = reg.size,
                    color = regColor
                )
            }
            items(reg, key = { "reg_${it.name}_${it.refDisplay}" }) { variable ->
                VariableItem(
                    variable = variable,
                    typeSuggestions = typeSuggestions,
                    busy = busy,
                    onRename = onRename,
                    onRetype = onRetype
                )
            }
        }

        if (sp.isNotEmpty()) {
            item(key = "hdr_sp") {
                if (reg.isNotEmpty()) Spacer(Modifier.height(4.dp))
                VariableSectionHeader(
                    text = stringResource(R.string.func_vars_section_sp),
                    count = sp.size,
                    color = spColor
                )
            }
            items(sp, key = { "sp_${it.name}_${it.refDisplay}" }) { variable ->
                VariableItem(
                    variable = variable,
                    typeSuggestions = typeSuggestions,
                    busy = busy,
                    onRename = onRename,
                    onRetype = onRetype
                )
            }
        }

        if (bp.isNotEmpty()) {
            item(key = "hdr_bp") {
                if (reg.isNotEmpty() || sp.isNotEmpty()) Spacer(Modifier.height(4.dp))
                VariableSectionHeader(
                    text = stringResource(R.string.func_vars_section_bp),
                    count = bp.size,
                    color = bpColor
                )
            }
            items(bp, key = { "bp_${it.name}_${it.refDisplay}" }) { variable ->
                VariableItem(
                    variable = variable,
                    typeSuggestions = typeSuggestions,
                    busy = busy,
                    onRename = onRename,
                    onRetype = onRetype
                )
            }
        }
    }
}

@Composable
private fun VariableSectionHeader(text: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = color.copy(alpha = 0.14f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = LocalAppFont.current,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VariableItem(
    variable: FunctionVariable,
    typeSuggestions: List<String>,
    busy: Boolean,
    onRename: (oldName: String, newName: String) -> Unit,
    onRetype: (varName: String, newType: String) -> Unit
) {
    var editMode by remember(variable.name, variable.type) { mutableStateOf(VarEditMode.NONE) }
    var newName by remember(variable.name) { mutableStateOf(variable.name) }
    var newType by remember(variable.type) { mutableStateOf(variable.type) }
    val isDark = LocalDarkTheme.current

    val kindColor = when (variable.kind) {
        "arg" -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        "reg" -> if (isDark) Color(0xFFCE93D8) else Color(0xFF7B1FA2)
        else -> if (isDark) Color(0xFF64B5F6) else Color(0xFF1565C0)
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (editMode != VarEditMode.NONE) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = BorderStroke(
            width = if (editMode != VarEditMode.NONE) 1.2.dp else 1.dp,
            color = if (editMode != VarEditMode.NONE) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top summary row (always visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = kindColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = variable.kind.uppercase().ifBlank { "VAR" },
                        fontSize = 10.sp,
                        color = kindColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = variable.name,
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (variable.refDisplay.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "@ ${variable.refDisplay}",
                            fontFamily = LocalAppFont.current,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Type pill + quick action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val highlightedType = rememberHighlightedCCode(variable.type.ifBlank { "unknown" })
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !busy) {
                            newType = variable.type
                            editMode = if (editMode == VarEditMode.RETYPE) VarEditMode.NONE else VarEditMode.RETYPE
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = highlightedType,
                            fontFamily = LocalAppFont.current,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Retype toggle button
                Surface(
                    onClick = {
                        newType = variable.type
                        editMode = if (editMode == VarEditMode.RETYPE) VarEditMode.NONE else VarEditMode.RETYPE
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(8.dp),
                    color = if (editMode == VarEditMode.RETYPE) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Text(
                        text = stringResource(R.string.func_vars_retype),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (editMode == VarEditMode.RETYPE) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                // Rename icon button
                IconButton(
                    onClick = {
                        newName = variable.name
                        editMode = if (editMode == VarEditMode.RENAME) VarEditMode.NONE else VarEditMode.RENAME
                    },
                    enabled = !busy,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.func_rename),
                        tint = if (editMode == VarEditMode.RENAME) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Inline Rename Editor
            if (editMode == VarEditMode.RENAME) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.func_vars_rename_hint)) },
                        singleLine = true,
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                newName = variable.name
                                editMode = VarEditMode.NONE
                            },
                            enabled = !busy
                        ) {
                            Text(stringResource(R.string.func_cancel))
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val trimmed = newName.trim()
                                if (trimmed.isNotEmpty() && trimmed != variable.name) {
                                    onRename(variable.name, trimmed)
                                }
                                editMode = VarEditMode.NONE
                            },
                            enabled = !busy && newName.trim().isNotEmpty() && newName.trim() != variable.name,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.func_confirm))
                        }
                    }
                }
            }

            // Inline Retype (afvt) Editor
            if (editMode == VarEditMode.RETYPE) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                val filteredSuggestions = remember(typeSuggestions, newType, variable.type) {
                    val needle = newType.trim()
                    val pool = typeSuggestions.ifEmpty { LocalTypesSnapshot.COMMON_C_TYPES }
                    if (needle.isEmpty() || needle.equals(variable.type.trim(), ignoreCase = true)) {
                        pool.filterNot { it.equals(needle, ignoreCase = true) }.take(18)
                    } else {
                        val matching = pool.filter { it.contains(needle, ignoreCase = true) && !it.equals(needle, ignoreCase = true) }
                        if (matching.isNotEmpty()) matching.take(16) else pool.take(12)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newType,
                        onValueChange = { newType = it },
                        label = { Text(stringResource(R.string.func_vars_retype_hint)) },
                        singleLine = true,
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        trailingIcon = {
                            if (newType.isNotEmpty()) {
                                IconButton(onClick = { newType = "" }, enabled = !busy) {
                                    Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Mobile-friendly modifier chips (*, const, unsigned, struct)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModifierQuickChip(label = "* ptr", enabled = !busy) {
                            val t = newType.trim()
                            newType = when {
                                t.isEmpty() -> "void *"
                                t.endsWith("*") -> "${t}*"
                                else -> "$t *"
                            }
                        }
                        ModifierQuickChip(label = "const", enabled = !busy) {
                            val t = newType.trim()
                            newType = if (t.startsWith("const ")) t.removePrefix("const ") else "const $t".trim()
                        }
                        ModifierQuickChip(label = "unsigned", enabled = !busy) {
                            val t = newType.trim()
                            newType = if (t.startsWith("unsigned ")) t.removePrefix("unsigned ") else "unsigned $t".trim()
                        }
                        ModifierQuickChip(label = "struct", enabled = !busy) {
                            val t = newType.trim()
                            newType = if (t.startsWith("struct ")) t.removePrefix("struct ") else "struct $t".trim()
                        }
                    }

                    // Instant filtered type suggestion strip (includes loaded structs/enums/typedefs)
                    if (filteredSuggestions.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.func_vars_quick_types),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredSuggestions, key = { it }) { suggestion ->
                                Surface(
                                    onClick = { newType = suggestion },
                                    enabled = !busy,
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = suggestion,
                                        fontFamily = LocalAppFont.current,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                newType = variable.type
                                editMode = VarEditMode.NONE
                            },
                            enabled = !busy
                        ) {
                            Text(stringResource(R.string.func_cancel))
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val trimmed = newType.trim()
                                if (trimmed.isNotEmpty() && trimmed != variable.type) {
                                    onRetype(variable.name, trimmed)
                                }
                                editMode = VarEditMode.NONE
                            },
                            enabled = !busy && newType.trim().isNotEmpty() && newType.trim() != variable.type,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.func_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModifierQuickChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Text(
            text = label,
            fontFamily = LocalAppFont.current,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

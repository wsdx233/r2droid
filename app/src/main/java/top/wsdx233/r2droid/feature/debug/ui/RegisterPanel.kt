package top.wsdx233.r2droid.feature.debug.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.debug.data.DebugRegister
import top.wsdx233.r2droid.feature.debug.data.RegisterRole
import top.wsdx233.r2droid.ui.theme.LocalAppFont

private enum class RegisterFilter {
    ALL,
    CHANGED,
    CORE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterBottomSheet(
    registers: List<DebugRegister>,
    autoShowRegisters: Boolean,
    onAutoShowRegistersChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    canEditRegisters: Boolean = false,
    onWriteRegister: (String, Long) -> Unit = { _, _ -> },
    onJumpToDisasm: (Long) -> Unit = {},
    onInspectMemory: (Long) -> Unit = {}
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = bottomSheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            RegisterPanelContent(
                registers = registers,
                autoShowRegisters = autoShowRegisters,
                onAutoShowRegistersChange = onAutoShowRegistersChange,
                canEditRegisters = canEditRegisters,
                onWriteRegister = onWriteRegister,
                onJumpToDisasm = { addr ->
                    onJumpToDisasm(addr)
                    onDismissRequest()
                },
                onInspectMemory = { addr ->
                    onInspectMemory(addr)
                    onDismissRequest()
                },
                gridMaxHeight = 380.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterPanelContent(
    registers: List<DebugRegister>,
    autoShowRegisters: Boolean,
    onAutoShowRegistersChange: (Boolean) -> Unit,
    canEditRegisters: Boolean = false,
    onWriteRegister: (String, Long) -> Unit = { _, _ -> },
    onJumpToDisasm: (Long) -> Unit = {},
    onInspectMemory: (Long) -> Unit = {},
    gridMaxHeight: androidx.compose.ui.unit.Dp = 280.dp,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(RegisterFilter.ALL) }
    val changedCount = remember(registers) { registers.count { it.changed } }
    val filteredRegisters = remember(registers, filter) {
        when (filter) {
            RegisterFilter.ALL -> registers
            RegisterFilter.CHANGED -> registers.filter { it.changed }
            RegisterFilter.CORE -> registers.filter {
                it.role in setOf(
                    RegisterRole.PC,
                    RegisterRole.SP,
                    RegisterRole.BP,
                    RegisterRole.FLAGS,
                    RegisterRole.GENERAL
                )
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.debug_panel_tab_registers),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (changedCount > 0) {
                    DebugBadgePill(
                        text = "+$changedCount",
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        monospace = true
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.debug_register_auto_show),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Switch(
                    checked = autoShowRegisters,
                    onCheckedChange = onAutoShowRegistersChange
                )
            }
        }

        if (registers.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DebugFilterPill(
                    label = stringResource(R.string.debug_register_filter_all),
                    selected = filter == RegisterFilter.ALL,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { filter = RegisterFilter.ALL }
                )
                DebugFilterPill(
                    label = stringResource(R.string.debug_register_filter_changed, changedCount),
                    selected = filter == RegisterFilter.CHANGED,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { filter = RegisterFilter.CHANGED }
                )
                DebugFilterPill(
                    label = stringResource(R.string.debug_register_filter_core),
                    selected = filter == RegisterFilter.CORE,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    onClick = { filter = RegisterFilter.CORE }
                )
            }
        }

        if (registers.isEmpty()) {
            DebugEmptyState(
                icon = Icons.Default.DeveloperBoard,
                message = stringResource(R.string.debug_register_empty)
            )
        } else if (filteredRegisters.isEmpty() && filter == RegisterFilter.CHANGED) {
            DebugEmptyState(
                icon = Icons.Default.DeveloperBoard,
                message = stringResource(R.string.debug_register_no_changed)
            )
        } else {
            RegisterGrid(
                registers = filteredRegisters,
                canEditRegisters = canEditRegisters,
                onWriteRegister = onWriteRegister,
                onJumpToDisasm = onJumpToDisasm,
                onInspectMemory = onInspectMemory,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = gridMaxHeight)
            )
        }
    }
}

@Composable
fun RegisterGrid(
    registers: List<DebugRegister>,
    modifier: Modifier = Modifier,
    canEditRegisters: Boolean = false,
    onWriteRegister: (String, Long) -> Unit = { _, _ -> },
    onJumpToDisasm: (Long) -> Unit = {},
    onInspectMemory: (Long) -> Unit = {}
) {
    var selectedRegister by remember { mutableStateOf<DebugRegister?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(registers, key = { it.name }) { register ->
            RegisterTile(
                register = register,
                onClick = { selectedRegister = register }
            )
        }
    }

    selectedRegister?.let { target ->
        RegisterActionDialog(
            register = target,
            canEditRegisters = canEditRegisters,
            onDismiss = { selectedRegister = null },
            onWriteRegister = { value ->
                onWriteRegister(target.name, value)
                selectedRegister = null
            },
            onJumpToDisasm = {
                onJumpToDisasm(target.value)
                selectedRegister = null
            },
            onInspectMemory = {
                onInspectMemory(target.value)
                selectedRegister = null
            }
        )
    }
}

@Composable
private fun RegisterTile(
    register: DebugRegister,
    onClick: () -> Unit
) {
    val changed = register.changed
    val containerColor = when {
        changed -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
        register.role == RegisterRole.PC -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = when {
        changed -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
        register.role == RegisterRole.PC -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
    val nameColor = when {
        changed -> MaterialTheme.colorScheme.onTertiaryContainer
        register.role == RegisterRole.PC -> MaterialTheme.colorScheme.primary
        register.role == RegisterRole.SP || register.role == RegisterRole.BP -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val valueColor = when {
        changed -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = register.name.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalAppFont.current,
                    fontSize = 12.sp,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (changed) {
                    DebugBadgePill(
                        text = stringResource(R.string.debug_register_changed_badge),
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                } else if (register.role in setOf(RegisterRole.PC, RegisterRole.SP, RegisterRole.BP, RegisterRole.FLAGS)) {
                    DebugBadgePill(
                        text = register.role.name,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        contentColor = nameColor
                    )
                }
            }
            Text(
                text = formatDebugHex(register.value, minDigits = 8),
                fontFamily = LocalAppFont.current,
                fontSize = 12.sp,
                fontWeight = if (changed) FontWeight.Bold else FontWeight.Medium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (changed && register.previousValue != null) {
                Text(
                    text = "← ${formatDebugHex(register.previousValue, minDigits = 8)}",
                    fontFamily = LocalAppFont.current,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterActionDialog(
    register: DebugRegister,
    canEditRegisters: Boolean,
    onDismiss: () -> Unit,
    onWriteRegister: (Long) -> Unit,
    onJumpToDisasm: () -> Unit,
    onInspectMemory: () -> Unit
) {
    val context = LocalContext.current
    val currentHex = remember(register) { formatDebugHex(register.value) }
    var input by remember(register) { mutableStateOf(currentHex) }
    var inputError by remember(register) { mutableStateOf<String?>(null) }
    val invalidMsg = stringResource(R.string.debug_register_edit_invalid)

    DebugDialogContainer(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = register.name.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.Bold
                )
                DebugBadgePill(
                    text = register.role.name,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (register.changed) {
                    DebugBadgePill(
                        text = stringResource(R.string.debug_register_changed_badge),
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }
        },
        content = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatDebugHex(register.value, minDigits = 8),
                        fontFamily = LocalAppFont.current,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.debug_register_decimal, register.value.toULong().toString()),
                        fontFamily = LocalAppFont.current,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (register.changed && register.previousValue != null) {
                        Text(
                            text = stringResource(
                                R.string.debug_register_previous,
                                formatDebugHex(register.previousValue, minDigits = 8)
                            ),
                            fontFamily = LocalAppFont.current,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = onJumpToDisasm,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.debug_action_jump_disasm), style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = onInspectMemory,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.debug_action_view_memory), style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = { copyDebugText(context, currentHex) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.debug_action_copy), style = MaterialTheme.typography.labelMedium)
                }
            }

            if (canEditRegisters) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        inputError = null
                    },
                    label = { Text(stringResource(R.string.debug_register_edit_label)) },
                    placeholder = { Text(stringResource(R.string.debug_register_edit_hint)) },
                    isError = inputError != null,
                    supportingText = inputError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = stringResource(R.string.debug_register_readonly_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            if (canEditRegisters) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val parsed = parseDebugUserLong(input)
                        if (parsed == null) {
                            inputError = invalidMsg
                        } else {
                            onWriteRegister(parsed)
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.debug_register_apply))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.func_close))
                }
            }
        }
    )
}

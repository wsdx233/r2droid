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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.debug.data.BreakpointKind
import top.wsdx233.r2droid.feature.debug.data.DebugBreakpoint
import top.wsdx233.r2droid.feature.debug.data.DebugCapabilities
import top.wsdx233.r2droid.feature.debug.data.WatchpointAccess
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreakpointPanel(
    breakpoints: List<DebugBreakpoint>,
    currentPc: Long?,
    capabilities: DebugCapabilities = DebugCapabilities(),
    onJumpToAddress: (Long) -> Unit,
    onInspectMemory: (Long) -> Unit = {},
    onAddBreakpoint: (String, BreakpointKind, String?, WatchpointAccess?) -> Unit = { _, _, _, _ -> },
    onRemoveBreakpoint: (Long) -> Unit,
    onToggleBreakpointEnabled: (Long, Boolean) -> Unit = { _, _ -> },
    onUpdateBreakpointCondition: (Long, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingConditionTarget by remember { mutableStateOf<DebugBreakpoint?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DebugSectionHeader(
            title = stringResource(R.string.debug_panel_breakpoints_title),
            count = breakpoints.size
        ) {
            if (capabilities.canUseBreakpoints) {
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.debug_bp_add),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (breakpoints.isEmpty()) {
            DebugEmptyState(
                icon = Icons.Default.LocationOn,
                message = stringResource(R.string.debug_panel_no_breakpoints)
            )
        } else {
            val sorted = remember(breakpoints) { breakpoints.sortedBy { it.address } }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted, key = { "${it.address}_${it.kind}_${it.access}" }) { bp ->
                    BreakpointCard(
                        breakpoint = bp,
                        isPc = currentPc == bp.address,
                        canEditCondition = capabilities.canUseConditionalBreakpoints,
                        onJump = {
                            if (bp.kind == BreakpointKind.WATCHPOINT) {
                                onInspectMemory(bp.address)
                            } else {
                                onJumpToAddress(bp.address)
                            }
                        },
                        onInspectMemory = { onInspectMemory(bp.address) },
                        onToggleEnabled = { enabled -> onToggleBreakpointEnabled(bp.address, enabled) },
                        onEditCondition = { editingConditionTarget = bp },
                        onRemove = { onRemoveBreakpoint(bp.address) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddBreakpointDialog(
            initialAddress = currentPc?.let { formatDebugHex(it) }.orEmpty(),
            capabilities = capabilities,
            onDismiss = { showAddDialog = false },
            onConfirm = { expr, kind, condition, access ->
                onAddBreakpoint(expr, kind, condition, access)
                showAddDialog = false
            }
        )
    }

    editingConditionTarget?.let { target ->
        EditBreakpointConditionDialog(
            breakpoint = target,
            onDismiss = { editingConditionTarget = null },
            onConfirm = { condition ->
                onUpdateBreakpointCondition(target.address, condition)
                editingConditionTarget = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakpointCard(
    breakpoint: DebugBreakpoint,
    isPc: Boolean,
    canEditCondition: Boolean,
    onJump: () -> Unit,
    onInspectMemory: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEditCondition: () -> Unit,
    onRemove: () -> Unit
) {
    val contentAlpha = if (breakpoint.enabled) 1f else 0.62f
    val containerColor = when {
        isPc -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        !breakpoint.enabled -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = when {
        isPc -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        breakpoint.kind == BreakpointKind.WATCHPOINT -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onJump),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when {
                        !breakpoint.enabled -> Icons.Default.RadioButtonUnchecked
                        breakpoint.kind == BreakpointKind.WATCHPOINT -> Icons.Default.Visibility
                        else -> Icons.Default.LocationOn
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = when {
                        !breakpoint.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        isPc -> MaterialTheme.colorScheme.primary
                        breakpoint.kind == BreakpointKind.WATCHPOINT -> MaterialTheme.colorScheme.tertiary
                        breakpoint.kind == BreakpointKind.HARDWARE -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(contentAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatDebugHex(breakpoint.address),
                        fontFamily = LocalAppFont.current,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    val (kindText, kindBg, kindFg) = when (breakpoint.kind) {
                        BreakpointKind.SOFTWARE -> Triple(
                            stringResource(R.string.debug_bp_badge_sw),
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.onErrorContainer
                        )
                        BreakpointKind.HARDWARE -> Triple(
                            stringResource(R.string.debug_bp_badge_hw),
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        BreakpointKind.WATCHPOINT -> Triple(
                            stringResource(
                                R.string.debug_bp_badge_wp,
                                (breakpoint.access ?: WatchpointAccess.WRITE).shortLabel
                            ),
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    DebugBadgePill(text = kindText, containerColor = kindBg, contentColor = kindFg, monospace = true)
                    if (isPc) {
                        DebugBadgePill(
                            text = stringResource(R.string.debug_panel_current_pc),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Switch(
                    checked = breakpoint.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            if (!breakpoint.condition.isNullOrBlank()) {
                Surface(
                    onClick = { if (canEditCondition) onEditCondition() },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "if: ${breakpoint.condition}",
                        fontFamily = LocalAppFont.current,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(contentAlpha),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (breakpoint.enabled) {
                            stringResource(R.string.debug_bp_enabled_short)
                        } else {
                            stringResource(R.string.debug_bp_disabled)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (breakpoint.hitCount > 0) {
                        Text(
                            text = "· ${stringResource(R.string.debug_bp_hits, breakpoint.hitCount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    breakpoint.backendId?.let { id ->
                        Text(
                            text = "· $id",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = LocalAppFont.current,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (breakpoint.kind == BreakpointKind.WATCHPOINT) {
                        IconButton(onClick = onInspectMemory, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = stringResource(R.string.debug_action_view_memory),
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    if (canEditCondition) {
                        IconButton(onClick = onEditCondition, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.debug_bp_edit_cond_desc),
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.debug_panel_breakpoint_remove),
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddBreakpointDialog(
    initialAddress: String,
    capabilities: DebugCapabilities,
    onDismiss: () -> Unit,
    onConfirm: (String, BreakpointKind, String?, WatchpointAccess?) -> Unit
) {
    var kind by remember { mutableStateOf(BreakpointKind.SOFTWARE) }
    var access by remember { mutableStateOf(WatchpointAccess.WRITE) }
    var addressExpr by remember { mutableStateOf(initialAddress) }
    var condition by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val emptyErr = stringResource(R.string.dialog_jump_error_empty)

    val canWatch = capabilities.canUseWatchpoints || capabilities.canUseHardwareBreakpoints

    DebugDialogContainer(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.debug_bp_add_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        content = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = kind == BreakpointKind.SOFTWARE,
                    onClick = { kind = BreakpointKind.SOFTWARE },
                    label = { Text(stringResource(R.string.debug_bp_kind_software)) }
                )
                FilterChip(
                    selected = kind == BreakpointKind.HARDWARE,
                    onClick = { kind = BreakpointKind.HARDWARE },
                    enabled = capabilities.canUseHardwareBreakpoints,
                    label = { Text(stringResource(R.string.debug_bp_kind_hardware)) }
                )
                FilterChip(
                    selected = kind == BreakpointKind.WATCHPOINT,
                    onClick = { kind = BreakpointKind.WATCHPOINT },
                    enabled = canWatch,
                    label = { Text(stringResource(R.string.debug_bp_kind_watchpoint)) }
                )
            }

            if (kind == BreakpointKind.WATCHPOINT) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.debug_bp_access_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = access == WatchpointAccess.READ,
                            onClick = { access = WatchpointAccess.READ },
                            label = { Text(stringResource(R.string.debug_bp_access_read)) }
                        )
                        FilterChip(
                            selected = access == WatchpointAccess.WRITE,
                            onClick = { access = WatchpointAccess.WRITE },
                            label = { Text(stringResource(R.string.debug_bp_access_write)) }
                        )
                        FilterChip(
                            selected = access == WatchpointAccess.READ_WRITE,
                            onClick = { access = WatchpointAccess.READ_WRITE },
                            label = { Text(stringResource(R.string.debug_bp_access_rw)) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = addressExpr,
                onValueChange = {
                    addressExpr = it
                    error = null
                },
                label = { Text(stringResource(R.string.debug_bp_address_label)) },
                placeholder = { Text(stringResource(R.string.debug_bp_address_hint)) },
                isError = error != null,
                supportingText = error?.let { msg -> { Text(msg) } },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (capabilities.canUseConditionalBreakpoints) {
                OutlinedTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = { Text(stringResource(R.string.debug_bp_condition_label)) },
                    placeholder = { Text(stringResource(R.string.debug_bp_condition_hint)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmed = addressExpr.trim()
                    if (trimmed.isEmpty()) {
                        error = emptyErr
                    } else {
                        onConfirm(
                            trimmed,
                            kind,
                            condition.trim().ifBlank { null },
                            if (kind == BreakpointKind.WATCHPOINT) access else null
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.debug_bp_add))
            }
        }
    )
}

@Composable
private fun EditBreakpointConditionDialog(
    breakpoint: DebugBreakpoint,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var text by remember(breakpoint) { mutableStateOf(breakpoint.condition.orEmpty()) }

    DebugDialogContainer(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.debug_bp_edit_condition_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatDebugHex(breakpoint.address),
                    fontFamily = LocalAppFont.current,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        content = {
            Text(
                text = stringResource(R.string.debug_bp_edit_condition_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.debug_bp_condition_label)) },
                placeholder = { Text(stringResource(R.string.debug_bp_condition_hint)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onConfirm(text.trim().ifBlank { null }) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.debug_register_apply))
            }
        }
    )
}

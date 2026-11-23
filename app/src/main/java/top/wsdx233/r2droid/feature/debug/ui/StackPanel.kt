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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.debug.data.DebugStackEntry
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@Composable
fun StackPanel(
    stackEntries: List<DebugStackEntry>,
    stackPointer: Long?,
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {},
    onJumpToDisasm: (Long) -> Unit = {},
    onInspectMemory: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedEntry by remember { mutableStateOf<DebugStackEntry?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DebugSectionHeader(title = stringResource(R.string.debug_panel_stack_title)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                stackPointer?.let { sp ->
                    Surface(
                        onClick = { onInspectMemory(sp) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "SP ${formatDebugHex(sp)}",
                            fontFamily = LocalAppFont.current,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading && stackPointer != null,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.common_refresh),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        when {
            stackPointer == null -> {
                DebugEmptyState(
                    icon = Icons.Default.ViewStream,
                    message = stringResource(R.string.debug_panel_stack_unavailable)
                )
            }
            isLoading && stackEntries.isEmpty() -> {
                DebugLoadingBox()
            }
            stackEntries.isEmpty() -> {
                DebugEmptyState(
                    icon = Icons.Default.ViewStream,
                    message = stringResource(R.string.debug_panel_stack_empty)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(stackEntries, key = { _, entry -> entry.address }) { index, entry ->
                        val offset = entry.address - stackPointer
                        val isTop = index == 0 || offset == 0L
                        val hintText = buildStackDerefSummary(entry)
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEntry = entry },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isTop) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isTop) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DebugBadgePill(
                                        text = if (offset == 0L) "SP" else "+0x${offset.toULong().toString(16).uppercase()}",
                                        containerColor = if (isTop) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        contentColor = if (isTop) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        monospace = true
                                    )
                                    Text(
                                        text = formatDebugHex(entry.address),
                                        fontFamily = LocalAppFont.current,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(0.45f)
                                    )
                                    Text(
                                        text = formatDebugHex(entry.value, minDigits = 8),
                                        fontFamily = LocalAppFont.current,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (hintText != null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(0.55f)
                                    )
                                }
                                if (hintText != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "→ $hintText",
                                            fontFamily = LocalAppFont.current,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        StackEntryActionDialog(
            entry = entry,
            onDismiss = { selectedEntry = null },
            onJumpToDisasm = { addr ->
                onJumpToDisasm(addr)
                selectedEntry = null
            },
            onInspectMemory = { addr ->
                onInspectMemory(addr)
                selectedEntry = null
            }
        )
    }
}

private fun buildStackDerefSummary(entry: DebugStackEntry): String? {
    val parts = buildList {
        entry.symbol?.takeIf { it.isNotBlank() }?.let { add(it) }
        entry.stringPreview?.takeIf { it.isNotBlank() }?.let { add("\"$it\"") }
        entry.reference?.takeIf { ref ->
            ref.isNotBlank() && none { existing -> ref.equals(existing, ignoreCase = true) }
        }?.let { ref ->
            if (isEmpty()) add(ref)
        }
    }
    return parts.joinToString(" · ").ifBlank { null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackEntryActionDialog(
    entry: DebugStackEntry,
    onDismiss: () -> Unit,
    onJumpToDisasm: (Long) -> Unit,
    onInspectMemory: (Long) -> Unit
) {
    val context = LocalContext.current
    val valueHex = formatDebugHex(entry.value)
    val derefSummary = buildStackDerefSummary(entry)

    DebugDialogContainer(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.debug_stack_slot_title, formatDebugHex(entry.address)),
                fontFamily = LocalAppFont.current,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
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
                        text = stringResource(R.string.debug_stack_value_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDebugHex(entry.value, minDigits = 8),
                        fontFamily = LocalAppFont.current,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.debug_register_decimal, entry.value.toULong().toString()),
                        fontFamily = LocalAppFont.current,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (derefSummary != null) {
                        Text(
                            text = "${stringResource(R.string.debug_stack_deref_label)}: $derefSummary",
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
                    onClick = { onJumpToDisasm(entry.value) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.debug_stack_jump_value_disasm), style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = { onInspectMemory(entry.value) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.debug_stack_view_value_mem), style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = { onInspectMemory(entry.address) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.debug_stack_view_slot_mem), style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = { copyDebugText(context, valueHex) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.debug_action_copy), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.func_close))
            }
        }
    )
}

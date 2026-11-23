package top.wsdx233.r2droid.feature.hex.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.hex.HexExportStatus
import top.wsdx233.r2droid.feature.hex.HexHashUiState
import top.wsdx233.r2droid.feature.hex.HexInspectorUiState
import top.wsdx233.r2droid.feature.hex.HexPanelTab
import top.wsdx233.r2droid.feature.hex.HexSelectionDataState
import top.wsdx233.r2droid.feature.hex.data.HexBase64DecodeResult
import top.wsdx233.r2droid.feature.hex.data.HexCopyFormat
import top.wsdx233.r2droid.feature.hex.data.HexEndianness
import top.wsdx233.r2droid.feature.hex.data.HexSelectionState
import top.wsdx233.r2droid.feature.hex.data.HexXorKeyMode
import top.wsdx233.r2droid.feature.hex.data.decodeBase64Bytes
import top.wsdx233.r2droid.feature.hex.data.encodeBase64Bytes
import top.wsdx233.r2droid.feature.hex.data.formatAsHexSpaced
import top.wsdx233.r2droid.feature.hex.data.formatHexAddressCompact
import top.wsdx233.r2droid.feature.hex.data.formatPrintableUtf8Preview
import top.wsdx233.r2droid.feature.hex.data.inspectHexBytes
import top.wsdx233.r2droid.feature.hex.data.parsePositiveByteLength
import top.wsdx233.r2droid.feature.hex.data.parseXorKey
import top.wsdx233.r2droid.feature.hex.data.xorDecode
import top.wsdx233.r2droid.ui.theme.LocalAppFont

private enum class DecodeMode {
    XOR,
    BASE64
}

@Composable
fun HexToolsPanel(
    modifier: Modifier = Modifier,
    selectedTab: HexPanelTab,
    onSelectTab: (HexPanelTab) -> Unit,
    cursorAddress: Long,
    selectionState: HexSelectionState,
    inspectorState: HexInspectorUiState,
    selectionDataState: HexSelectionDataState,
    hashState: HexHashUiState,
    exportStatus: HexExportStatus,
    viewStartAddress: Long,
    viewEndAddress: Long,
    onEndiannessChange: (HexEndianness) -> Unit,
    onPointerBitsChange: (Int) -> Unit,
    onRefreshDisasm: () -> Unit,
    onJumpToAddress: (Long) -> Unit,
    onStartSelection: (Long) -> Unit,
    onSetSelectionRange: (Long, Long) -> Unit,
    onClearSelection: () -> Unit,
    onCopyText: (String) -> Unit,
    onCopyFormatted: (HexCopyFormat) -> Unit,
    onRequestFillSelection: () -> Unit,
    onRequestExportBinary: (Long, Long) -> Unit,
    onComputeHashes: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val dragOffsetY = remember { Animatable(0f) }
    val dragScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var panelHeightPx by remember { mutableIntStateOf(0) }
    var lastDragDelta by remember { mutableFloatStateOf(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    var showCustomLengthDialog by remember { mutableStateOf(false) }

    val dragGestureModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { lastDragDelta = 0f },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                lastDragDelta = dragAmount
                val next = (dragOffsetY.value + dragAmount).coerceAtLeast(0f)
                dragScope.launch { dragOffsetY.snapTo(next) }
            },
            onDragEnd = {
                val maxThreshold = with(density) { 84.dp.toPx() }
                val minThreshold = with(density) { 48.dp.toPx() }
                val threshold = if (panelHeightPx > 0) {
                    minOf(maxThreshold, (panelHeightPx * 0.25f).coerceAtLeast(minThreshold))
                } else {
                    maxThreshold
                }
                val quickSwipe = dragOffsetY.value > with(density) { 20.dp.toPx() } && lastDragDelta > 12f
                if (dragOffsetY.value >= threshold || quickSwipe) {
                    currentOnDismiss()
                } else {
                    dragScope.launch {
                        dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                }
            },
            onDragCancel = {
                dragScope.launch {
                    dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
        )
    }

    val tabEntries = listOf(
        HexTabEntry(HexPanelTab.INSPECTOR, stringResource(R.string.hex_panel_tab_inspector), null),
        HexTabEntry(
            HexPanelTab.SELECTION,
            stringResource(R.string.hex_panel_tab_selection),
            if (selectionState.active) formatCompactByteCount(selectionState.byteCount) else null
        ),
        HexTabEntry(HexPanelTab.HASH, stringResource(R.string.hex_panel_tab_hash), null),
        HexTabEntry(HexPanelTab.DECODE, stringResource(R.string.hex_panel_tab_decode), null)
    )
    val selectedTabIndex = tabEntries.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { panelHeightPx = it.height }
            .graphicsLayer { translationY = dragOffsetY.value },
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HexToolsPanelHeader(
                cursorAddress = cursorAddress,
                selectionState = selectionState,
                onToggleSelection = {
                    if (selectionState.active) onClearSelection() else onStartSelection(cursorAddress)
                },
                onDismiss = onDismiss,
                dragModifier = dragGestureModifier
            )

            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            ) {
                tabEntries.forEachIndexed { index, entry ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { onSelectTab(entry.tab) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = entry.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                entry.badge?.let { badge ->
                                    HexBadgePill(
                                        text = badge,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        monospace = true
                                    )
                                }
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                HexPanelTab.INSPECTOR -> HexInspectorTab(
                    cursorAddress = cursorAddress,
                    inspectorState = inspectorState,
                    viewStartAddress = viewStartAddress,
                    viewEndAddress = viewEndAddress,
                    onEndiannessChange = onEndiannessChange,
                    onPointerBitsChange = onPointerBitsChange,
                    onRefreshDisasm = onRefreshDisasm,
                    onJumpToAddress = onJumpToAddress,
                    onSelectInstructionBytes = { size ->
                        if (size > 0) {
                            onSetSelectionRange(cursorAddress, size.toLong())
                        }
                    },
                    onCopyText = onCopyText
                )

                HexPanelTab.SELECTION -> HexSelectionTab(
                    cursorAddress = cursorAddress,
                    selectionState = selectionState,
                    selectionDataState = selectionDataState,
                    exportStatus = exportStatus,
                    onStartSelection = { onStartSelection(cursorAddress) },
                    onClearSelection = onClearSelection,
                    onQuickLength = { len ->
                        val base = if (selectionState.active) selectionState.rangeStart else cursorAddress
                        onSetSelectionRange(base, len)
                    },
                    onOpenCustomLength = { showCustomLengthDialog = true },
                    onCopyFormatted = onCopyFormatted,
                    onRequestFillSelection = onRequestFillSelection,
                    onRequestExportBinary = {
                        val start = if (selectionState.active) selectionState.rangeStart else cursorAddress
                        val count = if (selectionState.active) selectionState.byteCount else 1L
                        onRequestExportBinary(start, count)
                    }
                )

                HexPanelTab.HASH -> HexHashTab(
                    cursorAddress = cursorAddress,
                    selectionState = selectionState,
                    hashState = hashState,
                    onComputeHashes = onComputeHashes,
                    onCopyText = onCopyText
                )

                HexPanelTab.DECODE -> HexDecodeTab(
                    cursorAddress = cursorAddress,
                    selectionState = selectionState,
                    selectionDataState = selectionDataState,
                    onCopyText = onCopyText
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
    }

    if (showCustomLengthDialog) {
        HexCustomRangeDialog(
            baseAddress = if (selectionState.active) selectionState.rangeStart else cursorAddress,
            initialLength = if (selectionState.active) selectionState.byteCount else 16L,
            onDismiss = { showCustomLengthDialog = false },
            onConfirm = { length ->
                showCustomLengthDialog = false
                val base = if (selectionState.active) selectionState.rangeStart else cursorAddress
                onSetSelectionRange(base, length)
            }
        )
    }
}

private data class HexTabEntry(
    val tab: HexPanelTab,
    val title: String,
    val badge: String?
)

@Composable
private fun HexToolsPanelHeader(
    cursorAddress: Long,
    selectionState: HexSelectionState,
    onToggleSelection: () -> Unit,
    onDismiss: () -> Unit,
    dragModifier: Modifier
) {
    val dragHandleDesc = stringResource(R.string.hex_panel_drag_handle)
    val dotColor = if (selectionState.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
                .semantics { contentDescription = dragHandleDesc },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = stringResource(R.string.hex_panel_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val badgeText = if (selectionState.active) {
                    "${formatHexAddressCompact(selectionState.rangeStart)}..${formatHexAddressCompact(selectionState.rangeEnd)}"
                } else {
                    formatHexAddressCompact(cursorAddress)
                }
                HexBadgePill(
                    text = badgeText,
                    containerColor = if (selectionState.active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (selectionState.active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    monospace = true
                )
            }

            Surface(
                onClick = onToggleSelection,
                shape = RoundedCornerShape(8.dp),
                color = if (selectionState.active) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = BorderStroke(
                    1.dp,
                    if (selectionState.active) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (selectionState.active) Icons.Default.Close else Icons.Default.SelectAll,
                        contentDescription = null,
                        tint = if (selectionState.active) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(
                            if (selectionState.active) R.string.hex_selection_clear_short
                            else R.string.hex_selection_start_short
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectionState.active) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.hex_panel_collapse),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun HexInspectorTab(
    cursorAddress: Long,
    inspectorState: HexInspectorUiState,
    viewStartAddress: Long,
    viewEndAddress: Long,
    onEndiannessChange: (HexEndianness) -> Unit,
    onPointerBitsChange: (Int) -> Unit,
    onRefreshDisasm: () -> Unit,
    onJumpToAddress: (Long) -> Unit,
    onSelectInstructionBytes: (Int) -> Unit,
    onCopyText: (String) -> Unit
) {
    val snapshot = remember(
        cursorAddress,
        inspectorState.bytes,
        inspectorState.endianness,
        inspectorState.pointerBits
    ) {
        inspectHexBytes(
            address = cursorAddress,
            bytes = inspectorState.bytes,
            endianness = inspectorState.endianness,
            pointerBits = inspectorState.pointerBits
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 285.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Controls strip: LE / BE + 64-bit / 32-bit + raw byte binary
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HexFilterPill(
                    label = stringResource(R.string.hex_inspector_le),
                    selected = inspectorState.endianness == HexEndianness.LITTLE,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onEndiannessChange(HexEndianness.LITTLE) }
                )
                HexFilterPill(
                    label = stringResource(R.string.hex_inspector_be),
                    selected = inspectorState.endianness == HexEndianness.BIG,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onEndiannessChange(HexEndianness.BIG) }
                )
                HexFilterPill(
                    label = if (inspectorState.pointerBits == 32) "32-bit" else "64-bit",
                    selected = false,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { onPointerBitsChange(if (inspectorState.pointerBits == 32) 64 else 32) }
                )
            }

            if (snapshot.hexByte != null && snapshot.binaryByte != null) {
                Text(
                    text = "${snapshot.hexByte} · ${snapshot.binaryByte}",
                    fontSize = 11.sp,
                    fontFamily = LocalAppFont.current,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onCopyText(snapshot.binaryByte) }
                )
            }
        }

        if (inspectorState.isBytesLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Single Instruction Disassembly Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.hex_inspector_disasm),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val disasmSize = inspectorState.disasm?.size ?: 0
                        if (disasmSize > 0) {
                            HexBadgePill(
                                text = "${disasmSize}B",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                monospace = true
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val disasm = inspectorState.disasm
                        if (disasm != null && disasm.size > 0) {
                            Surface(
                                onClick = { onSelectInstructionBytes(disasm.size) },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = stringResource(R.string.hex_inspector_select_insn_bytes, disasm.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onRefreshDisasm,
                            enabled = !inspectorState.disasmLoading,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.common_refresh),
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                when {
                    inspectorState.disasmLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                text = stringResource(R.string.hex_inspector_disasm_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    inspectorState.disasmError != null -> {
                        Text(
                            text = inspectorState.disasmError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inspectorState.disasm != null -> {
                        val disasm = inspectorState.disasm
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCopyText(disasm.text) },
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = disasm.text,
                                fontFamily = LocalAppFont.current,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (disasm.formattedBytes.isNotEmpty()) {
                                Text(
                                    text = disasm.formattedBytes,
                                    fontFamily = LocalAppFont.current,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.hex_inspector_unavailable),
                            fontFamily = LocalAppFont.current,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Numeric types grid (2 columns)
        val unavailable = stringResource(R.string.hex_inspector_unavailable)
        InspectorRowPair(
            leftLabel = "int8", leftValue = snapshot.int8 ?: unavailable, leftCopy = snapshot.int8,
            rightLabel = "uint8", rightValue = snapshot.uint8 ?: unavailable, rightCopy = snapshot.uint8,
            onCopy = onCopyText
        )
        InspectorRowPair(
            leftLabel = "int16", leftValue = snapshot.int16 ?: unavailable, leftCopy = snapshot.int16,
            rightLabel = "uint16", rightValue = snapshot.uint16 ?: unavailable, rightCopy = snapshot.uint16,
            onCopy = onCopyText
        )
        InspectorRowPair(
            leftLabel = "int32", leftValue = snapshot.int32 ?: unavailable, leftCopy = snapshot.int32,
            rightLabel = "uint32", rightValue = snapshot.uint32 ?: unavailable, rightCopy = snapshot.uint32,
            onCopy = onCopyText
        )
        InspectorRowPair(
            leftLabel = "int64", leftValue = snapshot.int64 ?: unavailable, leftCopy = snapshot.int64,
            rightLabel = "uint64", rightValue = snapshot.uint64 ?: unavailable, rightCopy = snapshot.uint64,
            onCopy = onCopyText
        )
        InspectorRowPair(
            leftLabel = "float32", leftValue = snapshot.float32 ?: unavailable, leftCopy = snapshot.float32,
            rightLabel = "float64", rightValue = snapshot.float64 ?: unavailable, rightCopy = snapshot.float64,
            onCopy = onCopyText
        )

        // Pointer row with Jump action
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = snapshot.pointerDisplay != null) {
                        snapshot.pointerDisplay?.let(onCopyText)
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.hex_inspector_pointer_label, snapshot.pointerBits),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = snapshot.pointerDisplay ?: unavailable,
                        fontFamily = LocalAppFont.current,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val ptrVal = snapshot.pointerValue
                if (ptrVal != null) {
                    val inRange = viewEndAddress > viewStartAddress &&
                        ptrVal.toULong() >= viewStartAddress.toULong() &&
                        ptrVal.toULong() < viewEndAddress.toULong()
                    Surface(
                        onClick = { onJumpToAddress(ptrVal) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (inRange) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (inRange) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.hex_inspector_jump),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (inRange) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // UTF-8 / UTF-16 previews
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = snapshot.utf8Preview != null) {
                            snapshot.utf8Preview?.let(onCopyText)
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hex_inspector_utf8),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = snapshot.utf8Preview ?: unavailable,
                        fontFamily = LocalAppFont.current,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                val utf16Label = if (inspectorState.endianness == HexEndianness.LITTLE) {
                    stringResource(R.string.hex_inspector_utf16_le)
                } else {
                    stringResource(R.string.hex_inspector_utf16_be)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = snapshot.utf16Preview != null) {
                            snapshot.utf16Preview?.let(onCopyText)
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = utf16Label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = snapshot.utf16Preview ?: unavailable,
                        fontFamily = LocalAppFont.current,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorRowPair(
    leftLabel: String,
    leftValue: String,
    leftCopy: String?,
    rightLabel: String,
    rightValue: String,
    rightCopy: String?,
    onCopy: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        InspectorValueTile(
            label = leftLabel,
            value = leftValue,
            copyValue = leftCopy,
            onCopy = onCopy,
            modifier = Modifier.weight(1f)
        )
        InspectorValueTile(
            label = rightLabel,
            value = rightValue,
            copyValue = rightCopy,
            onCopy = onCopy,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InspectorValueTile(
    label: String,
    value: String,
    copyValue: String?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = copyValue != null) {
                    copyValue?.let(onCopy)
                }
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                fontFamily = LocalAppFont.current,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (copyValue != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HexSelectionTab(
    cursorAddress: Long,
    selectionState: HexSelectionState,
    selectionDataState: HexSelectionDataState,
    exportStatus: HexExportStatus,
    onStartSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onQuickLength: (Long) -> Unit,
    onOpenCustomLength: () -> Unit,
    onCopyFormatted: (HexCopyFormat) -> Unit,
    onRequestFillSelection: () -> Unit,
    onRequestExportBinary: () -> Unit
) {
    val startAddr = if (selectionState.active) selectionState.rangeStart else cursorAddress
    val endAddr = if (selectionState.active) selectionState.rangeEnd else cursorAddress
    val byteCount = if (selectionState.active) selectionState.byteCount else 1L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 285.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Range Summary Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (selectionState.active) {
                            "${formatHexAddressCompact(startAddr)} .. ${formatHexAddressCompact(endAddr)}"
                        } else {
                            stringResource(R.string.hex_selection_cursor_single, formatHexAddressCompact(cursorAddress))
                        },
                        fontFamily = LocalAppFont.current,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    HexBadgePill(
                        text = "$byteCount B (0x${byteCount.toULong().toString(16).uppercase()})",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        monospace = true
                    )
                }

                if (!selectionState.active) {
                    Text(
                        text = stringResource(R.string.hex_selection_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick selection length pills
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!selectionState.active) {
                HexFilterPill(
                    label = stringResource(R.string.hex_selection_start),
                    selected = false,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = onStartSelection
                )
            } else {
                HexFilterPill(
                    label = stringResource(R.string.hex_selection_clear),
                    selected = true,
                    accentColor = MaterialTheme.colorScheme.error,
                    onClick = onClearSelection
                )
            }
            listOf(4L, 8L, 16L, 32L, 64L, 256L).forEach { len ->
                HexFilterPill(
                    label = "${len} B",
                    selected = selectionState.active && selectionState.byteCount == len,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onQuickLength(len) }
                )
            }
            HexFilterPill(
                label = stringResource(R.string.hex_selection_custom_length),
                selected = false,
                accentColor = MaterialTheme.colorScheme.secondary,
                onClick = onOpenCustomLength
            )
        }

        // Copy format buttons
        Text(
            text = stringResource(R.string.hex_selection_copy_section),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (selectionDataState.exceedsPreviewLimit) {
            Text(
                text = stringResource(R.string.hex_selection_copy_limit_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HexActionTonalButton(
                        label = stringResource(R.string.hex_copy_hex),
                        onClick = { onCopyFormatted(HexCopyFormat.HEX_SPACED) },
                        enabled = !selectionDataState.isLoading,
                        modifier = Modifier.weight(1f)
                    )
                    HexActionTonalButton(
                        label = stringResource(R.string.hex_copy_hex_compact),
                        onClick = { onCopyFormatted(HexCopyFormat.HEX_COMPACT) },
                        enabled = !selectionDataState.isLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HexActionTonalButton(
                        label = stringResource(R.string.hex_copy_c_array),
                        onClick = { onCopyFormatted(HexCopyFormat.C_ARRAY) },
                        enabled = !selectionDataState.isLoading,
                        modifier = Modifier.weight(1f)
                    )
                    HexActionTonalButton(
                        label = stringResource(R.string.hex_copy_python_bytes),
                        onClick = { onCopyFormatted(HexCopyFormat.PYTHON_BYTES) },
                        enabled = !selectionDataState.isLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Export Binary (wt) & Fill Range
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isExporting = exportStatus is HexExportStatus.Exporting
            Button(
                onClick = onRequestExportBinary,
                enabled = !isExporting,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.hex_export_binary_wt),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onRequestFillSelection,
                enabled = selectionState.active && !selectionDataState.exceedsPreviewLimit,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.FormatPaint, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.multiselect_fill),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HexHashTab(
    cursorAddress: Long,
    selectionState: HexSelectionState,
    hashState: HexHashUiState,
    onComputeHashes: (Long, Long) -> Unit,
    onCopyText: (String) -> Unit
) {
    val startAddr = if (selectionState.active) selectionState.rangeStart else cursorAddress
    val endAddr = if (selectionState.active) selectionState.rangeEnd else cursorAddress
    val byteCount = if (selectionState.active) selectionState.byteCount else 1L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 285.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatHexAddressCompact(startAddr)}..${formatHexAddressCompact(endAddr)} · $byteCount B",
                fontFamily = LocalAppFont.current,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            hashState.result?.let { res ->
                TextButton(
                    onClick = {
                        val combined = "CRC32: ${res.crc32}\nMD5: ${res.md5}\nSHA256: ${res.sha256}"
                        onCopyText(combined)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.hex_hash_copy_all), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        when {
            hashState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            hashState.exceedsLimit -> {
                HexInfoBanner(text = stringResource(R.string.hex_hash_limit_exceeded))
            }
            hashState.requiresManualCompute -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HexInfoBanner(text = stringResource(R.string.hex_hash_large_prompt, formatCompactByteCount(byteCount)))
                    FilledTonalButton(
                        onClick = { onComputeHashes(startAddr, byteCount) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.hex_hash_compute_now))
                    }
                }
            }
            hashState.error != null -> {
                HexInfoBanner(
                    text = hashState.error,
                    isError = true
                )
            }
            hashState.result != null -> {
                val res = hashState.result
                HexHashRowCard(label = "CRC32", value = res.crc32, onCopy = onCopyText)
                HexHashRowCard(label = "MD5", value = res.md5, onCopy = onCopyText)
                HexHashRowCard(label = "SHA-256", value = res.sha256, onCopy = onCopyText)
            }
            else -> {
                HexInfoBanner(text = stringResource(R.string.hex_inspector_unavailable))
            }
        }
    }
}

@Composable
private fun HexHashRowCard(
    label: String,
    value: String,
    onCopy: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCopy(value) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = value,
                    fontFamily = LocalAppFont.current,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.multiselect_copy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun HexDecodeTab(
    cursorAddress: Long,
    selectionState: HexSelectionState,
    selectionDataState: HexSelectionDataState,
    onCopyText: (String) -> Unit
) {
    var decodeMode by remember { mutableStateOf(DecodeMode.XOR) }
    var xorKeyInput by remember { mutableStateOf("") }
    var xorKeyMode by remember { mutableStateOf(HexXorKeyMode.HEX) }

    val byteCount = if (selectionState.active) selectionState.byteCount else 1L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 285.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HexFilterPill(
                    label = stringResource(R.string.hex_decode_xor),
                    selected = decodeMode == DecodeMode.XOR,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { decodeMode = DecodeMode.XOR }
                )
                HexFilterPill(
                    label = stringResource(R.string.hex_decode_base64),
                    selected = decodeMode == DecodeMode.BASE64,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { decodeMode = DecodeMode.BASE64 }
                )
            }
            Text(
                text = "${formatHexAddressCompact(if (selectionState.active) selectionState.rangeStart else cursorAddress)} · $byteCount B",
                fontFamily = LocalAppFont.current,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        when {
            selectionDataState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            selectionDataState.exceedsPreviewLimit -> {
                HexInfoBanner(text = stringResource(R.string.hex_decode_limit_warning))
            }
            selectionDataState.error != null -> {
                HexInfoBanner(text = selectionDataState.error, isError = true)
            }
            decodeMode == DecodeMode.XOR -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = xorKeyInput,
                        onValueChange = { xorKeyInput = it },
                        placeholder = {
                            Text(
                                text = stringResource(
                                    if (xorKeyMode == HexXorKeyMode.HEX) R.string.hex_decode_xor_hex_hint
                                    else R.string.hex_decode_xor_ascii_hint
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalAppFont.current),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    HexFilterPill(
                        label = "Hex",
                        selected = xorKeyMode == HexXorKeyMode.HEX,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onClick = { xorKeyMode = HexXorKeyMode.HEX }
                    )
                    HexFilterPill(
                        label = "ASCII",
                        selected = xorKeyMode == HexXorKeyMode.ASCII,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onClick = { xorKeyMode = HexXorKeyMode.ASCII }
                    )
                }

                val parsedKey = remember(xorKeyInput, xorKeyMode) { parseXorKey(xorKeyInput, xorKeyMode) }
                when {
                    xorKeyInput.isBlank() -> {
                        HexInfoBanner(text = stringResource(R.string.hex_decode_xor_empty_hint))
                    }
                    parsedKey == null -> {
                        HexInfoBanner(
                            text = stringResource(R.string.hex_decode_xor_invalid_hex),
                            isError = true
                        )
                    }
                    else -> {
                        val preview = remember(selectionDataState.bytes, parsedKey) {
                            xorDecode(selectionDataState.bytes, parsedKey)
                        }
                        HexDecodedPreviewCard(
                            title = stringResource(R.string.hex_decode_xor_result, preview.bytes.size),
                            textPreview = preview.textPreview,
                            hexPreview = preview.hexPreview,
                            truncated = preview.truncated,
                            onCopyText = { onCopyText(formatPrintableUtf8Preview(preview.bytes, preview.bytes.size)) },
                            onCopyHex = { onCopyText(formatAsHexSpaced(preview.bytes)) }
                        )
                    }
                }
            }
            else -> {
                val b64Decoded = remember(selectionDataState.bytes) {
                    decodeBase64Bytes(selectionDataState.bytes)
                }
                when (b64Decoded) {
                    is HexBase64DecodeResult.Success -> {
                        val preview = b64Decoded.preview
                        HexDecodedPreviewCard(
                            title = stringResource(R.string.hex_decode_b64_result, preview.bytes.size),
                            textPreview = preview.textPreview,
                            hexPreview = preview.hexPreview,
                            truncated = preview.truncated,
                            onCopyText = { onCopyText(formatPrintableUtf8Preview(preview.bytes, preview.bytes.size)) },
                            onCopyHex = { onCopyText(formatAsHexSpaced(preview.bytes)) }
                        )
                    }
                    is HexBase64DecodeResult.Invalid -> {
                        HexInfoBanner(text = stringResource(R.string.hex_decode_b64_invalid))
                    }
                }

                // Base64 Encode preview of the selected raw bytes
                val encodedFull = remember(selectionDataState.bytes) {
                    encodeBase64Bytes(selectionDataState.bytes)
                }
                if (encodedFull.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCopyText(encodedFull) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.hex_decode_b64_encode),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.multiselect_copy),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = if (encodedFull.length > 256) encodedFull.take(256) + "…" else encodedFull,
                                fontFamily = LocalAppFont.current,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HexDecodedPreviewCard(
    title: String,
    textPreview: String,
    hexPreview: String,
    truncated: Boolean,
    onCopyText: () -> Unit,
    onCopyHex: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HexMiniCopyPill(label = stringResource(R.string.hex_decode_copy_text), onClick = onCopyText)
                    HexMiniCopyPill(label = stringResource(R.string.hex_decode_copy_hex), onClick = onCopyHex)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.hex_inspector_utf8),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = textPreview.ifEmpty { stringResource(R.string.hex_inspector_unavailable) },
                    fontFamily = LocalAppFont.current,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.hex_copy_hex),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = hexPreview.ifEmpty { stringResource(R.string.hex_inspector_unavailable) },
                    fontFamily = LocalAppFont.current,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (truncated) {
                Text(
                    text = stringResource(R.string.hex_decode_preview_truncated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HexCustomRangeDialog(
    baseAddress: Long,
    initialLength: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var lengthText by remember { mutableStateOf(initialLength.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hex_selection_custom_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.hex_selection_custom_dialog_start, formatHexAddressCompact(baseAddress)),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = LocalAppFont.current,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = {
                        lengthText = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.hex_selection_custom_dialog_label)) },
                    placeholder = { Text("e.g. 64 or 0x100") },
                    isError = isError,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = parsePositiveByteLength(lengthText)
                    if (parsed != null) {
                        onConfirm(parsed)
                    } else {
                        isError = true
                    }
                }
            ) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun HexMiniCopyPill(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun HexActionTonalButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
        modifier = modifier
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HexBadgePill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    monospace: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (monospace) LocalAppFont.current else null,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun HexFilterPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun HexInfoBanner(
    text: String,
    isError: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            1.dp,
            if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

internal fun formatCompactByteCount(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)}MB"
    bytes >= 1024L -> "${bytes / 1024L}KB"
    else -> "${bytes}B"
}

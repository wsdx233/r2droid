package top.wsdx233.r2droid.feature.debug.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import top.wsdx233.r2droid.feature.debug.data.BreakpointKind
import top.wsdx233.r2droid.feature.debug.data.DebugBackend
import top.wsdx233.r2droid.feature.debug.data.DebugBreakpoint
import top.wsdx233.r2droid.feature.debug.data.DebugCapabilities
import top.wsdx233.r2droid.feature.debug.data.DebugFrame
import top.wsdx233.r2droid.feature.debug.data.DebugRegister
import top.wsdx233.r2droid.feature.debug.data.DebugStackEntry
import top.wsdx233.r2droid.feature.debug.data.DebugStopReason
import top.wsdx233.r2droid.feature.debug.data.DebugStopReasonType
import top.wsdx233.r2droid.feature.debug.data.DebugThread
import top.wsdx233.r2droid.feature.debug.data.DebugTraceEntry
import top.wsdx233.r2droid.feature.debug.data.RegisterRole
import top.wsdx233.r2droid.feature.debug.data.WatchpointAccess
import top.wsdx233.r2droid.feature.disasm.DebugStatus
import top.wsdx233.r2droid.ui.theme.LocalAppFont

private const val TAB_CONTROLS = 0
private const val TAB_REGISTERS = 1
private const val TAB_BREAKPOINTS = 2
private const val TAB_STACK = 3
private const val TAB_MEMORY = 4
private const val TAB_FRAMES = 5
private const val TAB_THREADS = 6
private const val TAB_TRACE = 7

@Composable
fun DebugPanel(
    modifier: Modifier = Modifier,
    debugStatus: DebugStatus,
    debugBackend: DebugBackend,
    pcAddress: Long?,
    stopReason: DebugStopReason?,
    registers: List<DebugRegister>,
    breakpoints: List<DebugBreakpoint>,
    stackEntries: List<DebugStackEntry>,
    stackPointer: Long?,
    stackLoading: Boolean = false,
    memoryAddress: Long?,
    memoryBytes: ByteArray,
    memoryLoading: Boolean,
    memoryWriting: Boolean = false,
    memoryError: String?,
    frames: List<DebugFrame> = emptyList(),
    framesLoading: Boolean = false,
    framesError: String? = null,
    threads: List<DebugThread> = emptyList(),
    threadsLoading: Boolean = false,
    threadsError: String? = null,
    traceEntries: List<DebugTraceEntry>,
    capabilities: DebugCapabilities = DebugCapabilities(),
    autoShowRegisters: Boolean = false,
    onAutoShowRegistersChange: (Boolean) -> Unit = {},
    onStartDebugging: () -> Unit,
    onStepInto: () -> Unit,
    onStepOver: () -> Unit,
    onStepOut: () -> Unit = {},
    onContinue: () -> Unit,
    onPause: () -> Unit,
    onResetDebugging: () -> Unit,
    onStopDebugging: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit = {},
    onJumpToAddress: (Long) -> Unit,
    onWriteRegister: (String, Long) -> Unit = { _, _ -> },
    onAddBreakpoint: (String, BreakpointKind, String?, WatchpointAccess?) -> Unit = { _, _, _, _ -> },
    onRemoveBreakpoint: (Long) -> Unit,
    onToggleBreakpointEnabled: (Long, Boolean) -> Unit = { _, _ -> },
    onUpdateBreakpointCondition: (Long, String?) -> Unit = { _, _ -> },
    onRefreshStack: () -> Unit = {},
    onRefreshMemoryAtPc: () -> Unit,
    onRefreshMemoryAtSp: () -> Unit,
    onRefreshMemoryAtBp: () -> Unit = {},
    onQueryMemoryExpression: (String) -> Unit = {},
    onInspectMemoryAddress: (Long) -> Unit = {},
    onWriteMemory: (Long, ByteArray) -> Unit = { _, _ -> },
    onRefreshFrames: () -> Unit = {},
    onRefreshThreads: () -> Unit = {},
    onSelectThread: (String) -> Unit = {},
    onClearTrace: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(TAB_CONTROLS) }
    val changedRegisterCount = remember(registers) { registers.count { it.changed } }
    val hasBpRegister = remember(registers) { registers.any { it.role == RegisterRole.BP } }

    val inspectMemoryAndSwitch: (Long) -> Unit = { addr ->
        onInspectMemoryAddress(addr)
        selectedTab = TAB_MEMORY
    }

    val tabs = listOf(
        TabEntry(stringResource(R.string.debug_panel_tab_controls), null),
        TabEntry(
            stringResource(R.string.debug_panel_tab_registers),
            changedRegisterCount.takeIf { it > 0 }?.let { "+$it" }
        ),
        TabEntry(
            stringResource(R.string.debug_panel_tab_breakpoints),
            breakpoints.size.takeIf { it > 0 }?.toString()
        ),
        TabEntry(stringResource(R.string.debug_panel_tab_stack), null),
        TabEntry(stringResource(R.string.debug_panel_tab_memory), null),
        TabEntry(
            stringResource(R.string.debug_panel_tab_frames),
            frames.size.takeIf { it > 0 }?.toString()
        ),
        TabEntry(
            stringResource(R.string.debug_panel_tab_threads),
            threads.size.takeIf { it > 0 }?.toString()
        ),
        TabEntry(
            stringResource(R.string.debug_panel_tab_trace),
            traceEntries.size.takeIf { it > 0 }?.toString()
        )
    )

    val dragOffsetY = remember { Animatable(0f) }
    val dragScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var panelHeightPx by remember { mutableIntStateOf(0) }
    var lastDragDelta by remember { mutableFloatStateOf(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

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
            DebugPanelHeader(
                debugStatus = debugStatus,
                debugBackend = debugBackend,
                pcAddress = pcAddress,
                stopReason = stopReason,
                onJumpToPc = { pcAddress?.let(onJumpToAddress) },
                onSettings = onSettings,
                onDismiss = onDismiss,
                dragModifier = dragGestureModifier
            )

            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            ) {
                tabs.forEachIndexed { index, entry ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
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
                                    val isChangedRegs = index == TAB_REGISTERS
                                    DebugBadgePill(
                                        text = badge,
                                        containerColor = if (isChangedRegs) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        contentColor = if (isChangedRegs) {
                                            MaterialTheme.colorScheme.onTertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        monospace = true
                                    )
                                }
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                TAB_CONTROLS -> DebugControlsTab(
                    debugStatus = debugStatus,
                    capabilities = capabilities,
                    onStartDebugging = onStartDebugging,
                    onStepInto = onStepInto,
                    onStepOver = onStepOver,
                    onStepOut = onStepOut,
                    onContinue = onContinue,
                    onPause = onPause,
                    onResetDebugging = onResetDebugging,
                    onStopDebugging = onStopDebugging,
                    onSettings = onSettings
                )
                TAB_REGISTERS -> RegisterPanelContent(
                    registers = registers,
                    autoShowRegisters = autoShowRegisters,
                    onAutoShowRegistersChange = onAutoShowRegistersChange,
                    canEditRegisters = capabilities.canEditRegisters && debugStatus == DebugStatus.SUSPENDED,
                    onWriteRegister = onWriteRegister,
                    onJumpToDisasm = onJumpToAddress,
                    onInspectMemory = inspectMemoryAndSwitch,
                    gridMaxHeight = 270.dp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
                TAB_BREAKPOINTS -> BreakpointPanel(
                    breakpoints = breakpoints,
                    currentPc = pcAddress,
                    capabilities = capabilities,
                    onJumpToAddress = onJumpToAddress,
                    onInspectMemory = inspectMemoryAndSwitch,
                    onAddBreakpoint = onAddBreakpoint,
                    onRemoveBreakpoint = onRemoveBreakpoint,
                    onToggleBreakpointEnabled = onToggleBreakpointEnabled,
                    onUpdateBreakpointCondition = onUpdateBreakpointCondition
                )
                TAB_STACK -> StackPanel(
                    stackEntries = stackEntries,
                    stackPointer = stackPointer,
                    isLoading = stackLoading,
                    onRefresh = onRefreshStack,
                    onJumpToDisasm = onJumpToAddress,
                    onInspectMemory = inspectMemoryAndSwitch
                )
                TAB_MEMORY -> MemoryPanel(
                    address = memoryAddress,
                    bytes = memoryBytes,
                    isLoading = memoryLoading,
                    isWriting = memoryWriting,
                    error = memoryError,
                    canWriteMemory = capabilities.canWriteMemory && debugStatus == DebugStatus.SUSPENDED,
                    hasBp = hasBpRegister,
                    onRefreshPc = onRefreshMemoryAtPc,
                    onRefreshSp = onRefreshMemoryAtSp,
                    onRefreshBp = onRefreshMemoryAtBp,
                    onQueryExpression = onQueryMemoryExpression,
                    onReadAddress = onInspectMemoryAddress,
                    onWriteMemory = onWriteMemory
                )
                TAB_FRAMES -> FramesPanel(
                    frames = frames,
                    isLoading = framesLoading,
                    error = framesError,
                    isSupported = capabilities.canListFrames,
                    onRefresh = onRefreshFrames,
                    onJumpToAddress = onJumpToAddress,
                    onInspectMemory = inspectMemoryAndSwitch
                )
                TAB_THREADS -> ThreadsPanel(
                    threads = threads,
                    isLoading = threadsLoading,
                    error = threadsError,
                    isSupported = capabilities.canListThreads,
                    onRefresh = onRefreshThreads,
                    onSelectThread = onSelectThread,
                    onJumpToAddress = onJumpToAddress
                )
                TAB_TRACE -> TracePanel(
                    traceEntries = traceEntries,
                    onClearTrace = onClearTrace,
                    onJumpToAddress = onJumpToAddress
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
    }
}

private data class TabEntry(val title: String, val badge: String?)

@Composable
private fun DebugPanelHeader(
    debugStatus: DebugStatus,
    debugBackend: DebugBackend,
    pcAddress: Long?,
    stopReason: DebugStopReason?,
    onJumpToPc: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
    dragModifier: Modifier
) {
    val statusColor = when (debugStatus) {
        DebugStatus.SUSPENDED -> MaterialTheme.colorScheme.primary
        DebugStatus.RUNNING, DebugStatus.STARTING, DebugStatus.STOPPING -> MaterialTheme.colorScheme.tertiary
        DebugStatus.ERROR -> MaterialTheme.colorScheme.error
        DebugStatus.IDLE -> MaterialTheme.colorScheme.outline
    }
    val dragHandleDesc = stringResource(R.string.debug_panel_drag_handle)

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
                        .background(statusColor)
                )
                Text(
                    text = debugBackend.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DebugBadgePill(
                    text = debugStatus.name,
                    containerColor = statusColor.copy(alpha = 0.16f),
                    contentColor = statusColor,
                    monospace = true
                )
            }

            if (pcAddress != null) {
                Surface(
                    onClick = onJumpToPc,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.debug_panel_pc, formatDebugHex(pcAddress)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = LocalAppFont.current,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.debug_panel_pc, stringResource(R.string.debug_panel_no_pc)),
                    fontSize = 11.sp,
                    fontFamily = LocalAppFont.current,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onSettings,
                enabled = debugStatus == DebugStatus.IDLE || debugStatus == DebugStatus.ERROR,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.debug_panel_settings),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.debug_panel_collapse),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        stopReason?.let { reason ->
            DebugStopDetails(
                reason = reason,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun DebugStopDetails(
    reason: DebugStopReason,
    modifier: Modifier = Modifier
) {
    val isFault = reason.type in setOf(
        DebugStopReasonType.ESIL_ERROR,
        DebugStopReasonType.EXCEPTION,
        DebugStopReasonType.SIGNAL
    )
    val bgColor = if (isFault) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fgColor = if (isFault) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.debug_panel_stop_reason,
                    reason.signal?.let { "${reason.type.name} · $it" } ?: reason.type.name
                ),
                style = MaterialTheme.typography.labelSmall,
                color = fgColor,
                fontWeight = FontWeight.Bold
            )
            reason.address?.let {
                Text(
                    text = stringResource(R.string.debug_panel_stop_instruction, formatDebugHex(it)),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = LocalAppFont.current,
                    color = fgColor
                )
            }
            reason.accessAddress?.let {
                Text(
                    text = stringResource(R.string.debug_panel_stop_access, formatDebugHex(it)),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = LocalAppFont.current,
                    color = fgColor
                )
            }
            reason.message?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = fgColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebugControlsTab(
    debugStatus: DebugStatus,
    capabilities: DebugCapabilities,
    onStartDebugging: () -> Unit,
    onStepInto: () -> Unit,
    onStepOver: () -> Unit,
    onStepOut: () -> Unit,
    onContinue: () -> Unit,
    onPause: () -> Unit,
    onResetDebugging: () -> Unit,
    onStopDebugging: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (debugStatus) {
            DebugStatus.IDLE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onStartDebugging,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.debug_panel_start))
                    }
                    OutlinedButton(
                        onClick = onSettings,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.debug_panel_settings))
                    }
                }
            }
            DebugStatus.STARTING,
            DebugStatus.STOPPING -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = stringResource(R.string.debug_panel_progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DebugStatus.RUNNING -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onPause,
                        enabled = capabilities.canPause,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.debug_panel_pause))
                    }
                }
            }
            DebugStatus.ERROR -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = onResetDebugging, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.debug_panel_reset))
                    }
                    OutlinedButton(onClick = onStopDebugging, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.debug_panel_stop))
                    }
                    OutlinedButton(onClick = onSettings, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.debug_panel_settings))
                    }
                }
            }
            DebugStatus.SUSPENDED -> {
                // Primary execution controls in a balanced 2x2 grid: Continue / Step Into / Step Over / Step Out
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DebugControlActionButton(
                            icon = Icons.Default.PlayArrow,
                            label = stringResource(R.string.debug_panel_continue),
                            enabled = capabilities.canContinue,
                            primary = true,
                            onClick = onContinue,
                            modifier = Modifier.weight(1f)
                        )
                        DebugControlActionButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            label = stringResource(R.string.debug_panel_step_into),
                            enabled = capabilities.canStepInto,
                            onClick = onStepInto,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DebugControlActionButton(
                            icon = Icons.AutoMirrored.Filled.Redo,
                            label = stringResource(R.string.debug_panel_step_over),
                            enabled = capabilities.canStepOver,
                            onClick = onStepOver,
                            modifier = Modifier.weight(1f)
                        )
                        DebugControlActionButton(
                            icon = Icons.Default.KeyboardArrowUp,
                            label = stringResource(R.string.debug_panel_step_out),
                            enabled = capabilities.canStepOut,
                            onClick = onStepOut,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Lifecycle controls: Reset & Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onResetDebugging,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.debug_panel_reset), style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onStopDebugging,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.debug_panel_stop), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugControlActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = modifier
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = modifier
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

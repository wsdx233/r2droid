package top.wsdx233.r2droid.feature.debug.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.debug.data.DebugBackend
import top.wsdx233.r2droid.feature.debug.data.DebugCapabilities
import top.wsdx233.r2droid.feature.debug.data.DebugRegister
import top.wsdx233.r2droid.feature.debug.data.DebugStackEntry
import top.wsdx233.r2droid.feature.debug.data.DebugStopReason
import top.wsdx233.r2droid.feature.debug.data.DebugStopReasonType
import top.wsdx233.r2droid.feature.debug.data.DebugTraceEntry
import top.wsdx233.r2droid.feature.disasm.DebugStatus
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@Composable
fun DebugPanel(
    modifier: Modifier = Modifier,
    debugStatus: DebugStatus,
    debugBackend: DebugBackend,
    pcAddress: Long?,
    stopReason: DebugStopReason?,
    registers: List<DebugRegister>,
    breakpoints: Set<Long>,
    stackEntries: List<DebugStackEntry>,
    stackPointer: Long?,
    memoryAddress: Long?,
    memoryBytes: ByteArray,
    memoryLoading: Boolean,
    memoryError: String?,
    traceEntries: List<DebugTraceEntry>,
    capabilities: DebugCapabilities = DebugCapabilities(),
    autoShowRegisters: Boolean = false,
    onAutoShowRegistersChange: (Boolean) -> Unit = {},
    onStartDebugging: () -> Unit,
    onStepInto: () -> Unit,
    onStepOver: () -> Unit,
    onContinue: () -> Unit,
    onPause: () -> Unit,
    onResetDebugging: () -> Unit,
    onStopDebugging: () -> Unit,
    onSettings: () -> Unit,
    onJumpToAddress: (Long) -> Unit,
    onRemoveBreakpoint: (Long) -> Unit,
    onRefreshMemoryAtPc: () -> Unit,
    onRefreshMemoryAtSp: () -> Unit,
    onClearTrace: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.debug_panel_tab_controls),
        stringResource(R.string.debug_panel_tab_registers),
        stringResource(R.string.debug_panel_tab_breakpoints),
        stringResource(R.string.debug_panel_tab_stack),
        stringResource(R.string.debug_panel_tab_memory),
        stringResource(R.string.debug_panel_tab_trace)
    )

    Surface(
        modifier = modifier.padding(12.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DebugPanelHeader(
                debugStatus = debugStatus,
                debugBackend = debugBackend,
                pcAddress = pcAddress,
                stopReason = stopReason,
                onSettings = onSettings
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                containerColor = Color.Transparent
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> DebugControlsTab(
                    debugStatus = debugStatus,
                    capabilities = capabilities,
                    onStartDebugging = onStartDebugging,
                    onStepInto = onStepInto,
                    onStepOver = onStepOver,
                    onContinue = onContinue,
                    onPause = onPause,
                    onResetDebugging = onResetDebugging,
                    onStopDebugging = onStopDebugging,
                    onSettings = onSettings
                )
                1 -> Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.debug_register_auto_show),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = autoShowRegisters,
                            onCheckedChange = onAutoShowRegistersChange
                        )
                    }
                    RegisterGrid(
                        registers = registers,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
                2 -> BreakpointPanel(
                    breakpoints = breakpoints,
                    currentPc = pcAddress,
                    onJumpToAddress = onJumpToAddress,
                    onRemoveBreakpoint = onRemoveBreakpoint
                )
                3 -> StackPanel(
                    stackEntries = stackEntries,
                    stackPointer = stackPointer
                )
                4 -> MemoryPanel(
                    address = memoryAddress,
                    bytes = memoryBytes,
                    isLoading = memoryLoading,
                    error = memoryError,
                    onRefreshPc = onRefreshMemoryAtPc,
                    onRefreshSp = onRefreshMemoryAtSp
                )
                5 -> TracePanel(
                    traceEntries = traceEntries,
                    onClearTrace = onClearTrace
                )
            }
        }
    }
}

@Composable
private fun DebugPanelHeader(
    debugStatus: DebugStatus,
    debugBackend: DebugBackend,
    pcAddress: Long?,
    stopReason: DebugStopReason?,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${debugBackend.displayName} · ${debugStatus.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.debug_panel_pc,
                    pcAddress?.let { "0x${it.toULong().toString(16).uppercase()}" }
                        ?: stringResource(R.string.debug_panel_no_pc)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = LocalAppFont.current
            )
            stopReason?.let { DebugStopDetails(it) }
        }
        IconButton(onClick = onSettings, enabled = debugStatus == DebugStatus.IDLE || debugStatus == DebugStatus.ERROR) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.debug_panel_settings))
        }
    }
}

@Composable
private fun DebugStopDetails(reason: DebugStopReason) {
    val color = when (reason.type) {
        DebugStopReasonType.ESIL_ERROR, DebugStopReasonType.EXCEPTION, DebugStopReasonType.SIGNAL ->
            MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val style = MaterialTheme.typography.bodySmall
    Text(
        text = stringResource(R.string.debug_panel_stop_reason, reason.signal?.let { "${reason.type.name} · $it" } ?: reason.type.name),
        style = style,
        color = color,
        fontWeight = FontWeight.Bold
    )
    reason.address?.let {
        Text(
            text = stringResource(R.string.debug_panel_stop_instruction, "0x${it.toULong().toString(16).uppercase()}"),
            style = style,
            color = color
        )
    }
    reason.accessAddress?.let {
        Text(
            text = stringResource(R.string.debug_panel_stop_access, "0x${it.toULong().toString(16).uppercase()}"),
            style = style,
            color = color
        )
    }
    reason.message?.takeIf { it.isNotBlank() }?.let {
        Text(text = it, style = style, color = color, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DebugControlsTab(
    debugStatus: DebugStatus,
    capabilities: DebugCapabilities,
    onStartDebugging: () -> Unit,
    onStepInto: () -> Unit,
    onStepOver: () -> Unit,
    onContinue: () -> Unit,
    onPause: () -> Unit,
    onResetDebugging: () -> Unit,
    onStopDebugging: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (debugStatus) {
            DebugStatus.IDLE -> {
                IconButton(onClick = onStartDebugging) {
                    Icon(Icons.Default.PowerSettingsNew, stringResource(R.string.debug_panel_start))
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, stringResource(R.string.debug_panel_settings))
                }
            }
            DebugStatus.STARTING,
            DebugStatus.STOPPING -> {
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.Memory, stringResource(R.string.debug_panel_progress))
                }
            }
            DebugStatus.RUNNING -> {
                IconButton(onClick = onPause, enabled = capabilities.canPause) {
                    Icon(Icons.Default.Pause, stringResource(R.string.debug_panel_pause))
                }
            }
            DebugStatus.ERROR -> {
                IconButton(onClick = onResetDebugging) {
                    Icon(Icons.Default.PowerSettingsNew, stringResource(R.string.debug_panel_reset))
                }
                IconButton(onClick = onStopDebugging) {
                    Icon(Icons.Default.Close, stringResource(R.string.debug_panel_stop))
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, stringResource(R.string.debug_panel_settings))
                }
            }
            DebugStatus.SUSPENDED -> {
                IconButton(onClick = onContinue, enabled = capabilities.canContinue) {
                    Icon(Icons.Default.PlayArrow, stringResource(R.string.debug_panel_continue))
                }
                IconButton(onClick = onStepInto, enabled = capabilities.canStepInto) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.debug_panel_step_into))
                }
                IconButton(onClick = onStepOver, enabled = capabilities.canStepOver) {
                    Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.debug_panel_step_over))
                }
                IconButton(onClick = onResetDebugging) {
                    Icon(Icons.Default.PowerSettingsNew, stringResource(R.string.debug_panel_reset))
                }
                IconButton(onClick = onStopDebugging) {
                    Icon(Icons.Default.Close, stringResource(R.string.debug_panel_stop))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

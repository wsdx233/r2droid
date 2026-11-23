package top.wsdx233.r2droid.feature.disasm.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.core.ui.dialogs.CustomCommandDialog
import top.wsdx233.r2droid.core.ui.dialogs.ModifyDialog
import top.wsdx233.r2droid.core.ui.dialogs.canOpenPatchEditor
import top.wsdx233.r2droid.core.ui.dialogs.requestByteEditor
import top.wsdx233.r2droid.core.ui.dialogs.InstructionDetailDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import top.wsdx233.r2droid.core.ui.components.AutoHideAddressScrollbar
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.R
import androidx.compose.runtime.setValue
import top.wsdx233.r2droid.feature.debug.ui.DebugPanel
import top.wsdx233.r2droid.feature.debug.ui.RegisterBottomSheet
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.debug.data.DebugBackend
import top.wsdx233.r2droid.util.R2PipeManager

/**
 * Virtualized Disassembly Viewer - uses DisasmDataManager for smooth infinite scrolling.
 * 
 * Core design:
 * - LazyColumn displays loaded instructions from DisasmDataManager
 * - Data is loaded on-demand as user scrolls
 * - Custom fast scrollbar for quick navigation
 * - Placeholder shown for unloaded regions
 */
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.core.data.model.formatCompactAddress
import top.wsdx233.r2droid.feature.annotations.AnnotationAction
import top.wsdx233.r2droid.feature.disasm.data.DisasmDataManager
import top.wsdx233.r2droid.feature.disasm.DisasmViewModel
import top.wsdx233.r2droid.feature.disasm.DisasmEvent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DisassemblyViewer(
    viewModel: top.wsdx233.r2droid.feature.disasm.DisasmViewModel,
    cursorAddress: Long,
    scrollToSelectionTrigger: kotlinx.coroutines.flow.StateFlow<Int>,
    onInstructionClick: (Long) -> Unit,
    onAnnotate: (AnnotationAction, Long) -> Unit,
    onNavigateToR2Frida: (() -> Unit)? = null
) {
    val managerState by viewModel.disasmDataManagerState.collectAsState()
    val disasmDataManager = managerState
    val cacheVersion by viewModel.disasmCacheVersion.collectAsState()
    val multiSelectState by viewModel.multiSelectState.collectAsState()
    val breakpoints by viewModel.breakpoints.collectAsState()
    val debugBreakpoints by viewModel.debugBreakpoints.collectAsState()
    val pcAddress by viewModel.pcAddress.collectAsState()
    val debugStatus by viewModel.debugStatus.collectAsState()
    val debugStopReason by viewModel.debugStopReason.collectAsState()
    val debugError by viewModel.debugError.collectAsState()
    val registers by viewModel.registers.collectAsState()
    val debugStackState by viewModel.debugStackState.collectAsState()
    val debugMemoryState by viewModel.debugMemoryState.collectAsState()
    val debugFramesState by viewModel.debugFramesState.collectAsState()
    val debugThreadsState by viewModel.debugThreadsState.collectAsState()
    val debugTraceEntries by viewModel.debugTraceEntries.collectAsState()
    val debugLaunchConfig by viewModel.debugLaunchConfig.collectAsState()
    val debugCapabilities by viewModel.debugCapabilities.collectAsState()
    val debugBackend by viewModel.debugBackend.collectAsState()
    val isR2FridaSession = R2PipeManager.isR2FridaSession

    var showRegisters by remember { mutableStateOf(false) }
    var autoShowRegisters by remember { mutableStateOf(false) }
    var showDebugControls by remember { mutableStateOf(false) }
    var showDebugSettings by remember { mutableStateOf(false) }
    var footerHeightPx by remember { mutableIntStateOf(0) }
    var debugPanelHeightPx by remember { mutableIntStateOf(0) }
    val occludedBottomPx = footerHeightPx + if (showDebugControls) debugPanelHeightPx else 0
    val currentOccludedBottomPx by rememberUpdatedState(occludedBottomPx)

    LaunchedEffect(debugStatus, autoShowRegisters) {
        if (debugStatus != top.wsdx233.r2droid.feature.disasm.DebugStatus.IDLE) {
            showDebugControls = true
        }
        if (autoShowRegisters && debugStatus == top.wsdx233.r2droid.feature.disasm.DebugStatus.SUSPENDED) {
            showRegisters = true
        }
    }

    // Back handler to cancel multi-select
    androidx.activity.compose.BackHandler(enabled = multiSelectState.active) {
        viewModel.onEvent(DisasmEvent.ClearMultiSelect)
    }

    // Menu & Dialog States
    var showMenu by remember { mutableStateOf(false) }
    var menuTargetAddress by remember { mutableStateOf<Long?>(null) }
    var menuTapOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var menuRowHeight by remember { mutableIntStateOf(0) }

    val functionActions = remember(viewModel) { viewModel.functionMenuActions() }

    var showModifyDialog by remember { mutableStateOf(false) }
    var modifySession by remember { mutableStateOf<R2PipeManager.R2Session?>(null) }
    var modifyType by remember { mutableStateOf("hex") } // hex, string, asm
    var modifyInitialValue by remember { mutableStateOf<String?>("") }
    var showCustomCommandDialog by remember { mutableStateOf(false) }

    // Reopen in write mode dialog
    var showReopenDialog by remember { mutableStateOf(false) }
    var pendingWriteAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    if (disasmDataManager == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
    // Capture a single consistent snapshot to avoid race conditions in key/content lambdas.
    // Reading the volatile allInstructions multiple times during a layout pass can cause
    // duplicate keys if mergeInstructions swaps the list between reads.
    val dataSnapshot = remember(disasmDataManager, cacheVersion) { disasmDataManager.getDataSnapshot() }
    val instructionSnapshot = dataSnapshot.instructions
    val loadedCount = instructionSnapshot.size

    if (loadedCount <= 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Loading instructions...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    // Calculate initial scroll position based on cursor
    val initialIndex = remember(disasmDataManager, cursorAddress) {
        disasmDataManager.findClosestIndex(cursorAddress).coerceAtLeast(0)
    }
    
    val listState = rememberLazyListState(
        // Prepare a few measured rows in the direction of travel, not a whole data chunk.
        cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.25f) },
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, maxOf(0, loadedCount - 1))
    )
    
    val coroutineScope = rememberCoroutineScope()
    var isScrollbarDragging by remember(disasmDataManager) { mutableStateOf(false) }
    val isScrollbarSeeking by viewModel.isScrollbarSeeking.collectAsState()
    
    // Track previous cursor address to only scroll when it actually changes
    var previousCursorAddress by remember(disasmDataManager) { mutableLongStateOf(cursorAddress) }
    var hasInitiallyScrolled by remember(disasmDataManager) { mutableStateOf(false) }
    
    // Auto-scroll to cursor ONLY when cursorAddress changes (not on data load)
    LaunchedEffect(disasmDataManager, cursorAddress) {
        // Skip if this is just the initial composition with same address
        if (hasInitiallyScrolled && cursorAddress == previousCursorAddress) {
            return@LaunchedEffect
        }

        previousCursorAddress = cursorAddress
        hasInitiallyScrolled = true

        // Load data at target address first, then scroll
        viewModel.loadAndScrollTo(cursorAddress)
    }
    
    // Observe scroll to selection trigger from TopAppBar button
    val scrollToSelectionTrigger by scrollToSelectionTrigger.collectAsState()
    LaunchedEffect(scrollToSelectionTrigger) {
        if (scrollToSelectionTrigger > 0) {
            viewModel.loadAndScrollTo(cursorAddress)
        }
    }
    
    // Navigation targets are resolved against the same immutable snapshot used by LazyColumn.
    val scrollTarget by viewModel.scrollTarget.collectAsState()
    LaunchedEffect(listState, scrollTarget, instructionSnapshot) {
        val target = scrollTarget ?: return@LaunchedEffect
        val index = if (target.animate) DisasmDataManager.findClosestIndex(instructionSnapshot, target.address)
            else DisasmDataManager.coveredIndex(instructionSnapshot, target.address)
        // Cache and target flows can be collected in either order. Never acknowledge a target
        // against an old window, or briefly expose the old rows beneath the placeholder.
        if (index < 0) return@LaunchedEffect
        if (index >= 0) {
            if (!target.animate) {
                // Scrollbar commits must not fight the finger with a centering animation.
                listState.scrollToItem(index)
            } else {
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                    listState.scrollToItem(index)
                }
                val layout = listState.layoutInfo
                val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                if (item != null) {
                    val listBottomPadPx = with(density) { 8.dp.roundToPx() }
                    val occludedInListPx = (currentOccludedBottomPx - listBottomPadPx).coerceAtLeast(0)
                    val delta = computeCenteredScrollDelta(
                        viewportStart = layout.viewportStartOffset,
                        viewportEnd = layout.viewportEndOffset,
                        occludedBottomPx = occludedInListPx,
                        itemOffset = item.offset,
                        itemSize = item.size
                    )
                    if (kotlin.math.abs(delta) > 1) listState.animateScrollBy(delta.toFloat())
                }
            }
        }
        viewModel.clearScrollTarget(target)
    }

    // When the debug panel opens or expands over the active instruction, center it in the visible upper viewport.
    var centeredAfterPanelOpen by remember(showDebugControls) { mutableStateOf(false) }
    LaunchedEffect(showDebugControls, debugPanelHeightPx) {
        if (!showDebugControls || debugPanelHeightPx <= 0 || scrollTarget != null) return@LaunchedEffect
        val activeAddr = pcAddress ?: cursorAddress
        val index = DisasmDataManager.findClosestIndex(instructionSnapshot, activeAddr)
        if (index < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return@LaunchedEffect
        val listBottomPadPx = with(density) { 8.dp.roundToPx() }
        val occludedInListPx = (occludedBottomPx - listBottomPadPx).coerceAtLeast(0)
        val visibleBottom = (layout.viewportEndOffset - occludedInListPx)
            .coerceAtLeast(layout.viewportStartOffset + item.size)
        val needsCentering = !centeredAfterPanelOpen || (item.offset + item.size > visibleBottom)
        centeredAfterPanelOpen = true
        if (needsCentering) {
            val delta = computeCenteredScrollDelta(
                viewportStart = layout.viewportStartOffset,
                viewportEnd = layout.viewportEndOffset,
                occludedBottomPx = occludedInListPx,
                itemOffset = item.offset,
                itemSize = item.size
            )
            if (kotlin.math.abs(delta) > 1) listState.animateScrollBy(delta.toFloat())
        }
    }

    // Emit only when an edge/window changes, not for every row or scroll pixel. Start early
    // enough to hide R2 latency, and suspend edge loading during scrollbar previews/commits.
    LaunchedEffect(listState, instructionSnapshot, isScrollbarDragging, isScrollbarSeeking, scrollTarget) {
        if (isScrollbarDragging || isScrollbarSeeking || scrollTarget != null) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val first = layout.visibleItemsInfo.firstOrNull()?.index
            val last = layout.visibleItemsInfo.lastOrNull()?.index
            val before = instructionSnapshot.firstOrNull()?.addr?.takeIf {
                first != null && first < 30 && it > disasmDataManager.viewStartAddress
            }
            val after = instructionSnapshot.lastOrNull()?.let { instr ->
                (instr.addr + instr.size.coerceAtLeast(1)).takeIf {
                    last != null && last >= instructionSnapshot.size - 30 && it < disasmDataManager.viewEndAddress
                }
            }
            before to after
        }.collect { (before, after) ->
            if (before != null) viewModel.onEvent(DisasmEvent.LoadMore(false))
            if (after != null) viewModel.onEvent(DisasmEvent.LoadMore(true))
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Jump maps are pre-computed in DisasmDataManager on background thread
        val jumpToIndex = dataSnapshot.jumpToIndex
        val targetToIndex = dataSnapshot.targetToIndex
        val normalStyle = rememberDisasmRowStyle(selected = false)
        val selectedStyle = rememberDisasmRowStyle(selected = true)
        
        val bottomContentPadding = with(density) { occludedBottomPx.toDp() } + 80.dp
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            contentPadding = PaddingValues(bottom = bottomContentPadding)
        ) {
            items(
                count = loadedCount,
                key = { index ->
                    instructionSnapshot[index].addr
                },
                contentType = { "instruction" }
            ) { index ->
                // Use the captured snapshot for consistent reads
                val instr = instructionSnapshot.getOrNull(index)
                
                if (instr != null) {
                    val isThisRowMenuTarget = showMenu && menuTargetAddress == instr.addr
                    
                    // Look up jump indices for this instruction
                    val jumpIdx = jumpToIndex[instr.addr]
                    val targetIdx = targetToIndex[instr.addr]
                    
                    DisasmRow(
                        instr = instr,
                        style = if (instr.addr == cursorAddress) selectedStyle else normalStyle,
                        isMultiSelected = multiSelectState.contains(instr.addr),
                        isPC = instr.addr == pcAddress,
                        isBreakpoint = breakpoints.contains(instr.addr),
                        onGutterClick = { viewModel.toggleBreakpoint(instr.addr) },
                        onClick = { offset, height ->
                            if (multiSelectState.active) {
                                viewModel.onEvent(DisasmEvent.UpdateMultiSelect(instr.addr))
                            } else if (instr.addr == cursorAddress) {
                                menuTargetAddress = instr.addr
                                menuTapOffset = offset
                                menuRowHeight = height
                                showMenu = true
                            } else {
                                onInstructionClick(instr.addr)
                            }
                        },
                        onLongClick = { _, _ ->
                            viewModel.onEvent(DisasmEvent.StartMultiSelect(instr.addr))
                        },
                        showMenu = isThisRowMenuTarget,
                        menuContent = {
                            DisasmContextMenu(
                                expanded = isThisRowMenuTarget,
                                address = instr.addr,
                                instr = instr,
                                onDismiss = { showMenu = false },
                                onCopy = { text ->
                                    clipboardManager.setText(AnnotatedString(text))
                                    showMenu = false
                                },
                                onModify = edit@{ type ->
                                    showMenu = false
                                    if (!context.canOpenPatchEditor()) return@edit
                                    val capturedOpcode = instr.opcode
                                    if (type == "comment") {
                                        modifyType = type
                                        modifyInitialValue = null
                                        showModifyDialog = true
                                    } else {
                                        coroutineScope.launch {
                                            context.requestByteEditor(onReopen = {
                                                pendingWriteAction = it
                                                showReopenDialog = true
                                            }) {
                                                modifySession = it
                                                modifyType = type
                                                showModifyDialog = true
                                                modifyInitialValue = when (type) {
                                                    "asm" -> capturedOpcode
                                                    "hex" -> ""
                                                    "string" -> null
                                                    else -> ""
                                                }
                                            }
                                        }
                                    }
                                },
                                onXrefs = {
                                    viewModel.onEvent(DisasmEvent.FetchXrefs(instr.addr))
                                    showMenu = false
                                },
                                onCustomCommand = {
                                    showCustomCommandDialog = true
                                    showMenu = false
                                },
                                functionActions = functionActions,
                                onAnnotate = onAnnotate,
                                onInstructionDetail = {
                                    viewModel.onEvent(DisasmEvent.FetchInstructionDetail(instr.addr))
                                    showMenu = false
                                },
                                onJumpToTarget = { addr ->
                                    onInstructionClick(addr)
                                    showMenu = false
                                },
                                showDebugActions = debugStatus != top.wsdx233.r2droid.feature.disasm.DebugStatus.IDLE,
                                isBreakpoint = breakpoints.contains(instr.addr),
                                onToggleBreakpoint = {
                                    viewModel.toggleBreakpoint(instr.addr)
                                    showMenu = false
                                },
                                onRunToCursor = {
                                    viewModel.runToCursor(instr.addr)
                                    showMenu = false
                                },
                                onSetPcHere = {
                                    viewModel.setDebugPc(instr.addr)
                                    showMenu = false
                                },
                                offset = if (SettingsManager.menuAtTouch) {
                                    with(density) {
                                        androidx.compose.ui.unit.DpOffset(menuTapOffset.x.toDp(), (menuTapOffset.y - menuRowHeight).toDp())
                                    }
                                } else androidx.compose.ui.unit.DpOffset.Zero
                            )
                        },
                        jumpIndex = jumpIdx,
                        jumpTargetIndex = targetIdx
                    )
                } else {
                    // Placeholder row
                    DisasmPlaceholderRow(rowIndex = index)
                }
            }
        }
        
        DisasmScrollbarPreviewLayer(
            previewState = viewModel.scrollbarPreview,
            instructions = instructionSnapshot,
            listState = listState,
            onRetry = viewModel::retryScrollbarPreview,
            onDismiss = viewModel::dismissScrollbarPreview
        )

        // Measure the docked debug panel and footer; the scrollbar gets the remaining upper height.
        Column(Modifier.matchParentSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = AbsoluteAlignment.CenterRight) {
                DisasmAddressScrollbar(
                    listState = listState,
                    instructions = instructionSnapshot,
                    manager = disasmDataManager,
                    previewState = viewModel.scrollbarPreview,
                    onPreview = viewModel::previewScrollbarAddress,
                    onCommit = viewModel::scrollbarJumpTo,
                    isSeeking = isScrollbarSeeking,
                    onDragStateChange = {
                        isScrollbarDragging = it
                        viewModel.onScrollbarDragStateChange(it)
                    }
                )
            }

            AnimatedVisibility(
                visible = showDebugControls,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                DebugPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { debugPanelHeightPx = it.height },
                    debugStatus = debugStatus,
                    debugBackend = debugBackend,
                    pcAddress = pcAddress,
                    stopReason = debugStopReason,
                    registers = registers,
                    breakpoints = debugBreakpoints,
                    stackEntries = debugStackState.entries,
                    stackPointer = debugStackState.stackPointer,
                    stackLoading = debugStackState.isLoading,
                    memoryAddress = debugMemoryState.address,
                    memoryBytes = debugMemoryState.bytes,
                    memoryLoading = debugMemoryState.isLoading,
                    memoryWriting = debugMemoryState.isWriting,
                    memoryError = debugMemoryState.error,
                    frames = debugFramesState.frames,
                    framesLoading = debugFramesState.isLoading,
                    framesError = debugFramesState.error,
                    threads = debugThreadsState.threads,
                    threadsLoading = debugThreadsState.isLoading,
                    threadsError = debugThreadsState.error,
                    traceEntries = debugTraceEntries,
                    capabilities = debugCapabilities,
                    autoShowRegisters = autoShowRegisters,
                    onAutoShowRegistersChange = { autoShowRegisters = it },
                    onStartDebugging = { viewModel.startDebugging() },
                    onStepInto = { viewModel.performDebugAction("step") },
                    onStepOver = { viewModel.performDebugAction("over") },
                    onStepOut = { viewModel.performDebugAction("out") },
                    onContinue = { viewModel.performDebugAction("continue") },
                    onPause = { viewModel.pauseExecution() },
                    onResetDebugging = { viewModel.resetDebugging() },
                    onStopDebugging = { viewModel.stopDebugging() },
                    onSettings = { showDebugSettings = true },
                    onDismiss = { showDebugControls = false },
                    onJumpToAddress = { addr -> onInstructionClick(addr) },
                    onWriteRegister = { name, value -> viewModel.writeDebugRegister(name, value) },
                    onAddBreakpoint = { expr, kind, cond, access ->
                        viewModel.addDebugBreakpoint(expr, kind, cond, access)
                    },
                    onRemoveBreakpoint = { addr -> viewModel.removeDebugBreakpoint(addr) },
                    onToggleBreakpointEnabled = { addr, enabled -> viewModel.setBreakpointEnabled(addr, enabled) },
                    onUpdateBreakpointCondition = { addr, cond -> viewModel.setBreakpointCondition(addr, cond) },
                    onRefreshStack = { viewModel.refreshStack() },
                    onRefreshMemoryAtPc = { viewModel.readMemoryAtPc() },
                    onRefreshMemoryAtSp = { viewModel.readMemoryAtSp() },
                    onRefreshMemoryAtBp = { viewModel.readMemoryAtBp() },
                    onQueryMemoryExpression = { expr -> viewModel.readMemoryByExpression(expr) },
                    onInspectMemoryAddress = { addr -> viewModel.readMemoryAt(addr) },
                    onWriteMemory = { addr, payload -> viewModel.writeDebugMemory(addr, payload) },
                    onRefreshFrames = { viewModel.refreshFrames() },
                    onRefreshThreads = { viewModel.refreshThreads() },
                    onSelectThread = { tid -> viewModel.selectDebugThread(tid) },
                    onClearTrace = { viewModel.clearDebugTrace() }
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { footerHeightPx = it.height }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    DisasmCurrentAddress(listState, instructionSnapshot, viewModel, isScrollbarDragging)
                    if (debugStatus != top.wsdx233.r2droid.feature.disasm.DebugStatus.IDLE && pcAddress != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = { pcAddress?.let(onInstructionClick) },
                            label = {
                                Text(
                                    stringResource(R.string.debug_footer_next_pc, "0x%X".format(pcAddress)),
                                    fontSize = 11.sp,
                                    fontFamily = LocalAppFont.current,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Loaded: $loadedCount instrs",
                        fontSize = 12.sp,
                        fontFamily = LocalAppFont.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { showDebugControls = !showDebugControls },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Toggle Debug Controls",
                        tint = if (showDebugControls || debugStatus != top.wsdx233.r2droid.feature.disasm.DebugStatus.IDLE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        
        // Context Menu
        // Xrefs Dialog

        
        // Async fetch for modify dialog initial value
        LaunchedEffect(showModifyDialog, modifyType, menuTargetAddress) {
            if (showModifyDialog && menuTargetAddress != null && modifyInitialValue == null) {
                when (modifyType) {
                    "string" -> {
                        val result = top.wsdx233.r2droid.util.R2PipeManager.execute("ps @ ${menuTargetAddress}")
                        modifyInitialValue = result.getOrDefault("").trim()
                    }
                    "comment" -> {
                        val result = top.wsdx233.r2droid.util.R2PipeManager.execute("CC. @ ${menuTargetAddress}")
                        val raw = result.getOrDefault("").trim()
                        modifyInitialValue = if (raw.matches(Regex("^[A-Za-z0-9+/]+=*$"))) {
                            try {
                                val bytes = android.util.Base64.decode(raw, android.util.Base64.NO_WRAP)
                                val decoded = String(bytes, Charsets.UTF_8)
                                val reEncoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                if (reEncoded == raw) decoded else raw
                            } catch (_: Exception) { raw }
                        } else raw
                    }
                }
            }
        }

        // Modify Dialog
        if (showModifyDialog && menuTargetAddress != null) {
            val title = when(modifyType) {
                "hex" -> "Modify Hex (wx)"
                "string" -> "Modify String (w)"
                "asm" -> "Modify Opcode (wa)"
                "comment" -> "Modify Comment (CCu)"
                else -> "Modify"
            }
            if (modifyInitialValue != null) {
                ModifyDialog(
                    title = title,
                    initialValue = modifyInitialValue!!,
                    onDismiss = { showModifyDialog = false; modifyInitialValue = "" },
                    onConfirm = { value ->
                        val address = menuTargetAddress!!
                        if (modifyType == "comment") {
                            viewModel.onEvent(DisasmEvent.WriteComment(address, value))
                        } else {
                            modifySession?.let { session ->
                                when (modifyType) {
                                    "hex" -> viewModel.onEvent(DisasmEvent.WriteHex(session, address, value))
                                    "string" -> viewModel.onEvent(DisasmEvent.WriteString(session, address, value))
                                    "asm" -> viewModel.onEvent(DisasmEvent.WriteAsm(session, address, value))
                                }
                            }
                        }
                    }
                )
            } else {
                // Loading state while fetching initial value
                AlertDialog(
                    onDismissRequest = { showModifyDialog = false; modifyInitialValue = "" },
                    title = { Text(title) },
                    text = {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showModifyDialog = false; modifyInitialValue = "" }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        // Reopen in write mode dialog
        if (showReopenDialog) {
            AlertDialog(
                onDismissRequest = { showReopenDialog = false; pendingWriteAction = null },
                title = { Text(androidx.compose.ui.res.stringResource(R.string.reopen_write_title)) },
                text = { Text(androidx.compose.ui.res.stringResource(R.string.reopen_write_message)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showReopenDialog = false
                        val action = pendingWriteAction
                        pendingWriteAction = null
                        coroutineScope.launch {
                            action?.invoke()
                        }
                    }) {
                        Text(androidx.compose.ui.res.stringResource(R.string.reopen_write_confirm))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showReopenDialog = false; pendingWriteAction = null }) {
                        Text(androidx.compose.ui.res.stringResource(R.string.reopen_write_cancel))
                    }
                }
            )
        }

        // Custom Command Dialog
        if (showCustomCommandDialog) {
            CustomCommandDialog(
                onDismiss = { showCustomCommandDialog = false },
                onConfirm = { /* handled internally */ }
            )
        }

        // Instruction Detail Dialog
        val instructionDetailState by viewModel.instructionDetailState.collectAsState()
        if (instructionDetailState.visible) {
            InstructionDetailDialog(
                detail = instructionDetailState.data,
                isLoading = instructionDetailState.isLoading,
                targetAddress = instructionDetailState.targetAddress,
                aiExplanation = instructionDetailState.aiExplanation,
                aiExplainLoading = instructionDetailState.aiExplainLoading,
                aiExplainError = instructionDetailState.aiExplainError,
                onDismiss = { viewModel.onEvent(DisasmEvent.DismissInstructionDetail) },
                onJump = { addr -> onInstructionClick(addr) },
                onAiExplain = if (SettingsManager.aiEnabled) { { addr ->
                    viewModel.onEvent(DisasmEvent.ExplainInstructionWithAi(addr))
                } } else null
            )
        }

        val aiPolishState by viewModel.aiPolishState.collectAsState()
        if (aiPolishState.visible) {
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(DisasmEvent.DismissAiPolish) },
                title = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.disasm_ai_explain_result_title)
                    )
                },
                text = {
                    when {
                        aiPolishState.isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        aiPolishState.error != null -> {
                            Text(
                                text = aiPolishState.error ?: "",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                MarkdownText(
                                    markdown = aiPolishState.result,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(360.dp)
                                        .verticalScroll(rememberScrollState()),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
//                                    syntaxHighlightColor = MaterialTheme.colorScheme.surfaceContainerHighest,
//                                    syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.onEvent(DisasmEvent.DismissAiPolish) }
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.func_close))
                    }
                }
            )
        }

        debugError?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.clearDebugError() },
                title = { Text(stringResource(R.string.debug_error_title)) },
                text = { Text(error) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.clearDebugError() }) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                }
            )
        }

        if (showRegisters) {
            RegisterBottomSheet(
                registers = registers,
                autoShowRegisters = autoShowRegisters,
                onAutoShowRegistersChange = { autoShowRegisters = it },
                onDismissRequest = { showRegisters = false },
                canEditRegisters = debugCapabilities.canEditRegisters && debugStatus == top.wsdx233.r2droid.feature.disasm.DebugStatus.SUSPENDED,
                onWriteRegister = { name, value -> viewModel.writeDebugRegister(name, value) },
                onJumpToDisasm = { addr -> onInstructionClick(addr) },
                onInspectMemory = { addr ->
                    viewModel.readMemoryAt(addr)
                    showDebugControls = true
                }
            )
        }

        if (showDebugSettings) {
            AlertDialog(
                onDismissRequest = { showDebugSettings = false },
                title = { Text(stringResource(R.string.debug_backend_title)) },
                text = {
                    Column {
                        if (debugBackend == DebugBackend.FRIDA && !isR2FridaSession) {
                            Text(
                                text = stringResource(R.string.debug_frida_requires_session),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            onNavigateToR2Frida?.let { navigate ->
                                Button(
                                    onClick = {
                                        showDebugSettings = false
                                        navigate()
                                    },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Text(stringResource(R.string.debug_go_to_r2frida))
                                }
                            }
                        }
                        val backends = DebugBackend.entries.toTypedArray()
                        backends.forEach { backend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setDebugBackend(backend)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = debugBackend == backend,
                                    onClick = {
                                        viewModel.setDebugBackend(backend)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (backend) {
                                        DebugBackend.ESIL -> stringResource(R.string.debug_backend_esil)
                                        DebugBackend.NATIVE_GDB -> stringResource(R.string.debug_backend_native_r2)
                                        DebugBackend.FRIDA -> stringResource(R.string.debug_backend_frida)
                                        DebugBackend.GDB_REMOTE -> stringResource(R.string.debug_backend_gdb_remote)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateDebugLaunchConfig(
                                        debugLaunchConfig.copy(startAtCurrentSeek = !debugLaunchConfig.startAtCurrentSeek)
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = debugLaunchConfig.startAtCurrentSeek,
                                onCheckedChange = { checked ->
                                    viewModel.updateDebugLaunchConfig(debugLaunchConfig.copy(startAtCurrentSeek = checked))
                                }
                            )
                            Text(stringResource(R.string.debug_config_start_current_seek))
                        }
                        OutlinedTextField(
                            value = debugLaunchConfig.startAddressText,
                            onValueChange = { viewModel.updateDebugLaunchConfig(debugLaunchConfig.copy(startAddressText = it)) },
                            label = { Text(stringResource(R.string.debug_config_start_address)) },
                            placeholder = { Text(stringResource(R.string.debug_config_start_address_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = debugLaunchConfig.stackAddressText,
                            onValueChange = { viewModel.updateDebugLaunchConfig(debugLaunchConfig.copy(stackAddressText = it)) },
                            label = { Text(stringResource(R.string.debug_config_stack_address)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = debugLaunchConfig.stackSizeText,
                            onValueChange = { viewModel.updateDebugLaunchConfig(debugLaunchConfig.copy(stackSizeText = it)) },
                            label = { Text(stringResource(R.string.debug_config_stack_size)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = debugLaunchConfig.maxEsilStepsText,
                            onValueChange = { viewModel.updateDebugLaunchConfig(debugLaunchConfig.copy(maxEsilStepsText = it)) },
                            label = { Text(stringResource(R.string.debug_config_max_esil_steps)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = debugLaunchConfig.esilTimeoutMsText,
                            onValueChange = { viewModel.updateDebugLaunchConfig(debugLaunchConfig.copy(esilTimeoutMsText = it)) },
                            label = { Text(stringResource(R.string.debug_config_esil_timeout_ms)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = debugLaunchConfig.attachPidText,
                            onValueChange = { viewModel.updateDebugLaunchConfig(debugLaunchConfig.copy(attachPidText = it)) },
                            label = { Text(stringResource(R.string.debug_config_attach_pid)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = debugLaunchConfig.gdbRemoteHostText,
                            onValueChange = { viewModel.updateDebugLaunchConfig(debugLaunchConfig.copy(gdbRemoteHostText = it)) },
                            label = { Text(stringResource(R.string.debug_config_gdb_host)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = debugLaunchConfig.gdbRemotePortText,
                            onValueChange = { viewModel.updateDebugLaunchConfig(debugLaunchConfig.copy(gdbRemotePortText = it)) },
                            label = { Text(stringResource(R.string.debug_config_gdb_port)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showDebugSettings = false }) {
                        Text(stringResource(R.string.func_close))
                    }
                }
            )
        }
    }
}

/**
 * Computes the scroll delta needed to center [itemOffset]..([itemOffset] + [itemSize])
 * within the unobstructed portion of the viewport above [occludedBottomPx].
 */
internal fun computeCenteredScrollDelta(
    viewportStart: Int,
    viewportEnd: Int,
    occludedBottomPx: Int,
    itemOffset: Int,
    itemSize: Int
): Int {
    val visibleBottom = (viewportEnd - occludedBottomPx.coerceAtLeast(0))
        .coerceAtLeast(viewportStart + itemSize)
    val center = (viewportStart + visibleBottom - itemSize) / 2
    return itemOffset - center
}

/** Isolate frequently changing list reads from the large viewer/menu/debug composition. */
@Composable
private fun DisasmCurrentAddress(
    listState: LazyListState,
    instructions: List<DisasmInstruction>,
    viewModel: DisasmViewModel,
    isDragging: Boolean
) {
    // While dragging, the bubble owns the target address; the footer still describes the list.
    val preview = if (isDragging) null else viewModel.scrollbarPreview.collectAsState().value
    val hex = preview?.let { formatCompactAddress(it.address) }
        ?: instructions.getOrNull(listState.firstVisibleItemIndex)?.displayAddress ?: "0"
    Text(
        "Addr: 0x$hex",
        fontSize = 12.sp,
        fontFamily = LocalAppFont.current,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun DisasmAddressScrollbar(
    listState: LazyListState,
    instructions: List<DisasmInstruction>,
    manager: DisasmDataManager,
    previewState: kotlinx.coroutines.flow.StateFlow<top.wsdx233.r2droid.feature.disasm.DisasmScrollbarPreview?>,
    onPreview: (Long) -> Unit,
    onCommit: (Long) -> Unit,
    isSeeking: Boolean,
    onDragStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentInstructions by rememberUpdatedState(instructions)
    var dragging by remember(manager) { mutableStateOf(false) }
    val thumbCenter = remember(manager) { mutableFloatStateOf(0f) }
    val previewBands = remember(manager) {
        lazy(LazyThreadSafetyMode.NONE) {
            manager.addressOverview.rasterize(manager.viewStartAddress, manager.viewEndAddress, PREVIEW_ROWS)
        }
    }
    // This is only bookkeeping for pointer callbacks, not observable UI state.
    val lastPreviewIndex = remember(listState) { intArrayOf(-1) }
    // A dwell load (or a distant LRU hit) can replace/prepend the window while the finger is still
    // down. Re-resolve the latest address against exactly the snapshot used by LazyColumn.
    LaunchedEffect(listState, instructions) {
        lastPreviewIndex[0] = -1
        val address = previewState.value?.address ?: return@LaunchedEffect
        val index = DisasmDataManager.coveredIndex(instructions, address)
        if (index >= 0) {
            lastPreviewIndex[0] = index
            listState.requestScrollToItem(index)
        }
    }
    if (dragging) {
        DisasmDragBubble(previewState, manager, instructions, previewBands.value, thumbCenter)
    }
    AutoHideAddressScrollbar(
        listState = listState,
        totalItems = instructions.size,
        viewStartAddress = manager.viewStartAddress,
        viewEndAddress = manager.viewEndAddress,
        currentAddress = { instructions.getOrNull(listState.firstVisibleItemIndex)?.addr ?: manager.viewStartAddress },
        modifier = modifier,
        alwaysShow = true,
        isSeeking = isSeeking,
        thumbWidth = DisasmThumbWidth,
        overview = manager.addressOverview,
        onDragThumbCenter = { thumbCenter.floatValue = it },
        onScrollToAddress = { address ->
            onPreview(address)
            val index = DisasmDataManager.coveredIndex(currentInstructions, address)
            if (index >= 0 && index != lastPreviewIndex[0]) {
                lastPreviewIndex[0] = index
                // Latest request wins at the next remeasure; no coroutine queue or synchronous
                // remeasure for each pointer event. R2 I/O is separately dwell-debounced.
                listState.requestScrollToItem(index)
            }
        },
        onDragComplete = onCommit,
        onDragStateChange = {
            dragging = it
            lastPreviewIndex[0] = -1
            if (it) {
                // Stop an existing list fling even when the first preview is a cache miss.
                listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            }
            onDragStateChange(it)
        }
    )
}

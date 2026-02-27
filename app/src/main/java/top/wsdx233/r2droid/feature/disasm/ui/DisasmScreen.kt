package top.wsdx233.r2droid.feature.disasm.ui

import android.annotation.SuppressLint
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.core.ui.dialogs.CustomCommandDialog
import top.wsdx233.r2droid.core.ui.dialogs.FunctionInfoDialog
import top.wsdx233.r2droid.core.ui.dialogs.FunctionVariablesDialog
import top.wsdx233.r2droid.core.ui.dialogs.FunctionXrefsDialog
import top.wsdx233.r2droid.core.ui.dialogs.ModifyDialog
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
import top.wsdx233.r2droid.feature.debug.ui.RegisterBottomSheet
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.debug.data.DebugBackend

/**
 * Virtualized Disassembly Viewer - uses DisasmDataManager for smooth infinite scrolling.
 * 
 * Core design:
 * - LazyColumn displays loaded instructions from DisasmDataManager
 * - Data is loaded on-demand as user scrolls
 * - Custom fast scrollbar for quick navigation
 * - Placeholder shown for unloaded regions
 */
import top.wsdx233.r2droid.feature.disasm.DisasmEvent

@SuppressLint("FrequentlyChangingValue")
@Composable
fun DisassemblyViewer(
    viewModel: top.wsdx233.r2droid.feature.disasm.DisasmViewModel,
    cursorAddress: Long,
    scrollToSelectionTrigger: kotlinx.coroutines.flow.StateFlow<Int>,
    onInstructionClick: (Long) -> Unit
) {
    val disasmDataManager = viewModel.disasmDataManager
    val cacheVersion by viewModel.disasmCacheVersion.collectAsState()
    val multiSelectState by viewModel.multiSelectState.collectAsState()
    val breakpoints by viewModel.breakpoints.collectAsState()
    val pcAddress by viewModel.pcAddress.collectAsState()
    val debugStatus by viewModel.debugStatus.collectAsState()
    val registers by viewModel.registers.collectAsState()
    val debugBackend by viewModel.debugBackend.collectAsState()

    var showRegisters by remember { mutableStateOf(false) }
    var showDebugControls by remember { mutableStateOf(false) }
    var showDebugSettings by remember { mutableStateOf(false) }

    LaunchedEffect(debugStatus) {
        if (debugStatus == top.wsdx233.r2droid.feature.disasm.DebugStatus.SUSPENDED) {
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

    var showModifyDialog by remember { mutableStateOf(false) }
    var modifyType by remember { mutableStateOf("hex") } // hex, string, asm
    var modifyInitialValue by remember { mutableStateOf<String?>("") }
    var showCustomCommandDialog by remember { mutableStateOf(false) }

    // Reopen in write mode dialog
    var showReopenDialog by remember { mutableStateOf(false) }
    var pendingWriteAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val clipboardManager = LocalClipboardManager.current
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
    val instructionSnapshot = remember(cacheVersion) { disasmDataManager.getSnapshot() }
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
    // Virtual address range for scrollbar calculation
    val viewStartAddr = remember { disasmDataManager.viewStartAddress }
    val viewEndAddr = remember { disasmDataManager.viewEndAddress }
    val totalAddressRange = viewEndAddr - viewStartAddr
    
    // Calculate initial scroll position based on cursor
    val initialIndex = remember(cursorAddress) {
        disasmDataManager.findClosestIndex(cursorAddress).coerceAtLeast(0)
    }
    
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, maxOf(0, loadedCount - 1))
    )
    
    // Coroutine scope for scrollbar interactions
    val coroutineScope = rememberCoroutineScope()
    
    // Track previous cursor address to only scroll when it actually changes
    var previousCursorAddress by remember { mutableLongStateOf(cursorAddress) }
    var hasInitiallyScrolled by remember { mutableStateOf(false) }
    
    // Auto-scroll to cursor ONLY when cursorAddress changes (not on data load)
    LaunchedEffect(cursorAddress) {
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
    
    // Observe scroll target from ViewModel - scrolls after data is loaded
    val scrollTarget by viewModel.scrollTarget.collectAsState()
    LaunchedEffect(scrollTarget) {
        val target = scrollTarget ?: return@LaunchedEffect
        val (targetAddr, targetIndex) = target
        if (targetIndex >= 0 && loadedCount > 0) {
            val clampedIndex = targetIndex.coerceIn(0, loadedCount - 1)
            // Check if target is already visible on screen
            val visibleItem = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == clampedIndex }
            if (visibleItem != null) {
                // Already visible: smooth scroll to center from current position
                val viewportHeight = listState.layoutInfo.viewportEndOffset -
                        listState.layoutInfo.viewportStartOffset
                val desiredOffset = (viewportHeight - visibleItem.size) / 2
                val delta = visibleItem.offset - desiredOffset
                if (kotlin.math.abs(delta) > 1) {
                    listState.animateScrollBy(delta.toFloat())
                }
            } else {
                // Not visible: jump first, then center after layout updates
                listState.scrollToItem(clampedIndex)
                // Wait for the next frame to ensure layout is updated
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    kotlinx.coroutines.delay(50) // Give time for layout to settle
                    val layoutInfo = listState.layoutInfo
                    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                    val targetItemInfo = layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == clampedIndex }
                    if (targetItemInfo != null && viewportHeight > 0) {
                        val centerOffset = (viewportHeight - targetItemInfo.size) / 2
                        val currentOffset = targetItemInfo.offset
                        if (centerOffset > 0 && kotlin.math.abs(currentOffset - centerOffset) > 1) {
                            listState.animateScrollBy((currentOffset - centerOffset).toFloat())
                        }
                    }
                }
            }
        }
        viewModel.clearScrollTarget()
    }

    // Load more when near edges - use snapshotFlow for better control
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0,
                disasmDataManager.loadedInstructionCount
            )
        }.collect { (firstVisible, lastVisible, total) ->
            // Preload around visible area
            val currentInstr = disasmDataManager.getInstructionAt(firstVisible)
            if (currentInstr != null) {
                viewModel.onEvent(DisasmEvent.Preload(currentInstr.addr))
            }
            
            // Load more at top
            if (firstVisible in 1..<10) {
                viewModel.onEvent(DisasmEvent.LoadMore(false))
            }
            
            // Load more at bottom
            if (total > 0 && lastVisible > total - 10) {
                viewModel.onEvent(DisasmEvent.LoadMore(true))
            }
        }
    }
    
    Box(Modifier.fillMaxSize()) {
        // Jump maps are pre-computed in DisasmDataManager on background thread
        val jumpToIndex = remember(cacheVersion) { disasmDataManager.jumpToIndexMap }
        val targetToIndex = remember(cacheVersion) { disasmDataManager.targetToIndexMap }
        
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(
                count = loadedCount,
                key = { index ->
                    instructionSnapshot.getOrNull(index)?.addr ?: -(index.toLong() + 1)
                }
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
                        isSelected = instr.addr == cursorAddress,
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
                                onModify = { type ->
                                    showMenu = false
                                    val capturedOpcode = instr.opcode
                                    if (type == "comment") {
                                        modifyType = type
                                        modifyInitialValue = null
                                        showModifyDialog = true
                                    } else {
                                        coroutineScope.launch {
                                            val isWritable = try {
                                                val ijResult = top.wsdx233.r2droid.util.R2PipeManager.execute("ij").getOrDefault("{}")
                                                org.json.JSONObject(ijResult).getJSONObject("core").getBoolean("iorw")
                                            } catch (_: Exception) { false }
                                            if (isWritable) {
                                                modifyType = type
                                                showModifyDialog = true
                                                modifyInitialValue = when (type) {
                                                    "asm" -> capturedOpcode
                                                    "hex" -> ""
                                                    "string" -> null
                                                    else -> ""
                                                }
                                            } else {
                                                pendingWriteAction = {
                                                    modifyType = type
                                                    showModifyDialog = true
                                                    modifyInitialValue = when (type) {
                                                        "asm" -> capturedOpcode
                                                        "hex" -> ""
                                                        "string" -> null
                                                        else -> ""
                                                    }
                                                }
                                                showReopenDialog = true
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
                                onAnalyzeFunction = {
                                    viewModel.onEvent(DisasmEvent.AnalyzeFunction(instr.addr))
                                    showMenu = false
                                },
                                onFunctionInfo = {
                                    viewModel.onEvent(DisasmEvent.FetchFunctionInfo(instr.addr))
                                    showMenu = false
                                },
                                onFunctionXrefs = {
                                    viewModel.onEvent(DisasmEvent.FetchFunctionXrefs(instr.addr))
                                    showMenu = false
                                },
                                onFunctionVariables = {
                                    viewModel.onEvent(DisasmEvent.FetchFunctionVariables(instr.addr))
                                    showMenu = false
                                },
                                onInstructionDetail = {
                                    viewModel.onEvent(DisasmEvent.FetchInstructionDetail(instr.addr))
                                    showMenu = false
                                },
                                onJumpToTarget = { addr ->
                                    onInstructionClick(addr)
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
                    DisasmPlaceholderRow()
                }
            }
        }
        
        // Current address for scrollbar and footer display
        val currentIndex = listState.firstVisibleItemIndex
        val currentAddr = remember(cacheVersion, currentIndex) {
            disasmDataManager.getAddressAt(currentIndex) ?: 0L
        }
        
        // Auto-hiding Fast Scrollbar
        AutoHideAddressScrollbar(
            listState = listState,
            totalItems = loadedCount,
            viewStartAddress = viewStartAddr,
            viewEndAddress = viewEndAddr,
            currentAddress = currentAddr,
            modifier = Modifier.align(Alignment.CenterEnd),
            alwaysShow = true,
            onScrollToAddress = { targetAddr ->
                // During drag: only do immediate scroll within loaded data, no loading
//                val immediateIndex = disasmDataManager.estimateIndexForAddress(targetAddr)
//                val clampedIndex = immediateIndex.coerceIn(0, maxOf(0, disasmDataManager.loadedInstructionCount - 1))
//                coroutineScope.launch {
//                    listState.scrollToItem(clampedIndex)
//                }
                viewModel.scrollbarJumpTo(targetAddr)
            },
            onDragComplete = { targetAddr ->
                // On drag end / tap: treat as a full jump — reset data to avoid address gaps
                viewModel.scrollbarJumpTo(targetAddr)
            }
        )
        
        // Footer: Position Info
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Addr: ${"0x%X".format(currentAddr)}", 
                    fontSize = 12.sp, 
                    fontFamily = LocalAppFont.current
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Loaded: $loadedCount instrs", 
                    fontSize = 12.sp, 
                    fontFamily = LocalAppFont.current
                )
            }
            IconButton(
                onClick = { showDebugControls = !showDebugControls },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Toggle Debug Controls",
                    tint = if (showDebugControls) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                         when(modifyType) {
                            "hex" -> viewModel.onEvent(DisasmEvent.WriteHex(menuTargetAddress!!, value))
                            "string" -> viewModel.onEvent(DisasmEvent.WriteString(menuTargetAddress!!, value))
                            "asm" -> viewModel.onEvent(DisasmEvent.WriteAsm(menuTargetAddress!!, value))
                            "comment" -> viewModel.onEvent(DisasmEvent.WriteComment(menuTargetAddress!!, value))
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
                            top.wsdx233.r2droid.util.R2PipeManager.execute("oo+")
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

        // Function Info Dialog
        val functionInfoState by viewModel.functionInfoState.collectAsState()
        if (functionInfoState.visible) {
            FunctionInfoDialog(
                functionInfo = functionInfoState.data,
                isLoading = functionInfoState.isLoading,
                targetAddress = functionInfoState.targetAddress,
                onDismiss = { viewModel.onEvent(DisasmEvent.DismissFunctionInfo) },
                onRename = { newName ->
                    viewModel.onEvent(
                        DisasmEvent.RenameFunctionFromInfo(functionInfoState.targetAddress, newName)
                    )
                },
                onJump = { addr -> onInstructionClick(addr) }
            )
        }

        // Function Xrefs Dialog
        val functionXrefsState by viewModel.functionXrefsState.collectAsState()
        if (functionXrefsState.visible) {
            FunctionXrefsDialog(
                xrefs = functionXrefsState.data,
                isLoading = functionXrefsState.isLoading,
                targetAddress = functionXrefsState.targetAddress,
                onDismiss = { viewModel.onEvent(DisasmEvent.DismissFunctionXrefs) },
                onJump = { addr -> onInstructionClick(addr) }
            )
        }

        // Function Variables Dialog
        val functionVariablesState by viewModel.functionVariablesState.collectAsState()
        if (functionVariablesState.visible) {
            FunctionVariablesDialog(
                variables = functionVariablesState.data,
                isLoading = functionVariablesState.isLoading,
                targetAddress = functionVariablesState.targetAddress,
                onDismiss = { viewModel.onEvent(DisasmEvent.DismissFunctionVariables) },
                onRename = { oldName, newName ->
                    viewModel.onEvent(
                        DisasmEvent.RenameFunctionVariable(
                            functionVariablesState.targetAddress, oldName, newName
                        )
                    )
                }
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

        AnimatedVisibility(
            visible = showDebugControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            DebugControlBar(
                debugStatus = debugStatus,
                onInitEsil = { viewModel.initEsil() },
                onStepInto = { viewModel.performDebugAction("step") },
                onStepOver = { viewModel.performDebugAction("over") },
                onContinue = { viewModel.performDebugAction("continue") },
                onPause = { viewModel.pauseExecution() },
                onShowRegisters = { showRegisters = true },
                onSettings = { showDebugSettings = true }
            )
        }

        if (showRegisters) {
            RegisterBottomSheet(
                registers = registers,
                onDismissRequest = { showRegisters = false }
            )
        }

        if (showDebugSettings) {
            AlertDialog(
                onDismissRequest = { showDebugSettings = false },
                title = { Text("Debug Backend") },
                text = {
                    Column {
                        val backends = DebugBackend.entries.toTypedArray()
                        backends.forEach { backend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setDebugBackend(backend)
                                        showDebugSettings = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = debugBackend == backend,
                                    onClick = {
                                        viewModel.setDebugBackend(backend)
                                        showDebugSettings = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = backend.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showDebugSettings = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

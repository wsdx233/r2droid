package top.wsdx233.r2droid.feature.hex.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.BinInfo
import top.wsdx233.r2droid.core.data.model.formatCompactAddress
import top.wsdx233.r2droid.core.ui.components.AutoHideScrollbar
import top.wsdx233.r2droid.core.ui.components.FunctionMenuActions
import top.wsdx233.r2droid.core.ui.dialogs.CustomCommandDialog
import top.wsdx233.r2droid.core.ui.dialogs.ModifyDialog
import top.wsdx233.r2droid.core.ui.dialogs.requestByteEditor
import top.wsdx233.r2droid.feature.annotations.AnnotationAction
import top.wsdx233.r2droid.feature.disasm.ui.computeCenteredScrollDelta
import top.wsdx233.r2droid.feature.hex.HexEvent
import top.wsdx233.r2droid.feature.hex.HexExportStatus
import top.wsdx233.r2droid.feature.hex.HexPanelTab
import top.wsdx233.r2droid.feature.hex.HexViewModel
import top.wsdx233.r2droid.feature.hex.data.HexCopyFormat
import top.wsdx233.r2droid.feature.hex.data.HexDataManager
import top.wsdx233.r2droid.feature.hex.data.HexDataManager.Companion.BYTES_PER_ROW
import top.wsdx233.r2droid.feature.hex.data.HexDataManager.Companion.CHUNK_SIZE
import top.wsdx233.r2droid.feature.hex.data.HexDataManager.Companion.ROWS_PER_CHUNK
import top.wsdx233.r2droid.feature.hex.data.defaultExportSliceFileName
import top.wsdx233.r2droid.feature.hex.data.parseHexBytes
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.util.R2PipeManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HexViewer(
    viewModel: HexViewModel,
    hexDataManager: HexDataManager,
    cursorAddress: Long,
    binInfo: BinInfo? = null,
    scrollToSelectionTrigger: StateFlow<Int>,
    onByteClick: (Long) -> Unit,
    onJumpToAddress: (Long) -> Unit = onByteClick,
    onShowXrefs: (Long) -> Unit,
    functionActions: FunctionMenuActions,
    onAnnotate: (AnnotationAction, Long) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val selectionState by viewModel.selectionState.collectAsState()
    val showToolsPanel by viewModel.toolsPanelVisible.collectAsState()
    val selectedToolsTab by viewModel.toolsPanelTab.collectAsState()
    val inspectorState by viewModel.inspectorState.collectAsState()
    val selectionDataState by viewModel.selectionDataState.collectAsState()
    val hashState by viewModel.hashState.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val dataModifiedEvent by viewModel.dataModifiedEvent.collectAsState()

    // Back handler clears active multi-byte selection first (aligned with Disasm multi-select).
    BackHandler(enabled = selectionState.active) {
        viewModel.onEvent(HexEvent.ClearSelection)
    }

    // Sync default endianness and pointer width from binary info until user toggles manually.
    LaunchedEffect(binInfo?.endian, binInfo?.bits) {
        viewModel.onEvent(HexEvent.SyncBinaryDefaults(binInfo?.endian, binInfo?.bits))
    }

    // Menu & Dialog States
    var showMenu by remember { mutableStateOf(false) }
    var menuTargetAddress by remember { mutableStateOf<Long?>(null) }

    // Editing States
    var showKeyboard by remember { mutableStateOf(false) }
    var keyboardSession by remember { mutableStateOf<R2PipeManager.R2Session?>(null) }
    var editingBuffer by remember { mutableStateOf("") } // Stores partial nibble input (0-1 char)

    // Modification Dialogs
    var showModifyDialog by remember { mutableStateOf(false) }
    var modifySession by remember { mutableStateOf<R2PipeManager.R2Session?>(null) }
    var modifyType by remember { mutableStateOf("hex") } // hex, string, asm
    var modifyInitialValue by remember { mutableStateOf<String?>("") }
    var showCustomCommandDialog by remember { mutableStateOf(false) }
    var showFillDialog by remember { mutableStateOf(false) }

    // Reopen in write mode dialog
    var showReopenDialog by remember { mutableStateOf(false) }
    var pendingWriteAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }

    // Binary slice export (wt) via SAF document creator
    var pendingExportRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val exportSliceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val range = pendingExportRange
        pendingExportRange = null
        if (uri != null && range != null) {
            viewModel.exportRangeToUri(context, uri, range.first, range.second)
        }
    }

    LaunchedEffect(exportStatus) {
        when (val status = exportStatus) {
            is HexExportStatus.Success -> {
                val msg = if (status.usedWt) {
                    context.getString(R.string.hex_export_success_wt, status.byteCount)
                } else {
                    context.getString(R.string.hex_export_success_fallback, status.byteCount)
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onEvent(HexEvent.ClearExportStatus)
            }
            is HexExportStatus.Error -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.hex_export_failed, status.message),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.onEvent(HexEvent.ClearExportStatus)
            }
            else -> Unit
        }
    }

    // Keep keyboard and tools panel mutually exclusive so the hex grid stays visible.
    LaunchedEffect(showToolsPanel) {
        if (showToolsPanel && showKeyboard) {
            showKeyboard = false
            editingBuffer = ""
        }
    }

    val totalRows = hexDataManager.totalRows
    if (totalRows <= 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.hex_no_data), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val listState = rememberLazyListState(
        cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.25f) },
        initialFirstVisibleItemIndex = hexDataManager.getRowIndexForAddress(cursorAddress)
    )

    var toolsPanelHeightPx by remember { mutableIntStateOf(0) }
    val occludedBottomPx = if (showToolsPanel) toolsPanelHeightPx else 0
    val currentOccludedBottomPx by rememberUpdatedState(occludedBottomPx)

    // Observe chunks covering cursorAddress so Data Inspector updates immediately as chunks load.
    val cursorRowIndex = hexDataManager.getRowIndexForAddress(cursorAddress)
    val cursorChunkIndex = cursorRowIndex / ROWS_PER_CHUNK
    val cursorChunk by remember(hexDataManager, cursorChunkIndex) { hexDataManager.chunkState(cursorChunkIndex) }
    val nextCursorChunk by remember(hexDataManager, cursorChunkIndex) { hexDataManager.chunkState(cursorChunkIndex + 1) }

    var lastInspectedMod by remember(hexDataManager) { mutableLongStateOf(dataModifiedEvent) }
    LaunchedEffect(
        showToolsPanel,
        cursorAddress,
        hexDataManager,
        cursorChunk,
        nextCursorChunk,
        dataModifiedEvent
    ) {
        if (!showToolsPanel) return@LaunchedEffect
        val modified = dataModifiedEvent != lastInspectedMod
        if (modified) lastInspectedMod = dataModifiedEvent
        viewModel.onEvent(HexEvent.InspectAddress(cursorAddress, forceRefresh = modified))
    }

    var lastSelectionMod by remember(hexDataManager) { mutableLongStateOf(dataModifiedEvent) }
    LaunchedEffect(
        showToolsPanel,
        selectedToolsTab,
        selectionState,
        cursorAddress,
        hexDataManager,
        cursorChunk,
        dataModifiedEvent
    ) {
        if (!showToolsPanel || selectedToolsTab == HexPanelTab.INSPECTOR) return@LaunchedEffect
        val modified = dataModifiedEvent != lastSelectionMod
        if (modified) lastSelectionMod = dataModifiedEvent
        viewModel.onEvent(HexEvent.SyncSelectionData(cursorAddress, forceRefresh = modified))
    }

    // Keep manual scrolling independent of the cursor until selection actually changes.
    LaunchedEffect(hexDataManager, cursorAddress) {
        val targetRowIndex = hexDataManager.getRowIndexForAddress(cursorAddress)
        if (targetRowIndex in 0 until totalRows) {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            val visibleBottom = layoutInfo.viewportEndOffset - currentOccludedBottomPx
            val item = visible.firstOrNull { it.index == targetRowIndex }
            if (item == null) {
                val visibleItems = visible.size
                val centerOffset = if (visibleItems > 0) visibleItems / 3 else 5
                val scrollIndex = (targetRowIndex - centerOffset).coerceIn(0, totalRows - 1)
                listState.animateScrollToItem(scrollIndex)
            } else if (currentOccludedBottomPx > 0 && item.offset + item.size > visibleBottom) {
                val delta = computeCenteredScrollDelta(
                    viewportStart = layoutInfo.viewportStartOffset,
                    viewportEnd = layoutInfo.viewportEndOffset,
                    occludedBottomPx = currentOccludedBottomPx,
                    itemOffset = item.offset,
                    itemSize = item.size
                )
                if (kotlin.math.abs(delta) > 1) listState.animateScrollBy(delta.toFloat())
            }
        }
    }

    // Center cursor row in visible area above the tools panel when panel opens over it.
    var centeredAfterPanelOpen by remember(showToolsPanel) { mutableStateOf(false) }
    LaunchedEffect(showToolsPanel, toolsPanelHeightPx) {
        if (!showToolsPanel || toolsPanelHeightPx <= 0) return@LaunchedEffect
        val targetRowIndex = hexDataManager.getRowIndexForAddress(cursorAddress)
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == targetRowIndex } ?: return@LaunchedEffect
        val visibleBottom = (layout.viewportEndOffset - toolsPanelHeightPx)
            .coerceAtLeast(layout.viewportStartOffset + item.size)
        val needsCentering = !centeredAfterPanelOpen || (item.offset + item.size > visibleBottom)
        centeredAfterPanelOpen = true
        if (needsCentering && item.offset + item.size > visibleBottom) {
            val delta = computeCenteredScrollDelta(
                viewportStart = layout.viewportStartOffset,
                viewportEnd = layout.viewportEndOffset,
                occludedBottomPx = toolsPanelHeightPx,
                itemOffset = item.offset,
                itemSize = item.size
            )
            if (kotlin.math.abs(delta) > 1) listState.animateScrollBy(delta.toFloat())
        }
    }

    // Observe scroll to selection trigger from TopAppBar button
    val scrollToSelectionTriggerValue by scrollToSelectionTrigger.collectAsState()
    LaunchedEffect(hexDataManager, scrollToSelectionTriggerValue) {
        if (scrollToSelectionTriggerValue > 0) {
            val targetRowIndex = hexDataManager.getRowIndexForAddress(cursorAddress)
            if (targetRowIndex in 0 until totalRows) {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo.size
                val centerOffset = if (visibleItems > 0) visibleItems / 2 else 5
                val scrollIndex = (targetRowIndex - centerOffset).coerceIn(0, totalRows - 1)
                listState.animateScrollToItem(scrollIndex)
            }
        }
    }

    LoadVisibleHexChunks(listState, hexDataManager)

    val selectedColumn = ((cursorAddress - hexDataManager.viewStartAddress) and 7L).toInt()
    val gridStyle = rememberHexGridStyle()
    val hexByteText = Color(gridStyle.palette.byteText)

    val copyTextWithToast: (String) -> Unit = { text ->
        if (text.length > 512 * 1024) {
            Toast.makeText(context, R.string.hex_selection_copy_limit_warning, Toast.LENGTH_SHORT).show()
        } else {
            clipboardManager.setText(AnnotatedString(text))
            Toast.makeText(context, R.string.hex_copied_toast, Toast.LENGTH_SHORT).show()
        }
    }

    val copyFormattedBytes: (Long, HexCopyFormat) -> Unit = { targetAddr, format ->
        coroutineScope.launch {
            viewModel.readFormattedSelection(targetAddr, format)
                .onSuccess { copyTextWithToast(it) }
                .onFailure { err ->
                    Toast.makeText(context, err.message ?: context.getString(R.string.common_error), Toast.LENGTH_SHORT).show()
                }
        }
    }

    val openMenu: (Long) -> Unit = { address ->
        menuTargetAddress = address
        showMenu = true
    }
    val selectByte: (Long) -> Unit = { address ->
        if (selectionState.active) {
            viewModel.onEvent(HexEvent.UpdateSelection(address))
        } else if (address == cursorAddress) {
            openMenu(address)
        } else {
            if (showKeyboard) editingBuffer = ""
            onByteClick(address)
        }
    }
    val longClickByte: (Long) -> Unit = { address ->
        showMenu = false
        if (showKeyboard) {
            showKeyboard = false
            editingBuffer = ""
        }
        onByteClick(address)
        viewModel.onEvent(HexEvent.StartSelection(address))
    }

    val menuContent: @Composable (Long) -> Unit = { address ->
        HexContextMenu(
            expanded = true,
            address = address,
            onDismiss = { showMenu = false },
            onCopy = { text ->
                copyTextWithToast(text)
                showMenu = false
            },
            onModify = { type ->
                showMenu = false
                coroutineScope.launch {
                    context.requestByteEditor(onReopen = {
                        pendingWriteAction = it
                        showReopenDialog = true
                    }) {
                        modifySession = it
                        modifyType = type
                        modifyInitialValue = if (type == "hex") "" else null
                        showModifyDialog = true
                    }
                }
            },
            onXrefs = {
                onShowXrefs(address)
                showMenu = false
            },
            onCustomCommand = {
                showCustomCommandDialog = true
                showMenu = false
            },
            functionActions = functionActions,
            onAnnotate = onAnnotate,
            onStartSelection = {
                showMenu = false
                if (showKeyboard) {
                    showKeyboard = false
                    editingBuffer = ""
                }
                onByteClick(address)
                viewModel.onEvent(HexEvent.StartSelection(address))
            },
            onOpenInspector = {
                showMenu = false
                showKeyboard = false
                editingBuffer = ""
                onByteClick(address)
                viewModel.onEvent(HexEvent.SetToolsPanelVisible(true, HexPanelTab.INSPECTOR))
            },
            onCopyBytes = { format ->
                showMenu = false
                copyFormattedBytes(address, format)
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Async fetch for modify dialog initial value
        LaunchedEffect(showModifyDialog, modifyType, menuTargetAddress) {
            if (showModifyDialog && menuTargetAddress != null && modifyInitialValue == null) {
                val addrExpr = "0x${menuTargetAddress!!.toULong().toString(16)}"
                val cmd = when (modifyType) {
                    "string" -> "ps @ $addrExpr"
                    "asm" -> "pi 1 @ $addrExpr"
                    else -> null
                }
                modifyInitialValue = if (cmd != null) {
                    R2PipeManager.execute(cmd, markDirty = false).getOrDefault("").trim()
                } else ""
            }
        }

        // Modify Dialog
        if (showModifyDialog && menuTargetAddress != null) {
            val title = when (modifyType) {
                "hex" -> stringResource(R.string.hex_modify_hex)
                "string" -> stringResource(R.string.hex_modify_string)
                "asm" -> stringResource(R.string.hex_modify_opcode)
                else -> stringResource(R.string.hex_modify_default)
            }
            if (modifyInitialValue != null) {
                ModifyDialog(
                    title = title,
                    initialValue = modifyInitialValue!!,
                    onDismiss = { showModifyDialog = false; modifyInitialValue = "" },
                    onConfirm = { value ->
                        modifySession?.let { session ->
                            when (modifyType) {
                                "hex" -> viewModel.onEvent(HexEvent.WriteHex(session, menuTargetAddress!!, value))
                                "string" -> viewModel.onEvent(HexEvent.WriteString(session, menuTargetAddress!!, value))
                                "asm" -> viewModel.onEvent(HexEvent.WriteAsm(session, menuTargetAddress!!, value))
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
                        TextButton(onClick = { showModifyDialog = false; modifyInitialValue = "" }) {
                            Text(stringResource(R.string.dialog_cancel))
                        }
                    }
                )
            }
        }

        // Fill Selected Bytes Dialog
        if (showFillDialog) {
            var fillValue by remember { mutableStateOf("") }
            val isValidHex = remember(fillValue) { parseHexBytes(fillValue) != null }
            AlertDialog(
                onDismissRequest = { showFillDialog = false },
                title = { Text(stringResource(R.string.hex_fill_title)) },
                text = {
                    OutlinedTextField(
                        value = fillValue,
                        onValueChange = { fillValue = it },
                        label = { Text(stringResource(R.string.hex_fill_hint)) },
                        singleLine = true,
                        isError = fillValue.isNotBlank() && !isValidHex,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val pattern = fillValue
                            showFillDialog = false
                            coroutineScope.launch {
                                context.requestByteEditor(onReopen = {
                                    pendingWriteAction = it
                                    showReopenDialog = true
                                }) { session ->
                                    viewModel.onEvent(HexEvent.FillSelection(session, pattern))
                                }
                            }
                        },
                        enabled = isValidHex
                    ) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFillDialog = false }) {
                        Text(stringResource(R.string.dialog_cancel))
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

        // Reopen in write mode dialog
        if (showReopenDialog) {
            AlertDialog(
                onDismissRequest = { showReopenDialog = false; pendingWriteAction = null },
                title = { Text(stringResource(R.string.reopen_write_title)) },
                text = { Text(stringResource(R.string.reopen_write_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showReopenDialog = false
                        val action = pendingWriteAction
                        pendingWriteAction = null
                        coroutineScope.launch {
                            action?.invoke()
                        }
                    }) {
                        Text(stringResource(R.string.reopen_write_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReopenDialog = false; pendingWriteAction = null }) {
                        Text(stringResource(R.string.reopen_write_cancel))
                    }
                }
            )
        }

        HexGridHeader(selectedColumn, gridStyle)

        // Dark Gray Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(gridStyle.palette.separator)
        )

        val bottomContentPadding = with(density) { occludedBottomPx.toDp() }
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(gridStyle.palette.contentBackground)
                    .padding(end = HexScrollbarWidth),
                contentPadding = PaddingValues(bottom = bottomContentPadding)
            ) {
                // Rows never move relative to each other; the default positional key is sufficient.
                items(totalRows) { rowIndex ->
                    val addr = hexDataManager.getRowAddress(rowIndex)
                    val byteCount = hexDataManager.bytesInRow(rowIndex)
                    val chunkIndex = rowIndex / ROWS_PER_CHUNK
                    val chunk by remember(hexDataManager, chunkIndex) { hexDataManager.chunkState(chunkIndex) }
                    HexGridRow(
                        addr = addr,
                        chunk = chunk,
                        offset = (rowIndex % ROWS_PER_CHUNK) * BYTES_PER_ROW,
                        byteCount = byteCount,
                        cursorAddress = cursorAddress,
                        selectedColumn = selectedColumn,
                        editingBuffer = if (cursorAddress in addr until addr + byteCount) editingBuffer else "",
                        style = gridStyle,
                        onByteClick = selectByte,
                        onByteLongClick = longClickByte,
                        menuAddress = if (showMenu) menuTargetAddress else null,
                        menuContent = menuContent,
                        selectionActive = selectionState.active,
                        selectionStart = selectionState.rangeStart,
                        selectionEnd = selectionState.rangeEnd
                    )
                }
            }

            // Docked panel + scrollbar overlay (mirrors DisasmScreen + DebugPanel layout).
            Column(Modifier.matchParentSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AutoHideScrollbar(
                        listState = listState,
                        totalItems = totalRows,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        trackWidth = HexScrollbarWidth,
                        alwaysShow = true
                    )
                }

                AnimatedVisibility(
                    visible = showToolsPanel,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    HexToolsPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { toolsPanelHeightPx = it.height },
                        selectedTab = selectedToolsTab,
                        onSelectTab = { viewModel.onEvent(HexEvent.SelectToolsPanelTab(it)) },
                        cursorAddress = cursorAddress,
                        selectionState = selectionState,
                        inspectorState = inspectorState,
                        selectionDataState = selectionDataState,
                        hashState = hashState,
                        exportStatus = exportStatus,
                        viewStartAddress = hexDataManager.viewStartAddress,
                        viewEndAddress = hexDataManager.viewEndAddress,
                        onEndiannessChange = { viewModel.onEvent(HexEvent.SetEndianness(it)) },
                        onPointerBitsChange = { viewModel.onEvent(HexEvent.SetPointerBits(it)) },
                        onRefreshDisasm = { viewModel.onEvent(HexEvent.InspectAddress(cursorAddress, forceRefresh = true)) },
                        onJumpToAddress = onJumpToAddress,
                        onStartSelection = { addr -> viewModel.onEvent(HexEvent.StartSelection(addr)) },
                        onSetSelectionRange = { start, len -> viewModel.onEvent(HexEvent.SetSelectionRange(start, len)) },
                        onClearSelection = { viewModel.onEvent(HexEvent.ClearSelection) },
                        onCopyText = copyTextWithToast,
                        onCopyFormatted = { format -> copyFormattedBytes(cursorAddress, format) },
                        onRequestFillSelection = { showFillDialog = true },
                        onRequestExportBinary = { start, count ->
                            pendingExportRange = start to count
                            try {
                                exportSliceLauncher.launch(defaultExportSliceFileName(start, count))
                            } catch (err: Exception) {
                                pendingExportRange = null
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.hex_export_failed, err.message ?: "Error"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onComputeHashes = { start, count ->
                            viewModel.onEvent(HexEvent.ComputeHashes(start, count))
                        },
                        onDismiss = { viewModel.onEvent(HexEvent.SetToolsPanelVisible(false)) }
                    )
                }
            }
        }

        // Footer: Keyboard Toggle | Position / Range / Selection | Data Inspector Toggle
        Row(
            Modifier
                .fillMaxWidth()
                .background(gridStyle.palette.footerBackground)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (showKeyboard) {
                            showKeyboard = false
                            editingBuffer = ""
                        } else {
                            viewModel.onEvent(HexEvent.SetToolsPanelVisible(false))
                            coroutineScope.launch {
                                context.requestByteEditor(onReopen = {
                                    pendingWriteAction = it
                                    showReopenDialog = true
                                }) {
                                    keyboardSession = it
                                    showKeyboard = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = stringResource(R.string.hex_toggle_keyboard_desc),
                        tint = if (showKeyboard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                HexCurrentPosition(listState, hexDataManager, hexByteText)
                Spacer(Modifier.width(8.dp))
                if (selectionState.active) {
                    Text(
                        text = stringResource(
                            R.string.hex_selection_footer_info,
                            formatCompactByteCount(selectionState.byteCount)
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = LocalAppFont.current,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            viewModel.onEvent(HexEvent.SetToolsPanelVisible(true, HexPanelTab.SELECTION))
                        }
                    )
                } else {
                    val range = remember(hexDataManager) {
                        "Range: 0x${formatCompactAddress(hexDataManager.viewStartAddress)}-0x${formatCompactAddress(hexDataManager.viewEndAddress)}"
                    }
                    Text(
                        text = range,
                        fontSize = 12.sp,
                        fontFamily = LocalAppFont.current,
                        color = hexByteText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (showToolsPanel) {
                        viewModel.onEvent(HexEvent.SetToolsPanelVisible(false))
                    } else {
                        showKeyboard = false
                        editingBuffer = ""
                        viewModel.onEvent(HexEvent.SetToolsPanelVisible(true))
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = stringResource(R.string.hex_toggle_inspector_desc),
                    tint = if (showToolsPanel || selectionState.active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // Hex Keyboard
        if (showKeyboard) {
            HexKeyboard(
                onNibbleClick = { char ->
                    // Append nibble to editing buffer
                    val newBuffer = editingBuffer + char
                    if (newBuffer.length >= 2) {
                        // Complete byte - write it
                        val byteValue = newBuffer.take(2)
                        keyboardSession?.let { session ->
                            viewModel.onEvent(HexEvent.WriteHex(session, cursorAddress, byteValue))
                        }
                        editingBuffer = ""
                        // Move to next byte
                        onByteClick(cursorAddress + 1)
                    } else {
                        editingBuffer = newBuffer
                    }
                },
                onBackspace = {
                    if (editingBuffer.isNotEmpty()) {
                        // Delete last character from buffer
                        editingBuffer = editingBuffer.dropLast(1)
                    } else {
                        // Move to previous byte
                        if (cursorAddress > 0) {
                            onByteClick(cursorAddress - 1)
                        }
                    }
                },
                onClose = {
                    showKeyboard = false
                    editingBuffer = ""
                }
            )
        }
    }
}

@Composable
private fun LoadVisibleHexChunks(listState: LazyListState, manager: HexDataManager) {
    // A write/explicit refresh also reloads a stationary viewport. Page arrivals do not.
    LaunchedEffect(listState, manager, manager.refreshGeneration) {
        snapshotFlow {
            val first = listState.firstVisibleItemIndex
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first
            (first / ROWS_PER_CHUNK)..(last / ROWS_PER_CHUNK)
        }.collectLatest { chunks -> manager.loadViewport(chunks.first, chunks.last) }
    }
}

// Only this label observes row-by-row scrolling; the grid, header and dialogs do not.
@Composable
private fun HexCurrentPosition(listState: LazyListState, manager: HexDataManager, color: Color) {
    val address = manager.getRowAddress(listState.firstVisibleItemIndex)
    Text(
        text = "Pos: 0x${formatCompactAddress(address)}",
        fontSize = 12.sp,
        fontFamily = LocalAppFont.current,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

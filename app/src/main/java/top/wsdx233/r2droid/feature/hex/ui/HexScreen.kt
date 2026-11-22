package top.wsdx233.r2droid.feature.hex.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.formatCompactAddress
import top.wsdx233.r2droid.core.ui.components.AutoHideScrollbar
import top.wsdx233.r2droid.core.ui.components.FunctionMenuActions
import top.wsdx233.r2droid.core.ui.dialogs.CustomCommandDialog
import top.wsdx233.r2droid.core.ui.dialogs.ModifyDialog
import top.wsdx233.r2droid.core.ui.dialogs.requestByteEditor
import top.wsdx233.r2droid.feature.annotations.AnnotationAction
import top.wsdx233.r2droid.feature.hex.HexEvent
import top.wsdx233.r2droid.feature.hex.HexViewModel
import top.wsdx233.r2droid.feature.hex.data.HexDataManager
import top.wsdx233.r2droid.feature.hex.data.HexDataManager.Companion.BYTES_PER_ROW
import top.wsdx233.r2droid.feature.hex.data.HexDataManager.Companion.ROWS_PER_CHUNK
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.util.R2PipeManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HexViewer(
    viewModel: HexViewModel,
    hexDataManager: HexDataManager,
    cursorAddress: Long,
    scrollToSelectionTrigger: StateFlow<Int>,
    onByteClick: (Long) -> Unit,
    onShowXrefs: (Long) -> Unit,
    functionActions: FunctionMenuActions,
    onAnnotate: (AnnotationAction, Long) -> Unit
) {
    val context = LocalContext.current
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

    // Reopen in write mode dialog
    var showReopenDialog by remember { mutableStateOf(false) }
    var pendingWriteAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }

    val clipboardManager = LocalClipboardManager.current
    
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
    
    // Coroutine scope for scrollbar interactions
    val coroutineScope = rememberCoroutineScope()
    
    // Keep manual scrolling independent of the cursor until selection actually changes.
    LaunchedEffect(hexDataManager, cursorAddress) {
        val targetRowIndex = hexDataManager.getRowIndexForAddress(cursorAddress)
        if (targetRowIndex in 0 until totalRows) {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            if (visible.none { it.index == targetRowIndex }) {
                val visibleItems = visible.size
                val centerOffset = if (visibleItems > 0) visibleItems / 2 else 5
                val scrollIndex = (targetRowIndex - centerOffset).coerceIn(0, totalRows - 1)
                listState.animateScrollToItem(scrollIndex)
            }
        }
    }
    
    // Observe scroll to selection trigger from TopAppBar button
    val scrollToSelectionTrigger by scrollToSelectionTrigger.collectAsState()
    LaunchedEffect(hexDataManager, scrollToSelectionTrigger) {
        if (scrollToSelectionTrigger > 0) {
            val targetRowIndex = hexDataManager.getRowIndexForAddress(cursorAddress)
            if (targetRowIndex in 0 until totalRows) {
                // Calculate centering
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

    val openMenu: (Long) -> Unit = { address ->
        menuTargetAddress = address
        showMenu = true
    }
    val selectByte: (Long) -> Unit = { address ->
        if (address == cursorAddress) openMenu(address) else {
            if (showKeyboard) editingBuffer = ""
            onByteClick(address)
        }
    }
    val menuContent: @Composable (Long) -> Unit = { address ->
        HexContextMenu(
            expanded = true,
            address = address,
            onDismiss = { showMenu = false },
            onCopy = { text ->
                clipboardManager.setText(AnnotatedString(text))
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
            onAnnotate = onAnnotate
        )
    }
    
    Column(Modifier.fillMaxSize()) {
        // Async fetch for modify dialog initial value
        LaunchedEffect(showModifyDialog, modifyType, menuTargetAddress) {
            if (showModifyDialog && menuTargetAddress != null && modifyInitialValue == null) {
                val cmd = when (modifyType) {
                    "string" -> "ps @ ${menuTargetAddress}"
                    "asm" -> "pi 1 @ ${menuTargetAddress}"
                    else -> null
                }
                modifyInitialValue = if (cmd != null) {
                    top.wsdx233.r2droid.util.R2PipeManager.execute(cmd).getOrDefault("").trim()
                } else ""
            }
        }

        // Modify Dialog
        if (showModifyDialog && menuTargetAddress != null) {
            val title = when(modifyType) {
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
                        androidx.compose.material3.TextButton(onClick = { showModifyDialog = false; modifyInitialValue = "" }) {
                            Text("Cancel")
                        }
                    }
                )
            }
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
                    androidx.compose.material3.TextButton(onClick = {
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
                    androidx.compose.material3.TextButton(onClick = { showReopenDialog = false; pendingWriteAction = null }) {
                        Text(stringResource(R.string.reopen_write_cancel))
                    }
                }
            )
        }

        HexGridHeader(selectedColumn, gridStyle)

        //Dark Gray Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(gridStyle.palette.separator)
        )

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(gridStyle.palette.contentBackground).padding(end = HexScrollbarWidth)
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
                        onByteLongClick = openMenu,
                        menuAddress = if (showMenu) menuTargetAddress else null,
                        menuContent = menuContent
                    )
                }
            }
            AutoHideScrollbar(
                listState = listState,
                totalItems = totalRows,
                modifier = Modifier.align(Alignment.CenterEnd),
                trackWidth = HexScrollbarWidth,
                alwaysShow = true
            )
        }
        

        // Footer: Info & Keyboard Toggle
        Row(
            Modifier
                .fillMaxWidth()
                .background(gridStyle.palette.footerBackground)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (showKeyboard) {
                            showKeyboard = false
                            editingBuffer = ""
                        } else {
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
            }
            val range = remember(hexDataManager) {
                "Range: 0x${formatCompactAddress(hexDataManager.viewStartAddress)}-0x${formatCompactAddress(hexDataManager.viewEndAddress)}"
            }
            Text(range, fontSize = 12.sp, fontFamily = LocalAppFont.current, color = hexByteText)
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
    Text("Pos: 0x${formatCompactAddress(address)}", fontSize = 12.sp, fontFamily = LocalAppFont.current, color = color)
}

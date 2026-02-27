package top.wsdx233.r2droid.feature.hex.ui

/**
 * Virtualized Hex Viewer - uses items(count) pattern for smooth scrolling.
 * 
 * Core design:
 * - LazyColumn knows total rows upfront (totalSize / 16)
 * - Each row is identified by index (stable key)
 * - Data is loaded on-demand from HexDataManager cache
 * - Placeholder shown for unloaded rows
 */
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.AutoHideScrollbar
import top.wsdx233.r2droid.core.ui.dialogs.CustomCommandDialog
import top.wsdx233.r2droid.core.ui.dialogs.ModifyDialog
import top.wsdx233.r2droid.feature.hex.HexEvent
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@SuppressLint("FrequentlyChangingValue")
@Composable
fun HexViewer(
    viewModel: top.wsdx233.r2droid.feature.hex.HexViewModel,
    cursorAddress: Long,
    scrollToSelectionTrigger: kotlinx.coroutines.flow.StateFlow<Int>,
    onByteClick: (Long) -> Unit,
    onShowXrefs: (Long) -> Unit
) {
    val hexDataManager = viewModel.hexDataManager
    
    // Observe cache version to trigger recomposition when chunks load
    val cacheVersion by viewModel.hexCacheVersion.collectAsState()
    
    // Menu & Dialog States
    var showMenu by remember { mutableStateOf(false) }
    var menuTargetAddress by remember { mutableStateOf<Long?>(null) }
    
    // Editing States
    var showKeyboard by remember { mutableStateOf(false) }
    var editingBuffer by remember { mutableStateOf("") } // Stores partial nibble input (0-1 char)
    
    // Modification Dialogs
    var showModifyDialog by remember { mutableStateOf(false) }
    var modifyType by remember { mutableStateOf("hex") } // hex, string, asm
    var modifyInitialValue by remember { mutableStateOf<String?>("") }
    var showCustomCommandDialog by remember { mutableStateOf(false) }

    // Reopen in write mode dialog
    var showReopenDialog by remember { mutableStateOf(false) }
    var pendingWriteAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val clipboardManager = LocalClipboardManager.current
    
    if (hexDataManager == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
    val totalRows = hexDataManager.totalRows
    if (totalRows <= 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.hex_no_data), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    
    // Total file size for scrollbar calculation
    val totalSize = totalRows.toLong() * 16
    
    // Calculate initial scroll position based on cursor
    // Use getRowIndexForAddress for correct mapping with virtual address offsets
    val initialRowIndex = hexDataManager.getRowIndexForAddress(cursorAddress).coerceIn(0, maxOf(0, totalRows - 1))
    
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialRowIndex.coerceAtLeast(0)
    )
    
    // Coroutine scope for scrollbar interactions
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to cursor when it changes, but only if cursor is off-screen
    LaunchedEffect(cursorAddress) {
        val targetRowIndex = hexDataManager.getRowIndexForAddress(cursorAddress)
        if (targetRowIndex in 0 until totalRows) {
            val layoutInfo = listState.layoutInfo
            val visibleIndices = layoutInfo.visibleItemsInfo.map { it.index }
            // Only scroll if the target row is not currently visible
            if (targetRowIndex !in visibleIndices) {
                val visibleItems = visibleIndices.size
                val centerOffset = if (visibleItems > 0) visibleItems / 2 else 5
                val scrollIndex = (targetRowIndex - centerOffset).coerceIn(0, totalRows - 1)
                listState.animateScrollToItem(scrollIndex)
            }
        }
    }
    
    // Observe scroll to selection trigger from TopAppBar button
    val scrollToSelectionTrigger by scrollToSelectionTrigger.collectAsState()
    LaunchedEffect(scrollToSelectionTrigger) {
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
    
    // Preload chunks based on visible items
    LaunchedEffect(listState.firstVisibleItemIndex, cacheVersion) {
        val firstVisible = listState.firstVisibleItemIndex
        val addr = hexDataManager.getRowAddress(firstVisible)
        viewModel.onEvent(HexEvent.PreloadHex(addr))
    }

    // Calculate which column is selected (0-7 for each 8-byte row)
    val selectedColumn = (cursorAddress % 8).toInt()
    // Light paper yellow for highlighting - 30% transparent overlay
    val highlightColor = Color(0x4DFFFDE7) // ~30% alpha yellow
    
    // Load colors from resources for theme support
    val hexHeaderBackground = colorResource(R.color.hex_header_background)
    val hexContentBackground = colorResource(R.color.hex_content_background)
    val hexFooterBackground = colorResource(R.color.hex_footer_background)
    val hexAddressBackground = colorResource(R.color.hex_address_background)
    val hexAddressText = colorResource(R.color.hex_address_text)
    val hexRowEven = colorResource(R.color.hex_row_even)
    val hexRowOdd = colorResource(R.color.hex_row_odd)
    val hexDivider = colorResource(R.color.hex_divider)
    val hexSeparatorLine = colorResource(R.color.hex_separator_line)
    val hexByteText = colorResource(R.color.hex_byte_text)
    val hexColumnHeaderText = colorResource(R.color.hex_column_header_text)
    
    Column(Modifier.fillMaxSize()) {
        // Overlay container for Menu and Dialogs

        
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
                         when(modifyType) {
                            "hex" -> viewModel.onEvent(HexEvent.WriteHex(menuTargetAddress!!, value))
                            "string" -> viewModel.onEvent(HexEvent.WriteString(menuTargetAddress!!, value))
                            "asm" -> viewModel.onEvent(HexEvent.WriteAsm(menuTargetAddress!!, value))
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
                            top.wsdx233.r2droid.util.R2PipeManager.execute("oo+")
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

        // Sticky Header: 0 1 2 3 4 5 6 7
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(hexHeaderBackground)
                .padding(end = 8.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Empty space for address column (matching data row)
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Empty - just spacing to match address column below
            }
            
            // Divider matching data row
            VerticalDivider()
            
            // 8 columns with divider between 4th and 5th (index 3 and 4)
            Row(Modifier.weight(1f).padding(vertical = 4.dp)) {
                (0..7).forEach { colIndex ->
                    // Add divider before column 4
                    if (colIndex == 4) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(hexDivider)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f),
                            contentAlignment = Alignment.Center
                    ) {
                        // Overlay highlight for selected column
                        if (colIndex == selectedColumn) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(highlightColor)
                            )
                        }
                        Text(
                            colIndex.toString(),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = hexColumnHeaderText
                        )
                    }
                }
            }
            
            // Divider matching data row
            VerticalDivider()
            
            // ASCII header
            Row(
                Modifier.width(100.dp).padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                (0..7).forEach { i ->
                    Box(
                        modifier = Modifier
                            .width(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Overlay highlight for selected column
                        if (i == selectedColumn) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(highlightColor)
                            )
                        }
                        Text(
                            i.toString(),
                            fontSize = 12.sp,
                            color = hexColumnHeaderText
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
        }

        //Dark Gray Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hexSeparatorLine)
        )

        
        Box(Modifier.weight(1f)) {
            // Removed SelectionContainer to fix crash on long press
            // (java.lang.IllegalArgumentException: Comparison method violates its general contract!)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(hexContentBackground).padding(end = 8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    // Virtualized items - use count instead of list
                    items(
                        count = totalRows,
                        key = { index -> index } // Stable key for smooth scrolling
                    ) { rowIndex ->
                        // Calculate address using HexDataManager (supports virtual address offsets)
                        val addr = hexDataManager.getRowAddress(rowIndex)
                        
                        // Trigger loading for this row's chunk
                        LaunchedEffect(rowIndex) {
                            viewModel.onEvent(HexEvent.LoadHexChunk(addr))
                        }
                        
                        // Force re-read when cache version changes
                        val bytes = remember(cacheVersion, rowIndex) {
                            hexDataManager.getRowData(rowIndex)
                        }
                        
                        if (bytes != null) {
                            // Split into 8-byte rows for display
                            val b1 = bytes.take(8).toList()
                            val b2 = bytes.drop(8).toList()
                            
                            HexVisualRow(
                                addr = addr,
                                bytes = b1.map { it },
                                index = 0,
                                cursorAddress = cursorAddress,
                                selectedColumn = selectedColumn,
                                highlightColor = highlightColor,
                                onByteClick = { clickedAddr ->
                                    if (clickedAddr == cursorAddress) {
                                        // Already selected, show context menu
                                        menuTargetAddress = clickedAddr
                                        showMenu = true
                                    } else if (showKeyboard) {
                                        // If keyboard is open, clicking just moves cursor and resets buffer
                                        onByteClick(clickedAddr)
                                        editingBuffer = ""
                                    } else {
                                        onByteClick(clickedAddr)
                                    }
                                },
                                onByteLongClick = { clickedAddr ->
                                    menuTargetAddress = clickedAddr
                                    showMenu = true
                                },
                                showMenu = showMenu && menuTargetAddress != null,
                                menuTargetAddress = menuTargetAddress,
                                menuContent = {
                                    if (menuTargetAddress != null) {
                                        HexContextMenu(
                                            expanded = showMenu,
                                            address = menuTargetAddress!!,
                                            onDismiss = { showMenu = false },
                                            onCopy = { text ->
                                                clipboardManager.setText(AnnotatedString(text))
                                                showMenu = false
                                            },
                                            onModify = { type ->
                                                showMenu = false
                                                coroutineScope.launch {
                                                    val isWritable = try {
                                                        val ijResult = top.wsdx233.r2droid.util.R2PipeManager.execute("ij").getOrDefault("{}")
                                                        org.json.JSONObject(ijResult).getJSONObject("core").getBoolean("iorw")
                                                    } catch (_: Exception) { false }
                                                    if (isWritable) {
                                                        modifyType = type
                                                        showModifyDialog = true
                                                        modifyInitialValue = if (type == "hex") "" else null
                                                    } else {
                                                        pendingWriteAction = {
                                                            modifyType = type
                                                            showModifyDialog = true
                                                            modifyInitialValue = if (type == "hex") "" else null
                                                        }
                                                        showReopenDialog = true
                                                    }
                                                }
                                            },
                                            onXrefs = {
                                                onShowXrefs(menuTargetAddress!!)
                                                showMenu = false
                                            },
                                            onCustomCommand = {
                                                showCustomCommandDialog = true
                                                showMenu = false
                                            },
                                        )
                                    }
                                },
                                editingBuffer = if (cursorAddress >= addr && cursorAddress < addr + 8) editingBuffer else "",
                                hexAddressBackground = hexAddressBackground,
                                hexAddressText = hexAddressText,
                                hexRowEven = hexRowEven,
                                hexRowOdd = hexRowOdd,
                                hexDivider = hexDivider,
                                hexByteText = hexByteText
                            )
                            if (b2.isNotEmpty()) {
                                HexVisualRow(
                                    addr = addr + 8,
                                    bytes = b2.map { it },
                                    index = 1,
                                    cursorAddress = cursorAddress,
                                    selectedColumn = selectedColumn,
                                    highlightColor = highlightColor,
                                    onByteClick = { clickedAddr ->
                                        if (clickedAddr == cursorAddress) {
                                            // Already selected, show context menu
                                            menuTargetAddress = clickedAddr
                                            showMenu = true
                                        } else if (showKeyboard) {
                                            onByteClick(clickedAddr)
                                            editingBuffer = ""
                                        } else {
                                            onByteClick(clickedAddr)
                                        }
                                    },
                                    onByteLongClick = { clickedAddr ->
                                        menuTargetAddress = clickedAddr
                                        showMenu = true
                                    },
                                    showMenu = showMenu && menuTargetAddress != null,
                                    menuTargetAddress = menuTargetAddress,
                                    menuContent = {
                                        if (menuTargetAddress != null) {
                                            HexContextMenu(
                                                expanded = showMenu,
                                                address = menuTargetAddress!!,
                                                onDismiss = { showMenu = false },
                                                onCopy = { text ->
                                                    clipboardManager.setText(AnnotatedString(text))
                                                    showMenu = false
                                                },
                                                onModify = { type ->
                                                    showMenu = false
                                                    coroutineScope.launch {
                                                        val isWritable = try {
                                                            val ijResult = top.wsdx233.r2droid.util.R2PipeManager.execute("ij").getOrDefault("{}")
                                                            org.json.JSONObject(ijResult).getJSONObject("core").getBoolean("iorw")
                                                        } catch (_: Exception) { false }
                                                        if (isWritable) {
                                                            modifyType = type
                                                            showModifyDialog = true
                                                            modifyInitialValue = if (type == "hex") "" else null
                                                        } else {
                                                            pendingWriteAction = {
                                                                modifyType = type
                                                                showModifyDialog = true
                                                                modifyInitialValue = if (type == "hex") "" else null
                                                            }
                                                            showReopenDialog = true
                                                        }
                                                    }
                                                },
                                                onXrefs = {
                                                    onShowXrefs(menuTargetAddress!!)
                                                    showMenu = false
                                                },
                                                onCustomCommand = {
                                                    showCustomCommandDialog = true
                                                    showMenu = false
                                                }
                                            )
                                        }
                                    },
                                    editingBuffer = if (cursorAddress >= addr && cursorAddress < addr + 8) editingBuffer else "",
                                    hexAddressBackground = hexAddressBackground,
                                    hexAddressText = hexAddressText,
                                    hexRowEven = hexRowEven,
                                    hexRowOdd = hexRowOdd,
                                    hexDivider = hexDivider,
                                    hexByteText = hexByteText
                                )
                            }
                        } else {
                            // Placeholder row (skeleton)
                            HexPlaceholderRow(addr, hexAddressBackground, hexAddressText, hexDivider)
                        }
                    }
                }
            // End of LazyColumn (SelectionContainer was removed)
            
            // Auto-hiding Fast Scrollbar
            if (totalSize > 0) {
                AutoHideScrollbar(
                    listState = listState,
                    totalItems = totalRows,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    alwaysShow = true,
                    onScrollToIndex = { targetRow ->
                        // Index-based scrollbar, no additional action needed
                    }
                )
            }
        }
        

        // Footer: Info & Keyboard Toggle
        Row(
            Modifier
                .fillMaxWidth()
                .background(hexFooterBackground)
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
                                val isWritable = try {
                                    val ijResult = top.wsdx233.r2droid.util.R2PipeManager.execute("ij").getOrDefault("{}")
                                    org.json.JSONObject(ijResult).getJSONObject("core").getBoolean("iorw")
                                } catch (_: Exception) { false }
                                if (isWritable) {
                                    showKeyboard = true
                                } else {
                                    pendingWriteAction = { showKeyboard = true }
                                    showReopenDialog = true
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
                val currentPos = hexDataManager.getRowAddress(listState.firstVisibleItemIndex)
                Text("Pos: ${"0x%X".format(currentPos)}", fontSize = 12.sp, fontFamily = LocalAppFont.current, color = hexByteText)
            }
            if (totalSize > 0L) {
                Text("Range: ${"0x%X".format(hexDataManager.viewStartAddress)}-${"0x%X".format(hexDataManager.viewEndAddress)}", fontSize = 12.sp, fontFamily = LocalAppFont.current, color = hexByteText)
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
                        viewModel.onEvent(HexEvent.WriteHex(cursorAddress, byteValue))
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

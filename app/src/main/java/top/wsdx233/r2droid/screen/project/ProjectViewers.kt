package top.wsdx233.r2droid.screen.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.data.HexDataManager
import top.wsdx233.r2droid.data.model.*

/**
 * Virtualized Hex Viewer - uses items(count) pattern for smooth scrolling.
 * 
 * Core design:
 * - LazyColumn knows total rows upfront (totalSize / 16)
 * - Each row is identified by index (stable key)
 * - Data is loaded on-demand from HexDataManager cache
 * - Placeholder shown for unloaded rows
 */
@Composable
fun HexViewer(
    viewModel: ProjectViewModel,
    cursorAddress: Long,
    onByteClick: (Long) -> Unit
) {
    val hexDataManager = viewModel.hexDataManager
    
    // Observe cache version to trigger recomposition when chunks load
    val cacheVersion by viewModel.hexCacheVersion.collectAsState()
    
    if (hexDataManager == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
    val totalRows = hexDataManager.totalRows
    if (totalRows <= 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Data", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    
    // Total file size for scrollbar calculation
    val totalSize = totalRows.toLong() * 16
    
    // Calculate initial scroll position based on cursor
    // Use getRowIndexForAddress for correct mapping with virtual address offsets
    val initialRowIndex = hexDataManager.getRowIndexForAddress(cursorAddress).coerceIn(0, maxOf(0, totalRows - 1))
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialRowIndex.coerceAtLeast(0)
    )
    
    // Coroutine scope for scrollbar interactions
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    
    // Auto-scroll to cursor when it changes
    LaunchedEffect(cursorAddress) {
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
    
    // Observe scroll to selection trigger from TopAppBar button
    val scrollToSelectionTrigger by viewModel.scrollToSelectionTrigger.collectAsState()
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
        viewModel.preloadHexAround(addr)
    }

    // Calculate which column is selected (0-7 for each 8-byte row)
    val selectedColumn = (cursorAddress % 8).toInt()
    // Light paper yellow for highlighting - 30% transparent overlay
    val highlightColor = Color(0x4DFFFDE7) // ~30% alpha yellow
    
    Column(Modifier.fillMaxSize()) {
        // Sticky Header: 0 1 2 3 4 5 6 7
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE0E0E0))
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
                                .background(Color(0xFFBDBDBD))
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 12.sp,
                            color = Color.Gray
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
                            color = Color.Gray
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
                .background(Color(0xFFAAAAAA))
        )

        
        Box(Modifier.weight(1f)) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(Color(0xFFF0F0F0)),
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
                            viewModel.loadHexChunkForAddress(addr)
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
                                onByteClick = onByteClick
                            )
                            if (b2.isNotEmpty()) {
                                HexVisualRow(
                                    addr = addr + 8,
                                    bytes = b2.map { it },
                                    index = 1,
                                    cursorAddress = cursorAddress,
                                    selectedColumn = selectedColumn,
                                    highlightColor = highlightColor,
                                    onByteClick = onByteClick
                                )
                            }
                        } else {
                            // Placeholder row (skeleton)
                            HexPlaceholderRow(addr)
                        }
                    }
                }
            }
            
            // Fast Scrollbar
            if (totalSize > 0) {
                 // Current scroll position in virtual address space
                 val currentPos = hexDataManager.getRowAddress(listState.firstVisibleItemIndex)
                 val viewStart = hexDataManager.viewStartAddress
                 
                 // Custom Vertical Scrollbar
                 Box(
                     modifier = Modifier
                         .align(Alignment.CenterEnd)
                         .fillMaxHeight()
                         .width(24.dp)
                         .background(Color.Transparent)
                         .pointerInput(Unit) {
                             detectVerticalDragGestures { change, _ ->
                                 val height = size.height
                                 val newY = (change.position.y / height).coerceIn(0f, 1f)
                                 val targetRow = (newY * totalRows).toInt()
                                 // Scroll to the target row
                                 coroutineScope.launch {
                                     listState.scrollToItem(targetRow.coerceIn(0, maxOf(0, totalRows - 1)))
                                 }
                             }
                         }
                         .pointerInput(Unit) {
                             detectTapGestures { offset ->
                                 val height = size.height
                                 val newY = (offset.y / height).coerceIn(0f, 1f)
                                 val targetRow = (newY * totalRows).toInt()
                                 coroutineScope.launch {
                                     listState.scrollToItem(targetRow.coerceIn(0, maxOf(0, totalRows - 1)))
                                 }
                             }
                         }
                 ) {
                     // Calculate thumb position relative to virtual address range
                     val thumbY = ((currentPos - viewStart).toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                     val bias = (thumbY * 2 - 1).coerceIn(-1f, 1f)
                     Box(
                         Modifier
                             .align(androidx.compose.ui.BiasAlignment(0f, bias))
                             .size(8.dp, 40.dp)
                             .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                     )
                 }
            }
        }
        
        // Footer: Info
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val currentPos = hexDataManager.getRowAddress(listState.firstVisibleItemIndex)
            Text("Pos: ${"0x%X".format(currentPos)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            if (totalSize > 0L) {
                Text("Range: ${"0x%X".format(hexDataManager.viewStartAddress)}-${"0x%X".format(hexDataManager.viewEndAddress)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/**
 * Placeholder row shown when data is not yet loaded.
 */
@Composable
fun HexPlaceholderRow(addr: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA))
            .height(IntrinsicSize.Min)
    ) {
        // Address
        Box(
            modifier = Modifier
                .width(70.dp)
                .fillMaxHeight()
                .background(Color(0xFFDDDDDD))
                .padding(start = 4.dp, top = 2.dp)
        ) {
            Text(
                text = "%06X".format(addr),
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 14.sp
            )
        }
        
        VerticalDivider()
        
        // Placeholder hex area
        Row(Modifier.weight(1f)) {
            repeat(8) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .padding(2.dp)
                        .background(Color(0xFFE0E0E0), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                )
            }
        }
        
        VerticalDivider()
        
        // Placeholder ASCII area
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(20.dp)
                .padding(4.dp)
                .background(Color(0xFFE0E0E0), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun HexVisualRow(
    addr: Long, 
    bytes: List<Byte>, 
    index: Int, 
    cursorAddress: Long,
    selectedColumn: Int,
    highlightColor: Color,
    onByteClick: (Long) -> Unit
) {
    // 8 bytes row
    val oddRow = (addr / 8) % 2 == 1L
    
    // Check if this row contains the cursor
    val rowStartAddr = addr
    val rowEndAddr = addr + bytes.size - 1
    val isRowSelected = cursorAddress >= rowStartAddr && cursorAddress <= rowEndAddr
    
    // Base background: alternating colors (zebra stripes)
    val baseBgColor = if (oddRow) Color(0xFFE8EAF6) else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(baseBgColor)
            .height(IntrinsicSize.Min)
    ) {
        // Address with gray background - CENTERED, BOLD, DARK GRAY
        Box(
            modifier = Modifier
                .width(70.dp)
                .fillMaxHeight()
                .background(Color(0xFFDDDDDD)), // Gray background for address
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "%06X".format(addr), 
                color = Color(0xFF424242), // Dark gray
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp
            )
        }
        
        VerticalDivider()
        
        // Hex - with divider between 4th and 5th columns, and column highlighting
        Row(Modifier.weight(1f).fillMaxHeight()) {
             bytes.forEachIndexed { i, b ->
                val byteAddr = addr + i
                val isSelected = (byteAddr == cursorAddress)
                val isColumnHighlighted = (i == selectedColumn)
                
                // Add divider before column 4 (between 3rd and 4th column, 0-indexed)
                if (i == 4) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFBDBDBD))
                    )
                }
                
                // Background: base stays transparent, overlay highlight if needed
                // For selected cell: use primary container
                // For column/row highlight: use semi-transparent yellow overlay
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onByteClick(byteAddr) }
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    // Overlay: row highlight or column highlight (30% transparent yellow)
                    if (!isSelected && (isRowSelected || isColumnHighlighted)) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(highlightColor)
                        )
                    }
                    Text(
                         text = "%02X".format(b),
                         fontFamily = FontFamily.Monospace,
                         fontSize = 13.sp,
                         color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black,
                         textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                         fontWeight = FontWeight.Medium
                    )
                }
             }
             // Padding if < 8 bytes
             repeat(8 - bytes.size) { padIndex ->
                 val actualIndex = bytes.size + padIndex
                 // Add divider before column 4 even in padding area
                 if (actualIndex == 4) {
                     Box(
                         modifier = Modifier
                             .width(1.dp)
                             .fillMaxHeight()
                             .background(Color(0xFFBDBDBD))
                     )
                 }
                 Spacer(Modifier.weight(1f))
             }
        }
        
        VerticalDivider()
        
        // ASCII with column highlighting
        Row(
            Modifier.width(100.dp).padding(start = 4.dp)
        ) {
            bytes.forEachIndexed { i, b ->
                val byteAddr = addr + i
                val isSelected = (byteAddr == cursorAddress)
                val isColumnHighlighted = (i == selectedColumn)
                val c = b.toInt().toChar()
                val charStr = if (c.isISOControl() || !c.isDefined()) "." else c.toString()
                
                // Background: transparent, overlay highlight if needed
                
                Box(
                    modifier = Modifier
                        .width(12.dp) // Fixed width per char approx
                        .clickable { onByteClick(byteAddr) }
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    // Overlay: row highlight or column highlight (30% transparent yellow)
                    if (!isSelected && (isRowSelected || isColumnHighlighted)) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(highlightColor)
                        )
                    }
                     Text(
                        text = charStr,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black
                    )
                }
            }
        }
    }
}


/**
 * Virtualized Disassembly Viewer - uses DisasmDataManager for smooth infinite scrolling.
 * 
 * Core design:
 * - LazyColumn displays loaded instructions from DisasmDataManager
 * - Data is loaded on-demand as user scrolls
 * - Custom fast scrollbar for quick navigation
 * - Placeholder shown for unloaded regions
 */
@Composable
fun DisassemblyViewer(
    viewModel: ProjectViewModel,
    cursorAddress: Long,
    onInstructionClick: (Long) -> Unit
) {
    val disasmDataManager = viewModel.disasmDataManager
    val cacheVersion by viewModel.disasmCacheVersion.collectAsState()
    val xrefsState by viewModel.xrefsState.collectAsState()
    
    // Menu & Dialog States
    var showMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    var menuTargetAddress by remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    
    var showModifyDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var modifyType by remember { androidx.compose.runtime.mutableStateOf("hex") } // hex, string, asm
    var showCustomCommandDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    
    val clipboardManager = LocalClipboardManager.current
    
    if (disasmDataManager == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
    val loadedCount = remember(cacheVersion) { disasmDataManager.loadedInstructionCount }
    
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
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, maxOf(0, loadedCount - 1))
    )
    
    // Coroutine scope for scrollbar interactions
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    
    // Track previous cursor address to only scroll when it actually changes
    var previousCursorAddress by remember { androidx.compose.runtime.mutableStateOf(cursorAddress) }
    var hasInitiallyScrolled by remember { androidx.compose.runtime.mutableStateOf(false) }
    
    // Auto-scroll to cursor ONLY when cursorAddress changes (not on data load)
    LaunchedEffect(cursorAddress) {
        // Skip if this is just the initial composition with same address
        if (hasInitiallyScrolled && cursorAddress == previousCursorAddress) {
            return@LaunchedEffect
        }
        
        previousCursorAddress = cursorAddress
        hasInitiallyScrolled = true
        
        val targetIndex = disasmDataManager.findClosestIndex(cursorAddress)
        if (targetIndex >= 0 && targetIndex < disasmDataManager.loadedInstructionCount) {
            // Calculate centering
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo.size
            val centerOffset = if (visibleItems > 0) visibleItems / 2 else 5
            val scrollIndex = (targetIndex - centerOffset).coerceIn(0, disasmDataManager.loadedInstructionCount - 1)
            listState.animateScrollToItem(scrollIndex)
        }
    }
    
    // Observe scroll to selection trigger from TopAppBar button
    val scrollToSelectionTrigger by viewModel.scrollToSelectionTrigger.collectAsState()
    LaunchedEffect(scrollToSelectionTrigger) {
        if (scrollToSelectionTrigger > 0) {
            val targetIndex = disasmDataManager.findClosestIndex(cursorAddress)
            if (targetIndex >= 0 && targetIndex < disasmDataManager.loadedInstructionCount) {
                // Calculate centering
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo.size
                val centerOffset = if (visibleItems > 0) visibleItems / 2 else 5
                val scrollIndex = (targetIndex - centerOffset).coerceIn(0, disasmDataManager.loadedInstructionCount - 1)
                listState.animateScrollToItem(scrollIndex)
            }
        }
    }
    
    // Load more when near edges - use snapshotFlow for better control
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0,
                disasmDataManager.loadedInstructionCount
            )
        }.collect { (firstVisible, lastVisible, total) ->
            // Preload around visible area
            val currentInstr = disasmDataManager.getInstructionAt(firstVisible)
            if (currentInstr != null) {
                viewModel.preloadDisasmAround(currentInstr.addr)
            }
            
            // Load more at top
            if (firstVisible < 10 && firstVisible > 0) {
                viewModel.loadDisasmMore(false)
            }
            
            // Load more at bottom
            if (total > 0 && lastVisible > total - 10) {
                viewModel.loadDisasmMore(true)
            }
        }
    }
    
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 8.dp, end = 32.dp, top = 8.dp, bottom = 8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(
                count = loadedCount,
                key = { index -> 
                    disasmDataManager.getInstructionAt(index)?.addr ?: index.toLong()
                }
            ) { index ->
                // Force re-read when cache version changes
                val instr = remember(cacheVersion, index) {
                    disasmDataManager.getInstructionAt(index)
                }
                
                if (instr != null) {
                    val isThisRowMenuTarget = showMenu && menuTargetAddress == instr.addr
                    DisasmRow(
                        instr = instr, 
                        isSelected = instr.addr == cursorAddress, 
                        onClick = { 
                            if (instr.addr == cursorAddress) {
                                // Already selected, show menu
                                menuTargetAddress = instr.addr
                                showMenu = true
                            } else {
                                onInstructionClick(instr.addr) 
                            }
                        },
                        onLongClick = {
                            // 仅设置菜单目标地址并显示菜单，不改变当前选中状态
                            menuTargetAddress = instr.addr
                            showMenu = true
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
                                    modifyType = type
                                    showModifyDialog = true
                                    showMenu = false
                                },
                                onXrefs = {
                                    viewModel.fetchXrefs(instr.addr)
                                    showMenu = false
                                },
                                onCustomCommand = {
                                    showCustomCommandDialog = true
                                    showMenu = false
                                }
                            )
                        }
                    )
                } else {
                    // Placeholder row
                    DisasmPlaceholderRow()
                }
            }
        }
        
        // Fast Scrollbar
        val currentIndex = listState.firstVisibleItemIndex
        val currentAddr = remember(cacheVersion, currentIndex) {
            disasmDataManager.getAddressAt(currentIndex) ?: 0L
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(24.dp)
                .background(Color.Transparent)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        val height = size.height
                        val newY = (change.position.y / height).coerceIn(0f, 1f)
                        // Estimate target address based on position within virtual address range
                        val targetAddr = viewStartAddr + (newY * totalAddressRange).toLong()
                        // Find closest index
                        val targetIndex = disasmDataManager.estimateIndexForAddress(targetAddr)
                        val clampedIndex = targetIndex.coerceIn(0, maxOf(0, disasmDataManager.loadedInstructionCount - 1))
                        // Scroll and load more if needed
                        coroutineScope.launch {
                            listState.scrollToItem(clampedIndex)
                            // Also trigger loading around this address
                            viewModel.loadDisasmChunkForAddress(targetAddr)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val height = size.height
                        val newY = (offset.y / height).coerceIn(0f, 1f)
                        // Estimate target address within virtual address range
                        val targetAddr = viewStartAddr + (newY * totalAddressRange).toLong()
                        val targetIndex = disasmDataManager.estimateIndexForAddress(targetAddr)
                        val clampedIndex = targetIndex.coerceIn(0, maxOf(0, disasmDataManager.loadedInstructionCount - 1))
                        coroutineScope.launch {
                            listState.scrollToItem(clampedIndex)
                            viewModel.loadDisasmChunkForAddress(targetAddr)
                        }
                    }
                }
        ) {
            // Calculate thumb position relative to virtual address range
            val thumbY = if (totalAddressRange > 0 && currentAddr >= viewStartAddr) {
                ((currentAddr - viewStartAddr).toFloat() / totalAddressRange.toFloat()).coerceIn(0f, 1f)
            } else if (loadedCount > 0) {
                (currentIndex.toFloat() / loadedCount.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val bias = (thumbY * 2 - 1).coerceIn(-1f, 1f)
            Box(
                Modifier
                    .align(androidx.compose.ui.BiasAlignment(0f, bias))
                    .size(8.dp, 40.dp)
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            )
        }
        
        // Footer: Position Info
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Addr: ${"0x%X".format(currentAddr)}", 
                fontSize = 12.sp, 
                fontFamily = FontFamily.Monospace
            )
            Text(
                "Loaded: $loadedCount instrs", 
                fontSize = 12.sp, 
                fontFamily = FontFamily.Monospace
            )
        }
        
        // Context Menu
        // Xrefs Dialog
        if (xrefsState.visible) {
             if (xrefsState.isLoading) {
                 AlertDialog(
                     onDismissRequest = { viewModel.dismissXrefs() },
                     title = { Text("Loading Xrefs...") },
                     text = { 
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                     },
                     confirmButton = {}
                 )
             } else {
                 XrefsDialog(
                     xrefsData = xrefsState.data,
                     targetAddress = xrefsState.targetAddress,
                     onDismiss = { viewModel.dismissXrefs() },
                     onJump = { addr ->
                         viewModel.jumpToAddress(addr)
                         viewModel.dismissXrefs()
                     }
                 )
             }
        }
        
        // Modify Dialog
        if (showModifyDialog && menuTargetAddress != null) {
            val title = when(modifyType) {
                "hex" -> "Modify Hex (wx)"
                "string" -> "Modify String (w)"
                "asm" -> "Modify Opcode (wa)"
                else -> "Modify"
            }
            ModifyDialog(
                title = title,
                initialValue = "",
                onDismiss = { showModifyDialog = false },
                onConfirm = { value ->
                     when(modifyType) {
                        "hex" -> viewModel.writeHex(menuTargetAddress!!, value)
                        "string" -> viewModel.writeString(menuTargetAddress!!, value)
                        "asm" -> viewModel.writeAsm(menuTargetAddress!!, value)
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
    }
}

/**
 * Placeholder row shown when instruction data is not yet loaded.
 */
@Composable
fun DisasmPlaceholderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        // Address placeholder
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(18.dp)
                .padding(end = 4.dp)
                .background(Color(0xFFE0E0E0), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
        // Bytes placeholder
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(18.dp)
                .padding(end = 4.dp)
                .background(Color(0xFFE0E0E0), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
        // Disasm placeholder
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .background(Color(0xFFE0E0E0), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun DisasmRow(
    instr: DisasmInstruction, 
    isSelected: Boolean, 
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showMenu: Boolean = false,
    menuContent: @Composable () -> Unit = {}
) {
    // Cutter style coloring logic
    val opcodeColor = when (instr.type) {
        "call", "ucall" -> Color(0xFF42A5F5) // Blue
        "jmp", "cjmp", "ujmp" -> Color(0xFF66BB6A) // Green
        "ret" -> Color(0xFFEF5350) // Red
        "push", "pop", "rpush" -> Color(0xFFAB47BC) // Purple
        "cmp", "test" -> Color(0xFFFFCA28) // Orange/Yellow
        "nop" -> Color.Gray
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .pointerInput(onClick, onLongClick) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                }
                .padding(vertical = 1.dp) // tighter spacing
        ) {
            // Addr
            Text(
                text = "0x%08x".format(instr.addr),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.width(90.dp)
            )
            
            // Bytes (Optional - usually hidden in graph mode but shown in linear)
            val bytesStr = if (instr.bytes.length > 12) instr.bytes.take(12) + ".." else instr.bytes
            Text(
                text = bytesStr.padEnd(14),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.DarkGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.width(100.dp)
            )
            
            // Opcode / Disasm
            Text(
                text = instr.disasm,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else opcodeColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if(instr.type in listOf("call", "jmp", "ret")) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Render menu inside Box so it anchors to this row
        if (showMenu) {
            menuContent()
        }
    }
}

@Composable
fun DecompilationViewer(
    viewModel: ProjectViewModel,
    data: DecompilationData,
    cursorAddress: Long,
    onAddressClick: (Long) -> Unit
) {
    if (data.code.isBlank()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Decompilation Data", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    var textLayoutResult by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val scrollState = androidx.compose.foundation.rememberScrollState()

    // Split code into lines for line number display
    val lines = data.code.lines()
    
    // Build line-to-offset mapping for scrolling
    val lineToOffset = remember(data.code) {
        var offset = 0
        lines.mapIndexed { index, line ->
            val start = offset
            offset += line.length + 1 // +1 for newline
            index to start
        }.toMap()
    }
    
    // Find which line corresponds to cursor address
    val cursorLineIndex = remember(cursorAddress, data.annotations) {
        if (cursorAddress == 0L) -1
        else {
            val note = data.annotations.firstOrNull { it.offset == cursorAddress }
            if (note != null && note.start >= 0 && note.start < data.code.length) {
                // Find line number for this offset
                var charCount = 0
                lines.indexOfFirst { line ->
                    charCount += line.length + 1
                    charCount > note.start
                }
            } else -1
        }
    }
    
    // Dark Blue background for highlighted line (adapted for dark theme)
    val highlightBackgroundColor = Color(0xFF1A3A60)
    
    // Process annotations
    val annotatedString = buildAnnotatedString {
        append(data.code)
        
        data.annotations.forEach { note ->
            val start = note.start
            val end = note.end
            
            // Validate bounds
            if (start >= 0 && end <= data.code.length && start < end) {
                // Highlighting based on cursor
                // If annotation corresponds to current cursor address, highlight background
                if (note.offset == cursorAddress && cursorAddress != 0L) {
                    addStyle(
                        style = SpanStyle(background = highlightBackgroundColor),
                        start = start,
                        end = end
                    )
                }
                
                val color = when(note.syntaxHighlight) {
                    "datatype" -> Color(0xFF569CD6) // Blue (VSCodeish)
                    "function_name" -> Color(0xFFFFD700) // Gold
                    "keyword" -> Color(0xFFC586C0) // Purple
                    "local_variable" -> Color(0xFF9CDCFE) // Light Blue
                    "global_variable" -> Color(0xFF4EC9B0) // Teal
                    "comment" -> Color(0xFF6A9955) // Green
                    "string" -> Color(0xFFCE9178) // Orange/Red
                    "offset" -> Color(0xFFB5CEA8) // Light Green
                    else -> null 
                }
                
                if (color != null) {
                    addStyle(
                        style = SpanStyle(color = color),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }
    
    // Auto-scroll logic
    val density = androidx.compose.ui.platform.LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    
    // Track previous cursor address to only scroll when it actually changes
    var previousCursorAddress by remember { androidx.compose.runtime.mutableStateOf(cursorAddress) }
    var hasInitiallyScrolled by remember { androidx.compose.runtime.mutableStateOf(false) }
    
    androidx.compose.runtime.LaunchedEffect(cursorAddress, textLayoutResult) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        if (cursorAddress == 0L) return@LaunchedEffect
        
        // Skip if this is just the initial composition with same address
        if (hasInitiallyScrolled && cursorAddress == previousCursorAddress) {
            return@LaunchedEffect
        }
        
        previousCursorAddress = cursorAddress
        hasInitiallyScrolled = true
        
        // Find annotation for cursor
        val note = data.annotations.firstOrNull { it.offset == cursorAddress }
        if (note != null && note.start < layout.layoutInput.text.length) {
            val line = layout.getLineForOffset(note.start)
            val top = layout.getLineTop(line)
            
            // Center the line (approx)
            val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
            val targetScroll = (top - screenHeightPx / 3).toInt().coerceAtLeast(0)
            
            scrollState.animateScrollTo(targetScroll)
        }
    }
    
    // Observe scroll to selection trigger from TopAppBar button
    val scrollToSelectionTrigger by viewModel.scrollToSelectionTrigger.collectAsState()
    LaunchedEffect(scrollToSelectionTrigger) {
        if (scrollToSelectionTrigger > 0) {
            val layout = textLayoutResult ?: return@LaunchedEffect
            if (cursorAddress == 0L) return@LaunchedEffect
            
            val note = data.annotations.firstOrNull { it.offset == cursorAddress }
            if (note != null && note.start < layout.layoutInput.text.length) {
                val line = layout.getLineForOffset(note.start)
                val top = layout.getLineTop(line)
                
                val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
                val targetScroll = (top - screenHeightPx / 3).toInt().coerceAtLeast(0)
                
                scrollState.animateScrollTo(targetScroll)
            }
        }
    }

    // Calculate line number width based on total lines
    val lineNumberWidth = remember(lines.size) {
        val digits = lines.size.toString().length
        (digits * 10 + 16).dp // Approximate width per digit + padding
    }

    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
        ) {
            // Line numbers column
            Box(
                modifier = Modifier
                    .width(lineNumberWidth)
                    .verticalScroll(scrollState)
                    .background(Color(0xFF252526))
                    .padding(top = 8.dp, bottom = 8.dp)
            ) {
                Column {
                    lines.forEachIndexed { index, _ ->
                        val isCurrentLine = index == cursorLineIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .background(if (isCurrentLine) highlightBackgroundColor else Color.Transparent),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = if (isCurrentLine) Color(0xFFFFFFFF) else Color(0xFF858585),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
            
            // Code content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                Text(
                    text = annotatedString,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFD4D4D4), // Standard light gray text
                    lineHeight = 18.sp,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val layout = textLayoutResult ?: return@detectTapGestures
                            if (pos.y <= layout.size.height) {
                                val offset = layout.getOffsetForPosition(pos)
                                val note = data.annotations.firstOrNull { it.start <= offset && it.end >= offset }
                                if (note != null && note.offset != 0L) {
                                    onAddressClick(note.offset)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DisasmContextMenu(
    expanded: Boolean,
    address: Long,
    instr: DisasmInstruction?,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onModify: (String) -> Unit,
    onXrefs: () -> Unit,
    onCustomCommand: () -> Unit
) {
    if (expanded) {
        // Need to hoist state of submenu? DropdownMenu handles content recomposition.
        // But we need a persistent state for the submenu.
        var showCopySubMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            if (!showCopySubMenu) {
                DropdownMenuItem(
                    text = { Text("Copy...") },
                    onClick = { showCopySubMenu = true },
                    trailingIcon = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                )
                
                HorizontalDivider()
                
                DropdownMenuItem(
                    text = { Text("Modify Hex") },
                    onClick = { onModify("hex") }
                )
                DropdownMenuItem(
                    text = { Text("Modify String") },
                    onClick = { onModify("string") }
                )
                DropdownMenuItem(
                    text = { Text("Modify Opcode") },
                    onClick = { onModify("asm") }
                )
                
                HorizontalDivider()
                
                DropdownMenuItem(
                    text = { Text("Xrefs") },
                    onClick = { onXrefs() }
                )
                
                DropdownMenuItem(
                    text = { Text("Custom Command...") },
                    onClick = { onCustomCommand() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Back") },
                    onClick = { showCopySubMenu = false },
                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, null) }
                )
                HorizontalDivider()
                
                DropdownMenuItem(
                    text = { Text("Address") },
                    onClick = { onCopy("0x%08x".format(address)) }
                )
                if (instr != null) {
                    DropdownMenuItem(
                        text = { Text("Opcode") },
                        onClick = { onCopy(instr.disasm) }
                    )
                    DropdownMenuItem(
                        text = { Text("Bytes") },
                        onClick = { onCopy(instr.bytes) }
                    )
                    DropdownMenuItem(
                        text = { Text("Full Row") },
                        onClick = { 
                            val bytesStr = if (instr.bytes.length > 12) instr.bytes.take(12) + ".." else instr.bytes
                            onCopy("0x%08x  %s  %s".format(address, bytesStr.padEnd(14), instr.disasm)) 
                        }
                    )
                }
            }
        }
    }
}

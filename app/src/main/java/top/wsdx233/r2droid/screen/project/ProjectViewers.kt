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
import androidx.compose.ui.res.colorResource
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
import top.wsdx233.r2droid.R

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
        // Sticky Header: 0 1 2 3 4 5 6 7
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(hexHeaderBackground)
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(hexContentBackground),
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
                                onByteClick = onByteClick,
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
                                    onByteClick = onByteClick,
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
                .background(hexFooterBackground)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val currentPos = hexDataManager.getRowAddress(listState.firstVisibleItemIndex)
            Text("Pos: ${"0x%X".format(currentPos)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = hexByteText)
            if (totalSize > 0L) {
                Text("Range: ${"0x%X".format(hexDataManager.viewStartAddress)}-${"0x%X".format(hexDataManager.viewEndAddress)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = hexByteText)
            }
        }
    }
}

/**
 * Placeholder row shown when data is not yet loaded.
 */
@Composable
fun HexPlaceholderRow(
    addr: Long,
    hexAddressBackground: Color = Color(0xFFDDDDDD),
    hexAddressText: Color = Color.Black,
    hexDivider: Color = Color(0xFFBDBDBD)
) {
    val hexPlaceholderRow = colorResource(R.color.hex_placeholder_row)
    val hexPlaceholderBlock = colorResource(R.color.hex_placeholder_block)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(hexPlaceholderRow)
            .height(IntrinsicSize.Min)
    ) {
        // Address
        Box(
            modifier = Modifier
                .width(70.dp)
                .fillMaxHeight()
                .background(hexAddressBackground)
                .padding(start = 4.dp, top = 2.dp)
        ) {
            Text(
                text = "%06X".format(addr),
                color = hexAddressText,
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
                        .background(hexPlaceholderBlock, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
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
                .background(hexPlaceholderBlock, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
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
    onByteClick: (Long) -> Unit,
    hexAddressBackground: Color = Color(0xFFDDDDDD),
    hexAddressText: Color = Color(0xFF424242),
    hexRowEven: Color = Color.White,
    hexRowOdd: Color = Color(0xFFE8EAF6),
    hexDivider: Color = Color(0xFFBDBDBD),
    hexByteText: Color = Color.Black
) {
    // 8 bytes row
    val oddRow = (addr / 8) % 2 == 1L
    
    // Check if this row contains the cursor
    val rowStartAddr = addr
    val rowEndAddr = addr + bytes.size - 1
    val isRowSelected = cursorAddress >= rowStartAddr && cursorAddress <= rowEndAddr
    
    // Base background: alternating colors (zebra stripes)
    val baseBgColor = if (oddRow) hexRowOdd else hexRowEven

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
                .background(hexAddressBackground), // Gray background for address
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "%06X".format(addr), 
                color = hexAddressText, // Dark gray
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
                            .background(hexDivider)
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
                         color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else hexByteText,
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
                             .background(hexDivider)
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
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else hexByteText
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
        // Build jump tracking maps for internal jumps
        // These maps connect jump instructions to their targets with indices
        val jumpMaps = remember(cacheVersion, loadedCount) {
            val jumpToIndex = mutableMapOf<Long, Int>()     // jump instruction addr -> index
            val targetToIndex = mutableMapOf<Long, Int>()   // jump target addr -> index
            var jumpCounter = 1
            
            // Scan all loaded instructions to build jump maps
            for (i in 0 until loadedCount) {
                val instr = disasmDataManager.getInstructionAt(i) ?: continue
                
                // Check if this is an internal jump instruction
                if (instr.type in listOf("jmp", "cjmp", "ujmp") && instr.jump != null) {
                    // Check if it's internal (within the same function)
                    val isInternal = instr.fcnAddr > 0 && 
                                     instr.jump >= instr.fcnAddr && 
                                     instr.jump <= instr.fcnLast
                    
                    if (isInternal) {
                        // Assign index to this jump
                        jumpToIndex[instr.addr] = jumpCounter
                        // Also mark the target with the same index
                        targetToIndex[instr.jump] = jumpCounter
                        jumpCounter++
                    }
                }
            }
            
            Pair(jumpToIndex, targetToIndex)
        }
        
        val (jumpToIndex, targetToIndex) = jumpMaps
        
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
                    
                    // Look up jump indices for this instruction
                    val jumpIdx = jumpToIndex[instr.addr]
                    val targetIdx = targetToIndex[instr.addr]
                    
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
    val disasmPlaceholderBg = colorResource(R.color.disasm_placeholder_background)
    
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
                .background(disasmPlaceholderBg, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
        // Bytes placeholder
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(18.dp)
                .padding(end = 4.dp)
                .background(disasmPlaceholderBg, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
        // Disasm placeholder
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .background(disasmPlaceholderBg, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
    }
}

/**
 * Helper function to format address in a compact way
 * Removes 0x prefix and leading zeros for shorter display
 */
private fun formatCompactAddress(addr: Long): String {
    val hex = "%X".format(addr)
    // Keep at least 4 characters for readability
    return if (hex.length <= 4) hex else hex.trimStart('0').ifEmpty { "0" }
}

/**
 * Format jump index - show only last 2 digits
 */
private fun formatJumpIndex(index: Int): String {
    return (index % 100).toString().padStart(2, '0')
}

@Composable
fun DisasmRow(
    instr: DisasmInstruction, 
    isSelected: Boolean, 
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showMenu: Boolean = false,
    menuContent: @Composable () -> Unit = {},
    jumpIndex: Int? = null,           // Index for this jump (if it's a jump instruction)
    jumpTargetIndex: Int? = null      // Index if this is a jump target
) {
    // Column background colors (themed)
    val colJumpBg = colorResource(R.color.disasm_col_jump)
    val colAddressBg = colorResource(R.color.disasm_col_address)
    val colBytesBg = colorResource(R.color.disasm_col_bytes)
    val colOpcodeBg = colorResource(R.color.disasm_col_opcode)
    val colCommentBg = colorResource(R.color.disasm_col_comment)
    val secondaryRowBg = colorResource(R.color.disasm_secondary_row)
    
    // Color definitions
    val commentColor = Color(0xFF6A9955)  // Green for comments
    val flagColor = Color(0xFF4EC9B0)     // Teal for flags
    val funcNameColor = Color(0xFFDCDCAA) // Yellow for function names
    val funcIconColor = Color(0xFF569CD6) // Blue for function icon
    val jumpOutColor = Color(0xFF66BB6A)  // Green arrow for jump out (external)
    val jumpInColor = Color(0xFFFFCA28)   // Yellow arrow for jump in (external)
    val jumpInternalColor = Color(0xFF64B5F6) // Blue for internal jumps
    val addressColor = Color(0xFF888888)  // Gray for address
    val bytesColor = Color(0xFF999999)    // Lighter gray for bytes
    
    // Cutter style coloring logic
    val opcodeColor = when (instr.type) {
        "call", "ucall", "ircall" -> Color(0xFF42A5F5) // Blue
        "jmp", "cjmp", "ujmp" -> Color(0xFF66BB6A) // Green
        "ret" -> Color(0xFFEF5350) // Red
        "push", "pop", "rpush" -> Color(0xFFAB47BC) // Purple
        "cmp", "test", "acmp" -> Color(0xFFFFCA28) // Orange/Yellow
        "nop" -> Color.Gray
        "lea" -> Color(0xFF4FC3F7) // Light Blue
        "mov" -> Color(0xFFA25410) // White/Light Gray
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    // Check if this is the start of a function
    val isFunctionStart = instr.fcnAddr > 0 && instr.addr == instr.fcnAddr
    
    // Check for external jump out/in
    val isExternalJumpOut = instr.isJumpOut()
    val hasExternalJumpIn = instr.hasJumpIn()
    
    // Check if this is a jump instruction (internal or external)
    val isJumpInstruction = instr.type in listOf("jmp", "cjmp", "ujmp")
    val isInternalJump = isJumpInstruction && instr.jump != null && !isExternalJumpOut
    
    // Determine jump direction for internal jumps
    val jumpDirection = if (isInternalJump && instr.jump != null) {
        if (instr.jump > instr.addr) "↓" else "↑"
    } else null
    
    // Prepare bytes - always truncate with ... if too long (max 10 chars displayed)
    val bytesStr = instr.bytes.lowercase()
    val displayBytes = if (bytesStr.length > 10) bytesStr.take(8) + "…" else bytesStr
    
    // Prepare inline comment
    val inlineComment = buildString {
        if (instr.ptr != null) {
            append("; ${formatCompactAddress(instr.ptr)}")
        }
        if (instr.refptr && instr.refs.isNotEmpty()) {
            val dataRef = instr.refs.firstOrNull { it.type == "DATA" }
            if (dataRef != null) {
                if (isNotEmpty()) append(" ")
                append("[${formatCompactAddress(dataRef.addr)}]")
            }
        }
    }.trim()
    
    // Only comments go to secondary row (not bytes)
    val hasInlineComment = inlineComment.isNotEmpty()
    
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .pointerInput(onClick, onLongClick) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                }
        ) {
            // === Pre-instruction annotations ===
            
            // 1. Display flags (like ;-- _start: or ;-- rip:)
            if (instr.flags.isNotEmpty()) {
                instr.flags.forEach { flag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 80.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ";-- $flag:",
                            color = flagColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            
            // 2. Display function header if this is function start
            if (isFunctionStart) {
                val funcSize = if (instr.fcnLast > instr.fcnAddr) instr.fcnLast - instr.fcnAddr else 0
                val funcName = instr.flags.firstOrNull { 
                    !it.startsWith("section.") && !it.startsWith("reloc.") 
                } ?: "fcn.${"%08x".format(instr.addr)}"
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 80.dp, top = 2.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Blue function icon
                    Text(
                        text = "▶",
                        color = funcIconColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = "$funcSize: ",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "$funcName ();",
                        color = funcNameColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // === Main instruction row (compact, single line) ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Jump indicator column (fixed width) - with background color
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .fillMaxHeight()
                        .background(if (isSelected) Color.Transparent else colJumpBg),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // External jump out - green left arrow
                        isExternalJumpOut -> {
                            Text(
                                text = "←",
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else jumpOutColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // External jump in target - yellow right arrow
                        hasExternalJumpIn -> {
                            Text(
                                text = "→",
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else jumpInColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Internal jump instruction - blue arrow with direction and last 2 digits
                        isInternalJump && jumpIndex != null -> {
                            Text(
                                text = "${jumpDirection ?: ""}${formatJumpIndex(jumpIndex)}",
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else jumpInternalColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Jump target - show target indicator with last 2 digits
                        jumpTargetIndex != null -> {
                            Text(
                                text = "▸${formatJumpIndex(jumpTargetIndex)}",
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else jumpInternalColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
                
                // Address column - compact format with background
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .background(if (isSelected) Color.Transparent else colAddressBg)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = formatCompactAddress(instr.addr),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else addressColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Bytes column - always visible, truncated with ...
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .fillMaxHeight()
                        .background(if (isSelected) Color.Transparent else colBytesBg)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = displayBytes,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else bytesColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
                
                // Opcode / Disasm column - with background, takes remaining space
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isSelected) Color.Transparent else colOpcodeBg)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = instr.disasm,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else opcodeColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = if(instr.type in listOf("call", "jmp", "cjmp", "ret")) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                // Inline comment column (if present and short enough for same line)
                if (hasInlineComment && inlineComment.length <= 20) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(if (isSelected) Color.Transparent else colCommentBg)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = inlineComment,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else commentColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            // === Secondary row for long inline comments (only comments, not bytes) ===
            if (hasInlineComment && inlineComment.length > 20) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else secondaryRowBg)
                        .padding(start = 80.dp, top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = inlineComment,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f) else commentColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
            
            // === Post-instruction comment (from radare2) ===
            if (!instr.comment.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else colCommentBg.copy(alpha = 0.3f))
                        .padding(start = 80.dp, top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "; ${instr.comment}",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else commentColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
            
            // Show xref comments for jump targets
            if (instr.xrefs.isNotEmpty()) {
                val codeXrefs = instr.xrefs.filter { it.type == "CODE" }
                if (codeXrefs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else colCommentBg.copy(alpha = 0.2f))
                            .padding(start = 80.dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val xrefText = if (codeXrefs.size == 1) {
                            "; XREF from ${formatCompactAddress(codeXrefs[0].addr)}"
                        } else {
                            "; XREF from ${codeXrefs.size} locations"
                        }
                        Text(
                            text = xrefText,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else commentColor.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }
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

package top.wsdx233.r2droid.feature.disasm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.core.ui.dialogs.CustomCommandDialog
import top.wsdx233.r2droid.core.ui.dialogs.ModifyDialog
import top.wsdx233.r2droid.core.ui.dialogs.XrefsDialog

import top.wsdx233.r2droid.core.ui.components.AutoHideAddressScrollbar
import top.wsdx233.r2droid.ui.theme.LocalAppFont

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
    viewModel: top.wsdx233.r2droid.feature.disasm.DisasmViewModel,
    cursorAddress: Long,
    scrollToSelectionTrigger: kotlinx.coroutines.flow.StateFlow<Int>,
    onInstructionClick: (Long) -> Unit
) {
    val disasmDataManager = viewModel.disasmDataManager
    val cacheVersion by viewModel.disasmCacheVersion.collectAsState()
    
    // Menu & Dialog States
    var showMenu by remember { mutableStateOf(false) }
    var menuTargetAddress by remember { mutableStateOf<Long?>(null) }
    
    var showModifyDialog by remember { mutableStateOf(false) }
    var modifyType by remember { mutableStateOf("hex") } // hex, string, asm
    var showCustomCommandDialog by remember { mutableStateOf(false) }
    
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
    
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, maxOf(0, loadedCount - 1))
    )
    
    // Coroutine scope for scrollbar interactions
    val coroutineScope = rememberCoroutineScope()
    
    // Track previous cursor address to only scroll when it actually changes
    var previousCursorAddress by remember { mutableStateOf(cursorAddress) }
    var hasInitiallyScrolled by remember { mutableStateOf(false) }
    
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
    val scrollToSelectionTrigger by scrollToSelectionTrigger.collectAsState()
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
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                val targetIndex = disasmDataManager.estimateIndexForAddress(targetAddr)
                val clampedIndex = targetIndex.coerceIn(0, maxOf(0, disasmDataManager.loadedInstructionCount - 1))
                coroutineScope.launch {
                    listState.scrollToItem(clampedIndex)
                    viewModel.loadDisasmChunkForAddress(targetAddr)
                }
            }
        )
        
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
                fontFamily = LocalAppFont.current
            )
            Text(
                "Loaded: $loadedCount instrs", 
                fontSize = 12.sp, 
                fontFamily = LocalAppFont.current
            )
        }
        
        // Context Menu
        // Xrefs Dialog

        
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

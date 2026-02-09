package top.wsdx233.r2droid.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R

/**
 * Auto-hiding scrollbar that appears only during scrolling.
 * 
 * Features:
 * - Fades in when scrolling starts
 * - Fades out after 3 seconds of inactivity
 * - Supports drag and tap for quick navigation
 * - Smooth animations for show/hide
 * 
 * @param listState The LazyListState to observe for scroll events
 * @param totalItems Total number of items in the list
 * @param modifier Modifier for positioning (should include Alignment.CenterEnd)
 * @param thumbColor Color of the scroll thumb
 * @param trackWidth Width of the scrollbar track (touch area)
 * @param thumbWidth Width of the visible thumb
 * @param thumbHeight Height of the visible thumb
 * @param hideDelayMs Delay before hiding the scrollbar after scroll stops
 * @param animationDurationMs Duration of fade in/out animation
 * @param alwaysShow If true, always show the scrollbar (no auto-hide)
 * @param onScrollToIndex Callback when user drags/taps scrollbar, receives target index
 */
@Composable
fun AutoHideScrollbar(
    listState: LazyListState,
    totalItems: Int,
    modifier: Modifier = Modifier,
    thumbColor: Color = colorResource(R.color.thumb_color),
    trackWidth: Dp = 16.dp,
    thumbWidth: Dp =  8.dp,
    thumbHeight: Dp = 40.dp,
    hideDelayMs: Long = 3000L,
    animationDurationMs: Int = 300,
    alwaysShow: Boolean = false,
    onScrollToIndex: (Int) -> Unit = {}
) {
    if (totalItems <= 0) return
    
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(alwaysShow) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressing by remember { mutableStateOf(false) }
    
    // Animate alpha for smooth fade in/out
    val alpha by animateFloatAsState(
        targetValue = if (alwaysShow || isVisible || isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = animationDurationMs),
        label = "scrollbar_alpha"
    )
    
    // Use primary color when pressing or dragging, otherwise use default thumb color
    val currentThumbColor = if (isPressing || isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        thumbColor
    }
    
    // Monitor scroll state changes (only if not alwaysShow)
    LaunchedEffect(listState, alwaysShow) {
        if (!alwaysShow) {
            snapshotFlow { 
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset 
            }.collectLatest { (_, _) ->
                // Show scrollbar on any scroll change
                isVisible = true
                // Hide after delay (only if not currently dragging)
                delay(hideDelayMs)
                if (!isDragging) {
                    isVisible = false
                }
            }
        }
    }
    
    // Calculate thumb position
    val currentIndex = listState.firstVisibleItemIndex
    val layoutInfo = listState.layoutInfo
    val visibleItemsCount = layoutInfo.visibleItemsInfo.size
    
    // Calculate scroll percentage considering visible items
    // When at bottom: currentIndex = totalItems - visibleItemsCount
    val scrollableItems = maxOf(1, totalItems - visibleItemsCount)
    val thumbY = if (totalItems > 0 && scrollableItems > 0) {
        (currentIndex.toFloat() / scrollableItems.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bias = (thumbY * 2 - 1).coerceIn(-1f, 1f)
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(trackWidth)
            .alpha(alpha)
            .pointerInput(totalItems) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        isPressing = true
                        isVisible = true
                    },
                    onDragEnd = {
                        isDragging = false
                        isPressing = false
                        // Start hide timer (only if not alwaysShow)
                        if (!alwaysShow) {
                            coroutineScope.launch {
                                delay(hideDelayMs)
                                if (!isDragging) {
                                    isVisible = false
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        isPressing = false
                        if (!alwaysShow) {
                            coroutineScope.launch {
                                delay(hideDelayMs)
                                if (!isDragging) {
                                    isVisible = false
                                }
                            }
                        }
                    },
                    onVerticalDrag = { change, _ ->
                        val height = size.height
                        val newY = (change.position.y / height).coerceIn(0f, 1f)
                        val targetIndex = (newY * totalItems).toInt().coerceIn(0, maxOf(0, totalItems - 1))
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                        onScrollToIndex(targetIndex)
                    }
                )
            }
            .pointerInput(totalItems) {
                detectTapGestures(
                    onTap = { offset ->
                        isPressing = true
                        isVisible = true
                        val height = size.height
                        val newY = (offset.y / height).coerceIn(0f, 1f)
                        val targetIndex = (newY * totalItems).toInt().coerceIn(0, maxOf(0, totalItems - 1))
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                        onScrollToIndex(targetIndex)
                        // Reset pressing state after a short delay
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(100)
                            isPressing = false
                        }
                        // Start hide timer (only if not alwaysShow)
                        if (!alwaysShow) {
                            coroutineScope.launch {
                                delay(hideDelayMs)
                                if (!isDragging) {
                                    isVisible = false
                                }
                            }
                        }
                    }
                )
            }
    ) {
        // Scrollbar thumb - positioned at the right edge
        Box(
            Modifier
                .align(BiasAlignment(1f, bias))
                .size(thumbWidth, thumbHeight)
                .background(currentThumbColor)
        )
    }
}

/**
 * Specialized auto-hiding scrollbar for address-based navigation.
 * Used by Hex and Disasm viewers where scrolling is based on virtual addresses.
 * 
 * @param listState The LazyListState to observe for scroll events
 * @param totalItems Total number of items in the list (loaded items)
 * @param viewStartAddress Start address of the viewable range
 * @param viewEndAddress End address of the viewable range  
 * @param currentAddress Current address at the visible position
 * @param modifier Modifier for positioning
 * @param thumbColor Color of the scroll thumb
 * @param alwaysShow If true, always show the scrollbar (no auto-hide)
 * @param onScrollToAddress Callback when user drags/taps, receives target address
 */
@Composable
fun AutoHideAddressScrollbar(
    listState: LazyListState,
    totalItems: Int,
    viewStartAddress: Long,
    viewEndAddress: Long,
    currentAddress: Long,
    modifier: Modifier = Modifier,
    thumbColor: Color = colorResource(R.color.thumb_color),
    trackWidth: Dp = 16.dp,
    thumbWidth: Dp = 8.dp,
    thumbHeight: Dp = 40.dp,
    hideDelayMs: Long = 3000L,
    animationDurationMs: Int = 300,
    alwaysShow: Boolean = false,
    onScrollToAddress: (Long) -> Unit = {}
) {
    if (totalItems <= 0) return
    
    val totalAddressRange = viewEndAddress - viewStartAddress
    if (totalAddressRange <= 0) return
    
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(alwaysShow) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressing by remember { mutableStateOf(false) }
    
    // Animate alpha for smooth fade in/out
    val alpha by animateFloatAsState(
        targetValue = if (alwaysShow || isVisible || isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = animationDurationMs),
        label = "scrollbar_alpha"
    )
    
    // Use primary color when pressing or dragging, otherwise use default thumb color
    val currentThumbColor = if (isPressing || isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        thumbColor
    }
    
    // Monitor scroll state changes (only if not alwaysShow)
    LaunchedEffect(listState, alwaysShow) {
        if (!alwaysShow) {
            snapshotFlow { 
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset 
            }.collectLatest { (_, _) ->
                isVisible = true
                delay(hideDelayMs)
                if (!isDragging) {
                    isVisible = false
                }
            }
        }
    }
    
    // Calculate thumb position based on current address in virtual range
    val thumbY = if (currentAddress >= viewStartAddress) {
        ((currentAddress - viewStartAddress).toFloat() / totalAddressRange.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bias = (thumbY * 2 - 1).coerceIn(-1f, 1f)
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(trackWidth)
            .alpha(alpha)
            .pointerInput(totalAddressRange) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        isPressing = true
                        isVisible = true
                    },
                    onDragEnd = {
                        isDragging = false
                        isPressing = false
                        // Start hide timer (only if not alwaysShow)
                        if (!alwaysShow) {
                            coroutineScope.launch {
                                delay(hideDelayMs)
                                if (!isDragging) {
                                    isVisible = false
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        isPressing = false
                        if (!alwaysShow) {
                            coroutineScope.launch {
                                delay(hideDelayMs)
                                if (!isDragging) {
                                    isVisible = false
                                }
                            }
                        }
                    },
                    onVerticalDrag = { change, _ ->
                        val height = size.height
                        val newY = (change.position.y / height).coerceIn(0f, 1f)
                        val targetAddr = viewStartAddress + (newY * totalAddressRange).toLong()
                        onScrollToAddress(targetAddr)
                    }
                )
            }
            .pointerInput(totalAddressRange) {
                detectTapGestures(
                    onTap = { offset ->
                        isPressing = true
                        isVisible = true
                        val height = size.height
                        val newY = (offset.y / height).coerceIn(0f, 1f)
                        val targetAddr = viewStartAddress + (newY * totalAddressRange).toLong()
                        onScrollToAddress(targetAddr)
                        // Reset pressing state after a short delay
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(100)
                            isPressing = false
                        }
                        // Start hide timer (only if not alwaysShow)
                        if (!alwaysShow) {
                            coroutineScope.launch {
                                delay(hideDelayMs)
                                if (!isDragging) {
                                    isVisible = false
                                }
                            }
                        }
                    }
                )
            }
    ) {
        // Scrollbar thumb - positioned at the right edge
        Box(
            Modifier
                .align(BiasAlignment(1f, bias))
                .size(thumbWidth, thumbHeight)
                .background(currentThumbColor)
        )
    }
}

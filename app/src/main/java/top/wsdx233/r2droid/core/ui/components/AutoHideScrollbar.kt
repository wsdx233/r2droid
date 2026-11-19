package top.wsdx233.r2droid.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.AddressOverview
import top.wsdx233.r2droid.core.data.model.RegionKind
import kotlin.math.roundToInt

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
    val alpha = animateFloatAsState(
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
    
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(trackWidth)
            .graphicsLayer { this.alpha = alpha.value }
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
        // Scroll/layout state changes every frame. Only invalidate the thumb's drawing.
        val scrollableItems = (totalItems - listState.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
        val ratio = (listState.firstVisibleItemIndex.toFloat() / scrollableItems).coerceIn(0f, 1f)
        val width = thumbWidth.toPx().coerceAtMost(size.width)
        val height = thumbHeight.toPx().coerceAtMost(size.height)
        val x = if (layoutDirection == LayoutDirection.Ltr) size.width - width else 0f
        drawRect(currentThumbColor, Offset(x, ratio * (size.height - height)), Size(width, height))
    }
}

/**
 * Address scrollbar with frame-local thumb feedback. [onScrollToAddress] is a cheap preview;
 * [onDragComplete] is called exactly once on release (including a tap), never on cancellation.
 * Keep [isSeeking] true while loading the committed address to avoid snapping the thumb back.
 */
@Composable
fun AutoHideAddressScrollbar(
    listState: LazyListState,
    totalItems: Int,
    viewStartAddress: Long,
    viewEndAddress: Long,
    currentAddress: () -> Long,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.onSurface,
    trackWidth: Dp = 16.dp,
    thumbWidth: Dp = 8.dp,
    thumbHeight: Dp = 40.dp,
    hideDelayMs: Long = 3000L,
    animationDurationMs: Int = 300,
    alwaysShow: Boolean = false,
    onScrollToAddress: (Long) -> Unit = {},
    onDragComplete: (Long) -> Unit = {},
    onDragStateChange: (Boolean) -> Unit = {},
    isSeeking: Boolean = false,
    overview: AddressOverview = AddressOverview.Empty,
    onDragThumbCenter: ((Float) -> Unit)? = null
) {
    if (totalItems <= 0 || viewEndAddress <= viewStartAddress) return

    val preview by rememberUpdatedState(onScrollToAddress)
    val reportCenter by rememberUpdatedState(onDragThumbCenter)
    val commit by rememberUpdatedState(onDragComplete)
    val dragStateChange by rememberUpdatedState(onDragStateChange)
    val address by rememberUpdatedState(currentAddress)
    val seeking by rememberUpdatedState(isSeeking)
    var isVisible by remember { mutableStateOf(alwaysShow) }
    var isDragging by remember { mutableStateOf(false) }
    // Read only during drawing/gestures: pointer moves do not recompose the scrollbar.
    var dragThumbRatio by remember(viewStartAddress, viewEndAddress) { mutableStateOf<Float?>(null) }
    val alpha = animateFloatAsState(
        targetValue = if (alwaysShow || isVisible || isDragging || isSeeking) 1f else 0f,
        animationSpec = tween(animationDurationMs),
        label = "address_scrollbar_alpha"
    )

    LaunchedEffect(listState, alwaysShow, hideDelayMs) {
        if (!alwaysShow) {
            snapshotFlow {
                Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, isDragging || seeking)
            }.collectLatest {
                isVisible = true
                delay(hideDelayMs)
                isVisible = false
            }
        }
    }
    LaunchedEffect(isDragging, isSeeking) {
        if (!isDragging && !isSeeking) dragThumbRatio = null
    }

    fun addressRatio(): Float {
        val range = (viewEndAddress - 1 - viewStartAddress).coerceAtLeast(1)
        return ((address().coerceIn(viewStartAddress, viewEndAddress - 1) - viewStartAddress).toDouble() / range)
            .toFloat().coerceIn(0f, 1f)
    }

    // Keep the cache modifier stable across preview/list recompositions. Only overview, range,
    // size or density changes should rebuild the strip geometry, never a moving thumb.
    val strip = remember(overview, viewStartAddress, viewEndAddress, thumbWidth, thumbHeight) {
        Modifier.drawWithCache {
            val thumbPx = thumbHeight.toPx().coerceAtMost(size.height)
            val travel = size.height - thumbPx
            val thumbWidthPx = thumbWidth.toPx().coerceAtMost(size.width)
            val stripWidth = minOf(5.dp.toPx(), thumbWidthPx)
            if (overview == AddressOverview.Empty || travel <= 0f || stripWidth <= 0f) {
                return@drawWithCache onDrawBehind { }
            }

            val bandsByKind = Array(RegionKind.entries.size) { Path() }
            val dividers = Path()
            val rows = travel.roundToInt().coerceAtLeast(1)
            val left = size.width - (thumbWidthPx + stripWidth) / 2f
            val right = left + stripWidth
            // The first/last raster boundaries are the centers of the thumb at either end.
            fun rowY(row: Int) = thumbPx / 2f + (row.toFloat() / rows) * travel
            for (band in overview.rasterize(viewStartAddress, viewEndAddress, rows)) {
                val top = rowY(band.firstRow)
                val bottom = rowY(band.endRow)
                bandsByKind[band.kind.ordinal].addRect(Rect(left, top, right, bottom))
                if (band.separatorBefore) {
                    dividers.addRect(Rect(left, top, right, minOf(top + 1f, bottom)))
                }
            }
            onDrawBehind {
                for (kind in RegionKind.entries) {
                    val path = bandsByKind[kind.ordinal]
                    if (!path.isEmpty) drawPath(path, BinaryPermissionColors[kind])
                }
                if (!dividers.isEmpty) drawPath(dividers, Color.Black.copy(alpha = 0.52f))
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(trackWidth)
            .graphicsLayer { this.alpha = alpha.value }
            .then(strip)
            .pointerInput(viewStartAddress, viewEndAddress, thumbHeight) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val height = size.height.toFloat()
                    val thumbPx = thumbHeight.toPx().coerceAtMost(height)
                    val thumbTop = (dragThumbRatio ?: addressRatio()) * (height - thumbPx)
                    val grabOffset = if (down.position.y in thumbTop..(thumbTop + thumbPx)) {
                        down.position.y - thumbTop
                    } else thumbPx / 2f
                    var lastAddress: Long
                    fun moveTo(y: Float): Long {
                        val ratio = scrollbarRatioAtPosition(y, height, thumbPx, grabOffset)
                        dragThumbRatio = ratio
                        reportCenter?.invoke(thumbPx / 2f + ratio * (height - thumbPx))
                        val target = scrollbarAddressAtRatio(viewStartAddress, viewEndAddress, ratio)
                        preview(target)
                        return target
                    }
                    isDragging = true
                    isVisible = true
                    dragStateChange(true)
                    try {
                        lastAddress = moveTo(down.position.y)
                        val released = verticalDrag(down.id) { change ->
                            change.consume()
                            lastAddress = moveTo(change.position.y)
                        }
                        if (released) commit(lastAddress) else dragThumbRatio = null
                    } finally {
                        isDragging = false
                        dragStateChange(false)
                    }
                }
            }
    ) {
        val height = thumbHeight.toPx().coerceAtMost(size.height)
        val width = thumbWidth.toPx().coerceAtMost(size.width)
        val ratio = dragThumbRatio ?: addressRatio()
        // The contrasting wings stay visible at either side of the permission strip.
        drawRect(
            color = thumbColor,
            topLeft = Offset(size.width - width, ratio * (size.height - height)),
            size = Size(width, height),
            alpha = if (isDragging || seeking) 0.52f else 0.44f
        )
    }
}

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.AddressOverview
import top.wsdx233.r2droid.core.data.model.AddressStructureOverlay
import top.wsdx233.r2droid.core.data.model.BinaryByteProfile
import top.wsdx233.r2droid.core.data.model.OverviewMarkerKind
import top.wsdx233.r2droid.core.data.model.OverviewRasterizer
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
    val currentListState by rememberUpdatedState(listState)
    val currentTotalItems by rememberUpdatedState(totalItems)
    val currentAlwaysShow by rememberUpdatedState(alwaysShow)
    val currentHideDelayMs by rememberUpdatedState(hideDelayMs)
    val currentOnScrollToIndex by rememberUpdatedState(onScrollToIndex)
    var isVisible by remember { mutableStateOf(alwaysShow) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressing by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    fun cancelHideTimer() {
        hideJob?.cancel()
        hideJob = null
    }

    fun scheduleHideTimer() {
        cancelHideTimer()
        if (!currentAlwaysShow) {
            hideJob = coroutineScope.launch {
                delay(currentHideDelayMs)
                if (!isDragging) {
                    isVisible = false
                }
            }
        }
    }

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
    LaunchedEffect(listState, alwaysShow, hideDelayMs) {
        if (!alwaysShow) {
            snapshotFlow { 
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset 
            }.collectLatest { (_, _) ->
                cancelHideTimer()
                isVisible = true
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
            .pointerInput(thumbHeight) {
                detectVerticalDragGestures(
                    onDragStart = {
                        cancelHideTimer()
                        isDragging = true
                        isPressing = true
                        isVisible = true
                    },
                    onDragEnd = {
                        isDragging = false
                        isPressing = false
                        scheduleHideTimer()
                    },
                    onDragCancel = {
                        isDragging = false
                        isPressing = false
                        scheduleHideTimer()
                    },
                    onVerticalDrag = { change, _ ->
                        val height = size.height.toFloat()
                        val thumbPx = thumbHeight.toPx().coerceAtMost(height)
                        val ratio = scrollbarRatioAtPosition(change.position.y, height, thumbPx, thumbPx / 2f)
                        val items = currentTotalItems
                        val scrollable = (items - currentListState.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
                        val targetIndex = (ratio * scrollable).roundToInt().coerceIn(0, maxOf(0, items - 1))
                        coroutineScope.launch {
                            currentListState.scrollToItem(targetIndex)
                        }
                        currentOnScrollToIndex(targetIndex)
                    }
                )
            }
            .pointerInput(thumbHeight) {
                detectTapGestures(
                    onTap = { offset ->
                        cancelHideTimer()
                        isPressing = true
                        isVisible = true
                        val height = size.height.toFloat()
                        val thumbPx = thumbHeight.toPx().coerceAtMost(height)
                        val ratio = scrollbarRatioAtPosition(offset.y, height, thumbPx, thumbPx / 2f)
                        val items = currentTotalItems
                        val scrollable = (items - currentListState.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
                        val targetIndex = (ratio * scrollable).roundToInt().coerceIn(0, maxOf(0, items - 1))
                        coroutineScope.launch {
                            currentListState.scrollToItem(targetIndex)
                        }
                        currentOnScrollToIndex(targetIndex)
                        // Reset pressing state after a short delay
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(100)
                            isPressing = false
                        }
                        scheduleHideTimer()
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
    byteProfile: BinaryByteProfile = BinaryByteProfile.Empty,
    structureOverlay: AddressStructureOverlay = AddressStructureOverlay.Empty,
    onDragThumbCenter: ((Float) -> Unit)? = null
) {
    if (totalItems <= 0 || viewEndAddress.toULong() <= viewStartAddress.toULong()) return

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
        val uStart = viewStartAddress.toULong()
        val uEnd = viewEndAddress.toULong()
        val range = (uEnd - 1UL - uStart).coerceAtLeast(1UL).toDouble()
        val uAddr = address().toULong().coerceIn(uStart, uEnd - 1UL)
        return ((uAddr - uStart).toDouble() / range).toFloat().coerceIn(0f, 1f)
    }

    // Keep the cache modifier stable across preview/list recompositions. Only overview, byte/semantic
    // structure, range, size or density changes should rebuild the strip geometry, never a moving thumb.
    val strip = remember(overview, byteProfile, structureOverlay, viewStartAddress, viewEndAddress, thumbWidth, thumbHeight) {
        Modifier.drawWithCache {
            val thumbPx = thumbHeight.toPx().coerceAtMost(size.height)
            val travel = size.height - thumbPx
            val thumbWidthPx = thumbWidth.toPx().coerceAtMost(size.width)
            val stripWidth = minOf(6.dp.toPx(), thumbWidthPx)
            val rows = travel.roundToInt().coerceAtLeast(1)
            val raster = if (travel <= 0f || stripWidth <= 0f) {
                top.wsdx233.r2droid.core.data.model.DetailedOverviewRaster.Empty
            } else {
                OverviewRasterizer.rasterize(
                    overview = overview,
                    start = viewStartAddress,
                    endExclusive = viewEndAddress,
                    rows = rows,
                    byteProfile = byteProfile,
                    overlay = structureOverlay
                )
            }
            if (raster.isEmpty) {
                return@drawWithCache onDrawBehind { }
            }

            val left = if (layoutDirection == LayoutDirection.Ltr) {
                size.width - (thumbWidthPx + stripWidth) / 2f
            } else {
                (thumbWidthPx - stripWidth) / 2f
            }
            val right = left + stripWidth
            // The first/last raster boundaries are the centers of the thumb at either end.
            fun rowY(row: Int) = thumbPx / 2f + (row.toFloat() / rows) * travel

            class StripRun(val barLeft: Float, val top: Float, val bottom: Float, val color: Color)
            val runs = ArrayList<StripRun>(minOf(rows, 512))
            val mappedRail = Path()
            val dividers = Path()

            var railStart = -1
            var runStart = -1
            var runColor = 0
            var runWidthPx = -1

            fun flushRun(endRow: Int) {
                if (runStart >= 0 && runColor != 0) {
                    val barLeft = (right - runWidthPx).coerceAtLeast(left)
                    runs.add(StripRun(barLeft, rowY(runStart), rowY(endRow), Color(runColor)))
                }
                runStart = -1
                runColor = 0
                runWidthPx = -1
            }

            for (r in 0 until rows) {
                val c = raster.colors[r]
                if (c != 0) {
                    if (railStart < 0) railStart = r
                    val wPx = (stripWidth * raster.widthFactors[r]).roundToInt().coerceIn(1, stripWidth.roundToInt().coerceAtLeast(1))
                    if (c != runColor || wPx != runWidthPx) {
                        flushRun(r)
                        runStart = r
                        runColor = c
                        runWidthPx = wPx
                    }
                } else {
                    flushRun(r)
                    if (railStart >= 0) {
                        mappedRail.addRect(Rect(left, rowY(railStart), right, rowY(r)))
                        railStart = -1
                    }
                }
                if (raster.separators[r]) {
                    val y = rowY(r)
                    dividers.addRect(Rect(left, y, right, minOf(y + 1f, rowY(r + 1))))
                }
            }
            flushRun(rows)
            if (railStart >= 0) {
                mappedRail.addRect(Rect(left, rowY(railStart), right, rowY(rows)))
            }

            val tickLeft = (left - 2.5.dp.toPx()).coerceAtLeast(0f)
            val tickWidths = right - tickLeft
            val markerHeightSmall = 1.5.dp.toPx()
            val markerHeightMed = 2.dp.toPx()
            val markerHeightLarge = 2.5.dp.toPx()

            onDrawBehind {
                if (!mappedRail.isEmpty) {
                    drawPath(mappedRail, Color.Black.copy(alpha = 0.16f))
                }
                for (run in runs) {
                    drawRect(
                        color = run.color,
                        topLeft = Offset(run.barLeft, run.top),
                        size = Size(right - run.barLeft, (run.bottom - run.top).coerceAtLeast(1f))
                    )
                }
                if (!dividers.isEmpty) {
                    drawPath(dividers, Color.Black.copy(alpha = 0.48f))
                }
                for (marker in raster.markers) {
                    val y = rowY(marker.row)
                    val (color, h) = when (marker.kind) {
                        OverviewMarkerKind.PC -> Color(0xFF22C55E) to markerHeightLarge
                        OverviewMarkerKind.BREAKPOINT -> Color(0xFFEF4444) to markerHeightMed
                        OverviewMarkerKind.ENTRY -> Color(0xFF38BDF8) to markerHeightMed
                        OverviewMarkerKind.ANNOTATION -> Color(0xFFC084FC) to markerHeightSmall
                        OverviewMarkerKind.CURSOR -> Color.White.copy(alpha = 0.9f) to markerHeightSmall
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(tickLeft, y - h / 2f),
                        size = Size(tickWidths, h)
                    )
                }
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
        val x = if (layoutDirection == LayoutDirection.Ltr) size.width - width else 0f
        // The contrasting wings stay visible at either side of the permission strip.
        drawRect(
            color = thumbColor,
            topLeft = Offset(x, ratio * (size.height - height)),
            size = Size(width, height),
            alpha = if (isDragging || seeking) 0.52f else 0.44f
        )
    }
}

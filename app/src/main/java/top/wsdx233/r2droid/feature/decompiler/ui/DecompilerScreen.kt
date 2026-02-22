package top.wsdx233.r2droid.feature.decompiler.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import top.wsdx233.r2droid.core.data.model.DecompilationData
import top.wsdx233.r2droid.core.data.model.DecompilationAnnotation
import top.wsdx233.r2droid.feature.project.ProjectViewModel
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@OptIn(ExperimentalTextApi::class)
@Composable
fun DecompilationViewer(
    viewModel: ProjectViewModel,
    data: DecompilationData,
    cursorAddress: Long,
    onAddressClick: (Long) -> Unit,
    onJumpAndDecompile: (Long) -> Unit = {}
) {
    val showLineNumbers by viewModel.decompilerShowLineNumbers.collectAsState()
    val wordWrap by viewModel.decompilerWordWrap.collectAsState()

    if (data.code.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Decompilation Data", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    // Pinch-to-zoom scale (persisted in app settings)
    val baseFontSize = 13f
    val baseLineHeight = 18f
    var scale by remember { mutableFloatStateOf(viewModel.decompilerZoomScale.value) }
    val resetZoomTrigger by viewModel.resetZoomTrigger.collectAsState()
    LaunchedEffect(resetZoomTrigger) {
        if (resetZoomTrigger > 0) scale = 1f
    }
    val scaledFontSize = (baseFontSize * scale).sp
    val scaledLineHeightSp = (baseLineHeight * scale).sp

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val highlightBackgroundColor = Color(0xFF1A3A60)
    val lines = remember(data.code) { data.code.split('\n') }
    val lineStartOffsets = remember(data.code, lines) { buildLineStartOffsets(lines) }
    val cursorAnnotation = remember(cursorAddress, data.annotations) {
        if (cursorAddress == 0L) null else data.annotations.firstOrNull { it.offset == cursorAddress }
    }
    val cursorLineIndex = remember(cursorAnnotation, lineStartOffsets) {
        cursorAnnotation?.let { findLineForOffset(lineStartOffsets, it.start) } ?: -1
    }

    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = LocalAppFont.current,
        fontSize = scaledFontSize,
        lineHeight = scaledLineHeightSp,
        color = Color(0xFFD4D4D4)
    )

    val charWidthPx = remember(textMeasurer, textStyle) {
        textMeasurer.measure(AnnotatedString("M"), style = textStyle).size.width.toFloat().coerceAtLeast(1f)
    }
    val lineHeightPx = with(density) { scaledLineHeightSp.toPx() }
    val horizontalPaddingPx = with(density) { 8.dp.toPx() }
    val verticalPaddingPx = with(density) { 8.dp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .pointerInput(wordWrap) {
                awaitEachGesture {
                    var prevSpan = 0f
                    var zooming = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val span = (p1 - p2).getDistance()
                            if (zooming && prevSpan > 1f) {
                                val centroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                                val oldScale = scale
                                val newScale = (oldScale * span / prevSpan).coerceIn(0.5f, 3f)
                                if (newScale != oldScale) {
                                    val ratio = newScale / oldScale
                                    val targetY = ((verticalScrollState.value + centroid.y) * ratio - centroid.y)
                                    verticalScrollState.dispatchRawDelta(targetY - verticalScrollState.value)
                                    if (!wordWrap) {
                                        val targetX = ((horizontalScrollState.value + centroid.x) * ratio - centroid.x)
                                        horizontalScrollState.dispatchRawDelta(targetX - horizontalScrollState.value)
                                    }
                                    scale = newScale
                                }
                            }
                            prevSpan = span
                            zooming = true
                            event.changes.forEach { it.consume() }
                        } else if (zooming) {
                            event.changes.forEach { it.consume() }
                            prevSpan = 0f
                        }
                    } while (event.changes.any { it.pressed })
                    if (zooming) viewModel.updateDecompilerZoomScale(scale)
                }
            }
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }

        val lineNumberWidthPx = remember(lines.size, showLineNumbers, scale) {
            if (!showLineNumbers) 0f else {
                val digits = lines.size.toString().length
                (digits * 10f * scale + 16f) * density.density
            }
        }
        val codeStartXPx = lineNumberWidthPx + horizontalPaddingPx
        val codeRenderWidthPx = (viewportWidthPx - codeStartXPx - horizontalPaddingPx).coerceAtLeast(1f)
        val wrapColumns = remember(wordWrap, codeRenderWidthPx, charWidthPx) {
            if (wordWrap) (codeRenderWidthPx / charWidthPx).toInt().coerceAtLeast(1) else Int.MAX_VALUE
        }

        val wrappedLineCounts = remember(lines, wrapColumns, wordWrap) {
            IntArray(lines.size) { idx ->
                if (!wordWrap) 1
                else maxOf(1, ceil(lines[idx].length.toDouble() / wrapColumns.toDouble()).toInt())
            }
        }
        val visualLineStarts = remember(wrappedLineCounts) { buildVisualLineStarts(wrappedLineCounts) }
        val totalVisualLines = visualLineStarts.last()
        val maxLineLength = remember(lines) { lines.maxOfOrNull { it.length } ?: 0 }
        val contentWidthPx = remember(wordWrap, viewportWidthPx, codeStartXPx, horizontalPaddingPx, maxLineLength, charWidthPx) {
            if (wordWrap) viewportWidthPx
            else codeStartXPx + horizontalPaddingPx + (maxLineLength * charWidthPx)
        }
        val contentHeightPx = remember(totalVisualLines, lineHeightPx, verticalPaddingPx) {
            totalVisualLines * lineHeightPx + verticalPaddingPx * 2
        }

        val annotationsByLine = remember(data.annotations, lineStartOffsets, lines.size) {
            buildAnnotationsByLine(data.annotations, lineStartOffsets, lines.size)
        }
        val clickableNotes = remember(data.annotations) {
            data.annotations.filter { it.offset != 0L }.sortedBy { it.start }
        }

        var previousCursorAddress by remember { mutableStateOf(cursorAddress) }
        var hasInitiallyScrolled by remember { mutableStateOf(false) }

        suspend fun scrollToCursorIfNeeded(force: Boolean) {
            val note = cursorAnnotation ?: return
            if (!force && hasInitiallyScrolled && cursorAddress == previousCursorAddress) return
            previousCursorAddress = cursorAddress
            hasInitiallyScrolled = true
            val line = findLineForOffset(lineStartOffsets, note.start).coerceAtLeast(0)
            val lineStart = lineStartOffsets[line]
            val offsetInLine = (note.start - lineStart).coerceAtLeast(0)
            val wrappedIndex = if (wordWrap) offsetInLine / wrapColumns else 0
            val visualIndex = (visualLineStarts[line] + wrappedIndex).coerceAtLeast(0)
            val targetY = (verticalPaddingPx + visualIndex * lineHeightPx - viewportHeightPx / 3f)
                .toInt()
                .coerceAtLeast(0)
            verticalScrollState.animateScrollTo(targetY)
        }

        LaunchedEffect(cursorAddress, visualLineStarts, wrapColumns, lineHeightPx) {
            if (cursorAddress != 0L) scrollToCursorIfNeeded(force = false)
        }

        val scrollToSelectionTrigger by viewModel.scrollToSelectionTrigger.collectAsState()
        LaunchedEffect(scrollToSelectionTrigger, visualLineStarts, wrapColumns, lineHeightPx) {
            if (scrollToSelectionTrigger > 0 && cursorAddress != 0L) {
                scrollToCursorIfNeeded(force = true)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
                .then(if (!wordWrap) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
                .pointerInput(
                    cursorAddress,
                    lines,
                    lineStartOffsets,
                    visualLineStarts,
                    wrapColumns,
                    lineHeightPx,
                    codeStartXPx
                ) {
                    detectTapGestures { pos ->
                        val contentX = pos.x + if (wordWrap) 0f else horizontalScrollState.value.toFloat()
                        if (contentX < codeStartXPx) return@detectTapGestures
                        val yInContent = (pos.y + verticalScrollState.value - verticalPaddingPx).coerceAtLeast(0f)
                        val visualLineIndex = (yInContent / lineHeightPx).toInt()
                        if (visualLineIndex < 0 || visualLineIndex >= totalVisualLines) return@detectTapGestures
                        val line = findLineForVisualIndex(visualLineStarts, visualLineIndex)
                        if (line !in lines.indices) return@detectTapGestures

                        val wrappedIndex = visualLineIndex - visualLineStarts[line]
                        val lineText = lines[line]
                        val startCol = if (wordWrap) wrappedIndex * wrapColumns else 0
                        val xInCode = (contentX - codeStartXPx).coerceAtLeast(0f)
                        val charInSegment = (xInCode / charWidthPx).toInt().coerceAtLeast(0)
                        val charInLine = (startCol + charInSegment).coerceAtMost(lineText.length)
                        val globalOffset = (lineStartOffsets[line] + charInLine).coerceIn(0, data.code.length)
                        val note = findAnnotationAtOffset(clickableNotes, globalOffset) ?: return@detectTapGestures
                        if (note.offset == cursorAddress) onJumpAndDecompile(note.offset)
                        else onAddressClick(note.offset)
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .background(Color(0xFF1E1E1E))
                    .then(
                        with(density) {
                            Modifier
                                .width(contentWidthPx.toDp())
                                .height(contentHeightPx.toDp())
                        }
                    )
            ) {
                val firstVisual = (((verticalScrollState.value - verticalPaddingPx) / lineHeightPx).toInt() - 2)
                    .coerceAtLeast(0)
                val visibleRows = (viewportHeightPx / lineHeightPx).toInt() + 5
                val lastVisual = (firstVisual + visibleRows).coerceAtMost(totalVisualLines)
                val gutterX = if (wordWrap) 0f else horizontalScrollState.value.toFloat()
                val nonWrapStartCol = if (wordWrap) 0 else {
                    (((horizontalScrollState.value - codeStartXPx) / charWidthPx).toInt()).coerceAtLeast(0)
                }
                val nonWrapVisibleCols = if (wordWrap) Int.MAX_VALUE
                else (viewportWidthPx / charWidthPx).toInt() + 8

                if (showLineNumbers) {
                    drawRect(
                        color = Color(0xFF252526),
                        topLeft = Offset(gutterX, 0f),
                        size = androidx.compose.ui.geometry.Size(lineNumberWidthPx, contentHeightPx)
                    )
                }

                for (visual in firstVisual until lastVisual) {
                    val line = findLineForVisualIndex(visualLineStarts, visual)
                    if (line !in lines.indices) continue
                    val wrappedIndex = visual - visualLineStarts[line]
                    val lineText = lines[line]
                    val startCol = if (wordWrap) wrappedIndex * wrapColumns else nonWrapStartCol
                    if (startCol > lineText.length) continue
                    val endCol = if (wordWrap) minOf(lineText.length, startCol + wrapColumns)
                    else minOf(lineText.length, startCol + nonWrapVisibleCols)
                    val segment = lineText.substring(startCol, endCol)
                    val baselineY = verticalPaddingPx + visual * lineHeightPx

                    val lineNotes = annotationsByLine[line]
                    val lineStartOffset = lineStartOffsets[line]
                    val segmentGlobalStart = lineStartOffset + startCol
                    val segmentGlobalEnd = lineStartOffset + endCol

                    val segmentText = buildAnnotatedString {
                        append(segment)
                        if (cursorAddress != 0L) {
                            lineNotes.filter { it.offset == cursorAddress }.forEach { note ->
                                val start = maxOf(segmentGlobalStart, note.start)
                                val end = minOf(segmentGlobalEnd, note.end)
                                if (start < end) {
                                    addStyle(
                                        SpanStyle(background = highlightBackgroundColor),
                                        start - segmentGlobalStart,
                                        end - segmentGlobalStart
                                    )
                                }
                            }
                        }
                        lineNotes.forEach { note ->
                            val color = syntaxColor(note.syntaxHighlight) ?: return@forEach
                            val start = maxOf(segmentGlobalStart, note.start)
                            val end = minOf(segmentGlobalEnd, note.end)
                            if (start < end) {
                                addStyle(
                                    SpanStyle(color = color),
                                    start - segmentGlobalStart,
                                    end - segmentGlobalStart
                                )
                            }
                        }
                    }

                    if (showLineNumbers) {
                        if (line == cursorLineIndex) {
                            drawRect(
                                color = highlightBackgroundColor,
                                topLeft = Offset(gutterX, baselineY),
                                size = androidx.compose.ui.geometry.Size(lineNumberWidthPx, lineHeightPx)
                            )
                        }
                        if (wrappedIndex == 0 || !wordWrap) {
                            val lineNo = (line + 1).toString()
                            drawText(
                                textMeasurer = textMeasurer,
                                text = AnnotatedString(lineNo),
                                style = textStyle.copy(
                                    color = if (line == cursorLineIndex) Color.White else Color(0xFF858585)
                                ),
                                topLeft = Offset(
                                    x = gutterX + lineNumberWidthPx - horizontalPaddingPx - lineNo.length * charWidthPx,
                                    y = baselineY
                                )
                            )
                        }
                    }

                    drawText(
                        textMeasurer = textMeasurer,
                        text = segmentText,
                        style = textStyle,
                        topLeft = Offset(
                            x = codeStartXPx + if (wordWrap) 0f else startCol * charWidthPx,
                            y = baselineY
                        )
                    )
                }
            }
        }
    }
}

private fun buildLineStartOffsets(lines: List<String>): IntArray {
    val offsets = IntArray(lines.size)
    var current = 0
    for (i in lines.indices) {
        offsets[i] = current
        current += lines[i].length + 1
    }
    return offsets
}

private fun buildVisualLineStarts(wrappedLineCounts: IntArray): IntArray {
    val starts = IntArray(wrappedLineCounts.size + 1)
    var sum = 0
    for (i in wrappedLineCounts.indices) {
        starts[i] = sum
        sum += wrappedLineCounts[i]
    }
    starts[wrappedLineCounts.size] = sum
    return starts
}

private fun findLineForOffset(lineStartOffsets: IntArray, offset: Int): Int {
    if (lineStartOffsets.isEmpty()) return -1
    var low = 0
    var high = lineStartOffsets.lastIndex
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lineStartOffsets[mid] <= offset) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

private fun findLineForVisualIndex(visualLineStarts: IntArray, visualIndex: Int): Int {
    var low = 0
    var high = visualLineStarts.size - 2
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (visualLineStarts[mid] <= visualIndex) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

private fun buildAnnotationsByLine(
    annotations: List<DecompilationAnnotation>,
    lineStartOffsets: IntArray,
    lineCount: Int
): List<List<DecompilationAnnotation>> {
    val result = List(lineCount) { mutableListOf<DecompilationAnnotation>() }
    annotations.forEach { note ->
        if (note.start >= note.end) return@forEach
        val startLine = findLineForOffset(lineStartOffsets, note.start)
        val endLine = findLineForOffset(lineStartOffsets, note.end - 1)
        for (line in startLine..endLine) {
            if (line in 0 until lineCount) result[line].add(note)
        }
    }
    return result
}

private fun syntaxColor(type: String?): Color? = when (type) {
    "datatype" -> Color(0xFF569CD6)
    "function_name" -> Color(0xFFFFD700)
    "keyword" -> Color(0xFFC586C0)
    "local_variable" -> Color(0xFF9CDCFE)
    "global_variable" -> Color(0xFF4EC9B0)
    "comment" -> Color(0xFF6A9955)
    "string" -> Color(0xFFCE9178)
    "offset" -> Color(0xFFB5CEA8)
    else -> null
}

private fun findAnnotationAtOffset(
    sortedAnnotations: List<DecompilationAnnotation>,
    offset: Int
): DecompilationAnnotation? {
    var low = 0
    var high = sortedAnnotations.lastIndex
    var candidate = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (sortedAnnotations[mid].start <= offset) {
            candidate = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    if (candidate < 0) return null
    for (i in candidate downTo 0) {
        val note = sortedAnnotations[i]
        if (note.start > offset) continue
        if (note.end < offset) continue
        if (offset in note.start..note.end) return note
    }
    return null
}

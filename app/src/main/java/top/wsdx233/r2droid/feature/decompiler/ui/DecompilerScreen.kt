package top.wsdx233.r2droid.feature.decompiler.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import top.wsdx233.r2droid.core.data.model.DecompilationData
import top.wsdx233.r2droid.feature.project.ProjectViewModel
import top.wsdx233.r2droid.ui.theme.LocalAppFont

internal const val MIN_DECOMPILER_SCALE = 0.5f
internal const val MAX_DECOMPILER_SCALE = 3f
private const val BASE_DECOMPILER_FONT_SIZE = 13f
private const val BASE_DECOMPILER_LINE_HEIGHT = 18f

internal fun computeDecompilerLineHeightsPx(
    layout: TextLayoutResult,
    rawLineStarts: List<Int>
): List<Int> {
    if (rawLineStarts.isEmpty() || layout.lineCount <= 0) return emptyList()
    val maxOffset = layout.layoutInput.text.length
    val lastVisualLine = layout.lineCount - 1
    return rawLineStarts.indices.map { i ->
        val start = rawLineStarts[i].coerceIn(0, maxOffset)
        val vLine = layout.getLineForOffset(start).coerceIn(0, lastVisualLine)
        val top = layout.getLineTop(vLine)
        val bottom = if (i < rawLineStarts.lastIndex) {
            val nextStart = rawLineStarts[i + 1].coerceIn(0, maxOffset)
            val nextVLine = layout.getLineForOffset(nextStart).coerceIn(0, lastVisualLine)
            if (nextVLine > vLine) {
                layout.getLineTop(nextVLine)
            } else {
                layout.getLineBottom(vLine)
            }
        } else {
            layout.getLineBottom(lastVisualLine)
        }
        (bottom.roundToInt() - top.roundToInt()).coerceAtLeast(0)
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
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
    val resetZoomTrigger by viewModel.resetZoomTrigger.collectAsState()
    val scrollToSelectionTrigger by viewModel.scrollToSelectionTrigger.collectAsState()

    var scale by remember {
        mutableFloatStateOf(
            viewModel.decompilerZoomScale.value.coerceIn(MIN_DECOMPILER_SCALE, MAX_DECOMPILER_SCALE)
        )
    }
    LaunchedEffect(resetZoomTrigger) {
        if (resetZoomTrigger > 0) scale = 1f
    }

    DecompilationViewerContent(
        data = data,
        cursorAddress = cursorAddress,
        showLineNumbers = showLineNumbers,
        wordWrap = wordWrap,
        scale = scale,
        scrollToSelectionTrigger = scrollToSelectionTrigger,
        onScaleChange = { scale = it },
        onScaleChangeCommitted = { viewModel.updateDecompilerZoomScale(it) },
        onAddressClick = onAddressClick,
        onJumpAndDecompile = onJumpAndDecompile
    )
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
internal fun DecompilationViewerContent(
    data: DecompilationData,
    cursorAddress: Long,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    scale: Float,
    scrollToSelectionTrigger: Int = 0,
    onScaleChange: (Float) -> Unit = {},
    onScaleChangeCommitted: (Float) -> Unit = {},
    onAddressClick: (Long) -> Unit = {},
    onJumpAndDecompile: (Long) -> Unit = {}
) {
    if (data.code.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Decompilation Data", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val effectiveScale = scale.coerceIn(MIN_DECOMPILER_SCALE, MAX_DECOMPILER_SCALE)
    val scaledFontSize = (BASE_DECOMPILER_FONT_SIZE * effectiveScale).sp
    val scaledLineHeight = (BASE_DECOMPILER_LINE_HEIGHT * effectiveScale).sp

    val appFont = LocalAppFont.current
    val lineHeightStyle = remember {
        LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    }
    val platformTextStyle = remember { PlatformTextStyle(includeFontPadding = false) }
    val codeTextStyle = remember(appFont, scaledFontSize, scaledLineHeight) {
        TextStyle(
            fontFamily = appFont,
            fontSize = scaledFontSize,
            lineHeight = scaledLineHeight,
            color = Color(0xFFD4D4D4),
            platformStyle = platformTextStyle,
            lineHeightStyle = lineHeightStyle
        )
    }
    val lineNumberTextStyle = remember(appFont, scaledFontSize, scaledLineHeight) {
        TextStyle(
            fontFamily = appFont,
            fontSize = scaledFontSize,
            lineHeight = scaledLineHeight,
            platformStyle = platformTextStyle,
            lineHeightStyle = lineHeightStyle
        )
    }

    var textLayoutResult by remember(data.code) { mutableStateOf<TextLayoutResult?>(null) }
    val scrollState = rememberScrollState()

    // Normalize line endings and track exact line start offsets for accurate cursor/wrap mapping
    val normalizedCode = remember(data.code) { data.code.replace("\r\n", "\n") }
    val lines = remember(normalizedCode) { normalizedCode.split('\n') }

    // Find which line corresponds to cursor address
    val cursorLineIndex = remember(cursorAddress, data.annotations, lines) {
        if (cursorAddress == 0L) -1
        else {
            val note = data.annotations.firstOrNull { it.offset == cursorAddress }
            if (note != null && note.start >= 0 && note.start < data.code.length) {
                var charCount = 0
                var idx = 0
                val raw = data.code
                for (i in 0 until note.start.coerceAtMost(raw.length)) {
                    if (raw[i] == '\n') idx++
                    charCount = i + 1
                }
                idx.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
            } else -1
        }
    }

    // Dark Blue background for highlighted line (adapted for dark theme)
    val highlightBackgroundColor = Color(0xFF1A3A60)

    // Process annotations (memoized so pinch-zoom recompositions don't rebuild spans every frame)
    val annotatedString = remember(data, cursorAddress) {
        buildAnnotatedString {
            append(data.code)

            data.annotations.forEach { note ->
                val start = note.start
                val end = note.end

                // Validate bounds
                if (start >= 0 && end <= data.code.length && start < end) {
                    if (note.offset == cursorAddress && cursorAddress != 0L) {
                        addStyle(
                            style = SpanStyle(background = highlightBackgroundColor),
                            start = start,
                            end = end
                        )
                    }

                    val color = when (note.syntaxHighlight) {
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
    }
    
    // Auto-scroll logic
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    
    // Track previous cursor address to only scroll when it actually changes
    var previousCursorAddress by remember { mutableLongStateOf(cursorAddress) }
    var hasInitiallyScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(cursorAddress, textLayoutResult) {
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

    // Measure line number width using actual typography so it adapts to zoom, fontScale, and custom fonts
    val textMeasurer = rememberTextMeasurer()
    val lineNumberHorizontalPadding = remember(effectiveScale) {
        (8f * effectiveScale).coerceIn(4f, 12f).dp
    }
    val lineNumberWidth = remember(
        lines.size,
        lineNumberTextStyle,
        density,
        textMeasurer,
        lineNumberHorizontalPadding
    ) {
        val digitCount = lines.size.toString().length.coerceAtLeast(2)
        val maxLineLabel = lines.size.toString()
        val measuredWidthPx = maxOf(
            textMeasurer.measure(
                text = "8".repeat(digitCount),
                style = lineNumberTextStyle,
                maxLines = 1,
                softWrap = false
            ).size.width,
            textMeasurer.measure(
                text = "0".repeat(digitCount),
                style = lineNumberTextStyle,
                maxLines = 1,
                softWrap = false
            ).size.width,
            textMeasurer.measure(
                text = maxLineLabel,
                style = lineNumberTextStyle,
                maxLines = 1,
                softWrap = false
            ).size.width
        )
        with(density) { measuredWidthPx.toDp() } + lineNumberHorizontalPadding * 2 + 2.dp
    }

    val horizontalScrollState = rememberScrollState()

    // Compute exact line start offsets in raw data.code (handling both LF and CRLF)
    val rawLineStarts = remember(data.code) {
        val starts = mutableListOf(0)
        val raw = data.code
        for (i in raw.indices) {
            if (raw[i] == '\n') starts.add(i + 1)
        }
        starts
    }

    // Compute cumulative-rounded per-line heights directly from TextLayoutResult for exact alignment
    val lineHeightsPx = remember(
        textLayoutResult,
        rawLineStarts,
        data.code,
        scaledFontSize,
        scaledLineHeight,
        wordWrap
    ) {
        val layout = textLayoutResult ?: return@remember null
        if (layout.layoutInput.text.text != data.code) return@remember null
        if (layout.layoutInput.softWrap != wordWrap) return@remember null
        if (layout.layoutInput.style.fontSize != scaledFontSize ||
            layout.layoutInput.style.lineHeight != scaledLineHeight
        ) return@remember null
        computeDecompilerLineHeightsPx(layout, rawLineStarts)
    }

    val scaledLineHeightDp = with(density) { scaledLineHeight.toDp() }
    val currentScale by rememberUpdatedState(effectiveScale)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnScaleChangeCommitted by rememberUpdatedState(onScaleChangeCommitted)

    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .verticalScroll(scrollState)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        var prevSpan = 0f
                        var zooming = false
                        var activeScale = currentScale
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val p1 = pressed[0].position
                                val p2 = pressed[1].position
                                val span = (p1 - p2).getDistance()
                                if (zooming && prevSpan > 1f) {
                                    val centroid = Offset(
                                        (p1.x + p2.x) / 2f,
                                        (p1.y + p2.y) / 2f
                                    )
                                    val oldScale = activeScale
                                    val newScale = (oldScale * span / prevSpan)
                                        .coerceIn(MIN_DECOMPILER_SCALE, MAX_DECOMPILER_SCALE)
                                    if (newScale != oldScale) {
                                        scrollState.dispatchRawDelta(
                                            (scrollState.value + centroid.y) *
                                                (newScale / oldScale - 1f)
                                        )
                                        activeScale = newScale
                                        currentOnScaleChange(newScale)
                                    }
                                } else if (!zooming) {
                                    activeScale = currentScale
                                }
                                prevSpan = span
                                zooming = true
                                event.changes.forEach { it.consume() }
                            } else if (zooming) {
                                event.changes.forEach { it.consume() }
                                prevSpan = 0f
                            }
                        } while (event.changes.any { it.pressed })
                        if (zooming) currentOnScaleChangeCommitted(activeScale)
                    }
                }
        ) {
            // Line numbers column (conditional)
            if (showLineNumbers) {
                DisableSelection {
                    Box(
                        modifier = Modifier
                            .width(lineNumberWidth)
                            .background(Color(0xFF252526))
                            .padding(top = 8.dp, bottom = 8.dp)
                            .testTag("decompiler_line_number_gutter")
                    ) {
                        Column {
                            lines.forEachIndexed { index, _ ->
                                val isCurrentLine = index == cursorLineIndex
                                val h = lineHeightsPx?.getOrNull(index)?.let { with(density) { it.toDp() } }
                                    ?: scaledLineHeightDp
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(h)
                                        .background(if (isCurrentLine) highlightBackgroundColor else Color.Transparent),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    Text(
                                        text = (index + 1).toString(),
                                        style = lineNumberTextStyle,
                                        color = if (isCurrentLine) Color(0xFFFFFFFF) else Color(0xFF858585),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible,
                                        modifier = Modifier
                                            .padding(horizontal = lineNumberHorizontalPadding)
                                            .testTag("decompiler_line_number_${index + 1}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Code content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(if (!wordWrap) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                Text(
                    text = annotatedString,
                    style = codeTextStyle,
                    softWrap = wordWrap,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .testTag("decompiler_code_text")
                        .pointerInput(data, cursorAddress) {
                            detectTapGestures { pos ->
                                val layout = textLayoutResult ?: return@detectTapGestures
                                if (pos.y <= layout.size.height) {
                                    val offset = layout.getOffsetForPosition(pos)
                                    val note = data.annotations.firstOrNull { it.start <= offset && it.end >= offset }
                                    if (note != null && note.offset != 0L) {
                                        if (note.offset == cursorAddress) {
                                            onJumpAndDecompile(note.offset)
                                        } else {
                                            onAddressClick(note.offset)
                                        }
                                    }
                                }
                            }
                        }
                )
            }
        }
    }
}

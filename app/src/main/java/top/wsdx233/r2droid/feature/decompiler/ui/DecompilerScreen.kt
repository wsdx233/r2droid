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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.core.data.model.DecompilationData
import top.wsdx233.r2droid.feature.project.ProjectViewModel
import top.wsdx233.r2droid.ui.theme.LocalAppFont

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
    val scaledLineHeight = (baseLineHeight * scale).sp

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scrollState = rememberScrollState()

    // Split code into lines for line number display
    val lines = data.code.lines()
    
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

    // Calculate line number width based on total lines and scale
    val lineNumberWidth = remember(lines.size, scale) {
        val digits = lines.size.toString().length
        (digits * 10 * scale + 16).dp
    }

    val horizontalScrollState = rememberScrollState()

    // Compute per-line heights when word wrap is enabled (wrapped lines need more space)
    val lineHeights = remember(textLayoutResult, wordWrap, lines.size) {
        val layout = textLayoutResult ?: return@remember null
        if (!wordWrap) return@remember null
        var charOffset = 0
        lines.mapIndexed { i, line ->
            val start = charOffset
            charOffset += line.length + 1
            val vLine = layout.getLineForOffset(start.coerceAtMost(layout.layoutInput.text.length - 1))
            val top = layout.getLineTop(vLine)
            val bottom = if (i < lines.size - 1) {
                val ns = charOffset.coerceAtMost(layout.layoutInput.text.length - 1)
                layout.getLineTop(layout.getLineForOffset(ns))
            } else {
                layout.getLineBottom(layout.lineCount - 1)
            }
            bottom - top
        }
    }

    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .pointerInput(Unit) {
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
                                    val centroid = Offset(
                                        (p1.x + p2.x) / 2f,
                                        (p1.y + p2.y) / 2f
                                    )
                                    val oldScale = scale
                                    val newScale = (oldScale * span / prevSpan)
                                        .coerceIn(0.5f, 3f)
                                    if (newScale != oldScale) {
                                        scrollState.dispatchRawDelta(
                                            (scrollState.value + centroid.y) *
                                                (newScale / oldScale - 1f)
                                        )
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
            // Line numbers column (conditional)
            if (showLineNumbers) {
                DisableSelection {
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
                                val h = lineHeights?.getOrNull(index)?.let { with(density) { it.toDp() } }
                                    ?: scaledLineHeight.value.dp
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(h)
                                        .background(if (isCurrentLine) highlightBackgroundColor else Color.Transparent),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = (index + 1).toString(),
                                        fontFamily = LocalAppFont.current,
                                        fontSize = scaledFontSize,
                                        color = if (isCurrentLine) Color(0xFFFFFFFF) else Color(0xFF858585),
                                        modifier = Modifier.padding(end = 8.dp)
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
                    .verticalScroll(scrollState)
                    .then(if (!wordWrap) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                Text(
                    text = annotatedString,
                    fontFamily = LocalAppFont.current,
                    fontSize = scaledFontSize,
                    color = Color(0xFFD4D4D4),
                    lineHeight = scaledLineHeight,
                    softWrap = wordWrap,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier.pointerInput(cursorAddress) {
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

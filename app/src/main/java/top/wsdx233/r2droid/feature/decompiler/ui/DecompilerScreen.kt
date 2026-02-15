package top.wsdx233.r2droid.feature.decompiler.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.core.data.model.DecompilationData
import top.wsdx233.r2droid.data.SettingsManager
import top.wsdx233.r2droid.feature.project.ProjectViewModel
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@Composable
fun DecompilationViewer(
    viewModel: ProjectViewModel,
    data: DecompilationData,
    cursorAddress: Long,
    onAddressClick: (Long) -> Unit
) {
    val showLineNumbers = remember { SettingsManager.decompilerShowLineNumbers }
    val wordWrap = remember { SettingsManager.decompilerWordWrap }
    if (data.code.isBlank()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Decompilation Data", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

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
    var previousCursorAddress by remember { mutableStateOf(cursorAddress) }
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

    // Calculate line number width based on total lines
    val lineNumberWidth = remember(lines.size) {
        val digits = lines.size.toString().length
        (digits * 10 + 16).dp // Approximate width per digit + padding
    }

    val horizontalScrollState = rememberScrollState()

    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
        ) {
            // Line numbers column (conditional)
            if (showLineNumbers) {
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
                                    fontFamily = LocalAppFont.current,
                                    fontSize = 13.sp,
                                    color = if (isCurrentLine) Color(0xFFFFFFFF) else Color(0xFF858585),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
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
                    fontSize = 13.sp,
                    color = Color(0xFFD4D4D4),
                    lineHeight = 18.sp,
                    softWrap = wordWrap,
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

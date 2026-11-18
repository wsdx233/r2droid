package top.wsdx233.r2droid.feature.disasm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.core.data.model.formatCompactAddress
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme

/** Pick color based on current theme */
@Composable
private fun dc(light: Long, dark: Long): Color =
    if (LocalDarkTheme.current) Color(dark) else Color(light)

private fun DrawScope.band(start: Float, end: Float, color: Color) {
    if (end <= start) return
    val left = if (layoutDirection == LayoutDirection.Ltr) start else size.width - end
    drawRect(color, Offset(left, 0f), Size(end - start, size.height))
}

@Composable
private fun InstructionLine(
    modifier: Modifier,
    onGutterClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Box {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, content = content)
        // The whole gutter remains tappable even beside a multiline instruction.
        Spacer(Modifier.matchParentSize().wrapContentWidth(Alignment.Start)
            .width(DisasmRowLayout.GutterWidth).clickable(onClick = onGutterClick))
    }
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
    isMultiSelected: Boolean = false,
    isPC: Boolean = false,
    isBreakpoint: Boolean = false,
    onGutterClick: () -> Unit = {},
    onClick: (Offset, Int) -> Unit,
    onLongClick: (Offset, Int) -> Unit,
    showMenu: Boolean = false,
    menuContent: @Composable () -> Unit = {},
    jumpIndex: Int? = null,
    jumpTargetIndex: Int? = null
) {
    // Whether this row has any highlight (cursor or multi-select)
    val highlighted = isSelected || isMultiSelected

    // Column background colors
    val colJumpBg = dc(0xFFF3F8FF, 0xFF1E2A3A)
    val colAddressBg = dc(0xFFEFF8EE, 0xFF1E2E28)
    val colBytesBg = dc(0xFFFFF8E1, 0xFF2E2818)
    val colOpcodeBg = dc(0xFFFFFFFF, 0xFF1E1E1E)
    val colCommentBg = dc(0xFFF8F0FF, 0xFF281E30)
    val colFlagBg = dc(0xFFE0F7FA, 0xFF1A3040)
    val colFuncHeaderBg = dc(0xFFE3F2FD, 0xFF1A2840)
    val colR2CommentBg = dc(0xFFF4F4F4, 0xFF401A28)
    val colXrefBg = dc(0xFFE0FFF1, 0xFF40301A)
    val colInlineCommentBg = dc(0xFFE5EAF5, 0xFF301A40)

    // Text colors
    val commentColor = dc(0xFF2E7D32, 0xFF6A9955)
    val flagColor = dc(0xFF00897B, 0xFF4EC9B0)
    val funcNameColor = dc(0xFF795548, 0xFFDCDCAA)
    val funcIconColor = dc(0xFF1565C0, 0xFF569CD6)
    val jumpOutColor = dc(0xFF2E7D32, 0xFF66BB6A)
    val jumpInColor = dc(0xFFF57F17, 0xFFFFCA28)
    val jumpInternalColor = dc(0xFF1976D2, 0xFF64B5F6)
    val addressColor = dc(0xFF616161, 0xFF888888)
    val bytesColor = dc(0xFF757575, 0xFF999999)

    // Opcode colors
    val opcodeColor = when (instr.type) {
        "call", "ucall", "ircall" -> dc(0xFF1565C0, 0xFF42A5F5)
        "jmp", "cjmp", "ujmp" -> dc(0xFF2E7D32, 0xFF66BB6A)
        "ret" -> dc(0xFFC62828, 0xFFEF5350)
        "push", "pop", "rpush" -> dc(0xFF7B1FA2, 0xFFAB47BC)
        "cmp", "test", "acmp" -> dc(0xFFF57F17, 0xFFFFCA28)
        "nop" -> Color.Gray
        "lea" -> dc(0xFF0277BD, 0xFF4FC3F7)
        "mov" -> dc(0xFF8D3B0A, 0xFFA25410)
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    // Check if this is the start of a function
    val isFunctionStart = instr.fcnAddr > 0 && instr.addr == instr.fcnAddr
    
    // Check for external jump out/in
    val isExternalJumpOut = remember(instr) { instr.isJumpOut() }
    val hasExternalJumpIn = remember(instr) { instr.hasJumpIn() }
    
    // Check if this is a jump instruction (internal or external)
    val isJumpInstruction = instr.type == "jmp" || instr.type == "cjmp" || instr.type == "ujmp"
    val isInternalJump = isJumpInstruction && instr.jump != null && !isExternalJumpOut
    
    // Determine jump direction for internal jumps
    val jumpDirection = if (isInternalJump && instr.jump != null) {
        if (instr.jump > instr.addr) "↓" else "↑"
    } else null
    
    // Prepare bytes and inline comment from pre-computed fields
    val displayBytes = instr.displayBytes
    val inlineComment = instr.inlineComment

    // Only comments go to secondary row (not bytes)
    val hasInlineComment = inlineComment.isNotEmpty()
    
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    Box {
        val pcHighlightColor = Color(0x40FFEB3B)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        isPC -> pcHighlightColor
                        isSelected && isMultiSelected -> MaterialTheme.colorScheme.tertiaryContainer
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isMultiSelected -> MaterialTheme.colorScheme.secondaryContainer
                        else -> Color.Transparent
                    }
                )
                .pointerInput(instr.addr) {
                    detectTapGestures(
                        onTap = { offset -> currentOnClick(offset, size.height) },
                        onLongPress = { offset -> currentOnLongClick(offset, size.height) }
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
                            .background(if (highlighted) Color.Transparent else colFlagBg)
                            .padding(start = DisasmRowLayout.AnnotationIndent, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ";-- $flag:",
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else flagColor,
                            fontFamily = LocalAppFont.current,
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
                } ?: "fcn.${"%%08x".format(instr.addr)}"
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (highlighted) Color.Transparent else colFuncHeaderBg)
                        .padding(start = DisasmRowLayout.AnnotationIndent, top = 2.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Blue function icon
                    Text(
                        text = "▶",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else funcIconColor,
                        fontFamily = LocalAppFont.current,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = "$funcSize: ",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontFamily = LocalAppFont.current,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "$funcName ();",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else funcNameColor,
                        fontFamily = LocalAppFont.current,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Let the tallest text set the line height (including wrapped opcodes). Painting
            // the column bands behind that line avoids an intrinsic text pass per lazy item.
            val inlineColumn = inlineComment.length in 1..20
            val inlineWidth = if (inlineColumn) remember { mutableIntStateOf(0) } else null
            InstructionLine(
                onGutterClick = onGutterClick,
                modifier = Modifier.fillMaxWidth().drawBehind {
                    if (!highlighted) {
                        val gutter = DisasmRowLayout.GutterWidth.roundToPx().toFloat().coerceAtMost(size.width)
                        val address = (gutter + DisasmRowLayout.AddressWidth.roundToPx()).coerceAtMost(size.width)
                        val bytes = (address + DisasmRowLayout.BytesWidth.roundToPx()).coerceAtMost(size.width)
                        val opcode = (size.width - (inlineWidth?.intValue ?: 0)).coerceAtLeast(bytes)
                        band(0f, gutter, colJumpBg)
                        band(gutter, address, colAddressBg)
                        band(address, bytes, colBytesBg)
                        band(bytes, opcode, colOpcodeBg)
                        band(opcode, size.width, colInlineCommentBg)
                    }
                }
            ) {
                // Used as gutter for breakpoints and PC indicator
                Box(
                    modifier = Modifier.width(DisasmRowLayout.GutterWidth),
                    contentAlignment = Alignment.Center
                ) {
                    // Render breakpoint red dot in background
                    if (isBreakpoint) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color.Red.copy(alpha=0.8f), shape = CircleShape)
                        )
                    }
                    
                    // Main Jump text
                    when {
                        // External jump out - green left arrow
                        isExternalJumpOut -> {
                            Text(
                                text = "←",
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else jumpOutColor,
                                fontFamily = LocalAppFont.current,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // External jump in target - yellow right arrow
                        hasExternalJumpIn -> {
                            Text(
                                text = "→",
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else jumpInColor,
                                fontFamily = LocalAppFont.current,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Internal jump instruction - blue arrow with direction and last 2 digits
                        isInternalJump && jumpIndex != null -> {
                            Text(
                                text = "${jumpDirection ?: ""}${formatJumpIndex(jumpIndex)}",
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else jumpInternalColor,
                                fontFamily = LocalAppFont.current,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Jump target - show target indicator with last 2 digits
                        jumpTargetIndex != null -> {
                            Text(
                                text = "▸${formatJumpIndex(jumpTargetIndex)}",
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else jumpInternalColor,
                                fontFamily = LocalAppFont.current,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Render PC arrow overriding everything (or overlaying)
                    if (isPC) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowRightAlt,
                            contentDescription = "PC",
                            tint = Color.Yellow,
                            modifier = Modifier.size(16.dp).align(Alignment.Center)
                        )
                    }
                }
                
                // Address column - compact format with background
                Box(
                    modifier = Modifier
                        .width(DisasmRowLayout.AddressWidth)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = instr.displayAddress,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else addressColor,
                        fontFamily = LocalAppFont.current,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Bytes column - always visible, truncated with ...
                Box(
                    modifier = Modifier
                        .width(DisasmRowLayout.BytesWidth)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = displayBytes,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else bytesColor,
                        fontFamily = LocalAppFont.current,
                        fontSize = 10.sp
                    )
                }
                
                // Opcode / Disasm column - with background, takes remaining space
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = instr.disasm,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else opcodeColor,
                        fontFamily = LocalAppFont.current,
                        fontSize = DisasmRowLayout.OpcodeFontSize,
                        fontWeight = when (instr.type) {
                            "call", "jmp", "cjmp", "ret" -> FontWeight.Bold
                            else -> FontWeight.Normal
                        }
                    )
                }
                
                // Inline comment column (if present and short enough for same line)
                if (inlineWidth != null) {
                    Box(
                        modifier = Modifier
                            .onSizeChanged { inlineWidth.intValue = it.width }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = inlineComment,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else commentColor,
                            fontFamily = LocalAppFont.current,
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
                        .background(if (highlighted) Color.Transparent else colInlineCommentBg)
                        .padding(start = DisasmRowLayout.AnnotationIndent, top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = inlineComment,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f) else commentColor,
                        fontFamily = LocalAppFont.current,
                        fontSize = 10.sp
                    )
                }
            }
            
            // === Post-instruction comment (from radare2) ===
            if (!instr.comment.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (highlighted) Color.Transparent else colR2CommentBg)
                        .padding(start = DisasmRowLayout.AnnotationIndent, top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "; ${instr.comment}",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else commentColor,
                        fontFamily = LocalAppFont.current,
                        fontSize = 10.sp
                    )
                }
            }
            
            // Show xref comments for jump targets
            if (instr.xrefs.isNotEmpty()) {
                val codeXrefs = remember(instr.xrefs) { instr.xrefs.filter { it.type == "CODE" } }
                if (codeXrefs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (highlighted) Color.Transparent else colXrefBg)
                            .padding(start = DisasmRowLayout.AnnotationIndent, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val xrefText = if (codeXrefs.size == 1) {
                            "; XREF from ${formatCompactAddress(codeXrefs[0].addr)}"
                        } else {
                            "; XREF from ${codeXrefs.size} locations"
                        }
                        Text(
                            text = xrefText,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else commentColor,
                            fontFamily = LocalAppFont.current,
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

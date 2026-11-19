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
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.core.data.model.formatCompactAddress

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

private val addressColumn = Modifier.width(DisasmRowLayout.AddressWidth)
    .padding(horizontal = 2.dp).wrapContentWidth(Alignment.End)
private val bytesColumn = Modifier.width(DisasmRowLayout.BytesWidth)
    .padding(horizontal = 2.dp).wrapContentWidth(Alignment.Start)
private val annotationPadding = Modifier.padding(start = DisasmRowLayout.AnnotationIndent, top = 1.dp, bottom = 1.dp)
    .wrapContentWidth(Alignment.Start)

@Composable
private fun AnnotationLine(text: String, style: TextStyle, background: Color, highlighted: Boolean) {
    val line = Modifier.fillMaxWidth()
    BasicText(text, (if (highlighted) line else line.background(background)).then(annotationPadding), style)
}

@Composable
internal fun DisasmRow(
    instr: DisasmInstruction,
    style: DisasmRowStyle,
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
    val isSelected = style.selected
    val highlighted = isSelected || isMultiSelected
    val background = when {
        isPC -> Color(0x40FFEB3B)
        isSelected && isMultiSelected -> MaterialTheme.colorScheme.tertiaryContainer
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isMultiSelected -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Unspecified
    }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val row = Modifier.fillMaxWidth()
    Column((if (background == Color.Unspecified) row else row.background(background))
        .pointerInput(instr.addr) {
            detectTapGestures(
                onTap = { currentOnClick(it, size.height) },
                onLongPress = { currentOnLongClick(it, size.height) }
            )
        }
    ) {
        instr.flags.forEach { AnnotationLine(";-- $it:", style.flag, style.flagBg, highlighted) }
        if (instr.fcnAddr > 0 && instr.addr == instr.fcnAddr) {
            val funcSize = if (instr.fcnLast > instr.fcnAddr) instr.fcnLast - instr.fcnAddr else 0
            val funcName = instr.flags.firstOrNull { !it.startsWith("section.") && !it.startsWith("reloc.") }
                ?: "fcn.${"%%08x".format(instr.addr)}"
            val header = Modifier.fillMaxWidth()
            Row((if (highlighted) header else header.background(style.headerBg))
                .padding(start = DisasmRowLayout.AnnotationIndent, top = 2.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText("▶", Modifier.padding(end = 4.dp), style.functionIcon)
                BasicText("$funcSize: ", style = style.functionSize)
                BasicText("$funcName ();", style = style.functionName)
            }
        }

        val inlineComment = instr.inlineComment
        val inlineWidth = if (inlineComment.length in 1..20) remember { mutableIntStateOf(0) } else null
        // Let the tallest cell set the height, including wrapped text. Only the trailing band
        // depends on a measured width; read it during draw rather than composition.
        val bands = if (highlighted) Modifier else Modifier.drawBehind {
            val gutter = DisasmRowLayout.GutterWidth.roundToPx().toFloat().coerceAtMost(size.width)
            val address = (gutter + DisasmRowLayout.AddressWidth.roundToPx()).coerceAtMost(size.width)
            val bytes = (address + DisasmRowLayout.BytesWidth.roundToPx()).coerceAtMost(size.width)
            val opcode = (size.width - (inlineWidth?.intValue ?: 0)).coerceAtLeast(bytes)
            band(0f, gutter, style.gutterBg)
            band(gutter, address, style.addressBg)
            band(address, bytes, style.bytesBg)
            band(bytes, opcode, style.opcodeBg)
            band(opcode, size.width, style.inlineBg)
        }
        InstructionLine(modifier = Modifier.fillMaxWidth().then(bands), onGutterClick = onGutterClick) {
            Box(Modifier.width(DisasmRowLayout.GutterWidth), contentAlignment = Alignment.Center) {
                if (isBreakpoint) {
                    Spacer(Modifier.size(12.dp).background(Color.Red.copy(alpha = .8f), CircleShape))
                }
                val hasJumpIn = remember(instr) { instr.hasJumpIn() }
                when {
                    instr.isJumpOut() -> BasicText("←", style = style.outgoing)
                    hasJumpIn -> BasicText("→", style = style.incoming)
                    jumpIndex != null && instr.jump != null -> {
                        val direction = if (instr.jump > instr.addr) "↓" else "↑"
                        BasicText("$direction${formatJumpIndex(jumpIndex)}", style = style.branch)
                    }
                    jumpTargetIndex != null -> BasicText("▸${formatJumpIndex(jumpTargetIndex)}", style = style.target)
                }
                if (isPC) {
                    Icon(Icons.AutoMirrored.Filled.ArrowRightAlt, contentDescription = "PC",
                        modifier = Modifier.size(16.dp), tint = Color.Yellow)
                }
            }
            BasicText(instr.displayAddress, addressColumn, style.address)
            BasicText(instr.displayBytes, bytesColumn, style.bytes)
            BasicText(instr.disasm, Modifier.weight(1f).padding(horizontal = 4.dp)
                .wrapContentWidth(Alignment.Start), style.opcode(instr.type))
            if (inlineWidth != null) {
                BasicText(inlineComment, Modifier.onSizeChanged { inlineWidth.intValue = it.width }
                    .padding(horizontal = 4.dp), style.comment)
            }
        }

        if (inlineComment.length > 20) {
            AnnotationLine(inlineComment, style.longComment, style.inlineBg, highlighted)
        }
        if (!instr.comment.isNullOrEmpty()) {
            AnnotationLine("; ${instr.comment}", style.comment, style.commentBg, highlighted)
        }
        if (instr.xrefs.isNotEmpty()) {
            val xrefText = remember(instr.xrefs) {
                var count = 0
                var from = 0L
                for (xref in instr.xrefs) if (xref.type == "CODE") {
                    count++
                    from = xref.addr
                }
                when (count) {
                    0 -> null
                    1 -> "; XREF from ${formatCompactAddress(from)}"
                    else -> "; XREF from $count locations"
                }
            }
            if (xrefText != null) AnnotationLine(xrefText, style.xref, style.xrefBg, highlighted)
        }

        // Popup uses its parent's bounds; keep it anchored to the entire instruction.
        if (showMenu) menuContent()
    }
}

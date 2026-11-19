package top.wsdx233.r2droid.feature.disasm.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme

/** Resolve the palette and merge typography once per viewer, not once per cell entering a fling. */
@Composable
internal fun rememberDisasmRowStyle(selected: Boolean): DisasmRowStyle {
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val base = LocalTextStyle.current
    val font = LocalAppFont.current
    return remember(dark, scheme, base, font, selected) {
        DisasmRowStyle(dark, scheme, base.copy(fontFamily = font), selected)
    }
}

internal class DisasmRowStyle(
    private val dark: Boolean,
    scheme: ColorScheme,
    private val base: TextStyle,
    val selected: Boolean
) {
    private fun tone(light: Long, night: Long) = Color(if (dark) night else light)
    private val selectionInk = scheme.onPrimaryContainer

    private fun text(color: Color, size: TextUnit, weight: FontWeight? = base.fontWeight, selectedAlpha: Float = 1f) =
        base.copy(
            color = when {
                !selected -> color
                selectedAlpha == 1f -> selectionInk
                else -> selectionInk.copy(alpha = selectedAlpha)
            },
            fontSize = size,
            fontWeight = weight
        )

    val gutterBg = tone(0xFFF3F8FF, 0xFF1E2A3A)
    val addressBg = tone(0xFFEFF8EE, 0xFF1E2E28)
    val bytesBg = tone(0xFFFFF8E1, 0xFF2E2818)
    val opcodeBg = tone(0xFFFFFFFF, 0xFF1E1E1E)
    val inlineBg = tone(0xFFE5EAF5, 0xFF301A40)
    val flagBg = tone(0xFFE0F7FA, 0xFF1A3040)
    val headerBg = tone(0xFFE3F2FD, 0xFF1A2840)
    val commentBg = tone(0xFFF4F4F4, 0xFF401A28)
    val xrefBg = tone(0xFFE0FFF1, 0xFF40301A)

    val address = text(tone(0xFF616161, 0xFF888888), 11.sp, FontWeight.Medium)
    val bytes = text(tone(0xFF757575, 0xFF999999), 10.sp, selectedAlpha = .7f)
    val flag = text(tone(0xFF00897B, 0xFF4EC9B0), 11.sp)
    val functionIcon = text(tone(0xFF1565C0, 0xFF569CD6), 12.sp, FontWeight.Bold)
    val functionSize = text(scheme.onSurface.copy(alpha = .7f), 11.sp, selectedAlpha = .7f)
    val functionName = text(tone(0xFF795548, 0xFFDCDCAA), 11.sp, FontWeight.Bold)
    private val commentColor = tone(0xFF2E7D32, 0xFF6A9955)
    val comment = text(commentColor, 10.sp)
    val longComment = text(commentColor, 10.sp, selectedAlpha = .9f)
    val xref = text(commentColor, 10.sp, selectedAlpha = .8f)
    val outgoing = text(tone(0xFF2E7D32, 0xFF66BB6A), 11.sp, FontWeight.Bold)
    val incoming = text(tone(0xFFF57F17, 0xFFFFCA28), 11.sp, FontWeight.Bold)
    val target = text(tone(0xFF1976D2, 0xFF64B5F6), 9.sp)
    val branch = target.copy(fontWeight = FontWeight.Bold)

    private fun opcodeStyle(color: Color) = text(color, DisasmRowLayout.OpcodeFontSize, FontWeight.Normal)
    private val plain = opcodeStyle(scheme.onSurface)
    private val call = opcodeStyle(tone(0xFF1565C0, 0xFF42A5F5))
    private val directCall = call.copy(fontWeight = FontWeight.Bold)
    private val jump = opcodeStyle(tone(0xFF2E7D32, 0xFF66BB6A))
    private val directJump = jump.copy(fontWeight = FontWeight.Bold)
    private val ret = text(tone(0xFFC62828, 0xFFEF5350), DisasmRowLayout.OpcodeFontSize, FontWeight.Bold)
    private val stack = opcodeStyle(tone(0xFF7B1FA2, 0xFFAB47BC))
    private val compare = opcodeStyle(tone(0xFFF57F17, 0xFFFFCA28))
    private val nop = opcodeStyle(Color.Gray)
    private val lea = opcodeStyle(tone(0xFF0277BD, 0xFF4FC3F7))
    private val mov = opcodeStyle(tone(0xFF8D3B0A, 0xFFA25410))

    fun opcode(type: String): TextStyle = when (type) {
        "call" -> directCall
        "ucall", "ircall" -> call
        "jmp", "cjmp" -> directJump
        "ujmp" -> jump
        "ret" -> ret
        "push", "pop", "rpush" -> stack
        "cmp", "test", "acmp" -> compare
        "nop" -> nop
        "lea" -> lea
        "mov" -> mov
        else -> plain
    }
}

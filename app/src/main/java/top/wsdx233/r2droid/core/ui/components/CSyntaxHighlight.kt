package top.wsdx233.r2droid.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import top.wsdx233.r2droid.core.data.parser.CLexer
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme

/**
 * Highlights C declarations (structs, enums, unions, typedefs, function prototypes)
 * using [CLexer] with adaptive high-contrast palettes for both light and dark themes.
 */
@Composable
fun rememberHighlightedCCode(code: String): AnnotatedString {
    val isDark = LocalDarkTheme.current
    return remember(code, isDark) { highlightCCode(code, isDark) }
}

fun highlightCCode(code: String, isDark: Boolean): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")
    val tokens = CLexer.tokenize(code)
    return buildAnnotatedString {
        append(code)
        tokens.forEach { token ->
            val start = token.start.coerceIn(0, code.length)
            val end = token.end.coerceIn(start, code.length)
            if (start >= end) return@forEach
            val style = when (token.syntaxHighlight) {
                "keyword" -> SpanStyle(
                    color = if (isDark) Color(0xFFC586C0) else Color(0xFF8B1FA9),
                    fontWeight = FontWeight.SemiBold
                )
                "datatype" -> SpanStyle(
                    color = if (isDark) Color(0xFF569CD6) else Color(0xFF005CC5),
                    fontWeight = FontWeight.Medium
                )
                "function_name" -> SpanStyle(
                    color = if (isDark) Color(0xFFDCDCAA) else Color(0xFF6F42C1),
                    fontWeight = FontWeight.SemiBold
                )
                "comment" -> SpanStyle(
                    color = if (isDark) Color(0xFF6A9955) else Color(0xFF22863A)
                )
                "string" -> SpanStyle(
                    color = if (isDark) Color(0xFFCE9178) else Color(0xFFA31515)
                )
                "offset" -> SpanStyle(
                    color = if (isDark) Color(0xFFB5CEA8) else Color(0xFF098658)
                )
                else -> null
            }
            if (style != null) {
                addStyle(style, start, end)
            }
        }
    }
}

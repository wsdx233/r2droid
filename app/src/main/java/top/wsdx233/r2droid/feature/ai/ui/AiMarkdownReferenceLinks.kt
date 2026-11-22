package top.wsdx233.r2droid.feature.ai.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration

private const val MaxFenceLinkNodes = 128

@Composable
internal fun rememberMarkdownReferenceLinks(onClick: ((String) -> Unit)?): MarkdownReferenceLinks? {
    if (onClick == null) return null
    val current = rememberUpdatedState(onClick)
    val color = MaterialTheme.colorScheme.tertiary
    return remember(color) {
        MarkdownReferenceLinks(color) {
            current.value(it)
        }
    }
}

internal class MarkdownReferenceLinks(
    proseColor: Color,
    private val onClick: (String) -> Unit
) {
    private companion object {
        // Bare URIs remain plain text, but a URI's components are never project links.
        val bareUri = Regex("(?i)(?<![\\p{L}\\p{N}_])(?:https?://|mailto:)[^\\s<>`\"'\\\\]+")
        val codeReferenceStyle = SpanStyle(textDecoration = TextDecoration.Underline)
        val codeReferenceLinkStyle = TextLinkStyles(style = codeReferenceStyle)
    }

    private val proseReferenceStyle = codeReferenceStyle.copy(color = proseColor)
    private val proseStyle = TextLinkStyles(style = proseReferenceStyle)
    private val listener = LinkInteractionListener { onClick((it as LinkAnnotation.Clickable).tag) }
    private val struckProseStyle = TextLinkStyles(
        style = proseReferenceStyle.copy(
            textDecoration = TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
        )
    )

    fun forProse(text: AnnotatedString): AnnotatedString {
        val ranges = referenceRanges(text)
        if (ranges.isEmpty()) return text
        val strikes = text.spanStyles.filter { it.item.textDecoration == TextDecoration.LineThrough }
        var strikeIndex = 0
        return AnnotatedString.Builder(text).apply {
            ranges.forEach { range ->
                while (strikeIndex < strikes.size && strikes[strikeIndex].end <= range.start) strikeIndex++
                val strike = strikes.getOrNull(strikeIndex)
                val style = if (strike != null && strike.start <= range.start && range.end <= strike.end) {
                    struckProseStyle
                } else proseStyle
                addReference(text.text, range, style)
            }
        }.toAnnotatedString()
    }

    class Code(val text: AnnotatedString, val denseRanges: List<TextRange>)

    fun forCode(text: AnnotatedString): Code? {
        val ranges = referenceRanges(text)
        if (ranges.isEmpty()) return null
        // Compose creates an interactive layout node per LinkAnnotation. Large dense dumps
        // instead share a single tap handler and keep syntax colors under the underlines.
        val dense = ranges.size > MaxFenceLinkNodes
        val linked = AnnotatedString.Builder(text).apply {
            ranges.forEach { range ->
                if (dense) addStyle(codeReferenceStyle, range.start, range.end)
                else addReference(text.text, range, codeReferenceLinkStyle)
            }
        }.toAnnotatedString()
        return Code(linked, if (dense) ranges else emptyList())
    }

    private fun AnnotatedString.Builder.addReference(source: String, range: TextRange, style: TextLinkStyles) {
        addLink(LinkAnnotation.Clickable(source.substring(range.start, range.end), style, listener), range.start, range.end)
    }

    private fun referenceRanges(text: AnnotatedString): List<TextRange> = buildList {
        val uriMatches = bareUri.findAll(text.text).iterator()
        var uri: MatchResult? = null
        val urls = text.getLinkAnnotations(0, text.length)
        var urlIndex = 0
        forEachChatReference(text.text) { start, end ->
            while (urlIndex < urls.size && urls[urlIndex].end <= start) urlIndex++
            if (urlIndex < urls.size && urls[urlIndex].start < end) return@forEachChatReference
            while ((uri?.range?.last ?: -1) < start && uriMatches.hasNext()) uri = uriMatches.next()
            if (uri?.range?.let { it.first < end && it.last >= start } != true) add(TextRange(start, end))
        }
    }

    fun denseCodeTap(code: Code, layout: () -> TextLayoutResult?): Modifier = Modifier.pointerInput(code) {
        awaitEachGesture {
            // Don't consume down or long presses: SelectionContainer still owns long-press
            // selection and the surrounding scroll container can cancel moving gestures.
            awaitFirstDown(requireUnconsumed = false)
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                ?: return@awaitEachGesture
            val result = layout() ?: return@awaitEachGesture
            val offset = result.getOffsetForPosition(up.position)
            // OffsetForPosition snaps to the nearest insertion point. Check the actual glyph
            // bounds so e.g. tapping after the end of a code line doesn't open its last token.
            val hit = when {
                offset < code.text.length && result.getBoundingBox(offset).contains(up.position) -> offset
                offset > 0 && result.getBoundingBox(offset - 1).contains(up.position) -> offset - 1
                else -> return@awaitEachGesture
            }
            val index = code.denseRanges.binarySearch { range ->
                when {
                    range.end <= hit -> -1
                    range.start > hit -> 1
                    else -> 0
                }
            }
            if (index >= 0) {
                val range = code.denseRanges[index]
                up.consume()
                onClick(code.text.text.substring(range.start, range.end))
            }
        }
    }
}

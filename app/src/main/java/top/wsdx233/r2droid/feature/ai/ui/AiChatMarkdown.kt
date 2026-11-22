package top.wsdx233.r2droid.feature.ai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme

private sealed interface AiMdPart {
    data class Text(val text: String) : AiMdPart
    data class Code(val text: String, val language: String?, val closingLineComplete: Boolean) : AiMdPart
}

private val openingFence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
private val headingLine = Regex("^(#{1,6})\\s+(.*)$")
private val bulletLine = Regex("^(\\s*)([-+*]|\\d+[.)])\\s+(.*)$")
private val checkboxLine = Regex("^\\[([ xX])]\\s+(.*)$")
private val tableDelimiter = Regex(":?-{3,}:?")
private val inlineTokens = Regex(
    "\\\\[\\\\`*{}\\[\\]()#+.!_>~|\\-]|" +
        "(`+)(.+?)\\1|\\[([^]]+)]\\(([^)]+)\\)|\\*\\*(.+?)\\*\\*|" +
        "(?<![\\p{L}\\p{N}_])__(.+?)__(?![\\p{L}\\p{N}_])|" +
        "~~(.+?)~~|(?<!\\*)\\*([^*]+)\\*(?!\\*)|" +
        "(?<![\\p{L}\\p{N}_])_(.+?)_(?![\\p{L}\\p{N}_])"
)
private val inlineContentTag = buildAnnotatedString { appendInlineContent("code", " ") }
    .getStringAnnotations(0, 1).single().tag

private fun parseMarkdownParts(text: String): List<AiMdPart> {
    val parts = mutableListOf<AiMdPart>()
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            parts += AiMdPart.Text(plain.toString())
            plain.clear()
        }
    }
    val lines = text.split('\n')
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val fence = openingFence.matchEntire(line)
        if (fence != null) {
            val marker = fence.groupValues[1]
            val language = fence.groupValues[2].trim().takeIf(String::isNotEmpty)
            var end = index + 1
            while (end < lines.size && !isClosingFence(lines[end], marker)) end++
            flush()
            val code = lines.subList(index + 1, end).joinToString("\n")
            parts += AiMdPart.Code(code, language, closingLineComplete = end < lines.lastIndex)
            index = (end + 1).coerceAtMost(lines.size)
            continue
        }
        plain.append(line)
        if (index < lines.lastIndex) plain.append('\n')
        index++
    }
    flush()
    return parts
}

private fun isClosingFence(line: String, marker: String): Boolean {
    var cursor = 0
    while (cursor < line.length && line[cursor] == ' ') cursor++
    if (cursor > 3) return false
    val start = cursor
    while (cursor < line.length && line[cursor] == marker[0]) cursor++
    if (cursor - start < marker.length) return false
    while (cursor < line.length) if (!line[cursor++].isWhitespace()) return false
    return true
}

private fun isHorizontalRule(line: String): Boolean {
    var cursor = 0
    while (cursor < line.length && (line[cursor] == ' ' || line[cursor] == '\t')) cursor++
    if (cursor > 3 || cursor == line.length) return false
    val marker = line[cursor]
    if (marker != '-' && marker != '*' && marker != '_') return false
    var count = 0
    while (cursor < line.length) {
        val ch = line[cursor++]
        if (ch == marker) count++ else if (!ch.isWhitespace()) return false
    }
    return count >= 3
}

@Composable
internal fun AiMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    baseFontSize: TextUnit = 16.sp,
    baseLineHeight: TextUnit = 26.sp,
    onCopy: (String) -> Unit = {},
    onOpenCodeDetail: (title: String, code: String, language: String?) -> Unit = { _, _, _ -> },
    onReferenceClick: ((String) -> Unit)? = null,
    streaming: Boolean = false
) {
    val parts = remember(text) { parseMarkdownParts(text) }
    val references = rememberMarkdownReferenceLinks(onReferenceClick)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            when (part) {
                is AiMdPart.Text -> if (part.text.isNotBlank()) {
                    PlainMarkdownBlock(
                        text = part.text,
                        baseColor = baseColor,
                        baseFontSize = baseFontSize,
                        baseLineHeight = baseLineHeight,
                        references = references,
                        // An unfinished token can still grow; scan this line only when stable.
                        deferLastLine = references != null && streaming && index == parts.lastIndex &&
                            !part.text.endsWith('\n')
                    )
                }
                is AiMdPart.Code -> MarkdownCodeBlock(
                    code = part.text,
                    language = part.language,
                    onCopy = onCopy,
                    onOpenDetail = onOpenCodeDetail,
                    references = references,
                    // Even a closing marker at EOF is provisional until its line is complete.
                    deferAnnotations = references != null && streaming && !part.closingLineComplete
                )
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(
    code: String,
    language: String?,
    onCopy: (String) -> Unit,
    onOpenDetail: (title: String, code: String, language: String?) -> Unit,
    references: MarkdownReferenceLinks?,
    deferAnnotations: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val fallbackLabel = stringResource(R.string.ai_code_label)
    val label = language ?: fallbackLabel
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    Surface(
        color = colors.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = colors.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(
                    onClick = {
                        onCopy(code)
                        copied = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    AnimatedContent(
                        targetState = copied,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(tween(240, easing = WawEmphDecelEasing), initialScale = 0.8f)) togetherWith
                                fadeOut(tween(120))
                        },
                        label = "code-copy"
                    ) { ok ->
                        Icon(
                            if (ok) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.ai_copy),
                            modifier = Modifier.size(18.dp),
                            tint = if (ok) colors.primary else colors.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { onOpenDetail(label, code, language) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.OpenInFull,
                        contentDescription = stringResource(R.string.ai_tool_fullscreen),
                        modifier = Modifier.size(18.dp),
                        tint = colors.onSurfaceVariant
                    )
                }
            }
            HighlightedCodeView(
                text = code,
                language = language,
                modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                references = if (deferAnnotations) null else references,
                deferHighlighting = deferAnnotations
            )
        }
    }
}

@Composable
internal fun HighlightedCodeView(
    text: String,
    language: String? = null,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.5.sp,
    lineHeight: TextUnit = 21.sp,
    showLineNumbers: Boolean = true,
    softWrap: Boolean = false,
    references: MarkdownReferenceLinks? = null,
    deferHighlighting: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val mono = LocalAppFont.current
    val highlighted = remember(text, language, dark, colors.onSurface, deferHighlighting) {
        if (deferHighlighting) AnnotatedString(text) else highlightCodeSnippet(text, language, dark, colors.onSurface)
    }
    val linked = if (references == null) null else remember(highlighted, references) {
        references.forCode(highlighted)
    }
    val tapLayout = if (linked?.denseRanges.isNullOrEmpty()) null else remember(linked) {
        mutableStateOf<TextLayoutResult?>(null)
    }
    val numbers = remember(text, showLineNumbers) {
        if (showLineNumbers) (1..(text.count { it == '\n' } + 1)).joinToString("\n") else ""
    }
    val rowModifier = if (softWrap) modifier else modifier.horizontalScroll(rememberScrollState())
    Row(rowModifier) {
        if (showLineNumbers) {
            DisableSelection {
                Text(
                    text = numbers,
                    modifier = Modifier.padding(end = 12.dp),
                    color = colors.outline.copy(alpha = 0.72f),
                    fontFamily = mono,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    textAlign = TextAlign.End,
                    softWrap = false
                )
            }
        }
        SelectionContainer {
            Text(
                text = linked?.text ?: highlighted,
                modifier = if (tapLayout != null) {
                    requireNotNull(references).denseCodeTap(requireNotNull(linked)) { tapLayout.value }
                } else Modifier,
                color = colors.onSurface,
                fontFamily = mono,
                fontSize = fontSize,
                lineHeight = lineHeight,
                softWrap = softWrap,
                onTextLayout = { tapLayout?.value = it }
            )
        }
    }
}

@Composable
private fun PlainMarkdownBlock(
    text: String,
    baseColor: Color,
    baseFontSize: TextUnit,
    baseLineHeight: TextUnit,
    references: MarkdownReferenceLinks?,
    deferLastLine: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val lines = remember(text) { text.lines() }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var index = 0
            while (index < lines.size) {
                val line = lines[index]
                val table = if ('|' in line) remember(lines, index) { parseMarkdownTable(lines, index) } else null
                when {
                    isHorizontalRule(line) -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = colors.outlineVariant.copy(alpha = 0.7f)
                        )
                        index++
                    }
                    table != null -> {
                        MarkdownTableView(
                            table,
                            references,
                            deferLastRow = deferLastLine && table.rows.size > 1 && table.nextLine == lines.size
                        )
                        index = table.nextLine
                    }
                    else -> {
                        PlainMarkdownLine(
                            line = line,
                            baseColor = baseColor,
                            baseFontSize = baseFontSize,
                            baseLineHeight = baseLineHeight,
                            references = if (deferLastLine && index == lines.lastIndex) null else references
                        )
                        index++
                    }
                }
            }
        }
    }
}

@Composable
private fun PlainMarkdownLine(
    line: String,
    baseColor: Color,
    baseFontSize: TextUnit,
    baseLineHeight: TextUnit,
    references: MarkdownReferenceLinks?
) {
    val colors = MaterialTheme.colorScheme
    if (line.isBlank()) {
        Spacer(Modifier.height(2.dp))
        return
    }
    val heading = remember(line) { headingLine.find(line) }
    val bullet = remember(line) { bulletLine.find(line) }
    val quote = line.trimStart().startsWith(">")
    val body = when {
        heading != null -> heading.groupValues[2]
        bullet != null -> bullet.groupValues[3]
        quote -> line.trimStart().removePrefix(">").trimStart()
        else -> line
    }
    val prefix = bullet?.let { if (it.groupValues[2].first().isDigit()) it.groupValues[2] else "•" }
    val lineModifier = if (quote) {
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, RoundedCornerShape(8.dp))
            .drawBehind {
                drawLine(
                    color = colors.outlineVariant,
                    start = Offset(0f, 6.dp.toPx()),
                    end = Offset(0f, size.height - 6.dp.toPx()),
                    strokeWidth = 3.dp.toPx()
                )
            }
            .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
    } else {
        Modifier.fillMaxWidth()
    }
    val headingSize = heading?.let { (25 - it.groupValues[1].length * 2).coerceAtLeast(16).sp }
    val fontSize = headingSize ?: baseFontSize
    val lineHeight = if (headingSize != null) (headingSize.value * 1.38f).sp else baseLineHeight
    Row(
        modifier = lineModifier.then(if (heading != null) Modifier.padding(top = 4.dp) else Modifier),
        verticalAlignment = Alignment.Top
    ) {
        if (prefix != null) {
            Text(
                text = prefix,
                modifier = Modifier.padding(start = (bullet.groupValues[1].length * 4).dp, end = 8.dp),
                fontSize = baseFontSize,
                lineHeight = baseLineHeight,
                color = baseColor.copy(alpha = 0.85f)
            )
        }
        val checkbox = remember(body) { checkboxLine.find(body) }
        if (checkbox != null) {
            Icon(
                imageVector = if (checkbox.groupValues[1].isBlank()) Icons.Rounded.CheckBoxOutlineBlank else Icons.Rounded.CheckBox,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.padding(top = 3.dp, end = 6.dp).size(18.dp)
            )
        }
        InlineMarkdownText(
            text = checkbox?.groupValues?.get(2) ?: body,
            modifier = Modifier.weight(1f),
            references = references,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = if (quote) colors.onSurfaceVariant else baseColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = 0.sp,
                fontWeight = if (heading != null) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}

private fun buildInlineAnnotated(
    text: String,
    linkStyle: SpanStyle,
    codeStyle: SpanStyle,
    preserveReferences: Boolean
): AnnotatedString = buildAnnotatedString {
    // A namespace separator followed by '_' can look like the start of Markdown
    // emphasis (e.g. sym._start_). Keep the literal token in that case.
    val literalOpeners = if (preserveReferences && "._" in text) buildSet<Int> {
        forEachChatReference(text) { start, end ->
            for (offset in start + 1 until end) {
                if (text[offset - 1] == '.' && text[offset] == '_') add(offset)
            }
        }
    } else null
    var last = 0
    inlineTokens.findAll(text).forEach { match ->
        if (literalOpeners?.contains(match.range.first) == true) return@forEach
        append(text, last, match.range.first)
        when {
            text[match.range.first] == '\\' -> append(text[match.range.first + 1])
            match.groupValues[1].isNotEmpty() -> {
                pushStringAnnotation("CODE", "")
                withStyle(codeStyle) { append(match.groupValues[2]) }
                pop()
            }
            match.groupValues[3].isNotEmpty() -> {
                val target = match.groupValues[4]
                if (target.startsWith("https://", true) || target.startsWith("http://", true) || target.startsWith("mailto:", true)) {
                    val start = length
                    append(match.groupValues[3])
                    addLink(LinkAnnotation.Url(target, TextLinkStyles(style = linkStyle)), start, length)
                } else {
                    append(match.groupValues[3])
                }
            }
            match.groupValues[5].isNotEmpty() || match.groupValues[6].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[5].ifEmpty { match.groupValues[6] }) }
            match.groupValues[7].isNotEmpty() ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(match.groupValues[7]) }
            else ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[8].ifEmpty { match.groupValues[9] }) }
        }
        last = match.range.last + 1
    }
    append(text, last, text.length)
}

private fun inlineCodeContent(
    text: AnnotatedString,
    fragments: List<IntRange>,
    wrapped: List<IntRange>,
    background: Color,
    hasChipLinks: Boolean
): AnnotatedString {
    if (fragments.isEmpty() && wrapped.isEmpty()) return text
    // Chip links belong to the chip's own BasicText, not the parent placeholder.
    val parent = if (!hasChipLinks) text else text.flatMapAnnotations { annotation ->
        val inChip = annotation.item is LinkAnnotation.Clickable && fragments.binarySearch { fragment ->
            when {
                annotation.start < fragment.first -> 1
                annotation.start > fragment.last -> -1
                else -> 0
            }
        } >= 0
        if (inChip) emptyList() else listOf(annotation)
    }
    return AnnotatedString.Builder(parent).apply {
        fragments.forEachIndexed { index, range ->
            addStringAnnotation(inlineContentTag, "code-$index", range.first, range.last + 1)
        }
        wrapped.forEach { addStyle(SpanStyle(background = background), it.first, it.last + 1) }
    }.toAnnotatedString()
}

@Composable
private fun InlineMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    references: MarkdownReferenceLinks?
) {
    val colors = MaterialTheme.colorScheme
    val mono = LocalAppFont.current
    val dark = LocalDarkTheme.current
    val density = LocalDensity.current
    val codeSize = (if (style.fontSize.isUnspecified) 16.sp else style.fontSize) * 0.86f
    val codeStyle = style.copy(
        color = colors.onSurface.copy(alpha = 0.92f),
        fontFamily = mono,
        fontSize = codeSize,
        lineHeight = codeSize * 1.15f,
        textDecoration = TextDecoration.None
    )
    val preserveReferences = references != null
    val markdown = remember(text, colors.primary, colors.onSurface, mono, codeSize, preserveReferences) {
        buildInlineAnnotated(
            text = text,
            linkStyle = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline),
            codeStyle = SpanStyle(color = colors.onSurface.copy(alpha = 0.92f), fontFamily = mono, fontSize = codeSize),
            preserveReferences = preserveReferences
        )
    }
    // Cache per rendered line/cell, independently of the growing message and callback changes.
    val annotated = if (references == null) markdown else remember(markdown, references) {
        references.forProse(markdown)
    }
    val codes = remember(annotated) { annotated.getStringAnnotations("CODE", 0, annotated.length) }
    if (codes.isEmpty()) {
        BasicText(annotated, modifier.fillMaxWidth(), style = style)
        return
    }
    val measurer = rememberTextMeasurer(cacheSize = 2)
    // Soft tinted rounded code pills matching Wawlink's chat.png
    val chipBg = if (dark) Color.White.copy(alpha = 0.11f) else colors.primaryContainer.copy(alpha = 0.55f)
    BoxWithConstraints(modifier) {
        val horizontal = with(density) { 5.dp.roundToPx() }
        val vertical = with(density) { 2.dp.roundToPx() }
        val available = if (constraints.hasBoundedWidth) (constraints.maxWidth - horizontal * 2).coerceAtLeast(1) else Constraints.Infinity
        val (fragments, wrapped, hasChipLinks) = remember(annotated, codes, references, codeStyle, available, measurer) {
            val pills = mutableListOf<IntRange>()
            val wrappedLinks = mutableListOf<IntRange>()
            var hasLinks = false
            codes.forEach { code ->
                val source = annotated.text.substring(code.start, code.end)
                val measured = measurer.measure(source, codeStyle, constraints = Constraints(maxWidth = available))
                val links = if (references == null) emptyList() else annotated.getLinkAnnotations(code.start, code.end)
                // If a chip boundary cuts a link in two, leave that run in the parent flow
                // so the whole token remains one clickable range, even when it wraps.
                val cutsLink = links.isNotEmpty() && (
                    links.any { it.start < code.start || it.end > code.end } ||
                        (0 until measured.lineCount - 1).any { line ->
                            val end = code.start + measured.getLineEnd(line)
                            links.any { it.start < end && end < it.end }
                        }
                    )
                if (cutsLink) {
                    wrappedLinks += code.start until code.end
                } else {
                    hasLinks = hasLinks || links.isNotEmpty()
                    var start = 0
                    for (line in 0 until measured.lineCount) {
                        val end = measured.getLineEnd(line)
                        if (end > start) pills += (code.start + start) until (code.start + end)
                        start = end
                    }
                }
            }
            Triple(pills, wrappedLinks, hasLinks)
        }
        val displayed = remember(annotated, fragments, wrapped, chipBg, hasChipLinks) {
            inlineCodeContent(annotated, fragments, wrapped, chipBg, hasChipLinks)
        }
        val content = remember(annotated, fragments, hasChipLinks, codeStyle, density, chipBg, measurer) {
            fragments.mapIndexed { index, range ->
                val source = annotated.text.substring(range)
                val measured = measurer.measure(source, codeStyle, softWrap = false)
                val chipText = if (hasChipLinks) annotated.subSequence(range.first, range.last + 1) else null
                "code-$index" to InlineTextContent(
                    Placeholder(
                        width = with(density) { (measured.size.width + horizontal * 2).toSp() },
                        height = with(density) { (measured.size.height + vertical * 2).toSp() },
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) { original ->
                    DisableSelection {
                        val chipModifier = Modifier
                            .fillMaxSize()
                            .background(chipBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                        if (chipText == null) {
                            BasicText(original, chipModifier, style = codeStyle, softWrap = false)
                        } else {
                            BasicText(chipText, chipModifier, style = codeStyle, softWrap = false)
                        }
                    }
                }
            }.toMap()
        }
        BasicText(displayed, Modifier.fillMaxWidth(), style = style, inlineContent = content)
    }
}

// region Markdown Tables (ported from Wawlink MarkdownTable.kt)

private enum class MdColAlign { Left, Center, Right }

private data class MdTableData(
    val rows: List<List<String>>,
    val alignments: List<MdColAlign>,
    val nextLine: Int
)

private fun parseTableCells(line: String): List<String>? {
    if ('|' !in line) return null
    val separators = mutableListOf<Int>()
    var cursor = 0
    while (cursor < line.length) {
        when (line[cursor]) {
            '\\' -> cursor += if (cursor + 1 < line.length) 2 else 1
            '`' -> {
                val start = cursor
                while (cursor < line.length && line[cursor] == '`') cursor++
                val ticks = cursor - start
                var end = cursor
                var closing = -1
                while (end < line.length) {
                    if (line[end] != '`') { end++; continue }
                    val run = end
                    while (end < line.length && line[end] == '`') end++
                    if (end - run == ticks) { closing = end; break }
                }
                if (closing >= 0) cursor = closing
            }
            '|' -> { separators += cursor; cursor++ }
            else -> cursor++
        }
    }
    if (separators.isEmpty()) return null
    val cells = mutableListOf<String>()
    var start = 0
    separators.forEach { sep ->
        cells += line.substring(start, sep).trim()
        start = sep + 1
    }
    cells += line.substring(start).trim()
    if (line.substring(0, separators.first()).isBlank()) cells.removeAt(0)
    if (line.substring(separators.last() + 1).isBlank()) cells.removeAt(cells.lastIndex)
    return cells.map { it.replace("\\|", "|") }
}

private fun parseMarkdownTable(lines: List<String>, start: Int): MdTableData? {
    if (start + 1 >= lines.size) return null
    val headings = parseTableCells(lines[start]) ?: return null
    val delimiters = parseTableCells(lines[start + 1]) ?: return null
    if (headings.isEmpty() || headings.size != delimiters.size || delimiters.any { !tableDelimiter.matches(it) }) return null
    val alignments = delimiters.map {
        when {
            it.startsWith(':') && it.endsWith(':') -> MdColAlign.Center
            it.endsWith(':') -> MdColAlign.Right
            else -> MdColAlign.Left
        }
    }
    val rows = mutableListOf(headings)
    var next = start + 2
    while (next < lines.size && lines[next].isNotBlank()) {
        val cells = parseTableCells(lines[next]) ?: break
        rows += List(headings.size) { cells.getOrElse(it) { "" } }
        next++
    }
    return MdTableData(rows, alignments, next)
}

@Composable
private fun MarkdownTableView(
    table: MdTableData,
    references: MarkdownReferenceLinks?,
    deferLastRow: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val tint = if (dark) Color.White else colors.onSurface
    val grid = tint.copy(alpha = 0.08f)
    val columns = table.alignments.size
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnWidth = if (maxWidth < 520.dp && columns >= 4) (maxWidth / 2.45f).coerceIn(112.dp, 178.dp) else maxWidth / columns
        val tableWidth = maxOf(maxWidth, columnWidth * columns)
        val scroll = rememberScrollState()
        Column(if (tableWidth > maxWidth) Modifier.horizontalScroll(scroll) else Modifier) {
            Surface(
                modifier = Modifier.width(tableWidth),
                color = Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, tint.copy(alpha = 0.12f))
            ) {
                Column {
                    table.rows.forEachIndexed { rowIndex, cells ->
                        val style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = if (rowIndex == 0) 13.sp else 13.5.sp,
                            lineHeight = 19.sp,
                            fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = colors.onSurface.copy(alpha = if (rowIndex == 0) 1f else 0.88f)
                        )
                        Layout(
                            modifier = Modifier.fillMaxWidth().drawBehind {
                                if (rowIndex == 0) drawRect(tint.copy(alpha = if (dark) 0.06f else 0.04f))
                                val stroke = 0.5.dp.toPx()
                                if (rowIndex > 0) drawLine(grid, Offset.Zero, Offset(size.width, 0f), stroke)
                                for (col in 1 until columns) {
                                    val x = size.width * col / columns
                                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), stroke)
                                }
                            },
                            content = {
                                cells.forEachIndexed { col, cell ->
                                    InlineMarkdownText(
                                        text = cell,
                                        references = if (deferLastRow && rowIndex == table.rows.lastIndex) null else references,
                                        style = style.copy(
                                            textAlign = when (table.alignments[col]) {
                                                MdColAlign.Left -> TextAlign.Left
                                                MdColAlign.Center -> TextAlign.Center
                                                MdColAlign.Right -> TextAlign.Right
                                            }
                                        )
                                    )
                                }
                            }
                        ) { measurables, constraints ->
                            val horizontal = 10.dp.roundToPx()
                            val vertical = 8.dp.roundToPx()
                            val width = constraints.maxWidth
                            val placeables = measurables.mapIndexed { col, measurable ->
                                val cellWidth = width * (col + 1) / columns - width * col / columns
                                measurable.measure(Constraints.fixedWidth((cellWidth - horizontal * 2).coerceAtLeast(0)))
                            }
                            val height = (placeables.maxOfOrNull { it.height } ?: 0) + vertical * 2
                            layout(width, height) {
                                placeables.forEachIndexed { col, placeable ->
                                    placeable.placeRelative(width * col / columns + horizontal, (height - placeable.height) / 2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region Lightweight Syntax Highlighter (C/JS/Asm/R2/JSON/Shell)

private val codeTokenRegex = Regex(
    "(//[^\\n]*|/\\*.*?\\*/|#[^\\n]*)|" +
        "(\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`)|" +
        "\\b(0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?)\\b|" +
        "\\b(const|let|var|function|return|if|else|for|while|switch|case|break|continue|try|catch|finally|throw|new|class|import|export|from|async|await|true|false|null|undefined|void|int|char|long|short|unsigned|signed|struct|typedef|static|extern|sizeof|uint8_t|uint16_t|uint32_t|uint64_t|int32_t|int64_t|bool|mov|ldr|str|add|sub|cmp|jmp|je|jne|jg|jl|jz|jnz|call|ret|bl|blr|bx|push|pop|lea|xor|and|or|nop|Interceptor|Module|Process|Memory|Java|ObjC|console|r2)\\b",
    RegexOption.DOT_MATCHES_ALL
)

private val r2CommandRegex = Regex(
    "^([a-zA-Z0-9_:.!?/\\\\~-]+)(\\s+[^@#|>~\\n]*)?(@[^|>~\\n]*)?([|>~][^\\n]*)?$"
)

internal fun highlightCodeSnippet(
    text: String,
    language: String?,
    dark: Boolean,
    defaultColor: Color
): AnnotatedString {
    if (text.isEmpty() || text.length > 32_000) return AnnotatedString(text)
    val commentColor = if (dark) Color(0xFF8B949E) else Color(0xFF6A737D)
    val stringColor = if (dark) Color(0xFFA5D6A7) else Color(0xFF116329)
    val numberColor = if (dark) Color(0xFF79C0FF) else Color(0xFF0550AE)
    val keywordColor = if (dark) Color(0xFFD2A8FF) else Color(0xFF8250DF)
    val cmdColor = if (dark) Color(0xFF8DD8A3) else Color(0xFF146C2E)
    val addrColor = if (dark) Color(0xFFFFB77D) else Color(0xFF953800)

    val lang = language?.lowercase().orEmpty()
    if (lang == "r2" || lang == "radare2" || lang == "bash" || lang == "sh") {
        val lines = text.lines()
        if (lines.size == 1) {
            val command = text.trim()
            r2CommandRegex.matchEntire(command)?.let { m ->
                val start = text.indexOf(command)
                return buildAnnotatedString {
                    append(text, 0, start)
                    withStyle(SpanStyle(color = cmdColor, fontWeight = FontWeight.SemiBold)) {
                        append(m.groupValues[1])
                    }
                    if (m.groupValues[2].isNotEmpty()) {
                        withStyle(SpanStyle(color = defaultColor)) { append(m.groupValues[2]) }
                    }
                    if (m.groupValues[3].isNotEmpty()) {
                        withStyle(SpanStyle(color = addrColor, fontWeight = FontWeight.Medium)) { append(m.groupValues[3]) }
                    }
                    if (m.groupValues[4].isNotEmpty()) {
                        withStyle(SpanStyle(color = numberColor)) { append(m.groupValues[4]) }
                    }
                    append(text, start + command.length, text.length)
                }
            }
        }
    }

    return buildAnnotatedString {
        var cursor = 0
        for (match in codeTokenRegex.findAll(text)) {
            if (match.range.first > cursor) {
                append(text, cursor, match.range.first)
            }
            val color = when {
                match.groupValues[1].isNotEmpty() -> commentColor
                match.groupValues[2].isNotEmpty() -> stringColor
                match.groupValues[3].isNotEmpty() -> numberColor
                else -> keywordColor
            }
            withStyle(SpanStyle(color = color)) { append(match.value) }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text, cursor, text.length)
    }
}

// endregion

package top.wsdx233.r2droid.feature.ai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.ai.data.ChatMessage
import top.wsdx233.r2droid.feature.ai.data.ChatRole
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme

// Wawlink M3 Expressive motion curves
internal val WawEmphEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
internal val WawEmphDecelEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
internal val WawEmphAccelEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
internal fun <T> wawSpring() = spring<T>(dampingRatio = 0.65f, stiffness = 380f)

@Immutable
internal data class AiSemanticColors(
    val success: Color,
    val successContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val onWarnContainer: Color
)

@Composable
internal fun rememberAiSemanticColors(): AiSemanticColors {
    val dark = LocalDarkTheme.current
    return remember(dark) {
        if (dark) {
            AiSemanticColors(
                success = Color(0xFF8DD8A3),
                successContainer = Color(0xFF00531E),
                warn = Color(0xFFFFB77D),
                warnContainer = Color(0xFF6D3200),
                onWarnContainer = Color(0xFFFFDCC1)
            )
        } else {
            AiSemanticColors(
                success = Color(0xFF146C2E),
                successContainer = Color(0xFFC4EED0),
                warn = Color(0xFFE65100),
                warnContainer = Color(0xFFFFDCC1),
                onWarnContainer = Color(0xFF2E1500)
            )
        }
    }
}

private const val ThinkOpen = "<think>"
private const val ThinkClose = "</think>"
private val thoughtPattern = Regex("<think>(.*?)(?:</think>|\\z)", RegexOption.DOT_MATCHES_ALL)

private data class ThoughtContent(
    val thought: String,
    val visibleContent: String,
    val thinkingStreaming: Boolean
)

private fun String.withoutPartialTag(tag: String): String {
    for (length in minOf(this.length, tag.length - 1) downTo 1) {
        if (regionMatches(this.length - length, tag, 0, length)) return dropLast(length)
    }
    return this
}

private fun extractThoughtContent(raw: String, streaming: Boolean): ThoughtContent {
    val text = if (streaming) raw.withoutPartialTag(ThinkOpen) else raw
    if (ThinkOpen !in text) return ThoughtContent("", text, false)
    val visible = StringBuilder()
    val thoughts = StringBuilder()
    var cursor = 0
    var unclosedThink = false
    for (match in thoughtPattern.findAll(text)) {
        visible.append(text, cursor, match.range.first)
        cursor = match.range.last + 1
        val thought = match.groupValues[1]
        val openBlock = !match.value.endsWith(ThinkClose)
        if (streaming && openBlock) unclosedThink = true
        val normalized = if (streaming && openBlock) thought.withoutPartialTag(ThinkClose).trim()
        else thought.trim()
        if (normalized.isNotEmpty()) {
            if (thoughts.isNotEmpty()) thoughts.append("\n\n")
            thoughts.append(normalized)
        }
    }
    visible.append(text, cursor, text.length)
    return ThoughtContent(thoughts.toString(), visible.toString(), unclosedThink)
}

// Deliberately keyed by content identity in the LazyColumn, never by a streaming delta.
@Composable
internal fun MessageBubble(
    message: ChatMessage,
    busy: Boolean,
    onCopy: (String) -> Unit,
    onDetail: (ChatTextDetail) -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onDecideTool: (callId: String, approved: Boolean, alwaysAllow: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onReferenceClick: ((String) -> Unit)? = null
) {
    val isUser = message.role == ChatRole.User
    val parsed = remember(message.role, message.content, message.reasoning, message.isStreaming) {
        if (message.role == ChatRole.Assistant) {
            val extracted = extractThoughtContent(message.content, message.isStreaming)
            val combinedThought = buildString {
                if (message.reasoning.isNotBlank()) append(message.reasoning.trim())
                if (extracted.thought.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append(extracted.thought)
                }
            }
            val isThinkingActive = extracted.thinkingStreaming ||
                (message.isStreaming && message.reasoning.isNotBlank() && extracted.visibleContent.isBlank() && message.toolCalls.isEmpty())
            ThoughtContent(combinedThought, extracted.visibleContent, isThinkingActive)
        } else {
            ThoughtContent("", message.content, false)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 6.dp, bottomStart = 24.dp),
                    modifier = Modifier.widthIn(max = maxWidth * 0.84f)
                ) {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                letterSpacing = 0.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            if (message.role != ChatRole.Assistant) {
                Text(
                    text = stringResource(if (message.role == ChatRole.System) R.string.ai_role_system else R.string.ai_role_execution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (parsed.thought.isNotBlank()) {
                WawThinkingNode(
                    nodeKey = "think-${message.id}",
                    thought = parsed.thought,
                    streaming = parsed.thinkingStreaming,
                    onCopy = onCopy,
                    onDetail = onDetail
                )
            } else if (message.isStreaming && parsed.visibleContent.isBlank() && message.toolCalls.isEmpty()) {
                WawThinkingPlaceholder(nodeKey = "thinking-wait-${message.id}")
            }

            if (parsed.visibleContent.isNotBlank()) {
                AiMarkdown(
                    text = parsed.visibleContent,
                    streaming = message.isStreaming,
                    onReferenceClick = onReferenceClick.takeIf { message.role == ChatRole.Assistant },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 4.dp,
                            end = 4.dp,
                            top = if (parsed.thought.isNotBlank()) 14.dp else 0.dp
                        ),
                    onCopy = onCopy,
                    onOpenCodeDetail = { title, code, language ->
                        onDetail(ChatTextDetail(title = title, text = code, code = true, language = language))
                    }
                )
            }

            if (message.isStreaming && parsed.visibleContent.isNotBlank()) {
                StreamingCaret()
            }

            if (message.toolCalls.isNotEmpty()) {
                Box(Modifier.padding(top = if (parsed.visibleContent.isNotBlank() || parsed.thought.isNotBlank()) 14.dp else 0.dp)) {
                    ToolRail(
                        calls = message.toolCalls,
                        canDecide = busy,
                        onCopy = onCopy,
                        onDetail = onDetail,
                        onDecide = onDecideTool
                    )
                }
            }

            message.actionResults.forEachIndexed { index, result ->
                key(index) { LegacyActionCard(result, onCopy, onDetail) }
            }
        }

        if (!message.isStreaming && (!busy || message.toolCalls.all { it.status.isTerminal })) {
            val copyText = parsed.visibleContent
            val selectLabel = stringResource(R.string.ai_select_copy)
            MessageActions(
                alignEnd = isUser,
                showMenu = message.toolCalls.isEmpty() && message.actionResults.isEmpty(),
                onCopy = copyText.takeIf(String::isNotBlank)?.let { { onCopy(it) } },
                onSelect = copyText.takeIf(String::isNotBlank)?.let { { onDetail(ChatTextDetail(selectLabel, it)) } },
                onEdit = onEdit.takeIf { isUser && !busy },
                onRegenerate = onRegenerate.takeIf { message.role == ChatRole.Assistant && !busy },
                onDelete = onDelete.takeUnless { busy }
            )
        }
    }
}

@Composable
private fun StreamingCaret() {
    val transition = rememberInfiniteTransition(label = "chat caret")
    val opacity by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "opacity"
    )
    Box(
        Modifier
            .padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
            .size(8.dp, 18.dp)
            .graphicsLayer { alpha = opacity }
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun WawThinkingPlaceholder(nodeKey: String) {
    val title = stringResource(R.string.ai_chat_thinking_active)
    WawNodeFrame(
        nodeKey = nodeKey,
        icon = Icons.Rounded.Psychology,
        leadStyle = WawLeadStyle.SOFT,
        open = false,
        title = title,
        onToggle = null,
        status = {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                strokeWidth = 2.dp
            )
        },
        titleContent = { ShimmerTitle(title) }
    ) {}
}

@Composable
private fun WawThinkingNode(
    nodeKey: String,
    thought: String,
    streaming: Boolean,
    onCopy: (String) -> Unit,
    onDetail: (ChatTextDetail) -> Unit
) {
    var expanded by rememberSaveable(nodeKey) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val semantic = rememberAiSemanticColors()
    val activeTitle = stringResource(R.string.ai_chat_thinking_active)
    val doneTitle = stringResource(R.string.ai_chat_thought_title)
    val openDetail = {
        onDetail(ChatTextDetail(title = doneTitle, text = thought, markdown = true))
    }

    WawNodeFrame(
        nodeKey = nodeKey,
        icon = Icons.Rounded.Psychology,
        leadStyle = WawLeadStyle.SOFT,
        open = expanded,
        title = if (streaming) activeTitle else doneTitle,
        onToggle = { expanded = !expanded },
        onIconClick = openDetail,
        status = {
            if (streaming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    color = colors.onSurfaceVariant,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = semantic.success
                )
            }
        },
        rail = {
            WawStagedRailButton(
                open = expanded,
                delayMs = 0,
                icon = Icons.Rounded.OpenInFull,
                label = stringResource(R.string.ai_tool_fullscreen),
                onClick = openDetail
            )
            WawRailCopyButton(
                open = expanded,
                delayMs = 60,
                onCopy = { onCopy(thought) }
            )
        },
        titleContent = {
            if (streaming) {
                ShimmerTitle(activeTitle)
            } else {
                Text(
                    text = doneTitle,
                    fontSize = 16.5.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
            }
        }
    ) {
        ChatExpand(!expanded) {
            ThoughtPreview(text = thought, tail = streaming)
        }
        ChatExpand(expanded) {
            AiMarkdown(
                text = thought,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                baseColor = colors.onSurfaceVariant,
                baseFontSize = 14.5.sp,
                baseLineHeight = 23.sp,
                onCopy = onCopy,
                onOpenCodeDetail = { title, code, language ->
                    onDetail(ChatTextDetail(title = title, text = code, code = true, language = language))
                }
            )
        }
    }
}

@Composable
private fun ShimmerTitle(text: String) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "shift"
    )
    var width by remember { mutableFloatStateOf(160f) }
    val highlight = colors.primary
    Text(
        text = text,
        fontSize = 16.5.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        style = TextStyle(
            brush = Brush.linearGradient(
                0f to colors.onSurface,
                0.35f to colors.onSurface,
                0.5f to highlight,
                0.65f to colors.onSurface,
                1f to colors.onSurface,
                start = Offset(shift * width * 3f, 0f),
                end = Offset((shift + 1f) * width * 3f, 0f)
            )
        ),
        onTextLayout = { width = it.size.width.toFloat().coerceAtLeast(1f) }
    )
}

/**
 * Ported from Wawlink's ThoughtPreview.kt:
 * When streaming (`tail = true`), shows a live single-line right-aligned marquee with a 48dp left-edge alpha fade.
 * When finished (`tail = false`), shows a crisp single-line head preview with ellipsis.
 */
@Composable
private fun ThoughtPreview(text: String, tail: Boolean) {
    val style = LocalTextStyle.current.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.5.sp,
        lineHeight = 20.sp
    )
    val measurer = rememberTextMeasurer(cacheSize = 0)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        val width = constraints.maxWidth
        val window = remember(text, tail, width, style, measurer) {
            thoughtPreviewWindow(text, tail, width) { candidate ->
                measurer.measure(
                    candidate,
                    style,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = width.coerceAtMost(Constraints.Infinity / 3) * 3)
                )
            }
        }
        val semantics = Modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }
        if (tail) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        clip = true
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        if (window.overflow) {
                            drawRect(
                                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), endX = 48.dp.toPx()),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            ) {
                Text(
                    text = window.text,
                    modifier = semantics
                        .fillMaxWidth()
                        .wrapContentWidth(if (window.overflow) Alignment.End else Alignment.Start, unbounded = true),
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        } else {
            Text(
                text = window.text,
                modifier = semantics.fillMaxWidth(),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class ThoughtPreviewWindow(val text: String, val overflow: Boolean)

private fun thoughtPreviewWindow(
    source: String,
    tail: Boolean,
    width: Int,
    measure: (String) -> TextLayoutResult
): ThoughtPreviewWindow {
    if (source.isEmpty() || width <= 0) return ThoughtPreviewWindow("", false)
    var count = minOf(128, source.length)
    while (true) {
        var start = if (tail) source.length - count else 0
        var end = if (tail) source.length else count
        if (start > 0 && source[start].isLowSurrogate() && source[start - 1].isHighSurrogate()) start--
        if (end < source.length && source[end - 1].isHighSurrogate() && source[end].isLowSurrogate()) end++
        val candidate = source.substring(start, end).replace('\n', ' ')
        val layout = measure(candidate)
        val lineBreak = layout.multiParagraph.didExceedMaxLines
        val overflow = layout.didOverflowWidth || layout.size.width > width || lineBreak
        if (count == source.length || layout.size.width >= width * 2f || lineBreak) {
            return ThoughtPreviewWindow(candidate, overflow)
        }
        count = if (count >= source.length - count) source.length else count * 2
    }
}

/**
 * Ported from Wawlink's FeedHistoryActions (AgentChatNavigation.kt):
 * Direct 1-tap copy with animated check feedback + More button opening a spring-pop WawPopupMenu.
 */
@Composable
private fun MessageActions(
    alignEnd: Boolean,
    showMenu: Boolean,
    onCopy: (() -> Unit)?,
    onSelect: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val hasMenu = showMenu && (onSelect != null || onEdit != null || onRegenerate != null || onDelete != null)
    if (onCopy == null && !hasMenu) return
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onCopy != null) {
            IconButton(
                onClick = {
                    onCopy()
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
                    label = "msg-copy"
                ) { ok ->
                    Icon(
                        imageVector = if (ok) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(if (ok) R.string.ai_copied else R.string.ai_copy),
                        modifier = Modifier.size(18.dp),
                        tint = if (ok) colors.primary else colors.onSurfaceVariant
                    )
                }
            }
        }
        if (hasMenu) {
            Box {
                IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = stringResource(R.string.ai_chat_message_actions),
                        modifier = Modifier.size(19.dp),
                        tint = colors.onSurfaceVariant
                    )
                }
                WawPopupMenu(expanded = open, onDismissRequest = { open = false }) {
                    if (onSelect != null) {
                        WawPopupMenuItem(
                            label = stringResource(R.string.ai_select_copy),
                            icon = Icons.Rounded.SelectAll,
                            onClick = { open = false; onSelect() }
                        )
                    }
                    if (onEdit != null) {
                        WawPopupMenuItem(
                            label = stringResource(R.string.ai_edit_resend),
                            icon = Icons.Rounded.Edit,
                            onClick = { open = false; onEdit() }
                        )
                    }
                    if (onRegenerate != null) {
                        WawPopupMenuItem(
                            label = stringResource(R.string.ai_regenerate),
                            icon = Icons.Rounded.Refresh,
                            onClick = { open = false; onRegenerate() }
                        )
                    }
                    if (onDelete != null) {
                        WawPopupMenuItem(
                            label = stringResource(R.string.ai_delete),
                            icon = Icons.Rounded.Delete,
                            dangerous = true,
                            onClick = { open = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatExpand(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(420, easing = WawEmphEasing), expandFrom = Alignment.Top) +
            fadeIn(tween(320, delayMillis = 60, easing = WawEmphDecelEasing)),
        exit = shrinkVertically(tween(360, easing = WawEmphEasing), shrinkTowards = Alignment.Top) +
            fadeOut(tween(180, easing = WawEmphAccelEasing)),
        content = { content() }
    )
}

internal data class ChatTextDetail(
    val title: String,
    val text: String,
    val code: Boolean = false,
    val markdown: Boolean = false,
    val language: String? = null
)

/**
 * Full-screen detail viewer ported from Wawlink's CodeDetailScreen & ThinkingDetailScreen.
 * Uses Dialog(usePlatformDefaultWidth = false) so it renders full-window while preserving Dialog semantics.
 */
@Composable
internal fun SelectCopyDialog(
    detail: ChatTextDetail,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var wordWrap by rememberSaveable(detail.title, detail.code) { mutableStateOf(true) }
    var copied by remember(detail.text) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = colors.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.ai_chat_close),
                            tint = colors.onSurfaceVariant
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (detail.code && !detail.language.isNullOrBlank()) {
                            Text(
                                text = detail.language,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                    if (detail.code) {
                        IconButton(onClick = { wordWrap = !wordWrap }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.WrapText,
                                contentDescription = stringResource(R.string.ai_code_word_wrap),
                                tint = if (wordWrap) colors.primary else colors.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        enabled = detail.text.isNotEmpty(),
                        onClick = {
                            onCopyAll()
                            copied = true
                        }
                    ) {
                        AnimatedContent(
                            targetState = copied,
                            transitionSpec = {
                                (fadeIn(tween(180)) + scaleIn(tween(240, easing = WawEmphDecelEasing), initialScale = 0.8f)) togetherWith
                                    fadeOut(tween(120))
                            },
                            label = "detail-copy"
                        ) { ok ->
                            Icon(
                                imageVector = if (ok) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.ai_copy_all),
                                tint = if (ok) colors.primary else colors.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    when {
                        detail.text.isEmpty() -> Text(
                            text = stringResource(R.string.ai_tool_empty),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        detail.code -> HighlightedCodeView(
                            text = detail.text,
                            language = detail.language,
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 13.5.sp,
                            lineHeight = 21.sp,
                            showLineNumbers = true,
                            softWrap = wordWrap
                        )
                        detail.markdown -> AiMarkdown(
                            text = detail.text,
                            modifier = Modifier.fillMaxWidth(),
                            onCopy = { onCopyAll() }
                        )
                        else -> SelectionContainer {
                            Text(
                                text = detail.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 25.sp
                                ),
                                color = colors.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ported from Wawlink's ManagementMenu: spring-scale floating menu with rounded 24dp surface.
 */
@Composable
internal fun WawPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    val motion = rememberTransition(visible, label = "waw-popup-menu")
    val scale by motion.animateFloat(
        transitionSpec = {
            if (targetState) tween(400, easing = WawEmphDecelEasing) else tween(180, easing = WawEmphAccelEasing)
        },
        label = "scale"
    ) { if (it) 1f else 0.72f }
    val opacity by motion.animateFloat(
        transitionSpec = { if (targetState) tween(220) else tween(140) },
        label = "opacity"
    ) { if (it) 1f else 0f }
    val density = LocalDensity.current
    var pivot by remember { mutableStateOf(TransformOrigin(0.5f, 0f)) }
    val position = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val shadow = with(density) { 24.dp.roundToPx() }
                val inset = with(density) { 12.dp.roundToPx() }
                val gap = with(density) { 4.dp.roundToPx() }
                val width = popupContentSize.width - shadow * 2
                val height = popupContentSize.height - shadow * 2
                val x = (anchorBounds.right - width)
                    .coerceIn(inset, (windowSize.width - width - inset).coerceAtLeast(inset)) - shadow
                val below = anchorBounds.bottom + gap + height <= windowSize.height - inset
                val y = (if (below) anchorBounds.bottom + gap else (anchorBounds.top - height - gap).coerceAtLeast(inset)) - shadow
                pivot = TransformOrigin(
                    ((anchorBounds.left + anchorBounds.width / 2f - x) / popupContentSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    if (below) 0f else 1f
                )
                return IntOffset(x, y)
            }
        }
    }
    if (visible.currentState || visible.targetState) {
        Popup(
            popupPositionProvider = position,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true, clippingEnabled = false)
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = opacity
                        transformOrigin = pivot
                        clip = false
                    }
                    .padding(24.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 10.dp
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 196.dp, max = 280.dp)
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp),
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
internal fun WawPopupMenuItem(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    dangerous: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val fg = when {
        dangerous -> colors.error
        selected -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val bg = if (selected) colors.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.primary)
            }
        }
    }
}

/**
 * Ported from Wawlink's AgentSheet (AgentTheme.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WawSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 14.dp, bottom = 6.dp)
                    .size(32.dp, 4.dp)
                    .background(colors.outline.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            actions()
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ai_chat_close), tint = colors.onSurfaceVariant)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp).padding(contentPadding),
            content = content
        )
    }
}

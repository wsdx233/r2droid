package top.wsdx233.r2droid.feature.ai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.ai.data.ActionResult
import top.wsdx233.r2droid.feature.ai.data.ActionType
import top.wsdx233.r2droid.feature.ai.data.AiToolCall
import top.wsdx233.r2droid.feature.ai.data.ToolCallStatus
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme

internal enum class WawLeadStyle { DARK, SOFT, TONE, WARN }

/**
 * Wawlink's signature cardless 2-column editorial node:
 * - Left 44dp rail: squircle (13dp) <-> circle (22dp) morphing lead icon + staged vertical action buttons when expanded.
 * - Right body: bold title + compact inline status + rotating chevron, collapsed single-line preview, expanded detail.
 */
@Composable
internal fun WawNodeFrame(
    nodeKey: String,
    icon: ImageVector,
    leadStyle: WawLeadStyle,
    open: Boolean,
    title: String,
    onToggle: (() -> Unit)?,
    statusLabel: String? = null,
    status: @Composable RowScope.() -> Unit = {},
    rail: @Composable ColumnScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    onIconClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantic = rememberAiSemanticColors()
    val pauseFollow = LocalPauseChatFollow.current
    val radius by animateDpAsState(
        targetValue = if (open) 22.dp else 13.dp,
        animationSpec = tween(450, easing = WawEmphEasing),
        label = "lead-radius"
    )
    val (bg, fg) = when (leadStyle) {
        WawLeadStyle.DARK -> colors.onSurface to colors.surface
        WawLeadStyle.SOFT -> colors.surfaceContainerHigh to colors.onSurface
        WawLeadStyle.TONE -> colors.primaryContainer to colors.onPrimaryContainer
        WawLeadStyle.WARN -> semantic.warnContainer to semantic.onWarnContainer
    }
    val toggleAction: (() -> Unit)? = onToggle?.let { action ->
        {
            pauseFollow()
            action()
        }
    }
    val iconAction: (() -> Unit)? = onIconClick?.let { action ->
        {
            pauseFollow()
            action()
        }
    } ?: toggleAction
    val toggleDesc = stringResource(if (open) R.string.ai_tool_collapse else R.string.ai_tool_expand)
    val iconDesc = if (onIconClick != null) stringResource(R.string.ai_tool_open_detail, title) else toggleDesc

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(radius))
                    .background(bg)
                    .then(
                        if (iconAction != null) {
                            Modifier.clickable(
                                interactionSource = remember(nodeKey) { MutableInteractionSource() },
                                indication = ripple(color = fg),
                                role = Role.Button,
                                onClickLabel = iconDesc,
                                onClick = iconAction
                            )
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (onIconClick != null) iconDesc else null,
                    tint = fg,
                    modifier = Modifier.size(24.dp)
                )
            }
            rail()
        }
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (statusLabel != null) Modifier.semantics { stateDescription = statusLabel } else Modifier)
                    .then(
                        if (toggleAction != null) {
                            Modifier.clickable(role = Role.Button, onClickLabel = toggleDesc, onClick = toggleAction)
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    if (titleContent != null) {
                        titleContent()
                    } else {
                        Text(
                            text = title,
                            fontSize = 16.5.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                status()
                if (onToggle != null) {
                    val rotation by animateFloatAsState(
                        targetValue = if (open) 180f else 0f,
                        animationSpec = tween(500, easing = WawEmphEasing),
                        label = "node-chevron"
                    )
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation },
                        tint = colors.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
internal fun WawStagedRailButton(
    open: Boolean,
    delayMs: Int,
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            if (delayMs > 0) delay(delayMs.toLong())
            shown = true
        } else {
            shown = false
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(320, easing = WawEmphDecelEasing)) +
            slideInVertically(wawSpring()) { -8 } +
            scaleIn(wawSpring(), initialScale = 0.9f),
        exit = fadeOut(tween(180, easing = WawEmphAccelEasing)) +
            shrinkVertically(tween(260, easing = WawEmphEasing))
    ) {
        Column(
            modifier = Modifier
                .padding(top = 6.dp)
                .width(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = tint)
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun WawRailCopyButton(
    open: Boolean,
    delayMs: Int,
    onCopy: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    WawStagedRailButton(
        open = open,
        delayMs = delayMs,
        icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
        label = stringResource(if (copied) R.string.ai_copied else R.string.ai_copy),
        tint = if (copied) colors.primary else colors.onSurfaceVariant,
        onClick = {
            onCopy()
            copied = true
        }
    )
}

@Composable
internal fun WawNodeStatus(
    status: ToolCallStatus,
    startedAtMillis: Long? = null,
    durationMillis: Long? = null
) {
    val colors = MaterialTheme.colorScheme
    val semantic = rememberAiSemanticColors()
    val mono = LocalAppFont.current
    var nowMillis by remember(status, startedAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status, startedAtMillis) {
        if (status == ToolCallStatus.Running && startedAtMillis != null) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(250)
            }
        }
    }
    val elapsedText = remember(status, startedAtMillis, durationMillis, nowMillis) {
        val ms = when {
            status == ToolCallStatus.Running && startedAtMillis != null -> (nowMillis - startedAtMillis).coerceAtLeast(0L)
            durationMillis != null && durationMillis >= 1000L -> durationMillis
            else -> null
        }
        ms?.let { formatElapsedSeconds((it / 1000L).toInt()) }.orEmpty()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (status) {
            ToolCallStatus.Running -> CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = colors.onSurfaceVariant,
                strokeWidth = 2.dp
            )
            ToolCallStatus.Succeeded -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = semantic.success
            )
            ToolCallStatus.Failed -> Icon(
                Icons.Rounded.Error,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.error
            )
            ToolCallStatus.Denied -> Icon(
                Icons.Rounded.Block,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.error
            )
            ToolCallStatus.Interrupted -> Icon(
                Icons.Rounded.StopCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
            ToolCallStatus.AwaitingApproval -> Icon(
                Icons.Rounded.GppMaybe,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = semantic.warn
            )
            ToolCallStatus.Queued -> Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
            ToolCallStatus.Manual -> Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = colors.onSurfaceVariant
            )
        }
        if (elapsedText.isNotEmpty()) {
            Text(
                text = elapsedText,
                fontFamily = mono,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

private fun formatElapsedSeconds(seconds: Int): String = when {
    seconds < 1 -> ""
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
internal fun ToolRail(
    calls: List<AiToolCall>,
    canDecide: Boolean,
    onCopy: (String) -> Unit,
    onDetail: (ChatTextDetail) -> Unit,
    onDecide: (callId: String, approved: Boolean, alwaysAllow: Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        calls.forEach { call ->
            key(call.id) {
                ToolCallNode(
                    call = call,
                    canDecide = canDecide,
                    onCopy = onCopy,
                    onDetail = onDetail,
                    onDecide = onDecide
                )
            }
        }
    }
}

@Composable
private fun ToolCallNode(
    call: AiToolCall,
    canDecide: Boolean,
    onCopy: (String) -> Unit,
    onDetail: (ChatTextDetail) -> Unit,
    onDecide: (callId: String, approved: Boolean, alwaysAllow: Boolean) -> Unit
) {
    val payload = remember(call.name, call.arguments) { extractToolPayload(call.name, call.arguments) }
    val awaitingApproval = canDecide && call.status == ToolCallStatus.AwaitingApproval

    if (awaitingApproval) {
        WawApprovalCard(
            call = call,
            commandPreview = payload.primaryText.ifBlank { call.arguments },
            language = payload.language,
            onDecide = { approved, alwaysAllow -> onDecide(call.id, approved, alwaysAllow) }
        )
        return
    }

    var expanded by rememberSaveable(call.id) { mutableStateOf(false) }
    val toolTitle = toolDisplayTitle(call.name)
    val icon = toolDisplayIcon(call.name)
    val leadStyle = toolLeadStyle(call.name)
    val statusText = toolStatusText(call.status)

    val fullDetailText = remember(payload.primaryText, call.output, call.arguments) {
        buildString {
            val primary = payload.primaryText.ifBlank { call.arguments.takeUnless { it.trim() == "{}" }.orEmpty() }
            if (primary.isNotBlank()) append(primary)
            if (call.output.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(call.output)
            }
        }
    }
    val copyPayload = remember(call.status, payload.primaryText, fullDetailText) {
        if (call.status == ToolCallStatus.Manual && payload.primaryText.isNotEmpty()) payload.primaryText
        else fullDetailText.ifEmpty { call.arguments }
    }
    val openFullDetail = {
        onDetail(
            ChatTextDetail(
                title = toolTitle,
                text = fullDetailText.ifEmpty { call.arguments },
                code = true,
                language = payload.language
            )
        )
    }

    WawNodeFrame(
        nodeKey = call.id,
        icon = icon,
        leadStyle = leadStyle,
        open = expanded,
        title = toolTitle,
        onToggle = { expanded = !expanded },
        statusLabel = statusText,
        onIconClick = openFullDetail,
        status = { WawNodeStatus(call.status, call.startedAtMillis, call.durationMillis) },
        rail = {
            WawStagedRailButton(
                open = expanded,
                delayMs = 0,
                icon = Icons.Rounded.OpenInFull,
                label = stringResource(R.string.ai_tool_detail),
                onClick = openFullDetail
            )
            WawRailCopyButton(
                open = expanded,
                delayMs = 60,
                onCopy = { onCopy(copyPayload) }
            )
        }
    ) {
        ChatExpand(!expanded) {
            ToolCollapsedPreview(
                preview = payload.previewLine,
                language = payload.language,
                status = call.status,
                statusText = statusText,
                errorHint = call.output.takeIf { call.status == ToolCallStatus.Failed }
            )
        }
        ChatExpand(expanded) {
            ToolExpandedBody(
                commandOrCode = payload.primaryText,
                language = payload.language,
                output = call.output,
                status = call.status,
                statusText = statusText,
                onOpenDetail = openFullDetail
            )
        }
    }
}

@Composable
private fun WawApprovalCard(
    call: AiToolCall,
    commandPreview: String,
    language: String?,
    onDecide: (approved: Boolean, alwaysAllow: Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val mono = LocalAppFont.current
    val highlighted = remember(commandPreview, language, dark, colors.onSurface) {
        highlightCodeSnippet(commandPreview, language ?: "r2", dark, colors.onSurface)
    }

    Surface(
        color = colors.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.padding(18.dp)) {
            WawNodeFrame(
                nodeKey = "approval-${call.id}",
                icon = Icons.Rounded.GppMaybe,
                leadStyle = WawLeadStyle.WARN,
                open = false,
                title = stringResource(R.string.ai_tool_approval_title),
                onToggle = null
            ) {
                Column(Modifier.padding(top = 4.dp)) {
                    Text(
                        text = stringResource(R.string.ai_tool_approval_hint),
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp,
                        color = colors.onSurfaceVariant
                    )
                    Surface(
                        color = colors.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = highlighted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                fontFamily = mono,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onDecide(false, false) },
                            modifier = Modifier.height(46.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_approval_deny))
                        }
                        Button(
                            onClick = { onDecide(true, false) },
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_approval_allow))
                        }
                    }
                    TextButton(
                        onClick = { onDecide(true, true) },
                        modifier = Modifier.padding(top = 2.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_tool_approval_always), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCollapsedPreview(
    preview: String,
    language: String?,
    status: ToolCallStatus,
    statusText: String,
    errorHint: String?
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val mono = LocalAppFont.current
    Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (preview.isNotEmpty()) {
            val highlighted = remember(preview, language, dark, colors.onSurfaceVariant) {
                highlightCodeSnippet(preview, language, dark, colors.onSurfaceVariant)
            }
            Text(
                text = highlighted,
                fontFamily = mono,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            status == ToolCallStatus.Failed && !errorHint.isNullOrBlank() -> Text(
                text = errorHint.lineSequence().firstOrNull().orEmpty(),
                fontSize = 12.5.sp,
                color = colors.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            status == ToolCallStatus.Manual || status == ToolCallStatus.Denied || status == ToolCallStatus.Interrupted -> Text(
                text = statusText,
                fontSize = 12.5.sp,
                color = if (status == ToolCallStatus.Denied) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val MAX_INLINE_CODE_LINES = 14
private const val MAX_INLINE_OUTPUT_LINES = 20
private const val MAX_INLINE_OUTPUT_CHARS = 1800

@Composable
private fun ToolExpandedBody(
    commandOrCode: String,
    language: String?,
    output: String,
    status: ToolCallStatus,
    statusText: String,
    onOpenDetail: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val mono = LocalAppFont.current
    val isError = status == ToolCallStatus.Failed || status == ToolCallStatus.Denied
    val barColor = if (isError) colors.error else colors.outlineVariant

    SelectionContainer {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (commandOrCode.isNotEmpty()) {
                val codeLines = remember(commandOrCode) { commandOrCode.lines() }
                val truncatedCode = remember(codeLines) {
                    if (codeLines.size <= MAX_INLINE_CODE_LINES) commandOrCode
                    else codeLines.take(MAX_INLINE_CODE_LINES).joinToString("\n") + "\n…"
                }
                val highlighted = remember(truncatedCode, language, dark, colors.onSurface) {
                    highlightCodeSnippet(truncatedCode, language, dark, colors.onSurface)
                }
                Text(
                    text = highlighted,
                    fontFamily = mono,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (codeLines.size == 1) FontWeight.SemiBold else FontWeight.Normal,
                    color = colors.onSurface
                )
            }

            if (status == ToolCallStatus.Manual) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 12.5.sp,
                    color = colors.onSurfaceVariant
                )
            }

            if (output.isNotEmpty()) {
                val outputLines = remember(output) { output.lines() }
                val needsMore = outputLines.size > MAX_INLINE_OUTPUT_LINES || output.length > MAX_INLINE_OUTPUT_CHARS
                val previewOutput = remember(output, outputLines, needsMore) {
                    if (!needsMore) output
                    else outputLines.take(MAX_INLINE_OUTPUT_LINES).joinToString("\n").take(MAX_INLINE_OUTPUT_CHARS)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .drawWithContent {
                            drawContent()
                            drawLine(
                                color = barColor,
                                start = Offset.Zero,
                                end = Offset(0f, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = previewOutput,
                        fontFamily = mono,
                        fontSize = 13.sp,
                        lineHeight = 19.5.sp,
                        color = if (isError) colors.error else colors.onSurfaceVariant
                    )
                    if (needsMore) {
                        Text(
                            text = stringResource(R.string.ai_tool_more_lines, outputLines.size),
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenDetail)
                                .padding(vertical = 2.dp),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary
                        )
                    }
                }
            } else if (status == ToolCallStatus.Succeeded) {
                Text(
                    text = stringResource(R.string.ai_tool_no_output),
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 12.5.sp,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun LegacyActionCard(
    result: ActionResult,
    onCopy: (String) -> Unit,
    onDetail: (ChatTextDetail) -> Unit
) {
    val call = remember(result) {
        val name = when (result.type) {
            ActionType.R2Command -> "r2_command"
            ActionType.JavaScript -> "run_js"
            ActionType.FridaScript -> "run_frida"
        }
        val key = when (result.type) {
            ActionType.R2Command -> "command"
            ActionType.JavaScript -> "code"
            ActionType.FridaScript -> "script"
        }
        AiToolCall(
            id = "legacy-${result.hashCode()}",
            name = name,
            arguments = JSONObject().put(key, result.input).toString(),
            status = if (result.success) ToolCallStatus.Succeeded else ToolCallStatus.Failed,
            output = result.output
        )
    }
    Box(Modifier.padding(top = 10.dp)) {
        ToolCallNode(
            call = call,
            canDecide = false,
            onCopy = onCopy,
            onDetail = onDetail,
            onDecide = { _, _, _ -> }
        )
    }
}

private data class ExtractedToolPayload(
    val primaryText: String,
    val previewLine: String,
    val language: String?
)

private fun extractToolPayload(name: String, arguments: String): ExtractedToolPayload {
    val json = runCatching { JSONObject(arguments) }.getOrNull()
    val primary = when (name) {
        "r2_command" -> json?.opt("command") as? String
        "run_js", "suggest_js" -> (json?.opt("code") as? String) ?: (json?.opt("source") as? String)
        "run_frida", "suggest_frida" -> (json?.opt("script") as? String) ?: (json?.opt("source") as? String)
        "get_binary_info" -> "ij"
        "list_sections" -> {
            val offset = json?.optInt("offset", 0) ?: 0
            val limit = json?.optInt("limit", 20) ?: 20
            "iSj (offset=$offset, limit=$limit)"
        }
        else -> null
    } ?: arguments.takeUnless { it.trim() == "{}" }.orEmpty()

    val previewLine = primary.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?.take(180)
        .orEmpty()

    val language = when (name) {
        "r2_command", "get_binary_info", "list_sections" -> "r2"
        "run_js", "suggest_js", "run_frida", "suggest_frida" -> "javascript"
        else -> null
    }
    return ExtractedToolPayload(primary, previewLine, language)
}

@Composable
private fun toolDisplayTitle(name: String): String = when (name) {
    "r2_command" -> stringResource(R.string.ai_tool_r2_command)
    "run_js" -> stringResource(R.string.ai_tool_run_js)
    "run_frida" -> stringResource(R.string.ai_tool_run_frida)
    "suggest_js" -> stringResource(R.string.ai_tool_js)
    "suggest_frida" -> stringResource(R.string.ai_tool_frida)
    "get_binary_info" -> stringResource(R.string.ai_tool_binary_info)
    "list_sections" -> stringResource(R.string.ai_tool_sections)
    else -> name.ifBlank { stringResource(R.string.ai_tool_unknown) }
}

private fun toolDisplayIcon(name: String): ImageVector = when (name) {
    "r2_command" -> Icons.Rounded.Terminal
    "run_js", "suggest_js" -> Icons.Rounded.Code
    "run_frida", "suggest_frida" -> Icons.Rounded.BugReport
    "list_sections" -> Icons.AutoMirrored.Rounded.ViewList
    else -> Icons.Rounded.Analytics
}

private fun toolLeadStyle(name: String): WawLeadStyle = when (name) {
    "run_js", "suggest_js", "run_frida", "suggest_frida" -> WawLeadStyle.TONE
    else -> WawLeadStyle.DARK
}

@Composable
private fun toolStatusText(status: ToolCallStatus): String = stringResource(
    when (status) {
        ToolCallStatus.Queued -> R.string.ai_tool_status_queued
        ToolCallStatus.AwaitingApproval -> R.string.ai_tool_status_approval
        ToolCallStatus.Running -> R.string.ai_tool_status_running
        ToolCallStatus.Succeeded -> R.string.ai_tool_status_succeeded
        ToolCallStatus.Failed -> R.string.ai_tool_status_failed
        ToolCallStatus.Denied -> R.string.ai_tool_status_denied
        ToolCallStatus.Interrupted -> R.string.ai_tool_status_interrupted
        ToolCallStatus.Manual -> R.string.ai_tool_status_manual
    }
)

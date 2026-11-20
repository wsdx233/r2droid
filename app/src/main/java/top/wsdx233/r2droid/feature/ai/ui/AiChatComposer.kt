package top.wsdx233.r2droid.feature.ai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.ai.data.AiExecutionMode
import top.wsdx233.r2droid.feature.ai.data.AiProviderConfig
import top.wsdx233.r2droid.feature.ai.data.ThinkingLevel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    statusText: String?,
    config: AiProviderConfig,
    onModelChange: (String, String) -> Unit,
    thinkingLevel: ThinkingLevel,
    onThinkingLevelChange: (ThinkingLevel) -> Unit,
    executionMode: AiExecutionMode,
    onToggleMode: (() -> Unit)?,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var openMenu by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to colors.surface.copy(alpha = 0f),
                    0.35f to colors.surface,
                    1f to colors.surface
                )
            )
            .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
            border = if (focused) BorderStroke(2.dp, colors.primary) else null
        ) {
            Column(Modifier.padding(8.dp)) {
                AnimatedVisibility(
                    visible = isGenerating && !statusText.isNullOrBlank(),
                    enter = expandVertically(tween(380, easing = WawEmphEasing)) +
                        fadeIn(tween(280, delayMillis = 60, easing = WawEmphDecelEasing)),
                    exit = shrinkVertically(tween(340, easing = WawEmphEasing)) +
                        fadeOut(tween(180, easing = WawEmphAccelEasing))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp
                        )
                        AnimatedContent(
                            targetState = statusText.orEmpty(),
                            transitionSpec = {
                                (fadeIn(tween(300, easing = WawEmphDecelEasing)) +
                                    slideInVertically(tween(300, easing = WawEmphDecelEasing)) { 8 }) togetherWith
                                    fadeOut(tween(140, easing = WawEmphAccelEasing))
                            },
                            label = "composer-status"
                        ) { label ->
                            Text(
                                text = label,
                                fontSize = 13.5.sp,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp, max = 148.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = colors.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        letterSpacing = 0.sp
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    decorationBox = { innerField ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.ai_input_hint),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        letterSpacing = 0.sp
                                    ),
                                    color = colors.onSurfaceVariant
                                )
                            }
                            innerField()
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ModelPicker(
                            config = config,
                            expanded = openMenu == "model",
                            onToggle = { openMenu = if (openMenu == "model") null else "model" },
                            onDismiss = { if (openMenu == "model") openMenu = null },
                            onModelChange = { providerId, model ->
                                onModelChange(providerId, model)
                                openMenu = null
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        ThinkingPicker(
                            selected = thinkingLevel,
                            expanded = openMenu == "think",
                            onToggle = { openMenu = if (openMenu == "think") null else "think" },
                            onDismiss = { if (openMenu == "think") openMenu = null },
                            onThinkingLevelChange = { level ->
                                onThinkingLevelChange(level)
                                openMenu = null
                            }
                        )
                        if (onToggleMode != null) {
                            ExecutionModePicker(
                                mode = executionMode,
                                expanded = openMenu == "mode",
                                onToggle = { openMenu = if (openMenu == "mode") null else "mode" },
                                onDismiss = { if (openMenu == "mode") openMenu = null },
                                onSelectFrida = { frida ->
                                    if ((executionMode == AiExecutionMode.Frida) != frida) onToggleMode()
                                    openMenu = null
                                }
                            )
                        }
                    }
                    SendStopButton(
                        isGenerating = isGenerating,
                        hasText = text.isNotBlank(),
                        onSend = {
                            focusManager.clearFocus()
                            onSend()
                        },
                        onStop = onStop
                    )
                }
            }
        }
    }
}

@Composable
private fun SendStopButton(
    isGenerating: Boolean,
    hasText: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val enabled = isGenerating || hasText
    val radius by animateDpAsState(
        targetValue = if (isGenerating) 23.dp else 16.dp,
        animationSpec = wawSpring(),
        label = "send-radius"
    )
    val bg by animateColorAsState(
        targetValue = when {
            isGenerating -> colors.onSurface
            enabled -> colors.primary
            else -> colors.onSurface.copy(alpha = 0.12f)
        },
        animationSpec = tween(280, easing = WawEmphEasing),
        label = "send-bg"
    )
    val fg = when {
        isGenerating -> colors.surface
        enabled -> colors.onPrimary
        else -> colors.onSurface.copy(alpha = 0.38f)
    }
    val desc = stringResource(if (isGenerating) R.string.ai_stop else R.string.ai_send)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = desc) {
                if (isGenerating) onStop() else onSend()
            }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isGenerating,
            transitionSpec = {
                (fadeIn(tween(260, easing = WawEmphDecelEasing)) + scaleIn(wawSpring())) togetherWith
                    fadeOut(tween(140))
            },
            label = "send-icon"
        ) { stopping ->
            Icon(
                imageVector = if (stopping) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ModelPicker(
    config: AiProviderConfig,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onModelChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val provider = config.providers.find { it.id == config.activeProviderId }
    val model = config.activeModelName ?: provider?.models?.firstOrNull()
    val label = model ?: stringResource(R.string.ai_chat_choose_model)
    val radius by animateDpAsState(if (expanded) 14.dp else 22.dp, wawSpring(), label = "model-radius")
    val bg by animateColorAsState(
        if (expanded) colors.primaryContainer else Color.Transparent,
        tween(280, easing = WawEmphEasing),
        label = "model-bg"
    )
    val fg = if (expanded) colors.onPrimaryContainer else colors.onSurfaceVariant

    Box(modifier) {
        Row(
            modifier = Modifier
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(radius))
                .background(bg)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModelInitialBadge(modelName = model, size = 24.dp)
            Text(
                text = label,
                color = fg,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        WawComposerMenu(expanded = expanded, onDismiss = onDismiss) {
            var hasModels = false
            config.providers.forEach { candidate ->
                if (candidate.models.isNotEmpty()) {
                    Text(
                        text = candidate.name,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceVariant
                    )
                    candidate.models.forEach { name ->
                        hasModels = true
                        val selected = provider?.id == candidate.id && model == name
                        WawComposerPopupRow(
                            label = name,
                            selected = selected,
                            iconContent = { ModelInitialBadge(modelName = name, size = 26.dp) },
                            onClick = { onModelChange(candidate.id, name) }
                        )
                    }
                }
            }
            if (!hasModels) {
                Text(
                    text = stringResource(R.string.ai_no_provider_hint),
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelInitialBadge(modelName: String?, size: androidx.compose.ui.unit.Dp) {
    val initial = remember(modelName) {
        modelName?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    }
    val tint = remember(initial) {
        Color.hsl(
            hue = (initial.code * 137.508f) % 360f,
            saturation = 0.50f,
            lightness = 0.85f
        )
    }
    Box(
        modifier = Modifier.size(size).background(tint, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial.toString(),
            color = Color(0xFF1F1F1F),
            fontSize = (size.value * 0.54f).sp,
            lineHeight = (size.value * 0.62f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ThinkingPicker(
    selected: ThinkingLevel,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onThinkingLevelChange: (ThinkingLevel) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box {
        WawComposerButton(
            description = stringResource(R.string.ai_chat_thinking_level, thinkingLevelLabel(selected)),
            active = expanded,
            lit = selected != ThinkingLevel.None,
            onClick = onToggle
        ) {
            ThinkingBulb(level = selected)
        }
        WawComposerMenu(expanded = expanded, onDismiss = onDismiss) {
            Text(
                text = stringResource(R.string.ai_chat_thinking_title),
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant
            )
            ThinkingLevel.entries.forEach { level ->
                WawComposerPopupRow(
                    label = thinkingLevelLabel(level),
                    selected = level == selected,
                    iconContent = { ThinkingBulb(level = level) },
                    onClick = { onThinkingLevelChange(level) }
                )
            }
        }
    }
}

@Composable
private fun ExecutionModePicker(
    mode: AiExecutionMode,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSelectFrida: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isFrida = mode == AiExecutionMode.Frida
    val label = stringResource(if (isFrida) R.string.ai_mode_frida else R.string.ai_mode_r2)
    Box {
        WawComposerButton(
            description = label,
            active = expanded,
            lit = isFrida,
            onClick = onToggle
        ) {
            Icon(
                imageVector = if (isFrida) Icons.Rounded.BugReport else Icons.Rounded.Terminal,
                contentDescription = null,
                modifier = Modifier.size(21.dp)
            )
        }
        WawComposerMenu(expanded = expanded, onDismiss = onDismiss) {
            Text(
                text = stringResource(R.string.ai_chat_mode_title),
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant
            )
            WawComposerPopupRow(
                label = stringResource(R.string.ai_mode_r2),
                selected = !isFrida,
                icon = Icons.Rounded.Terminal,
                onClick = { onSelectFrida(false) }
            )
            WawComposerPopupRow(
                label = stringResource(R.string.ai_mode_frida),
                selected = isFrida,
                icon = Icons.Rounded.BugReport,
                onClick = { onSelectFrida(true) }
            )
        }
    }
}

/**
 * Ported directly from Wawlink's Composer.kt:
 * Animated Canvas lightbulb whose fill opacity and 0..5 rays express thinking intensity,
 * with a diagonal slash when None and a spring pop when changed.
 */
@Composable
private fun ThinkingBulb(
    level: ThinkingLevel,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val rays = when (level) {
        ThinkingLevel.None -> 0
        ThinkingLevel.Light -> 1
        ThinkingLevel.Auto -> 2
        ThinkingLevel.Normal -> 3
        ThinkingLevel.Heavy -> 5
    }
    val fillTarget = when (rays) {
        0 -> 0f
        1 -> 0.22f
        2 -> 0.40f
        3 -> 0.65f
        else -> 1f
    }
    val fill by animateFloatAsState(fillTarget, tween(350, easing = WawEmphEasing), label = "bulb-fill")
    val angles = when (rays) {
        1 -> listOf(0f)
        2 -> listOf(-30f, 30f)
        3 -> listOf(-45f, 0f, 45f)
        5 -> listOf(-64f, -32f, 0f, 32f, 64f)
        else -> emptyList()
    }
    val rayProgress = (0 until 5).map { index ->
        animateFloatAsState(
            targetValue = if (index < rays) 1f else 0f,
            animationSpec = tween(420, delayMillis = index * 35, easing = WawEmphDecelEasing),
            label = "ray-$index"
        ).value
    }
    var popped by remember { mutableStateOf(true) }
    LaunchedEffect(level) {
        popped = false
        delay(16)
        popped = true
    }
    val scale by animateFloatAsState(if (popped) 1f else 0.82f, wawSpring(), label = "bulb-scale")
    Canvas(
        modifier
            .size(22.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        val s = size.minDimension / 24f
        val path = Path().apply {
            moveTo(12f * s, 6.2f * s)
            cubicTo(7.48f * s, 6.2f * s, 5.95f * s, 11.94f * s, 9.3f * s, 14.53f * s)
            lineTo(9.3f * s, 16.4f * s)
            lineTo(14.7f * s, 16.4f * s)
            lineTo(14.7f * s, 14.53f * s)
            cubicTo(18.05f * s, 11.94f * s, 16.52f * s, 6.2f * s, 12f * s, 6.2f * s)
            close()
        }
        drawPath(path, tint.copy(alpha = fill))
        drawPath(path, tint, style = Stroke(1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, alpha: Float = 1f) = drawLine(
            color = tint.copy(alpha = alpha),
            start = Offset(x1 * s, y1 * s),
            end = Offset(x2 * s, y2 * s),
            strokeWidth = 1.8f * s,
            cap = StrokeCap.Round
        )
        line(9.6f, 19f, 14.4f, 19f)
        line(10.6f, 21.4f, 13.4f, 21.4f)
        angles.forEachIndexed { index, angle ->
            val a = angle * PI.toFloat() / 180f
            val p = rayProgress[index]
            line(
                12f + sin(a) * 7.6f,
                10.5f - cos(a) * 7.6f,
                12f + sin(a) * (7.6f + 2.4f * p),
                10.5f - cos(a) * (7.6f + 2.4f * p),
                p
            )
        }
        if (rays == 0) line(5.5f, 4.5f, 18.5f, 19.5f)
    }
}

@Composable
private fun WawComposerButton(
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    lit: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val radius by animateDpAsState(if (active) 14.dp else 21.dp, wawSpring(), label = "btn-radius")
    val bg by animateColorAsState(
        if (active) colors.primaryContainer else Color.Transparent,
        tween(280, easing = WawEmphEasing),
        label = "btn-bg"
    )
    val fg = when {
        active -> colors.onPrimaryContainer
        lit -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { content() }
    }
}

/**
 * Ported from Wawlink's ComposerMenu: upward spring-pop rounded 26dp menu.
 */
@Composable
private fun WawComposerMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = expanded
    val scroll = rememberScrollState()
    val motion = rememberTransition(transition, label = "composer-menu")
    val scale by motion.animateFloat(
        transitionSpec = {
            if (targetState) tween(440, easing = WawEmphDecelEasing) else tween(200, easing = WawEmphAccelEasing)
        },
        label = "menu-scale"
    ) { if (it) 1f else 0.65f }
    val opacity by motion.animateFloat(
        transitionSpec = {
            if (targetState) tween(260, easing = WawEmphDecelEasing) else tween(160, easing = WawEmphAccelEasing)
        },
        label = "menu-opacity"
    ) { if (it) 1f else 0f }
    val density = LocalDensity.current
    var pivotX by remember { mutableFloatStateOf(0.15f) }
    var pivotY by remember { mutableFloatStateOf(1f) }
    val provider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val inset = with(density) { 12.dp.roundToPx() }
                val shadow = with(density) { 28.dp.roundToPx() }
                val contentWidth = popupContentSize.width - shadow * 2
                val x = (anchorBounds.left - with(density) { 6.dp.roundToPx() })
                    .coerceIn(inset, (windowSize.width - contentWidth - inset).coerceAtLeast(inset)) - shadow
                val y = (anchorBounds.top - popupContentSize.height + shadow - with(density) { 10.dp.roundToPx() })
                    .coerceAtLeast(inset - shadow)
                pivotX = ((anchorBounds.left + anchorBounds.width / 2f - x) / popupContentSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                pivotY = ((popupContentSize.height - shadow).toFloat() / popupContentSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                return IntOffset(x, y)
            }
        }
    }
    if (transition.currentState || transition.targetState) {
        Popup(
            popupPositionProvider = provider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true, clippingEnabled = false)
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = opacity
                        transformOrigin = TransformOrigin(pivotX, pivotY)
                        clip = false
                    }
                    .padding(28.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(26.dp),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 230.dp, max = 310.dp)
                            .heightIn(max = 440.dp)
                            .verticalScroll(scroll)
                            .padding(vertical = 8.dp),
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
private fun WawComposerPopupRow(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (selected) colors.primaryContainer else Color.Transparent,
        tween(260, easing = WawEmphEasing),
        label = "row-bg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    if (selected) Color.Transparent else colors.surfaceContainer,
                    RoundedCornerShape(if (selected) 14.dp else 21.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides if (selected) colors.onPrimaryContainer else colors.onSurface) {
                when {
                    iconContent != null -> iconContent()
                    icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
                }
            }
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            fontSize = 15.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.primary)
        }
    }
}

@Composable
private fun thinkingLevelLabel(level: ThinkingLevel): String = stringResource(
    when (level) {
        ThinkingLevel.None -> R.string.ai_thinking_none
        ThinkingLevel.Auto -> R.string.ai_thinking_auto
        ThinkingLevel.Light -> R.string.ai_thinking_light
        ThinkingLevel.Normal -> R.string.ai_thinking_normal
        ThinkingLevel.Heavy -> R.string.ai_thinking_heavy
    }
)

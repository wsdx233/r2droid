package top.wsdx233.r2droid.feature.ai.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jeziellago.compose.markdowntext.MarkdownText
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.ai.data.ActionResult
import top.wsdx233.r2droid.feature.ai.data.ActionType
import top.wsdx233.r2droid.feature.ai.data.ChatMessage
import top.wsdx233.r2droid.feature.ai.data.ChatRole
import top.wsdx233.r2droid.feature.ai.data.ThinkingLevel
import top.wsdx233.r2droid.feature.ai.PendingApproval

private data class ThoughtContent(
    val thought: String,
    val visibleContent: String
)

private fun extractThoughtContent(raw: String): ThoughtContent {
    val regex = Regex("(?s)<think>(.*?)</think>")
    val matches = regex.findAll(raw).toList()
    if (matches.isEmpty()) return ThoughtContent("", raw)
    val thought = matches.joinToString("\n\n") { it.groupValues[1].trim() }
    val visible = regex.replace(raw, "").trim()
    return ThoughtContent(thought = thought, visibleContent = visible)
}

private fun collapseToLastTwoLines(text: String): String {
    val lines = text.lines()
    if (lines.size <= 2) return text
    return lines.takeLast(2).joinToString("\n")
}

// region Message Bubbles

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onCopy: (String) -> Unit,
    onSelectCopy: (String) -> Unit,
    onEdit: ((String) -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val isUser = message.role == ChatRole.User
    var showMenu by remember { mutableStateOf(false) }
    var thoughtExpanded by remember { mutableStateOf(false) }
    val thoughtContent = remember(message.content) { extractThoughtContent(message.content) }
    val displayThought = if (thoughtExpanded) thoughtContent.thought else collapseToLastTwoLines(thoughtContent.thought)
    val messageContent = thoughtContent.visibleContent.ifBlank { message.content }

    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.85f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role label
        Text(
            text = when (message.role) {
                ChatRole.User -> stringResource(R.string.ai_role_you)
                ChatRole.Assistant -> stringResource(R.string.ai_role_assistant)
                ChatRole.ExecutionResult -> stringResource(R.string.ai_role_execution)
                ChatRole.System -> stringResource(R.string.ai_role_system)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = when {
                    isUser -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = { showMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (thoughtContent.thought.isNotBlank()) {
                        ThoughtBlock(
                            thought = displayThought,
                            expanded = thoughtExpanded,
                            onToggle = { thoughtExpanded = !thoughtExpanded }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    MarkdownText(
                        markdown = messageContent,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        syntaxHighlightColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurface
                    )

                    // Action results (collapsible)
                    if (message.actionResults.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        message.actionResults.forEach { result ->
                            ActionResultBlock(result)
                        }
                    }
                }
            }

            // Context menu
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_copy)) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = {
                        onCopy(message.content)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_select_copy)) },
                    leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                    onClick = {
                        onSelectCopy(message.content)
                        showMenu = false
                    }
                )
                if (isUser && onEdit != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ai_edit_resend)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            onEdit(message.content)
                            showMenu = false
                        }
                    )
                }
                if (!isUser && onRegenerate != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ai_regenerate)) },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        onClick = {
                            onRegenerate()
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = {
                        onDelete()
                        showMenu = false
                    }
                )
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun StreamingMessageBubble(content: String) {
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.85f
    var thoughtExpanded by remember(content) { mutableStateOf(false) }
    val thoughtContent = remember(content) { extractThoughtContent(content) }
    val displayThought = if (thoughtExpanded) thoughtContent.thought else collapseToLastTwoLines(thoughtContent.thought)
    val messageContent = thoughtContent.visibleContent.ifBlank { content }
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursorBlink"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = stringResource(R.string.ai_role_assistant),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.widthIn(max = maxWidth)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Column {
                        if (thoughtContent.thought.isNotBlank()) {
                            ThoughtBlock(
                                thought = displayThought,
                                expanded = thoughtExpanded,
                                onToggle = { thoughtExpanded = !thoughtExpanded }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        MarkdownText(
                            markdown = messageContent,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            syntaxHighlightColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // Blinking cursor
                Box(
                    modifier = Modifier
                        .padding(start = 1.dp, bottom = 2.dp)
                        .size(width = 2.dp, height = 16.dp)
                        .alpha(cursorAlpha)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

// endregion

@Composable
private fun ThoughtBlock(
    thought: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ai_thought),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onToggle) {
                    Text(
                        text = if (expanded) stringResource(R.string.ai_show_less)
                        else stringResource(R.string.ai_thought_expand),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text = thought,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

// region Select & Copy Dialog

@Composable
fun SelectCopyDialog(
    text: String,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.ai_select_copy))
                TextButton(onClick = onCopyAll) {
                    Text(stringResource(R.string.ai_copy_all))
                }
            }
        },
        text = {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

// endregion

// region Action Result Block

@Composable
fun ActionResultBlock(result: ActionResult) {
    var expanded by remember { mutableStateOf(false) }
    val maxOutputLength = 2000
    val isLongOutput = result.output.length > maxOutputLength
    var outputExpanded by remember { mutableStateOf(false) }
    val displayOutput = if (isLongOutput && !outputExpanded) {
        result.output.take(maxOutputLength) + "\n..."
    } else {
        result.output
    }
    val typeLabel = when (result.type) {
        ActionType.R2Command -> "R2"
        ActionType.JavaScript -> "JS"
    }
    val statusColor = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Type badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = result.input,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E1E1E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        SelectionContainer {
                            Text(
                                text = displayOutput,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFE0E0E0)
                                )
                            )
                        }
                        if (isLongOutput) {
                            Text(
                                text = if (outputExpanded) stringResource(R.string.ai_show_less)
                                else stringResource(R.string.ai_show_more, result.output.length),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF64B5F6),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { outputExpanded = !outputExpanded }
                            )
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region Input Bar

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    thinkingLevel: ThinkingLevel,
    onThinkingLevelChange: (ThinkingLevel) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    var showThinkingMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thinking level button
            Box {
                IconButton(onClick = { showThinkingMenu = true }) {
                    Text(
                        text = thinkingLevelIcon(thinkingLevel),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (thinkingLevel.apiEffort != null)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showThinkingMenu,
                    onDismissRequest = { showThinkingMenu = false }
                ) {
                    ThinkingLevel.entries.forEach { level ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    thinkingLevelLabel(level),
                                    color = if (level == thinkingLevel)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onThinkingLevelChange(level)
                                showThinkingMenu = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.ai_input_hint)) },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Spacer(Modifier.width(8.dp))
            if (isGenerating) {
                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.ai_stop))
                }
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank()
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.ai_send))
                }
            }
        }
    }
}

@Composable
private fun thinkingLevelIcon(level: ThinkingLevel): String = when (level) {
    ThinkingLevel.None -> "⊘"
    ThinkingLevel.Auto -> "A"
    ThinkingLevel.Light -> "L"
    ThinkingLevel.Normal -> "M"
    ThinkingLevel.Heavy -> "H"
}

@Composable
private fun thinkingLevelLabel(level: ThinkingLevel): String = when (level) {
    ThinkingLevel.None -> stringResource(R.string.ai_thinking_none)
    ThinkingLevel.Auto -> stringResource(R.string.ai_thinking_auto)
    ThinkingLevel.Light -> stringResource(R.string.ai_thinking_light)
    ThinkingLevel.Normal -> stringResource(R.string.ai_thinking_normal)
    ThinkingLevel.Heavy -> stringResource(R.string.ai_thinking_heavy)
}

// endregion

// region Thinking Indicator

@Composable
fun ThinkingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.ai_thinking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// endregion

// region Command Approval Dialog

@Composable
fun CommandApprovalDialog(
    pendingApproval: PendingApproval,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Block dismiss, must choose */ },
        title = { Text(stringResource(R.string.ai_approval_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.ai_approval_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = pendingApproval.command,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) {
                Text(stringResource(R.string.ai_approval_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text(stringResource(R.string.ai_approval_deny))
            }
        }
    )
}

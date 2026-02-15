package top.wsdx233.r2droid.feature.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.ai.AiEvent
import top.wsdx233.r2droid.feature.ai.AiViewModel
import top.wsdx233.r2droid.feature.ai.data.ChatRole
import top.wsdx233.r2droid.feature.ai.data.ChatSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiChatScreen(viewModel: AiViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showSystemPromptDialog by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    var selectCopyText by remember { mutableStateOf<String?>(null) }
    var deletingSessionId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Filter out ExecutionResult messages for display
    val displayMessages = remember(uiState.messages) {
        uiState.messages.filter { it.role != ChatRole.ExecutionResult }
    }

    // Auto-scroll to bottom
    val messageCount = displayMessages.size
    val hasStreaming = uiState.streamingContent.isNotBlank()
    LaunchedEffect(messageCount, hasStreaming) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Model selector
                val activeProvider = uiState.config.providers.find {
                    it.id == uiState.config.activeProviderId
                }
                Box {
                    TextButton(onClick = { showModelSelector = true }) {
                        Text(
                            text = if (activeProvider != null) {
                                "${activeProvider.name} / ${uiState.config.activeModelName ?: "?"}"
                            } else {
                                stringResource(R.string.ai_no_provider)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = showModelSelector,
                        onDismissRequest = { showModelSelector = false }
                    ) {
                        uiState.config.providers.forEach { provider ->
                            provider.models.forEach { model ->
                                val isActive = provider.id == uiState.config.activeProviderId &&
                                        model == uiState.config.activeModelName
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${provider.name} / $model",
                                            color = if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        viewModel.onEvent(AiEvent.SetProvider(provider.id, model))
                                        showModelSelector = false
                                    }
                                )
                            }
                        }
                        if (uiState.config.providers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ai_no_provider_hint)) },
                                onClick = { showModelSelector = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // History button
                IconButton(onClick = { showHistoryDialog = true }) {
                    Icon(Icons.Default.History, contentDescription = stringResource(R.string.ai_history))
                }

                // System prompt button
                IconButton(onClick = { showSystemPromptDialog = true }) {
                    Icon(Icons.Default.Psychology, contentDescription = stringResource(R.string.ai_system_prompt))
                }

                // New chat button
                IconButton(onClick = { viewModel.onEvent(AiEvent.NewChat) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ai_new_chat))
                }
            }
        }

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(displayMessages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    onCopy = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("AI Chat", text))
                        Toast.makeText(context, R.string.ai_copied, Toast.LENGTH_SHORT).show()
                    },
                    onSelectCopy = { text -> selectCopyText = text },
                    onEdit = if (message.role == ChatRole.User) { text ->
                        editingMessageId = message.id
                        editingText = text
                    } else null,
                    onRegenerate = if (message.role == ChatRole.Assistant) {
                        { viewModel.onEvent(AiEvent.RegenerateFrom(message.id)) }
                    } else null,
                    onDelete = { viewModel.onEvent(AiEvent.DeleteMessage(message.id)) }
                )
            }

            // Streaming content
            if (uiState.isGenerating && uiState.streamingContent.isNotBlank()) {
                item(key = "streaming") {
                    StreamingMessageBubble(content = uiState.streamingContent)
                }
            }

            // Thinking indicator
            if (uiState.isGenerating && uiState.streamingContent.isBlank()) {
                item(key = "thinking") {
                    ThinkingIndicator()
                }
            }
        }

        // Error display
        uiState.error?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Input bar
        ChatInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            isGenerating = uiState.isGenerating,
            onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.onEvent(AiEvent.SendMessage(inputText.trim()))
                    inputText = ""
                }
            },
            onStop = { viewModel.onEvent(AiEvent.StopGeneration) }
        )
    }

    // System prompt dialog
    if (showSystemPromptDialog) {
        SystemPromptDialog(
            currentPrompt = uiState.systemPrompt,
            onDismiss = { showSystemPromptDialog = false },
            onSave = { prompt ->
                viewModel.onEvent(AiEvent.UpdateSystemPrompt(prompt))
                showSystemPromptDialog = false
            },
            onReset = {
                viewModel.onEvent(AiEvent.ResetSystemPrompt)
                showSystemPromptDialog = false
            }
        )
    }

    // Edit message dialog
    if (editingMessageId != null) {
        EditMessageDialog(
            text = editingText,
            onTextChange = { editingText = it },
            onDismiss = { editingMessageId = null },
            onConfirm = {
                viewModel.onEvent(AiEvent.EditMessage(editingMessageId!!, editingText))
                editingMessageId = null
            }
        )
    }

    // Select & Copy dialog
    selectCopyText?.let { text ->
        SelectCopyDialog(
            text = text,
            onDismiss = { selectCopyText = null },
            onCopyAll = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AI Chat", text))
                Toast.makeText(context, R.string.ai_copied, Toast.LENGTH_SHORT).show()
                selectCopyText = null
            }
        )
    }

    // History dialog
    if (showHistoryDialog) {
        ChatHistoryDialog(
            sessions = uiState.chatSessions,
            onDismiss = { showHistoryDialog = false },
            onOpen = { sessionId ->
                viewModel.onEvent(AiEvent.LoadChat(sessionId))
                showHistoryDialog = false
            },
            onDeleteRequest = { sessionId -> deletingSessionId = sessionId },
        )
    }

    // Command approval dialog
    uiState.pendingApproval?.let { pending ->
        CommandApprovalDialog(
            pendingApproval = pending,
            onApprove = { viewModel.onEvent(AiEvent.ApproveCommand) },
            onDeny = { viewModel.onEvent(AiEvent.DenyCommand) }
        )
    }

    // Delete chat confirmation
    deletingSessionId?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { deletingSessionId = null },
            title = { Text(stringResource(R.string.ai_history_delete_title)) },
            text = { Text(stringResource(R.string.ai_history_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(AiEvent.DeleteChat(sessionId))
                    deletingSessionId = null
                }) {
                    Text(stringResource(R.string.home_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSessionId = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun ChatHistoryDialog(
    sessions: List<ChatSession>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onDeleteRequest: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_history)) },
        text = {
            if (sessions.isEmpty()) {
                Text(
                    stringResource(R.string.ai_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    items(sessions, key = { it.id }) { session ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    session.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    dateFormat.format(Date(session.timestamp)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onDeleteRequest(session.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.ai_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onOpen(session.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun SystemPromptDialog(
    currentPrompt: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var text by remember { mutableStateOf(currentPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_system_prompt)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                maxLines = 15,
                label = { Text(stringResource(R.string.ai_prompt_label)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.ai_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.ai_prompt_reset))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ai_cancel))
                }
            }
        }
    )
}

@Composable
private fun EditMessageDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_edit_message)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 10
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.ai_resend))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_cancel))
            }
        }
    )
}

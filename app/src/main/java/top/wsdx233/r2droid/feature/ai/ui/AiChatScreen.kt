package top.wsdx233.r2droid.feature.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.ai.AiEvent
import top.wsdx233.r2droid.feature.ai.AiViewModel
import top.wsdx233.r2droid.feature.ai.data.AiExecutionMode
import top.wsdx233.r2droid.feature.ai.data.ChatMessage
import top.wsdx233.r2droid.feature.ai.data.ChatRole
import top.wsdx233.r2droid.feature.ai.data.ChatSessionMetadata
import top.wsdx233.r2droid.feature.ai.data.ToolCallStatus
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.roundToInt

// The project can remove this composable when switching tabs. Retain UI state for the
// lifetime of its ViewModel without keeping the project/ViewModel alive after it closes.
internal class ChatScreenMemory {
    val drafts = mutableStateMapOf<String?, String>()
    var modeSessionId: Int? = null
    private val viewports = mutableMapOf<String, ChatViewport>()

    // Unsaved chats have no transcript to restore. Their draft is managed separately.
    @Composable
    fun viewportFor(chatId: String?): ChatViewport = remember(this, chatId) {
        if (chatId == null) ChatViewport() else viewports.getOrPut(chatId, ::ChatViewport)
    }

    fun forgetViewport(chatId: String) {
        viewports.remove(chatId)
    }
}

private val chatScreenMemory = WeakHashMap<AiViewModel, ChatScreenMemory>()

class AiChatTopBarState {
    internal var pendingOpenHistory by mutableStateOf(false)
    internal var pendingNewChat by mutableStateOf(false)
    var isFeedScrolled by mutableStateOf(false)
        internal set

    fun openHistory() {
        pendingOpenHistory = true
    }

    fun requestNewChat() {
        pendingNewChat = true
    }
}

@Composable
fun AiChatTopBarSubtitle(viewModel: AiViewModel) {
    val sessionTitle by remember(viewModel) {
        viewModel.uiState
            .map { state -> state.chatSessions.firstOrNull { it.id == state.currentChatId }?.title }
            .distinctUntilChanged()
    }.collectAsState(initial = null)
    val subtitle = sessionTitle ?: stringResource(R.string.ai_new_chat)
    AnimatedContent(
        targetState = subtitle,
        transitionSpec = {
            fadeIn(tween(220, easing = WawEmphDecelEasing)) togetherWith fadeOut(tween(140, easing = WawEmphAccelEasing))
        },
        label = "ai-chat-subtitle"
    ) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AiChatScreen(
    viewModel: AiViewModel,
    onNavigateToDetail: (Long, Int) -> Unit,
    onShowXrefs: (Long) -> Unit,
    topBarState: AiChatTopBarState? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val r2State by R2PipeManager.state.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    // MainActivity uses adjustPan for other editors. Chat uses ADJUST_RESIZE + imePadding()
    // while ProjectBottomBar stays stably anchored behind the IME for a flash-free keyboard transition.
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val original = window?.attributes?.softInputMode ?: 0
        window?.setSoftInputMode((original and LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { window?.setSoftInputMode(original) }
    }
    val isFridaSession = remember(r2State) { R2PipeManager.isR2FridaSession }
    val currentSessionId = remember(r2State) { R2PipeManager.sessionId }
    val targetSubtitle = remember(r2State) {
        R2PipeManager.currentFilePath?.let { path ->
            if (path.contains('/')) File(path).name.ifBlank { path } else path
        }
    }
    val chatId = uiState.currentChatId
    val memory = remember(viewModel) { chatScreenMemory.getOrPut(viewModel) { ChatScreenMemory() } }
    val viewport = memory.viewportFor(chatId)
    val drafts = memory.drafts
    val inputText = drafts[chatId].orEmpty()
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showNewChatConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var editingMessageId by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    var editingText by rememberSaveable(chatId) { mutableStateOf("") }
    var detail by remember(chatId) { mutableStateOf<ChatTextDetail?>(null) }
    var selectedReference by remember(chatId, currentSessionId) { mutableStateOf<String?>(null) }
    var deletingSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var overlayHeight by remember { mutableStateOf(136.dp) }
    var feedScrolled by remember(viewport) { mutableStateOf(false) }

    LaunchedEffect(viewModel, currentSessionId) {
        if (currentSessionId != memory.modeSessionId) {
            viewModel.onEvent(AiEvent.SetExecutionMode(if (isFridaSession) AiExecutionMode.Frida else AiExecutionMode.R2))
            memory.modeSessionId = currentSessionId
        }
    }

    if (topBarState != null) {
        LaunchedEffect(topBarState.pendingOpenHistory) {
            if (topBarState.pendingOpenHistory) {
                topBarState.pendingOpenHistory = false
                showHistorySheet = true
            }
        }
        LaunchedEffect(topBarState.pendingNewChat) {
            if (topBarState.pendingNewChat) {
                topBarState.pendingNewChat = false
                if (uiState.isGenerating) showNewChatConfirmDialog = true
                else viewModel.onEvent(AiEvent.NewChat)
            }
        }
        LaunchedEffect(topBarState, feedScrolled) {
            topBarState.isFeedScrolled = feedScrolled
        }
        DisposableEffect(topBarState) {
            onDispose { topBarState.isFeedScrolled = false }
        }
    }

    val displayMessages = remember(uiState.messages) {
        uiState.messages.filter { it.role != ChatRole.ExecutionResult }
    }
    val onCopy: (String) -> Unit = remember(context) {
        { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ai_tab_chat), text))
            Toast.makeText(context, R.string.ai_copied, Toast.LENGTH_SHORT).show()
        }
    }
    val title = uiState.chatSessions.firstOrNull { it.id == chatId }?.title ?: stringResource(R.string.ai_tab_chat)
    val runningStatusText = if (uiState.isGenerating) {
        val lastCalls = displayMessages.lastOrNull()?.toolCalls.orEmpty()
        when {
            uiState.isCompacting -> stringResource(R.string.ai_chat_status_compacting)
            lastCalls.any { it.status == ToolCallStatus.AwaitingApproval } -> stringResource(R.string.ai_chat_status_approval)
            lastCalls.any { it.status == ToolCallStatus.Running } -> stringResource(R.string.ai_chat_status_running)
            else -> stringResource(R.string.ai_chat_status_thinking)
        }
    } else null

    val colors = MaterialTheme.colorScheme
    val barColor by animateColorAsState(
        targetValue = if (feedScrolled) colors.surfaceContainer else colors.surface,
        animationSpec = tween(350, easing = WawEmphEasing),
        label = "topbar-color"
    )

    Box(Modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize().imePadding()) {
            if (topBarState == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(barColor)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(
                            Icons.Rounded.Forum,
                            contentDescription = stringResource(R.string.ai_history),
                            tint = colors.onSurfaceVariant
                        )
                    }
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = {
                        if (uiState.isGenerating) showNewChatConfirmDialog = true
                        else viewModel.onEvent(AiEvent.NewChat)
                    }) {
                        Icon(
                            Icons.Rounded.EditNote,
                            contentDescription = stringResource(R.string.ai_new_chat),
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(viewport) {
                    ChatFeed(
                        messages = displayMessages,
                        viewport = viewport,
                        bottomPadding = overlayHeight + 12.dp,
                        onScrolledChanged = { feedScrolled = it },
                        modifier = Modifier.fillMaxSize()
                    ) { message, modifier ->
                        MessageBubble(
                            message = message,
                            busy = uiState.isGenerating,
                            modifier = modifier,
                            onCopy = onCopy,
                            onDetail = { detail = it },
                            onReferenceClick = { selectedReference = it },
                            onEdit = {
                                editingMessageId = message.id
                                editingText = message.content
                            },
                            onRegenerate = { viewModel.onEvent(AiEvent.RegenerateFrom(message.id)) },
                            onDelete = { viewModel.onEvent(AiEvent.DeleteMessage(message.id)) },
                            onDecideTool = { callId, approved, alwaysAllow ->
                                viewModel.onEvent(AiEvent.DecideTool(message.id, callId, approved, alwaysAllow))
                            }
                        )
                    }
                }

                if (displayMessages.isEmpty() && uiState.error == null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = overlayHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = colors.primary
                        )
                        Text(
                            text = stringResource(R.string.ai_chat_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface
                        )
                        if (!targetSubtitle.isNullOrBlank()) {
                            Text(
                                text = targetSubtitle,
                                fontFamily = LocalAppFont.current,
                                fontSize = 13.5.sp,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { size -> overlayHeight = with(density) { size.height.toDp() } }
                ) {
                    var lastError by remember(chatId) { mutableStateOf(uiState.error) }
                    LaunchedEffect(uiState.error) { uiState.error?.let { lastError = it } }
                    AnimatedVisibility(
                        visible = uiState.error != null,
                        enter = expandVertically(tween(320, easing = WawEmphDecelEasing)) + fadeIn(tween(220)),
                        exit = shrinkVertically(tween(240, easing = WawEmphAccelEasing)) + fadeOut(tween(150))
                    ) {
                        ChatErrorBanner(
                            error = uiState.error ?: lastError.orEmpty(),
                            onDismiss = { viewModel.onEvent(AiEvent.DismissError) }
                        )
                    }

                    key(chatId) {
                        ChatInputBar(
                            text = inputText,
                            onTextChange = { if (it.isEmpty()) drafts.remove(chatId) else drafts[chatId] = it },
                            isGenerating = uiState.isGenerating,
                            statusText = runningStatusText,
                            config = uiState.config,
                            onModelChange = { providerId, model -> viewModel.onEvent(AiEvent.SetProvider(providerId, model)) },
                            thinkingLevel = uiState.thinkingLevel,
                            onThinkingLevelChange = { viewModel.onEvent(AiEvent.SetThinkingLevel(it)) },
                            executionMode = uiState.executionMode,
                            onToggleMode = if (isFridaSession) {
                                {
                                    val mode = if (uiState.executionMode == AiExecutionMode.R2) AiExecutionMode.Frida else AiExecutionMode.R2
                                    viewModel.onEvent(AiEvent.SetExecutionMode(mode))
                                }
                            } else null,
                            onSend = {
                                if (inputText.isNotBlank() && !uiState.isGenerating) {
                                    viewport.followTail.value = true
                                    drafts.remove(chatId)
                                    viewModel.onEvent(AiEvent.SendMessage(inputText.trim()))
                                }
                            },
                            onStop = { viewModel.onEvent(AiEvent.StopGeneration) }
                        )
                    }
                }
            }
        }
    }

    if (showNewChatConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatConfirmDialog = false },
            title = { Text(stringResource(R.string.ai_new_chat_running_title)) },
            text = { Text(stringResource(R.string.ai_new_chat_running_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showNewChatConfirmDialog = false
                    viewModel.onEvent(AiEvent.NewChat)
                }) {
                    Text(stringResource(R.string.ai_new_chat_running_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatConfirmDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    editingMessageId?.let { messageId ->
        EditMessageSheet(
            text = editingText,
            onTextChange = { editingText = it },
            onDismiss = { editingMessageId = null },
            onConfirm = {
                viewModel.onEvent(AiEvent.EditMessage(messageId, editingText))
                editingMessageId = null
            }
        )
    }

    detail?.let { payload ->
        SelectCopyDialog(detail = payload, onDismiss = { detail = null }, onCopyAll = { onCopy(payload.text) })
    }

    selectedReference?.let { token ->
        AiChatReferenceSheet(
            token = token,
            onCopy = onCopy,
            onNavigateToDetail = onNavigateToDetail,
            onShowXrefs = onShowXrefs,
            onDismiss = { selectedReference = null }
        )
    }

    if (showHistorySheet) {
        ChatHistorySheet(
            sessions = uiState.chatSessions,
            activeSessionId = chatId,
            onDismiss = { showHistorySheet = false },
            onOpen = { sessionId ->
                viewModel.onEvent(AiEvent.LoadChat(sessionId))
                showHistorySheet = false
            },
            onDeleteRequest = { sessionId -> deletingSessionId = sessionId }
        )
    }

    deletingSessionId?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { deletingSessionId = null },
            title = { Text(stringResource(R.string.ai_history_delete_title)) },
            text = { Text(stringResource(R.string.ai_history_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    drafts.remove(sessionId)
                    memory.forgetViewport(sessionId)
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
private fun ChatErrorBanner(error: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Error,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(20.dp)
            )
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    text = error,
                    color = colors.onErrorContainer,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.ai_chat_dismiss_error),
                    tint = colors.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private const val TailKey = "ai-chat-tail"
internal val LocalPauseChatFollow = staticCompositionLocalOf<() -> Unit> { {} }

internal class ChatViewport {
    val followTail = mutableStateOf(true)
    var readingAnchor: ChatReadingAnchor? = null
}

internal data class ChatReadingAnchor(val messageId: String, val index: Int, val offset: Int) {
    fun indexIn(messages: List<ChatMessage>): Int {
        if (messages.getOrNull(index)?.id == messageId) return index
        val byId = messages.indexOfFirst { it.id == messageId }
        // A deleted row leaves the next row in its old slot, or the last row if truncated.
        return if (byId >= 0) byId else index.coerceAtMost(messages.lastIndex)
    }

    fun offsetIn(messages: List<ChatMessage>, index: Int): Int =
        if (messages[index].id == messageId) offset else 0
}

private fun LazyListState.readingAnchor(): ChatReadingAnchor? {
    val index = firstVisibleItemIndex
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index && it.key != TailKey }
    val messageId = item?.key as? String ?: return null
    return ChatReadingAnchor(messageId, index, firstVisibleItemScrollOffset)
}

internal fun findPreviousUserMessageIndex(
    messages: List<ChatMessage>,
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    offsetThresholdPx: Int = 24
): Int? {
    if (messages.isEmpty()) return null
    val clamped = firstVisibleIndex.coerceAtMost(messages.lastIndex)
    if (clamped >= 0 && firstVisibleIndex <= messages.lastIndex &&
        messages[clamped].role == ChatRole.User && firstVisibleOffset > offsetThresholdPx
    ) {
        return clamped
    }
    val start = if (firstVisibleIndex > messages.lastIndex) messages.lastIndex else clamped - 1
    for (i in start downTo 0) {
        if (messages[i].role == ChatRole.User) return i
    }
    return null
}

@Composable
internal fun ChatFeed(
    messages: List<ChatMessage>,
    viewport: ChatViewport,
    bottomPadding: Dp,
    onScrolledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    messageContent: @Composable (ChatMessage, Modifier) -> Unit
) {
    val autoFollow = viewport.followTail
    val savedAnchor = remember(viewport) { viewport.readingAnchor.takeUnless { autoFollow.value } }
    val startedWithMessages = remember(viewport) { messages.isNotEmpty() }
    val list = remember(viewport) {
        if (savedAnchor != null && startedWithMessages) {
            val index = savedAnchor.indexIn(messages)
            LazyListState(index, savedAnchor.offsetIn(messages, index))
        } else {
            LazyListState(if (autoFollow.value) messages.size else 0)
        }
    }
    var restoringAnchor by remember(list) { mutableStateOf(savedAnchor != null) }
    if (restoringAnchor && savedAnchor != null) {
        val latestMessages by rememberUpdatedState(messages)
        LaunchedEffect(list) {
            if (!startedWithMessages) {
                val current = snapshotFlow { latestMessages.takeIf { it.isNotEmpty() } }
                    .filterNotNull().first()
                if (!autoFollow.value) {
                    val index = savedAnchor.indexIn(current)
                    list.scrollToItem(index, savedAnchor.offsetIn(current, index))
                }
            }
            snapshotFlow { list.layoutInfo.totalItemsCount > 1 }.first { it }
            val current = latestMessages
            if (!autoFollow.value && current.isNotEmpty()) {
                val index = savedAnchor.indexIn(current)
                // An edited row may now be shorter than the saved offset. In that case
                // show the row from its start rather than scrolling into a later message.
                if (savedAnchor.offsetIn(current, index) > 0 && list.firstVisibleItemIndex > index) {
                    list.scrollToItem(index)
                }
            }
            restoringAnchor = false
        }
    }
    DisposableEffect(list) {
        onDispose {
            // A disposed feed retains only a measured position, never its LazyListState.
            if (autoFollow.value) viewport.readingAnchor = null
            else if (!restoringAnchor) list.readingAnchor()?.let { viewport.readingAnchor = it }
        }
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var followTail by autoFollow
    val pauseFollow = remember(autoFollow) { { autoFollow.value = false } }
    var jumping by remember { mutableStateOf(false) }
    var jumpingToBottom by remember { mutableStateOf(false) }
    var userScrollTick by remember { mutableIntStateOf(0) }
    var scrollbarDragging by remember { mutableStateOf(false) }
    var scrollControlsVisible by remember { mutableStateOf(false) }

    val scrolled by remember(list) {
        derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 4 }
    }
    LaunchedEffect(scrolled) { onScrolledChanged(scrolled) }

    val previousUserIndex by remember(messages, list) {
        derivedStateOf {
            findPreviousUserMessageIndex(
                messages = messages,
                firstVisibleIndex = list.firstVisibleItemIndex,
                firstVisibleOffset = list.firstVisibleItemScrollOffset
            )
        }
    }

    LaunchedEffect(userScrollTick) {
        if (userScrollTick == 0) return@LaunchedEffect
        scrollControlsVisible = true
        snapshotFlow { list.isScrollInProgress || scrollbarDragging }.first { !it }
        delay(1600L)
        scrollControlsVisible = false
    }

    val scrollObserver = remember(list) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    if (list.canScrollBackward || list.canScrollForward) userScrollTick++
                    if (available.y > 0f && list.canScrollBackward) followTail = false
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                if (list.canScrollBackward || list.canScrollForward) userScrollTick++
                if (list.canScrollBackward) followTail = false
            }
            if ((interaction is DragInteraction.Stop || interaction is DragInteraction.Cancel) && !list.canScrollForward) {
                followTail = true
            }
        }
    }
    LaunchedEffect(list) {
        snapshotFlow {
            // A pre-layout, footer-only, or clamped restore is not a return to the tail.
            when {
                restoringAnchor -> null
                list.canScrollForward || list.isScrollInProgress || scrollbarDragging -> false
                list.layoutInfo.totalItemsCount > 1 -> true
                else -> null
            }
        }.filterNotNull().dropWhile { it }.collect { atRestAtEnd ->
            if (atRestAtEnd) followTail = true
        }
    }
    LaunchedEffect(list) {
        // Observe measured layout, never text deltas. Keep a separate footer so even a very
        // tall final message can be reached without scrolling to an oversized offset.
        snapshotFlow {
            if (followTail && !jumping && !scrollbarDragging && !list.isScrollInProgress && list.canScrollForward) list.layoutInfo else null
        }.collect { layout ->
            if (layout != null) coroutineScope {
                launch { if (followTail && !jumping && !scrollbarDragging) list.followFooter() }
            }
        }
    }

    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollObserver),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(messages, key = { it.id }, contentType = { it.role }) { message ->
                var entered by rememberSaveable(message.id) { mutableStateOf(message.role == ChatRole.User) }
                LaunchedEffect(message.id) { entered = true }
                val enter by animateFloatAsState(
                    targetValue = if (entered) 1f else 0f,
                    animationSpec = tween(480, easing = WawEmphDecelEasing),
                    label = "msg-enter"
                )
                CompositionLocalProvider(LocalPauseChatFollow provides pauseFollow) {
                    messageContent(
                        message,
                        Modifier
                            .animateItem(
                                fadeInSpec = if (message.role == ChatRole.User) null else tween(480, easing = WawEmphDecelEasing),
                                fadeOutSpec = tween(180, easing = WawEmphAccelEasing),
                                placementSpec = null
                            )
                            .graphicsLayer {
                                translationY = (1f - enter) * with(density) { 16.dp.toPx() }
                                val s = 0.985f + 0.015f * enter
                                scaleX = s
                                scaleY = s
                            }
                    )
                }
            }
            item(key = TailKey) { Spacer(Modifier.height(1.dp)) }
        }

        ChatFastScrollbar(
            listState = list,
            visible = scrollControlsVisible,
            isDragging = scrollbarDragging,
            onDraggingChange = { dragging ->
                scrollbarDragging = dragging
                if (dragging) {
                    followTail = false
                    userScrollTick++
                } else if (!list.canScrollForward) {
                    followTail = true
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 8.dp, bottom = bottomPadding, end = 2.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = scrollControlsVisible && previousUserIndex != null,
                enter = fadeIn(tween(260, easing = WawEmphDecelEasing)) + scaleIn(wawSpring()),
                exit = fadeOut(tween(150, easing = WawEmphAccelEasing))
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val target = previousUserIndex ?: return@FilledTonalIconButton
                        if (!jumping) scope.launch {
                            jumping = true
                            followTail = false
                            userScrollTick++
                            try {
                                list.animateScrollToItem(target, 0)
                            } finally {
                                jumping = false
                            }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.ai_chat_jump_prev_user))
                }
            }
            AnimatedVisibility(
                visible = list.canScrollForward && !followTail && !jumpingToBottom,
                enter = fadeIn(tween(260, easing = WawEmphDecelEasing)) + scaleIn(wawSpring()),
                exit = fadeOut(tween(150, easing = WawEmphAccelEasing))
            ) {
                FilledTonalIconButton(
                    onClick = {
                        if (!jumping) scope.launch {
                            jumping = true
                            jumpingToBottom = true
                            try {
                                list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                                followTail = true
                            } finally {
                                jumping = false
                                jumpingToBottom = false
                            }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.ai_chat_jump_to_latest))
                }
            }
        }
    }
}

@Composable
private fun ChatFastScrollbar(
    listState: LazyListState,
    visible: Boolean,
    isDragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val canScroll by remember(listState) {
        derivedStateOf { listState.canScrollBackward || listState.canScrollForward }
    }
    if (!canScroll) return

    var dragRatio by remember { mutableStateOf<Float?>(null) }
    val alpha by animateFloatAsState(
        targetValue = if (visible || isDragging) 1f else 0f,
        animationSpec = tween(if (visible || isDragging) 180 else 320, easing = WawEmphEasing),
        label = "chat-scrollbar-alpha"
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 7.dp else 4.dp,
        animationSpec = wawSpring(),
        label = "chat-scrollbar-width"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isDragging) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(220, easing = WawEmphEasing),
        label = "chat-scrollbar-color"
    )

    fun currentScrollProgress(): Float {
        if (!listState.canScrollBackward) return 0f
        if (!listState.canScrollForward) return 1f
        val layout = listState.layoutInfo
        val total = layout.totalItemsCount
        val items = layout.visibleItemsInfo
        if (total <= 0 || items.isEmpty()) return 0f
        val first = items.first()
        val last = items.last()
        val firstFrac = first.index + (-first.offset.toFloat() / first.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        val lastFrac = last.index + ((layout.viewportEndOffset - last.offset).toFloat() / last.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        val span = (lastFrac - firstFrac).coerceAtLeast(0.1f)
        return (firstFrac / (total - span).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
    }

    fun scrubToRatio(ratio: Float) {
        val layout = listState.layoutInfo
        val total = layout.totalItemsCount
        if (total <= 0) return
        val tail = total - 1
        scope.launch {
            when {
                ratio <= 0.005f -> listState.scrollToItem(0, 0)
                ratio >= 0.995f -> listState.scrollToItem(tail, 0)
                else -> {
                    val avgSize = layout.visibleItemsInfo.map { it.size }.filter { it > 0 }.average()
                        .takeIf { !it.isNaN() }?.toFloat() ?: 240f
                    val viewportHeight = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
                    val visibleSpan = (viewportHeight / avgSize).coerceAtLeast(0.5f)
                    val maxTop = (total - visibleSpan).coerceAtLeast(0.5f)
                    val pos = ratio * maxTop
                    val targetIndex = pos.toInt().coerceIn(0, tail)
                    val itemSize = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.size
                        ?: avgSize.roundToInt()
                    val offsetPx = ((pos - targetIndex) * itemSize).roundToInt().coerceAtLeast(0)
                    listState.scrollToItem(targetIndex, offsetPx)
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(18.dp)
            .graphicsLayer { this.alpha = alpha }
            .pointerInput(listState) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    val thumbHeightPx = 44.dp.toPx().coerceAtMost(height * 0.4f)
                    val travel = (height - thumbHeightPx).coerceAtLeast(1f)
                    val currentTop = (dragRatio ?: currentScrollProgress()) * travel
                    val grabOffset = if (down.position.y in currentTop..(currentTop + thumbHeightPx)) {
                        down.position.y - currentTop
                    } else thumbHeightPx / 2f

                    fun applyY(y: Float) {
                        val ratio = ((y - grabOffset) / travel).coerceIn(0f, 1f)
                        dragRatio = ratio
                        scrubToRatio(ratio)
                    }

                    onDraggingChange(true)
                    try {
                        applyY(down.position.y)
                        verticalDrag(down.id) { change ->
                            change.consume()
                            applyY(change.position.y)
                        }
                    } finally {
                        dragRatio = null
                        onDraggingChange(false)
                    }
                }
            }
    ) {
        val ratio = dragRatio ?: currentScrollProgress()
        val thumbH = 44.dp.toPx().coerceAtMost(size.height * 0.4f)
        val thumbW = thumbWidth.toPx().coerceAtMost(size.width)
        val top = ratio * (size.height - thumbH).coerceAtLeast(0f)
        val left = if (layoutDirection == LayoutDirection.Ltr) size.width - thumbW else 0f
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(left, top),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
        )
    }
}

private suspend fun LazyListState.followFooter() {
    val layout = layoutInfo
    val tailIndex = layout.totalItemsCount - 1
    if (tailIndex < 0) return
    val footer = layout.visibleItemsInfo.lastOrNull()?.takeIf { it.key == TailKey }
    if (footer == null) {
        scrollToItem(tailIndex)
    } else {
        val remaining = footer.offset.toFloat() + footer.size + layout.afterContentPadding - layout.viewportEndOffset
        if (remaining > 0f) scrollBy(remaining)
    }
}

@Composable
private fun ChatHistorySheet(
    sessions: List<ChatSessionMetadata>,
    activeSessionId: String?,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onDeleteRequest: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    WawSheet(title = stringResource(R.string.ai_history), onDismiss = onDismiss) {
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ai_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(sessions, key = { _, item -> item.id }) { index, session ->
                    val active = session.id == activeSessionId
                    val shape = when {
                        sessions.size == 1 -> RoundedCornerShape(22.dp)
                        index == 0 -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        index == sessions.lastIndex -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
                        else -> RoundedCornerShape(8.dp)
                    }
                    Surface(
                        color = if (active) colors.primaryContainer else colors.surfaceContainerLow,
                        shape = shape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(session.id) }
                                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (active) colors.primary else colors.surfaceContainerHigh,
                                        RoundedCornerShape(13.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = if (active) colors.onPrimary else colors.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = session.title,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (active) colors.onPrimaryContainer else colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = dateFormat.format(Date(session.timestamp)),
                                    fontSize = 12.5.sp,
                                    color = if (active) colors.onPrimaryContainer.copy(alpha = 0.75f) else colors.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteRequest(session.id) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.ai_delete),
                                    tint = colors.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditMessageSheet(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    WawSheet(title = stringResource(R.string.ai_edit_message), onDismiss = onDismiss) {
        WawFilledTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = stringResource(R.string.ai_input_hint),
            minHeight = 100.dp,
            maxHeight = 240.dp
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(46.dp)
            ) {
                Text(stringResource(R.string.ai_cancel))
            }
            Button(
                onClick = onConfirm,
                enabled = text.isNotBlank(),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text(stringResource(R.string.ai_resend))
            }
        }
    }
}

@Composable
private fun WawFilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: Dp,
    maxHeight: Dp
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = if (focused) colors.surface else colors.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = if (focused) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight)
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = colors.onSurface,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = colors.onSurfaceVariant, fontSize = 15.sp)
                    }
                    inner()
                }
            }
        )
    }
}

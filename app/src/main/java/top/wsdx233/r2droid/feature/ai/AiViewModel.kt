package top.wsdx233.r2droid.feature.ai

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.ai.data.*
import top.wsdx233.r2droid.util.R2PipeManager
import java.util.UUID
import javax.inject.Inject

sealed interface AiEvent {
    data class SendMessage(val text: String) : AiEvent
    data class EditMessage(val messageId: String, val newText: String) : AiEvent
    data class RegenerateFrom(val messageId: String) : AiEvent
    data class DeleteMessage(val messageId: String) : AiEvent
    object StopGeneration : AiEvent
    object DismissError : AiEvent
    object NewChat : AiEvent
    data class SetProvider(val providerId: String, val modelName: String) : AiEvent
    data class SetModel(val modelName: String) : AiEvent
    data class UpdateSystemPrompt(val prompt: String) : AiEvent
    object ResetSystemPrompt : AiEvent
    object SaveChat : AiEvent
    data class LoadChat(val sessionId: String) : AiEvent
    data class DeleteChat(val sessionId: String) : AiEvent
    data class DecideTool(val messageId: String, val callId: String, val approved: Boolean, val alwaysAllow: Boolean = false) : AiEvent
    data class SetThinkingLevel(val level: ThinkingLevel) : AiEvent
    data class SetExecutionMode(val mode: AiExecutionMode) : AiEvent
}

data class AiUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val isCompacting: Boolean = false,
    val compaction: ChatCompaction? = null,
    val error: String? = null,
    val config: AiProviderConfig = AiProviderConfig(),
    val systemPrompt: String = AiSettingsManager.DEFAULT_SYSTEM_PROMPT,
    val chatSessions: List<ChatSessionMetadata> = emptyList(),
    val currentChatId: String? = null,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.Auto,
    val executionMode: AiExecutionMode = AiExecutionMode.R2
)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiUiState(
        config = AiSettingsManager.configFlow.value,
        systemPrompt = AiSettingsManager.systemPrompt,
        executionMode = if (R2PipeManager.isR2FridaSession) AiExecutionMode.Frida else AiExecutionMode.R2
    ))
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private val executor = R2ActionExecutor(context.cacheDir)
    private var generation: Job? = null
    private var target: R2ActionExecutor.Target? = null
    private var approval: Approval? = null
    private var live: LiveReply? = null
    private var sessionAutoApprove = false
    private val historyLock = Mutex()
    private var loadingChat: Job? = null

    private data class ApprovalResult(val approved: Boolean, val alwaysAllow: Boolean)
    private class Approval(val messageId: String, val callId: String, val answer: CompletableDeferred<ApprovalResult>)
    private class LiveReply(
        val id: String,
        val text: StringBuilder = StringBuilder(),
        val reasoning: StringBuilder = StringBuilder()
    )

    init {
        viewModelScope.launch { AiSettingsManager.configFlow.collect { config -> _uiState.update { it.copy(config = config) } } }
        viewModelScope.launch { AiSettingsManager.sessionsFlow.collect { chats -> _uiState.update { it.copy(chatSessions = chats) } } }
        viewModelScope.launch {
            combine(R2PipeManager.activeSessionId, R2PipeManager.sessions, R2PipeManager.state) { _, _, _ ->
                target?.isCurrent() == false
            }.collect { changed -> if (changed && target?.isCurrent() == false) stopGeneration(TARGET_CHANGED) }
        }
    }

    fun onEvent(event: AiEvent) {
        when (event) {
            is AiEvent.SendMessage -> sendMessage(event.text)
            is AiEvent.EditMessage -> editAndResend(event.messageId, event.newText)
            is AiEvent.RegenerateFrom -> regenerateFrom(event.messageId)
            is AiEvent.DeleteMessage -> deleteMessage(event.messageId)
            AiEvent.StopGeneration -> stopGeneration()
            AiEvent.DismissError -> _uiState.update { it.copy(error = null) }
            AiEvent.NewChat -> newChat()
            is AiEvent.SetProvider -> AiSettingsManager.setActiveProvider(event.providerId, event.modelName)
            is AiEvent.SetModel -> AiSettingsManager.setActiveModel(event.modelName)
            is AiEvent.UpdateSystemPrompt -> updateSystemPrompt(event.prompt)
            AiEvent.ResetSystemPrompt -> updateSystemPrompt(AiSettingsManager.DEFAULT_SYSTEM_PROMPT)
            AiEvent.SaveChat -> saveCurrentChat()
            is AiEvent.LoadChat -> loadChat(event.sessionId)
            is AiEvent.DeleteChat -> deleteChat(event.sessionId)
            is AiEvent.DecideTool -> approval?.takeIf { it.messageId == event.messageId && it.callId == event.callId }
                ?.answer?.complete(ApprovalResult(event.approved, event.alwaysAllow))
            is AiEvent.SetThinkingLevel -> _uiState.update { it.copy(thinkingLevel = event.level) }
            is AiEvent.SetExecutionMode -> _uiState.update { it.copy(executionMode = event.mode) }
        }
    }

    private fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isGenerating) return
        loadingChat?.cancel()
        val message = ChatMessage(role = ChatRole.User, content = text)
        _uiState.update { it.copy(currentChatId = it.currentChatId ?: UUID.randomUUID().toString(),
            messages = it.messages + message, error = null) }
        saveCurrentChat()
        startGeneration()
    }

    private fun startGeneration() {
        val state = _uiState.value
        val provider = state.config.providers.find { it.id == state.config.activeProviderId }
        val model = state.config.activeModelName ?: provider?.models?.firstOrNull()
        if (provider == null || model.isNullOrBlank()) {
            _uiState.update { it.copy(error = if (provider == null) "No AI provider configured" else "No model selected") }
            return
        }
        val bound = executor.capture(state.executionMode)
        target = bound
        val tools = bound?.definitions.orEmpty()
        val modeNote = when {
            bound == null -> "No active radare2 session is connected; answer in text."
            state.executionMode == AiExecutionMode.Frida && tools.any { it.name == R2ActionExecutor.RUN_FRIDA } ->
                "Active mode: Frida (r2frida connected). Prefer `run_frida` for runtime instrumentation and `r2_command` for session checks."
            else ->
                "Active mode: Radare2. Use `r2_command` and `run_js` tools to inspect and analyze the binary."
        }
        val prompt = "${state.systemPrompt}\n\n$modeNote"
        _uiState.update { it.copy(isGenerating = true, error = null) }
        generation = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val owner = currentCoroutineContext().job
            try {
                val seen = mutableSetOf<String>()
                var round = 0
                while (true) {
                    round++
                    currentCoroutineContext().ensureActive()
                    check(bound?.isCurrent() != false) { TARGET_CHANGED }
                    val prior = _uiState.value.messages
                    val row = ChatMessage(role = ChatRole.Assistant, content = "", isStreaming = true)
                    _uiState.update { it.copy(messages = it.messages + row) }
                    val draft = LiveReply(row.id)
                    live = draft
                    var completed: AiTurnEvent.Completed? = null
                    var lastPaint = 0L
                    aiRepository.streamChatTurn(
                        provider, prior, model, prompt, state.thinkingLevel, tools, _uiState.value.compaction,
                        onCompacting = { _uiState.update { it.copy(isCompacting = true) } },
                        onCompacted = { checkpoint ->
                            check(bound?.isCurrent() != false) { TARGET_CHANGED }
                            _uiState.update { it.copy(isCompacting = false, compaction = checkpoint) }
                            saveCurrentChat(prior) // Persist the cache, not the empty streaming row.
                        }
                    ).collect { event ->
                        currentCoroutineContext().ensureActive()
                        check(completed == null) { "Unexpected data after the end of the model turn" }
                        when (event) {
                            is AiTurnEvent.Text -> {
                                draft.text.append(event.delta)
                                val now = SystemClock.uptimeMillis()
                                if (now - lastPaint >= 32L) {
                                    updateReply(row.id) { it.copy(content = draft.text.toString(), reasoning = draft.reasoning.toString()) }
                                    lastPaint = now
                                }
                            }
                            is AiTurnEvent.Reasoning -> {
                                draft.reasoning.append(event.delta)
                                val now = SystemClock.uptimeMillis()
                                if (now - lastPaint >= 32L) {
                                    updateReply(row.id) { it.copy(reasoning = draft.reasoning.toString()) }
                                    lastPaint = now
                                }
                            }
                            is AiTurnEvent.Completed -> completed = event
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    check(bound?.isCurrent() != false) { TARGET_CHANGED }
                    val end = checkNotNull(completed) { "Incomplete model response. No tool was run." }
                    val calls = end.calls
                    check(calls.all { it.id.isNotBlank() && seen.add(it.id) }) {
                        "Invalid or repeated tool call ID. No tool from this response was run."
                    }
                    check(calls.isEmpty() || tools.isNotEmpty()) { "Unexpected tool request in a text-only conversation" }
                    check(calls.isNotEmpty() || draft.text.isNotBlank() || draft.reasoning.isNotBlank()) { "AI returned an empty response" }
                    updateReply(row.id) { it.copy(
                        content = draft.text.toString(),
                        reasoning = draft.reasoning.toString(),
                        isStreaming = false,
                        toolCalls = calls,
                        responsesReasoning = end.responsesReasoning,
                        reasoningOrigin = if (end.responsesReasoning.isEmpty()) null else provider.reasoningOrigin(model)
                    ) }
                    live = null
                    if (calls.isEmpty()) break

                    val activeTarget = checkNotNull(bound)
                    var pause = false
                    for (call in calls) {
                        currentCoroutineContext().ensureActive()
                        check(activeTarget.isCurrent()) { TARGET_CHANGED }
                        val result = when {
                            pause -> call.copy(status = ToolCallStatus.Interrupted, output = "Not run: this turn was paused.")
                            !sessionAutoApprove && activeTarget.requiresApproval(call) -> {
                                val decision = awaitApproval(row.id, call)
                                if (decision.approved && decision.alwaysAllow) sessionAutoApprove = true
                                if (!decision.approved) {
                                    call.copy(status = ToolCallStatus.Denied, output = "Denied by user; not executed.")
                                } else {
                                    executeCall(activeTarget, row.id, call)
                                }
                            }
                            else -> executeCall(activeTarget, row.id, call)
                        }
                        currentCoroutineContext().ensureActive()
                        check(activeTarget.isCurrent()) { TARGET_CHANGED }
                        val limit = SettingsManager.aiOutputTruncateLimit.coerceAtLeast(1)
                        updateCall(row.id, if (result.output.length <= limit) result else result.copy(
                            output = result.output.take(limit) + "\n[Output truncated]"))
                        if (result.status in setOf(ToolCallStatus.Manual, ToolCallStatus.Interrupted)) pause = true
                    }
                    if (pause) break
                    val maxRounds = SettingsManager.aiMaxToolRounds
                    if (maxRounds > 0 && round >= maxRounds) {
                        error("Tool round limit ($maxRounds) reached; send a new message to continue.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (limit: AiContextLimitException) {
                _uiState.update { it.copy(error = context.getString(R.string.ai_chat_context_limit, limit.message)) }
            } catch (failure: Exception) {
                _uiState.update { it.copy(error = failure.message ?: "AI request failed") }
            } finally {
                // A stopped request can unwind after a new conversation has already started.
                if (generation === owner) finishRun()
            }
        }
        generation?.start()
    }

    private suspend fun executeCall(
        activeTarget: R2ActionExecutor.Target,
        messageId: String,
        call: AiToolCall
    ): AiToolCall {
        currentCoroutineContext().ensureActive()
        check(activeTarget.isCurrent()) { TARGET_CHANGED }
        val running = call.copy(status = ToolCallStatus.Running, startedAtMillis = System.currentTimeMillis())
        updateCall(messageId, running)
        return activeTarget.execute(running)
    }

    private suspend fun awaitApproval(messageId: String, call: AiToolCall): ApprovalResult {
        val waiting = Approval(messageId, call.id, CompletableDeferred())
        approval = waiting
        return try {
            updateCall(messageId, call.copy(status = ToolCallStatus.AwaitingApproval))
            saveCurrentChat()
            waiting.answer.await()
        } finally {
            if (approval === waiting) approval = null
        }
    }

    // Only the last row can be live. Replacing an item preserves both the Compose key and the wire ID.
    private inline fun updateReply(id: String, change: (ChatMessage) -> ChatMessage) {
        _uiState.update { state ->
            val row = state.messages.lastOrNull()
            if (row == null || row.id != id) state else state.copy(messages = state.messages.toMutableList().apply {
                this[lastIndex] = change(row)
            })
        }
    }

    private fun updateCall(messageId: String, call: AiToolCall) = updateReply(messageId) { row ->
        row.copy(toolCalls = row.toolCalls.map { if (it.id == call.id) call else it })
    }

    private fun stopGeneration(error: String? = null) {
        val job = generation ?: return
        generation = null
        job.cancel()
        finishRun(error)
    }

    private fun finishRun(error: String? = null) {
        generation = null
        target = null
        approval = null
        live?.let { draft -> updateReply(draft.id) { it.copy(content = draft.text.toString(), reasoning = draft.reasoning.toString()) } }
        live = null
        _uiState.update { state ->
            val last = state.messages.lastOrNull()
            val messages = when {
                last == null -> state.messages
                last.isStreaming && last.content.isBlank() && last.reasoning.isBlank() && last.toolCalls.isEmpty() -> state.messages.dropLast(1)
                else -> last.settledAfterInterruption().let { settled ->
                    if (last === settled) state.messages else state.messages.toMutableList().apply { this[lastIndex] = settled }
                }
            }
            state.copy(messages = messages, isGenerating = false, isCompacting = false, error = error ?: state.error)
        }
        saveCurrentChat()
    }

    private fun AiUiState.withHistory(rows: List<ChatMessage>) =
        copy(messages = rows, compaction = compaction?.takeIf { it.matchesPrefix(rows) })

    private fun editAndResend(id: String, text: String) {
        val state = _uiState.value
        if (state.isGenerating || text.isBlank()) return
        val index = state.messages.indexOfFirst { it.id == id && it.role == ChatRole.User }
        if (index < 0) return
        _uiState.update { it.withHistory(it.messages.take(index)) }
        sendMessage(text)
    }

    private fun regenerateFrom(id: String) {
        val state = _uiState.value
        if (state.isGenerating) return
        val index = state.messages.indexOfFirst { it.id == id && it.role == ChatRole.Assistant }
        if (index <= 0) return
        loadingChat?.cancel()
        _uiState.update { it.withHistory(it.messages.take(index)) }
        startGeneration()
    }

    private fun deleteMessage(id: String) {
        if (_uiState.value.isGenerating) return
        _uiState.update { state -> state.withHistory(state.messages.filterNot { it.id == id }) }
        saveCurrentChat()
    }

    private fun updateSystemPrompt(prompt: String) {
        AiSettingsManager.systemPrompt = prompt
        _uiState.update { it.copy(systemPrompt = prompt) }
    }

    private fun newChat() {
        loadingChat?.cancel()
        stopGeneration()
        sessionAutoApprove = false
        _uiState.update { it.copy(messages = emptyList(), compaction = null, error = null, currentChatId = null) }
    }

    // Snapshot before switching chats. FIFO lock admission keeps an older save from replacing a newer edit.
    private fun saveCurrentChat(messages: List<ChatMessage> = _uiState.value.messages) {
        val state = _uiState.value
        val id = state.currentChatId ?: return
        val title = messages.firstOrNull { it.role == ChatRole.User }?.content?.take(50) ?: "Chat"
        val session = ChatSession(id, title, System.currentTimeMillis(), messages, state.compaction)
        historyOperation(id) { withContext(Dispatchers.IO) { AiSettingsManager.saveSession(session) } }
    }

    private fun loadChat(id: String) {
        loadingChat?.cancel()
        stopGeneration()
        sessionAutoApprove = false
        loadingChat = historyOperation(id) {
            val session = withContext(Dispatchers.IO) { AiSettingsManager.loadSession(id) } ?: return@historyOperation
            _uiState.update { it.copy(messages = session.messages, compaction = session.compaction?.takeIf { cache ->
                cache.matchesPrefix(session.messages)
            }, currentChatId = id, error = null) }
        }
    }

    private fun deleteChat(id: String) {
        loadingChat?.cancel()
        if (_uiState.value.currentChatId == id) newChat()
        historyOperation(id) { withContext(Dispatchers.IO) { AiSettingsManager.deleteSession(id) } }
    }

    private fun historyOperation(id: String, block: suspend () -> Unit) = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            historyLock.withLock { block() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            if (_uiState.value.currentChatId == id) _uiState.update { it.copy(error = "Chat history: ${e.message}") }
        }
    }

    private companion object {
        const val TARGET_CHANGED = "The selected r2 session changed or disconnected. Send a new message to continue."
    }
}

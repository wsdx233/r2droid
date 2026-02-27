package top.wsdx233.r2droid.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.feature.ai.data.ActionType
import top.wsdx233.r2droid.feature.ai.data.AiProviderConfig
import top.wsdx233.r2droid.feature.ai.data.AiRepository
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.ai.data.AiSettingsManager
import top.wsdx233.r2droid.feature.ai.data.ChatMessage
import top.wsdx233.r2droid.feature.ai.data.ChatRole
import top.wsdx233.r2droid.feature.ai.data.ChatSession
import top.wsdx233.r2droid.feature.ai.data.R2ActionExecutor
import top.wsdx233.r2droid.feature.ai.data.ActionResult
import top.wsdx233.r2droid.feature.ai.data.ThinkingLevel
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject

sealed interface AiEvent {
    data class SendMessage(val text: String) : AiEvent
    data class EditMessage(val messageId: String, val newText: String) : AiEvent
    data class RegenerateFrom(val messageId: String) : AiEvent
    data class DeleteMessage(val messageId: String) : AiEvent
    object StopGeneration : AiEvent
    object NewChat : AiEvent
    data class SetProvider(val providerId: String, val modelName: String) : AiEvent
    data class SetModel(val modelName: String) : AiEvent
    data class UpdateSystemPrompt(val prompt: String) : AiEvent
    object ResetSystemPrompt : AiEvent
    object SaveChat : AiEvent
    data class LoadChat(val sessionId: String) : AiEvent
    data class DeleteChat(val sessionId: String) : AiEvent
    object ApproveCommand : AiEvent
    object DenyCommand : AiEvent
    data class SetThinkingLevel(val level: ThinkingLevel) : AiEvent
}

data class PendingApproval(
    val command: String,
    val type: ActionType
)

data class AiUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val error: String? = null,
    val config: AiProviderConfig = AiProviderConfig(),
    val systemPrompt: String = AiSettingsManager.DEFAULT_SYSTEM_PROMPT,
    val chatSessions: List<ChatSession> = emptyList(),
    val currentChatId: String? = null,
    val pendingApproval: PendingApproval? = null,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.Auto
)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private val actionExecutor = R2ActionExecutor()
    private var approvalDeferred: CompletableDeferred<Boolean>? = null

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            AiSettingsManager.configFlow.collect { config ->
                _uiState.update { it.copy(config = config) }
            }
        }
        viewModelScope.launch {
            AiSettingsManager.sessionsFlow.collect { sessions ->
                _uiState.update { it.copy(chatSessions = sessions) }
            }
        }
        _uiState.update { it.copy(systemPrompt = AiSettingsManager.systemPrompt) }
    }

    fun onEvent(event: AiEvent) {
        when (event) {
            is AiEvent.SendMessage -> sendMessage(event.text)
            is AiEvent.EditMessage -> editAndResend(event.messageId, event.newText)
            is AiEvent.RegenerateFrom -> regenerateFrom(event.messageId)
            is AiEvent.DeleteMessage -> deleteMessage(event.messageId)
            is AiEvent.StopGeneration -> stopGeneration()
            is AiEvent.NewChat -> newChat()
            is AiEvent.SetProvider -> setProvider(event.providerId, event.modelName)
            is AiEvent.SetModel -> setModel(event.modelName)
            is AiEvent.UpdateSystemPrompt -> updateSystemPrompt(event.prompt)
            is AiEvent.ResetSystemPrompt -> resetSystemPrompt()
            is AiEvent.SaveChat -> saveCurrentChat()
            is AiEvent.LoadChat -> loadChat(event.sessionId)
            is AiEvent.DeleteChat -> deleteChat(event.sessionId)
            is AiEvent.ApproveCommand -> resolveApproval(true)
            is AiEvent.DenyCommand -> resolveApproval(false)
            is AiEvent.SetThinkingLevel -> _uiState.update { it.copy(thinkingLevel = event.level) }
        }
    }

    private fun sendMessage(text: String) {
        val userMsg = ChatMessage(role = ChatRole.User, content = text)
        _uiState.update { it.copy(messages = it.messages + userMsg, error = null) }
        startGeneration()
    }

    private fun startGeneration() {
        val state = _uiState.value
        val config = state.config
        val provider = config.providers.find { it.id == config.activeProviderId }
        if (provider == null) {
            _uiState.update { it.copy(error = "No AI provider configured") }
            return
        }
        val model = config.activeModelName ?: provider.models.firstOrNull()
        if (model == null) {
            _uiState.update { it.copy(error = "No model selected") }
            return
        }

        aiRepository.configure(provider)
        _uiState.update { it.copy(isGenerating = true, streamingContent = "", error = null) }

        generationJob = viewModelScope.launch {
            try {
                val fullResponse = StringBuilder()

                aiRepository.streamChat(
                    messages = state.messages,
                    modelName = model,
                    systemPrompt = state.systemPrompt,
                    useResponsesApi = provider.useResponsesApi,
                    thinkingLevel = state.thinkingLevel
                )
                    .collect { delta ->
                        fullResponse.append(delta)
                        _uiState.update { it.copy(streamingContent = fullResponse.toString()) }
                    }

                val responseText = fullResponse.toString()
                val parsed = actionExecutor.parseResponse(responseText)

                if (parsed.actions.isNotEmpty()) {
                    // Execute actions
                    val results = mutableListOf<ActionResult>()
                    val feedbackBuilder = StringBuilder("Execution Results:\n")

                    for (action in parsed.actions) {
                        // Check if command requires user approval
                        if (action.type == ActionType.R2Command && AiSettingsManager.requiresApproval(action.content)) {
                            _uiState.update {
                                it.copy(
                                    pendingApproval = PendingApproval(action.content, action.type),
                                    streamingContent = responseText + "\n\n⚠️ Waiting for approval: [[${action.content}]]"
                                )
                            }
                            val deferred = CompletableDeferred<Boolean>()
                            approvalDeferred = deferred
                            val approved = deferred.await()
                            _uiState.update { it.copy(pendingApproval = null) }
                            if (!approved) {
                                val denied = ActionResult(
                                    type = action.type,
                                    input = action.content,
                                    output = "Command denied by user",
                                    success = false
                                )
                                results.add(denied)
                                feedbackBuilder.appendLine("R2 Command: ${action.content}")
                                feedbackBuilder.appendLine("Output:")
                                feedbackBuilder.appendLine("Command denied by user")
                                feedbackBuilder.appendLine()
                                continue
                            }
                        }

                        val result = when (action.type) {
                            ActionType.R2Command -> {
                                _uiState.update {
                                    it.copy(streamingContent = responseText + "\n\n⏳ Running: [[${action.content}]]...")
                                }
                                actionExecutor.executeR2Command(action.content)
                            }
                            ActionType.JavaScript -> {
                                _uiState.update {
                                    it.copy(streamingContent = "$responseText\n\n⏳ Running JavaScript...")
                                }
                                actionExecutor.executeJavaScript(action.content)
                            }
                        }
                        results.add(result)

                        val typeLabel = if (action.type == ActionType.R2Command) "R2 Command" else "JavaScript"
                        feedbackBuilder.appendLine("$typeLabel: ${action.content}")
                        feedbackBuilder.appendLine("Output:")
                        feedbackBuilder.appendLine(result.output)
                        feedbackBuilder.appendLine()
                    }

                    // Add assistant message with action results
                    val assistantMsg = ChatMessage(
                        role = ChatRole.Assistant,
                        content = responseText,
                        actionResults = results
                    )
                    _uiState.update {
                        it.copy(messages = it.messages + assistantMsg, streamingContent = "")
                    }

                    // If not complete, feed results back and continue
                    if (!parsed.isComplete && !parsed.hasAsk) {
                        var feedbackText = feedbackBuilder.toString()
                        val truncateLimit = SettingsManager.aiOutputTruncateLimit
                        if (feedbackText.length > truncateLimit) {
                            feedbackText = feedbackText.take(truncateLimit) + "\n... [Output Truncated] ..."
                        }
                        val feedbackMsg = ChatMessage(
                            role = ChatRole.ExecutionResult,
                            content = feedbackText
                        )
                        _uiState.update { it.copy(messages = it.messages + feedbackMsg) }
                        startGeneration()
                        return@launch
                    }
                } else {
                    // Plain text response
                    val assistantMsg = ChatMessage(
                        role = ChatRole.Assistant,
                        content = responseText
                    )
                    _uiState.update {
                        it.copy(messages = it.messages + assistantMsg, streamingContent = "")
                    }
                }

                _uiState.update { it.copy(isGenerating = false) }
                autoSaveChat()

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingContent = "",
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    private fun resolveApproval(approved: Boolean) {
        approvalDeferred?.complete(approved)
        approvalDeferred = null
    }

    private fun stopGeneration() {
        approvalDeferred?.complete(false)
        approvalDeferred = null
        generationJob?.cancel()
        val partial = _uiState.value.streamingContent
        if (partial.isNotBlank()) {
            val msg = ChatMessage(role = ChatRole.Assistant, content = partial)
            _uiState.update {
                it.copy(messages = it.messages + msg, isGenerating = false, streamingContent = "", pendingApproval = null)
            }
        } else {
            _uiState.update { it.copy(isGenerating = false, streamingContent = "", pendingApproval = null) }
        }
    }

    private fun editAndResend(messageId: String, newText: String) {
        val idx = _uiState.value.messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        stopGeneration()
        val truncated = _uiState.value.messages.take(idx)
        _uiState.update { it.copy(messages = truncated) }
        sendMessage(newText)
    }

    private fun regenerateFrom(messageId: String) {
        val idx = _uiState.value.messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        stopGeneration()
        val truncated = _uiState.value.messages.take(idx)
        _uiState.update { it.copy(messages = truncated) }
        startGeneration()
    }

    private fun deleteMessage(messageId: String) {
        _uiState.update { state ->
            state.copy(messages = state.messages.filter { it.id != messageId })
        }
    }

    private fun newChat() {
        // Auto-save current chat before starting new one
        autoSaveChat()
        stopGeneration()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                streamingContent = "",
                error = null,
                isGenerating = false,
                currentChatId = null
            )
        }
    }

    private fun setProvider(providerId: String, modelName: String) {
        AiSettingsManager.setActiveProvider(providerId, modelName)
        aiRepository.resetClient()
    }

    private fun setModel(modelName: String) {
        AiSettingsManager.setActiveModel(modelName)
    }

    private fun updateSystemPrompt(prompt: String) {
        AiSettingsManager.systemPrompt = prompt
        _uiState.update { it.copy(systemPrompt = prompt) }
    }

    private fun resetSystemPrompt() {
        updateSystemPrompt(AiSettingsManager.DEFAULT_SYSTEM_PROMPT)
    }

    // region Chat History

    private fun autoSaveChat() {
        val state = _uiState.value
        val userMessages = state.messages.filter { it.role == ChatRole.User }
        if (userMessages.isEmpty()) return

        val title = userMessages.first().content.take(50)
        val chatId = state.currentChatId ?: java.util.UUID.randomUUID().toString()
        val session = ChatSession(
            id = chatId,
            title = title,
            timestamp = System.currentTimeMillis(),
            messages = state.messages
        )
        AiSettingsManager.saveSession(session)
        _uiState.update { it.copy(currentChatId = chatId) }
    }

    private fun saveCurrentChat() {
        autoSaveChat()
    }

    private fun loadChat(sessionId: String) {
        val session = _uiState.value.chatSessions.find { it.id == sessionId } ?: return
        stopGeneration()
        _uiState.update {
            it.copy(
                messages = session.messages,
                currentChatId = session.id,
                streamingContent = "",
                error = null,
                isGenerating = false
            )
        }
    }

    private fun deleteChat(sessionId: String) {
        AiSettingsManager.deleteSession(sessionId)
        // If deleting the current chat, clear it
        if (_uiState.value.currentChatId == sessionId) {
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    currentChatId = null,
                    streamingContent = "",
                    error = null
                )
            }
        }
    }

    // endregion
}

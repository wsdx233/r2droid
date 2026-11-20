package top.wsdx233.r2droid.feature.ai.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AiProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<String>,
    val useResponsesApi: Boolean = false
)

internal fun AiProvider.reasoningOrigin(modelName: String) = "${id}\n${baseUrl.trimEnd('/')}\n$modelName"

@Serializable
data class AiProviderConfig(
    val providers: List<AiProvider> = emptyList(),
    val activeProviderId: String? = null,
    val activeModelName: String? = null
)

@Serializable
enum class ChatRole { User, Assistant, System, ExecutionResult }

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val actionResults: List<ActionResult> = emptyList(), // Historical, display-only actions.
    val toolCalls: List<AiToolCall> = emptyList(),
    val reasoning: String = "",
    val responsesReasoning: List<String> = emptyList(),
    val reasoningOrigin: String? = null
)

@Serializable
data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val status: ToolCallStatus = ToolCallStatus.Queued,
    val output: String = "",
    val startedAtMillis: Long? = null,
    val durationMillis: Long? = null
)

@Serializable
enum class ToolCallStatus {
    Queued, AwaitingApproval, Running, Succeeded, Failed, Denied, Interrupted, Manual;

    val isTerminal: Boolean get() = this != Queued && this != AwaitingApproval && this != Running
}

data class AiToolDefinition(val name: String, val description: String, val parameters: org.json.JSONObject)

sealed interface AiTurnEvent {
    data class Text(val delta: String) : AiTurnEvent
    data class Reasoning(val delta: String) : AiTurnEvent
    data class Completed(val calls: List<AiToolCall>, val responsesReasoning: List<String> = emptyList()) : AiTurnEvent
}

/** A transcript is not an execution queue. Stopped or restored calls can never regain approval. */
internal fun ChatMessage.settledAfterInterruption(): ChatMessage {
    if (!isStreaming && toolCalls.all { it.status.isTerminal }) return this
    return copy(isStreaming = false, toolCalls = toolCalls.map { call ->
        if (call.status.isTerminal) call else call.copy(status = ToolCallStatus.Interrupted,
            output = "Interrupted; no result was confirmed. This call will not be replayed.")
    })
}

@Serializable
data class ActionResult(
    val type: ActionType,
    val input: String,
    val output: String,
    val success: Boolean
)

@Serializable
enum class ActionType { R2Command, JavaScript, FridaScript }

@Serializable
enum class AiExecutionMode { R2, Frida }

enum class ThinkingLevel(val labelResKey: String, val apiEffort: String?) {
    None("ai_thinking_none", null),
    Auto("ai_thinking_auto", null),
    Light("ai_thinking_light", "low"),
    Normal("ai_thinking_normal", "medium"),
    Heavy("ai_thinking_heavy", "high");
}

@Serializable
data class ChatSessionMetadata(
    val id: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** A cached summary of a transcript prefix, not a replacement for the original messages. */
@Serializable
data class ChatCompaction(
    val origin: String,
    val coveredMessages: Int,
    val lastMessageId: String,
    val summary: String
)

@Serializable
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val compaction: ChatCompaction? = null
)

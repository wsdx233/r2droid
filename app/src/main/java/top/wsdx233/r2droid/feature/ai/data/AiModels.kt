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
    val actionResults: List<ActionResult> = emptyList()
)

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

@Serializable
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
)

package top.wsdx233.r2droid.feature.ai.data

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage as ApiChatMessage
import com.aallam.openai.api.chat.ChatRole as ApiChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor() {

    private var openAI: OpenAI? = null
    private var currentProviderId: String? = null

    fun configure(provider: AiProvider) {
        if (currentProviderId == provider.id && openAI != null) return
        currentProviderId = provider.id
        openAI = OpenAI(
            OpenAIConfig(
                token = provider.apiKey,
                host = OpenAIHost(baseUrl = provider.baseUrl.trimEnd('/') + "/")
            )
        )
    }

    fun streamChat(
        messages: List<ChatMessage>,
        modelName: String,
        systemPrompt: String
    ): Flow<String> {
        val client = openAI ?: throw IllegalStateException("AI provider not configured")

        val apiMessages = buildList {
            add(ApiChatMessage(role = ApiChatRole.System, content = systemPrompt))
            for (msg in messages) {
                val role = when (msg.role) {
                    ChatRole.User, ChatRole.ExecutionResult -> ApiChatRole.User
                    ChatRole.Assistant -> ApiChatRole.Assistant
                    ChatRole.System -> ApiChatRole.System
                }
                add(ApiChatMessage(role = role, content = msg.content))
            }
        }

        val request = ChatCompletionRequest(
            model = ModelId(modelName),
            messages = apiMessages
        )

        return client.chatCompletions(request).mapNotNull { chunk ->
            chunk.choices.firstOrNull()?.delta?.content
        }
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> {
        val client = OpenAI(
            OpenAIConfig(
                token = apiKey,
                host = OpenAIHost(baseUrl = baseUrl.trimEnd('/') + "/")
            )
        )
        return client.models().map { it.id.id }.sorted()
    }

    fun resetClient() {
        openAI = null
        currentProviderId = null
    }
}

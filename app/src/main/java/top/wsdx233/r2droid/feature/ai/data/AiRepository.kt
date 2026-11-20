package top.wsdx233.r2droid.feature.ai.data

import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The server rejected the request before generation because its input exceeded the model's context window. */
internal class AiContextLimitException(message: String) : IOException(message)

@Singleton
class AiRepository @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun streamText(
        provider: AiProvider,
        messages: List<ChatMessage>,
        modelName: String,
        systemPrompt: String,
        thinkingLevel: ThinkingLevel = ThinkingLevel.Auto
    ): Flow<String> = streamTurn(provider, messages, modelName, systemPrompt, thinkingLevel, emptyList())
        .transform { if (it is AiTurnEvent.Text) emit(it.delta) }

    fun streamTurn(
        provider: AiProvider,
        messages: List<ChatMessage>,
        modelName: String,
        systemPrompt: String,
        thinkingLevel: ThinkingLevel,
        tools: List<AiToolDefinition>
    ): Flow<AiTurnEvent> = flow {
        var currentTools = tools
        var includeReasoning = true
        var includeSummary = true
        while (true) {
            try {
                streamOnce(
                    provider = provider,
                    messages = messages,
                    modelName = modelName,
                    systemPrompt = systemPrompt,
                    thinkingLevel = thinkingLevel,
                    tools = currentTools,
                    includeReasoningParams = includeReasoning,
                    includeReasoningSummary = includeSummary
                ) { emit(it) }
                break
            } catch (error: AiHttpException) {
                // Only a rejected HTTP request reaches this branch; a 2xx stream is never retried.
                when {
                    includeSummary && provider.useResponsesApi && thinkingLevel != ThinkingLevel.None && error.reasoningSummaryUnsupported() ->
                        includeSummary = false
                    includeReasoning && thinkingLevel != ThinkingLevel.None && error.reasoningUnsupported() -> {
                        includeReasoning = false
                        includeSummary = false
                    }
                    currentTools.isNotEmpty() && error.toolsUnsupported() ->
                        currentTools = emptyList()
                    else -> throw error
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun streamOnce(
        provider: AiProvider,
        messages: List<ChatMessage>,
        modelName: String,
        systemPrompt: String,
        thinkingLevel: ThinkingLevel,
        tools: List<AiToolDefinition>,
        includeReasoningParams: Boolean,
        includeReasoningSummary: Boolean,
        emit: suspend (AiTurnEvent) -> Unit
    ): Unit = coroutineScope {
        val endpoint = if (provider.useResponsesApi) "/responses" else "/chat/completions"
        val payload = AiRequestWire.build(
            provider = provider,
            messages = messages,
            modelName = modelName,
            systemPrompt = systemPrompt,
            thinkingLevel = thinkingLevel,
            tools = tools,
            includeReasoningParams = includeReasoningParams,
            includeReasoningSummary = includeReasoningSummary
        )
        val request = Request.Builder().url(provider.baseUrl.trimEnd('/') + endpoint)
            .header("Authorization", "Bearer ${provider.apiKey.trim()}")
            .header("Accept", "text/event-stream")
            .post(payload.body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = client.newCall(request)
        // Cancel the actual socket both before headers and while blocked in a body read.
        val cancelCall = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            ensureActive()
            call.execute().use { response ->
                ensureActive()
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    val diagnostic = "AI API failed (${response.code}): $body"
                    if (isContextLimitRejection(response.code, body)) throw AiContextLimitException(diagnostic)
                    throw AiHttpException(response.code, body, diagnostic)
                }
                val text = response.body.charStream().buffered()
                val events = AiSseReader(text)
                val names = tools.mapTo(mutableSetOf()) { it.name }
                val parser = if (provider.useResponsesApi) AiResponsesTurnWire(names) else AiChatTurnWire(names)
                while (true) {
                    ensureActive()
                    val event = events.next() ?: throw AiWireException("AI stream disconnected before a complete turn")
                    ensureActive()
                    val completed = parser.accept(
                        event = event,
                        emitText = { emit(AiTurnEvent.Text(it)) },
                        emitReasoning = { emit(AiTurnEvent.Reasoning(it)) }
                    )
                    if (completed != null) {
                        wireCheck(completed.calls.none { it.id in payload.callIds }, "Duplicate historical tool call ID")
                        ensureActive()
                        emit(completed)
                        break
                    }
                }
            }
        } catch (error: IOException) {
            ensureActive() // A cancelled socket read must surface as cancellation, not as a network error.
            throw error
        } finally {
            cancelCall.cancel()
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

    private fun isContextLimitRejection(status: Int, body: String): Boolean {
        if (status != 400 && status != 413 && status != 422) return false
        val json = runCatching { JSONObject(body) }.getOrNull()
        val error = json?.optJSONObject("error") ?: json
        // An output-token setting (or any unrelated parameter) is not the input window.
        if (error != null && !error.isNull("param") && error.optString("param") !in CONTEXT_INPUT_PARAMS) return false

        val code = (error?.opt("code") as? String).orEmpty().lowercase()
        val type = (error?.opt("type") as? String).orEmpty().lowercase()
        val explicitCode = code in CONTEXT_LIMIT_CODES || type in CONTEXT_LIMIT_CODES
        if ((code !in CONTEXT_LIMIT_CODES && code !in GENERIC_REQUEST_ERRORS) ||
            (type !in CONTEXT_LIMIT_CODES && type !in GENERIC_REQUEST_ERRORS)
        ) return false

        val message = ((error?.opt("message") as? String) ?: (json?.opt("error") as? String)
            ?: if (json == null) body else "").trim()
        if (INPUT_CONTEXT_MESSAGES.any { it.containsMatchIn(message) }) return true
        LEGACY_CONTEXT_MESSAGE.find(message)?.let { match ->
            // Some servers report the requested input + completion budget. Only the input count matters.
            val limit = match.groupValues[1].toLongOrNull() ?: return false
            val input = (match.groups[2] ?: match.groups[3])?.value?.toLongOrNull() ?: return false
            return limit > 0 && input >= limit
        }
        return explicitCode && !OUTPUT_BUDGET_MENTION.containsMatchIn(message)
    }

    private class AiHttpException(val code: Int, private val body: String, diagnostic: String) : IOException(diagnostic) {
        fun toolsUnsupported(): Boolean {
            if (code != 400 && code != 422) return false
            val json = runCatching { JSONObject(body) }.getOrNull()
            val error = json?.optJSONObject("error") ?: json
            val param = error?.optString("param").orEmpty().takeUnless { it.isBlank() || it == "null" }
            if (param != null && param != "tools") return false
            if (param == "tools" && error?.optString("code").orEmpty() in UNSUPPORTED_PARAMETER_CODES) return true
            val message = error?.optString("message")?.takeIf(String::isNotBlank) ?: body
            return TOOLS_UNSUPPORTED.any { it.containsMatchIn(message) }
        }
        fun reasoningSummaryUnsupported(): Boolean {
            if (code != 400 && code != 422) return false
            val json = runCatching { JSONObject(body) }.getOrNull()
            val error = json?.optJSONObject("error") ?: json
            val param = error?.optString("param").orEmpty().takeUnless { it.isBlank() || it == "null" }
            if (param == "reasoning.summary") return true
            val message = error?.optString("message")?.takeIf(String::isNotBlank) ?: body
            return REASONING_SUMMARY_UNSUPPORTED.any { it.containsMatchIn(message) }
        }

        fun reasoningUnsupported(): Boolean {
            if (code != 400 && code != 422) return false
            val json = runCatching { JSONObject(body) }.getOrNull()
            val error = json?.optJSONObject("error") ?: json
            val param = error?.optString("param").orEmpty().takeUnless { it.isBlank() || it == "null" }
            if (param != null && param != "reasoning" && param != "reasoning_effort" && !param.startsWith("reasoning.")) return false
            if (param in setOf("reasoning", "reasoning_effort") && error?.optString("code").orEmpty() in UNSUPPORTED_PARAMETER_CODES) return true
            val message = error?.optString("message")?.takeIf(String::isNotBlank) ?: body
            return REASONING_UNSUPPORTED.any { it.containsMatchIn(message) }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val UNSUPPORTED_PARAMETER_CODES = setOf("unsupported_parameter", "unknown_parameter", "unrecognized_parameter")
        val CONTEXT_INPUT_PARAMS = setOf("", "input", "messages", "prompt")
        val CONTEXT_LIMIT_CODES = setOf(
            "context_length_exceeded", "context_window_exceeded",
            "input_context_length_exceeded", "input_context_window_exceeded"
        )
        val GENERIC_REQUEST_ERRORS = setOf(
            "", "invalid_request_error", "invalid_request", "invalid_argument", "bad_request", "badrequesterror",
            "invalid_value", "validation_error"
        )
        val INPUT_CONTEXT_MESSAGES = listOf(
            """^(?:Your |The |This )?(?:input(?: tokens)?|prompt|messages) exceeds? (?:(?:the |this )?model's |the )?(?:maximum )?context (?:window|length)(?: of this model)?(?=[.!]|$)""",
            """^(?:Your |The |This )?(?:input|prompt) is too long for (?:this|the model's|the) context window(?=[.!]|$)""",
            """^Prompt is too long: \d+ tokens > \d+ maximum(?=[.!]|$)"""
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
        val LEGACY_CONTEXT_MESSAGE = Regex(
            """^This model's maximum context length is (\d+) tokens[.,]\s*however,? (?:""" +
                """your (?:input|prompt|messages) resulted in (\d+) tokens\b|""" +
                """you requested \d+ tokens\b(?: \((\d+) in (?:the|your) (?:input|prompt|messages)[,;)])?)""",
            RegexOption.IGNORE_CASE
        )
        val OUTPUT_BUDGET_MENTION = Regex(
            """\b(?:max_(?:output_|completion_)?tokens|(?:output|completion) (?:tokens|length|budget))\b""",
            RegexOption.IGNORE_CASE
        )
        val TOOLS_UNSUPPORTED = listOf(
            """\b(?:does not support|do not support|doesn't support|don't support)\s+(?:the\s+)?["'`]?(?:tools|tool (?:use|calling)|function calling)\b(?!\[|\.\w)""",
            """\b(?:tools|tool (?:use|calling)|function calling)["'`]?\s+(?:(?:are|is)\s+)?(?:not supported|unsupported)\b""",
            """\b(?:unsupported|unknown|unrecognized)\s+(?:request\s+)?(?:parameter|argument|field)s?(?:\s+supplied)?\s*[:=]?\s*["'`]?tools\b(?!\[|\.\w)"""
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
        val REASONING_SUMMARY_UNSUPPORTED = listOf(
            """reasoning\.summary""",
            """\bsummary\b.*\b(?:not supported|unsupported|must be verified|organization must be verified|invalid)\b"""
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
        val REASONING_UNSUPPORTED = listOf(
            """\b(?:reasoning|reasoning_effort)\b.*\b(?:not supported|unsupported|unknown|unrecognized|not allowed|invalid parameter)\b""",
            """\b(?:unsupported|unknown|unrecognized)\s+(?:request\s+)?(?:parameter|argument|field)s?(?:\s+supplied)?\s*[:=]?\s*["'`]?(?:reasoning|reasoning_effort)\b""",
            """\b(?:does not support|doesn't support)\s+(?:the\s+)?["'`]?(?:reasoning|reasoning_effort)\b"""
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
    }
}

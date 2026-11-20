package top.wsdx233.r2droid.feature.ai.data

import org.json.JSONArray
import org.json.JSONObject

internal object AiRequestWire {
    data class Request(val body: String, val callIds: Set<String>)

    fun build(
        provider: AiProvider,
        messages: List<ChatMessage>,
        modelName: String,
        systemPrompt: String,
        thinkingLevel: ThinkingLevel,
        tools: List<AiToolDefinition>,
        includeReasoningParams: Boolean = true,
        includeReasoningSummary: Boolean = true
    ): Request {
        val responses = provider.useResponsesApi
        val reasoningOrigin = if (responses && tools.isNotEmpty()) provider.reasoningOrigin(modelName) else null
        val history = JSONArray().put(message("system", systemPrompt))
        val callIds = mutableSetOf<String>()
        for (message in messages) {
            when (message.role) {
                ChatRole.User -> history.put(message("user", message.content))
                ChatRole.System -> history.put(message("system", message.content))
                ChatRole.ExecutionResult -> Unit // Legacy display data, not a user message.
                ChatRole.Assistant -> {
                    if (reasoningOrigin != null && message.reasoningOrigin == reasoningOrigin) {
                        require(message.responsesReasoning.size <= MAX_REASONING_ITEMS) { "Too many reasoning items" }
                        message.responsesReasoning.forEach { raw ->
                            require(raw.length <= MAX_REASONING_ITEM_LENGTH) { "Reasoning item is too large" }
                            val reasoning = parseWireObject(raw, "reasoning item")
                            require(reasoning.wireString("type") == "reasoning") { "Invalid reasoning item" }
                            history.put(JSONObject(reasoning.toString()))
                        }
                    }
                    // Never put a dangling assistant tool call on the wire, including on text-only retry.
                    val calls = message.toolCalls.takeIf { calls ->
                        tools.isNotEmpty() && calls.all { it.status.isTerminal }
                    }.orEmpty()
                    calls.forEach { call ->
                        require(call.id.isNotBlank() && callIds.add(call.id)) { "Duplicate or empty historical call ID" }
                        require(call.name.isNotBlank()) { "Empty historical tool name" }
                        validateToolArguments(call.arguments)
                    }
                    if (responses) addResponsesAssistant(history, message.content, calls)
                    else addChatAssistant(history, message.content, calls)
                }
            }
        }
        val body = JSONObject().put("model", modelName).put("stream", true).apply {
            put(if (responses) "input" else "messages", history)
            if (responses) {
                if (includeReasoningParams && thinkingLevel != ThinkingLevel.None) {
                    val reasoning = JSONObject()
                    thinkingLevel.apiEffort?.let { reasoning.put("effort", it) }
                    if (includeReasoningSummary) reasoning.put("summary", "auto")
                    if (reasoning.length() > 0) put("reasoning", reasoning)
                }
                if (tools.isNotEmpty()) put("include", JSONArray().put("reasoning.encrypted_content"))
            } else if (includeReasoningParams) {
                thinkingLevel.apiEffort?.let { put("reasoning_effort", it) }
            }
            if (tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    tools.forEach { tool ->
                        val function = JSONObject().put("name", tool.name)
                            .put("description", tool.description).put("parameters", tool.parameters)
                        put(if (responses) function.put("type", "function").put("strict", false) else chatFunction(function))
                    }
                })
            }
        }.toString()
        return Request(body, callIds)
    }

    private fun addChatAssistant(history: JSONArray, content: String, calls: List<AiToolCall>) {
        if (content.isEmpty() && calls.isEmpty()) return
        val assistant = JSONObject().put("role", "assistant").put("content", content.ifEmpty { null } ?: JSONObject.NULL)
        if (calls.isNotEmpty()) {
            assistant.put("tool_calls", JSONArray().apply {
                calls.forEach { call ->
                    put(chatFunction(functionCall(call)).put("id", call.id))
                }
            })
        }
        history.put(assistant)
        calls.forEach { call -> history.put(message("tool", call.output).put("tool_call_id", call.id)) }
    }

    private fun addResponsesAssistant(history: JSONArray, content: String, calls: List<AiToolCall>) {
        if (content.isNotEmpty()) history.put(message("assistant", content))
        calls.forEach { call ->
            history.put(functionCall(call).put("type", "function_call").put("call_id", call.id))
        }
        calls.forEach { call ->
            history.put(JSONObject().put("type", "function_call_output").put("call_id", call.id).put("output", call.output))
        }
    }

    private fun message(role: String, content: String) = JSONObject().put("role", role).put("content", content)

    private fun functionCall(call: AiToolCall) = JSONObject().put("name", call.name).put("arguments", call.arguments)

    private fun chatFunction(function: JSONObject) = JSONObject().put("type", "function").put("function", function)
}

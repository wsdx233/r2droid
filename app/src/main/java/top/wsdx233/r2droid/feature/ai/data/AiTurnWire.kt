package top.wsdx233.r2droid.feature.ai.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.BufferedReader
import java.io.IOException

internal const val MAX_REASONING_ITEMS = 32
internal const val MAX_REASONING_ITEM_LENGTH = 128 * 1024

internal class AiWireException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal data class AiSseEvent(val type: String?, val data: String)

internal class AiSseReader(private val reader: BufferedReader) {
    private var firstLine = true

    fun next(): AiSseEvent? {
        val data = StringBuilder()
        var hasData = false
        var type: String? = null
        while (true) {
            // SSE dispatches on a blank line, not at EOF. An interrupted frame is never a terminal event.
            var line = reader.readLine() ?: return null
            if (firstLine) {
                line = line.removePrefix("\uFEFF")
                firstLine = false
            }
            if (line.isEmpty()) {
                if (hasData) return AiSseEvent(type, data.toString())
                type = null
                continue
            }
            val colon = line.indexOf(':')
            val field = if (colon == -1) line else line.substring(0, colon)
            val value = if (colon == -1) "" else line.substring(colon + 1).removePrefix(" ")
            when (field) {
                "data" -> {
                    if (hasData) data.append('\n')
                    data.append(value)
                    hasData = true
                }
                "event" -> type = value.ifEmpty { null }
            }
        }
    }
}

internal interface AiTurnWire {
    suspend fun accept(
        event: AiSseEvent,
        emitText: suspend (String) -> Unit,
        emitReasoning: suspend (String) -> Unit = {}
    ): AiTurnEvent.Completed?
}

internal class AiChatTurnWire(private val tools: Set<String>) : AiTurnWire {
    private class Call {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    private val calls = sortedMapOf<Int, Call>()
    private var finish: String? = null

    override suspend fun accept(
        event: AiSseEvent,
        emitText: suspend (String) -> Unit,
        emitReasoning: suspend (String) -> Unit
    ): AiTurnEvent.Completed? {
        if (event.type == "error") throw AiWireException("Chat stream reported an error")
        if (event.data == "[DONE]") {
            val reason = finish ?: throw AiWireException("Chat stream ended without a finish reason")
            return when {
                reason == "stop" && calls.isEmpty() -> AiTurnEvent.Completed(emptyList())
                reason == "tool_calls" && calls.isNotEmpty() -> {
                    val result = calls.entries.mapIndexed { index, (actualIndex, call) ->
                        wireCheck(index == actualIndex, "Missing tool call index")
                        completedCall(call.id, call.name, call.arguments.toString(), tools)
                    }
                    AiTurnEvent.Completed(requireUniqueCalls(result))
                }
                else -> throw AiWireException("Unsuccessful or inconsistent Chat finish reason: $reason")
            }
        }
        if (event.data.isBlank()) return null
        val body = parseWireObject(event.data, "Chat event")
        if (body["error"].let { it != null && it != JsonNull }) throw AiWireException("Chat stream reported an error")
        val choices = body.wireArray("choices") ?: throw AiWireException("Missing Chat choices")
        if (choices.isEmpty()) return null // Optional usage chunk, even after finish_reason.
        wireCheck(choices.size == 1 && finish == null, "Unexpected Chat choice after finish or multiple choices")
        val choice = choices.single().wireObject("Chat choice")
        wireCheck((choice.wireIndex("index") ?: 0) == 0, "Unexpected Chat choice index")
        val delta = choice.wireObjectOrNull("delta")
        if (delta != null) {
            wireCheck(delta["function_call"] == null || delta["function_call"] == JsonNull, "Legacy function_call is not supported")
            val seen = mutableSetOf<Int>()
            delta.wireArray("tool_calls")?.forEach { entry ->
                val tool = entry.wireObject("tool call delta")
                val index = tool.wireIndex("index") ?: throw AiWireException("Missing tool call index")
                wireCheck(seen.add(index), "Repeated tool call index in Chat chunk")
                wireCheck(tools.isNotEmpty(), "Unexpected tool call in a text-only turn")
                val type = tool.wireString("type")
                wireCheck(type == null || type == "function", "Unsupported Chat tool type")
                val call = calls.getOrPut(index) { Call() }
                call.id = agree(call.id, tool.wireString("id"), "tool call ID")
                tool.wireObjectOrNull("function")?.let { function ->
                    call.name = agree(call.name, function.wireString("name"), "tool name")
                    function.wireString("arguments")?.let(call.arguments::append)
                }
            }
            val reasoning = delta.wireStringOrNull("reasoning_content")
                ?: delta.wireStringOrNull("reasoning")
                ?: delta.wireStringOrNull("thinking")
            reasoning?.takeIf(String::isNotEmpty)?.let { emitReasoning(it) }
            delta.wireString("content")?.takeIf(String::isNotEmpty)?.let { emitText(it) }
        }
        finish = choice.wireString("finish_reason")
        return null
    }
}

internal fun parseWireObject(raw: String, label: String): JsonObject = try {
    Json.parseToJsonElement(raw).wireObject(label)
} catch (error: SerializationException) {
    throw AiWireException("Invalid JSON for $label", error)
}

internal fun JsonElement.wireObject(label: String): JsonObject =
    this as? JsonObject ?: throw AiWireException("Expected an object for $label")

internal fun JsonObject.wireObjectOrNull(field: String): JsonObject? =
    this[field]?.takeUnless { it == JsonNull }?.wireObject(field)

internal fun JsonObject.wireString(field: String): String? {
    val value = this[field]?.takeUnless { it == JsonNull } ?: return null
    return (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw AiWireException("Expected a string for $field")
}

internal fun JsonObject.wireStringOrNull(field: String): String? {
    val value = this[field]?.takeUnless { it == JsonNull } ?: return null
    return (value as? JsonPrimitive)?.takeIf { it.isString }?.content
}

internal fun JsonObject.wireArray(field: String): JsonArray? {
    val value = this[field]?.takeUnless { it == JsonNull } ?: return null
    return value as? JsonArray ?: throw AiWireException("Expected an array for $field")
}

internal fun JsonObject.wireIndex(field: String): Int? {
    val value = this[field]?.takeUnless { it == JsonNull } ?: return null
    return (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it >= 0 }
        ?: throw AiWireException("Invalid index for $field")
}

internal fun wireCheck(condition: Boolean, message: String) {
    if (!condition) throw AiWireException(message)
}

internal fun agree(old: String?, new: String?, label: String): String? {
    wireCheck(old == null || new == null || old == new, "Conflicting $label")
    return new ?: old
}

private val jsonNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

private fun JsonElement.isValidJson(): Boolean = when (this) {
    is JsonObject -> values.all { it.isValidJson() }
    is JsonArray -> all { it.isValidJson() }
    is JsonPrimitive -> isString || this == JsonNull || content == "true" || content == "false" || jsonNumber.matches(content)
}

internal fun validateToolArguments(arguments: String) {
    // The tree decoder also represents unquoted, non-JSON values as primitives.
    wireCheck(parseWireObject(arguments, "tool arguments").isValidJson(), "Invalid JSON tool arguments")
}

internal fun completedCall(id: String?, name: String?, arguments: String?, tools: Set<String>): AiToolCall {
    if (id.isNullOrBlank() || name.isNullOrBlank() || arguments == null) throw AiWireException("Incomplete tool call")
    wireCheck(name in tools, "Tool call names an unadvertised function")
    validateToolArguments(arguments)
    return AiToolCall(id = id, name = name, arguments = arguments)
}

internal fun requireUniqueCalls(calls: List<AiToolCall>): List<AiToolCall> {
    val ids = mutableSetOf<String>()
    wireCheck(calls.all { ids.add(it.id) }, "Duplicate tool call ID")
    return calls
}

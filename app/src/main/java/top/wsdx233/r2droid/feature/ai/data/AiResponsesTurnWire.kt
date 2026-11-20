package top.wsdx233.r2droid.feature.ai.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

internal class AiResponsesTurnWire(private val tools: Set<String>) : AiTurnWire {
    private class OutputItem(val type: String) {
        var id: String? = null
        var added = false
        var done = false
        val call = if (type == "function_call") FunctionCall() else null
        val text = mutableMapOf<Int, StringBuilder>()
        var reasoning: JsonObject? = null
    }

    private class FunctionCall {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
        var finalArguments: String? = null

        fun snapshot(value: String?, final: Boolean) {
            if (value == null) return
            val confirmed = finalArguments
            wireCheck(confirmed == null || confirmed == value, "Conflicting final function arguments")
            wireCheck(value.startsWith(arguments), "Function argument snapshot conflicts with streamed arguments")
            arguments.setLength(0)
            arguments.append(value)
            if (final) finalArguments = value
        }
    }

    private val output = sortedMapOf<Int, OutputItem>()
    private val itemIds = mutableMapOf<String, Int>()
    private val reasoningBuffers = linkedMapOf<String, StringBuilder>()
    private var hasEmittedReasoning = false

    override suspend fun accept(
        event: AiSseEvent,
        emitText: suspend (String) -> Unit,
        emitReasoning: suspend (String) -> Unit
    ): AiTurnEvent.Completed? {
        if (event.data == "[DONE]") throw AiWireException("Responses stream ended without response.completed")
        val eventType = event.type?.takeUnless { it == "message" }
        if (event.data.isBlank() && eventType == null) return null
        val body = parseWireObject(event.data, "Responses event")
        val type = agree(body.wireString("type"), eventType, "Responses event type")
            ?: throw AiWireException("Missing Responses event type")
        when (type) {
            "error", "response.failed", "response.incomplete", "response.cancelled", "response.canceled" ->
                throw AiWireException("Unsuccessful Responses stream: $type")
            "response.output_item.added", "response.output_item.done" -> {
                val item = body.wireObjectOrNull("item") ?: throw AiWireException("Missing Responses output item")
                val index = body.wireIndex("output_index") ?: throw AiWireException("Missing Responses output index")
                snapshot(index, item, if (type == "response.output_item.added") Snapshot.Added else Snapshot.Done, emitText, emitReasoning)
            }
            "response.function_call_arguments.delta", "response.function_call_arguments.done" -> {
                val item = locate(body, "function_call")
                val call = checkNotNull(item.call)
                wireCheck(!item.done && call.finalArguments == null, "Function arguments received after done")
                call.id = agree(call.id, body.wireString("call_id"), "function call ID")
                call.name = agree(call.name, body.wireString("name"), "function name")
                if (type == "response.function_call_arguments.delta") {
                    call.arguments.append(body.requiredString("delta"))
                } else {
                    call.snapshot(body.requiredString("arguments"), final = true)
                }
            }
            "response.output_text.delta", "response.output_text.done" -> {
                val item = locate(body, "message")
                wireCheck(!item.done, "Text received after output item done")
                val contentIndex = body.wireIndex("content_index") ?: 0
                if (type == "response.output_text.delta") {
                    val delta = body.requiredString("delta")
                    item.text.getOrPut(contentIndex) { StringBuilder() }.append(delta)
                    if (delta.isNotEmpty()) emitText(delta)
                } else {
                    textSnapshot(item, contentIndex, body.requiredString("text"), emitText)
                }
            }
            "response.content_part.added", "response.content_part.done" -> {
                val part = body.wireObjectOrNull("part") ?: throw AiWireException("Missing Responses content part")
                if (part.wireString("type") == "output_text") {
                    val item = locate(body, "message")
                    wireCheck(!item.done, "Content part received after output item done")
                    textSnapshot(item, body.wireIndex("content_index") ?: 0, part.requiredString("text"), emitText)
                }
            }
            "response.reasoning_summary_text.delta" -> {
                val key = reasoningKey(body, "summary", body.wireIndex("summary_index") ?: 0)
                body.wireStringOrNull("delta")?.let { emitReasoningDelta(key, it, emitReasoning) }
            }
            "response.reasoning_summary_text.done" -> {
                val key = reasoningKey(body, "summary", body.wireIndex("summary_index") ?: 0)
                body.wireStringOrNull("text")?.let { reconcileReasoning(key, it, emitReasoning) }
            }
            "response.reasoning_summary_part.done" -> {
                val key = reasoningKey(body, "summary", body.wireIndex("summary_index") ?: 0)
                val part = body.wireObjectOrNull("part")
                part?.wireStringOrNull("text")?.let { reconcileReasoning(key, it, emitReasoning) }
            }
            "response.reasoning_text.delta" -> {
                val key = reasoningKey(body, "content", body.wireIndex("content_index") ?: 0)
                body.wireStringOrNull("delta")?.let { emitReasoningDelta(key, it, emitReasoning) }
            }
            "response.reasoning_text.done" -> {
                val key = reasoningKey(body, "content", body.wireIndex("content_index") ?: 0)
                body.wireStringOrNull("text")?.let { reconcileReasoning(key, it, emitReasoning) }
            }
            "response.completed" -> {
                val response = body.wireObjectOrNull("response") ?: throw AiWireException("Missing completed response")
                val status = response.wireString("status")
                wireCheck(status == null || status == "completed", "Unsuccessful Responses status: $status")
                wireCheck(response["error"].let { it == null || it == JsonNull } &&
                    response["incomplete_details"].let { it == null || it == JsonNull }, "Responses turn did not complete")
                val items = response.wireArray("output")
                items?.forEachIndexed { index, item -> snapshot(index, item.wireObject("completed item"), Snapshot.Completed, emitText, emitReasoning) }
                val calls = output.entries.mapNotNull { (index, item) ->
                    val call = item.call ?: return@mapNotNull null
                    wireCheck(items == null || index < items.size, "Completed response omitted a streamed function call")
                    completedCall(call.id, call.name, call.finalArguments, tools)
                }
                val reasoning = output.values.mapNotNull { item -> item.reasoning?.takeIf { item.done }?.toString() }
                wireCheck(reasoning.size <= MAX_REASONING_ITEMS, "Too many reasoning items")
                return AiTurnEvent.Completed(requireUniqueCalls(calls), reasoning)
            }
        }
        return null
    }

    private enum class Snapshot { Added, Done, Completed }

    private suspend fun snapshot(
        index: Int,
        raw: JsonObject,
        phase: Snapshot,
        emitText: suspend (String) -> Unit,
        emitReasoning: suspend (String) -> Unit
    ) {
        val item = getItem(index, raw.requiredString("type"), raw.wireString("id"))
        when (phase) {
            Snapshot.Added -> {
                wireCheck(!item.added && !item.done, "Duplicate added output item")
                item.added = true
            }
            Snapshot.Done -> {
                wireCheck(!item.done, "Duplicate done output item")
                item.done = true
            }
            Snapshot.Completed -> item.done = true
        }
        if (phase != Snapshot.Added) {
            val status = raw.wireString("status")
            wireCheck(status == null || status == "completed", "Unsuccessful Responses output item status: $status")
        }
        when (item.type) {
            "function_call" -> {
                val call = checkNotNull(item.call)
                call.id = agree(call.id, raw.wireString("call_id"), "function call ID")
                call.name = agree(call.name, raw.wireString("name"), "function name")
                call.snapshot(raw.wireString("arguments"), final = phase != Snapshot.Added)
            }
            "message" -> raw.wireArray("content")?.forEachIndexed { contentIndex, part ->
                val content = part.wireObject("message content")
                if (content.wireString("type") == "output_text") {
                    textSnapshot(item, contentIndex, content.requiredString("text"), emitText)
                }
            }
            "reasoning" -> {
                if (tools.isNotEmpty()) {
                    if (phase != Snapshot.Added) wireCheck(!item.id.isNullOrBlank(), "Missing reasoning item ID")
                    val context = JsonObject((item.reasoning.orEmpty() - "status") + raw.filterValues { it != JsonNull })
                    wireCheck(context.toString().length <= MAX_REASONING_ITEM_LENGTH, "Reasoning item is too large")
                    item.reasoning = context
                }
                raw.wireArray("summary")?.forEachIndexed { summaryIdx, part ->
                    val partObj = part as? JsonObject ?: return@forEachIndexed
                    val text = partObj.wireStringOrNull("text").orEmpty()
                    if (text.isNotEmpty()) {
                        reconcileReasoning("$index:summary:$summaryIdx", text, emitReasoning)
                    }
                }
                raw.wireArray("content")?.forEachIndexed { contentIdx, part ->
                    val partObj = part as? JsonObject ?: return@forEachIndexed
                    val text = partObj.wireStringOrNull("text").orEmpty()
                    if (text.isNotEmpty()) {
                        reconcileReasoning("$index:content:$contentIdx", text, emitReasoning)
                    }
                }
            }
        }
    }

    private fun reasoningKey(body: JsonObject, kind: String, partIndex: Int): String {
        val outputIndex = body.wireIndex("output_index")
            ?: body.wireStringOrNull("item_id")?.let(itemIds::get)
        val owner = outputIndex?.toString() ?: body.wireStringOrNull("item_id") ?: "default"
        return "$owner:$kind:$partIndex"
    }

    private suspend fun emitReasoningDelta(key: String, delta: String, emitReasoning: suspend (String) -> Unit) {
        if (delta.isEmpty()) return
        val existing = reasoningBuffers[key]
        if (existing == null) {
            if (hasEmittedReasoning) emitReasoning("\n\n")
            reasoningBuffers[key] = StringBuilder(delta)
        } else {
            existing.append(delta)
        }
        hasEmittedReasoning = true
        emitReasoning(delta)
    }

    private suspend fun reconcileReasoning(key: String, text: String, emitReasoning: suspend (String) -> Unit) {
        if (text.isEmpty()) return
        val existing = reasoningBuffers[key]
        if (existing != null) {
            if (text.startsWith(existing) && text.length > existing.length) {
                val remainder = text.substring(existing.length)
                existing.append(remainder)
                hasEmittedReasoning = true
                emitReasoning(remainder)
            }
            return
        }
        // Avoid duplicating text already streamed under an unindexed fallback key.
        if (reasoningBuffers.values.any { it.toString() == text }) return
        emitReasoningDelta(key, text, emitReasoning)
    }

    private fun locate(raw: JsonObject, type: String): OutputItem {
        val id = raw.wireString("item_id")
        val index = raw.wireIndex("output_index") ?: id?.let(itemIds::get)
            ?: if (type == "message" && id == null) 0 else throw AiWireException("Missing Responses output index")
        return getItem(index, type, id)
    }

    private fun getItem(index: Int, type: String, id: String?): OutputItem {
        if (id != null) {
            wireCheck(id.isNotBlank(), "Empty Responses output item ID")
            val otherIndex = itemIds.putIfAbsent(id, index)
            wireCheck(otherIndex == null || otherIndex == index, "Duplicate Responses output item ID")
        }
        if (type == "function_call") wireCheck(tools.isNotEmpty(), "Unexpected tool call in a text-only turn")
        val item = output.getOrPut(index) { OutputItem(type) }
        wireCheck(item.type == type, "Conflicting Responses output item type")
        item.id = agree(item.id, id, "Responses item ID")
        return item
    }

    private suspend fun textSnapshot(item: OutputItem, index: Int, text: String, emitText: suspend (String) -> Unit) {
        val emitted = item.text.getOrPut(index) { StringBuilder() }
        wireCheck(text.startsWith(emitted), "Text snapshot conflicts with streamed text")
        if (text.length > emitted.length) {
            val remainder = text.substring(emitted.length)
            emitted.append(remainder)
            emitText(remainder)
        }
    }

    private fun JsonObject.requiredString(name: String): String = wireString(name) ?: throw AiWireException("Missing $name")
}

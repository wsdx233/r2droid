package top.wsdx233.r2droid.feature.ai.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AiWireTest {
    private val repo = AiRepository()
    private val model = "example-model"
    private val tools = listOf(AiToolDefinition("lookup", "Look up a document", JSONObject("""{"type":"object"}""")))
    private val legacy = ChatMessage(role = ChatRole.ExecutionResult, content = "legacy output not sent")
    private val user = ChatMessage(role = ChatRole.User, content = "Find the documents")

    @Test
    fun `Chat interleaves indexed calls and round trips each result under its own tool ID`() = runBlocking {
        LocalServer().use { server ->
            server.enqueue(chatCalls + chatFinish + done)
            server.enqueue(chatText)
            val provider = server.provider()
            val events = turn(provider, listOf(user, legacy)).toList()
            val completed = events.last() as AiTurnEvent.Completed
            assertEquals(listOf(AiTurnEvent.Text("Searching… "), completed), events)
            assertEquals(listOf("call_a", "call_b"), completed.calls.map { it.id })
            assertEquals(listOf("""{"doc":"a"}""", """{"doc":"b"}"""), completed.calls.map { it.arguments })
            val assistant = ChatMessage(role = ChatRole.Assistant, content = "Searching… ", toolCalls =
                completed.calls.map { it.copy(status = ToolCallStatus.Succeeded, output = "Result for ${it.id}") })
            assertEquals(listOf("Finished", " "), turn(provider, listOf(user, legacy, assistant)).toList()
                .filterIsInstance<AiTurnEvent.Text>().map { it.delta })
            val first = server.requests.remove()
            assertEquals("/v1/chat/completions", first.path)
            assertEquals("lookup", first.body.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
            assertFalse(first.body.toString().contains(legacy.content))
            val history = server.requests.remove().body.getJSONArray("messages")
            assertEquals(5, history.length())
            val assistantCalls = history.getJSONObject(2).getJSONArray("tool_calls")
            completed.calls.forEachIndexed { index, original ->
                assertEquals(original.id, assistantCalls.getJSONObject(index).getString("id"))
                val result = history.getJSONObject(3 + index)
                assertEquals("tool", result.getString("role"))
                assertEquals(original.id, result.getString("tool_call_id"))
                assertEquals("Result for ${original.id}", result.getString("content"))
            }
        }
    }

    @Test
    fun `Responses reconciles argument and text snapshots and replays same origin reasoning`() = runBlocking {
        LocalServer().use { server ->
            val reasoning = """{"id":"rs_1","type":"reasoning","summary":[]}"""
            val reasoningWithEncrypted = """{"id":"rs_1","type":"reasoning","summary":[],"encrypted_content":"opaque"}"""
            val message = """{"id":"msg_1","type":"message","status":"completed","content":[{"type":"output_text","text":"A B"}]}"""
            val function = """{"id":"fc_1","type":"function_call","call_id":"call_a","name":"lookup","status":"completed","arguments":"{\"doc\":\"a\"}"}"""
            server.enqueue(
                response("response.output_item.done", """ "output_index":0,"item":$reasoningWithEncrypted """) +
                    response("response.reasoning_summary_text.delta", """ "delta":"not displayed" """) +
                    response("response.output_text.delta", """ "output_index":1,"item_id":"msg_1","content_index":0,"delta":"A " """) +
                    response("response.output_text.done", """ "output_index":1,"item_id":"msg_1","content_index":0,"text":"A B" """) +
                    response("response.output_item.done", """"output_index":1,"item":$message""") +
                    responseAdded + responseArgs +
                    response("response.function_call_arguments.done", """ "output_index":2,"item_id":"fc_1","name":"lookup","arguments":"{\"doc\":\"a\"}" """) +
                    response("response.output_item.done", """"output_index":2,"item":$function""") +
                    response("response.completed", """"response":{"status":"completed","output":[$reasoning,$message,$function]}""")
            )
            server.enqueue(responseComplete)
            server.enqueue(responseComplete)
            val provider = server.provider(responses = true)
            val events = turn(provider, listOf(legacy, user)).toList()
            assertEquals(listOf("A ", "B"), events.filterIsInstance<AiTurnEvent.Text>().map { it.delta })
            assertEquals(listOf("not displayed"), events.filterIsInstance<AiTurnEvent.Reasoning>().map { it.delta })
            val completed = events.filterIsInstance<AiTurnEvent.Completed>().single()
            val call = completed.calls.single()
            assertEquals("call_a", call.id)
            assertEquals("""{"doc":"a"}""", call.arguments)
            assertEquals("opaque", JSONObject(completed.responsesReasoning.single()).getString("encrypted_content"))
            val assistant = ChatMessage(role = ChatRole.Assistant, content = "A B",
                toolCalls = listOf(call.copy(status = ToolCallStatus.Succeeded, output = "Document A")),
                responsesReasoning = completed.responsesReasoning, reasoningOrigin = provider.reasoningOrigin(model))
            turn(provider, listOf(user, legacy, assistant)).toList()
            turn(provider.copy(id = "different provider"), listOf(user, assistant)).toList()
            val first = server.requests.remove()
            assertEquals("/v1/responses", first.path)
            val tool = first.body.getJSONArray("tools").getJSONObject(0)
            assertEquals("lookup", tool.getString("name"))
            assertFalse(tool.has("function"))
            assertEquals("low", first.body.getJSONObject("reasoning").getString("effort"))
            assertEquals("auto", first.body.getJSONObject("reasoning").getString("summary"))
            assertEquals("reasoning.encrypted_content", first.body.getJSONArray("include").getString(0))
            val input = server.requests.remove().body.getJSONArray("input")
            assertEquals(6, input.length())
            assertEquals("rs_1", input.getJSONObject(2).getString("id"))
            val requestedCall = input.getJSONObject(4)
            assertEquals("function_call", requestedCall.getString("type"))
            assertEquals(call.id, requestedCall.getString("call_id"))
            val result = input.getJSONObject(5)
            assertEquals("function_call_output", result.getString("type"))
            assertEquals(call.id, result.getString("call_id"))
            assertEquals("Document A", result.getString("output"))
            assertFalse(server.requests.remove().body.toString().contains("opaque"))
        }
    }

    @Test
    fun `Responses completed-only snapshot yields calls but cannot reuse a historical call ID`() = runBlocking {
        LocalServer().use { server ->
            val snapshot = response("response.completed", """ "response":{"status":"completed","output":[
                {"id":"fc_final","type":"function_call","call_id":"call_final","name":"lookup","arguments":"{}"}]} """)
            server.enqueue(snapshot)
            server.enqueue(snapshot)
            val provider = server.provider(responses = true)
            val completed = turn(provider).toList().single() as AiTurnEvent.Completed
            val call = completed.calls.single()
            assertEquals("call_final", call.id)
            val history = listOf(user, ChatMessage(role = ChatRole.Assistant, content = "",
                toolCalls = listOf(call.copy(status = ToolCallStatus.Succeeded, output = "Done"))))
            val seen = mutableListOf<AiTurnEvent>()
            val error = runCatching { turn(provider, history).toList(seen) }.exceptionOrNull()
            assertTrue(error is AiWireException)
            assertTrue(seen.isEmpty())
        }
    }

    @Test
    fun `malformed failed and truncated turns never emit calls`() = runBlocking {
        val badChat = listOf(
            chatCalls + chatFinish, // Only one half of the terminal signal.
            chatCalls + chatFinish + "data: [DONE]\n", // Lost end-of-frame delimiter.
            chatCalls + done,
            chatCalls + chatFinish.replace("tool_calls", "length") + done,
            chatCalls.replace("call_b", "call_a") + chatFinish + done,
            chatCalls.replace("\\\"b\\\"", "unquoted") + chatFinish + done,
            chatCalls.replace("call_b", "") + chatFinish + done
        )
        val badResponses = listOf(
            responseAdded + responseArgs,
            responseAdded + responseArgs + responseComplete, // Partial input and status-only terminal.
            responseAdded + responseArgs + response("response.failed", """"response":{"status":"failed"}"""),
            responseAdded + responseArgs + response("response.incomplete", """"response":{"status":"incomplete"}"""),
            responseAdded + responseArgs + response("response.completed", """"response":{"status":"completed","output":[]}"""),
            responseAdded + responseArgs + response("response.function_call_arguments.done", """"output_index":2,"arguments":"{}"""") + responseComplete,
            response("response.completed", """"response":{"status":"completed","output":[
                {"id":"f1","type":"function_call","call_id":"same","name":"lookup","arguments":"{}"},
                {"id":"f2","type":"function_call","call_id":"same","name":"lookup","arguments":"{}"}]}""")
        )
        LocalServer().use { server ->
            for ((responses, cases) in listOf(false to badChat, true to badResponses)) {
                for (wire in cases) {
                    server.enqueue(wire)
                    val received = mutableListOf<AiTurnEvent>()
                    val error = runCatching { turn(server.provider(responses)).toList(received) }.exceptionOrNull()
                    assertTrue("Unexpected result for $wire: $error", error is AiWireException)
                    assertTrue(received.none { it is AiTurnEvent.Completed })
                    assertTrue(received.filterIsInstance<AiTurnEvent.Text>().none { '{' in it.delta })
                }
            }
            assertEquals(badChat.size + badResponses.size, server.requests.size)
        }
    }

    @Test
    fun `only explicit unsupported tools HTTP errors retry once and without tool history`() = runBlocking {
        LocalServer().use { server ->
            server.enqueue("""{"error":{"param":"tools","code":"unsupported_parameter"}}""", 400)
            server.enqueue(chatText)
            server.enqueue("""{"error":{"message":"This model does not support function calling."}}""", 422)
            server.enqueue("still broken", 500)
            server.enqueue("""{"error":{"message":"Unsupported parameter: 'tools[0].function.parameters'"}}""", 422)
            val provider = server.provider()
            val history = listOf(user, legacy, ChatMessage(role = ChatRole.Assistant, content = "Earlier",
                toolCalls = listOf(AiToolCall("history", "lookup", "{}", ToolCallStatus.Succeeded, "private output"))))
            assertEquals(AiTurnEvent.Completed(emptyList()), turn(provider, history).toList().last())
            assertTrue(server.requests.remove().body.has("tools"))
            val retry = server.requests.remove().body
            assertFalse(retry.has("tools"))
            assertFalse(retry.toString().contains("private output"))
            assertFalse(retry.toString().contains(legacy.content))
            assertTrue(retry.toString().contains("Earlier"))
            val retryError = runCatching { turn(provider).toList() }.exceptionOrNull()
            assertTrue(retryError is IOException && "500" in retryError.message.orEmpty())
            val schemaError = runCatching { turn(provider).toList() }.exceptionOrNull()
            assertTrue(schemaError is IOException && "422" in schemaError.message.orEmpty())
            assertEquals(listOf(true, false, true), server.requests.map { it.body.has("tools") })
        }
    }

    @Test
    fun `explicit input window rejections keep the HTTP diagnostic and do not retry`() = runBlocking {
        val rejections = listOf(
            400 to """{"error":{"type":"invalid_request_error","code":"context_length_exceeded","param":null,"message":"Too long"}}""",
            422 to """{"error":{"type":"input_context_window_exceeded","param":"input"}}""",
            413 to """{"code":"context_length_exceeded","param":"messages"}""",
            400 to """{"error":{"type":"invalid_request_error","message":"Your input exceeds the context window of this model. Please adjust your input and try again."}}""",
            400 to "This model's maximum context length is 4096 tokens. However, you requested 4600 tokens (4200 in the messages, 400 in the completion).",
            400 to "This model's maximum context length is 4096 tokens. However, you requested 4097 tokens (4096 in the messages, 1 in the completion).",
            422 to """{"error":{"type":"invalid_request_error","message":"Prompt is too long: 4200 tokens > 4096 maximum"}}""",
            // The explicit rejection wins even when a compatibility heuristic also matches the diagnostic.
            400 to """{"error":{"code":"context_length_exceeded","message":"Your input exceeds the context window of this model. Remove reasoning.summary or shorten the input."}}"""
        )
        LocalServer().use { server ->
            for (responses in listOf(false, true)) {
                for ((status, body) in rejections) {
                    server.enqueue(body, status)
                    val received = mutableListOf<AiTurnEvent>()
                    val error = runCatching { turn(server.provider(responses)).toList(received) }.exceptionOrNull()
                    assertTrue("Expected input window rejection for $status $body: $error", error is AiContextLimitException)
                    assertEquals("AI API failed ($status): $body", error?.message)
                    assertTrue(received.isEmpty())
                    assertEquals(1, server.requests.size)
                    assertEquals(if (responses) "/v1/responses" else "/v1/chat/completions", server.requests.remove().path)
                }
            }
        }
    }

    @Test
    fun `ambiguous request errors and unrelated HTTP failures stay ordinary HTTP errors`() = runBlocking {
        val explicitBody = """{"error":{"code":"context_length_exceeded"}}"""
        val rejections = listOf(
            413 to "Request Entity Too Large",
            413 to """{"error":{"message":"Request body exceeds the maximum size."}}""",
            401 to explicitBody,
            403 to explicitBody,
            429 to explicitBody,
            500 to explicitBody,
            400 to """{"error":{"code":"rate_limit_exceeded","message":"Your input exceeds the context window of this model."}}""",
            400 to """{"error":{"message":"Maximum context length exceeded."}}""",
            400 to """{"error":{"message":"The prompt is too long."}}""",
            400 to """{"error":{"code":"invalid_value","param":"max_tokens","message":"Invalid max_tokens: must be less than the model's context length."}}""",
            422 to """{"error":{"code":"context_length_exceeded","param":"max_output_tokens","message":"Your input exceeds the context window of this model."}}""",
            400 to """{"error":{"code":"context_length_exceeded","message":"max_completion_tokens must not exceed the model limit."}}""",
            400 to """{"error":{"code":"context_length_exceeded","message":"This model's maximum context length is 4096 tokens. However, you requested 4600 tokens (600 in your prompt; 4000 for the completion)."}}""",
            400 to """{"error":{"code":"context_length_exceeded","message":"This model's maximum context length is 4096 tokens. However, you requested 4600 tokens."}}""",
            400 to """{"error":{"code":"invalid_value","param":"input","message":"Invalid input: exceeds the maximum string length."}}"""
        )
        LocalServer().use { server ->
            for (responses in listOf(false, true)) {
                for ((status, body) in rejections) {
                    server.enqueue(body, status)
                    val received = mutableListOf<AiTurnEvent>()
                    val error = runCatching { turn(server.provider(responses)).toList(received) }.exceptionOrNull()
                    assertTrue("Expected ordinary HTTP error for $status $body: $error", error is IOException && error !is AiContextLimitException)
                    assertEquals("AI API failed ($status): $body", error?.message)
                    assertTrue(received.isEmpty())
                    assertEquals(1, server.requests.size)
                    server.requests.remove()
                }
            }
        }
    }

    @Test
    fun `successful HTTP streams do not classify length finishes or failed partial turns as input rejections`() = runBlocking {
        val chatDelta = frame("""{"choices":[{"index":0,"delta":{"content":"Partial"}}]}""")
        val responseDelta = response("response.output_text.delta", """"output_index":0,"item_id":"m1","content_index":0,"delta":"Partial"""")
        val cases = listOf(
            false to (chatDelta + frame("""{"choices":[{"index":0,"delta":{},"finish_reason":"length"}]}""") + done),
            false to (chatDelta + frame("""{"error":{"code":"context_length_exceeded"}}""")),
            true to (responseDelta + response("response.failed", """"response":{"status":"failed","error":{"code":"context_length_exceeded"}}""")),
            true to (responseDelta + response("response.incomplete", """"response":{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"}}"""))
        )
        LocalServer().use { server ->
            for ((responses, wire) in cases) {
                server.enqueue(wire)
                val received = mutableListOf<AiTurnEvent>()
                val error = runCatching { turn(server.provider(responses)).toList(received) }.exceptionOrNull()
                assertTrue("Expected stream failure for $wire: $error", error is AiWireException)
                assertTrue(received.none { it is AiTurnEvent.Completed })
                assertEquals(1, server.requests.size)
                server.requests.remove()
            }
        }
    }

    @Test
    fun `transport failure before HTTP headers is not an input window rejection`() = runBlocking {
        LocalServer().use { server ->
            server.handlers.add { it.close() }
            val error = runCatching { turn(server.provider()).toList() }.exceptionOrNull()
            assertTrue("Expected transport failure: $error", error is IOException && error !is AiContextLimitException)
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun `text only API supports multi-line SSE and preserves whitespace without sending tools`() = runBlocking {
        LocalServer().use { server ->
            server.enqueue("\uFEFF: comment\r\nevent: message\r\nignored: line\r\n" +
                "data: {\"choices\":[\r\ndata: {\"index\":0,\"delta\":{\"content\":\"é\"},\"finish_reason\":null}]}\r\n\r\n" + chatText)
            assertEquals(listOf("é", "Finished", " "), repo.streamText(server.provider(), listOf(user, legacy), model, "System").toList())
            val request = server.requests.remove().body
            assertFalse(request.has("tools"))
            assertFalse(request.toString().contains(legacy.content))
        }
    }

    @Test
    fun `Chat streams reasoning_content and reasoning without leaking into answer text`() = runBlocking {
        LocalServer().use { server ->
            server.enqueue(
                frame("""{"choices":[{"index":0,"delta":{"reasoning_content":"Inspecting ","content":null}}]}""") +
                    frame("""{"choices":[{"index":0,"delta":{"reasoning":"sections."}}]}""") +
                    frame("""{"choices":[{"index":0,"delta":{"content":"Done."},"finish_reason":"stop"}]}""") +
                    done
            )
            val events = turn(server.provider()).toList()
            assertEquals(listOf("Inspecting ", "sections."), events.filterIsInstance<AiTurnEvent.Reasoning>().map { it.delta })
            assertEquals(listOf("Done."), events.filterIsInstance<AiTurnEvent.Text>().map { it.delta })
        }
    }

    @Test
    fun `Responses retries cleanly without reasoning summary when rejected by non-reasoning model`() = runBlocking {
        LocalServer().use { server ->
            server.enqueue("""{"error":{"param":"reasoning.summary","message":"reasoning.summary is only supported for reasoning models"}}""", 400)
            server.enqueue(
                response("response.output_item.done", """"output_index":0,"item":{"id":"rs_1","type":"reasoning","status":"completed","summary":[{"type":"summary_text","text":"Checked imports."}]}""") +
                    response("response.output_text.delta", """"output_index":1,"item_id":"m1","content_index":0,"delta":"Answer"""") +
                    response("response.completed", """"response":{"status":"completed","output":[{"id":"rs_1","type":"reasoning","summary":[{"type":"summary_text","text":"Checked imports."}]},{"id":"m1","type":"message","status":"completed","content":[{"type":"output_text","text":"Answer"}]}]}""")
            )
            val events = turn(server.provider(responses = true)).toList()
            assertEquals(listOf("Checked imports."), events.filterIsInstance<AiTurnEvent.Reasoning>().map { it.delta })
            assertEquals(listOf("Answer"), events.filterIsInstance<AiTurnEvent.Text>().map { it.delta })
            val firstReq = server.requests.remove().body.getJSONObject("reasoning")
            assertEquals("auto", firstReq.getString("summary"))
            val retryReq = server.requests.remove().body.getJSONObject("reasoning")
            assertFalse(retryReq.has("summary"))
            assertEquals("low", retryReq.getString("effort"))
        }
    }

    @Test
    fun `cancelling a stalled stream closes a pending HTTP read without releasing buffered calls`() = runBlocking {
        LocalServer().use { server ->
            val release = CountDownLatch(1)
            server.handlers.add { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write((chatCalls + chatFinish).toByteArray())
                exchange.responseBody.flush()
                release.await(10, TimeUnit.SECONDS)
            }
            val receivedText = CompletableDeferred<Unit>()
            val seen = mutableListOf<AiTurnEvent>()
            try {
                val job = launch {
                    turn(server.provider()).collect { event ->
                        seen.add(event)
                        if (event is AiTurnEvent.Text) receivedText.complete(Unit)
                    }
                }
                withTimeout(3_000) { receivedText.await() }
                withTimeout(2_000) { job.cancelAndJoin() }
                assertEquals(listOf(AiTurnEvent.Text("Searching… ")), seen)
                assertEquals(1, server.requests.size)
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `cancelling a request stalled before headers promptly joins collection`() = runBlocking {
        LocalServer().use { server ->
            val requestReceived = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            server.handlers.add {
                requestReceived.complete(Unit)
                release.await(10, TimeUnit.SECONDS)
            }
            try {
                val job = launch { turn(server.provider()).collect { error("Unexpected event: $it") } }
                withTimeout(3_000) { requestReceived.await() }
                withTimeout(2_000) { job.cancelAndJoin() }
                assertEquals(1, server.requests.size)
            } finally {
                release.countDown()
            }
        }
    }

    private fun turn(provider: AiProvider, history: List<ChatMessage> = listOf(user)) =
        repo.streamTurn(provider, history, model, "System", ThinkingLevel.Light, tools)

    private companion object {
        fun frame(data: String) = "data: $data\n\n"
        fun response(type: String, fields: String) = "event: $type\n" + frame("""{"type":"$type",$fields}""".replace("\n", ""))
        val done = frame("[DONE]")
        val chatFinish = frame("""{"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""")
        val chatCalls = frame("""{"choices":[{"index":0,"delta":{"content":"Searching… ","tool_calls":[
            {"index":1,"id":"call_b","type":"function","function":{"name":"lookup","arguments":"{\"doc\":"}},
            {"index":0,"id":"call_a","type":"function","function":{"name":"lookup","arguments":"{\"doc\":"}}]}}]}""".replace("\n", "")) +
            frame("""{"choices":[{"index":0,"delta":{"content":null,"tool_calls":[
            {"index":0,"function":{"arguments":"\"a\"}"}}, {"index":1,"function":{"arguments":"\"b\"}"}}]}}]}""".replace("\n", ""))
        val chatText = frame("""{"choices":[{"index":0,"delta":{"content":"Finished"}}]}""") +
            frame("""{"choices":[{"index":0,"delta":{"content":" "},"finish_reason":"stop"}]}""") +
            frame("""{"choices":[],"usage":{}}""") + done
        val responseAdded = response("response.output_item.added", """"output_index":2,"item":{
            "type":"function_call","id":"fc_1","call_id":"call_a","name":"lookup","arguments":""}""")
        val responseArgs = response("response.function_call_arguments.delta", """ "output_index":2,"item_id":"fc_1","delta":"{\"doc\":" """)
        val responseComplete = response("response.completed", """"response":{"status":"completed"}""")
    }
}

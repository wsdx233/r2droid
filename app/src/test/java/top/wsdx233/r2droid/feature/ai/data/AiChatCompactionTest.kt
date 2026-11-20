package top.wsdx233.r2droid.feature.ai.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AiChatCompactionTest {
    private val repo = AiRepository()
    private val tools = listOf(AiToolDefinition("lookup", "Look up a document", JSONObject("""{"type":"object"}""")))
    private val limit = """{"error":{"code":"context_length_exceeded"}}"""
    private val older = ChatMessage(role = ChatRole.User, content = "Find the first edition")
    private val current = ChatMessage(role = ChatRole.User, content = "Compare the second edition")

    @Test
    fun `both APIs fold only the rejected prefix then reuse and extend the same checkpoint`() = runBlocking {
        for (responses in listOf(false, true)) LocalServer().use { server ->
            val provider = server.provider(responses)
            val field = if (responses) "input" else "messages"
            val pinned = ChatMessage(role = ChatRole.System, content = "Pinned instruction")
            val oldResult = ChatMessage(role = ChatRole.Assistant, content = "<think>PRIVATE</think>Earlier answer",
                reasoning = "PRIVATE", toolCalls = listOf(AiToolCall("old", "lookup", "{}", ToolCallStatus.Denied, "no result")))
            val displayOnly = ChatMessage(role = ChatRole.ExecutionResult, content = "PRIVATE legacy output")
            val currentResult = oldResult.copy(id = "current-result", content = "Reading second edition", toolCalls = listOf(
                AiToolCall("second-call", "lookup", "{}", ToolCallStatus.Succeeded, "Second edition contents")))
            val history = listOf(pinned, older, oldResult, displayOnly, current, currentResult)
            var cache: ChatCompaction? = null
            var starts = 0
            suspend fun send(rows: List<ChatMessage>, p: AiProvider = provider) =
                repo.streamChatTurn(p, rows, "model", "Active instruction", ThinkingLevel.Normal, tools, cache,
                    onCompacting = { starts++ }, onCompacted = { cache = it }).toList()

            server.enqueue(limit, 400)
            server.enqueue(answer("First edition memo", responses))
            server.enqueue(answer("Compared", responses))
            val visible = send(history).filterIsInstance<AiTurnEvent.Text>()
            assertEquals(listOf(AiTurnEvent.Text("Compared")), visible)
            assertEquals(1, starts)
            assertEquals(displayOnly.id, cache?.lastMessageId)
            val original = server.requests.remove().body.getJSONArray(field)
            val summarize = server.requests.remove().body
            assertFalse(summarize.has("tools"))
            val records = summarize.getJSONArray(field).getJSONObject(1).getString("content")
            assertTrue(older.content in records && "Earlier answer" in records && "no result" in records)
            assertFalse("PRIVATE" in records || current.content in records || pinned.content in records)
            val retry = server.requests.remove().body.getJSONArray(field)
            assertEquals("Active instruction", retry.getJSONObject(0).getString("content"))
            assertEquals(pinned.content, retry.getJSONObject(1).getString("content"))
            assertEquals("assistant", retry.getJSONObject(2).getString("role"))
            assertTrue("First edition memo" in retry.getJSONObject(2).getString("content"))
            assertFalse(older.content in retry.toString())
            val suffix = (3 until retry.length()).map { retry.getJSONObject(it).toString() }
            assertEquals(suffix, (original.length() - suffix.size until original.length()).map { original.getJSONObject(it).toString() })

            val more = history + ChatMessage(role = ChatRole.User, content = "One more question")
            server.enqueue(answer("Yes", responses))
            send(more)
            assertTrue("First edition memo" in server.requests.remove().body.toString())
            server.enqueue(limit, 400)
            server.enqueue(answer("Updated memo", responses))
            server.enqueue(answer("Next", responses))
            send(more)
            server.requests.remove()
            val increment = server.requests.remove().body.getJSONArray(field).getJSONObject(1).getString("content")
            assertTrue("First edition memo" in increment && current.content in increment)
            assertFalse(older.content in increment || more.last().content in increment)
            assertTrue("Updated memo" in server.requests.remove().body.toString())
            val updated = requireNotNull(cache)
            assertFalse(updated.matchesPrefix(more - older))
            assertFalse(updated.matchesPrefix(history.take(2)))

            server.enqueue(answer("Other model's view", responses))
            send(more, provider.copy(id = "different-provider"))
            val switched = server.requests.remove().body.toString()
            assertFalse("Updated memo" in switched)
            assertTrue(older.content in switched)
            assertEquals(2, starts)
            assertTrue(server.requests.isEmpty())
        }
    }

    @Test
    fun `no retry for a lone oversized turn a failed summary or a second rejection`() = runBlocking {
        LocalServer().use { server ->
            val provider = server.provider()
            var committed = 0
            var starts = 0
            suspend fun send(rows: List<ChatMessage>) = runCatching {
                repo.streamChatTurn(provider, rows, "model", "System", ThinkingLevel.Auto, emptyList(), null,
                    { starts++ }, { committed++ }).toList()
            }.exceptionOrNull()
            server.enqueue(limit, 400)
            assertTrue(send(listOf(current)) is AiContextLimitException)
            assertEquals(0, starts)
            val history = listOf(older, current)
            server.enqueue(limit, 400)
            server.enqueue(answer("<think>unfinished reasoning", false))
            assertTrue(send(history) is IllegalStateException)
            assertEquals(0, committed)
            server.enqueue(limit, 400)
            server.enqueue(answer("Memo", false))
            server.enqueue(limit, 400)
            assertTrue(send(history) is AiContextLimitException)
            assertEquals(2, starts)
            assertEquals(6, server.requests.size)
        }
    }

    @Test
    fun `summary input rejection splits the rejected batch without dropping or repeating text`() = runBlocking {
        LocalServer().use { server ->
            val history = listOf(older.copy(content = "书📗".repeat(3000)), current)
            server.enqueue(limit, 400)
            server.enqueue(limit, 400)
            // Sub-batches, including a possible short suffix when a surrogate pair straddles the midpoint.
            repeat(3) { server.enqueue(answer("memo", false)) }
            server.enqueue(answer("memo", false))
            repo.streamChatTurn(server.provider(), history, "model", "System", ThinkingLevel.Auto,
                emptyList(), null, {}, {}).toList()
            val summaryBodies = server.requests.map { it.body.getJSONArray("messages") }.drop(1).dropLast(1)
            val pieces = summaryBodies.map { it.getJSONObject(1).getString("content").substringAfter("Additional conversation records:\n") }
            assertTrue(pieces.size >= 3)
            assertEquals(pieces.first(), pieces.drop(1).joinToString(""))
            assertTrue(pieces.first().endsWith(history.first().content))
        }
    }

    @Test
    fun `stop during summary never publishes a checkpoint or restarts the turn`() = runBlocking {
        LocalServer().use { server ->
            server.enqueue(limit, 400)
            val summarizing = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            server.handlers.add { summarizing.complete(Unit); release.await(5, TimeUnit.SECONDS) }
            var committed = false
            try {
                val job = launch {
                    repo.streamChatTurn(server.provider(), listOf(older, current), "model", "System", ThinkingLevel.Auto,
                        emptyList(), null, {}, { committed = true }).collect { error("Unexpected chat event") }
                }
                withTimeout(3_000) { summarizing.await() }
                withTimeout(2_000) { job.cancelAndJoin() }
                assertFalse(committed)
                assertEquals(2, server.requests.size)
            } finally {
                release.countDown()
            }
        }
    }

    private fun answer(text: String, responses: Boolean): String {
        val quoted = JSONObject.quote(text)
        return if (responses) {
            "data: " + """{"type":"response.completed","response":{"status":"completed","output":[{"id":"m","type":"message","content":[{"type":"output_text","text":$quoted}]}]}}""" + "\n\n"
        } else {
            "data: " + """{"choices":[{"delta":{"content":$quoted},"finish_reason":"stop"}]}""" + "\n\ndata: [DONE]\n\n"
        }
    }
}

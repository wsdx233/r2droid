package top.wsdx233.r2droid.feature.ai.data

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [AiChatHistoryStoreTest.HostOs::class])
class AiChatHistoryStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()
    private lateinit var context: Context
    private val preferences get() = context.getSharedPreferences("r2droid_ai_settings", Context.MODE_PRIVATE)
    private val historyDir get() = File(context.filesDir, "ai_chat_history")
    private val index get() = File(historyDir, "index.json")

    @Before
    fun setUp() {
        val files = temporary.newFolder()
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getFilesDir(): File = files
        }
        assertTrue(preferences.edit().clear().commit())
    }

    @After
    fun tearDown() {
        HostOs.failRenameTo = null
    }

    @Test
    fun `legacy conversations and index survive repeated cold starts`() {
        putLegacy("[${legacySession("first", 1)},${legacySession("second", 2)}]")
        repeat(4) {
            assertEquals(listOf("second", "first"), launch())
            assertFalse(preferences.contains("chat_sessions"))
            assertTrue(index.isFile)
            assertEquals("hello first", AiSettingsManager.loadSession("first")?.messages?.single()?.content)
            assertEquals("hello second", AiSettingsManager.loadSession("second")?.messages?.single()?.content)
            assertNull(AiSettingsManager.loadSession("first")?.compaction)
        }
    }

    @Test
    fun `missing or invalid index recovers metadata without changing session or unrelated files`() {
        val store = AiChatHistoryStore(context)
        store.saveSession(session("small", 1))
        val largeFile = sessionFile("large")
        largeFile.bufferedWriter().use { writer ->
            writer.write("""{"id":"large","title":"Large chat","timestamp":2,"messages":[{"role":"Assistant","content":""")
            writer.write('"'.code)
            val chunk = "x".repeat(8_192)
            repeat(1_024) { writer.write(chunk) }
            writer.write("\"}]}")
        }
        val untouched = mapOf(
            "other.json" to """{"setting":42}""",
            "copied.json" to legacySession("wrong-name", 4),
            "partial.json" to """{"id":"partial","title":"Partial","timestamp":5,"messages":[""",
            "pending.json.tmp" to legacySession("pending", 6)
        )
        untouched.forEach { (name, text) -> File(historyDir, name).writeText(text) }
        val largeLength = largeFile.length()
        val largeModified = largeFile.lastModified()
        val smallBefore = sessionFile("small").readText()

        for (badIndex in listOf(null, "[", "[{}]", "[] trailing")) {
            if (badIndex == null) {
                assertFalse(index.exists())
            } else {
                index.writeText(badIndex)
            }
            assertEquals(listOf("large", "small"), launch())
            assertEquals(ChatSessionMetadata("large", "Large chat", 2), AiSettingsManager.sessionsFlow.value.first())
            assertEquals(listOf("large", "small"), AiChatHistoryStore(context).loadIndex()?.map { it.id })
            assertEquals(largeLength, largeFile.length())
            assertEquals(largeModified, largeFile.lastModified())
            assertEquals(smallBefore, sessionFile("small").readText())
            untouched.forEach { (name, text) -> assertEquals(text, File(historyDir, name).readText()) }
        }
    }

    @Test
    fun `valid empty index does not trigger recovery or cleanup`() {
        val store = AiChatHistoryStore(context)
        store.saveIndex(emptyList())
        store.saveSession(session("unindexed", 1))

        assertTrue(launch().isEmpty())
        assertEquals("[]", index.readText())
        assertTrue(sessionFile("unindexed").isFile)
    }

    @Test
    fun `failed migration keeps legacy and existing index and retry preserves newer files`() {
        val store = AiChatHistoryStore(context)
        val existing = session("existing", 5)
        store.saveSession(existing)
        store.saveIndex(listOf(ChatSessionMetadata(existing.id, existing.title, existing.timestamp)))
        val priorIndex = index.readText()
        val legacy = "[${legacySession("existing", 1)},${legacySession("pending", 2)}]"
        putLegacy(legacy)
        HostOs.failRenameTo = index.absolutePath

        assertEquals(listOf("existing", "pending"), launch())
        assertEquals(priorIndex, index.readText())
        assertEquals(legacy, preferences.getString("chat_sessions", null))
        assertEquals(existing, AiSettingsManager.loadSession("existing"))
        assertEquals("hello pending", AiSettingsManager.loadSession("pending")?.messages?.single()?.content)

        val newer = session("pending", 10).let { it.copy(compaction = checkpoint(it)) }
        store.saveSession(newer)
        HostOs.failRenameTo = null
        assertEquals(listOf("pending", "existing"), launch())
        assertFalse(preferences.contains("chat_sessions"))
        assertEquals(existing, AiSettingsManager.loadSession("existing"))
        assertEquals(newer, AiSettingsManager.loadSession("pending"))
    }

    @Test
    fun `explicit deletion survives lost index and retry with legacy and stale file data`() {
        putLegacy("[${legacySession("deleted", 2)},${legacySession("kept", 1)}]")
        HostOs.failRenameTo = index.absolutePath
        assertEquals(listOf("deleted", "kept"), launch())
        HostOs.failRenameTo = null
        val staleFile = sessionFile("deleted").readText()

        AiSettingsManager.deleteSession("deleted")
        assertFalse(sessionFile("deleted").exists())
        assertNull(AiSettingsManager.loadSession("deleted"))
        assertTrue(preferences.contains("chat_sessions"))
        assertTrue(index.delete())
        sessionFile("deleted").writeText(staleFile)

        assertEquals(listOf("kept"), launch())
        assertFalse(preferences.contains("chat_sessions"))
        assertNull(AiSettingsManager.loadSession("deleted"))
        AiSettingsManager.saveSession(session("deleted", 3))
        assertEquals(listOf("kept"), launch())
        assertEquals("hello kept", AiSettingsManager.loadSession("kept")?.messages?.single()?.content)
    }

    @Test
    fun `large checkpoint and complete transcript survive cold load and index recovery`() {
        val store = AiChatHistoryStore(context)
        val first = session("compacted", 2)
        val tool = AiToolCall("a", "get_binary_info", "{}", ToolCallStatus.Succeeded, "64-bit ELF", durationMillis = 42)
        val assistant = ChatMessage(role = ChatRole.Assistant, content = "Result", toolCalls = listOf(tool))
        val chat = first.copy(
            messages = first.messages + assistant + ChatMessage(role = ChatRole.User, content = "Next"),
            compaction = checkpoint(first).copy(summary = "Line with \"quotes\" and 中文\n".repeat(32_768))
        )
        assertTrue(store.saveSession(chat))
        assertEquals(listOf("compacted.json"), historyDir.list()?.toList())

        val reopened = AiChatHistoryStore(context)
        assertNull(reopened.loadIndex())
        assertEquals(chat, reopened.loadSession(chat.id))
        assertEquals(listOf(chat.id), launch())
        assertEquals(listOf(ChatSessionMetadata(chat.id, chat.title, chat.timestamp)), reopened.loadIndex())
        assertEquals(chat, AiSettingsManager.loadSession(chat.id))

        assertTrue(reopened.saveSession(chat.copy(compaction = null)))
        assertEquals(chat.copy(compaction = null), reopened.loadSession(chat.id))
    }

    @Test
    fun `malformed optional checkpoint does not discard the transcript or metadata`() {
        val store = AiChatHistoryStore(context)
        val chat = session("malformed", 1)
        store.saveSession(chat)
        val transcript = sessionFile(chat.id).readText().dropLast(1)
        val invalid = listOf(
            "null",
            "[{}]",
            """{"origin":"local-provider","lastMessageId":"message","summary":"Summary"}""",
            """{"origin":"local-provider","coveredMessages":"1","lastMessageId":"message","summary":"Summary"}""",
            """{"origin":"local-provider","coveredMessages":1,"lastMessageId":"message","summary":{"text":"Summary"}}"""
        )
        for (value in invalid) {
            sessionFile(chat.id).writeText("""$transcript,"compaction":$value,"complete":true}""")
            assertEquals(chat, store.loadSession(chat.id))
            assertEquals(listOf(ChatSessionMetadata(chat.id, chat.title, chat.timestamp)), store.recoverIndex())
        }
    }

    @Test
    fun `recovery ignores a checkpoint file with an incomplete suffix`() {
        val store = AiChatHistoryStore(context)
        val chat = session("truncated", 1)
        store.saveSession(chat.copy(compaction = checkpoint(chat)))
        val file = sessionFile(chat.id)
        file.writeText(file.readText().dropLast(1))

        assertEquals(emptyList<ChatSessionMetadata>(), store.recoverIndex())
        assertNull(store.loadSession(chat.id))
        assertTrue(file.isFile)
    }

    @Test
    fun `failed session replacement leaves previous contents available`() {
        val store = AiChatHistoryStore(context)
        val before = session("same", 1).let { it.copy(compaction = checkpoint(it)) }
        val after = session("same", 2)
        store.saveSession(before)
        HostOs.failRenameTo = sessionFile("same").absolutePath
        assertThrows(ErrnoException::class.java) { store.saveSession(after) }
        assertEquals(before, store.loadSession("same"))
        assertEquals(listOf("same.json"), historyDir.list()?.toList())

        HostOs.failRenameTo = null
        store.saveSession(after)
        assertEquals(after, store.loadSession("same"))
    }

    @Test
    fun `cold load keeps completed results but never restores a live approval or runs legacy markup`() {
        val store = AiChatHistoryStore(context)
        val committed = AiToolCall("a", "get_binary_info", "{}", ToolCallStatus.Succeeded, "64-bit ELF")
        val pending = AiToolCall("b", "r2_command", "{\"command\":\"ij\"}", ToolCallStatus.AwaitingApproval)
        val legacy = ChatMessage(role = ChatRole.Assistant, content = "Example: `[[ij]]`",
            actionResults = listOf(ActionResult(ActionType.R2Command, "ij", "old", true)))
        val live = ChatMessage(role = ChatRole.Assistant, content = "Checking", isStreaming = true,
            toolCalls = listOf(committed, pending), responsesReasoning = listOf("{\"type\":\"reasoning\"}"),
            reasoningOrigin = "local-provider")
        val chat = session("tools", 1).copy(messages = listOf(legacy, live))
        store.saveSession(chat)

        val restored = requireNotNull(store.loadSession(chat.id)).messages
        assertEquals(legacy, restored.first())
        val reply = restored.last()
        assertFalse(reply.isStreaming)
        assertEquals(committed, reply.toolCalls.first())
        assertEquals("b", reply.toolCalls.last().id)
        assertEquals(ToolCallStatus.Interrupted, reply.toolCalls.last().status)
        assertEquals(live.responsesReasoning, reply.responsesReasoning)
        assertEquals(live.reasoningOrigin, reply.reasoningOrigin)
    }

    private fun launch(): List<String> {
        // initialize opens a new store against the same on-disk files on every launch.
        AiSettingsManager.initialize(context)
        return AiSettingsManager.sessionsFlow.value.map { it.id }
    }

    private fun putLegacy(raw: String) {
        assertTrue(preferences.edit().putString("chat_sessions", raw).commit())
    }

    private fun sessionFile(id: String) = File(historyDir, "$id.json")

    private fun session(id: String, timestamp: Long) = ChatSession(
        id = id,
        title = "New title $id",
        timestamp = timestamp,
        messages = listOf(ChatMessage(role = ChatRole.User, content = "new $id", timestamp = timestamp))
    )

    private fun checkpoint(chat: ChatSession) = ChatCompaction(
        origin = "local-provider",
        coveredMessages = chat.messages.size,
        lastMessageId = chat.messages.last().id,
        summary = "Earlier context"
    )

    private fun legacySession(id: String, timestamp: Long) =
        """{"id":"$id","title":"Old title $id","timestamp":$timestamp,"messages":[{"role":"User","content":"hello $id"}]}"""

    // Robolectric does not implement directory fsync and atomic rename; test the latter on disk.
    @Implements(Os::class)
    class HostOs {
        companion object {
            var failRenameTo: String? = null

            @JvmStatic
            @Implementation
            fun rename(oldPath: String, newPath: String) {
                if (newPath == failRenameTo) throw ErrnoException("rename", OsConstants.EIO)
                try {
                    Files.move(File(oldPath).toPath(), File(newPath).toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (e: IOException) {
                    throw ErrnoException("rename", OsConstants.EIO, e)
                }
            }

            @JvmStatic
            @Implementation
            fun open(path: String, flags: Int, mode: Int): FileDescriptor {
                check(File(path).isDirectory)
                return FileDescriptor()
            }

            @JvmStatic
            @Implementation
            fun fsync(fd: FileDescriptor) = Unit

            @JvmStatic
            @Implementation
            fun close(fd: FileDescriptor) = Unit
        }
    }
}

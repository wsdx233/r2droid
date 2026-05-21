package top.wsdx233.r2droid.feature.ai.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringReader

/**
 * File-backed AI chat history storage.
 *
 * Older builds stored the whole chat history as one SharedPreferences JSON string. Deleting one
 * session then had to re-encode every remaining message, which can allocate hundreds of MB for
 * long AI/action outputs. This store keeps metadata in a small index file and every conversation
 * in its own JSON file, so deleting a session is just a file delete plus a tiny index rewrite.
 *
 * JSON is read/written with Android's streaming JsonReader/JsonWriter instead of building one huge
 * JSON string in memory.
 */
class AiChatHistoryStore(context: Context) {
    private val historyDir = File(context.filesDir, HISTORY_DIR).apply { mkdirs() }
    private val indexFile = File(historyDir, INDEX_FILE_NAME)

    fun loadIndex(): List<ChatSessionMetadata> {
        if (!indexFile.isFile) return emptyList()
        return runCatching {
            JsonReader(InputStreamReader(indexFile.inputStream(), Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                val sessions = mutableListOf<ChatSessionMetadata>()
                reader.beginArray()
                while (reader.hasNext()) {
                    sessions.add(readMetadata(reader))
                }
                reader.endArray()
                sessions
            }
        }.getOrDefault(emptyList())
    }

    fun saveIndex(sessions: List<ChatSessionMetadata>) {
        writeJsonAtomic(indexFile) { writer ->
            writer.beginArray()
            sessions.forEach { writeMetadata(writer, it) }
            writer.endArray()
        }
    }

    fun loadSession(sessionId: String): ChatSession? {
        val file = sessionFile(sessionId)
        if (!file.isFile) return null
        return runCatching {
            JsonReader(InputStreamReader(file.inputStream(), Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                readSession(reader)
            }
        }.getOrNull()
    }

    fun saveSession(session: ChatSession) {
        writeJsonAtomic(sessionFile(session.id)) { writer ->
            writeSession(writer, session)
        }
    }

    fun deleteSession(sessionId: String) {
        val file = sessionFile(sessionId)
        if (file.exists()) file.delete()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        if (tmp.exists()) tmp.delete()
    }

    fun deleteSessionsNotIn(sessionIds: Set<String>) {
        val safeSessionIds = sessionIds.mapTo(mutableSetOf()) { safeFileName(it) }
        historyDir.listFiles { file ->
            file.isFile && file.name.endsWith(SESSION_FILE_SUFFIX)
        }?.forEach { file ->
            val id = file.name.removeSuffix(SESSION_FILE_SUFFIX)
            if (id !in safeSessionIds) file.delete()
        }
    }

    fun importLegacySessions(raw: String): List<ChatSessionMetadata>? {
        if (raw.isBlank()) return emptyList()
        val metadata = mutableListOf<ChatSessionMetadata>()
        return try {
            JsonReader(StringReader(raw)).use { reader ->
                reader.isLenient = true
                reader.beginArray()
                while (reader.hasNext()) {
                    val session = readSession(reader)
                    saveSession(session)
                    metadata.add(session.toMetadata())
                }
                reader.endArray()
            }
            saveIndex(metadata)
            metadata
        } catch (_: Exception) {
            null
        }
    }

    private fun sessionFile(sessionId: String): File = File(historyDir, safeFileName(sessionId) + SESSION_FILE_SUFFIX)

    private fun writeJsonAtomic(file: File, block: (JsonWriter) -> Unit) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { stream ->
            JsonWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { writer ->
                block(writer)
            }
        }
        if (file.exists() && !file.delete()) {
            tmp.delete()
            throw IllegalStateException("Failed to replace ${file.name}")
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IllegalStateException("Failed to write ${file.name}")
        }
    }

    private fun safeFileName(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun ChatSession.toMetadata() = ChatSessionMetadata(
        id = id,
        title = title,
        timestamp = timestamp
    )

    private fun readMetadata(reader: JsonReader): ChatSessionMetadata {
        var id = java.util.UUID.randomUUID().toString()
        var title = "Chat"
        var timestamp = System.currentTimeMillis()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextNullableString() ?: id
                "title" -> title = reader.nextNullableString().orEmpty().ifBlank { title }
                "timestamp" -> timestamp = reader.nextLongLenient(timestamp)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ChatSessionMetadata(id = id, title = title, timestamp = timestamp)
    }

    private fun writeMetadata(writer: JsonWriter, session: ChatSessionMetadata) {
        writer.beginObject()
        writer.name("id").value(session.id)
        writer.name("title").value(session.title)
        writer.name("timestamp").value(session.timestamp)
        writer.endObject()
    }

    private fun readSession(reader: JsonReader): ChatSession {
        var id = java.util.UUID.randomUUID().toString()
        var title = ""
        var timestamp = System.currentTimeMillis()
        val messages = mutableListOf<ChatMessage>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextNullableString() ?: id
                "title" -> title = reader.nextNullableString().orEmpty()
                "timestamp" -> timestamp = reader.nextLongLenient(timestamp)
                "messages" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        messages.add(readMessage(reader))
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (title.isBlank()) {
            title = messages.firstOrNull { it.role == ChatRole.User }?.content?.take(50).orEmpty()
        }
        if (title.isBlank()) title = "Chat"

        return ChatSession(
            id = id,
            title = title,
            timestamp = timestamp,
            messages = messages
        )
    }

    private fun writeSession(writer: JsonWriter, session: ChatSession) {
        writer.beginObject()
        writer.name("id").value(session.id)
        writer.name("title").value(session.title)
        writer.name("timestamp").value(session.timestamp)
        writer.name("messages")
        writer.beginArray()
        session.messages.forEach { writeMessage(writer, it) }
        writer.endArray()
        writer.endObject()
    }

    private fun readMessage(reader: JsonReader): ChatMessage {
        var id = java.util.UUID.randomUUID().toString()
        var role = ChatRole.User
        var content = ""
        var timestamp = System.currentTimeMillis()
        var isStreaming = false
        val actionResults = mutableListOf<ActionResult>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextNullableString() ?: id
                "role" -> role = reader.nextEnumOrDefault(role)
                "content" -> content = reader.nextNullableString().orEmpty()
                "timestamp" -> timestamp = reader.nextLongLenient(timestamp)
                "isStreaming" -> isStreaming = reader.nextBooleanLenient(false)
                "actionResults" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        actionResults.add(readActionResult(reader))
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ChatMessage(
            id = id,
            role = role,
            content = content,
            timestamp = timestamp,
            isStreaming = isStreaming,
            actionResults = actionResults
        )
    }

    private fun writeMessage(writer: JsonWriter, message: ChatMessage) {
        writer.beginObject()
        writer.name("id").value(message.id)
        writer.name("role").value(message.role.name)
        writer.name("content").value(message.content)
        writer.name("timestamp").value(message.timestamp)
        writer.name("isStreaming").value(message.isStreaming)
        writer.name("actionResults")
        writer.beginArray()
        message.actionResults.forEach { writeActionResult(writer, it) }
        writer.endArray()
        writer.endObject()
    }

    private fun readActionResult(reader: JsonReader): ActionResult {
        var type = ActionType.R2Command
        var input = ""
        var output = ""
        var success = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> type = reader.nextEnumOrDefault(type)
                "input" -> input = reader.nextNullableString().orEmpty()
                "output" -> output = reader.nextNullableString().orEmpty()
                "success" -> success = reader.nextBooleanLenient(false)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ActionResult(
            type = type,
            input = input,
            output = output,
            success = success
        )
    }

    private fun writeActionResult(writer: JsonWriter, result: ActionResult) {
        writer.beginObject()
        writer.name("type").value(result.type.name)
        writer.name("input").value(result.input)
        writer.name("output").value(result.output)
        writer.name("success").value(result.success)
        writer.endObject()
    }

    private inline fun <reified T : Enum<T>> JsonReader.nextEnumOrDefault(default: T): T {
        val value = nextNullableString() ?: return default
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }

    private fun JsonReader.nextNullableString(): String? {
        return when (peek()) {
            JsonToken.NULL -> {
                nextNull()
                null
            }
            JsonToken.STRING, JsonToken.NUMBER -> nextString()
            JsonToken.BOOLEAN -> nextBoolean().toString()
            else -> {
                skipValue()
                null
            }
        }
    }

    private fun JsonReader.nextLongLenient(default: Long): Long {
        return when (peek()) {
            JsonToken.NULL -> {
                nextNull()
                default
            }
            JsonToken.NUMBER, JsonToken.STRING -> nextString().toLongOrNull() ?: default
            else -> {
                skipValue()
                default
            }
        }
    }

    private fun JsonReader.nextBooleanLenient(default: Boolean): Boolean {
        return when (peek()) {
            JsonToken.NULL -> {
                nextNull()
                default
            }
            JsonToken.BOOLEAN -> nextBoolean()
            JsonToken.STRING, JsonToken.NUMBER -> {
                when (nextString().lowercase()) {
                    "true", "1" -> true
                    "false", "0" -> false
                    else -> default
                }
            }
            else -> {
                skipValue()
                default
            }
        }
    }

    companion object {
        private const val HISTORY_DIR = "ai_chat_history"
        private const val INDEX_FILE_NAME = "index.json"
        private const val SESSION_FILE_SUFFIX = ".json"
    }
}

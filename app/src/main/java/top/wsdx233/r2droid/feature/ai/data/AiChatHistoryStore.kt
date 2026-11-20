package top.wsdx233.r2droid.feature.ai.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.io.StringReader

/**
 * File-backed AI chat history storage.
 *
 * Older builds stored the whole chat history as one SharedPreferences JSON string. Deleting one
 * session then had to re-encode every remaining message, which can allocate hundreds of MB for
 * long AI/action outputs. This store keeps metadata in a small index file and every conversation
 * in its own JSON file, so deleting a session never reads other conversations.
 *
 * JSON is read/written with Android's streaming JsonReader/JsonWriter instead of building one huge
 * JSON string in memory.
 */
class AiChatHistoryStore(context: Context) {
    private val historyDir = File(context.filesDir, HISTORY_DIR).apply { mkdirs() }
    private val indexFile = File(historyDir, INDEX_FILE_NAME)

    fun loadIndex(): List<ChatSessionMetadata>? {
        if (!indexFile.isFile) return null
        return try {
            JsonReader(InputStreamReader(indexFile.inputStream(), Charsets.UTF_8)).use { reader ->
                val sessions = mutableListOf<ChatSessionMetadata>()
                val names = mutableSetOf<String>()
                reader.beginArray()
                while (reader.hasNext()) {
                    val metadata = readMetadata(reader)
                    val file = sessionFile(metadata.id)
                    check(names.add(file.name) && !isDeleted(metadata.id) && file.isFile)
                    sessions.add(metadata)
                }
                reader.endArray()
                check(reader.peek() == JsonToken.END_DOCUMENT)
                sessions
            }
        } catch (e: Exception) {
            Log.w(TAG, "Rebuilding unreadable chat index", e)
            null
        }
    }

    fun recoverIndex(): List<ChatSessionMetadata>? {
        val files = historyDir.listFiles { file ->
            file != indexFile && file.name.endsWith(SESSION_FILE_SUFFIX) && file.isFile
        } ?: return null
        return files.mapNotNull { file ->
            try {
                readSessionMetadata(file).takeUnless { isDeleted(it.id) }
            } catch (_: Exception) {
                // A broken or unrelated file is not a session. Leave it on disk.
                null
            }
        }.sortedByDescending { it.timestamp }
    }

    fun saveIndex(sessions: List<ChatSessionMetadata>) {
        writeJsonAtomic(indexFile) { writer ->
            writer.beginArray()
            sessions.forEach { writeMetadata(writer, it) }
            writer.endArray()
        }
        syncDirectory(historyDir)
    }

    fun loadSession(sessionId: String): ChatSession? {
        if (isDeleted(sessionId)) return null
        val file = sessionFile(sessionId)
        if (!file.isFile) return null
        return runCatching {
            JsonReader(InputStreamReader(file.inputStream(), Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                readSession(reader).takeIf { it.id == sessionId }
            }
        }.getOrNull()
    }

    fun saveSession(session: ChatSession): Boolean {
        if (isDeleted(session.id)) return false
        writeJsonAtomic(sessionFile(session.id)) { writer ->
            writeSession(writer, session)
        }
        return true
    }

    fun deleteSession(sessionId: String) {
        // Publish the deletion before unlinking the session or touching the index. An
        // interrupted delete or a retry of the legacy import must not bring it back.
        writeJsonAtomic(deletedFile(sessionId)) { writer ->
            writer.beginObject()
            writer.endObject()
        }
        syncDirectory(historyDir)
        val file = sessionFile(sessionId)
        if (file.isFile && !file.delete()) {
            throw IOException("Failed to delete ${file.name}")
        }
    }

    fun importLegacySessions(
        raw: String,
        existing: List<ChatSessionMetadata>
    ): List<ChatSessionMetadata>? {
        val metadata = existing.associateByTo(linkedMapOf()) { it.id }
        return try {
            JsonReader(StringReader(raw)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    val session = readSession(reader)
                    if (isDeleted(session.id)) continue
                    val file = sessionFile(session.id)
                    if (file.exists()) {
                        // Keep newer file data and make it durable even if an old build wrote it.
                        val stored = readSessionMetadata(file)
                        check(stored.id == session.id)
                        file.inputStream().use { it.fd.sync() }
                        metadata[stored.id] = stored
                    } else if (saveSession(session)) {
                        metadata[session.id] = session.toMetadata()
                    }
                }
                reader.endArray()
                check(reader.peek() == JsonToken.END_DOCUMENT)
            }
            val sessions = metadata.values.sortedByDescending { it.timestamp }
            saveIndex(sessions)
            // The directory itself may have been created during this migration.
            syncDirectory(checkNotNull(historyDir.parentFile))
            sessions
        } catch (e: Exception) {
            Log.w(TAG, "Leaving legacy chat history in preferences after incomplete import", e)
            null
        }
    }

    private fun writeJsonAtomic(file: File, block: (JsonWriter) -> Unit) {
        val tmp = File.createTempFile(".history-", ".tmp", historyDir)
        try {
            tmp.outputStream().use { stream ->
                JsonWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { writer ->
                    block(writer)
                    writer.flush()
                    stream.fd.sync()
                }
            }
            // Same-directory rename replaces the old file without ever unlinking it first.
            Os.rename(tmp.absolutePath, file.absolutePath)
        } finally {
            tmp.delete()
        }
    }

    private fun syncDirectory(directory: File) {
        val fd = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(fd)
        } finally {
            Os.close(fd)
        }
    }

    private fun sessionFile(sessionId: String): File = File(historyDir, safeFileName(sessionId) + SESSION_FILE_SUFFIX)

    private fun deletedFile(id: String): File = File(historyDir, safeFileName(id) + ".deleted")

    private fun isDeleted(id: String): Boolean = deletedFile(id).isFile

    private fun safeFileName(id: String): String {
        val name = id.replace(UNSAFE_FILENAME_CHAR, "_")
        return if (name + SESSION_FILE_SUFFIX == INDEX_FILE_NAME) "index~" else name
    }

    private fun readSessionMetadata(file: File): ChatSessionMetadata {
        // Both the old and current writer put these fields before messages. Android's
        // JsonReader.skipValue() can still allocate a huge string, so never enter the body.
        // The compact writer finishes with ]}; use that to reject obvious truncation.
        return RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            check(length >= 2)
            input.seek(length - 2)
            check(input.read() == ']'.code && input.read() == '}'.code)
            input.seek(0)
            val header = ByteArray(minOf(length, MAX_HEADER_BYTES.toLong()).toInt())
            input.readFully(header)
            JsonReader(StringReader(String(header, Charsets.UTF_8))).use { reader ->
                readMetadata(reader, session = true).also { check(sessionFile(it.id) == file) }
            }
        }
    }

    private fun readMetadata(reader: JsonReader, session: Boolean = false): ChatSessionMetadata {
        var id: String? = null
        var title: String? = null
        var timestamp: Long? = null
        var hasMessages = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> {
                    check(id == null && reader.peek() == JsonToken.STRING)
                    id = reader.nextString()
                }
                "title" -> {
                    check(title == null && reader.peek() == JsonToken.STRING)
                    title = reader.nextString()
                }
                "timestamp" -> {
                    check(timestamp == null && reader.peek() == JsonToken.NUMBER)
                    timestamp = reader.nextString().toLong()
                }
                "messages" -> {
                    check(session)
                    reader.beginArray()
                    hasMessages = true
                    break
                }
                else -> error("Unexpected chat metadata field")
            }
        }
        if (session) check(hasMessages) else reader.endObject()

        val sessionId = checkNotNull(id)
        check(sessionId.isNotBlank())
        return ChatSessionMetadata(sessionId, checkNotNull(title), checkNotNull(timestamp))
    }

    private fun ChatSession.toMetadata() = ChatSessionMetadata(
        id = id,
        title = title,
        timestamp = timestamp
    )

    private fun writeMetadata(writer: JsonWriter, session: ChatSessionMetadata) {
        writer.beginObject()
        writer.name("id").value(session.id)
        writer.name("title").value(session.title)
        writer.name("timestamp").value(session.timestamp)
        writer.endObject()
    }

    private fun readSession(reader: JsonReader): ChatSession {
        var id: String? = null
        var title = ""
        var timestamp = System.currentTimeMillis()
        val messages = mutableListOf<ChatMessage>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> {
                    check(id == null && reader.peek() == JsonToken.STRING)
                    id = reader.nextString()
                }
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
            id = checkNotNull(id).also { check(it.isNotBlank()) },
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
        private const val TAG = "AiChatHistoryStore"
        private const val MAX_HEADER_BYTES = 8 * 1024
        private val UNSAFE_FILENAME_CHAR = Regex("[^A-Za-z0-9._-]")
        private const val HISTORY_DIR = "ai_chat_history"
        private const val INDEX_FILE_NAME = "index.json"
        private const val SESSION_FILE_SUFFIX = ".json"
    }
}

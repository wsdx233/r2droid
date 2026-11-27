package top.wsdx233.r2droid.feature.bininfo.data

import android.util.JsonReader
import android.util.JsonToken
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.radare.r2pipe.kotlin.R2PipeSession
import top.wsdx233.r2droid.core.data.db.*
import top.wsdx233.r2droid.core.data.model.*
import top.wsdx233.r2droid.core.data.source.R2DataSource
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.util.R2Sanitizer
import java.io.InputStreamReader
import java.lang.ref.WeakReference

import javax.inject.Inject

/** Read a JSON number that may overflow signed Long (e.g. 0xFFFFFFFFFFFFFFFF from r2) or be null. */
private fun JsonReader.nextSafeAddr(default: Long = 0L): Long {
    val token = peek()
    if (token != JsonToken.NUMBER && token != JsonToken.STRING) {
        skipValue()
        return default
    }
    val raw = nextString().trim()
    return if (raw.startsWith("0x", ignoreCase = true)) {
        raw.drop(2).toULongOrNull(16)?.toLong() ?: default
    } else {
        raw.toLongOrNull() ?: raw.toULongOrNull()?.toLong() ?: default
    }
}

private fun JsonReader.nextSafeString(default: String = ""): String {
    val token = peek()
    return when (token) {
        JsonToken.STRING, JsonToken.NUMBER, JsonToken.BOOLEAN -> nextString()
        else -> {
            skipValue()
            default
        }
    }
}

private fun JsonReader.nextSafeInt(default: Int = 0): Int {
    val token = peek()
    if (token != JsonToken.NUMBER && token != JsonToken.STRING) {
        skipValue()
        return default
    }
    return nextString().trim().toIntOrNull() ?: default
}

private fun JsonReader.nextSectionNumber(): Long? {
    if (peek() != JsonToken.NUMBER && peek() != JsonToken.STRING) {
        skipValue()
        return null
    }
    val raw = nextString().trim()
    return if (raw.startsWith("0x", ignoreCase = true)) raw.drop(2).toULongOrNull(16)?.toLong()
    else raw.toLongOrNull() ?: raw.toULongOrNull()?.toLong()
}

class BinInfoRepository @Inject constructor(
    private val r2DataSource: R2DataSource,
    private val database: AppDatabase,
    private val stringDao: StringDao,
    private val sectionDao: SectionDao,
    private val symbolDao: SymbolDao,
    private val importDao: ImportDao,
    private val relocationDao: RelocationDao,
    private val functionDao: FunctionDao
) {
    private companion object {
        // Each Room cache table belongs to the active session; old and new writers must not interleave.
        val sectionSyncMutex = Mutex()
        val stringSyncMutex = Mutex()
        val symbolSyncMutex = Mutex()
        val importSyncMutex = Mutex()
        val relocationSyncMutex = Mutex()
        val functionSyncMutex = Mutex()
        var roomSectionOwner: WeakReference<R2PipeSession>? = null
    }

    suspend fun getOverview(): Result<BinInfo> {
        return r2DataSource.executeJson("ij").mapCatching { output ->
            if (output.isBlank()) throw RuntimeException("Empty response from r2")
            val json = R2Sanitizer.parseJsonObject(output)
            BinInfo.fromJson(json)
        }
    }

    suspend fun getSections(): Result<List<Section>> {
        return r2DataSource.executeJson("iSj").mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = R2Sanitizer.parseJsonArray(output)
            val list = mutableListOf<Section>()
            for (i in 0 until jsonArray.length()) {
                list.add(Section.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getSymbols(): Result<List<Symbol>> {
        return r2DataSource.executeJson("isj").mapCatching { output ->
             if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = R2Sanitizer.parseJsonArray(output)
            val list = mutableListOf<Symbol>()
            for (i in 0 until jsonArray.length()) {
                list.add(Symbol.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getImports(): Result<List<ImportInfo>> {
        return r2DataSource.executeJson("iij").mapCatching { output ->
             if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = R2Sanitizer.parseJsonArray(output)
            val list = mutableListOf<ImportInfo>()
            for (i in 0 until jsonArray.length()) {
                list.add(ImportInfo.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getExports(): Result<List<ExportInfo>> {
        return r2DataSource.executeJson("iEj").mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = R2Sanitizer.parseJsonArray(output)
            val list = mutableListOf<ExportInfo>()
            for (i in 0 until jsonArray.length()) {
                list.add(ExportInfo.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getRelocations(): Result<List<Relocation>> {
        return r2DataSource.executeJson("irj").mapCatching { output ->
             if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = R2Sanitizer.parseJsonArray(output)
            val list = mutableListOf<Relocation>()
            for (i in 0 until jsonArray.length()) {
                list.add(Relocation.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getStrings(useFullRange: Boolean = false): Result<List<StringInfo>> {
        val command = if (useFullRange) "izzj" else "izj"
        return r2DataSource.executeJson(command).mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = R2Sanitizer.parseJsonArray(output)
            val list = mutableListOf<StringInfo>()
            for (i in 0 until jsonArray.length()) {
                list.add(StringInfo.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    /**
     * 流式同步字符串到 Room 数据库。
     * 使用 JsonReader 逐条解析，在事务中分批写入，确保异常回滚且不串会话。
     */
    suspend fun syncStringsToDb(
        useFullRange: Boolean = false,
        session: R2PipeManager.R2Session? = R2PipeManager.activeSession()
    ): Result<Unit> = stringSyncMutex.withLock {
        val activeSession = session ?: return@withLock Result.failure(IllegalStateException("No active session"))
        val command = if (useFullRange) "izzj" else "izj"
        R2PipeManager.executeStream(activeSession, command) { inputStream ->
            database.withTransaction {
                check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during strings sync" }
                stringDao.clearAll()
                JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    reader.isLenient = true
                    val buffer = mutableListOf<StringEntity>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var vAddr = 0L; var str = ""; var type = ""; var section = ""
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "vaddr" -> vAddr = reader.nextSafeAddr()
                                "string" -> str = reader.nextSafeString()
                                "type" -> type = reader.nextSafeString()
                                "section" -> section = reader.nextSafeString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        buffer.add(StringEntity(vAddr = vAddr, string = str, type = type, section = section))
                        if (buffer.size >= 10000) {
                            check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during strings sync" }
                            stringDao.insertAll(buffer)
                            buffer.clear()
                        }
                    }
                    reader.endArray()
                    check(reader.peek() == JsonToken.END_DOCUMENT)
                    if (buffer.isNotEmpty()) {
                        check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during strings sync" }
                        stringDao.insertAll(buffer)
                    }
                }
            }
        }
    }

    suspend fun syncSectionsToDb(
        session: R2PipeManager.R2Session,
        captureSnapshot: Boolean,
        forceRefresh: Boolean = false
    ): Result<List<Section>> = sectionSyncMutex.withLock {
        val writeRoom = R2PipeManager.activeSessionPipe === session.pipe &&
            (forceRefresh || roomSectionOwner?.get() !== session.pipe)
        if (!writeRoom && !captureSnapshot) return@withLock Result.success(emptyList())
        val result = R2PipeManager.executeStream(session, "iSj") { inputStream ->
            val entities = if (writeRoom) mutableListOf<SectionEntity>() else null
            val snapshot = if (captureSnapshot) mutableListOf<Section>() else null
            JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                reader.isLenient = true
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.beginObject()
                    var name = ""; var size = 0L; var vSize = 0L; var perm = ""
                    var vAddr: Long? = null; var pAddr: Long? = null; var type = ""; var flags: Long? = null
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "name" -> name = reader.nextSafeString()
                            "size" -> size = reader.nextSectionNumber() ?: 0
                            "vsize" -> vSize = reader.nextSectionNumber() ?: 0
                            "perm" -> perm = reader.nextSafeString()
                            "vaddr" -> vAddr = reader.nextSectionNumber()
                            "paddr" -> pAddr = reader.nextSectionNumber()
                            "type" -> type = reader.nextSafeString()
                            "flags" -> flags = reader.nextSectionNumber()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    entities?.add(SectionEntity(vAddr = vAddr ?: 0, name = name, size = size, vSize = vSize, perm = perm, pAddr = pAddr ?: 0))
                    snapshot?.add(Section(name, size, vSize, perm, vAddr ?: -1, pAddr ?: -1, type, flags))
                }
                reader.endArray()
                check(reader.peek() == JsonToken.END_DOCUMENT)
            }
            if (entities != null && R2PipeManager.activeSessionPipe === session.pipe) {
                database.withTransaction {
                    check(R2PipeManager.activeSessionPipe === session.pipe) { "Session changed during sections sync" }
                    sectionDao.clearAll()
                    entities.chunked(10000).forEach { sectionDao.insertAll(it) }
                }
                roomSectionOwner = WeakReference(session.pipe)
            }
            snapshot.orEmpty()
        }
        result
    }

    suspend fun syncSymbolsToDb(
        session: R2PipeManager.R2Session? = R2PipeManager.activeSession()
    ): Result<Unit> = symbolSyncMutex.withLock {
        val activeSession = session ?: return@withLock Result.failure(IllegalStateException("No active session"))
        val result = R2PipeManager.executeStream(activeSession, "isj") { inputStream ->
            database.withTransaction {
                check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during symbols sync" }
                symbolDao.clearAll()
                JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    reader.isLenient = true
                    val buffer = mutableListOf<SymbolEntity>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var name = ""; var type = ""; var vAddr = 0L; var pAddr = 0L
                        var isImported = false; var realname: String? = null
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> name = reader.nextSafeString()
                                "type" -> type = reader.nextSafeString()
                                "vaddr" -> vAddr = reader.nextSafeAddr()
                                "paddr" -> pAddr = reader.nextSafeAddr()
                                "is_imported" -> isImported = when (reader.peek()) {
                                    JsonToken.BOOLEAN -> reader.nextBoolean()
                                    JsonToken.NUMBER -> reader.nextSafeInt() != 0
                                    JsonToken.STRING -> reader.nextString().let { it == "true" || it == "1" }
                                    else -> { reader.skipValue(); false }
                                }
                                "realname" -> realname = reader.nextSafeString().ifEmpty { null }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        buffer.add(SymbolEntity(name = name, type = type, vAddr = vAddr, pAddr = pAddr, isImported = isImported, realname = realname))
                        if (buffer.size >= 10000) {
                            check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during symbols sync" }
                            symbolDao.insertAll(buffer)
                            buffer.clear()
                        }
                    }
                    reader.endArray()
                    check(reader.peek() == JsonToken.END_DOCUMENT)
                    if (buffer.isNotEmpty()) {
                        check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during symbols sync" }
                        symbolDao.insertAll(buffer)
                    }
                }
            }
        }
        if (result.isFailure) {
            android.util.Log.e("SyncSymbols", "syncSymbolsToDb FAILED", result.exceptionOrNull())
        } else {
            android.util.Log.d("SyncSymbols", "syncSymbolsToDb SUCCESS")
        }
        result
    }

    suspend fun syncImportsToDb(
        session: R2PipeManager.R2Session? = R2PipeManager.activeSession()
    ): Result<Unit> = importSyncMutex.withLock {
        val activeSession = session ?: return@withLock Result.failure(IllegalStateException("No active session"))
        R2PipeManager.executeStream(activeSession, "iij") { inputStream ->
            database.withTransaction {
                check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during imports sync" }
                importDao.clearAll()
                JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    reader.isLenient = true
                    val buffer = mutableListOf<ImportEntity>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var name = ""; var ordinal = 0; var type = ""; var plt = 0L
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> name = reader.nextSafeString()
                                "ordinal" -> ordinal = reader.nextSafeInt()
                                "type" -> type = reader.nextSafeString()
                                "plt" -> plt = reader.nextSafeAddr()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        buffer.add(ImportEntity(name = name, ordinal = ordinal, type = type, plt = plt))
                        if (buffer.size >= 10000) {
                            check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during imports sync" }
                            importDao.insertAll(buffer)
                            buffer.clear()
                        }
                    }
                    reader.endArray()
                    check(reader.peek() == JsonToken.END_DOCUMENT)
                    if (buffer.isNotEmpty()) {
                        check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during imports sync" }
                        importDao.insertAll(buffer)
                    }
                }
            }
        }
    }

    suspend fun syncRelocationsToDb(
        session: R2PipeManager.R2Session? = R2PipeManager.activeSession()
    ): Result<Unit> = relocationSyncMutex.withLock {
        val activeSession = session ?: return@withLock Result.failure(IllegalStateException("No active session"))
        R2PipeManager.executeStream(activeSession, "irj") { inputStream ->
            database.withTransaction {
                check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during relocations sync" }
                relocationDao.clearAll()
                JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    reader.isLenient = true
                    val buffer = mutableListOf<RelocationEntity>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var name = ""; var type = ""; var vAddr = 0L; var pAddr = 0L
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> name = reader.nextSafeString()
                                "type" -> type = reader.nextSafeString()
                                "vaddr" -> vAddr = reader.nextSafeAddr()
                                "paddr" -> pAddr = reader.nextSafeAddr()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        buffer.add(RelocationEntity(name = name, type = type, vAddr = vAddr, pAddr = pAddr))
                        if (buffer.size >= 10000) {
                            check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during relocations sync" }
                            relocationDao.insertAll(buffer)
                            buffer.clear()
                        }
                    }
                    reader.endArray()
                    check(reader.peek() == JsonToken.END_DOCUMENT)
                    if (buffer.isNotEmpty()) {
                        check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during relocations sync" }
                        relocationDao.insertAll(buffer)
                    }
                }
            }
        }
    }

    suspend fun syncFunctionsToDb(
        session: R2PipeManager.R2Session? = R2PipeManager.activeSession()
    ): Result<Unit> = functionSyncMutex.withLock {
        val activeSession = session ?: return@withLock Result.failure(IllegalStateException("No active session"))
        R2PipeManager.executeStream(activeSession, "aflj") { inputStream ->
            database.withTransaction {
                check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during functions sync" }
                functionDao.clearAll()
                JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    reader.isLenient = true
                    val buffer = mutableListOf<FunctionEntity>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var name = ""; var addr = 0L; var size = 0L; var nbbs = 0; var signature = ""
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> name = reader.nextSafeString()
                                "addr", "offset" -> addr = reader.nextSafeAddr()
                                "size" -> size = reader.nextSafeAddr()
                                "nbbs" -> nbbs = reader.nextSafeInt()
                                "signature" -> signature = reader.nextSafeString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        buffer.add(FunctionEntity(addr, name, size, nbbs, signature))
                        if (buffer.size >= 10000) {
                            check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during functions sync" }
                            functionDao.insertAll(buffer)
                            buffer.clear()
                        }
                    }
                    reader.endArray()
                    check(reader.peek() == JsonToken.END_DOCUMENT)
                    if (buffer.isNotEmpty()) {
                        check(R2PipeManager.activeSessionPipe === activeSession.pipe) { "Session changed during functions sync" }
                        functionDao.insertAll(buffer)
                    }
                }
            }
        }
    }

    suspend fun getFunctions(): Result<List<FunctionInfo>> {
        return r2DataSource.executeJson("aflj").mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = JSONArray(output)
            val list = mutableListOf<FunctionInfo>()
            for (i in 0 until jsonArray.length()) {
                list.add(FunctionInfo.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getEntryPoints(): Result<List<EntryPoint>> {
        return r2DataSource.executeJson("iej").mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = JSONArray(output)
            val list = mutableListOf<EntryPoint>()
            for (i in 0 until jsonArray.length()) {
                list.add(EntryPoint.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }
}

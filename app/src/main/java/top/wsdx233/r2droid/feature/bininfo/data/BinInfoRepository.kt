package top.wsdx233.r2droid.feature.bininfo.data

import android.util.JsonReader
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.db.*
import top.wsdx233.r2droid.core.data.model.*
import top.wsdx233.r2droid.core.data.source.R2DataSource
import java.io.InputStreamReader

import javax.inject.Inject

/** Read a JSON number that may overflow signed Long (e.g. 0xFFFFFFFFFFFFFFFF from r2). */
private fun JsonReader.nextAddr(): Long {
    val s = nextString()  // lenient mode allows reading numbers as strings
    return s.toLongOrNull() ?: s.toULong().toLong()
}

class BinInfoRepository @Inject constructor(
    private val r2DataSource: R2DataSource,
    private val stringDao: StringDao,
    private val sectionDao: SectionDao,
    private val symbolDao: SymbolDao,
    private val importDao: ImportDao,
    private val relocationDao: RelocationDao,
    private val functionDao: FunctionDao
) {

    suspend fun getOverview(): Result<BinInfo> {
        return r2DataSource.executeJson("iIj").mapCatching { output ->
            if (output.isBlank()) throw RuntimeException("Empty response from r2")
            val json = JSONObject(output)
            BinInfo.fromJson(json)
        }
    }

    suspend fun getSections(): Result<List<Section>> {
        return r2DataSource.executeJson("iSj").mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = JSONArray(output)
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
            val jsonArray = JSONArray(output)
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
            val jsonArray = JSONArray(output)
            val list = mutableListOf<ImportInfo>()
            for (i in 0 until jsonArray.length()) {
                list.add(ImportInfo.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getRelocations(): Result<List<Relocation>> {
        return r2DataSource.executeJson("irj").mapCatching { output ->
             if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = JSONArray(output)
            val list = mutableListOf<Relocation>()
            for (i in 0 until jsonArray.length()) {
                list.add(Relocation.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getStrings(): Result<List<StringInfo>> {
        return r2DataSource.executeJson("izj").mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = JSONArray(output)
            val list = mutableListOf<StringInfo>()
            for (i in 0 until jsonArray.length()) {
                list.add(StringInfo.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    /**
     * 流式同步字符串到 Room 数据库。
     * 使用 JsonReader 逐条解析，每 5000 条批量写入，避免内存爆炸。
     */
    suspend fun syncStringsToDb(): Result<Unit> {
        stringDao.clearAll()
        return r2DataSource.executeStream("izj") { inputStream ->
            val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
            reader.isLenient = true
            val buffer = mutableListOf<StringEntity>()

            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var vAddr = 0L; var str = ""; var type = ""; var section = ""
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "vaddr" -> vAddr = reader.nextAddr()
                        "string" -> str = reader.nextString()
                        "type" -> type = reader.nextString()
                        "section" -> section = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                buffer.add(StringEntity(vAddr, str, type, section))
                if (buffer.size >= 10000) {
                    stringDao.insertAll(buffer)
                    buffer.clear()
                }
            }
            reader.endArray()
            reader.close()

            if (buffer.isNotEmpty()) {
                stringDao.insertAll(buffer)
            }
        }
    }

    suspend fun syncSectionsToDb(): Result<Unit> {
        sectionDao.clearAll()
        return r2DataSource.executeStream("iSj") { inputStream ->
            val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
            reader.isLenient = true
            val buffer = mutableListOf<SectionEntity>()
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var name = ""; var size = 0L; var vSize = 0L; var perm = ""; var vAddr = 0L; var pAddr = 0L
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> name = reader.nextString()
                        "size" -> size = reader.nextAddr()
                        "vsize" -> vSize = reader.nextAddr()
                        "perm" -> perm = reader.nextString()
                        "vaddr" -> vAddr = reader.nextAddr()
                        "paddr" -> pAddr = reader.nextAddr()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                buffer.add(SectionEntity(vAddr, name, size, vSize, perm, pAddr))
                if (buffer.size >= 10000) { sectionDao.insertAll(buffer); buffer.clear() }
            }
            reader.endArray(); reader.close()
            if (buffer.isNotEmpty()) sectionDao.insertAll(buffer)
        }
    }

    suspend fun syncSymbolsToDb(): Result<Unit> {
        symbolDao.clearAll()
        val result = r2DataSource.executeStream("isj") { inputStream ->
            val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
            reader.isLenient = true
            val buffer = mutableListOf<SymbolEntity>()
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var name = ""; var type = ""; var vAddr = 0L; var pAddr = 0L
                var isImported = false; var realname: String? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> name = reader.nextString()
                        "type" -> type = reader.nextString()
                        "vaddr" -> vAddr = reader.nextAddr()
                        "paddr" -> pAddr = reader.nextAddr()
                        "is_imported" -> isImported = when (reader.peek()) {
                            android.util.JsonToken.BOOLEAN -> reader.nextBoolean()
                            android.util.JsonToken.NUMBER -> reader.nextInt() != 0
                            android.util.JsonToken.STRING -> reader.nextString().let { it == "true" || it == "1" }
                            else -> { reader.skipValue(); false }
                        }
                        "realname" -> realname = reader.nextString().ifEmpty { null }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                buffer.add(SymbolEntity(name = name, type = type, vAddr = vAddr, pAddr = pAddr, isImported = isImported, realname = realname))
                if (buffer.size >= 10000) { symbolDao.insertAll(buffer); buffer.clear() }
            }
            reader.endArray(); reader.close()
            android.util.Log.d("SyncSymbols", "Parsed ${buffer.size} symbols, inserting to DB")
            if (buffer.isNotEmpty()) symbolDao.insertAll(buffer)
        }
        if (result.isFailure) {
            android.util.Log.e("SyncSymbols", "syncSymbolsToDb FAILED", result.exceptionOrNull())
        } else {
            android.util.Log.d("SyncSymbols", "syncSymbolsToDb SUCCESS")
        }
        return result
    }

    suspend fun syncImportsToDb(): Result<Unit> {
        importDao.clearAll()
        return r2DataSource.executeStream("iij") { inputStream ->
            val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
            reader.isLenient = true
            val buffer = mutableListOf<ImportEntity>()
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var name = ""; var ordinal = 0; var type = ""; var plt = 0L
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> name = reader.nextString()
                        "ordinal" -> ordinal = reader.nextInt()
                        "type" -> type = reader.nextString()
                        "plt" -> plt = reader.nextAddr()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                buffer.add(ImportEntity(name = name, ordinal = ordinal, type = type, plt = plt))
                if (buffer.size >= 10000) { importDao.insertAll(buffer); buffer.clear() }
            }
            reader.endArray(); reader.close()
            if (buffer.isNotEmpty()) importDao.insertAll(buffer)
        }
    }

    suspend fun syncRelocationsToDb(): Result<Unit> {
        relocationDao.clearAll()
        return r2DataSource.executeStream("irj") { inputStream ->
            val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
            reader.isLenient = true
            val buffer = mutableListOf<RelocationEntity>()
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var name = ""; var type = ""; var vAddr = 0L; var pAddr = 0L
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> name = reader.nextString()
                        "type" -> type = reader.nextString()
                        "vaddr" -> vAddr = reader.nextAddr()
                        "paddr" -> pAddr = reader.nextAddr()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                buffer.add(RelocationEntity(name = name, type = type, vAddr = vAddr, pAddr = pAddr))
                if (buffer.size >= 10000) { relocationDao.insertAll(buffer); buffer.clear() }
            }
            reader.endArray(); reader.close()
            if (buffer.isNotEmpty()) relocationDao.insertAll(buffer)
        }
    }

    suspend fun syncFunctionsToDb(): Result<Unit> {
        functionDao.clearAll()
        return r2DataSource.executeStream("aflj") { inputStream ->
            val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
            reader.isLenient = true
            val buffer = mutableListOf<FunctionEntity>()
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var name = ""; var addr = 0L; var size = 0L; var nbbs = 0; var signature = ""
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> name = reader.nextString()
                        "addr", "offset" -> addr = reader.nextAddr()
                        "size" -> size = reader.nextAddr()
                        "nbbs" -> nbbs = reader.nextInt()
                        "signature" -> signature = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                buffer.add(FunctionEntity(addr, name, size, nbbs, signature))
                if (buffer.size >= 10000) { functionDao.insertAll(buffer); buffer.clear() }
            }
            reader.endArray(); reader.close()
            if (buffer.isNotEmpty()) functionDao.insertAll(buffer)
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

package top.wsdx233.r2droid.data.repository

import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.data.model.*
import top.wsdx233.r2droid.util.R2PipeManager

/**
 * Repository to fetch analysis data from R2Pipe.
 */
class ProjectRepository {

    suspend fun getOverview(): Result<BinInfo> {
        // iIj: Binary Info
        return R2PipeManager.executeJson("iIj").mapCatching { output ->
            if (output.isBlank()) throw RuntimeException("Empty response from r2")
            val json = JSONObject(output)
            BinInfo.fromJson(json)
        }
    }

    /**
     * Get file size using r2 command.
     * `?v $s` returns the file size in hex, we convert to decimal.
     */
    suspend fun getFileSize(): Result<Long> {
        return R2PipeManager.execute("?v \$s").mapCatching { output ->
            val trimmed = output.toString().trim()
            // Parse hex value (e.g., "0x1234" or just "1234")
            if (trimmed.startsWith("0x", ignoreCase = true)) {
                trimmed.drop(2).toLong(16)
            } else {
                trimmed.toLongOrNull() ?: 0L
            }
        }
    }

    suspend fun getSections(): Result<List<Section>> {
        // iSj: Sections
        return R2PipeManager.executeJson("iSj").mapCatching { output ->
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
        // isj: Symbols
        return R2PipeManager.executeJson("isj").mapCatching { output ->
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
        // iij: Imports (Standard r2 command)
        return R2PipeManager.executeJson("iij").mapCatching { output ->
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
        // irj: Relocations
        return R2PipeManager.executeJson("irj").mapCatching { output ->
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
        // izj: Strings
        return R2PipeManager.executeJson("izj").mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = JSONArray(output)
            val list = mutableListOf<StringInfo>()
            for (i in 0 until jsonArray.length()) {
                list.add(StringInfo.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getFunctions(): Result<List<FunctionInfo>> {
        // aflj: Function List
        return R2PipeManager.executeJson("aflj").mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = JSONArray(output)
            val list = mutableListOf<FunctionInfo>()
            for (i in 0 until jsonArray.length()) {
                list.add(FunctionInfo.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }


    suspend fun getHexDump(offset: Long, length: Int): Result<ByteArray> {
        // pxj: Hex Dump
        val cmd = "pxj $length @ $offset"
        return R2PipeManager.executeJson(cmd).mapCatching { output ->
             if (output.isBlank()) return@mapCatching ByteArray(0)
             val jsonArray = JSONArray(output)
             val bytes = ByteArray(jsonArray.length())
             for (i in 0 until jsonArray.length()) {
                 bytes[i] = jsonArray.getInt(i).toByte()
             }
             bytes
        }
    }

    suspend fun getDisassembly(offset: Long, count: Int): Result<List<DisasmInstruction>> {
        // pdj: Print Disassembly
        val cmd = "pdj $count @ $offset"
        return R2PipeManager.executeJson(cmd).mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = JSONArray(output)
            val list = mutableListOf<DisasmInstruction>()
            for (i in 0 until jsonArray.length()) {
                list.add(DisasmInstruction.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getDecompilation(offset: Long): Result<DecompilationData> {
        // pdgj: Print Decompiled Code (User specified command/format)
        // Note: standard r2 might behave differently, assuming "pdgj" returns the structure provided by user.
        val cmd = "pdgj @ $offset"
         return R2PipeManager.executeJson(cmd).mapCatching { output ->
            if (output.isBlank()) throw RuntimeException("Empty decompilation output")
            val json = JSONObject(output)
            DecompilationData.fromJson(json)
        }
    }
    suspend fun getFunctionStart(addr: Long): Result<Long> {
        // Find function containing addr.
        // afij @ addr returns function info, including offset (start).
        // If not in function, it might return empty or error.
        val cmd = "afij @ $addr"
        return R2PipeManager.executeJson(cmd).mapCatching { output ->
             if (output.isBlank() || output == "[]") {
                  // Fallback: Just return the address if not found? 
                  // Or assume it's a function start?
                  // Let's return the address itself to be safe, or error?
                  // User wants "get function where pointer is located".
                  // If not in function, this fails.
                  // Try `isj` to find closest symbol?
                  // For now return addr.
                  return@mapCatching addr
             }
             val jsonArray = JSONArray(output)
             if (jsonArray.length() > 0) {
                 val funcInfo = jsonArray.getJSONObject(0)
                 funcInfo.optLong("offset", addr)
             } else {
                 addr
             }
        }
    }


    suspend fun getEntryPoints(): Result<List<EntryPoint>> {
        // iej: Entry Points
        return R2PipeManager.executeJson("iej").mapCatching { output ->
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



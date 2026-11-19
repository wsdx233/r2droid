package top.wsdx233.r2droid.feature.disasm.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.core.data.model.FunctionDetailInfo
import top.wsdx233.r2droid.core.data.model.FunctionVariable
import top.wsdx233.r2droid.core.data.model.FunctionVariablesData
import top.wsdx233.r2droid.core.data.model.FunctionXref
import top.wsdx233.r2droid.core.data.model.Xref
import top.wsdx233.r2droid.core.data.model.XrefWithDisasm
import top.wsdx233.r2droid.core.data.model.XrefsData
import top.wsdx233.r2droid.core.data.source.R2DataSource
import top.wsdx233.r2droid.util.R2PipeManager

import javax.inject.Inject
class DisasmRepository @Inject constructor(private val r2DataSource: R2DataSource) {

    suspend fun getDisassembly(offset: Long, count: Int): Result<List<DisasmInstruction>> =
        getDisassembly(offset, count, r2DataSource::executeJson, r2DataSource::execute)

    internal suspend fun getDisassembly(session: R2PipeManager.R2Session, offset: Long, count: Int): Result<List<DisasmInstruction>> =
        getDisassembly(offset, count,
            { R2PipeManager.execute(session, it, markDirty = false) },
            { R2PipeManager.execute(session, it) })

    private suspend fun getDisassembly(
        offset: Long, count: Int,
        executeJson: suspend (String) -> Result<String>,
        execute: suspend (String) -> Result<String>
    ): Result<List<DisasmInstruction>> = withContext(Dispatchers.Default) {
        try {
            // R2 serializes the pipe itself; only CPU parsing/model construction moves off Main.
            val output = executeJson("pdj $count @ $offset").getOrThrow()
            currentCoroutineContext().ensureActive()
            val list = if (output.isBlank()) emptyList() else {
                val jsonArray = JSONArray(output)
                List(jsonArray.length()) { i ->
                    currentCoroutineContext().ensureActive()
                    DisasmInstruction.fromJson(jsonArray.getJSONObject(i))
                }
            }
            Result.success(resolveStringRefs(list, execute))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Resolve STRN refs: fetch string content at ref addresses and append to instruction comments.
     */
    private suspend fun resolveStringRefs(
        instructions: List<DisasmInstruction>,
        execute: suspend (String) -> Result<String>
    ): List<DisasmInstruction> {
        val strnAddrs = instructions.flatMap { instr ->
            instr.refs.filter { it.type == "STRN" }.map { it.addr }
        }.distinct()
        if (strnAddrs.isEmpty()) return instructions

        // Fetch string for each unique STRN address
        val stringMap = mutableMapOf<Long, String>()
        for (addr in strnAddrs) {
            try {
                currentCoroutineContext().ensureActive()
                val result = execute("ps @ $addr")
                val str = result.getOrThrow().trim()
                if (!str.isNullOrEmpty()) stringMap[addr] = str
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {}
        }
        if (stringMap.isEmpty()) return instructions

        return instructions.map { instr ->
            val strnRefs = instr.refs.filter { it.type == "STRN" && stringMap.containsKey(it.addr) }
            if (strnRefs.isEmpty()) return@map instr
            val refStr = strnRefs.joinToString(" ; ") { "\"${stringMap[it.addr]}\"" }
            val newComment = if (instr.comment.isNullOrEmpty()) refStr else "${instr.comment} ; $refStr"
            instr.copy(comment = newComment)
        }
    }

    suspend fun getXrefs(addr: Long): Result<XrefsData> {
        return runCatching {
            // Get refs FROM current address (axfj)
            val axfResult = r2DataSource.executeJson("axfj @ $addr")
            val refsFrom = mutableListOf<XrefWithDisasm>()
            if (axfResult.isSuccess) {
                val output = axfResult.getOrDefault("")
                if (output.isNotBlank()) {
                    val jsonArray = JSONArray(output)
                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        val toAddr = json.optLong("to", 0)
                        val fcnName = getFunctionNameForAddress(toAddr)
                        val xref = Xref(
                            type = json.optString("type", ""),
                            from = json.optLong("from", 0),
                            to = toAddr,
                            opcode = json.optString("opcode", ""),
                            fcnName = fcnName,
                            refName = ""
                        )
                        val disasmInfo = getDisasmForAddress(xref.to)
                        refsFrom.add(XrefWithDisasm(
                            xref = xref,
                            disasm = disasmInfo.first,
                            instrType = disasmInfo.second,
                            bytes = disasmInfo.third
                        ))
                    }
                }
            }
            
            // Get refs TO current address (axtj)
            val axtResult = r2DataSource.executeJson("axtj @ $addr")
            val refsTo = mutableListOf<XrefWithDisasm>()
            if (axtResult.isSuccess) {
                val output = axtResult.getOrDefault("")
                if (output.isNotBlank()) {
                    val jsonArray = JSONArray(output)
                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        val xref = Xref(
                            type = json.optString("type", ""),
                            from = json.optLong("from", 0),
                            to = addr, 
                            opcode = json.optString("opcode", ""),
                            fcnName = json.optString("fcn_name", ""),
                            refName = json.optString("refname", "")
                        )
                        val disasmInfo = getDisasmForAddress(xref.from)
                        refsTo.add(XrefWithDisasm(
                            xref = xref,
                            disasm = disasmInfo.first,
                            instrType = disasmInfo.second,
                            bytes = disasmInfo.third
                        ))
                    }
                }
            }
            
            XrefsData(refsFrom = refsFrom, refsTo = refsTo)
        }
    }

    private suspend fun getDisasmForAddress(addr: Long): Triple<String, String, String> {
        return try {
            val result = r2DataSource.executeJson("pdj 1 @ $addr")
            if (result.isSuccess) {
                val output = result.getOrDefault("")
                if (output.isNotBlank()) {
                    val jsonArray = JSONArray(output)
                    if (jsonArray.length() > 0) {
                        val json = jsonArray.getJSONObject(0)
                        return Triple(
                            json.optString("disasm", json.optString("opcode", "")),
                            json.optString("type", ""),
                            json.optString("bytes", "")
                        )
                    }
                }
            }
            Triple("", "", "")
        } catch (e: Exception) {
            Triple("", "", "")
        }
    }

    // === Function Operations ===

    suspend fun analyzeFunction(addr: Long): Result<String> {
        return r2DataSource.execute("af @ $addr")
    }

    suspend fun getFunctionDetail(addr: Long): Result<FunctionDetailInfo?> {
        return r2DataSource.executeJson("afij @ $addr").mapCatching { output ->
            if (output.isBlank() || output == "[]") return@mapCatching null
            val jsonArray = JSONArray(output)
            if (jsonArray.length() > 0) {
                FunctionDetailInfo.fromJson(jsonArray.getJSONObject(0))
            } else null
        }
    }

    suspend fun getFunctionXrefs(addr: Long): Result<List<FunctionXref>> {
        return r2DataSource.executeJson("afxj @ $addr").mapCatching { output ->
            if (output.isBlank() || output == "[]") return@mapCatching emptyList()
            val jsonArray = JSONArray(output)
            val list = mutableListOf<FunctionXref>()
            for (i in 0 until jsonArray.length()) {
                list.add(FunctionXref.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getFunctionVariables(addr: Long): Result<FunctionVariablesData> {
        return r2DataSource.executeJson("afvj @ $addr").mapCatching { output ->
            if (output.isBlank() || output == "{}") return@mapCatching FunctionVariablesData()
            val json = JSONObject(output)
            fun parseVarArray(key: String, storage: String): List<FunctionVariable> {
                val arr = json.optJSONArray(key) ?: return emptyList()
                val list = mutableListOf<FunctionVariable>()
                for (i in 0 until arr.length()) {
                    list.add(FunctionVariable.fromJson(arr.getJSONObject(i), storage))
                }
                return list
            }
            FunctionVariablesData(
                reg = parseVarArray("reg", "reg"),
                sp = parseVarArray("sp", "sp"),
                bp = parseVarArray("bp", "bp")
            )
        }
    }

    suspend fun renameFunction(addr: Long, newName: String): Result<String> {
        return r2DataSource.execute("afn $newName @ $addr")
    }

    suspend fun renameFunctionVariable(addr: Long, newName: String, oldName: String): Result<String> {
        return r2DataSource.execute("afvn $newName $oldName @ $addr")
    }

    suspend fun getInstructionDetail(addr: Long): Result<top.wsdx233.r2droid.core.data.model.InstructionDetail?> {
        return r2DataSource.executeJson("aoj @ $addr").mapCatching { output ->
            if (output.isBlank() || output == "[]") return@mapCatching null
            val jsonArray = JSONArray(output)
            if (jsonArray.length() > 0) {
                top.wsdx233.r2droid.core.data.model.InstructionDetail.fromJson(jsonArray.getJSONObject(0))
            } else null
        }
    }

    private suspend fun getFunctionNameForAddress(addr: Long): String {
        return try {
            val result = r2DataSource.executeJson("afij @ $addr")
            if (result.isSuccess) {
                val output = result.getOrDefault("")
                if (output.isNotBlank() && output != "[]") {
                    val jsonArray = JSONArray(output)
                    if (jsonArray.length() > 0) {
                        val json = jsonArray.getJSONObject(0)
                        return json.optString("name", "")
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }
}

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
import top.wsdx233.r2droid.util.R2Sanitizer
import top.wsdx233.r2droid.util.optUnsignedLong

import javax.inject.Inject
class DisasmRepository @Inject constructor(private val r2DataSource: R2DataSource) {

    suspend fun getDisassembly(offset: Long, count: Int): Result<List<DisasmInstruction>> =
        getDisassembly(offset, count, r2DataSource::executeJson, r2DataSource::execute)

    internal suspend fun getDisassembly(session: R2PipeManager.R2Session, offset: Long, count: Int, annotations: DisasmAnnotations): Result<List<DisasmInstruction>> =
        getDisassembly(offset, count,
            { R2PipeManager.execute(session, it, markDirty = false) },
            { R2PipeManager.execute(session, it, markDirty = false) }, annotations)

    internal suspend fun loadAnnotations(session: R2PipeManager.R2Session): DisasmAnnotations = withContext(Dispatchers.Default) {
        DisasmAnnotations.load { R2PipeManager.execute(session, it, markDirty = false).getOrDefault("") }
    }

    private suspend fun getDisassembly(
        offset: Long, count: Int,
        executeJson: suspend (String) -> Result<String>,
        execute: suspend (String) -> Result<String>,
        annotations: DisasmAnnotations = DisasmAnnotations.Empty
    ): Result<List<DisasmInstruction>> = withContext(Dispatchers.Default) {
        try {
            // R2 serializes the pipe itself; only CPU parsing/model construction moves off Main.
            val start = annotations.rowStart(offset)
            val output = executeJson("pdj $count @ ${start.toHexAddr()}").getOrThrow()
            currentCoroutineContext().ensureActive()
            val list = if (output.isBlank()) emptyList() else {
                val jsonArray = R2Sanitizer.parseJsonArray(output)
                List(jsonArray.length()) { i ->
                    currentCoroutineContext().ensureActive()
                    DisasmInstruction.fromJson(jsonArray.getJSONObject(i))
                }
            }
            val annotated = annotations.render(list) { addr, size -> execute("p8 $size @ ${addr.toHexAddr()}").getOrThrow().trim() }
            Result.success(resolveStringRefs(annotated, execute))
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
                val result = execute("ps @ ${addr.toHexAddr()}")
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
            val hexAddr = addr.toHexAddr()
            // Get refs FROM current address (axfj)
            val axfResult = r2DataSource.executeJson("axfj @ $hexAddr")
            val refsFrom = mutableListOf<XrefWithDisasm>()
            if (axfResult.isSuccess) {
                val output = axfResult.getOrDefault("")
                if (output.isNotBlank()) {
                    val jsonArray = R2Sanitizer.parseJsonArray(output)
                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        val toAddr = json.optUnsignedLong("to", 0L)
                        val fcnName = getFunctionNameForAddress(toAddr)
                        val xref = Xref(
                            type = json.optString("type", ""),
                            from = json.optUnsignedLong("from", 0L),
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
            val axtResult = r2DataSource.executeJson("axtj @ $hexAddr")
            val refsTo = mutableListOf<XrefWithDisasm>()
            if (axtResult.isSuccess) {
                val output = axtResult.getOrDefault("")
                if (output.isNotBlank()) {
                    val jsonArray = R2Sanitizer.parseJsonArray(output)
                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        val xref = Xref(
                            type = json.optString("type", ""),
                            from = json.optUnsignedLong("from", 0L),
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
            val result = r2DataSource.executeJson("pdj 1 @ ${addr.toHexAddr()}")
            if (result.isSuccess) {
                val output = result.getOrDefault("")
                if (output.isNotBlank()) {
                    val jsonArray = R2Sanitizer.parseJsonArray(output)
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
        return r2DataSource.execute("af @ ${addr.toHexAddr()}")
    }

    suspend fun getFunctionDetail(addr: Long): Result<FunctionDetailInfo?> {
        return r2DataSource.executeJson("afij @ ${addr.toHexAddr()}").mapCatching { output ->
            if (output.isBlank() || output == "[]") return@mapCatching null
            val jsonArray = R2Sanitizer.parseJsonArray(output)
            if (jsonArray.length() > 0) {
                FunctionDetailInfo.fromJson(jsonArray.getJSONObject(0))
            } else null
        }
    }

    suspend fun getFunctionXrefs(addr: Long): Result<List<FunctionXref>> {
        return r2DataSource.executeJson("afxj @ ${addr.toHexAddr()}").mapCatching { output ->
            if (output.isBlank() || output == "[]") return@mapCatching emptyList()
            val jsonArray = R2Sanitizer.parseJsonArray(output)
            val list = mutableListOf<FunctionXref>()
            for (i in 0 until jsonArray.length()) {
                list.add(FunctionXref.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun getFunctionVariables(addr: Long): Result<FunctionVariablesData> {
        return r2DataSource.executeJson("afvj @ ${addr.toHexAddr()}").mapCatching { output ->
            if (output.isBlank() || output == "{}") return@mapCatching FunctionVariablesData()
            val json = R2Sanitizer.parseJsonObject(output)
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
        val cleanName = newName.trim()
        if (!top.wsdx233.r2droid.util.R2Sanitizer.isSafeIdentifier(cleanName)) {
            return Result.failure(IllegalArgumentException("Invalid function name."))
        }
        return r2DataSource.execute("afn $cleanName @ ${addr.toHexAddr()}")
    }

    suspend fun renameFunctionVariable(addr: Long, newName: String, oldName: String): Result<String> {
        val cleanNew = newName.trim()
        val cleanOld = oldName.trim()
        if (!top.wsdx233.r2droid.util.R2Sanitizer.isSafeIdentifier(cleanNew) ||
            !top.wsdx233.r2droid.util.R2Sanitizer.isSafeIdentifier(cleanOld)
        ) {
            return Result.failure(IllegalArgumentException("Invalid variable name."))
        }
        return r2DataSource.execute("afvn $cleanNew $cleanOld @ ${addr.toHexAddr()}")
    }

    suspend fun retypeFunctionVariable(addr: Long, varName: String, newType: String): Result<String> {
        val cleanVar = varName.trim()
        val cleanType = newType.trim().trim('"').trim()
        if (!top.wsdx233.r2droid.util.R2Sanitizer.isSafeIdentifier(cleanVar)) {
            return Result.failure(IllegalArgumentException("Invalid variable name."))
        }
        if (cleanType.isEmpty() || top.wsdx233.r2droid.util.R2Sanitizer.hasCommandInjection(cleanType) ||
            cleanType.any { it == '"' || it == '@' || it == '>' || it == '<' }
        ) {
            return Result.failure(IllegalArgumentException("Invalid variable type."))
        }
        val cmd = "afvt $cleanVar \"$cleanType\" @ ${addr.toHexAddr()}"
        return executeCheckedMutation(cmd)
    }

    suspend fun setFunctionSignature(addr: Long, signature: String): Result<String> {
        val cleanSig = signature.replace('\r', ' ').replace('\n', ' ').trim().trimEnd(';').trim()
        if (cleanSig.isEmpty() || top.wsdx233.r2droid.util.R2Sanitizer.hasCommandInjection(cleanSig) ||
            cleanSig.any { it == '@' || it == '>' || it == '<' }
        ) {
            return Result.failure(IllegalArgumentException("Invalid function signature."))
        }
        val cmd = "afs $cleanSig @ ${addr.toHexAddr()}"
        return executeCheckedMutation(cmd)
    }

    suspend fun setFunctionCallingConvention(addr: Long, callingConvention: String): Result<String> {
        val cleanCc = callingConvention.trim()
        if (!top.wsdx233.r2droid.util.R2Sanitizer.isSafeIdentifier(cleanCc)) {
            return Result.failure(IllegalArgumentException("Invalid calling convention."))
        }
        val cmd = "afc $cleanCc @ ${addr.toHexAddr()}"
        return executeCheckedMutation(cmd)
    }

    suspend fun getAvailableCallingConventions(): Result<List<top.wsdx233.r2droid.feature.types.data.CallingConventionInfo>> {
        return top.wsdx233.r2droid.util.suspendRunCatching {
            val tccj = r2DataSource.executeJson("tccj").getOrDefault("")
            val parsed = top.wsdx233.r2droid.feature.types.data.TypesJsonParser.parseTccj(tccj)
            if (parsed.isNotEmpty()) return@suspendRunCatching parsed
            val afcl = r2DataSource.execute("afcl").getOrDefault("")
            top.wsdx233.r2droid.feature.types.data.TypesJsonParser.parseAfclFallback(afcl)
        }
    }

    suspend fun getAvailableTypeSuggestions(): Result<List<String>> {
        return top.wsdx233.r2droid.util.suspendRunCatching {
            val tsj = r2DataSource.executeJson("tsj").getOrDefault("[]")
            val tej = r2DataSource.executeJson("tej").getOrDefault("{}")
            val tuj = r2DataSource.executeJson("tuj").getOrDefault("[]")
            val ttj = r2DataSource.executeJson("ttj").getOrDefault("{}")
            val tj = r2DataSource.executeJson("tj").getOrDefault("{}")
            val snapshot = top.wsdx233.r2droid.feature.types.data.LocalTypesSnapshot(
                structs = top.wsdx233.r2droid.feature.types.data.TypesJsonParser.parseTsj(tsj),
                enums = top.wsdx233.r2droid.feature.types.data.TypesJsonParser.parseTej(tej),
                unions = top.wsdx233.r2droid.feature.types.data.TypesJsonParser.parseTuj(tuj),
                typedefs = top.wsdx233.r2droid.feature.types.data.TypesJsonParser.parseTtj(ttj),
                primitives = top.wsdx233.r2droid.feature.types.data.TypesJsonParser.parseTj(tj)
            )
            snapshot.allTypeSuggestions()
        }
    }

    private suspend fun executeCheckedMutation(command: String): Result<String> {
        val target = R2PipeManager.captureActiveTarget()
        return if (target != null) {
            top.wsdx233.r2droid.util.suspendRunCatching {
                target.checkedTransaction(command, markDirty = true) { cmd -> cmd(command) }
            }
        } else {
            r2DataSource.execute(command)
        }
    }

    private fun Long.toHexAddr(): String = "0x${toULong().toString(16)}"

    suspend fun getInstructionDetail(addr: Long): Result<top.wsdx233.r2droid.core.data.model.InstructionDetail?> {
        return r2DataSource.executeJson("aoj @ ${addr.toHexAddr()}").mapCatching { output ->
            if (output.isBlank() || output == "[]") return@mapCatching null
            val jsonArray = R2Sanitizer.parseJsonArray(output)
            if (jsonArray.length() > 0) {
                top.wsdx233.r2droid.core.data.model.InstructionDetail.fromJson(jsonArray.getJSONObject(0))
            } else null
        }
    }

    private suspend fun getFunctionNameForAddress(addr: Long): String {
        return try {
            val result = r2DataSource.executeJson("afij @ ${addr.toHexAddr()}")
            if (result.isSuccess) {
                val output = result.getOrDefault("")
                if (output.isNotBlank() && output != "[]") {
                    val jsonArray = R2Sanitizer.parseJsonArray(output)
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

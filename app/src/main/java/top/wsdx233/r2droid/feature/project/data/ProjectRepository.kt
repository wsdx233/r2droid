package top.wsdx233.r2droid.feature.project.data

/**
 * Repository to fetch analysis data from R2Pipe.
 */
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.ArchInfo
import top.wsdx233.r2droid.core.data.model.BinInfo
import top.wsdx233.r2droid.core.data.model.BlockStatsData
import top.wsdx233.r2droid.core.data.model.DecompilationData
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.core.data.model.EntropyData
import top.wsdx233.r2droid.core.data.model.EntryPoint
import top.wsdx233.r2droid.core.data.model.FunctionDetailInfo
import top.wsdx233.r2droid.core.data.model.FunctionInfo
import top.wsdx233.r2droid.core.data.model.FunctionVariable
import top.wsdx233.r2droid.core.data.model.FunctionVariablesData
import top.wsdx233.r2droid.core.data.model.FunctionXref
import top.wsdx233.r2droid.core.data.model.GraphData
import top.wsdx233.r2droid.core.data.model.HashInfo
import top.wsdx233.r2droid.core.data.model.HeaderInfo
import top.wsdx233.r2droid.core.data.model.HistoryEntry
import top.wsdx233.r2droid.core.data.model.ImportInfo
import top.wsdx233.r2droid.core.data.model.MainAddressInfo
import top.wsdx233.r2droid.core.data.model.Relocation
import top.wsdx233.r2droid.core.data.model.Section
import top.wsdx233.r2droid.core.data.model.StringInfo
import top.wsdx233.r2droid.core.data.model.Symbol
import top.wsdx233.r2droid.core.data.model.Xref
import top.wsdx233.r2droid.core.data.model.XrefWithDisasm
import top.wsdx233.r2droid.core.data.model.XrefsData
import top.wsdx233.r2droid.core.data.parser.CLexer
import top.wsdx233.r2droid.feature.ai.data.AiRepository
import top.wsdx233.r2droid.feature.ai.data.AiSettingsManager
import top.wsdx233.r2droid.feature.ai.data.ChatMessage
import top.wsdx233.r2droid.feature.ai.data.ChatRole
import top.wsdx233.r2droid.util.R2PipeManager
import java.util.Locale
import javax.inject.Inject

class ProjectRepository @Inject constructor(
    private val aiRepository: AiRepository
) {

    suspend fun getOverview(): Result<BinInfo> {
        return runCatching {
            val iIjOutput = R2PipeManager.executeJson("iIj").getOrThrow()
            if (iIjOutput.isBlank()) throw RuntimeException("Empty response from r2")
            val baseInfo = BinInfo.fromJson(JSONObject(iIjOutput))
            
            val entropyOut = R2PipeManager.executeJson("p=ej 64").getOrDefault("{}")
            val entropyData = EntropyData.fromJson(JSONObject(entropyOut))

            val blockStatsOut = R2PipeManager.executeJson("p-j 64 @ 0").getOrDefault("{}")
            val blockStats = BlockStatsData.fromJson(JSONObject(blockStatsOut))
            
            val hashesOut = R2PipeManager.executeJson("itj").getOrDefault("{}")
            val hashes = HashInfo.fromJson(JSONObject(hashesOut))
            
            val mainAddrOut = R2PipeManager.executeJson("iMj").getOrDefault("{}")
            val mainAddr = MainAddressInfo.fromJson(JSONObject(mainAddrOut))
            
            val guessedSizeOut = R2PipeManager.execute("igj").getOrDefault("0").trim()
            val guessedSize = guessedSizeOut.removePrefix("0x").toLongOrNull(16) ?: 0L
            
            val entryPointsOut = R2PipeManager.executeJson("iej").getOrDefault("[]")
            val entryPointsArr = if (entryPointsOut.startsWith("[")) JSONArray(entryPointsOut) else JSONArray()
            val entryPoints = mutableListOf<EntryPoint>()
            for (i in 0 until entryPointsArr.length()) {
                entryPoints.add(EntryPoint.fromJson(entryPointsArr.getJSONObject(i)))
            }
            
            val archsOut = R2PipeManager.executeJson("iaj").getOrDefault("{}")
            val archs = ArchInfo.parseList(if (archsOut.startsWith("{")) JSONObject(archsOut) else JSONObject())
            
            val headersOut = R2PipeManager.executeJson("ihj").getOrDefault("[]")
            val headers = HeaderInfo.parseList(if (headersOut.startsWith("[")) JSONArray(headersOut) else JSONArray())
            
            val headersStrOut = R2PipeManager.executeJson("iHj").getOrDefault("{}")
            val headersString = (if (headersStrOut.startsWith("{")) JSONObject(headersStrOut) else JSONObject()).optString("header", "")
            
            baseInfo.copy(
                entropy = entropyData,
                blockStats = blockStats,
                hashes = hashes,
                mainAddr = mainAddr,
                guessedSize = guessedSize,
                entryPoints = entryPoints,
                archs = archs,
                headers = headers,
                headersString = headersString
            )
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

    /**
     * Resolve an expression (function name, symbol, or expression) to an address using ?v command.
     * `?v <expr>` returns the value of the expression in hex, we convert to decimal.
     */
    suspend fun resolveExpression(expression: String): Result<Long> {
        return R2PipeManager.execute("?v $expression").mapCatching { output ->
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

    suspend fun getDecompilation(offset: Long, decompilerType: String = "r2ghidra"): Result<DecompilationData> {
        if (decompilerType == "aipdg") {
            return runCatching {
                val pdg = R2PipeManager.execute("pdg @ $offset").getOrDefault("").trim()
                val source = pdg.ifBlank { throw RuntimeException("Empty pdg output") }
                val polished = requestAiPdgPolish(offset, source)
                DecompilationData(
                    code = polished,
                    annotations = CLexer.tokenize(polished)
                )
            }
        }
        if (decompilerType == "r2dec") {
            return R2PipeManager.executeJson("pddj @ $offset").mapCatching { output ->
                if (output.isBlank()) throw RuntimeException("Empty decompilation output")
                DecompilationData.fromPddj(JSONObject(output))
            }
        }
        val cmd = when (decompilerType) {
            "native" -> "pdcj @ $offset"
            else -> "pdgj @ $offset"
        }
        return R2PipeManager.executeJson(cmd).mapCatching { output ->
            if (output.isBlank()) throw RuntimeException("Empty decompilation output")
            DecompilationData.fromJson(JSONObject(output))
        }
    }

    private suspend fun requestAiPdgPolish(offset: Long, pdg: String): String {
        val config = AiSettingsManager.configFlow.value
        val provider = config.providers.find { it.id == config.activeProviderId }
            ?: throw IllegalStateException("No AI provider configured")
        val model = config.activeModelName ?: provider.models.firstOrNull()
            ?: throw IllegalStateException("No model selected")

        aiRepository.configure(provider)

        val userPrompt = buildString {
            appendLine("Please beautify the following `pdg` output for readability.")
            appendLine("Requirements:")
            appendLine("1) Preserve original semantics.")
            appendLine("2) Improve structure and naming readability without inventing facts.")
            appendLine("3) Add brief, useful comments when needed.")
            appendLine("4) Output plain text for decompiler rendering (no fenced code blocks).")
            appendLine()
            appendLine("Function address: 0x${offset.toString(16).uppercase()}")
            appendLine()
            appendLine("PDG:")
            appendLine(pdg)
            appendLine()
            appendLine(buildLanguagePromptInstruction())
        }

        val output = StringBuilder()
        aiRepository.streamChat(
            messages = listOf(ChatMessage(role = ChatRole.User, content = userPrompt)),
            modelName = model,
            systemPrompt = "You are a reverse engineering assistant. Beautify decompiler output for readability while keeping technical accuracy.",
            useResponsesApi = provider.useResponsesApi
        ).collect { chunk ->
            output.append(chunk)
        }

        return output.toString().trim().ifBlank {
            throw IllegalStateException("AI returned empty decompilation")
        }
    }
    private fun buildLanguagePromptInstruction(): String {
        val locale = Locale.getDefault()
        val tag = locale.toLanguageTag()
        val display = locale.getDisplayLanguage(locale).ifBlank { tag }
        return "Respond in the system language: $display ($tag)."
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

    /**
     * Get cross-references using axfj (refs from) and axtj (refs to).
     * Also fetches disassembly info for each referenced address via pdj1.
     * 
     * axfj @ addr: References FROM current address TO other addresses
     * axtj @ addr: References FROM other addresses TO current address
     */
    suspend fun getXrefs(addr: Long): Result<XrefsData> {
        return runCatching {
            // Get refs FROM current address (axfj)
            val axfResult = R2PipeManager.executeJson("axfj @ $addr")
            val refsFrom = mutableListOf<XrefWithDisasm>()
            if (axfResult.isSuccess) {
                val output = axfResult.getOrDefault("")
                if (output.isNotBlank()) {
                    val jsonArray = JSONArray(output)
                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        // axfj doesn't have fcn_name, so we need to look it up via afij
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
                        // Get disasm info for the target address (to)
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
            val axtResult = R2PipeManager.executeJson("axtj @ $addr")
            val refsTo = mutableListOf<XrefWithDisasm>()
            if (axtResult.isSuccess) {
                val output = axtResult.getOrDefault("")
                if (output.isNotBlank()) {
                    val jsonArray = JSONArray(output)
                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        // axtj returns: from (source), type, opcode, fcn_addr, fcn_name, refname
                        // Note: axtj doesn't have 'to' field, the 'to' is the current addr
                        val xref = Xref(
                            type = json.optString("type", ""),
                            from = json.optLong("from", 0),
                            to = addr, // The target is the current address
                            opcode = json.optString("opcode", ""),
                            fcnName = json.optString("fcn_name", ""),
                            refName = json.optString("refname", "")
                        )
                        // Get disasm info for the source address (from)
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
    
    /**
     * Get disassembly info for a single address using pdj1.
     * Returns Triple of (disasm, type, bytes).
     */
    private suspend fun getDisasmForAddress(addr: Long): Triple<String, String, String> {
        return try {
            val result = R2PipeManager.executeJson("pdj 1 @ $addr")
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
    
    /**
     * Get detailed history info for a list of addresses.
     * For each address, fetches: function name, hex bytes, disassembly.
     */
    suspend fun getHistoryDetails(addresses: List<Long>): Result<List<HistoryEntry>> {
        return runCatching {
            addresses.map { addr ->
                val disasmInfo = getDisasmForAddress(addr)
                val funcName = getFunctionNameForAddress(addr)
                HistoryEntry(
                    address = addr,
                    functionName = funcName,
                    bytes = disasmInfo.third,
                    disasm = disasmInfo.first
                )
            }
        }
    }

    // === Function Operations ===

    suspend fun analyzeFunction(addr: Long): Result<String> {
        return R2PipeManager.execute("af @ $addr")
    }

    suspend fun getFunctionDetail(addr: Long): Result<FunctionDetailInfo?> {
        return R2PipeManager.executeJson("afij @ $addr").mapCatching { output ->
            if (output.isBlank() || output == "[]") return@mapCatching null
            val jsonArray = JSONArray(output)
            if (jsonArray.length() > 0) {
                FunctionDetailInfo.fromJson(jsonArray.getJSONObject(0))
            } else null
        }
    }

    suspend fun getFunctionXrefs(addr: Long): Result<List<FunctionXref>> {
        return R2PipeManager.executeJson("afxj @ $addr").mapCatching { output ->
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
        return R2PipeManager.executeJson("afvj @ $addr").mapCatching { output ->
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
        return R2PipeManager.execute("afn $newName @ $addr")
    }

    suspend fun renameFunctionVariable(addr: Long, newName: String, oldName: String): Result<String> {
        return R2PipeManager.execute("afvn $newName $oldName @ $addr")
    }

    // === Graph Operations ===

    /**
     * Get function flow graph (agj) for the function at the given address.
     */
    suspend fun getFunctionGraph(addr: Long): Result<GraphData> {
        val cmd = "agf@$addr > 0 ; agj @ $addr" // 杩欐槸涓巹瀛﹂棶棰橈紝涓嶇煡閬撲负浠€涔坅gf涔嬪悗娴佺▼鍥炬樉绀哄湴鏇存暣榻愶紝鍙兘鏄痳2鍐呴儴鏈夎嚜鍔ㄦ帓鍒?
        return R2PipeManager.executeJson(cmd).mapCatching { output ->
            if (output.isBlank() || output == "[]") throw RuntimeException("Empty graph output")
            val jsonArray = JSONArray(output)
            GraphData.fromAgj(jsonArray)
        }
    }

    /**
     * Get cross-reference graph (agrj) for the function at the given address.
     */
    suspend fun getXrefGraph(addr: Long): Result<GraphData> {
        val cmd = "s $addr; agrj"
        return R2PipeManager.executeJson(cmd).mapCatching { output ->
            if (output.isBlank() || output == "{}") throw RuntimeException("Empty xref graph output")
            val json = JSONObject(output)
            GraphData.fromAgrj(json)
        }
    }

    /**
     * Get function call graph (agcj) for the function at the given address.
     */
    suspend fun getCallGraph(addr: Long): Result<GraphData> {
        val cmd = "s $addr; agcj"
        return R2PipeManager.executeJson(cmd).mapCatching { output ->
            if (output.isBlank() || output == "[]") throw RuntimeException("Empty call graph output")
            val jsonArray = JSONArray(output)
            GraphData.fromFunctionInfo(jsonArray)
        }
    }

    /**
     * Get global function call graph (agCj) for the entire binary.
     */
    suspend fun getGlobalCallGraph(): Result<GraphData> {
        val cmd = "agCj"
        return R2PipeManager.executeJson(cmd).mapCatching { output ->
            if (output.isBlank() || output == "[]") throw RuntimeException("Empty global call graph output")
            val jsonArray = JSONArray(output)
            GraphData.fromCallGraph(jsonArray)
        }
    }

    /**
     * Get data reference graph (agaj) for the function at the given address.
     */
    suspend fun getDataRefGraph(addr: Long): Result<GraphData> {
        val cmd = "s $addr; agaj"
        return R2PipeManager.executeJson(cmd).mapCatching { output ->
            if (output.isBlank() || output == "{}") throw RuntimeException("Empty data ref graph output")
            val json = JSONObject(output)
            GraphData.fromAgrj(json)
        }
    }

    /**
     * Resolve a function name to its address using afij@<name>.
     * Called lazily on click rather than eagerly on load.
     */
    suspend fun resolveFunctionAddress(name: String): Long {
        return try {
            val result = R2PipeManager.executeJson("afij@$name")
            if (result.isSuccess) {
                val out = result.getOrDefault("")
                if (out.isNotBlank() && out.startsWith("[")) {
                    val arr = JSONArray(out)
                    if (arr.length() > 0) {
                        arr.getJSONObject(0).optLong("addr", 0L)
                    } else 0L
                } else 0L
            } else 0L
        } catch (_: Exception) { 0L }
    }

    /**
     * Get function name for an address using afij.
     * Returns the function name if the address is within a function, empty string otherwise.
     */
    private suspend fun getFunctionNameForAddress(addr: Long): String {
        return try {
            val result = R2PipeManager.executeJson("afij @ $addr")
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



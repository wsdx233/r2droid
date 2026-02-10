package top.wsdx233.r2droid.feature.disasm.data

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

import javax.inject.Inject
class DisasmRepository @Inject constructor(private val r2DataSource: R2DataSource) {

    suspend fun getDisassembly(offset: Long, count: Int): Result<List<DisasmInstruction>> {
        // pdj: Print Disassembly
        val cmd = "pdj $count @ $offset"
        return r2DataSource.executeJson(cmd).mapCatching { output ->
            if (output.isBlank()) return@mapCatching emptyList()
            val jsonArray = JSONArray(output)
            val list = mutableListOf<DisasmInstruction>()
            for (i in 0 until jsonArray.length()) {
                list.add(DisasmInstruction.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun writeAsm(addr: Long, asm: String): Result<String> {
        // wa [asm] @ [addr]
        val escaped = asm.replace("\"", "\\\"")
        return r2DataSource.execute("wa $escaped @ $addr")
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

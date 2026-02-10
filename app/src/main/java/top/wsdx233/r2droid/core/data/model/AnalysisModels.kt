package top.wsdx233.r2droid.core.data.model

import org.json.JSONObject

data class BinInfo(
    val arch: String,
    val bits: Int,
    val os: String,
    val type: String,
    val compiled: String,
    val language: String,
    val machine: String,
    val subSystem: String,
    val size: Long = 0L
) {
    companion object {
        fun fromJson(json: JSONObject): BinInfo {
            return BinInfo(
                arch = json.optString("arch", "Unknown"),
                bits = json.optInt("bits", 0),
                os = json.optString("os", "Unknown"),
                type = json.optString("class", "Unknown"), // "class": "PE32"
                compiled = json.optString("compiled", ""),
                language = json.optString("lang", "Unknown"),
                machine = json.optString("machine", "Unknown"),
                subSystem = json.optString("subsys", "Unknown"),
                size = json.optLong("size", 0) // Typically in iIj details or core
            )
        }
    }
}

data class Section(
    val name: String,
    val size: Long,
    val vSize: Long,
    val perm: String,
    val vAddr: Long,
    val pAddr: Long
) {
    companion object {
        fun fromJson(json: JSONObject): Section {
            return Section(
                name = json.optString("name", ""),
                size = json.optLong("size", 0),
                vSize = json.optLong("vsize", 0),
                perm = json.optString("perm", ""),
                vAddr = json.optLong("vaddr", 0),
                pAddr = json.optLong("paddr", 0)
            )
        }
    }
}

data class Symbol(
    val name: String,
    val type: String,
    val vAddr: Long,
    val pAddr: Long,
    val isImported: Boolean
) {
    companion object {
        fun fromJson(json: JSONObject): Symbol {
            return Symbol(
                name = json.optString("name", ""),
                type = json.optString("type", ""),
                vAddr = json.optLong("vaddr", 0),
                pAddr = json.optLong("paddr", 0),
                isImported = json.optBoolean("is_imported", false)
            )
        }
    }
}

data class ImportInfo(
    val name: String,
    val ordinal: Int,
    val type: String,
    val plt: Long
) {
    companion object {
        fun fromJson(json: JSONObject): ImportInfo {
            // Adjust based on standard r2 iij output
            return ImportInfo(
                name = json.optString("name", ""),
                ordinal = json.optInt("ordinal", 0),
                type = json.optString("type", ""),
                plt = json.optLong("plt", 0)
            )
        }
    }
}

data class Relocation(
    val name: String,
    val type: String,
    val vAddr: Long,
    val pAddr: Long
) {
    companion object {
        fun fromJson(json: JSONObject): Relocation {
            return Relocation(
                name = json.optString("name", ""),
                type = json.optString("type", ""),
                vAddr = json.optLong("vaddr", 0),
                pAddr = json.optLong("paddr", 0)
            )
        }
    }
}

data class StringInfo(
    val string: String,
    val vAddr: Long,
    val section: String,
    val type: String
) {
    companion object {
        fun fromJson(json: JSONObject): StringInfo {
            return StringInfo(
                string = json.optString("string", ""),
                vAddr = json.optLong("vaddr", 0),
                section = json.optString("section", ""),
                type = json.optString("type", "")
            )
        }
    }
}

data class FunctionInfo(
    val name: String,
    val addr: Long,
    val size: Long,
    val nbbs: Int, // Number of basic blocks
    val signature: String
) {
    companion object {
        fun fromJson(json: JSONObject): FunctionInfo {
            return FunctionInfo(
                name = json.optString("name", ""),
                addr = json.optLong("addr", 0),
                size = json.optLong("size", 0),
                nbbs = json.optInt("nbbs", 0),
                signature = json.optString("signature", "")
            )
        }
    }
}

data class DecompilationData(
    val code: String,
    val annotations: List<DecompilationAnnotation>
) {
    companion object {
        fun fromJson(json: JSONObject): DecompilationData {
            val code = json.optString("code", "")
            val notesJson = json.optJSONArray("annotations")
            val annotations = mutableListOf<DecompilationAnnotation>()
            if (notesJson != null) {
                for (i in 0 until notesJson.length()) {
                     annotations.add(DecompilationAnnotation.fromJson(notesJson.getJSONObject(i)))
                }
            }
            return DecompilationData(code, annotations)
        }
    }
}

data class DecompilationAnnotation(
    val start: Int,
    val end: Int,
    val type: String,
    val syntaxHighlight: String? = null,
    val offset: Long = 0
) {
    companion object {
        fun fromJson(json: JSONObject): DecompilationAnnotation {
            return DecompilationAnnotation(
                start = json.optInt("start", 0),
                end = json.optInt("end", 0),
                type = json.optString("type", ""),
                syntaxHighlight = json.optString("syntax_highlight").takeIf { it.isNotEmpty() },
                offset = json.optLong("offset", 0)
            )
        }
    }
}

/**
 * Reference data from pdj output's refs array.
 */
data class DisasmRef(
    val addr: Long,
    val type: String  // "DATA", "CODE", "CALL", etc.
) {
    companion object {
        fun fromJson(json: JSONObject): DisasmRef {
            return DisasmRef(
                addr = json.optLong("addr", 0),
                type = json.optString("type", "")
            )
        }
    }
}

data class DisasmInstruction(
    val addr: Long,
    val opcode: String,
    val bytes: String,
    val type: String,
    val size: Int,
    val disasm: String,
    val family: String?,
    // Extended fields from pdj
    val flags: List<String> = emptyList(),       // e.g. ["_start", "rip", "entry0"]
    val comment: String? = null,                  // e.g. "; arg int64_t arg3 @ rdx"
    val fcnAddr: Long = 0,                        // Function start address
    val fcnLast: Long = 0,                        // Function last address
    val jump: Long? = null,                       // Jump target address (for jmp, cjmp)
    val fail: Long? = null,                       // Fail target for conditional jumps
    val ptr: Long? = null,                        // Pointer value (e.g. for lea)
    val refptr: Boolean = false,                  // Has reference pointer
    val refs: List<DisasmRef> = emptyList(),      // References from this instruction
    val xrefs: List<DisasmRef> = emptyList(),     // Cross-references to this instruction
    val esil: String? = null                      // ESIL representation
) {
    companion object {
        fun fromJson(json: JSONObject): DisasmInstruction {
            // Parse flags array
            val flagsList = mutableListOf<String>()
            json.optJSONArray("flags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.let { flagsList.add(it) }
                }
            }
            
            // Parse refs array
            val refsList = mutableListOf<DisasmRef>()
            json.optJSONArray("refs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { refsList.add(DisasmRef.fromJson(it)) }
                }
            }
            
            // Parse xrefs array
            val xrefsList = mutableListOf<DisasmRef>()
            json.optJSONArray("xrefs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { xrefsList.add(DisasmRef.fromJson(it)) }
                }
            }
            
            return DisasmInstruction(
                addr = json.optLong("addr", json.optLong("offset", 0)),
                opcode = json.optString("opcode", ""),
                bytes = json.optString("bytes", ""),
                type = json.optString("type", ""),
                size = json.optInt("size", 0),
                disasm = json.optString("disasm", ""),
                family = json.optString("family", ""),
                flags = flagsList,
                comment = json.optString("comment").takeIf { it.isNotEmpty() },
                fcnAddr = json.optLong("fcn_addr", 0),
                fcnLast = json.optLong("fcn_last", 0),
                jump = if (json.has("jump")) json.optLong("jump") else null,
                fail = if (json.has("fail")) json.optLong("fail") else null,
                ptr = if (json.has("ptr")) json.optLong("ptr") else null,
                refptr = json.optBoolean("refptr", false),
                refs = refsList,
                xrefs = xrefsList,
                esil = json.optString("esil").takeIf { it.isNotEmpty() }
            )
        }
    }
    
    /**
     * Check if this instruction is a jump to an address outside the current function.
     */
    fun isJumpOut(): Boolean {
        if (fcnAddr == 0L || fcnLast == 0L) return false
        val target = jump ?: return false
        return target < fcnAddr || target > fcnLast
    }
    
    /**
     * Check if this instruction has incoming jumps from outside the current function.
     * This requires xrefs to be populated and checks if any caller is outside function bounds.
     */
    fun hasJumpIn(): Boolean {
        if (fcnAddr == 0L || fcnLast == 0L) return false
        return xrefs.any { xref ->
            xref.type == "CODE" && (xref.addr < fcnAddr || xref.addr > fcnLast)
        }
    }
}

data class EntryPoint(
    val vAddr: Long,
    val pAddr: Long,
    val bAddr: Long,
    val lAddr: Long,
    val hAddr: Long,
    val type: String
) {
    companion object {
        fun fromJson(json: JSONObject): EntryPoint {
            return EntryPoint(
                vAddr = json.optLong("vaddr", 0),
                pAddr = json.optLong("paddr", 0),
                bAddr = json.optLong("baddr", 0),
                lAddr = json.optLong("laddr", 0),
                hAddr = json.optLong("haddr", 0),
                type = json.optString("type", "")
            )
        }
    }
}


/**
 * Basic Xref entry from axfj or axtj.
 * axfj returns: from (current addr), to (target addr), type, opcode
 * axtj returns: from (source addr), type, opcode, fcn_addr, fcn_name, refname
 */
data class Xref(
    val type: String,
    val from: Long,
    val to: Long,
    val opcode: String = "",
    val fcnName: String = "",
    val refName: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): Xref {
            return Xref(
                type = json.optString("type", ""),
                from = json.optLong("from", 0),
                to = json.optLong("to", 0),
                opcode = json.optString("opcode", ""),
                fcnName = json.optString("fcn_name", ""),
                refName = json.optString("refname", "")
            )
        }
    }
}

/**
 * Xref with additional disassembly info from pdj1 @ addr.
 */
data class XrefWithDisasm(
    val xref: Xref,
    val disasm: String = "",      // Disassembly text of the address
    val instrType: String = "",   // Instruction type (call, jmp, etc.)
    val bytes: String = ""        // Instruction bytes
)

/**
 * Combined xrefs data holding both "refs from" (axfj) and "refs to" (axtj).
 */
data class XrefsData(
    val refsFrom: List<XrefWithDisasm> = emptyList(),  // axfj - references FROM current address TO other addresses
    val refsTo: List<XrefWithDisasm> = emptyList()     // axtj - references FROM other addresses TO current address
)

/**
 * A single entry in the navigation history, enriched with disassembly details.
 */
data class HistoryEntry(
    val address: Long,
    val functionName: String = "",
    val bytes: String = "",
    val disasm: String = ""
)

/**
 * Detailed function info from afij command.
 * Richer than FunctionInfo (which is used for the function list from aflj).
 */
data class FunctionDetailInfo(
    val name: String,
    val addr: Long,
    val size: Long,
    val realSize: Long,
    val noReturn: Boolean,
    val stackFrame: Int,
    val callType: String,
    val cost: Int,
    val cc: Int,
    val bits: Int,
    val type: String,
    val nbbs: Int,
    val ninstrs: Int,
    val edges: Int,
    val signature: String,
    val minAddr: Long,
    val maxAddr: Long,
    val nlocals: Int,
    val nargs: Int,
    val isPure: Boolean,
    val isLineal: Boolean,
    val indegree: Int,
    val outdegree: Int,
    val diffType: String
) {
    companion object {
        fun fromJson(json: JSONObject): FunctionDetailInfo {
            return FunctionDetailInfo(
                name = json.optString("name", ""),
                addr = json.optLong("addr", json.optLong("offset", 0)),
                size = json.optLong("size", 0),
                realSize = json.optLong("realsz", 0),
                noReturn = json.optBoolean("noreturn", false),
                stackFrame = json.optInt("stackframe", 0),
                callType = json.optString("calltype", ""),
                cost = json.optInt("cost", 0),
                cc = json.optInt("cc", 0),
                bits = json.optInt("bits", 0),
                type = json.optString("type", ""),
                nbbs = json.optInt("nbbs", 0),
                ninstrs = json.optInt("ninstrs", 0),
                edges = json.optInt("edges", 0),
                signature = json.optString("signature", ""),
                minAddr = json.optLong("minaddr", 0),
                maxAddr = json.optLong("maxaddr", 0),
                nlocals = json.optInt("nlocals", 0),
                nargs = json.optInt("nargs", 0),
                isPure = json.optString("is-pure", "false") == "true",
                isLineal = json.optBoolean("is-lineal", false),
                indegree = json.optInt("indegree", 0),
                outdegree = json.optInt("outdegree", 0),
                diffType = json.optString("difftype", "")
            )
        }
    }
}

/**
 * Function cross-reference entry from afxj command.
 */
data class FunctionXref(
    val type: String,
    val from: Long,
    val to: Long
) {
    companion object {
        fun fromJson(json: JSONObject): FunctionXref {
            return FunctionXref(
                type = json.optString("type", ""),
                from = json.optLong("from", 0),
                to = json.optLong("to", 0)
            )
        }
    }
}

/**
 * Function variable entry from afvj command.
 */
data class FunctionVariable(
    val name: String,
    val kind: String,
    val type: String,
    val storage: String
) {
    companion object {
        fun fromJson(json: JSONObject, storage: String): FunctionVariable {
            return FunctionVariable(
                name = json.optString("name", ""),
                kind = json.optString("kind", ""),
                type = json.optString("type", ""),
                storage = storage
            )
        }
    }
}

/**
 * Combined function variables data from afvj, grouped by storage type.
 */
data class FunctionVariablesData(
    val reg: List<FunctionVariable> = emptyList(),
    val sp: List<FunctionVariable> = emptyList(),
    val bp: List<FunctionVariable> = emptyList()
) {
    val all: List<FunctionVariable> get() = reg + sp + bp
    val isEmpty: Boolean get() = reg.isEmpty() && sp.isEmpty() && bp.isEmpty()
}

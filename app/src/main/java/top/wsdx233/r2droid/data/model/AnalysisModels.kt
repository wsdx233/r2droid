package top.wsdx233.r2droid.data.model

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

data class DisasmInstruction(
    val addr: Long,
    val opcode: String,
    val bytes: String,
    val type: String,
    val size: Int,
    val disasm: String,
    val family: String?
) {
    companion object {
        fun fromJson(json: JSONObject): DisasmInstruction {
            return DisasmInstruction(
                addr = json.optLong("addr", 0),
                opcode = json.optString("opcode", ""),
                bytes = json.optString("bytes", ""),
                type = json.optString("type", ""),
                size = json.optInt("size", 0),
                disasm = json.optString("disasm", ""),
                family = json.optString("family", "")
            )
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

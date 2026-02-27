package top.wsdx233.r2droid.core.data.model

import android.util.Base64
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.parser.CLexer

data class BinInfo(
    val arch: String,
    val bits: Int,
    val os: String,
    val type: String,
    val compiled: String,
    val language: String,
    val machine: String,
    val subSystem: String,
    val size: Long = 0L,
    val entropy: EntropyData? = null,
    val blockStats: BlockStatsData? = null,
    val hashes: HashInfo? = null,
    val mainAddr: MainAddressInfo? = null,
    val guessedSize: Long? = null,
    val entryPoints: List<EntryPoint> = emptyList(),
    val archs: List<ArchInfo> = emptyList(),
    val headers: List<HeaderInfo> = emptyList(),
    val headersString: String? = null
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
    val isImported: Boolean,
    val realname: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject): Symbol {
            return Symbol(
                name = json.optString("name", ""),
                type = json.optString("type", ""),
                vAddr = json.optLong("vaddr", 0),
                pAddr = json.optLong("paddr", 0),
                isImported = json.optBoolean("is_imported", false),
                realname = json.optString("realname", "").ifEmpty { null }
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
            // Apply CLexer if no syntax highlighting exists (e.g. native decompiler)
            if (code.isNotEmpty() && annotations.none { it.syntaxHighlight != null }) {
                return DecompilationData(code, annotations + CLexer.tokenize(code))
            }
            return DecompilationData(code, annotations)
        }

        fun fromPddj(json: JSONObject): DecompilationData {
            val lines = json.optJSONArray("lines") ?: return DecompilationData("", emptyList())
            val sb = StringBuilder()
            val offsetAnnotations = mutableListOf<DecompilationAnnotation>()
            for (i in 0 until lines.length()) {
                val line = lines.getJSONObject(i)
                val start = sb.length
                sb.append(line.optString("str", ""))
                if (line.has("offset")) {
                    offsetAnnotations.add(DecompilationAnnotation(start, sb.length, "syntax_highlight", offset = line.optLong("offset", 0)))
                }
                if (i < lines.length() - 1) sb.append("\n")
            }
            val code = sb.toString()
            val syntaxAnnotations = CLexer.tokenize(code)
            return DecompilationData(code, offsetAnnotations + syntaxAnnotations)
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
                comment = json.optString("comment").takeIf { it.isNotEmpty() }?.let { tryDecodeBase64(it) },
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
        return target !in fcnAddr..fcnLast
    }
    
    /**
     * Check if this instruction has incoming jumps from outside the current function.
     * This requires xrefs to be populated and checks if any caller is outside function bounds.
     */
    fun hasJumpIn(): Boolean {
        if (fcnAddr == 0L || fcnLast == 0L) return false
        return xrefs.any { xref ->
            xref.type == "CODE" && (xref.addr !in fcnAddr..fcnLast)
        }
    }

    // --- Pre-computed display strings (lazy, not part of equals/hashCode) ---

    /** Compact hex address without leading zeros, min 4 chars */
    val displayAddress: String by lazy {
        val hex = "%X".format(addr)
        if (hex.length <= 4) hex else hex.trimStart('0').ifEmpty { "0" }
    }

    /** Lowercase bytes, truncated to 8 chars + ellipsis if longer than 10 */
    val displayBytes: String by lazy {
        val lower = bytes.lowercase()
        if (lower.length > 10) lower.take(8) + "\u2026" else lower
    }

    /** Pre-built inline comment from ptr/refs */
    val inlineComment: String by lazy {
        buildString {
            if (ptr != null) {
                val pHex = "%X".format(ptr)
                val pDisplay = if (pHex.length <= 4) pHex else pHex.trimStart('0').ifEmpty { "0" }
                append("; $pDisplay")
            }
            if (refptr && refs.isNotEmpty()) {
                val dataRef = refs.firstOrNull { it.type == "DATA" }
                if (dataRef != null) {
                    if (isNotEmpty()) append(" ")
                    val rHex = "%X".format(dataRef.addr)
                    val rDisplay = if (rHex.length <= 4) rHex else rHex.trimStart('0').ifEmpty { "0" }
                    append("[$rDisplay]")
                }
            }
        }.trim()
    }
}

private val BASE64_REGEX = Regex("^[A-Za-z0-9+/]+=*$")

private fun tryDecodeBase64(text: String): String {
    if (!BASE64_REGEX.matches(text)) return text
    return try {
        val decoded = Base64.decode(text, Base64.NO_WRAP)
        val str = String(decoded, Charsets.UTF_8)
        val reEncoded = Base64.encodeToString(decoded, Base64.NO_WRAP)
        if (reEncoded == text) str else text
    } catch (_: Exception) {
        text
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

data class EntropyBlock(val addr: Long, val value: Int)

data class EntropyData(
    val blocksize: Int,
    val address: Long,
    val size: Long,
    val entropy: List<EntropyBlock>
) {
    companion object {
        fun fromJson(json: JSONObject): EntropyData {
            val entropyArr = json.optJSONArray("entropy")
            val list = mutableListOf<EntropyBlock>()
            if (entropyArr != null) {
                for (i in 0 until entropyArr.length()) {
                    val e = entropyArr.getJSONObject(i)
                    list.add(EntropyBlock(e.optLong("addr"), e.optInt("value")))
                }
            }
            return EntropyData(
                json.optInt("blocksize"),
                json.optLong("address"),
                json.optLong("size"),
                list
            )
        }
    }
}

data class BlockStat(
    val offset: Long,
    val size: Int,
    val flags: Int,
    val comments: Int,
    val symbols: Int,
    val strings: Int,
    val perm: String
)

data class BlockStatsData(
    val from: Long,
    val to: Long,
    val blocksize: Int,
    val blocks: List<BlockStat>
) {
    companion object {
        fun fromJson(json: JSONObject): BlockStatsData {
            val blocksArr = json.optJSONArray("blocks")
            val list = mutableListOf<BlockStat>()
            if (blocksArr != null) {
                for (i in 0 until blocksArr.length()) {
                    val b = blocksArr.getJSONObject(i)
                    list.add(BlockStat(
                        b.optLong("offset"),
                        b.optInt("size"),
                        b.optInt("flags", 0),
                        b.optInt("comments", 0),
                        b.optInt("symbols", 0),
                        b.optInt("strings", 0),
                        b.optString("perm", "")
                    ))
                }
            }
            return BlockStatsData(
                json.optLong("from"),
                json.optLong("to"),
                json.optInt("blocksize"),
                list
            )
        }
    }
}

data class ArchInfo(
    val arch: String,
    val bits: Int,
    val offset: Long,
    val size: Long,
    val machine: String
) {
    companion object {
        fun parseList(json: JSONObject): List<ArchInfo> {
            val binsArr = json.optJSONArray("bins") ?: return emptyList()
            val list = mutableListOf<ArchInfo>()
            for (i in 0 until binsArr.length()) {
                val b = binsArr.getJSONObject(i)
                list.add(ArchInfo(
                    b.optString("arch"),
                    b.optInt("bits"),
                    b.optLong("offset"),
                    b.optLong("size"),
                    b.optString("machine")
                ))
            }
            return list
        }
    }
}

data class HeaderInfo(
    val name: String,
    val vaddr: Long,
    val paddr: Long,
    val size: Int,
    val value: Long,
    val format: String
) {
    companion object {
        fun parseList(jsonArray: org.json.JSONArray): List<HeaderInfo> {
            val list = mutableListOf<HeaderInfo>()
            for (i in 0 until jsonArray.length()) {
                val h = jsonArray.getJSONObject(i)
                list.add(HeaderInfo(
                    h.optString("name"),
                    h.optLong("vaddr"),
                    h.optLong("paddr"),
                    h.optInt("size"),
                    h.optLong("value"),
                    h.optString("format")
                ))
            }
            return list
        }
    }
}

data class HashInfo(val md5: String, val sha1: String, val sha256: String) {
    companion object {
        fun fromJson(json: JSONObject): HashInfo {
            return HashInfo(
                json.optString("md5"),
                json.optString("sha1"),
                json.optString("sha256")
            )
        }
    }
}

data class MainAddressInfo(val vaddr: Long, val paddr: Long) {
    companion object {
        fun fromJson(json: JSONObject): MainAddressInfo {
            return MainAddressInfo(
                json.optLong("vaddr"),
                json.optLong("paddr")
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

// === Instruction Detail Model (aoj output) ===

data class InstructionDetail(
    val opcode: String,
    val disasm: String,
    val description: String,
    val pseudo: String,
    val mnemonic: String,
    val addr: Long,
    val bytes: String,
    val size: Int,
    val type: String,
    val family: String,
    val jump: Long? = null,
    val fail: Long? = null,
    val esil: String,
    val cycles: Int,
    val sign: Boolean
) {
    companion object {
        fun fromJson(json: JSONObject): InstructionDetail {
            return InstructionDetail(
                opcode = json.optString("opcode", ""),
                disasm = json.optString("disasm", ""),
                description = json.optString("description", ""),
                pseudo = json.optString("pseudo", ""),
                mnemonic = json.optString("mnemonic", ""),
                addr = json.optLong("addr", 0),
                bytes = json.optString("bytes", ""),
                size = json.optInt("size", 0),
                type = json.optString("type", ""),
                family = json.optString("family", ""),
                jump = if (json.has("jump")) json.optLong("jump") else null,
                fail = if (json.has("fail")) json.optLong("fail") else null,
                esil = json.optString("esil", ""),
                cycles = json.optInt("cycles", 0),
                sign = json.optBoolean("sign", false)
            )
        }
    }
}

// === Graph Models ===

/**
 * A single instruction within a graph block node (from agj ops).
 */
data class GraphBlockInstruction(
    val addr: Long,
    val opcode: String,
    val disasm: String,
    val type: String,
    val bytes: String,
    val jump: Long? = null,
    val fail: Long? = null
) {
    companion object {
        fun fromJson(json: JSONObject): GraphBlockInstruction {
            return GraphBlockInstruction(
                addr = json.optLong("addr", 0),
                opcode = json.optString("opcode", ""),
                disasm = json.optString("disasm", json.optString("opcode", "")),
                type = json.optString("type", ""),
                bytes = json.optString("bytes", ""),
                jump = if (json.has("jump")) json.optLong("jump") else null,
                fail = if (json.has("fail")) json.optLong("fail") else null
            )
        }
    }
}

/**
 * Unified graph node that works for both agrj and agj output.
 */
data class GraphNode(
    val id: Int,
    val title: String,
    val address: Long = 0L,
    val body: String = "",
    val outNodes: List<Int> = emptyList(),
    val instructions: List<GraphBlockInstruction> = emptyList()
)

/**
 * Graph data holding all nodes. Used for both function flow (agj) and xref (agrj) graphs.
 */
data class GraphData(
    val nodes: List<GraphNode>,
    val title: String = ""
) {
    companion object {
        /**
         * Parse agrj output: {"nodes":[{"id":0,"title":"...","body":"","out_nodes":[1,2]}]}
         */
        fun fromAgrj(json: JSONObject): GraphData {
            val nodesArray = json.optJSONArray("nodes") ?: return GraphData(emptyList())
            val nodes = mutableListOf<GraphNode>()
            for (i in 0 until nodesArray.length()) {
                val n = nodesArray.getJSONObject(i)
                val outNodes = mutableListOf<Int>()
                n.optJSONArray("out_nodes")?.let { arr ->
                    for (j in 0 until arr.length()) outNodes.add(arr.getInt(j))
                }
                nodes.add(GraphNode(
                    id = n.optInt("id", i),
                    title = n.optString("title", ""),
                    body = n.optString("body", ""),
                    outNodes = outNodes
                ))
            }
            return GraphData(nodes)
        }

        /**
         * Parse agCj / agcj call graph output:
         * [{"name":"sym.main","size":42,"imports":["sym.printf","sym.exit"]}, ...]
         * Each element is a function; edges go from the function to its imports (callees).
         * Functions that only appear as imports get leaf nodes with no outgoing edges.
         */
        fun fromCallGraph(jsonArray: org.json.JSONArray): GraphData {
            if (jsonArray.length() == 0) return GraphData(emptyList())

            // Collect all entries that have "imports"
            data class Entry(val name: String, val imports: List<String>)
            val entries = mutableListOf<Entry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "")
                if (name.isBlank()) continue
                val importsArr = obj.optJSONArray("imports")
                val imports = mutableListOf<String>()
                if (importsArr != null) {
                    for (j in 0 until importsArr.length()) {
                        imports.add(importsArr.getString(j))
                    }
                }
                entries.add(Entry(name, imports))
            }

            // Build name → id mapping (callers first, then callee-only nodes)
            val nameToId = mutableMapOf<String, Int>()
            var nextId = 0
            for (e in entries) {
                if (e.name !in nameToId) nameToId[e.name] = nextId++
            }
            for (e in entries) {
                for (imp in e.imports) {
                    if (imp !in nameToId) nameToId[imp] = nextId++
                }
            }

            // Build nodes
            val nodes = mutableListOf<GraphNode>()
            // Caller nodes with edges
            for (e in entries) {
                val id = nameToId[e.name]!!
                val outNodes = e.imports.mapNotNull { nameToId[it] }
                nodes.add(GraphNode(id = id, title = e.name, outNodes = outNodes))
            }
            // Leaf nodes (only appear as imports)
            for ((name, id) in nameToId) {
                if (entries.none { it.name == name }) {
                    nodes.add(GraphNode(id = id, title = name))
                }
            }

            nodes.sortBy { it.id }
            return GraphData(nodes)
        }

        /**
         * Parse agcj function-info output (afij-like format):
         * [{"addr":N,"name":"sym.func","size":N,"indegree":N,"outdegree":N,...}]
         * No explicit edge data, so each function becomes a standalone node.
         */
        fun fromFunctionInfo(jsonArray: org.json.JSONArray): GraphData {
            if (jsonArray.length() == 0) return GraphData(emptyList())

            // If the first element has "imports", delegate to fromCallGraph
            if (jsonArray.getJSONObject(0).has("imports")) {
                return fromCallGraph(jsonArray)
            }

            val nodes = mutableListOf<GraphNode>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "0x%X".format(obj.optLong("addr", 0)))
                val addr = obj.optLong("addr", 0)
                nodes.add(GraphNode(
                    id = i,
                    title = name,
                    address = addr
                ))
            }
            return GraphData(nodes)
        }

        /**
         * Parse agj output: [{"name":"...","blocks":[{"addr":...,"ops":[...]}]}]
         * Converts blocks to graph nodes with edges derived from jump/fail targets.
         */
        fun fromAgj(jsonArray: org.json.JSONArray): GraphData {
            if (jsonArray.length() == 0) return GraphData(emptyList())
            val func = jsonArray.getJSONObject(0)
            val funcName = func.optString("name", "")
            val blocksArray = func.optJSONArray("blocks") ?: return GraphData(emptyList())

            // First pass: collect all block addresses and assign IDs
            val blockAddrToId = mutableMapOf<Long, Int>()
            val nodes = mutableListOf<GraphNode>()

            for (i in 0 until blocksArray.length()) {
                val block = blocksArray.getJSONObject(i)
                val addr = block.optLong("addr", 0)
                blockAddrToId[addr] = i
            }

            // Second pass: build nodes with edges
            for (i in 0 until blocksArray.length()) {
                val block = blocksArray.getJSONObject(i)
                val addr = block.optLong("addr", 0)
                val opsArray = block.optJSONArray("ops")
                val instructions = mutableListOf<GraphBlockInstruction>()
                val jumpTargets = mutableSetOf<Long>()

                if (opsArray != null) {
                    for (j in 0 until opsArray.length()) {
                        val op = opsArray.getJSONObject(j)
                        val instr = GraphBlockInstruction.fromJson(op)
                        instructions.add(instr)
                        instr.jump?.let { jumpTargets.add(it) }
                        instr.fail?.let { jumpTargets.add(it) }
                    }
                }

                // If no explicit jumps, the next block is the fallthrough
                if (jumpTargets.isEmpty() && i + 1 < blocksArray.length()) {
                    val nextAddr = blocksArray.getJSONObject(i + 1).optLong("addr", 0)
                    jumpTargets.add(nextAddr)
                }

                val outNodes = jumpTargets.mapNotNull { blockAddrToId[it] }

                nodes.add(GraphNode(
                    id = i,
                    title = "0x%X".format(addr),
                    address = addr,
                    instructions = instructions,
                    outNodes = outNodes
                ))
            }

            return GraphData(nodes, title = funcName)
        }
    }
}

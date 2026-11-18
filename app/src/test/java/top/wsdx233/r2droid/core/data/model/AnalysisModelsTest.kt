package top.wsdx233.r2droid.core.data.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AnalysisModelsTest {

    // ========== BinInfo Tests ==========

    @Test
    fun `BinInfo fromJson parses full JSON correctly`() {
        val json = JSONObject("""
            {
                "core": {
                    "type": "DYN",
                    "file": "/bin/ls",
                    "fd": 3,
                    "size": 132456,
                    "humansz": "129.4K",
                    "iorw": false,
                    "mode": "r-x",
                    "block": 0x1000,
                    "format": "elf64"
                },
                "bin": {
                    "arch": "x86",
                    "baddr": 4194304,
                    "binsz": 132456,
                    "bintype": "elf",
                    "bits": 64,
                    "canary": true,
                    "class": "ELF64",
                    "compiled": "2024-01-15",
                    "compiler": "GCC",
                    "crypto": false,
                    "endian": "LE",
                    "havecode": true,
                    "lang": "c",
                    "machine": "AMD x86-64",
                    "nx": true,
                    "os": "linux",
                    "cc": "amd64",
                    "pic": true,
                    "relocs": true,
                    "relro": "full",
                    "static": false,
                    "stripped": false,
                    "subsys": "linux",
                    "va": true
                }
            }
        """.trimIndent())

        val info = BinInfo.fromJson(json)
        assertEquals("x86", info.arch)
        assertEquals(64, info.bits)
        assertEquals("linux", info.os)
        assertEquals("DYN", info.type)
        assertEquals("ELF64", info.binaryClass)
        assertEquals("2024-01-15", info.compiled)
        assertEquals("GCC", info.compiler)
        assertEquals("c", info.language)
        assertEquals("AMD x86-64", info.machine)
        assertEquals("linux", info.subSystem)
        assertEquals("/bin/ls", info.file)
        assertEquals("129.4K", info.humanSize)
        assertEquals("elf", info.binType)
        assertEquals("LE", info.endian)
        assertEquals(132456L, info.size)
    }

    @Test
    fun `BinInfo fromJson handles minimal JSON`() {
        val json = JSONObject("{}")
        val info = BinInfo.fromJson(json)
        assertEquals("Unknown", info.arch)
        assertEquals(0, info.bits)
        assertEquals("Unknown", info.os)
    }

    @Test
    fun `BinInfo fromJson handles flat JSON without core or bin sections`() {
        val json = JSONObject("""{"type":"EXEC","arch":"arm","bits":32}""")
        val info = BinInfo.fromJson(json)
        assertEquals("arm", info.arch)
        assertEquals(32, info.bits)
        assertEquals("EXEC", info.type)
    }

    @Test
    fun `BinInfo fromJson builds ijSections from core and bin`() {
        val json = JSONObject("""
            {
                "core": {"type": "DYN", "file": "/test"},
                "bin": {"arch": "x86", "bits": 64}
            }
        """.trimIndent())
        val info = BinInfo.fromJson(json)
        assertTrue("Should have core section", info.ijSections.any { it.title == "core" })
        assertTrue("Should have bin section", info.ijSections.any { it.title == "bin" })
    }

    @Test
    fun `BinInfo isAddressLike detects address fields`() {
        // Test via BinInfo.fromJson that addresses are properly formatted
        val json = JSONObject("""
            {
                "bin": {
                    "baddr": 4194304,
                    "laddr": 0,
                    "vaddr": 4096
                }
            }
        """.trimIndent())
        val info = BinInfo.fromJson(json)
        // The ijSections should contain formatted addresses
        val binSection = info.ijSections.find { it.title == "bin" }
        assertNotNull(binSection)
        val baddrField = binSection!!.fields.find { it.label == "baddr" }
        assertNotNull(baddrField)
        assertEquals(4194304L, baddrField!!.address)
        assertTrue(baddrField.value.startsWith("0x"))
    }

    // ========== Section Tests ==========

    @Test
    fun `Section fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "name": ".text",
                "size": 4096,
                "vsize": 8192,
                "perm": "r-x",
                "vaddr": 4194304,
                "paddr": 4096
            }
        """.trimIndent())
        val section = Section.fromJson(json)
        assertEquals(".text", section.name)
        assertEquals(4096L, section.size)
        assertEquals(8192L, section.vSize)
        assertEquals("r-x", section.perm)
        assertEquals(4194304L, section.vAddr)
        assertEquals(4096L, section.pAddr)
    }

    @Test
    fun `Section fromJson handles missing fields with defaults`() {
        val json = JSONObject("{}")
        val section = Section.fromJson(json)
        assertEquals("", section.name)
        assertEquals(0L, section.size)
        assertEquals("", section.perm)
    }

    // ========== Symbol Tests ==========

    @Test
    fun `Symbol fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "name": "sym.main",
                "type": "FUNC",
                "vaddr": 4194560,
                "paddr": 4352,
                "is_imported": false,
                "realname": "main"
            }
        """.trimIndent())
        val symbol = Symbol.fromJson(json)
        assertEquals("sym.main", symbol.name)
        assertEquals("FUNC", symbol.type)
        assertEquals(4194560L, symbol.vAddr)
        assertEquals(4352L, symbol.pAddr)
        assertFalse(symbol.isImported)
        assertEquals("main", symbol.realname)
    }

    @Test
    fun `Symbol fromJson handles imported symbol`() {
        val json = JSONObject("""
            {
                "name": "sym.imp.printf",
                "type": "FUNC",
                "vaddr": 4194304,
                "paddr": 4096,
                "is_imported": true
            }
        """.trimIndent())
        val symbol = Symbol.fromJson(json)
        assertTrue(symbol.isImported)
        assertNull(symbol.realname)
    }

    // ========== ImportInfo Tests ==========

    @Test
    fun `ImportInfo fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "name": "printf",
                "ordinal": 1,
                "type": "FUNC",
                "plt": 4194304
            }
        """.trimIndent())
        val import = ImportInfo.fromJson(json)
        assertEquals("printf", import.name)
        assertEquals(1, import.ordinal)
        assertEquals("FUNC", import.type)
        assertEquals(4194304L, import.plt)
    }

    // ========== Relocation Tests ==========

    @Test
    fun `Relocation fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "name": "realloc",
                "type": "SET_64",
                "vaddr": 6295592,
                "paddr": 295576
            }
        """.trimIndent())
        val reloc = Relocation.fromJson(json)
        assertEquals("realloc", reloc.name)
        assertEquals("SET_64", reloc.type)
        assertEquals(6295592L, reloc.vAddr)
    }

    // ========== StringInfo Tests ==========

    @Test
    fun `StringInfo fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "string": "Hello, World!",
                "vaddr": 4196608,
                "section": ".rodata",
                "type": "ascii"
            }
        """.trimIndent())
        val strInfo = StringInfo.fromJson(json)
        assertEquals("Hello, World!", strInfo.string)
        assertEquals(4196608L, strInfo.vAddr)
        assertEquals(".rodata", strInfo.section)
        assertEquals("ascii", strInfo.type)
    }

    // ========== FunctionInfo Tests ==========

    @Test
    fun `FunctionInfo fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "name": "sym.main",
                "addr": 4194560,
                "size": 256,
                "nbbs": 12,
                "signature": "int main(int argc, char **argv)"
            }
        """.trimIndent())
        val func = FunctionInfo.fromJson(json)
        assertEquals("sym.main", func.name)
        assertEquals(4194560L, func.addr)
        assertEquals(256L, func.size)
        assertEquals(12, func.nbbs)
        assertEquals("int main(int argc, char **argv)", func.signature)
    }

    // ========== DisasmInstruction Tests ==========

    @Test
    fun `DisasmInstruction fromJson parses basic fields`() {
        val json = JSONObject("""
            {
                "addr": 4194560,
                "opcode": "push rbp",
                "bytes": "55",
                "type": "push",
                "size": 1,
                "disasm": "push rbp",
                "family": "cpu"
            }
        """.trimIndent())
        val instr = DisasmInstruction.fromJson(json)
        assertEquals(4194560L, instr.addr)
        assertEquals("push rbp", instr.opcode)
        assertEquals("55", instr.bytes)
        assertEquals("push", instr.type)
        assertEquals(1, instr.size)
        assertEquals("push rbp", instr.disasm)
        assertEquals("cpu", instr.family)
    }

    @Test
    fun `DisasmInstruction fromJson handles offset as addr alias`() {
        val json = JSONObject("""{"offset": 4194560, "opcode": "nop"}""")
        val instr = DisasmInstruction.fromJson(json)
        assertEquals(4194560L, instr.addr)
    }

    @Test
    fun `DisasmInstruction fromJson parses flags`() {
        val json = JSONObject("""
            {
                "addr": 4194560,
                "opcode": "push rbp",
                "flags": ["_start", "entry0"]
            }
        """.trimIndent())
        val instr = DisasmInstruction.fromJson(json)
        assertEquals(listOf("_start", "entry0"), instr.flags)
    }

    @Test
    fun `DisasmInstruction fromJson parses jump and fail`() {
        val json = JSONObject("""
            {
                "addr": 4194560,
                "opcode": "je 0x401020",
                "jump": 4194848,
                "fail": 4194566
            }
        """.trimIndent())
        val instr = DisasmInstruction.fromJson(json)
        assertEquals(4194848L, instr.jump)
        assertEquals(4194566L, instr.fail)
    }

    @Test
    fun `DisasmInstruction fromJson parses refs and xrefs`() {
        val json = JSONObject("""
            {
                "addr": 4194560,
                "opcode": "call 0x401000",
                "refs": [{"addr": 4194304, "type": "CALL"}],
                "xrefs": [{"addr": 4194500, "type": "CODE"}]
            }
        """.trimIndent())
        val instr = DisasmInstruction.fromJson(json)
        assertEquals(1, instr.refs.size)
        assertEquals(4194304L, instr.refs[0].addr)
        assertEquals("CALL", instr.refs[0].type)
        assertEquals(1, instr.xrefs.size)
        assertEquals(4194500L, instr.xrefs[0].addr)
    }

    @Test
    fun `DisasmInstruction isJumpOut returns true when jump target is outside function`() {
        val instr = DisasmInstruction(
            addr = 100L,
            opcode = "jmp",
            bytes = "",
            type = "jmp",
            size = 2,
            disasm = "jmp 0x200",
            family = "cpu",
            fcnAddr = 100L,
            fcnLast = 200L,
            jump = 300L
        )
        assertTrue(instr.isJumpOut())
    }

    @Test
    fun `DisasmInstruction isJumpOut returns false when jump target is inside function`() {
        val instr = DisasmInstruction(
            addr = 100L,
            opcode = "je",
            bytes = "",
            type = "cjmp",
            size = 2,
            disasm = "je 0x150",
            family = "cpu",
            fcnAddr = 100L,
            fcnLast = 200L,
            jump = 150L
        )
        assertFalse(instr.isJumpOut())
    }

    @Test
    fun `DisasmInstruction isJumpOut returns false when no function info`() {
        val instr = DisasmInstruction(
            addr = 100L,
            opcode = "jmp",
            bytes = "",
            type = "jmp",
            size = 2,
            disasm = "jmp 0x200",
            family = "cpu",
            jump = 300L
        )
        assertFalse(instr.isJumpOut())
    }

    @Test
    fun `DisasmInstruction hasJumpIn detects external code xref`() {
        val instr = DisasmInstruction(
            addr = 100L,
            opcode = "nop",
            bytes = "",
            type = "nop",
            size = 1,
            disasm = "nop",
            family = "cpu",
            fcnAddr = 100L,
            fcnLast = 200L,
            xrefs = listOf(DisasmRef(addr = 50L, type = "CODE"))
        )
        assertTrue(instr.hasJumpIn())
    }

    @Test
    fun `DisasmInstruction hasJumpIn returns false for internal xrefs`() {
        val instr = DisasmInstruction(
            addr = 150L,
            opcode = "nop",
            bytes = "",
            type = "nop",
            size = 1,
            disasm = "nop",
            family = "cpu",
            fcnAddr = 100L,
            fcnLast = 200L,
            xrefs = listOf(DisasmRef(addr = 120L, type = "CODE"))
        )
        assertFalse(instr.hasJumpIn())
    }

    @Test
    fun `DisasmInstruction displayAddress formats small address`() {
        val instr = DisasmInstruction(
            addr = 0xFFL,
            opcode = "nop", bytes = "", type = "nop", size = 1,
            disasm = "nop", family = null
        )
        assertEquals("FF", instr.displayAddress)
    }

    @Test
    fun `DisasmInstruction displayAddress formats large address with trimmed zeros`() {
        val instr = DisasmInstruction(
            addr = 0x00401234L,
            opcode = "nop", bytes = "", type = "nop", size = 1,
            disasm = "nop", family = null
        )
        assertEquals("401234", instr.displayAddress)
    }

    @Test
    fun `DisasmInstruction displayAddress handles zero address`() {
        val instr = DisasmInstruction(
            addr = 0L,
            opcode = "nop", bytes = "", type = "nop", size = 1,
            disasm = "nop", family = null
        )
        assertEquals("0", instr.displayAddress)
    }

    @Test fun `compact instruction addresses keep all bits of high addresses`() {
        val instr = DisasmInstruction(-1L, "nop", "00", "nop", 1, "nop", null, ptr = Long.MIN_VALUE)
        assertEquals("FFFFFFFFFFFFFFFF", instr.displayAddress)
        assertEquals("; 8000000000000000", instr.inlineComment)
    }

    @Test
    fun `DisasmInstruction displayBytes truncates long bytes`() {
        val instr = DisasmInstruction(
            addr = 0L,
            opcode = "nop", bytes = "4889e54883ec10", type = "nop", size = 1,
            disasm = "mov rbp, rsp", family = null
        )
        // 14 chars > 10, so should be truncated to first 8 + ellipsis
        assertTrue(instr.displayBytes.endsWith("…"))
        assertTrue(instr.displayBytes.length < "4889e54883ec10".length)
    }

    @Test
    fun `DisasmInstruction displayBytes keeps short bytes intact`() {
        val instr = DisasmInstruction(
            addr = 0L,
            opcode = "nop", bytes = "55", type = "nop", size = 1,
            disasm = "push rbp", family = null
        )
        assertEquals("55", instr.displayBytes)
    }

    @Test
    fun `DisasmInstruction inlineComment shows ptr address`() {
        val instr = DisasmInstruction(
            addr = 0L,
            opcode = "lea", bytes = "", type = "lea", size = 1,
            disasm = "lea rdi, [0x401000]", family = null,
            ptr = 4198400L // 0x401000
        )
        assertTrue(instr.inlineComment.startsWith(";"))
        assertTrue(instr.inlineComment.contains("401000"))
    }

    @Test
    fun `DisasmInstruction inlineComment empty when no ptr or refs`() {
        val instr = DisasmInstruction(
            addr = 0L,
            opcode = "nop", bytes = "", type = "nop", size = 1,
            disasm = "nop", family = null
        )
        assertEquals("", instr.inlineComment)
    }

    // ========== DisasmRef Tests ==========

    @Test
    fun `DisasmRef fromJson parses correctly`() {
        val json = JSONObject("""{"addr": 4194304, "type": "CALL"}""")
        val ref = DisasmRef.fromJson(json)
        assertEquals(4194304L, ref.addr)
        assertEquals("CALL", ref.type)
    }

    // ========== EntryPoint Tests ==========

    @Test
    fun `EntryPoint fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "vaddr": 4194304,
                "paddr": 4096,
                "baddr": 4194304,
                "laddr": 0,
                "haddr": 4096,
                "type": "program"
            }
        """.trimIndent())
        val ep = EntryPoint.fromJson(json)
        assertEquals(4194304L, ep.vAddr)
        assertEquals(4096L, ep.pAddr)
        assertEquals("program", ep.type)
    }

    // ========== Xref Tests ==========

    @Test
    fun `Xref fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "type": "CALL",
                "from": 4194560,
                "to": 4194304,
                "opcode": "call sym.printf",
                "fcn_name": "sym.main",
                "refname": ""
            }
        """.trimIndent())
        val xref = Xref.fromJson(json)
        assertEquals("CALL", xref.type)
        assertEquals(4194560L, xref.from)
        assertEquals(4194304L, xref.to)
        assertEquals("call sym.printf", xref.opcode)
        assertEquals("sym.main", xref.fcnName)
    }

    // ========== FunctionDetailInfo Tests ==========

    @Test
    fun `FunctionDetailInfo fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "name": "sym.main",
                "addr": 4194560,
                "size": 256,
                "realsz": 240,
                "noreturn": false,
                "stackframe": 32,
                "calltype": "amd64",
                "cost": 100,
                "cc": 5,
                "bits": 64,
                "type": "fcn",
                "nbbs": 12,
                "ninstrs": 45,
                "edges": 15,
                "signature": "int main(int argc, char **argv)",
                "minaddr": 4194560,
                "maxaddr": 4194816,
                "nlocals": 3,
                "nargs": 2,
                "is-pure": "true",
                "is-lineal": false,
                "indegree": 2,
                "outdegree": 5,
                "difftype": "new"
            }
        """.trimIndent())
        val detail = FunctionDetailInfo.fromJson(json)
        assertEquals("sym.main", detail.name)
        assertEquals(4194560L, detail.addr)
        assertEquals(256L, detail.size)
        assertFalse(detail.noReturn)
        assertEquals(32, detail.stackFrame)
        assertEquals("amd64", detail.callType)
        assertEquals(64, detail.bits)
        assertEquals(12, detail.nbbs)
        assertTrue(detail.isPure)
        assertFalse(detail.isLineal)
        assertEquals(2, detail.indegree)
        assertEquals(5, detail.outdegree)
    }

    // ========== InstructionDetail Tests ==========

    @Test
    fun `InstructionDetail fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "opcode": "push rbp",
                "disasm": "push rbp",
                "description": "push value onto stack",
                "pseudo": "rbp = push(rbp)",
                "mnemonic": "push",
                "addr": 4194560,
                "bytes": "55",
                "size": 1,
                "type": "push",
                "family": "cpu",
                "esil": "rbp,8,rsp,-,rsp,=[8],rsp,=",
                "cycles": 1,
                "sign": false
            }
        """.trimIndent())
        val detail = InstructionDetail.fromJson(json)
        assertEquals("push rbp", detail.opcode)
        assertEquals("push value onto stack", detail.description)
        assertEquals("push", detail.mnemonic)
        assertEquals(4194560L, detail.addr)
        assertEquals(1, detail.size)
        assertFalse(detail.sign)
    }

    // ========== EntropyData Tests ==========

    @Test
    fun `EntropyData fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "blocksize": 256,
                "address": 0,
                "size": 132456,
                "entropy": [
                    {"addr": 0, "value": 7},
                    {"addr": 256, "value": 5},
                    {"addr": 512, "value": 8}
                ]
            }
        """.trimIndent())
        val entropy = EntropyData.fromJson(json)
        assertEquals(256, entropy.blocksize)
        assertEquals(3, entropy.entropy.size)
        assertEquals(7, entropy.entropy[0].value)
        assertEquals(256L, entropy.entropy[1].addr)
    }

    // ========== HashInfo Tests ==========

    @Test
    fun `HashInfo fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "md5": "d41d8cd98f00b204e9800998ecf8427e",
                "sha1": "da39a3ee5e6b4b0d3255bfef95601890afd80709",
                "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            }
        """.trimIndent())
        val hash = HashInfo.fromJson(json)
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hash.md5)
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", hash.sha1)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash.sha256)
    }

    // ========== SearchResult Tests ==========

    @Test
    fun `SearchResult fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "addr": 4194304,
                "type": "string",
                "data": "Hello",
                "size": 5
            }
        """.trimIndent())
        val result = SearchResult.fromJson(json)
        assertEquals(4194304L, result.addr)
        assertEquals("string", result.type)
        assertEquals("Hello", result.data)
        assertEquals(5, result.size)
    }

    @Test
    fun `SearchResult fromJson falls back to opstr for data`() {
        val json = JSONObject("""
            {
                "addr": 4194304,
                "type": "push",
                "opstr": "push rbp"
            }
        """.trimIndent())
        val result = SearchResult.fromJson(json)
        assertEquals("push rbp", result.data)
    }

    // ========== GitHubRelease Tests ==========

    @Test
    fun `GitHubRelease fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "tag_name": "v0.3.0",
                "html_url": "https://github.com/wsdx233/r2droid/releases/tag/v0.3.0",
                "body": "Release notes here",
                "assets": [
                    {
                        "name": "app-full-release.apk",
                        "browser_download_url": "https://github.com/wsdx233/r2droid/releases/download/v0.3.0/app-full-release.apk"
                    }
                ]
            }
        """.trimIndent())
        val release = GitHubRelease.fromJson(json)
        assertEquals("v0.3.0", release.tagName)
        assertTrue(release.htmlUrl.contains("v0.3.0"))
        assertEquals("Release notes here", release.body)
        assertEquals(1, release.assets.size)
        assertEquals("app-full-release.apk", release.assets[0].name)
    }

    @Test
    fun `GitHubRelease fromJson handles missing body`() {
        val json = JSONObject("""{"tag_name": "v1.0", "html_url": "http://example.com"}""")
        val release = GitHubRelease.fromJson(json)
        // optString returns empty string when key is missing, not null
        assertEquals("", release.body)
        assertTrue(release.assets.isEmpty())
    }

    // ========== FunctionVariable Tests ==========

    @Test
    fun `FunctionVariable fromJson parses correctly`() {
        val json = JSONObject("""{"name": "argc", "kind": "arg", "type": "int"}""")
        val variable = FunctionVariable.fromJson(json, "reg")
        assertEquals("argc", variable.name)
        assertEquals("arg", variable.kind)
        assertEquals("int", variable.type)
        assertEquals("reg", variable.storage)
    }

    @Test
    fun `FunctionVariablesData all combines all variables`() {
        val data = FunctionVariablesData(
            reg = listOf(FunctionVariable("r1", "arg", "int", "reg")),
            sp = listOf(FunctionVariable("s1", "var", "char*", "sp")),
            bp = listOf(FunctionVariable("b1", "var", "long", "bp"))
        )
        assertEquals(3, data.all.size)
        assertFalse(data.isEmpty)
    }

    @Test
    fun `FunctionVariablesData isEmpty returns true when no variables`() {
        val data = FunctionVariablesData()
        assertTrue(data.isEmpty)
        assertTrue(data.all.isEmpty())
    }

    // ========== DecompilationAnnotation Tests ==========

    @Test
    fun `DecompilationAnnotation fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "start": 10,
                "end": 15,
                "type": "syntax_highlight",
                "syntax_highlight": "keyword",
                "offset": 4194304
            }
        """.trimIndent())
        val ann = DecompilationAnnotation.fromJson(json)
        assertEquals(10, ann.start)
        assertEquals(15, ann.end)
        assertEquals("syntax_highlight", ann.type)
        assertEquals("keyword", ann.syntaxHighlight)
        assertEquals(4194304L, ann.offset)
    }

    @Test
    fun `DecompilationAnnotation fromJson handles missing syntax_highlight`() {
        val json = JSONObject("""{"start": 0, "end": 5, "type": "offset"}""")
        val ann = DecompilationAnnotation.fromJson(json)
        assertNull(ann.syntaxHighlight)
    }

    // ========== FunctionXref Tests ==========

    @Test
    fun `FunctionXref fromJson parses correctly`() {
        val json = JSONObject("""{"type": "CALL", "from": 100, "to": 200}""")
        val xref = FunctionXref.fromJson(json)
        assertEquals("CALL", xref.type)
        assertEquals(100L, xref.from)
        assertEquals(200L, xref.to)
    }
}

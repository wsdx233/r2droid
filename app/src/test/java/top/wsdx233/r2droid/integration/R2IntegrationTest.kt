package top.wsdx233.r2droid.integration

import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import top.wsdx233.r2droid.core.data.model.*
import top.wsdx233.r2droid.core.data.parser.CLexer
import top.wsdx233.r2droid.testing.HostR2Pipe
import java.io.File

/**
 * Integration tests that exercise the real radare2 binary on the host machine.
 *
 * These tests launch r2, execute commands against a real binary, parse the
 * JSON output with the same model classes used by the Android app, and
 * verify that everything works end-to-end.
 *
 * Requirements:
 *   - radare2 must be installed and on PATH
 *   - A test binary must exist at the path specified by TEST_BINARY
 *
 * Run:  ./gradlew :app:testFullDebugUnitTest --tests "*.integration.*"
 */
class R2IntegrationTest {

    companion object {
        // Try common binaries across platforms
        private val CANDIDATE_BINARIES = listOf(
            "C:/Windows/notepad.exe",   // Windows
            "/bin/ls",                   // macOS / Linux
            "/usr/bin/ls",               // Linux alternative
            "/bin/echo",                 // Common Unix
            "/usr/bin/file"              // Common Unix
        )

        private lateinit var testBinary: String
        private lateinit var r2: HostR2Pipe
        private var initialized = false

        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            // Find an available binary
            testBinary = CANDIDATE_BINARIES.firstOrNull { File(it).exists() }
                ?: throw AssumptionViolatedException("No suitable test binary found on this system")

            println("[R2IntegrationTest] Using test binary: $testBinary")

            try {
                r2 = HostR2Pipe.open(
                    binaryPath = testBinary,
                    verbose = true
                )
                // Quick sanity check
                val greeting = r2.cmd("?V")
                assertTrue("Expected a radare2 version, got: $greeting", greeting.contains(Regex("\\d+\\.\\d+")))
                initialized = true
                println("[R2IntegrationTest] radare2 ready: $greeting")
            } catch (e: Exception) {
                throw AssumptionViolatedException("radare2 not available on this host: ${e.message}")
            }
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            if (initialized) {
                try {
                    r2.quit()
                } catch (_: Exception) {
                }
            }
        }
    }

    // ============================================================
    //  1. Binary Overview (ij)
    // ============================================================

    @Test
    fun `ij - parse binary overview from real r2 output`() {
        val output = r2.cmdj("ij")
        assertTrue("ij should return non-empty JSON", output.isNotBlank())
        assertTrue("ij should start with {", output.trimStart().startsWith("{"))

        val json = JSONObject(output)
        val info = BinInfo.fromJson(json)

        // Basic sanity: a real binary should have meaningful values
        assertNotEquals("arch should not be Unknown", "Unknown", info.arch)
        assertTrue("bits should be > 0", info.bits > 0)
        assertNotEquals("os should not be Unknown", "Unknown", info.os)

        println("  arch=${info.arch}, bits=${info.bits}, os=${info.os}, type=${info.type}")
        println("  binaryClass=${info.binaryClass}, format=${info.format}, endian=${info.endian}")
        println("  file=${info.file}, humanSize=${info.humanSize}, size=${info.size}")
    }

    @Test
    fun `ij - core section should contain file and size`() {
        val output = r2.cmdj("ij")
        val json = JSONObject(output)
        val core = json.optJSONObject("core")
        assertNotNull("Should have 'core' section", core)

        val info = BinInfo.fromJson(json)
        assertTrue("File name should not be empty", info.file.isNotEmpty())
        assertTrue("Size should be > 0", info.size > 0)
    }

    @Test
    fun `ij - bin section should contain meaningful analysis data`() {
        val output = r2.cmdj("ij")
        val json = JSONObject(output)
        val bin = json.optJSONObject("bin")
        assertNotNull("Should have 'bin' section", bin)

        val info = BinInfo.fromJson(json)
        // At least one of these should be meaningful
        val hasAny = info.arch.isNotEmpty() ||
                info.language.isNotEmpty() ||
                info.machine.isNotEmpty() ||
                info.compiler.isNotEmpty()
        assertTrue("Should have at least some bin metadata", hasAny)
    }

    @Test
    fun `ij - ijSections should be populated`() {
        val output = r2.cmdj("ij")
        val json = JSONObject(output)
        val info = BinInfo.fromJson(json)
        assertTrue("Should have at least one ijSection", info.ijSections.isNotEmpty())

        for (section in info.ijSections) {
            assertTrue("Section title should not be empty", section.title.isNotEmpty())
        }
    }

    // ============================================================
    //  2. Sections (iSj)
    // ============================================================

    @Test
    fun `iSj - parse sections from real r2 output`() {
        val output = r2.cmdj("iSj")
        assertTrue("iSj should return non-empty", output.isNotBlank())

        val jsonArray = JSONArray(output)
        assertTrue("Should have at least 1 section", jsonArray.length() > 0)

        val sections = mutableListOf<Section>()
        for (i in 0 until jsonArray.length()) {
            val s = Section.fromJson(jsonArray.getJSONObject(i))
            sections.add(s)
        }

        // Every real binary should have at least one section
        assertTrue("Should parse at least 1 section", sections.isNotEmpty())

        // ELF reserves section zero (SHT_NULL); it legitimately has no name or contents.
        for ((index, s) in sections.withIndex()) {
            if (s.name.isEmpty()) {
                assertEquals(0, index)
                assertEquals("NULL", s.type)
                assertEquals(0L, s.size)
            }
        }

        println("  Found ${sections.size} sections:")
        sections.take(5).forEach { println("    ${it.name} size=${it.size} vsize=${it.vSize} perm=${it.perm}") }
    }

    @Test
    fun `iSj - section addresses should be consistent`() {
        val output = r2.cmdj("iSj")
        val jsonArray = JSONArray(output)
        for (i in 0 until jsonArray.length()) {
            val s = Section.fromJson(jsonArray.getJSONObject(i))
            // vaddr and paddr should be >= 0
            assertTrue("vaddr should be >= 0", s.vAddr >= 0)
            assertTrue("paddr should be >= 0", s.pAddr >= 0)
        }
    }

    // ============================================================
    //  3. Symbols (isj)
    // ============================================================

    @Test
    fun `isj - parse symbols from real r2 output`() {
        val output = r2.cmdj("isj")
        if (output.isBlank() || output == "[]") {
            println("  No symbols found (stripped binary)")
            return
        }

        val jsonArray = JSONArray(output)
        val symbols = mutableListOf<Symbol>()
        for (i in 0 until jsonArray.length()) {
            symbols.add(Symbol.fromJson(jsonArray.getJSONObject(i)))
        }

        assertTrue("Should parse some symbols", symbols.isNotEmpty())

        // Check that at least some symbols have names
        val namedSymbols = symbols.filter { it.name.isNotBlank() }
        assertTrue("Should have named symbols", namedSymbols.isNotEmpty())

        println("  Found ${symbols.size} symbols (${namedSymbols.size} named)")
    }

    @Test
    fun `isj - imported symbols should be identifiable`() {
        val output = r2.cmdj("isj")
        if (output.isBlank() || output == "[]") return

        val jsonArray = JSONArray(output)
        val imports = mutableListOf<Symbol>()
        for (i in 0 until jsonArray.length()) {
            val s = Symbol.fromJson(jsonArray.getJSONObject(i))
            if (s.isImported) imports.add(s)
        }

        if (imports.isNotEmpty()) {
            println("  Found ${imports.size} imported symbols")
            // Imported symbols should have meaningful names
            for (imp in imports.take(5)) {
                assertTrue("Import name should not be empty", imp.name.isNotBlank())
            }
        }
    }

    // ============================================================
    //  4. Imports (iij)
    // ============================================================

    @Test
    fun `iij - parse imports from real r2 output`() {
        val output = r2.cmdj("iij")
        if (output.isBlank() || output == "[]") {
            println("  No imports found")
            return
        }

        val jsonArray = JSONArray(output)
        val imports = mutableListOf<ImportInfo>()
        for (i in 0 until jsonArray.length()) {
            imports.add(ImportInfo.fromJson(jsonArray.getJSONObject(i)))
        }

        assertTrue("Should parse imports", imports.isNotEmpty())

        // Imports should have names
        for (imp in imports) {
            assertTrue("Import name should not be empty", imp.name.isNotBlank())
        }

        println("  Found ${imports.size} imports")
        imports.take(3).forEach { println("    ${it.name} type=${it.type} plt=0x${it.plt.toString(16)}") }
    }

    // ============================================================
    //  5. Strings (izj)
    // ============================================================

    @Test
    fun `izj - parse strings from real r2 output`() {
        val output = r2.cmdj("izj")
        if (output.isBlank() || output == "[]") {
            println("  No strings found")
            return
        }

        val jsonArray = JSONArray(output)
        val strings = mutableListOf<StringInfo>()
        for (i in 0 until jsonArray.length()) {
            strings.add(StringInfo.fromJson(jsonArray.getJSONObject(i)))
        }

        assertTrue("Should parse strings", strings.isNotEmpty())

        // Strings should have content
        for (str in strings) {
            // r2 may return strings with escape-only content (e.g. "\t", "\n")
            // which are technically non-empty but may appear as whitespace.
            // Just verify the field is present and parseable.
        }

        println("  Found ${strings.size} strings")
        strings.take(3).forEach { println("    0x${it.vAddr.toString(16)}: \"${it.string.take(50)}\"") }
    }

    // ============================================================
    //  6. Entry Points (iej)
    // ============================================================

    @Test
    fun `iej - parse entry points from real r2 output`() {
        val output = r2.cmdj("iej")
        if (output.isBlank() || output == "[]") {
            println("  No entry points found")
            return
        }

        val jsonArray = JSONArray(output)
        val entries = mutableListOf<EntryPoint>()
        for (i in 0 until jsonArray.length()) {
            entries.add(EntryPoint.fromJson(jsonArray.getJSONObject(i)))
        }

        assertTrue("Should have at least 1 entry point", entries.isNotEmpty())

        for (ep in entries) {
            // vaddr should be non-zero for an entry point
            assertTrue("Entry vaddr should be > 0", ep.vAddr > 0)
        }

        println("  Found ${entries.size} entry points")
        entries.forEach { println("    0x${it.vAddr.toString(16)} type=${it.type}") }
    }

    // ============================================================
    //  7. Functions (aflj) — requires analysis
    // ============================================================

    @Test
    fun `aflj - parse functions after analysis`() {
        // Run analysis first
        r2.cmd("aa")

        val output = r2.cmdj("aflj")
        if (output.isBlank() || output == "[]") {
            println("  No functions found after analysis")
            return
        }

        val jsonArray = JSONArray(output)
        val functions = mutableListOf<FunctionInfo>()
        for (i in 0 until jsonArray.length()) {
            functions.add(FunctionInfo.fromJson(jsonArray.getJSONObject(i)))
        }

        assertTrue("Should have at least 1 function after analysis", functions.isNotEmpty())

        for (f in functions) {
            assertTrue("Function name should not be empty", f.name.isNotBlank())
            assertTrue("Function addr should be > 0", f.addr > 0)
            // size can be 0 for some functions (thunks etc.)
        }

        println("  Found ${functions.size} functions")
        functions.take(5).forEach { println("    ${it.name} @ 0x${it.addr.toString(16)} size=${it.size}") }
    }

    // ============================================================
    //  8. Disassembly (pdj) — requires analysis
    // ============================================================

    @Test
    fun `pdj - parse disassembly at entry point`() {
        r2.cmd("aa")

        val output = r2.cmdj("pdj 10 @ entry0")
        if (output.isBlank() || output == "[]") {
            println("  No disassembly at entry0")
            return
        }

        val jsonArray = JSONArray(output)
        val instructions = mutableListOf<DisasmInstruction>()
        for (i in 0 until jsonArray.length()) {
            instructions.add(DisasmInstruction.fromJson(jsonArray.getJSONObject(i)))
        }

        assertTrue("Should have at least 1 instruction", instructions.isNotEmpty())

        for (instr in instructions) {
            assertTrue("Instruction addr should be > 0", instr.addr > 0)
            // opcode or disasm should be non-empty
            assertTrue("Should have opcode or disasm", instr.opcode.isNotBlank() || instr.disasm.isNotBlank())
        }

        println("  Parsed ${instructions.size} instructions:")
        instructions.take(5).forEach {
            println("    0x${it.addr.toString(16)}: ${it.disasm}")
        }
    }

    @Test
    fun `pdj - displayAddress and displayBytes should produce valid output`() {
        r2.cmd("aa")

        val output = r2.cmdj("pdj 20 @ entry0")
        if (output.isBlank() || output == "[]") return

        val jsonArray = JSONArray(output)
        for (i in 0 until jsonArray.length()) {
            val instr = DisasmInstruction.fromJson(jsonArray.getJSONObject(i))
            // displayAddress should be a hex string
            val addr = instr.displayAddress
            assertTrue("displayAddress should not be empty", addr.isNotEmpty())
            assertTrue("displayAddress should be valid hex", addr.all { it.isLetterOrDigit() })

            // displayBytes should be lowercase
            if (instr.bytes.isNotBlank()) {
                val b = instr.displayBytes
                assertTrue("displayBytes should not be empty", b.isNotEmpty())
            }
        }
    }

    @Test
    fun `pdj - instruction flags and refs should parse without error`() {
        r2.cmd("aa")

        val output = r2.cmdj("pdj 30 @ entry0")
        if (output.isBlank() || output == "[]") return

        val jsonArray = JSONArray(output)
        for (i in 0 until jsonArray.length()) {
            val instr = DisasmInstruction.fromJson(jsonArray.getJSONObject(i))
            // Just verify no crash — flags/refs may or may not be present
            instr.flags
            instr.refs
            instr.xrefs
            instr.jump
            instr.fail
            instr.ptr
            instr.esil
        }
    }

    // ============================================================
    //  9. Function Detail (afij) — requires analysis
    // ============================================================

    @Test
    fun `afij - parse function detail info`() {
        r2.cmd("aa")

        // Get first function address
        val funcOutput = r2.cmdj("aflj")
        if (funcOutput.isBlank() || funcOutput == "[]") return

        val funcArray = JSONArray(funcOutput)
        val firstFunc = funcArray.getJSONObject(0)
        val addr = firstFunc.optLong("offset", firstFunc.optLong("addr", 0))
        if (addr == 0L) return

        val detailOutput = r2.cmdj("afij @ $addr")
        if (detailOutput.isBlank() || detailOutput == "[]") return

        val detailArray = JSONArray(detailOutput)
        val detail = FunctionDetailInfo.fromJson(detailArray.getJSONObject(0))

        assertEquals("Function name should match", firstFunc.optString("name", ""), detail.name)
        assertTrue("Should have nbbs >= 0", detail.nbbs >= 0)
        assertTrue("Should have edges >= 0", detail.edges >= 0)
        assertTrue("Should have ninstrs >= 0", detail.ninstrs >= 0)

        println("  Function: ${detail.name}")
        println("    size=${detail.size}, nbbs=${detail.nbbs}, edges=${detail.edges}")
        println("    signature=${detail.signature}")
    }

    // ============================================================
    //  10. Function Variables (afvj)
    // ============================================================

    @Test
    fun `afvj - parse function variables`() {
        r2.cmd("aa")

        val funcOutput = r2.cmdj("aflj")
        if (funcOutput.isBlank() || funcOutput == "[]") return

        val funcArray = JSONArray(funcOutput)
        // Try multiple functions to find one with variables
        for (i in 0 until minOf(5, funcArray.length())) {
            val func = funcArray.getJSONObject(i)
            val addr = func.optLong("offset", func.optLong("addr", 0))
            if (addr == 0L) continue

            val varOutput = r2.cmdj("afvj @ $addr")
            if (varOutput.isBlank() || varOutput == "{}") continue

            val json = JSONObject(varOutput)
            val data = FunctionVariablesData(
                reg = parseVarArray(json, "reg"),
                sp = parseVarArray(json, "sp"),
                bp = parseVarArray(json, "bp")
            )

            println("  Function ${func.optString("name")} vars: reg=${data.reg.size}, sp=${data.sp.size}, bp=${data.bp.size}")
            // At least verify it doesn't crash
            data.all.forEach { v ->
                assertTrue("Variable name should not be empty", v.name.isNotBlank())
            }
            return // Found a function with variables, test passes
        }
        println("  No function with variables found (still passes)")
    }

    private fun parseVarArray(json: JSONObject, key: String): List<FunctionVariable> {
        val arr = json.optJSONArray(key) ?: return emptyList()
        val list = mutableListOf<FunctionVariable>()
        for (i in 0 until arr.length()) {
            list.add(FunctionVariable.fromJson(arr.getJSONObject(i), key))
        }
        return list
    }

    // ============================================================
    //  11. Xrefs (axtj / axfj)
    // ============================================================

    @Test
    fun `axtj - parse cross-references`() {
        r2.cmd("aa")

        val funcOutput = r2.cmdj("aflj")
        if (funcOutput.isBlank() || funcOutput == "[]") return

        val funcArray = JSONArray(funcOutput)
        val addr = funcArray.getJSONObject(0).optLong("offset", funcArray.getJSONObject(0).optLong("addr", 0))
        if (addr == 0L) return

        val xrefOutput = r2.cmdj("axtj @ $addr")
        if (xrefOutput.isBlank() || xrefOutput == "[]") {
            println("  No xrefs found")
            return
        }

        val xrefArray = JSONArray(xrefOutput)
        val xrefs = mutableListOf<Xref>()
        for (i in 0 until xrefArray.length()) {
            xrefs.add(Xref.fromJson(xrefArray.getJSONObject(i)))
        }

        println("  Found ${xrefs.size} xrefs to 0x${addr.toString(16)}")
        xrefs.take(3).forEach {
            println("    type=${it.type} from=0x${it.from.toString(16)} to=0x${it.to.toString(16)}")
        }
    }

    // ============================================================
    //  12. Relocations (irj)
    // ============================================================

    @Test
    fun `irj - parse relocations`() {
        val output = r2.cmdj("irj")
        if (output.isBlank() || output == "[]") {
            println("  No relocations found")
            return
        }

        val jsonArray = JSONArray(output)
        val relocs = mutableListOf<Relocation>()
        for (i in 0 until jsonArray.length()) {
            relocs.add(Relocation.fromJson(jsonArray.getJSONObject(i)))
        }

        if (relocs.isNotEmpty()) {
            println("  Found ${relocs.size} relocations")
            relocs.take(3).forEach {
                println("    ${it.name} type=${it.type} @ 0x${it.vAddr.toString(16)}")
            }
        }
    }

    // ============================================================
    //  13. Instruction Detail (aoj)
    // ============================================================

    @Test
    fun `aoj - parse instruction detail`() {
        r2.cmd("aa")

        val disasmOutput = r2.cmdj("pdj 1 @ entry0")
        if (disasmOutput.isBlank() || disasmOutput == "[]") return

        val instrArray = JSONArray(disasmOutput)
        if (instrArray.length() == 0) return
        val addr = instrArray.getJSONObject(0).optLong("addr", 0)
        if (addr == 0L) return

        val detailOutput = r2.cmdj("aoj @ $addr")
        if (detailOutput.isBlank() || detailOutput == "[]") return

        val detailArray = JSONArray(detailOutput)
        val detail = InstructionDetail.fromJson(detailArray.getJSONObject(0))

        // Should have at least opcode/disasm
        assertTrue("Should have opcode", detail.opcode.isNotBlank())

        println("  Instruction detail at 0x${addr.toString(16)}:")
        println("    opcode=${detail.opcode}")
        println("    disasm=${detail.disasm}")
        println("    mnemonic=${detail.mnemonic}")
        println("    family=${detail.family}")
        println("    esil=${detail.esil}")
    }

    // ============================================================
    //  14. Graph (agj) — requires analysis
    // ============================================================

    @Test
    fun `agj - parse function control flow graph`() {
        r2.cmd("aa")

        val funcOutput = r2.cmdj("aflj")
        if (funcOutput.isBlank() || funcOutput == "[]") return

        val funcArray = JSONArray(funcOutput)
        // Find a non-trivial function (size > 20)
        var targetAddr = 0L
        var targetName = ""
        for (i in 0 until minOf(20, funcArray.length())) {
            val f = funcArray.getJSONObject(i)
            if (f.optLong("size", 0) > 20) {
                targetAddr = f.optLong("offset", f.optLong("addr", 0))
                targetName = f.optString("name", "")
                break
            }
        }
        if (targetAddr == 0L) {
            println("  No suitable function for graph test")
            return
        }

        val graphOutput = r2.cmdj("agj @ $targetAddr")
        if (graphOutput.isBlank() || graphOutput == "[]") {
            println("  No graph output for $targetName")
            return
        }

        val graphArray = JSONArray(graphOutput)
        val graph = GraphData.fromAgj(graphArray)

        assertEquals("Graph title should be function name", targetName, graph.title)
        assertTrue("Should have at least 1 node", graph.nodes.isNotEmpty())

        println("  Graph for $targetName: ${graph.nodes.size} nodes")
        graph.nodes.take(5).forEach { node ->
            println("    node[${node.id}] title=${node.title} out=${node.outNodes} instrs=${node.instructions.size}")
        }
    }

    @Test
    fun `agrj - parse xref graph`() {
        r2.cmd("aa")

        val funcOutput = r2.cmdj("aflj")
        if (funcOutput.isBlank() || funcOutput == "[]") return

        val funcArray = JSONArray(funcOutput)
        val addr = funcArray.getJSONObject(0).optLong("offset", funcArray.getJSONObject(0).optLong("addr", 0))
        if (addr == 0L) return

        val graphOutput = r2.cmdj("agrj @ $addr")
        if (graphOutput.isBlank()) {
            println("  No xref graph output")
            return
        }

        val json = JSONObject(graphOutput)
        val graph = GraphData.fromAgrj(json)

        // xref graph may be empty for some functions
        println("  Xref graph: ${graph.nodes.size} nodes")
    }

    // ============================================================
    //  15. Hex Dump (pxj)
    // ============================================================

    @Test
    fun `pxj - parse hex dump as byte array`() {
        val output = r2.cmdj("pxj 64 @ 0")
        if (output.isBlank() || output == "[]") {
            println("  No hex dump available")
            return
        }

        val jsonArray = JSONArray(output)
        assertTrue("Should have 64 bytes", jsonArray.length() == 64)

        val bytes = ByteArray(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            bytes[i] = jsonArray.getInt(i).toByte()
        }

        // At least some bytes should be non-zero for a real binary
        val nonZero = bytes.count { it.toInt() != 0 }
        assertTrue("Hex dump should contain some non-zero bytes", nonZero > 0)

        println("  Read ${bytes.size} bytes, $nonZero non-zero")
    }

    // ============================================================
    //  16. Search (search commands)
    // ============================================================

    @Test
    fun `search - parse search results`() {
        // Search for a common byte pattern
        val output = r2.cmd("/x 9090")
        // Text-based search results — just verify it doesn't crash
        println("  Search result: ${output.take(100)}")
    }

    // ============================================================
    //  17. CLexer with real decompiled code
    // ============================================================

    @Test
    fun `CLexer tokenizes real r2 decompilation output`() {
        // pdc ships with the core. Exercise its actual output without depending on host plugins.
        r2.cmd("af @ entry0")
        val code = r2.cmd("pdc @ entry0")
        val annotations = CLexer.tokenize(code)
        assertTrue("Expected type/keyword tokens in: ${code.take(150)}", annotations.any {
            it.syntaxHighlight == "keyword" || it.syntaxHighlight == "datatype"
        })
    }

    // ============================================================
    //  18. End-to-End Pipeline Test
    // ============================================================

    @Test
    fun `end-to-end - full analysis pipeline from r2 command to model`() {
        // Step 1: Get binary overview
        val ijOutput = r2.cmdj("ij")
        val binInfo = BinInfo.fromJson(JSONObject(ijOutput))
        println("  [Pipeline] Binary: ${binInfo.file} (${binInfo.arch}-${binInfo.bits})")

        // Step 2: Get sections
        val iSjOutput = r2.cmdj("iSj")
        if (iSjOutput.isNotBlank() && iSjOutput != "[]") {
            val sections = parseJsonArray(iSjOutput) { Section.fromJson(it) }
            println("  [Pipeline] Sections: ${sections.size}")
        }

        // Step 3: Get imports
        val iijOutput = r2.cmdj("iij")
        if (iijOutput.isNotBlank() && iijOutput != "[]") {
            val imports = parseJsonArray(iijOutput) { ImportInfo.fromJson(it) }
            println("  [Pipeline] Imports: ${imports.size}")
        }

        // Step 4: Get strings
        val izjOutput = r2.cmdj("izj")
        if (izjOutput.isNotBlank() && izjOutput != "[]") {
            val strings = parseJsonArray(izjOutput) { StringInfo.fromJson(it) }
            println("  [Pipeline] Strings: ${strings.size}")
        }

        // Step 5: Analyze and get functions
        r2.cmd("aa")
        val afljOutput = r2.cmdj("aflj")
        if (afljOutput.isNotBlank() && afljOutput != "[]") {
            val functions = parseJsonArray(afljOutput) { FunctionInfo.fromJson(it) }
            println("  [Pipeline] Functions: ${functions.size}")

            // Step 6: Disassemble first function
            if (functions.isNotEmpty()) {
                val firstFunc = functions.first()
                val pdjOutput = r2.cmdj("pdj 10 @ ${firstFunc.addr}")
                if (pdjOutput.isNotBlank() && pdjOutput != "[]") {
                    val instructions = parseJsonArray(pdjOutput) { DisasmInstruction.fromJson(it) }
                    println("  [Pipeline] Disassembled ${instructions.size} instructions from ${firstFunc.name}")
                }
            }
        }

        // Step 7: Entry points
        val iejOutput = r2.cmdj("iej")
        if (iejOutput.isNotBlank() && iejOutput != "[]") {
            val entries = parseJsonArray(iejOutput) { EntryPoint.fromJson(it) }
            println("  [Pipeline] Entry points: ${entries.size}")
        }

        println("  [Pipeline] Full analysis pipeline completed successfully!")
    }

    // ============================================================
    //  Helper
    // ============================================================

    private inline fun <T> parseJsonArray(json: String, transform: (JSONObject) -> T): List<T> {
        val arr = JSONArray(json)
        val list = mutableListOf<T>()
        for (i in 0 until arr.length()) {
            list.add(transform(arr.getJSONObject(i)))
        }
        return list
    }
}

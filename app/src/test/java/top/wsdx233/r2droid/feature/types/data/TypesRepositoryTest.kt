package top.wsdx233.r2droid.feature.types.data

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.wsdx233.r2droid.core.data.model.FunctionVariable
import top.wsdx233.r2droid.core.data.source.R2DataSource
import top.wsdx233.r2droid.feature.disasm.data.DisasmRepository
import java.io.InputStream

class TypesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun parsesTsjTujTejTtjTlTjAndTccj() {
        val structs = TypesJsonParser.parseTsj(
            """[
                {"name":"Packet","format":"dwd[16]b magic len flags data","fields":[
                    {"name":"magic","type":"uint32_t","offset":0,"size":4},
                    {"name":"len","type":"uint16_t","offset":4,"size":2},
                    {"name":"data","type":"uint8_t","offset":8,"size":16,"array_size":16}
                ]},
                {"name":"AliasStruct","format":"d x"}
            ]"""
        )
        assertEquals(2, structs.size)
        val packet = structs.first { it.name == "Packet" }
        assertEquals(24, packet.totalSize)
        assertEquals(3, packet.fields.size)
        assertEquals("uint8_t[16]", packet.fields[2].displayType)

        val unions = TypesJsonParser.parseTuj(
            """[{"name":"Val","format":"0q[8]b u64 raw","fields":[
                {"name":"u64","type":"uint64_t","offset":0,"size":8},
                {"name":"i32","type":"int32_t","offset":0,"size":4}
            ]}]"""
        )
        assertEquals(1, unions.size)
        assertEquals(8, unions[0].totalSize)

        val enums = TypesJsonParser.parseTej("""{"HttpStatus":{"NOT_FOUND":404,"OK":200}}""")
        assertEquals(1, enums.size)
        assertEquals("HttpStatus", enums[0].name)
        assertEquals(listOf(EnumMember("OK", 200L), EnumMember("NOT_FOUND", 404L)), enums[0].members)

        val typedefs = TypesJsonParser.parseTtj("""{"size_t":"unsigned long","CtxPtr":"struct Ctx *"}""")
        assertEquals(2, typedefs.size)
        assertEquals("struct Ctx *", typedefs.first { it.name == "CtxPtr" }.targetType)

        val links = TypesJsonParser.parseTl(
            """
            0x00401000 = Packet
            0xffff800000001020 = char *
            """.trimIndent()
        )
        assertEquals(2, links.size)
        assertEquals(0x401000L, links[0].address)
        assertEquals("Packet", links[0].typeName)
        assertEquals(0xffff800000001020uL.toLong(), links[1].address)
        assertEquals("char *", links[1].typeName)

        val primitives = TypesJsonParser.parseTj("""{"types":[{"type":"int32_t","size":32,"format":"d"}]}""")
        assertEquals(1, primitives.size)
        assertEquals(32, primitives[0].sizeBits)

        val ccs = TypesJsonParser.parseTccj(
            """{"amd64":{"ret":"rax","args":["rdi","rsi"],"signature":"rax amd64 (rdi, rsi)"}}"""
        )
        assertEquals(1, ccs.size)
        assertEquals("amd64", ccs[0].name)
        assertEquals("rax amd64 (rdi, rsi)", ccs[0].summary)
    }

    @Test
    fun buildTdCommandStripsLineCommentsAndProtectsSemicolons() {
        val decl = """
            // packet header
            struct Packet {
                uint32_t magic; // 0xdeadbeef
                char *url; /* "http://example.com" */
            };
        """.trimIndent()
        val cmd = TypesJsonParser.buildTdCommand(decl)
        assertTrue(cmd.startsWith("'td "))
        assertFalse(cmd.contains('\n'))
        assertFalse(cmd.contains("packet header"))
        assertFalse(cmd.contains("0xdeadbeef"))
        assertTrue(cmd.contains("uint32_t magic;"))
        assertTrue(cmd.contains("char *url;"))
    }

    @Test
    fun preprocessorDirectivesUseTempHeaderFileImport() = runTest {
        val code = """
            #define BUF_SIZE 32
            struct Buf { uint8_t data[BUF_SIZE]; };
        """.trimIndent()
        assertTrue(TypesJsonParser.hasPreprocessorDirectives(code))

        val session = FakeTypesSession()
        TypesRepository.defineFromC(session, code, cacheDir = tmp.root)
        assertEquals(1, session.transactionCommands.size)
        assertTrue(session.transactionCommands[0].startsWith("'to "))
        assertTrue(session.transactionCommands[0].endsWith(".h"))
    }

    @Test
    fun updateTypeRollsBackOnParseFailure() = runTest {
        val session = FakeTypesSession(failOnPrefix = "'td struct Broken")
        val previous = "struct Good { int a; };"
        val result = runCatching {
            TypesRepository.updateTypeCDefinition(
                session = session,
                originalName = "Good",
                previousC = previous,
                newC = "struct Broken { int ;"
            )
        }
        assertTrue(result.isFailure)
        assertEquals(
            listOf("t- Good", "'td struct Broken { int ;", "'td struct Good { int a; };"),
            session.transactionCommands
        )
    }

    @Test
    fun linkTypeToleratesNonFunctionStderrWarningAndVerifiesLink() = runTest {
        val session = FakeTypesSession(
            rawReplies = mapOf(
                "tl Packet = 0x402000" to "[stderr]\nERROR: Cannot find any function here",
                "tl" to "0x00402000 = Packet\n"
            )
        )
        TypesRepository.linkTypeToAddress(session, "Packet", 0x402000L)
        assertTrue("tl Packet = 0x402000" in session.rawCommands)

        val unknownSession = FakeTypesSession(
            rawReplies = mapOf("tl Missing = 0x402000" to "[stderr]\nERROR: unknown type Missing")
        )
        val failure = runCatching {
            TypesRepository.linkTypeToAddress(unknownSession, "Missing", 0x402000L)
        }
        assertTrue(failure.isFailure)
    }

    @Test
    fun functionVariableParsesRegAndStackRefDisplay() {
        val regVar = FunctionVariable.fromJson(
            JSONObject("""{"name":"ctx","kind":"reg","type":"struct Ctx *","ref":"rdi"}"""),
            storage = "reg"
        )
        assertEquals("rdi", regVar.refDisplay)

        val bpVar = FunctionVariable.fromJson(
            JSONObject("""{"name":"var_18h","kind":"var","type":"int64_t","ref":{"base":"rbp","offset":-24}}"""),
            storage = "bp"
        )
        assertEquals("rbp-0x18", bpVar.refDisplay)

        val spVar = FunctionVariable.fromJson(
            JSONObject("""{"name":"arg_10h","kind":"arg","type":"void *","ref":{"base":"rsp","offset":16}}"""),
            storage = "sp"
        )
        assertEquals("rsp+0x10", spVar.refDisplay)
    }

    @Test
    fun disasmRepositoryFormatsAfvtAfsAfcCommandsSafely() = runTest {
        val executed = mutableListOf<String>()
        val source = object : R2DataSource {
            override suspend fun execute(cmd: String): Result<String> {
                executed += cmd
                return Result.success("")
            }
            override suspend fun executeJson(cmd: String): Result<String> {
                executed += cmd
                return Result.success("{}")
            }
            override suspend fun <T> executeStream(cmd: String, block: suspend (InputStream) -> T): Result<T> =
                error("unused")
        }
        val repo = DisasmRepository(source)

        assertTrue(repo.retypeFunctionVariable(0x401000L, "var_8h", "struct Packet *").isSuccess)
        assertTrue(repo.setFunctionSignature(0x401000L, "int handle(struct Packet *p);").isSuccess)
        assertTrue(repo.setFunctionCallingConvention(0x401000L, "amd64").isSuccess)
        assertEquals(
            listOf(
                """afvt var_8h "struct Packet *" @ 0x401000""",
                "afs int handle(struct Packet *p) @ 0x401000",
                "afc amd64 @ 0x401000"
            ),
            executed
        )

        // Reject unsafe command separators
        assertTrue(repo.retypeFunctionVariable(0x401000L, "v", "int; !sh").isFailure)
        assertTrue(repo.setFunctionSignature(0x401000L, "int f(); !sh").isFailure)
        assertTrue(repo.setFunctionCallingConvention(0x401000L, "amd64; q").isFailure)
        assertNull(TypesJsonParser.parseUnsignedAddress("not_an_addr"))
    }

    @Test
    fun sanitizeHeaderFilenameProducesSafeHeaderName() {
        assertEquals("my_types.h", TypesJsonParser.sanitizeHeaderFilename("/tmp/my types.h"))
        assertEquals("custom_defs.h", TypesJsonParser.sanitizeHeaderFilename("custom defs"))
        assertEquals("imported_types.h", TypesJsonParser.sanitizeHeaderFilename(null))
    }

    private class FakeTypesSession(
        private val rawReplies: Map<String, String> = emptyMap(),
        private val failOnPrefix: String? = null
    ) : TypesCommandSession {
        val transactionCommands = mutableListOf<String>()
        val rawCommands = mutableListOf<String>()

        override suspend fun <T> transaction(
            operation: String,
            markDirty: Boolean,
            block: ((String) -> String) -> T
        ): T = block { cmd ->
            transactionCommands += cmd
            if (failOnPrefix != null && cmd.startsWith(failOnPrefix)) {
                error("cparse error: syntax error near ';'")
            }
            rawReplies[cmd] ?: ""
        }

        override suspend fun executeRaw(command: String, markDirty: Boolean, includeStderr: Boolean): String {
            rawCommands += command
            return rawReplies[command] ?: ""
        }
    }
}

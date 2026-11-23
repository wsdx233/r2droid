package top.wsdx233.r2droid.feature.debug.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R2DebugCommandRunnerTest {
    private val runner = R2DebugCommandRunner()

    @Test
    fun parseJsonArray_stripsFridaPrefix() {
        val parsed: JSONArray = runner.parseJsonArray("r2frida> [{\"base\":\"0x1000\",\"size\":16}]\n")

        assertEquals(1, parsed.length())
        assertEquals("0x1000", parsed.getJSONObject(0).getString("base"))
    }

    @Test
    fun parseJsonObject_stripsLogsAroundPayload() {
        val parsed: JSONObject = runner.parseJsonObject("log before\n{\"pc\":4096,\"sp\":8192}\nlog after")

        assertEquals(4096L, parsed.getLong("pc"))
        assertEquals(8192L, parsed.getLong("sp"))
    }

    @Test
    fun parseFridaRegisterOutput_supportsR2FridaTextBlocks() {
        val parsed = runner.parseFridaRegisterOutput(
            """
            tid 123 waiting
              pc : 0x0000000100001234
              sp : 0x000000016fdff000
              x0 : 0x2a
            """.trimIndent()
        )

        assertEquals(0x100001234L, parsed.getLong("pc"))
        assertEquals(0x16fdff000L, parsed.getLong("sp"))
        assertEquals(0x2aL, parsed.getLong("x0"))
    }

    @Test
    fun parseLongFlexible_supportsHexDecimalAndCommas() {
        assertEquals(0x1234L, runner.parseLongFlexible("0x1234"))
        assertEquals(1234L, runner.parseLongFlexible("1234"))
        assertEquals(0xabL, runner.parseLongFlexible("0xab,"))
    }

    @Test
    fun parseAddress_usesFirstOutputLine() {
        assertEquals(0x401000L, runner.parseAddress("0x401000\nextra output"))
    }

    @Test
    fun registerRoleForName_detectsImportantRoles() {
        assertEquals(RegisterRole.PC, registerRoleForName("rip"))
        assertEquals(RegisterRole.SP, registerRoleForName("rsp"))
        assertEquals(RegisterRole.BP, registerRoleForName("fp"))
        assertEquals(RegisterRole.FLAGS, registerRoleForName("eflags"))
        assertEquals(RegisterRole.VECTOR, registerRoleForName("xmm0"))
        assertEquals(RegisterRole.GENERAL, registerRoleForName("rax"))
    }

    @Test
    fun parseBreakpoints_supportsCommonAddressFieldsAndDeduplicates() {
        val parsed = runner.parseBreakpoints(
            """
            [
              {"addr":"0x1000","enabled":true,"temporary":true,"cond":"eax==1","hits":3,"name":"bp0"},
              {"offset":8192,"enabled":false,"condition":"x0==0","hit":2,"id":"bp1"},
              {"vaddr":"0x1000","enabled":true}
            ]
            """.trimIndent()
        )

        assertEquals(2, parsed.size)
        assertEquals(0x1000L, parsed[0].address)
        assertTrue(parsed[0].temporary)
        assertEquals("eax==1", parsed[0].condition)
        assertEquals(3, parsed[0].hitCount)
        assertEquals("bp0", parsed[0].backendId)
        assertEquals(8192L, parsed[1].address)
        assertEquals(false, parsed[1].enabled)
        assertEquals("x0==0", parsed[1].condition)
        assertEquals(2, parsed[1].hitCount)
        assertEquals("bp1", parsed[1].backendId)
    }

    @Test
    fun validateCommandOutput_detectsCommonFailures() {
        val cases = listOf(
            "error: command failed",
            "unknown command: ds",
            "invalid command",
            "Cannot open file",
            "permission denied",
            "not in debug mode"
        )

        cases.forEach { output ->
            val result = runCatching { runner.validateCommandOutput("cmd", output) }
            assertTrue("Expected failure for: $output", result.isFailure)
        }
    }

    @Test
    fun validateCommandOutput_acceptsNormalOutput() {
        runner.validateCommandOutput("cmd", "0x1000\n")
    }

    @Test
    fun parseJsonArray_rejectsMissingPayload() {
        val result = runCatching { runner.parseJsonArray("not json") }
        assertTrue(result.isFailure)
    }

    @Test
    fun toFridaCommand_addsPrefixAndTrimsLeadingWhitespace() {
        assertEquals(":drj", runner.toFridaCommand("drj"))
        assertEquals(":dbj", runner.toFridaCommand("  :dbj"))
    }

    @Test
    fun isFridaCommand_detectsColonPrefixedR2FridaCommands() {
        assertTrue(runner.isFridaCommand(":drj"))
        assertTrue(runner.isFridaCommand("  :dmj"))
        assertTrue(!runner.isFridaCommand("drj"))
    }

    @Test
    fun parseLongFlexible_supportsUnsigned64BitHex() {
        assertEquals(0xffffffff80001000uL.toLong(), runner.parseLongFlexible("0xffffffff80001000"))
        assertEquals(0x8000000000000000uL.toLong(), runner.parseLongFlexible("0x8000000000000000"))
    }

    @Test
    fun parseBreakpoints_detectsHardwareWatchpointsAndExpr() {
        val parsed = runner.parseBreakpoints(
            """
            [
              {"addr":"0x401000","hw":true,"perm":"--x","enabled":true,"expr":"rax==0x10","size":1},
              {"addr":"0x602000","hw":true,"perm":"rw-","enabled":false,"size":4,"name":"wp0"},
              {"addr":"0x602010","hw":true,"perm":"-w-","enabled":true,"size":8},
              {"addr":"0x602020","hw":true,"perm":"r--","enabled":true,"size":4}
            ]
            """.trimIndent()
        )

        assertEquals(4, parsed.size)
        assertEquals(BreakpointKind.HARDWARE, parsed[0].kind)
        assertEquals("rax==0x10", parsed[0].condition)
        assertEquals(null, parsed[0].access)

        assertEquals(BreakpointKind.WATCHPOINT, parsed[1].kind)
        assertEquals(WatchpointAccess.READ_WRITE, parsed[1].access)
        assertEquals(false, parsed[1].enabled)
        assertEquals(4, parsed[1].size)
        assertEquals("wp0", parsed[1].backendId)

        assertEquals(BreakpointKind.WATCHPOINT, parsed[2].kind)
        assertEquals(WatchpointAccess.WRITE, parsed[2].access)
        assertEquals(8, parsed[2].size)

        assertEquals(BreakpointKind.WATCHPOINT, parsed[3].kind)
        assertEquals(WatchpointAccess.READ, parsed[3].access)
    }

    @Test
    fun parseFrames_parsesDbtjJsonOutput() {
        val frames = runner.parseFrames(
            """
            [
              {"addr":4198912,"func":"sym.parse_packet","sp":140737488347136,"bp":140737488347200,"size":64,"desc":"main.c:42"},
              {"pc":"0x401080","fname":"sym.main","rsp":"0x7fffffffe040","fp":"0x7fffffffe080","frame_size":32}
            ]
            """.trimIndent()
        )

        assertEquals(2, frames.size)
        assertEquals(0, frames[0].index)
        assertEquals(4198912L, frames[0].address)
        assertEquals("sym.parse_packet", frames[0].functionName)
        assertEquals(140737488347136L, frames[0].sp)
        assertEquals(140737488347200L, frames[0].bp)
        assertEquals(64, frames[0].size)
        assertEquals("main.c:42", frames[0].description)

        assertEquals(1, frames[1].index)
        assertEquals(0x401080L, frames[1].address)
        assertEquals("sym.main", frames[1].functionName)
        assertEquals(0x7fffffffe040L, frames[1].sp)
        assertEquals(0x7fffffffe080L, frames[1].bp)
        assertEquals(32, frames[1].size)
    }

    @Test
    fun parseThreads_parsesDptjJsonOutput() {
        val threads = runner.parseThreads(
            """
            [
              {"pid":1234,"status":"t","path":"/data/local/tmp/demo","current":true,"pc":"0x401200"},
              {"tid":1235,"state":"s","name":"worker-1","selected":false,"addr":4199040}
            ]
            """.trimIndent()
        )

        assertEquals(2, threads.size)
        assertEquals("1234", threads[0].id)
        assertEquals("/data/local/tmp/demo", threads[0].name)
        assertEquals("t", threads[0].status)
        assertTrue(threads[0].selected)
        assertEquals(0x401200L, threads[0].pc)

        assertEquals("1235", threads[1].id)
        assertEquals("worker-1", threads[1].name)
        assertEquals("s", threads[1].status)
        assertTrue(!threads[1].selected)
        assertEquals(4199040L, threads[1].pc)
    }

    @Test
    fun parseStackJsonAndText_extractsDerefAndSymbols() {
        val fromJson = runner.parseStackJson(
            """
            [
              {"addr":"0x7fffffffe000","value":"0x401280","flag":"sym.main+0x10","ref":"sym.main+0x10"},
              {"addr":"0x7fffffffe008","value":"0x7fffffffe120","ref":{"value":"0x604000","string":"/bin/sh"}}
            ]
            """.trimIndent()
        )
        assertEquals(2, fromJson.size)
        assertEquals("sym.main+0x10", fromJson[0].symbol)
        assertEquals("/bin/sh", fromJson[1].stringPreview)
        assertEquals(0x604000L, fromJson[1].derefValue)

        val fromText = runner.parseStackText(
            """
            0x7fffffffe000 0x0000000000401180   ........ @ r-x sym.main+0x12
            0x7fffffffe008 0x00007fffffffe100   ........ stack R W 0x7fffffffe120 --> ascii "/bin/sh"
            0x7fffffffe010 0x0000000000000000   ........ 0
            """.trimIndent()
        )
        assertEquals(3, fromText.size)
        assertEquals("sym.main+0x12", fromText[0].symbol)
        assertEquals("/bin/sh", fromText[1].stringPreview)
        assertEquals(0x7fffffffe120L, fromText[1].derefValue)
        assertEquals(null, fromText[2].reference)
    }

    @Test
    fun parseHexBytes_parsesValidAndRejectsInvalid() {
        val bytes = runner.parseHexBytes("0x90 90, c3-00")
        assertTrue(bytes != null)
        assertEquals(4, bytes!!.size)
        assertEquals(0x90.toByte(), bytes[0])
        assertEquals(0xC3.toByte(), bytes[2])

        assertEquals(null, runner.parseHexBytes("90 9"))
        assertEquals(null, runner.parseHexBytes("ZZ"))
        assertEquals(null, runner.parseHexBytes("   "))
    }
}

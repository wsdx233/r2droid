package top.wsdx233.r2droid.feature.annotations

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.radare.r2pipe.kotlin.LaunchSpec
import org.radare.r2pipe.kotlin.R2Pipe
import top.wsdx233.r2droid.util.R2CommandConsole
import java.io.IOException

/** Native JSON shapes and flagspace selection differ across r2 commands; don't mock these contracts. */
class AnnotationIntegrationTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun bookmarksAndRenamesPreserveOtherFlagsAndCommentsAtTheSameAddress() = runBlocking {
        val file = tmp.newFile("sample.bin").also { it.writeBytes(ByteArray(64)) }
        val binary = System.getenv("R2_TEST_BIN") ?: "r2"
        try { ProcessBuilder(binary, "-v").start().waitFor() } catch (e: IOException) {
            throw AssumptionViolatedException("Host radare2 is not installed", e)
        }
        val console = R2CommandConsole { _, _ -> }
        R2Pipe.open(LaunchSpec(listOf(binary, "-N", "-q0", "-n", file.absolutePath)), logger = console).use { pipe ->
            val session = object : AnnotationSession {
                override suspend fun <T> transaction(operation: String, markDirty: Boolean, block: ((String) -> String) -> T): T =
                    block { console.execute(pipe, it).checkedStdout() }
            }
            session.mutate("fs selected")
            session.mutate("CC note @ 0")
            session.setName(0, null, "first_name")
            session.setName(32, null, "elsewhere")
            assertTrue(session.toggleBookmark(0))
            assertTrue(runCatching { session.setName(0, "first_name", "elsewhere") }.isFailure)
            session.setName(0, "first_name", "new_name")
            assertTrue(runCatching { session.setName(0, "elsewhere", "stolen") }.isFailure)
            assertEquals(listOf("new_name"), session.namesAt(0))
            assertEquals(listOf("elsewhere"), session.namesAt(32))
            assertEquals(listOf(0L), session.listBookmarks().map { it.address })
            assertFalse(session.toggleBookmark(0))
            assertTrue(session.listBookmarks().isEmpty())
            assertTrue(AnnotationRow(0, "new_name") in session.listNames())
            session.applySemantic(AnnotationAction.QWORD, 0, "")
            assertEquals(8, JSONArray(session.query("pdj 1 @ 0")).getJSONObject(0).getInt("size"))
            session.applySemantic(AnnotationAction.CODE, 0, "")
            assertEquals(listOf(AnnotationRow(0, "note")), session.listComments())
            session.mutate("CC keep @ 1")
            session.applySemantic(AnnotationAction.UNDEFINE, 0, "")
            assertEquals(listOf(AnnotationRow(1, "keep")), session.listComments())
            assertEquals(listOf("new_name"), session.namesAt(0))
        }
        assertArrayEquals(ByteArray(64), file.readBytes())
    }
}

package top.wsdx233.r2droid.feature.project.data

import org.json.JSONArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.wsdx233.r2droid.testing.HostR2Pipe
import java.io.File
import java.io.IOException
import java.io.StringWriter

class SavedProjectScriptTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `copy never attaches to source regardless of existing project`() {
        val copy = ProjectSavePlan(copyBinary = true, ownsBinary = true, attachToSession = false)
        assertEquals(copy, planProjectSave(hasSourceProject = true, copyRequested = true, managed = false, canAdopt = false))
        assertEquals(copy, planProjectSave(hasSourceProject = false, copyRequested = true, managed = false, canAdopt = false))
    }

    @Test
    fun `managed Save As copies even when not requested but initial autosave can adopt opened copy`() {
        assertEquals(
            ProjectSavePlan(copyBinary = true, ownsBinary = true, attachToSession = false),
            planProjectSave(hasSourceProject = true, copyRequested = false, managed = true, canAdopt = true)
        )
        assertEquals(
            ProjectSavePlan(copyBinary = false, ownsBinary = true, attachToSession = true),
            planProjectSave(hasSourceProject = false, copyRequested = false, managed = true, canAdopt = true)
        )
        assertEquals(
            ProjectSavePlan(copyBinary = true, ownsBinary = true, attachToSession = false),
            planProjectSave(hasSourceProject = false, copyRequested = false, managed = true, canAdopt = false)
        )
        assertEquals(
            ProjectSavePlan(copyBinary = false, ownsBinary = false, attachToSession = true),
            planProjectSave(hasSourceProject = false, copyRequested = false, managed = false, canAdopt = false)
        )
        assertEquals(
            ProjectSavePlan(copyBinary = false, ownsBinary = false, attachToSession = false),
            planProjectSave(hasSourceProject = true, copyRequested = false, managed = false, canAdopt = false)
        )
    }

    @Test
    fun `legacy deletion retains binary and script paths shared by another project or open session`() {
        val projectA = temp.newFolder("A")
        val projectB = temp.newFolder("B")
        val binaryDir = File(projectA, "binary").apply { mkdir() }
        val a = File(binaryDir, "input").apply { writeText("A") }
        val b = a.copyTo(File(projectB, "input"))
        val script = File(projectA, "project.r2").apply { writeText("legacy shared replay") }

        assertFalse(canDeleteProjectDirectory(projectA, listOf(a.absolutePath)))
        assertFalse(canDeleteProjectDirectory(projectA, listOf("${projectA.path}/binary/../binary/input")))
        assertFalse(canDeleteProjectDirectory(projectA, listOf(script.absolutePath)))
        assertTrue(canDeleteProjectDirectory(projectA, listOf(b.absolutePath)))
        assertTrue(projectA.deleteRecursively())
        assertEquals("A", b.readText())
    }

    @Test
    fun `non-cache PS replay cannot reopen original and binds maps to the restored descriptor`() {
        val input = """
            # r2 rdb project file
            # eval
            'e asm.bits = 64
            'e cmd.depth = 10
            'e cmd.repeat = false
            'e cmd.seek = o /project/A
            pushd /project
            o "a path with spaces" 0x00400000 rw-
            popd
            onnu /project/another-input r--
            om ${'$'}d 0x00400000 0x10 0x0 rw-
            omu 7 0x00400000 0x10 0x0 rw-
            omu 9 0x00500000 0x10 0x0 r--
            o=7
            # functions
            af+ 0x00400000 entry0
            CCu eHggaXMgbm90IGEgY29tbWFuZA== @ 0x00400000
            # seek
            s 0x00400000
        """.trimIndent()
        val expected = """
            # r2 rdb project file
            # r2droid project replay: uses the already-open binary
            # eval
            'e asm.bits = 64
            'e cmd.depth = 10
            'e cmd.repeat = false
            omu . 0x00400000 0x10 0x0 rw-
            # functions
            af+ 0x00400000 entry0
            CCu eHggaXMgbm90IGEgY29tbWFuZA== @ 0x00400000
            # seek
            s 0x00400000
        """.trimIndent() + "\n"

        // Both a fresh save and migration of old PS output produce the same safe script.
        assertEquals(expected, replay(input, sourceFd = 7))
        assertEquals(expected, replay(input))
        assertEquals(expected, replay(expected))
    }

    @Test
    fun `save preserves analysis but does not replay cache on another binary`() {
        val input = """
            # r2 rdb project file
            # eval
            'e io.cache = true
            'e io.cache.read = true
            'e io.cache.write = true
            omu 9 0x40 0x10 0x0 r--
            # functions
            af+ 0x40 entry0
            wx 9090 @ 0x40 # replaces: cccc
            # seek
            s 0x40
        """.trimIndent()
        val result = replay(input)
        assertFalse(result.contains("io.cache"))
        assertFalse(result.contains("wx "))
        assertTrue(result.contains("af+ 0x40 entry0"))
        assertTrue(result.contains("omu . 0x40 0x10 0x0 r--"))
    }

    @Test(expected = IllegalStateException::class)
    fun `legacy replay cannot switch descriptors from its analysis body`() {
        replay("# r2 rdb project file\n# functions\no /project/A\n")
    }

    @Test
    fun `native non-cache replay restores analysis and writes only to the saved copy`() {
        val aDirectory = temp.newFolder("original")
        val a = File(aDirectory, "input A").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val bDirectory = temp.newFolder("copy")
        val b = a.copyTo(File(bDirectory, "input B"))
        val script = File(bDirectory, "project.r2")
        val source = try {
            HostR2Pipe.open(a.absolutePath, extraArgs = listOf("-N", "-n", "-w"))
        } catch (e: IOException) {
            throw AssumptionViolatedException("Host radare2 unavailable", e)
        }
        try {
            source.cmd("f saveas.analysis 1 @ 0x2")
            source.cmd("pf.Pair bb left right")
            source.cmd("Cf 2 Pair @ 0")
            val raw = source.cmd("PS /dev/stdout")
            script.writeText(replay(raw, formats = source.cmd("pf.")))
            assertTrue(source.cmd("oj").contains(a.name))
            source.cmd("wx 59 @ 0x1")
            assertArrayEquals(byteArrayOf(1, 89, 3, 4), a.readBytes())
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), b.readBytes())
        } finally {
            source.quit()
        }

        fun openCopyAndWrite() {
            val copy = HostR2Pipe.open(b.absolutePath, extraArgs = listOf("-N", "-n", "-w"))
            try {
                copy.cmd(". ${script.absolutePath}")
                val descriptors = JSONArray(copy.cmd("oj"))
                val uris = (0 until descriptors.length()).map { descriptors.getJSONObject(it).getString("uri") }
                assertTrue(uris.contains(b.absolutePath))
                assertFalse(uris.contains(a.absolutePath))
                assertTrue(copy.cmd("fj").contains("saveas.analysis"))
                assertEquals("2", copy.cmd("pfs.Pair").trim())
                assertTrue(copy.cmd("pd 1 @ 0").contains("left"))
                copy.cmd("wx 5a @ 0x0")
            } finally {
                copy.quit()
            }
        }

        openCopyAndWrite()
        assertArrayEquals(byteArrayOf(1, 89, 3, 4), a.readBytes())
        assertArrayEquals(byteArrayOf(90, 2, 3, 4), b.readBytes())

        assertTrue(canDeleteProjectDirectory(aDirectory, listOf(b.absolutePath, script.absolutePath)))
        assertTrue(aDirectory.deleteRecursively())
        openCopyAndWrite()
        assertArrayEquals(byteArrayOf(90, 2, 3, 4), b.readBytes())
    }

    private fun replay(input: String, sourceFd: Int? = null, formats: String = ""): String = StringWriter().also {
        writeProjectReplay(input.reader().buffered(), it, sourceFd, formats)
    }.toString()

    @Test
    fun `cached project serializes analysis but not cache commands or old descriptors`() {
        val script = """
            # r2 rdb project file
            # eval
            'e asm.bits = 64
            'e io.cache = true
            'e io.cache.read = true
            e io.cache.write=true
            o "/project/old target" 0x00400000 rw-
            onnu /project/old-target r--
            o=3
            omu 3 0x00400000 0x00000010 0x00000000 r-x
            # functions
            af+ 0x00400000 entry0
            CCu eHggaXMgbm90IGEgY29tbWFuZA== @ 0x00400000
            wx 9090 @ 0x00400000 # replaces: cccc
            # seek
            s 0x00400000
        """.trimIndent()
        val expected = """
            # r2 rdb project file
            # r2droid project replay: uses the already-open binary
            # eval
            'e asm.bits = 64
            omu . 0x00400000 0x00000010 0x00000000 r-x
            # functions
            af+ 0x00400000 entry0
            CCu eHggaXMgbm90IGEgY29tbWFuZA== @ 0x00400000
            # seek
            s 0x00400000
        """.trimIndent() + "\n"
        assertEquals(expected, replay(script, sourceFd = 3))
    }
}

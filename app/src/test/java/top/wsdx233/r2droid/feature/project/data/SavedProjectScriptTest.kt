package top.wsdx233.r2droid.feature.project.data

import org.json.JSONArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test
    fun `temporary replay leaves legacy script untouched and reports filtered patches`() {
        val projectDir = temp.newFolder("legacy-project")
        val tempDir = temp.newFolder("restore-temp")
        val originalText = listOf(
            "# r2 rdb project file",
            "# eval",
            "'e io.cache = true",
            "'e io.cache.read = true",
            "'e io.cache.write = true",
            "o \"/old/target\" 0x00400000 rw-",
            "o=3",
            "omu 3 0x00400000 0x10 0x0 r-x",
            "# functions",
            "af+ 0x00400000 entry0",
            "wx 9090 @ 0x00400000 # replaces: cccc",
            "# seek",
            "s 0x00400000"
        ).joinToString("\r\n") + "\r\n"
        val script = File(projectDir, "project.r2").apply { writeBytes(originalText.toByteArray(Charsets.UTF_8)) }
        val originalBytes = script.readBytes()

        val prepared = createTemporaryReplayScript(script, tempDir)
        try {
            assertNotEquals(script.canonicalFile, prepared.file.canonicalFile)
            assertEquals(tempDir.canonicalFile, prepared.file.parentFile?.canonicalFile)
            assertTrue(prepared.report.isLegacyScript)
            assertTrue(prepared.report.filteredLegacyPatches)
            assertTrue(prepared.report.filteredLegacyCacheOptions)
            val replayedText = prepared.file.readText()
            assertTrue(replayedText.contains("af+ 0x00400000 entry0"))
            assertTrue(replayedText.contains("omu . 0x00400000 0x10 0x0 r-x"))
            assertFalse(replayedText.contains("wx 9090"))
            assertFalse(replayedText.contains("io.cache"))
        } finally {
            assertTrue(prepared.file.delete())
        }

        // Original archive script must remain byte-for-byte intact and resolvable for export.
        assertArrayEquals(originalBytes, script.readBytes())
        assertFalse(legacyScriptBackupFile(script).exists())
        assertEquals(script.canonicalFile, resolveOriginalLegacyScript(script)?.canonicalFile)
    }

    @Test
    fun `normalized script is not treated as legacy and does not trigger legacy backup`() {
        val projectDir = temp.newFolder("modern-project")
        val tempDir = temp.newFolder("modern-temp")
        val normalized = replay(
            """
            # r2 rdb project file
            # eval
            'e asm.bits = 64
            # functions
            af+ 0x40 entry0
            # seek
            s 0x40
            """.trimIndent()
        )
        val script = File(projectDir, "project.r2").apply { writeText(normalized) }

        val prepared = createTemporaryReplayScript(script, tempDir)
        try {
            assertFalse(prepared.report.isLegacyScript)
            assertFalse(prepared.report.filteredLegacyPatches)
            assertFalse(prepared.report.filteredLegacyCacheOptions)
        } finally {
            prepared.file.delete()
        }

        assertNull(resolveOriginalLegacyScript(script))
        val backup = updateProjectScriptWithLegacyBackup(script) { it.writeText(normalized) }
        assertNull(backup)
        assertFalse(legacyScriptBackupFile(script).exists())
    }

    @Test
    fun `first explicit update backs up legacy script byte-for-byte and preserves it across later updates`() {
        val projectDir = temp.newFolder("legacy-update")
        val originalBytes = (
            "# r2 rdb project file\r\n" +
                "# eval\r\n" +
                "'e io.cache = true\r\n" +
                "# functions\r\n" +
                "af+ 0x40 entry0\r\n" +
                "wx deadbeef @ 0x40 # replaces: 01020304\r\n" +
                "# seek\r\n" +
                "s 0x40\r\n"
            ).toByteArray(Charsets.UTF_8)
        val script = File(projectDir, "project.r2").apply { writeBytes(originalBytes) }
        val updatedV1 = replay("# r2 rdb project file\n# functions\naf+ 0x40 entry_v1\n")
        val updatedV2 = replay("# r2 rdb project file\n# functions\naf+ 0x40 entry_v2\n")

        val backup = updateProjectScriptWithLegacyBackup(script) { it.writeText(updatedV1) }
        assertNotNull(backup)
        assertEquals(legacyScriptBackupFile(script).canonicalFile, backup!!.canonicalFile)
        assertArrayEquals(originalBytes, backup.readBytes())
        assertEquals(updatedV1, script.readText())
        assertEquals(backup.canonicalFile, resolveOriginalLegacyScript(script)?.canonicalFile)

        // Subsequent explicit update must never overwrite the original legacy backup.
        val secondBackup = updateProjectScriptWithLegacyBackup(script) { it.writeText(updatedV2) }
        assertNull(secondBackup)
        assertEquals(updatedV2, script.readText())
        assertArrayEquals(originalBytes, backup.readBytes())
        assertEquals(backup.canonicalFile, resolveOriginalLegacyScript(script)?.canonicalFile)
    }

    @Test
    fun `legacy backup failure aborts overwrite and keeps original script untouched`() {
        val projectDir = temp.newFolder("legacy-backup-fail")
        val originalBytes = (
            "# r2 rdb project file\n" +
                "# functions\n" +
                "wx 9090 @ 0x40\n"
            ).toByteArray(Charsets.UTF_8)
        val script = File(projectDir, "project.r2").apply { writeBytes(originalBytes) }
        // Block the backup path with a directory so backup verification fails.
        assertTrue(legacyScriptBackupFile(script).mkdir())

        var overwriteCalled = false
        assertThrows(IllegalStateException::class.java) {
            updateProjectScriptWithLegacyBackup(script) {
                overwriteCalled = true
                it.writeText("should never overwrite")
            }
        }
        assertFalse(overwriteCalled)
        assertArrayEquals(originalBytes, script.readBytes())
    }

    @Test
    fun `native legacy restore replays analysis while preserving binary and original script across first update`() {
        val projectDir = temp.newFolder("legacy-native")
        val tempDir = temp.newFolder("legacy-native-temp")
        val binary = File(projectDir, "sample.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val script = File(projectDir, "project.r2")

        val v031Session = try {
            HostR2Pipe.open(binary.absolutePath, extraArgs = listOf("-N", "-n", "-e", "io.cache=true"))
        } catch (e: IOException) {
            throw AssumptionViolatedException("Host radare2 unavailable", e)
        }
        try {
            v031Session.cmd("f legacy.analysis 1 @ 0x2")
            v031Session.cmd("wx 59 @ 0x1")
            // Simulate v0.3.1 raw PS save: pending cached byte lives only in project.r2.
            val rawPs = v031Session.cmd("PS /dev/stdout")
            script.writeText(rawPs)
        } finally {
            v031Session.quit()
        }

        val originalScriptBytes = script.readBytes()
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), binary.readBytes())
        assertTrue(String(originalScriptBytes, Charsets.UTF_8).contains("wx "))

        val restored = HostR2Pipe.open(binary.absolutePath, extraArgs = listOf("-N", "-n"))
        try {
            val prepared = createTemporaryReplayScript(script, tempDir)
            try {
                assertTrue(prepared.report.isLegacyScript)
                assertTrue(prepared.report.filteredLegacyPatches)
                restored.cmd(". ${prepared.file.absolutePath}")
            } finally {
                assertTrue(prepared.file.delete())
            }
            assertTrue(restored.cmd("fj").contains("legacy.analysis"))
            assertEquals("01020304", restored.cmd("p8 4 @ 0").trim())
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), binary.readBytes())
            assertArrayEquals(originalScriptBytes, script.readBytes())

            // Simulate first explicit project update after restore.
            val freshReplay = replay(restored.cmd("PS /dev/stdout"))
            val backup = updateProjectScriptWithLegacyBackup(script) { it.writeText(freshReplay) }
            assertNotNull(backup)
            assertArrayEquals(originalScriptBytes, resolveOriginalLegacyScript(script)?.readBytes())
            assertFalse(script.readText().contains("wx "))
        } finally {
            restored.quit()
        }
    }
}

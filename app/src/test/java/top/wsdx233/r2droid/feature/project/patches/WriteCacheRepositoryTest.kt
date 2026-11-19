package top.wsdx233.r2droid.feature.project.patches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.radare.r2pipe.kotlin.LaunchSpec
import org.radare.r2pipe.kotlin.R2Pipe
import java.io.File
import java.io.IOException

class WriteCacheRepositoryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun parsesBundledFormatWithoutSwappingOldAndNewBytes() {
        val patch = WriteCacheFormat.parseEntries(
            """{"layer":[{"idx":0,"addr":18446744073709551614,"size":2,"after":"10aA","before":"FF00","written":true}]}"""
        ).single()
        assertEquals(ULong.MAX_VALUE - 1u, patch.address)
        assertEquals(ULong.MAX_VALUE, patch.lastAddress)
        assertEquals("10aa", patch.original)
        assertEquals("ff00", patch.replacement)
        assertTrue(patch.written)
        assertEquals(listOf(2, 0), WriteCacheFormat.parseLayers("- 0 cache layer with 2 patches\n* 1 cache layer with 0 patches"))
    }

    @Test fun reconcilesJsonWithCanonicalNativeByteOrder() {
        val (_, entries) = WriteCacheFormat.read { cmd -> when (cmd) {
            "wcl" -> "* 0 cache layer with 1 patches"
            "wcj" -> """{"layer":[{"idx":0,"addr":1,"size":1,"before":"12","after":"ab","written":false}]}"""
            "wc" -> "idx=0 addr=0x00000001 size=1 12 -> ab (not written)"
            else -> error(cmd)
        } }
        assertEquals(CachePatch(0, 1u, "12", "ab", false), entries.single())
    }

    @Test fun doesNotInterpretBrokenWcajOrInvalidRowsAsEmpty() {
        for (raw in listOf("", "[]", "{\"layers\":[[]]}", """{"layer":[{"idx":0,"addr":1,"size":2,"after":"00","before":"ff","written":false}]}""")) {
            assertThrows(raw, IllegalStateException::class.java) { WriteCacheFormat.parseEntries(raw) }
        }
        assertThrows(IllegalStateException::class.java) {
            WriteCacheFormat.read { if (it == "wcl") "* 0 cache layer with 1 patches" else """{"layer":[]}""" }
        }
    }

    @Test fun tracksEffectivePendingNotShadowedHistoryAndDisablesOverlapUndo() {
        val first = CachePatch(0, 0u, "0102", "aabb", false)
        val second = CachePatch(1, 0u, "aabb", "ccdd", true)
        val independent = CachePatch(2, 3u, "03", "ee", false)
        val snapshot = PatchSnapshot(listOf(first, second, independent), listOf(3), 0, emptyList())
        assertEquals(setOf(2), snapshot.pendingIndices)
        assertFalse(snapshot.canUndoEntry(0))
        assertFalse(snapshot.canUndoEntry(1))
        assertTrue(snapshot.canUndoEntry(2))
        val committed = snapshot.copy(entries = snapshot.entries.dropLast(1), layerSizes = listOf(2))
        assertFalse(committed.canCommit)
        assertTrue(committed.canReset)
        val layered = snapshot.copy(layerSizes = listOf(1, 3))
        assertFalse(layered.canCommit)
        assertFalse(layered.canReset)
        assertFalse(layered.canUndo)
    }

    @Test fun commitsOverlappingPatchesFromReadonlyDescriptorAndRestoresPermissions() = native { source, command ->
        command("wx aabbcc @ 1")
        command("wx ddee @ 2")
        val pending = nativeSnapshot(command)
        val permissions = command("om,:json")
        val fd = command("oqq")
        command("wci") // same cache and unchanged file when the descriptor is read-only
        assertEquals(pending, nativeSnapshot(command))
        assertEquals("102030405060", source.hex())
        commitCache(source.path, pending, command)
        assertEquals("10aaddee5060", source.hex())
        assertEquals(fd, command("oqq"))
        assertEquals(permissions, command("om,:json"))
        assertFalse(loadPatchTarget(source.path, emptyList(), command).writable)
        val committed = nativeSnapshot(command)
        assertFalse(committed.canCommit)
        assertFalse(committed.canUndo)
        assertEquals(pending.entries.size, committed.entries.size)
        command("wx fa @ 2")
        commitCache(source.path, nativeSnapshot(command), command)
        assertEquals("10aafaee5060", source.hex())
    }

    @Test fun individualUndoNeverWritesOriginalEvenWhenDescriptorIsWritable() = native { source, command ->
        command("oo+")
        command("wx aabb @ 1")
        command("wx ee @ 3") // immediately adjacent to the first range
        undoCacheEntry(source.path, nativeSnapshot(command), 0, command)
        assertEquals("102030ee5060", command("p8 6 @ 0").trim())
        assertEquals("102030405060", source.hex())
        val remaining = nativeSnapshot(command).entries.single()
        assertEquals(0, remaining.index)
        assertEquals(3uL, remaining.address)
    }

    @Test fun latestRepeatedRangeUndoReappliesThePreviousOverlayWithoutWritingOriginal() = native { source, command ->
        command("wx e1 @ 0")
        command("wx aa @ 1")
        command("wx bb @ 1")
        command("wx cc @ 1")
        val initial = nativeSnapshot(command)
        val commands = mutableListOf<String>()
        undoCacheEntry(source.path, initial, initial.entries.lastIndex) {
            commands += it
            command(it)
        }
        assertTrue("wcu" in commands)
        assertEquals(initial.entries.dropLast(1), nativeSnapshot(command).entries)
        assertEquals("e1bb30405060", command("p8 6 @ 0").trim())
        assertEquals("102030405060", source.hex())
    }

    @Test fun undoOfEditOverWrittenHistoryKeepsCommittedBytes() = native { source, command ->
        command("wx aa @ 1")
        commitCache(source.path, nativeSnapshot(command), command)
        val committed = nativeSnapshot(command)
        command("wx bb @ 1")
        undoCacheEntry(source.path, nativeSnapshot(command), 1, command)
        assertEquals(committed, nativeSnapshot(command))
        assertEquals("10aa30405060", source.hex())
        assertEquals("10aa30405060", command("p8 6 @ 0").trim())
    }

    @Test fun resetDropsPendingOverlayButDoesNotRevertFileWrites() = native { source, command ->
        command("wx aa @ 1")
        commitCache(source.path, nativeSnapshot(command), command)
        command("wx bb @ 2")
        command("wcr")
        assertEquals(listOf(0) to emptyList<CachePatch>(), WriteCacheFormat.read(command))
        assertEquals("10aa30405060", source.hex())
        assertEquals("10aa30405060", command("p8 6 @ 0").trim())
        assertEquals("true", command("e io.cache").trim())
    }

    @Test fun refusesChangedBaselineWithoutAttemptingWrites() = native { source, command ->
        command("wx ab @ 1")
        val pending = nativeSnapshot(command)
        source.writeBytes(byteArrayOf(0x10, 0x21, 0x30, 0x40, 0x50, 0x60))
        val invoked = mutableListOf<String>()
        val failure = assertThrows(IllegalStateException::class.java) {
            commitCache(source.path, pending) {
                invoked += it
                command(it)
            }
        }
        assertTrue(failure.message.orEmpty().contains("baseline"))
        assertFalse("oo+" in invoked || "wci" in invoked)
        assertEquals(pending, nativeSnapshot(command))
        assertEquals("102130405060", source.hex())
    }

    @Test fun silentCommitFailureIsNotReportedAsSuccess() = native { source, command ->
        command("wx aabb @ 1")
        val before = nativeSnapshot(command)
        val thrown = assertThrows(IllegalStateException::class.java) {
            commitCache(source.path, before) { if (it == "wci") "" else command(it) }
        }
        assertTrue(thrown.message.orEmpty().contains("could not be verified"))
        assertEquals(before, nativeSnapshot(command))
        assertEquals("102030405060", source.hex())
        assertFalse(loadPatchTarget(source.path, emptyList(), command).writable)
    }

    @Test fun rejectsPatchMappedToAnotherDescriptorBeforeMakingAnyWrites() = native { source, command ->
        val other = temp.newFile("other.bin").apply { writeBytes(byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50, 0x60)) }
        val originalFd = command("oqq").trim()
        command("on ${other.path} 0x100")
        command("o=$originalFd")
        command("wx ab @ 0x101")
        val pending = nativeSnapshot(command)
        assertTrue(pending.canCommit)
        assertThrows(IllegalStateException::class.java) { commitCache(source.path, pending, command) }
        assertEquals("102030405060", source.hex())
        assertEquals("102030405060", other.hex())
        assertEquals(pending, nativeSnapshot(command))
    }

    private fun nativeSnapshot(command: (String) -> String): PatchSnapshot {
        val (layers, entries) = WriteCacheFormat.read(command)
        return PatchSnapshot(entries, layers, 0, emptyList())
    }

    private fun native(test: (File, (String) -> String) -> Unit) {
        val binary = System.getenv("R2_TEST_BIN") ?: "r2"
        val version = try {
            ProcessBuilder(binary, "-v").redirectErrorStream(true).start().inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw AssumptionViolatedException("Host radare2 not installed", e)
        }
        if (!Regex("radare2 6\\.2\\.[46]\\b").containsMatchIn(version)) {
            throw AssumptionViolatedException("Native cache tests require radare2 6.2.4 or 6.2.6")
        }
        val source = temp.newFile("original.bin").apply { writeBytes(byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50, 0x60)) }
        R2Pipe.open(LaunchSpec(listOf(binary, "-N", "-q0", "-n", source.path))).use { pipe ->
            pipe.cmd("e io.cache=true")
            test(source, pipe::cmd)
        }
    }

    private fun File.hex(): String = readBytes().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

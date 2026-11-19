package top.wsdx233.r2droid.feature.project.patches

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PatchExportTest {
    @get:Rule val folder = TemporaryFolder()

    private fun patch(addr: ULong, hex: String) = CachePatch(0, addr, "00".repeat(hex.length / 2), hex, false)

    private fun layout(source: File, entries: List<CachePatch>, maps: String = "[]", va: Boolean = true): PatchTarget =
        loadPatchTarget(source.path, entries) { cmd -> when (cmd) {
            "oj" -> """[{"fd":3,"raised":true,"writable":false,"size":${source.length()},"uri":"${source.path}"}]"""
            "omj" -> maps
            "e io.va" -> va.toString()
            else -> error("Unexpected command: $cmd")
        } }

    @Test fun exportUsesLiveBytesAcrossRebasedMapsAndIncludesLastFileByte() {
        val file = folder.newFile().apply { writeBytes(ByteArray(8) { it.toByte() }) }
        val maps = """[{"map":1,"fd":3,"from":4096,"to":4099,"delta":4,"perm":"r-x"},
            {"map":2,"fd":3,"from":4100,"to":4103,"delta":0,"perm":"r--"}]"""
        val target = layout(file, listOf(patch(4098u, "aaaaaaaa"), patch(4099u, "bbbb")), maps)
        val copy = ByteArrayOutputStream()
        file.inputStream().use { input ->
            streamPatchedCopy(input, copy, target.ranges) { when (it) {
                "p8 2 @ 0x1004" -> "ccdd"
                "p8 2 @ 0x1002" -> "eeff"
                else -> error(it)
            } }
        }
        assertArrayEquals(byteArrayOf(0xcc.toByte(), 0xdd.toByte(), 2, 3, 4, 5, 0xee.toByte(), 0xff.toByte()), copy.toByteArray())
        assertArrayEquals(ByteArray(8) { it.toByte() }, file.readBytes())
    }

    @Test fun neverTreatOtherDescriptorsOrMemoryOnlyAddressesAsFileOffsets() {
        val source = folder.newFile().apply { writeBytes(ByteArray(8)) }
        val maps = """[{"map":1,"fd":3,"from":0,"to":3,"delta":0,"perm":"r--"},
            {"map":2,"fd":4,"from":4,"to":7,"delta":0,"perm":"rw-"}]"""
        assertThrows(IllegalStateException::class.java) { layout(source, listOf(patch(3u, "aabb")), maps) }
        assertThrows(IllegalStateException::class.java) { layout(source, listOf(patch(8u, "aa")), maps) }
        assertThrows(IllegalStateException::class.java) { layout(source, listOf(patch(7u, "aabb")), va = false) }
        assertEquals(7L, layout(source, listOf(patch(7u, "aa")), va = false).ranges.single().offset)
    }
}

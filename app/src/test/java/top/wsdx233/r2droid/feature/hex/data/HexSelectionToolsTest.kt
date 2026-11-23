package top.wsdx233.r2droid.feature.hex.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexSelectionToolsTest {

    @Test
    fun selectionStateNormalizesBoundsAndHighUnsignedAddresses() {
        val reversed = HexSelectionState(active = true, startAddr = 0x100F, endAddr = 0x1000)
        assertEquals(0x1000L, reversed.rangeStart)
        assertEquals(0x100FL, reversed.rangeEnd)
        assertEquals(16L, reversed.byteCount)
        assertTrue(reversed.contains(0x1000L))
        assertTrue(reversed.contains(0x1008L))
        assertTrue(reversed.contains(0x100FL))
        assertFalse(reversed.contains(0x1010L))

        val highA = 0xFFFF_8000_0000_0010UL.toLong()
        val highB = 0xFFFF_8000_0000_0000UL.toLong()
        val highSel = HexSelectionState(active = true, startAddr = highA, endAddr = highB)
        assertEquals(highB, highSel.rangeStart)
        assertEquals(highA, highSel.rangeEnd)
        assertEquals(17L, highSel.byteCount)
        assertTrue(highSel.contains(0xFFFF_8000_0000_0005UL.toLong()))

        val inactive = HexSelectionState()
        assertEquals(0L, inactive.byteCount)
        assertFalse(inactive.contains(0L))
    }

    @Test
    fun formatsHexCArrayAndPythonBytes() {
        val sample = byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x00, 0xFF.toByte())
        assertEquals("7F 45 4C 46 00 FF", formatAsHexSpaced(sample))
        assertEquals("7F454C4600FF", formatAsHexCompact(sample))
        assertEquals(
            "unsigned char data[6] = {\n    0x7F, 0x45, 0x4C, 0x46, 0x00, 0xFF\n};",
            formatAsCArray(sample)
        )
        assertEquals(
            "b\"\\x7f\\x45\\x4c\\x46\\x00\\xff\"",
            formatAsPythonBytes(sample)
        )

        val twenty = ByteArray(20) { it.toByte() }
        val pyMulti = formatAsPythonBytes(twenty, bytesPerLine = 16)
        assertTrue(pyMulti.startsWith("(\n    b\"\\x00\\x01"))
        assertTrue(pyMulti.endsWith("\"\n)"))
        assertEquals("unsigned char data[0] = {};", formatAsCArray(ByteArray(0)))
        assertEquals("b\"\"", formatAsPythonBytes(ByteArray(0)))
    }

    @Test
    fun computesStandardCrc32Md5AndSha256() {
        val vector = "123456789".toByteArray(Charsets.US_ASCII)
        val hashes = computeHashes(vector)
        assertEquals("cbf43926", hashes.crc32)
        assertEquals("25f9e794323b453885f5181f1b624d0b", hashes.md5)
        assertEquals("15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225", hashes.sha256)
        assertEquals(9L, hashes.byteCount)

        // Streaming accumulator matches single-shot result.
        val acc = HexHashAccumulator()
        acc.update(vector, 0, 4)
        acc.update(vector, 4, 5)
        assertEquals(hashes, acc.finish())
    }

    @Test
    fun parsesXorKeysAndDecodesSingleAndMultiByteStreams() {
        assertArrayEquals(byteArrayOf(0x5A), parseXorKey("0x5A", HexXorKeyMode.HEX))
        assertArrayEquals(
            byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            parseXorKey("DE AD:BE-EF", HexXorKeyMode.HEX)
        )
        assertNull(parseXorKey("ABC", HexXorKeyMode.HEX))
        assertNull(parseXorKey("ZZ", HexXorKeyMode.HEX))
        assertArrayEquals("key".toByteArray(), parseXorKey("key", HexXorKeyMode.ASCII))

        val plain = "Hello, r2droid!".toByteArray(Charsets.UTF_8)
        val key = byteArrayOf(0x5A, 0x33)
        val encrypted = xorBytes(plain, key)
        val preview = xorDecode(encrypted, key)
        assertArrayEquals(plain, preview.bytes)
        assertEquals("Hello, r2droid!", preview.textPreview)
        assertFalse(preview.truncated)
    }

    @Test
    fun decodesAndEncodesBase64IncludingUrlSafeUnpaddedAndTrailingNul() {
        val raw = "Hello, World!".toByteArray(Charsets.UTF_8)
        val encoded = encodeBase64Bytes(raw)
        assertEquals("SGVsbG8sIFdvcmxkIQ==", encoded)

        val decoded = decodeBase64Bytes("$encoded\n\u0000\u0000".toByteArray(Charsets.US_ASCII))
        assertTrue(decoded is HexBase64DecodeResult.Success)
        assertEquals("Hello, World!", (decoded as HexBase64DecodeResult.Success).preview.textPreview)
        assertArrayEquals(raw, decoded.preview.bytes)

        // Unpadded URL-safe Base64 for bytes [0xFB, 0xFF, 0xBF] -> "-_-_"
        val urlSafe = decodeBase64Bytes("-_-_".toByteArray(Charsets.US_ASCII))
        assertTrue(urlSafe is HexBase64DecodeResult.Success)
        assertArrayEquals(
            byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte()),
            (urlSafe as HexBase64DecodeResult.Success).preview.bytes
        )

        // Rejects non-Base64 binary bytes
        val invalid = decodeBase64Bytes(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))
        assertTrue(invalid is HexBase64DecodeResult.Invalid)
        val invalidNonAscii = decodeBase64Bytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte(), 0xFC.toByte()))
        assertTrue(invalidNonAscii is HexBase64DecodeResult.Invalid)
    }

    @Test
    fun parsesPositiveByteLengths() {
        assertEquals(64L, parsePositiveByteLength("64"))
        assertEquals(256L, parsePositiveByteLength("0x100"))
        assertEquals(256L, parsePositiveByteLength(" 0X100 "))
        assertNull(parsePositiveByteLength("0"))
        assertNull(parsePositiveByteLength("-16"))
        assertNull(parsePositiveByteLength("abc"))
    }
}

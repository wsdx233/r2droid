package top.wsdx233.r2droid.feature.hex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexDataInspectorTest {

    @Test
    fun inspectsIntegersFloatsPointersInLittleAndBigEndian() {
        // 8 bytes: 01 02 03 04 05 06 07 80
        val bytes = byteArrayOf(
            0x01, 0x02, 0x03, 0x04,
            0x05, 0x06, 0x07, 0x80.toByte()
        )

        val le = inspectHexBytes(0x401000L, bytes, HexEndianness.LITTLE, pointerBits = 64)
        assertEquals("0x01", le.hexByte)
        assertEquals("00000001", le.binaryByte)
        assertEquals("1", le.int8)
        assertEquals("1", le.uint8)
        assertEquals("513", le.int16) // 0x0201
        assertEquals("513", le.uint16)
        assertEquals("67305985", le.int32) // 0x04030201
        assertEquals("67305985", le.uint32)
        assertEquals(0x8007060504030201UL.toLong().toString(), le.int64)
        assertEquals(0x8007060504030201UL.toString(), le.uint64)
        assertEquals("0x8007060504030201", le.pointerDisplay)
        assertEquals(0x8007060504030201UL.toLong(), le.pointerValue)

        val be = inspectHexBytes(0x401000L, bytes, HexEndianness.BIG, pointerBits = 32)
        assertEquals("258", be.int16) // 0x0102
        assertEquals("258", be.uint16)
        assertEquals("16909060", be.int32) // 0x01020304
        assertEquals("16909060", be.uint32)
        assertEquals("72623859790382976", be.int64) // 0x0102030405060780
        assertEquals("72623859790382976", be.uint64)
        assertEquals("0x01020304", be.pointerDisplay)
        assertEquals(0x01020304L, be.pointerValue)
    }

    @Test
    fun inspectsSignedNegativeAndUnsignedBoundaries() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val snap = inspectHexBytes(0L, bytes, HexEndianness.LITTLE, pointerBits = 32)
        assertEquals("-1", snap.int8)
        assertEquals("255", snap.uint8)
        assertEquals("-1", snap.int16)
        assertEquals("65535", snap.uint16)
        assertEquals("-1", snap.int32)
        assertEquals("4294967295", snap.uint32)
        assertEquals("NaN", snap.float32)
        assertNull(snap.int64)
        assertNull(snap.uint64)
        assertNull(snap.float64)
        assertEquals("0xFFFFFFFF", snap.pointerDisplay)
    }

    @Test
    fun inspectsFloatAndDoubleInLittleAndBigEndian() {
        // IEEE 754 float32 1.5f = 0x3FC00000
        val f32Le = byteArrayOf(0x00, 0x00, 0xC0.toByte(), 0x3F)
        assertEquals("1.5", inspectHexBytes(0L, f32Le, HexEndianness.LITTLE).float32)
        val f32Be = byteArrayOf(0x3F, 0xC0.toByte(), 0x00, 0x00)
        assertEquals("1.5", inspectHexBytes(0L, f32Be, HexEndianness.BIG).float32)

        // IEEE 754 float64 -2.25 = 0xC002000000000000
        val f64Be = byteArrayOf(0xC0.toByte(), 0x02, 0, 0, 0, 0, 0, 0)
        assertEquals("-2.25", inspectHexBytes(0L, f64Be, HexEndianness.BIG).float64)
        assertEquals("-2.25", inspectHexBytes(0L, f64Be.reversedArray(), HexEndianness.LITTLE).float64)
    }

    @Test
    fun previewsUtf8AndUtf16WithNulTerminationAndEndianness() {
        val utf8Bytes = "Hello\n世界\u0000ignored".toByteArray(Charsets.UTF_8)
        val snapUtf8 = inspectHexBytes(0L, utf8Bytes, HexEndianness.LITTLE)
        assertEquals("Hello\\n世界", snapUtf8.utf8Preview)

        val nulLeading = byteArrayOf(0x00, 0x41)
        assertEquals("\\0", inspectHexBytes(0L, nulLeading, HexEndianness.LITTLE).utf8Preview)

        val utf16Le = "Hi\u0000X".toByteArray(Charsets.UTF_16LE)
        assertEquals("Hi", inspectHexBytes(0L, utf16Le, HexEndianness.LITTLE).utf16Preview)

        val utf16Be = "Hi\u0000X".toByteArray(Charsets.UTF_16BE)
        assertEquals("Hi", inspectHexBytes(0L, utf16Be, HexEndianness.BIG).utf16Preview)
    }

    @Test
    fun handlesEmptyAndShortByteArraysSafely() {
        val empty = inspectHexBytes(0x100L, ByteArray(0), HexEndianness.LITTLE, pointerBits = 64)
        assertEquals(0, empty.availableBytes)
        assertNull(empty.int8)
        assertNull(empty.utf8Preview)
        assertNull(empty.utf16Preview)
        assertNull(empty.pointerDisplay)

        val one = inspectHexBytes(0x100L, byteArrayOf(0x41), HexEndianness.LITTLE, pointerBits = 64)
        assertEquals("65", one.int8)
        assertEquals("65", one.uint8)
        assertNull(one.int16)
        assertNull(one.utf16Preview)
        assertEquals("A", one.utf8Preview)
    }

    @Test
    fun parsesSingleInstructionDisasmJson() {
        val json = """
            WARN: minor
            [{"offset":4198400,"bytes":"4889e5","size":3,"type":"mov","opcode":"mov rbp, rsp","disasm":"mov rbp, rsp"}]
        """.trimIndent()
        val parsed = parseSingleDisasmJson(json)
        assertEquals(HexSingleDisasm("mov rbp, rsp", "4889E5", 3, "mov"), parsed)
        assertEquals("48 89 E5", parsed?.formattedBytes)

        val opcodeOnly = """[{"bytes":"90","opcode":"nop"}]"""
        val parsedFallback = parseSingleDisasmJson(opcodeOnly)
        assertEquals(HexSingleDisasm("nop", "90", 1, ""), parsedFallback)

        assertNull(parseSingleDisasmJson("[]"))
        assertNull(parseSingleDisasmJson("not json"))
    }
}

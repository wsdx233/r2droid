package top.wsdx233.r2droid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2SanitizerTest {

    @Test
    fun `detects shell and r2 command injection characters`() {
        assertTrue(R2Sanitizer.hasCommandInjection("foo; !id"))
        assertTrue(R2Sanitizer.hasCommandInjection("foo | sh"))
        assertTrue(R2Sanitizer.hasCommandInjection("foo `id`"))
        assertTrue(R2Sanitizer.hasCommandInjection("foo $(id)"))
        assertTrue(R2Sanitizer.hasCommandInjection("foo\n!id"))
        assertFalse(R2Sanitizer.hasCommandInjection("sym.main_entry"))
    }

    @Test
    fun `validates identifiers, mangled symbols, hosts, and ports`() {
        assertTrue(R2Sanitizer.isSafeIdentifier("sym.main"))
        assertTrue(R2Sanitizer.isSafeIdentifier("fcn.00401000"))
        assertFalse(R2Sanitizer.isSafeIdentifier("sym.main; !id"))
        assertFalse(R2Sanitizer.isSafeIdentifier("sym main"))
        assertFalse(R2Sanitizer.isSafeIdentifier("sym@0x1000"))

        assertTrue(R2Sanitizer.isSafeMangledSymbol("_ZN3foo3barEv"))
        assertFalse(R2Sanitizer.isSafeMangledSymbol("_ZN3foo;id"))

        assertTrue(R2Sanitizer.isSafeHost("127.0.0.1"))
        assertTrue(R2Sanitizer.isSafeHost("[::1]"))
        assertFalse(R2Sanitizer.isSafeHost("127.0.0.1;id"))

        assertTrue(R2Sanitizer.isValidPort("27042"))
        assertFalse(R2Sanitizer.isValidPort("0"))
        assertFalse(R2Sanitizer.isValidPort("70000"))
        assertFalse(R2Sanitizer.isValidPort("27042;id"))
    }

    @Test
    fun `encodes base64 comments consistently without android dependency`() {
        assertEquals("", R2Sanitizer.encodeBase64Comment(""))
        assertEquals("aGVsbG8=", R2Sanitizer.encodeBase64Comment("hello"))
        assertEquals("YSAmIGI7ICFpZAo=", R2Sanitizer.encodeBase64Comment("a & b; !id\r\n"))
    }

    @Test
    fun `parses and formats unsigned 64-bit addresses above Long MAX_VALUE`() {
        val kernelAddr = 0xffffffff81000000UL.toLong()
        assertEquals("0xffffffff81000000", R2Sanitizer.formatHexAddress(kernelAddr))
        assertEquals("0xFFFFFFFF81000000", R2Sanitizer.formatHexAddressUpper(kernelAddr))
        assertEquals(kernelAddr, R2Sanitizer.parseUnsignedAddress("0xffffffff81000000"))
        assertEquals(kernelAddr, R2Sanitizer.parseUnsignedAddress("18446744071578845184"))

        val maxU64 = ULong.MAX_VALUE.toLong()
        assertEquals("0xffffffffffffffff", R2Sanitizer.formatHexAddress(maxU64))
        assertEquals(maxU64, R2Sanitizer.parseUnsignedAddress("18446744073709551615"))
        assertNull(R2Sanitizer.parseUnsignedAddress(""))
    }

    @Test
    fun `preserves unsigned 64-bit JSON integers above Long MAX_VALUE`() {
        val raw = """{"addr":18446744071578845184,"size":4096,"neg":-1,"flt":1.5,"msg":"18446744071578845184"}"""
        val obj = R2Sanitizer.parseJsonObject(raw)
        assertEquals(0xffffffff81000000UL.toLong(), obj.optUnsignedLong("addr"))
        assertEquals(4096L, obj.optUnsignedLong("size"))
        assertEquals(-1L, obj.optUnsignedLong("neg"))
        assertEquals("18446744071578845184", obj.getString("msg"))
        assertNull(obj.optUnsignedLongOrNull("missing"))

        val arr = R2Sanitizer.parseJsonArray("""[{"offset":18446744073709551615}]""")
        assertEquals(ULong.MAX_VALUE.toLong(), arr.getJSONObject(0).optUnsignedLong("offset"))
    }
}

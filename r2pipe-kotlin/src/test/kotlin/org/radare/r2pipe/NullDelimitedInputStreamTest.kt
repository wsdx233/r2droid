package org.radare.r2pipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.io.ByteArrayInputStream

class NullDelimitedInputStreamTest {
    @Test
    fun readsUntilNullByte() {
        val source = ByteArrayInputStream("hello\u0000world".toByteArray())
        val stream = NullDelimitedInputStream(source)

        val bytes = stream.readBytes()

        assertEquals("hello", bytes.decodeToString())
        assertEquals(-1, stream.read())
    }

    @Test
    fun closeDoesNotCloseUnderlyingStream() {
        val source = ByteArrayInputStream("abc".toByteArray())
        val stream = NullDelimitedInputStream(source)

        stream.close()

        assertEquals('a'.code, source.read())
        assertFalse(source.read() == -1)
    }
}

package org.radare.r2pipe

import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.PushbackInputStream
import kotlin.test.assertFailsWith

class NullDelimitedInputStreamTest {
    private fun source(text: String): PushbackInputStream = PushbackInputStream(
        ByteArrayInputStream(text.toByteArray()),
        NullDelimitedInputStream.READ_BUFFER_SIZE
    )

    @Test
    fun bulkReadPreservesOffsetsAndTheFollowingFrame() {
        val source = source("hello\u0000世界\u0000")
        val stream = NullDelimitedInputStream(source)
        val buffer = ByteArray(16) { '!'.code.toByte() }

        assertEquals(5, stream.read(buffer, 2, 10))
        assertEquals("hello", buffer.decodeToString(2, 7))
        assertEquals("!!", buffer.decodeToString(0, 2))
        assertEquals(-1, stream.read())
        assertEquals("世界", NullDelimitedInputStream(source).readBytes().decodeToString())
    }

    @Test
    fun zeroLengthReadsDoNotEndTheFrame() {
        val stream = NullDelimitedInputStream(source("hello\u0000"))

        assertEquals(0, stream.read(ByteArray(0), 0, 0))
        assertEquals("hello", stream.readBytes().decodeToString())
        assertEquals(0, stream.read(ByteArray(0), 0, 0))
    }

    @Test
    fun skipStopsAtTheDelimiterAndPreservesTheFollowingFrame() {
        val source = source("hello\u0000world\u0000")
        val stream = NullDelimitedInputStream(source)

        assertEquals(5L, stream.skip(100))
        assertEquals(-1, stream.read())
        assertEquals("world", NullDelimitedInputStream(source).readBytes().decodeToString())
    }

    @Test
    fun bulkReadsRejectTruncatedFrames() {
        val stream = NullDelimitedInputStream(source("truncated"))

        assertFailsWith<EOFException> { stream.readBytes() }
    }

    @Test
    fun singleByteReadsRejectTruncatedFrames() {
        val stream = NullDelimitedInputStream(source("a"))

        assertEquals('a'.code, stream.read())
        assertFailsWith<EOFException> { stream.read() }
    }
}

package org.radare.r2pipe.kotlin

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NullDelimitedInputStreamTest {
    private fun source(text: String) = StdioFrameSource(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun bulkReadPreservesOffsetsAndTheFollowingFrame() {
        val source = source("hello\u0000世界\u0000")
        val stream = NullDelimitedInputStream(source)
        val buffer = ByteArray(16) { '!'.code.toByte() }

        assertEquals(5, stream.read(buffer, 2, 10))
        assertEquals("hello", buffer.decodeToString(2, 7))
        assertEquals("!!", buffer.decodeToString(0, 2))
        assertEquals("!".repeat(buffer.size - 7), buffer.decodeToString(7))
        assertEquals(-1, stream.read())
        assertEquals("世界", NullDelimitedInputStream(source).readBytes().decodeToString())
    }

    @Test
    fun terminatorAtStartOfRefillPreservesTheNextFrame() {
        val payload = "a".repeat(StdioFrameSource.BUFFER_SIZE)
        val source = source("$payload\u0000tail\u0000")
        assertEquals(payload, NullDelimitedInputStream(source).readBytes().decodeToString())
        assertEquals("tail", NullDelimitedInputStream(source).readBytes().decodeToString())
    }

    @Test
    fun emptyFrameLeavesTheEntireDestinationUntouched() {
        val source = source("\u0000next\u0000")
        val buffer = ByteArray(10) { it.toByte() }
        val before = buffer.copyOf()

        assertEquals(-1, NullDelimitedInputStream(source).read(buffer, 1, 8))
        assertContentEquals(before, buffer)
        assertEquals("next", NullDelimitedInputStream(source).readBytes().decodeToString())
    }

    @Test
    fun closingABlockedReadAbortsItEvenIfBytesArriveDuringCancellation() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val input = object : ByteArrayInputStream("late\u0000".toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                entered.countDown()
                release.await()
                return super.read(b, off, len)
            }
        }
        val stream = NullDelimitedInputStream(StdioFrameSource(input), onIncomplete = release::countDown)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val read = workers.submit { assertFailsWith<IOException> { stream.read(ByteArray(16)) } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            workers.submit { stream.close() }.get(5, TimeUnit.SECONDS)
            read.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
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

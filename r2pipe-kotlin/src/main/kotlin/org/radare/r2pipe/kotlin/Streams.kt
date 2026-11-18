package org.radare.r2pipe.kotlin

import okhttp3.Call
import okhttp3.Response
import java.io.Closeable
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One lookahead buffer per process. Only the handshake or the active frame reads it. */
internal class StdioFrameSource(private val input: InputStream) : Closeable {
    private val buffer = ByteArray(BUFFER_SIZE)
    private var position = 0
    private var limit = 0
    @Volatile private var closed = false

    private fun fill(): Boolean {
        if (closed) throw IOException("R2 stdout is closed")
        if (position < limit) return true
        position = 0
        limit = input.read(buffer)
        return limit > 0
    }

    fun readByte(): Int = if (fill()) buffer[position++].toInt() and 0xff else -1

    fun readPayload(b: ByteArray, off: Int, len: Int): Int {
        if (!fill()) return -1
        val start = position
        val end = start + minOf(len, limit - start)
        while (position < end && buffer[position] != 0.toByte()) position++
        buffer.copyInto(b, off, start, position)
        return position - start
    }

    fun consumeBufferedNul(): Boolean {
        if (position >= limit || buffer[position] != 0.toByte()) return false
        position++
        return true
    }

    override fun close() {
        closed = true
        input.close()
    }

    companion object {
        const val BUFFER_SIZE = 256 * 1024
    }
}

internal class NullDelimitedInputStream(
    private val source: StdioFrameSource,
    private val onComplete: () -> Unit = {},
    private val onIncomplete: () -> Unit = {}
) : InputStream() {
    private enum class State { OPEN, COMPLETE, ABORTED }
    private val state = AtomicReference(State.OPEN)

    @Synchronized
    override fun read(): Int = readFrame {
        when (val value = source.readByte()) {
            -1 -> throw EOFException("R2 response ended before its NUL marker")
            0 -> {
                complete()
                -1
            }
            else -> value
        }
    }

    @Synchronized
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || off > b.size - len) {
            throw IndexOutOfBoundsException()
        }
        if (len == 0) return 0
        return readFrame {
            val count = source.readPayload(b, off, len)
            if (count < 0) throw EOFException("R2 response ended before its NUL marker")
            if (source.consumeBufferedNul()) complete()
            if (count == 0) -1 else count
        }
    }

    private inline fun readFrame(read: () -> Int): Int {
        when (state.get()) {
            State.COMPLETE -> return -1
            State.ABORTED -> throw IOException("R2 response stream is closed")
            State.OPEN -> Unit
        }
        try {
            val count = read()
            if (state.get() == State.ABORTED) throw IOException("R2 response stream is closed")
            return count
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    private fun complete() {
        if (state.compareAndSet(State.OPEN, State.COMPLETE)) onComplete()
    }

    // Never wait for the reader's monitor: terminating the child is what unblocks its pipe read.
    override fun close() {
        if (state.compareAndSet(State.OPEN, State.ABORTED)) onIncomplete()
    }
}

internal class HttpResponseInputStream(
    private val call: Call,
    response: Response,
    private val onClose: () -> Unit
) : FilterInputStream(response.body.byteStream()) {
    private val released = AtomicBoolean(false)
    private val readLock = Any()
    @Volatile
    private var ended = false

    override fun read(): Int {
        if (ended) return -1
        return finishRead(withOpenStream { super.read() })
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || off > b.size - len) throw IndexOutOfBoundsException()
        if (len == 0) return 0
        if (ended) return -1
        return finishRead(withOpenStream { super.read(b, off, len) })
    }

    override fun skip(n: Long): Long {
        if (n <= 0 || ended) return 0
        return withOpenStream { super.skip(n) }
    }

    override fun available(): Int {
        if (ended) return 0
        return withOpenStream { super.available() }
    }

    private fun finishRead(count: Int): Int {
        if (count == -1) {
            ended = true
            close()
        }
        return count
    }

    private inline fun <T> withOpenStream(block: () -> T): T = synchronized(readLock) {
        try {
            ensureOpen()
            val result = block()
            ensureOpen()
            result
        } catch (e: IOException) {
            try {
                close()
            } catch (cleanupError: Exception) {
                e.addSuppressed(cleanupError)
            }
            throw e
        }
    }

    private fun ensureOpen() {
        if (released.get() || call.isCanceled()) throw IOException("HTTP response stream is closed")
    }

    override fun close() {
        if (!released.compareAndSet(false, true)) return
        // Abort socket I/O before waiting for the non-thread-safe response source.
        call.cancel()
        try {
            synchronized(readLock) { super.close() }
        } finally {
            onClose()
        }
    }
}

package org.radare.r2pipe

import okhttp3.Call
import okhttp3.Response
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.io.PushbackInputStream
import java.util.concurrent.atomic.AtomicBoolean

internal class NullDelimitedInputStream(
    private val source: PushbackInputStream,
    private val onComplete: () -> Unit = {},
    private val onIncomplete: () -> Unit = {}
) : InputStream() {
    private var ended = false

    @Synchronized
    override fun read(): Int {
        if (ended) return -1
        return try {
            when (val value = source.read()) {
                -1 -> throw EOFException("R2 response ended before its NUL marker")
                0 -> {
                    finish(complete = true)
                    -1
                }
                else -> value
            }
        } catch (e: IOException) {
            finish(complete = false)
            throw e
        }
    }

    @Synchronized
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || off > b.size - len) {
            throw IndexOutOfBoundsException()
        }
        if (len == 0) return 0
        if (ended) return -1
        return try {
            val count = source.read(b, off, minOf(len, READ_BUFFER_SIZE))
            if (count == -1) {
                throw EOFException("R2 response ended before its NUL marker")
            }
            for (index in off until off + count) {
                if (b[index].toInt() == 0) {
                    val remaining = off + count - index - 1
                    if (remaining > 0) {
                        source.unread(b, index + 1, remaining)
                    }
                    finish(complete = true)
                    return if (index == off) -1 else index - off
                }
            }
            count
        } catch (e: IOException) {
            finish(complete = false)
            throw e
        }
    }

    @Synchronized
    override fun close() {
        finish(complete = false)
    }

    private fun finish(complete: Boolean) {
        if (ended) return
        ended = true
        if (complete) onComplete() else onIncomplete()
    }

    companion object {
        const val READ_BUFFER_SIZE = 256 * 1024
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

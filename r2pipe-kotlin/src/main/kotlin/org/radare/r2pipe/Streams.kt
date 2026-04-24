package org.radare.r2pipe

import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection

internal class NullDelimitedInputStream(source: InputStream) : FilterInputStream(source) {
    private var ended = false

    override fun read(): Int {
        if (ended) return -1
        val value = super.read()
        if (value <= 0) {
            ended = true
            return -1
        }
        if (value == 0) {
            ended = true
            return -1
        }
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (ended) return -1
        val count = super.read(b, off, len)
        if (count <= 0) {
            ended = true
            return -1
        }
        for (index in off until off + count) {
            if (b[index].toInt() == 0) {
                ended = true
                return if (index == off) -1 else index - off
            }
        }
        return count
    }

    override fun close() {
        ended = true
    }
}

internal class HttpResponseInputStream(
    private val connection: HttpURLConnection,
    source: InputStream
) : FilterInputStream(source) {
    override fun close() {
        try {
            super.close()
        } finally {
            connection.disconnect()
        }
    }
}

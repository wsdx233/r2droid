package top.wsdx233.r2droid.core.data.model

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.PriorityQueue
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Compact per-bucket byte statistics sampled directly from the local binary file.
 * Stored as unsigned 0..255 values in ByteArrays (3 bytes per bucket) so it stays tiny
 * and avoids native r2 `p=ej` issues on Android.
 */
class BinaryByteProfile(
    val startAddress: Long,
    val endExclusive: Long,
    val entropy: ByteArray,
    val zeroRatio: ByteArray,
    val asciiRatio: ByteArray
) {
    val bucketCount: Int get() = entropy.size
    val isEmpty: Boolean get() = entropy.isEmpty() || endExclusive.toULong() <= startAddress.toULong()

    fun entropyAt(bucket: Int): Int = entropy[bucket].toInt() and 0xFF
    fun zeroRatioAt(bucket: Int): Int = zeroRatio[bucket].toInt() and 0xFF
    fun asciiRatioAt(bucket: Int): Int = asciiRatio[bucket].toInt() and 0xFF

    fun bucketForAddress(address: Long): Int {
        if (isEmpty) return -1
        val uStart = startAddress.toULong()
        val uEnd = endExclusive.toULong()
        val range = (uEnd - 1UL - uStart).coerceAtLeast(1UL).toDouble()
        val clamped = address.toULong().coerceIn(uStart, uEnd - 1UL)
        return (((clamped - uStart).toDouble() / range) * bucketCount)
            .toInt()
            .coerceIn(0, bucketCount - 1)
    }

    companion object {
        val Empty = BinaryByteProfile(0L, 0L, ByteArray(0), ByteArray(0), ByteArray(0))
    }
}

object BinaryByteSampler {
    private const val DEFAULT_BUCKETS = 1024
    private const val DEFAULT_SAMPLE_WINDOW = 512
    private const val ELF_ALLOC = 2L
    private const val INV_LN2 = 1.4426950408889634
    private const val EXACT_ENTROPY_FILE_LIMIT = 4L * 1024 * 1024
    private const val SAMPLED_ENTROPY_WINDOW = 4096

    private data class FileSpan(
        val start: Long,
        val endExclusive: Long,
        val fileOffset: Long,
        val sourceSize: Long
    )

    private data class ResolvedSpan(
        val start: Long,
        var endExclusive: Long,
        val fileOffset: Long
    )

    /**
     * Sample byte-level structure across [startAddress, endAddress) directly from [file].
     * Reads at most [buckets] * [sampleWindowBytes] (~512 KB by default), taking a few ms.
     */
    fun sampleForLayout(
        file: File?,
        sections: List<Section>,
        virtualAddressing: Boolean?,
        startAddress: Long,
        endAddress: Long,
        isElf: Boolean = false,
        buckets: Int = DEFAULT_BUCKETS,
        sampleWindowBytes: Int = DEFAULT_SAMPLE_WINDOW
    ): BinaryByteProfile? {
        if (file == null || !file.isFile || !file.canRead()) return null
        val uStart = startAddress.toULong()
        val uEnd = endAddress.toULong()
        if (buckets <= 0 || sampleWindowBytes <= 0 || uEnd <= uStart) return null
        val fileLength = file.length()
        if (fileLength <= 0L) return null

        val fileSpans = buildFileSpans(sections, virtualAddressing, fileLength, isElf)
        if (fileSpans.isEmpty()) return null

        val addressSpan = uEnd - uStart
        val actualBuckets = minOf(buckets.toULong(), addressSpan).toInt().coerceAtLeast(1)
        val entropyOut = ByteArray(actualBuckets)
        val zeroOut = ByteArray(actualBuckets)
        val asciiOut = ByteArray(actualBuckets)
        val freq = IntArray(256)
        val raw = ByteArray(sampleWindowBytes)
        val buffer = ByteBuffer.wrap(raw)

        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val channel = raf.channel
                val totalRange = addressSpan.toDouble()
                var spanCursor = 0

                for (i in 0 until actualBuckets) {
                    val bStart = uStart + ((i.toDouble() / actualBuckets) * totalRange).toULong()
                    val bEnd = if (i == actualBuckets - 1) {
                        uEnd
                    } else {
                        val candidate = uStart + (((i + 1).toDouble() / actualBuckets) * totalRange).toULong()
                        val minNext = if (bStart < uEnd) bStart + 1UL else uEnd
                        maxOf(candidate, minNext).coerceAtMost(uEnd)
                    }
                    val mid = bStart + (bEnd - bStart) / 2UL

                    while (spanCursor < fileSpans.size && fileSpans[spanCursor].endExclusive.toULong() <= mid) {
                        spanCursor++
                    }
                    val span = fileSpans.getOrNull(spanCursor)?.takeIf {
                        mid >= it.start.toULong() && mid < it.endExclusive.toULong()
                    }
                    if (span == null) {
                        // NOBITS (e.g. .bss) or unmapped gap -> treat as zero-filled / hollow
                        entropyOut[i] = 0
                        zeroOut[i] = 0xFF.toByte()
                        asciiOut[i] = 0
                        continue
                    }

                    val overlapStart = maxOf(bStart, span.start.toULong())
                    val overlapEnd = minOf(bEnd, span.endExclusive.toULong())
                    val overlapLen = if (overlapEnd > overlapStart) overlapEnd - overlapStart else 1UL
                    val readLen = minOf(overlapLen, sampleWindowBytes.toULong()).toInt()
                    val sampleAddr = if (overlapLen > readLen.toULong()) {
                        overlapStart + (overlapLen - readLen.toULong()) / 2UL
                    } else {
                        overlapStart
                    }
                    val relOffset = sampleAddr - span.start.toULong()
                    if (relOffset > Long.MAX_VALUE.toULong()) {
                        entropyOut[i] = 0
                        zeroOut[i] = 0xFF.toByte()
                        asciiOut[i] = 0
                        continue
                    }
                    val filePos = span.fileOffset + relOffset.toLong()
                    if (filePos < 0L || filePos >= fileLength) {
                        entropyOut[i] = 0
                        zeroOut[i] = 0xFF.toByte()
                        asciiOut[i] = 0
                        continue
                    }
                    val cappedRead = minOf(readLen.toLong(), fileLength - filePos).toInt()
                    buffer.clear()
                    buffer.limit(cappedRead)
                    var read = 0
                    while (read < cappedRead) {
                        val n = channel.read(buffer, filePos + read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read <= 0) {
                        entropyOut[i] = 0
                        zeroOut[i] = 0xFF.toByte()
                        asciiOut[i] = 0
                        continue
                    }

                    analyzeWindow(raw, read, freq, entropyOut, zeroOut, asciiOut, i)
                }
            }
            BinaryByteProfile(startAddress, endAddress, entropyOut, zeroOut, asciiOut)
        }.getOrNull()
    }

    /**
     * Local Kotlin fallback for `p=ej <blockCount>` so the BinInfo Overview entropy graph works
     * reliably on native Android radare2 builds where `p=ej` has a bug.
     */
    fun computeEntropyData(file: File?, blockCount: Int = 64): EntropyData? {
        if (file == null || !file.isFile || !file.canRead() || blockCount <= 0) return null
        val fileLength = file.length()
        if (fileLength <= 0L) return null

        val actualBlocks = minOf(blockCount.toLong(), fileLength).toInt().coerceAtLeast(1)
        val blockSize = (fileLength / actualBlocks).coerceAtLeast(1L)
        val exactRead = fileLength <= EXACT_ENTROPY_FILE_LIMIT
        val windowSize = if (exactRead) blockSize.coerceAtMost(65536L).toInt() else SAMPLED_ENTROPY_WINDOW
        val raw = ByteArray(windowSize)
        val buffer = ByteBuffer.wrap(raw)
        val freq = IntArray(256)

        return runCatching {
            val blocks = ArrayList<EntropyBlock>(actualBlocks)
            RandomAccessFile(file, "r").use { raf ->
                val channel = raf.channel
                for (i in 0 until actualBlocks) {
                    val blockStart = i * blockSize
                    val blockEnd = if (i == actualBlocks - 1) fileLength else minOf(fileLength, blockStart + blockSize)
                    val len = (blockEnd - blockStart).coerceAtLeast(1L)
                    freq.fill(0)
                    var totalRead = 0L

                    if (exactRead) {
                        var pos = blockStart
                        while (pos < blockEnd) {
                            val chunk = minOf(windowSize.toLong(), blockEnd - pos).toInt()
                            buffer.clear()
                            buffer.limit(chunk)
                            val n = channel.read(buffer, pos)
                            if (n <= 0) break
                            for (j in 0 until n) freq[raw[j].toInt() and 0xFF]++
                            pos += n
                            totalRead += n
                        }
                    } else {
                        val sampleLen = minOf(len, windowSize.toLong()).toInt()
                        val samplePos = blockStart + (len - sampleLen) / 2
                        buffer.clear()
                        buffer.limit(sampleLen)
                        var read = 0
                        while (read < sampleLen) {
                            val n = channel.read(buffer, samplePos + read)
                            if (n <= 0) break
                            read += n
                        }
                        for (j in 0 until read) freq[raw[j].toInt() and 0xFF]++
                        totalRead = read.toLong()
                    }

                    val entropyVal = computeShannonScaled(freq, totalRead)
                    blocks.add(EntropyBlock(addr = blockStart, value = entropyVal))
                }
            }
            EntropyData(
                blocksize = blockSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                address = 0L,
                size = fileLength,
                entropy = blocks
            )
        }.getOrNull()
    }

    private fun buildFileSpans(
        sections: List<Section>,
        virtualAddressing: Boolean?,
        fileLength: Long,
        isElf: Boolean
    ): List<ResolvedSpan> {
        if (virtualAddressing == false || (virtualAddressing == null && sections.isEmpty())) {
            return listOf(ResolvedSpan(0L, fileLength, 0L))
        }
        if (virtualAddressing == null) return emptyList()

        val rawSpans = sections.mapNotNull { section ->
            val mapped = if (isElf && section.flags != null) {
                section.flags and ELF_ALLOC != 0L
            } else {
                section.vAddr != 0L || section.perm.any { it in "rwx" }
            }
            if (!mapped) return@mapNotNull null
            if (section.type.equals("NOBITS", ignoreCase = true) ||
                section.type.equals("SHT_NOBITS", ignoreCase = true)
            ) return@mapNotNull null
            if (section.pAddr < 0 || section.size <= 0 || section.pAddr >= fileLength) {
                return@mapNotNull null
            }
            val backedSize = minOf(section.size, fileLength - section.pAddr)
            val virtualLimit = if (section.vSize > 0) minOf(backedSize, section.vSize) else backedSize
            if (virtualLimit <= 0 || virtualLimit.toULong() > ULong.MAX_VALUE - section.vAddr.toULong()) return@mapNotNull null
            FileSpan(
                start = section.vAddr,
                endExclusive = section.vAddr + virtualLimit,
                fileOffset = section.pAddr,
                sourceSize = virtualLimit
            )
        }
        if (rawSpans.isEmpty()) return emptyList()

        val sorted = rawSpans.sortedBy { it.start.toULong() }
        val active = PriorityQueue(compareBy<FileSpan> { it.sourceSize }.thenBy { it.start.toULong() })
        val resolved = ArrayList<ResolvedSpan>(sorted.size)
        var next = 0
        var pos = sorted.first().start
        while (next < sorted.size || active.isNotEmpty()) {
            if (active.isEmpty()) pos = sorted[next].start
            while (next < sorted.size && sorted[next].start == pos) active.add(sorted[next++])
            val top = active.element()
            val end = minOf(top.endExclusive.toULong(), sorted.getOrNull(next)?.start?.toULong() ?: ULong.MAX_VALUE).toLong()
            val offsetAtPos = top.fileOffset + (pos.toULong() - top.start.toULong()).toLong()
            val last = resolved.lastOrNull()
            if (last != null && last.endExclusive == pos &&
                last.fileOffset + (pos.toULong() - last.start.toULong()).toLong() == offsetAtPos
            ) {
                last.endExclusive = end
            } else {
                resolved.add(ResolvedSpan(pos, end, offsetAtPos))
            }
            pos = end
            while (active.isNotEmpty() && active.element().endExclusive.toULong() <= pos.toULong()) active.remove()
        }
        return resolved
    }

    private fun analyzeWindow(
        data: ByteArray,
        length: Int,
        freq: IntArray,
        entropyOut: ByteArray,
        zeroOut: ByteArray,
        asciiOut: ByteArray,
        index: Int
    ) {
        freq.fill(0)
        var zeroOrFill = 0
        var asciiChars = 0
        var prevPrintable = false

        for (i in 0 until length) {
            val b = data[i].toInt() and 0xFF
            freq[b]++
            if (b == 0x00 || b == 0xFF) zeroOrFill++
            val printable = b in 0x20..0x7E || b == 0x09 || b == 0x0A || b == 0x0D
            if (printable) {
                asciiChars++
                prevPrintable = true
            } else {
                // Count single NUL terminator inside C-string pools as part of ASCII run.
                if (b == 0x00 && prevPrintable) asciiChars++
                prevPrintable = false
            }
        }

        entropyOut[index] = computeShannonScaled(freq, length.toLong()).toByte()
        zeroOut[index] = ((zeroOrFill * 255.0) / length).roundToInt().coerceIn(0, 255).toByte()
        asciiOut[index] = ((asciiChars * 255.0) / length).roundToInt().coerceIn(0, 255).toByte()
    }

    private fun computeShannonScaled(freq: IntArray, total: Long): Int {
        if (total <= 1L) return 0
        val totalD = total.toDouble()
        var entropy = 0.0
        for (c in freq) {
            if (c > 0) {
                val p = c / totalD
                entropy -= p * ln(p) * INV_LN2
            }
        }
        return ((entropy / 8.0) * 255.0).roundToInt().coerceIn(0, 255)
    }
}

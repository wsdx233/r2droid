package top.wsdx233.r2droid.feature.hex.data

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/** Chunks start at [viewStartAddress], so each logical row sits entirely within one chunk. */
class HexDataManager internal constructor(
    val viewStartAddress: Long,
    val viewEndAddress: Long,
    private val readChunk: suspend (Long, Int) -> Result<ByteArray>
) {
    companion object {
        const val CHUNK_SIZE = 4096
        const val BYTES_PER_ROW = 16
        const val ROWS_PER_CHUNK = CHUNK_SIZE / BYTES_PER_ROW
        private const val CACHE_MAX_SIZE = 100
        private const val PREFETCH_DISTANCE = 2
    }

    // A lazy list has Int indices even when the address range spans more than Int.MAX_VALUE rows.
    private val displaySize = if (viewEndAddress > viewStartAddress) {
        (viewEndAddress.toULong() - viewStartAddress.toULong())
            .coerceAtMost((Int.MAX_VALUE.toLong() * BYTES_PER_ROW).toULong()).toLong()
    } else 0L
    private val displayEnd = viewStartAddress + displaySize
    val totalRows: Int = ((displaySize + BYTES_PER_ROW - 1) / BYTES_PER_ROW).toInt()
    private val lastChunkIndex = if (totalRows == 0) -1 else (totalRows - 1) / ROWS_PER_CHUNK

    private class ChunkState : State<ByteArray?> {
        override var value: ByteArray? by mutableStateOf(null)
    }

    private class ChunkReference(val index: Int, state: ChunkState, queue: ReferenceQueue<ChunkState>) :
        WeakReference<ChunkState>(state, queue)

    private val lock = Any()
    private val readMutex = Mutex()
    private val cache = LinkedHashMap<Int, ChunkState>(CACHE_MAX_SIZE, 0.75f, true)
    // Only the LRU retains pages. An evicted handle remains observable if a composed row holds it;
    // weak entries are collected and pruned rather than keeping state for every page visited.
    private val collectedStates = ReferenceQueue<ChunkState>()
    private val states = HashMap<Int, ChunkReference>()
    private val missingChunk: State<ByteArray?> = mutableStateOf(null)
    private var closed = false
    private var generation by mutableIntStateOf(0)

    /** Changes on explicit invalidation, never on ordinary loads or evictions. */
    val refreshGeneration: Int get() = generation

    /** Observe just one chunk. Published arrays are read-only and shared across all its rows. */
    fun chunkState(chunkIndex: Int): State<ByteArray?> = synchronized(lock) {
        if (closed || chunkIndex !in 0..lastChunkIndex) missingChunk else stateFor(chunkIndex)
    }

    private fun stateFor(index: Int): ChunkState {
        while (true) {
            val collected = collectedStates.poll() as? ChunkReference ?: break
            if (states[collected.index] === collected) states.remove(collected.index)
        }
        return cache[index] ?: states[index]?.get() ?: ChunkState().also {
            states[index] = ChunkReference(index, it, collectedStates)
        }
    }

    fun getRowAddress(rowIndex: Int): Long = viewStartAddress + rowIndex.toLong() * BYTES_PER_ROW

    /** Length in the displayable window, even if the original address range spans more than Long.MAX_VALUE bytes. */
    fun bytesInRow(rowIndex: Int): Int = minOf(BYTES_PER_ROW.toLong(), displaySize - rowIndex.toLong() * BYTES_PER_ROW).toInt()

    fun getRowIndexForAddress(addr: Long): Int = when {
        addr <= viewStartAddress -> 0
        addr >= displayEnd -> (totalRows - 1).coerceAtLeast(0)
        else -> ((addr - viewStartAddress) / BYTES_PER_ROW).toInt()
    }

    /**
     * Reads up to [maxLength] bytes starting at [address] from already-cached chunks only.
     * Returns null if any required chunk is not currently cached.
     */
    fun readCachedBytes(address: Long, maxLength: Int): ByteArray? {
        if (maxLength <= 0 || displaySize <= 0L) return ByteArray(0)
        if (address < viewStartAddress || address >= displayEnd) return ByteArray(0)
        val remainingInView = displayEnd - address
        val targetLength = minOf(maxLength.toLong(), remainingInView).toInt()
        if (targetLength <= 0) return ByteArray(0)

        val out = ByteArray(targetLength)
        var copied = 0
        synchronized(lock) {
            if (closed) return null
            while (copied < targetLength) {
                val curAddr = address + copied
                val rel = curAddr - viewStartAddress
                val chunkIndex = (rel / CHUNK_SIZE).toInt()
                val offsetInChunk = (rel % CHUNK_SIZE).toInt()
                val chunkBytes = cache[chunkIndex]?.value ?: return null
                if (offsetInChunk >= chunkBytes.size) {
                    // Underlying short read ended before the logical view extent.
                    return out.copyOf(copied)
                }
                val chunkExpected = minOf(CHUNK_SIZE.toLong(), displayEnd - (viewStartAddress + chunkIndex.toLong() * CHUNK_SIZE)).toInt()
                val toCopy = minOf(targetLength - copied, chunkBytes.size - offsetInChunk)
                System.arraycopy(chunkBytes, offsetInChunk, out, copied, toCopy)
                copied += toCopy
                // If this chunk itself was a short read (shorter than expected chunk size), stop here.
                if (chunkBytes.size < chunkExpected) {
                    return out.copyOf(copied)
                }
            }
        }
        return out
    }

    /**
     * Reads up to [maxLength] bytes starting at [address], using cached chunks when available
     * and fetching uncached slices directly via [readChunk] without evicting viewport LRU entries.
     */
    suspend fun readBytes(address: Long, maxLength: Int): Result<ByteArray> {
        readCachedBytes(address, maxLength)?.let { return Result.success(it) }
        if (maxLength <= 0 || displaySize <= 0L) return Result.success(ByteArray(0))
        if (address < viewStartAddress || address >= displayEnd) return Result.success(ByteArray(0))

        val targetLength = minOf(maxLength.toLong(), displayEnd - address).toInt()
        if (targetLength <= 0) return Result.success(ByteArray(0))

        val context = currentCoroutineContext()
        val out = ByteArray(targetLength)
        var copied = 0
        while (copied < targetLength) {
            context.ensureActive()
            val curAddr = address + copied
            val rel = curAddr - viewStartAddress
            val chunkIndex = (rel / CHUNK_SIZE).toInt()
            val offsetInChunk = (rel % CHUNK_SIZE).toInt()
            val chunkStart = viewStartAddress + chunkIndex.toLong() * CHUNK_SIZE
            val chunkExpected = minOf(CHUNK_SIZE.toLong(), displayEnd - chunkStart).toInt()
            val sliceLen = minOf(targetLength - copied, chunkExpected - offsetInChunk)

            val cachedSlice = synchronized(lock) {
                if (closed) return Result.failure(CancellationException("HexDataManager closed"))
                cache[chunkIndex]?.value
            }

            if (cachedSlice != null) {
                if (offsetInChunk >= cachedSlice.size) {
                    return Result.success(out.copyOf(copied))
                }
                val available = minOf(sliceLen, cachedSlice.size - offsetInChunk)
                System.arraycopy(cachedSlice, offsetInChunk, out, copied, available)
                copied += available
                if (cachedSlice.size < chunkExpected) {
                    return Result.success(out.copyOf(copied))
                }
            } else {
                val fetched = readChunk(curAddr, sliceLen).getOrElse { err ->
                    if (err is CancellationException) throw err
                    return Result.failure(err)
                }
                context.ensureActive()
                if (fetched.isEmpty()) {
                    return Result.success(out.copyOf(copied))
                }
                val toCopy = minOf(sliceLen, fetched.size)
                System.arraycopy(fetched, 0, out, copied, toCopy)
                copied += toCopy
                if (fetched.size < sliceLen) {
                    return Result.success(out.copyOf(copied))
                }
            }
        }
        return Result.success(out)
    }

    /** Load the inclusive visible range before any adjacent prefetch. Cancel on viewport changes. */
    suspend fun loadViewport(firstChunk: Int, lastChunk: Int) {
        val first = maxOf(0, firstChunk)
        val last = minOf(lastChunk, lastChunkIndex)
        if (last < first) return
        val expectedGeneration = synchronized(lock) {
            if (closed) return
            generation
        }
        for (index in first..last) {
            if (!loadChunk(index, expectedGeneration)) return
        }
        // Prefetch must not evict part of this viewport when all its chunks fit in cache.
        var budget = CACHE_MAX_SIZE - (last - first + 1)
        for (distance in 1..PREFETCH_DISTANCE) {
            if (budget <= 0) break
            val before = first - distance
            val after = last + distance
            if (before >= 0) {
                if (!loadChunk(before, expectedGeneration)) return
                budget--
            }
            if (after <= lastChunkIndex && budget > 0) {
                if (!loadChunk(after, expectedGeneration)) return
                budget--
            }
        }
    }

    private suspend fun loadChunk(index: Int, expectedGeneration: Int): Boolean = readMutex.withLock {
        val context = currentCoroutineContext()
        context.ensureActive()
        synchronized(lock) {
            if (closed || generation != expectedGeneration) return@withLock false
            if (cache[index] != null) return@withLock true
        }
        val start = viewStartAddress + index.toLong() * CHUNK_SIZE
        val length = minOf(CHUNK_SIZE.toLong(), displayEnd - start).toInt()
        val bytes = readChunk(start, length).getOrElse {
            if (it is CancellationException) throw it
            null
        }
        context.ensureActive()
        synchronized(lock) {
            if (closed || generation != expectedGeneration) return@withLock false
            if (bytes != null && bytes.size <= length) {
                val state = stateFor(index)
                if (cache.size == CACHE_MAX_SIZE) {
                    val entries = cache.entries.iterator()
                    entries.next().value.value = null
                    entries.remove()
                }
                state.value = bytes
                cache[index] = state
            }
        }
        true
    }

    /** Invalidate all cached chunks intersecting the write, including writes crossing a boundary. */
    internal fun invalidateRange(address: Long, byteCount: Int) {
        if (byteCount <= 0) return
        val end = if (address > Long.MAX_VALUE - byteCount) Long.MAX_VALUE else address + byteCount
        val first = maxOf(address, viewStartAddress)
        if (end <= first || displayEnd <= first) return
        val last = minOf(end, displayEnd) - 1
        invalidate(((first - viewStartAddress) / CHUNK_SIZE).toInt()..((last - viewStartAddress) / CHUNK_SIZE).toInt())
    }

    internal fun clearCache() = invalidate(0..lastChunkIndex)

    internal fun close() = invalidate(0..lastChunkIndex, close = true)

    private fun invalidate(chunks: IntRange, close: Boolean = false): Unit = synchronized(lock) {
        if (closed) return@synchronized
        closed = close
        Snapshot.withMutableSnapshot {
            val entries = cache.entries.iterator()
            while (entries.hasNext()) {
                val entry = entries.next()
                if (entry.key in chunks) {
                    entry.value.value = null
                    entries.remove()
                }
            }
            generation++
        }
        if (closed) states.clear()
    }
}

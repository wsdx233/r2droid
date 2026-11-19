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

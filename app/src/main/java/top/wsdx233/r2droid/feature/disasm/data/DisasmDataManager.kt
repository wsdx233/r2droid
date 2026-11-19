package top.wsdx233.r2droid.feature.disasm.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.core.data.model.AddressOverview
import top.wsdx233.r2droid.core.data.model.DisasmInstruction

/** Instructions and jump decorations are published together, never from different revisions. */
data class DisasmSnapshot(
    val instructions: List<DisasmInstruction> = emptyList(),
    val jumpToIndex: Map<Long, Int> = emptyMap(),
    val targetToIndex: Map<Long, Int> = emptyMap()
)

/** Bounded, immutable display windows. Fetching and merging never run on the UI thread. */
class DisasmDataManager internal constructor(
    private val startAddress: Long,
    private val endAddress: Long,
    private val fetchInstructions: suspend (Long, Int) -> Result<List<DisasmInstruction>>,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val addressOverview: AddressOverview = AddressOverview.Empty
) {
    constructor(
        startAddress: Long,
        endAddress: Long,
        repository: DisasmRepository,
        addressOverview: AddressOverview = AddressOverview.Empty
    ) : this(startAddress, endAddress, repository::getDisassembly, addressOverview = addressOverview)

    companion object {
        const val INSTRUCTIONS_PER_CHUNK = 100
        const val AVG_INSTRUCTION_SIZE = 4
        private const val CACHE_MAX_SIZE = 50
        const val MAX_LOADED_INSTRUCTIONS = 3000

        /** Unlike nearest-index lookup, holes and addresses outside the window are cache misses. */
        fun coveredIndex(instructions: List<DisasmInstruction>, addr: Long): Int {
            // A cold scrollbar position is usually outside *every* LRU chunk. Reject it using
            // the endpoints instead of binary-searching the rows of each unrelated chunk.
            if (instructions.isEmpty() || addr < instructions.first().addr) return -1
            val last = instructions.last()
            if (addr >= last.addr) return if (covers(last, addr)) instructions.lastIndex else -1
            val found = instructions.binarySearchBy(addr) { it.addr }
            if (found >= 0) return found
            val index = -found - 2
            val previous = instructions.getOrNull(index) ?: return -1
            return if (covers(previous, addr)) index else -1
        }

        // Called with addr >= instr.addr. Subtraction remains valid at Long.MAX_VALUE.
        private fun covers(instr: DisasmInstruction, addr: Long): Boolean =
            addr.toULong() - instr.addr.toULong() < instr.size.coerceAtLeast(1).toULong()

        /** Allocation-free binary lookup, also usable against the exact snapshot rendered by UI. */
        fun findClosestIndex(instructions: List<DisasmInstruction>, addr: Long): Int {
            if (instructions.isEmpty()) return -1
            val found = instructions.binarySearchBy(addr) { it.addr }
            if (found >= 0) return found
            val next = -found - 1
            if (next == 0) return 0
            if (next == instructions.size) return next - 1
            // Unsigned subtraction also handles distances spanning the signed Long boundary.
            val before = addr.toULong() - instructions[next - 1].addr.toULong()
            val after = instructions[next].addr.toULong() - addr.toULong()
            return if (before < after) next - 1 else next
        }
    }

    @Volatile private var data = DisasmSnapshot()
    @Volatile private var generation = 0L
    @Volatile var onChunkLoaded: ((Long) -> Unit)? = null

    // Only brief bookkeeping/publication uses this monitor; parsing/merging stays outside it.
    private val stateLock = Any()
    private val mergeMutex = Mutex()
    private val cache = object : LinkedHashMap<Long, DisasmSnapshot>(CACHE_MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, DisasmSnapshot>?): Boolean =
            size > CACHE_MAX_SIZE
    }
    private val loading = mutableMapOf<Long, Any>()

    val estimatedTotalInstructions: Int
        get() {
            val range = (endAddress - startAddress).coerceAtLeast(0)
            return (range / AVG_INSTRUCTION_SIZE + if (range % AVG_INSTRUCTION_SIZE == 0L) 0 else 1)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    val viewStartAddress: Long get() = startAddress
    val viewEndAddress: Long get() = endAddress
    val loadedInstructionCount: Int get() = data.instructions.size
    val jumpToIndexMap: Map<Long, Int> get() = data.jumpToIndex
    val targetToIndexMap: Map<Long, Int> get() = data.targetToIndex

    fun getDataSnapshot(): DisasmSnapshot = data
    fun getSnapshot(): List<DisasmInstruction> = data.instructions
    fun getInstructionAt(index: Int): DisasmInstruction? = data.instructions.getOrNull(index)
    fun getAddressAt(index: Int): Long? = getInstructionAt(index)?.addr
    fun findIndexByAddress(addr: Long): Int = data.instructions.binarySearchBy(addr) { it.addr }.let {
        if (it >= 0) it else -1
    }
    fun getInstructionAtAddress(addr: Long): DisasmInstruction? {
        val snapshot = data.instructions
        return snapshot.getOrNull(snapshot.binarySearchBy(addr) { it.addr })
    }
    fun findClosestIndex(addr: Long): Int = findClosestIndex(data.instructions, addr)
    fun estimateIndexForAddress(addr: Long): Int = findClosestIndex(addr).coerceAtLeast(0)
    fun estimateAddressForIndex(index: Int): Long = getAddressAt(index)
        ?: (startAddress + index.coerceAtLeast(0).toLong() * AVG_INSTRUCTION_SIZE).coerceIn(startAddress, endAddress)

    /** A nearby instruction alone is not enough: the address must actually be covered. */
    fun isAddressRangeLoaded(addr: Long): Boolean = coveredIndex(data.instructions, addr) >= 0

    /**
     * Synchronous, bounded preview lookup (at most 50 binary searches). Cached jump maps are
     * built on the worker, so a pointer event only swaps an immutable snapshot, never merges it.
     */
    fun showCachedAddress(addr: Long): Boolean {
        synchronized(stateLock) {
            if (isAddressRangeLoaded(addr)) return true
            var key: Long? = null
            // Prefer the most recently used covering chunk. Touch its LRU entry after iteration.
            for ((address, chunk) in cache) {
                if (coveredIndex(chunk.instructions, addr) >= 0) key = address
            }
            val cached = cache[key ?: return false] ?: return false
            generation++
            loading.clear()
            data = cached
        }
        onChunkLoaded?.invoke(addr)
        return true
    }

    /** Invalidate outstanding requests without throwing away reusable chunks on every navigation. */
    fun prepareForJump() = synchronized(stateLock) {
        generation++
        loading.clear()
    }

    suspend fun loadChunkAroundAddress(addr: Long) {
        if (!isAddressRangeLoaded(addr)) loadFromAddress(addr)
    }

    suspend fun loadFromAddress(startAddr: Long) = loadFromAddress(startAddr, generation, replace = false)

    private suspend fun loadFromAddress(startAddr: Long, gen: Long, replace: Boolean) = withContext(workerDispatcher) {
        val token = Any()
        val cached = synchronized(stateLock) {
            if (generation != gen || loading.containsKey(startAddr)) return@withContext
            loading[startAddr] = token
            cache[startAddr]
        }
        try {
            val instructions = cached?.instructions ?: fetchInstructions(startAddr, INSTRUCTIONS_PER_CHUNK).getOrThrow()
                .distinctBy { it.addr }.sortedBy { it.addr }
            currentCoroutineContext().ensureActive()
            if (instructions.isEmpty()) return@withContext
            // These model properties are lazy. Warm them here so first appearance of a row
            // doesn't run formatting/string building during a scroll frame on Main.
            if (cached == null) instructions.forEach {
                it.displayAddress
                it.displayBytes
                it.inlineComment
            }
            val chunk = cached ?: buildSnapshot(instructions)
            val changed = mergeMutex.withLock {
                if (generation != gen) return@withLock false
                val current = data
                val merged = mergeInstructions(if (replace) emptyList() else current.instructions, instructions)
                val next = when {
                    merged === current.instructions -> current
                    merged === instructions -> chunk
                    else -> buildSnapshot(merged)
                }
                currentCoroutineContext().ensureActive()
                synchronized(stateLock) {
                    if (generation != gen) return@synchronized false
                    cache[startAddr] = chunk
                    data = next
                    next !== current
                }
            }
            if (changed && generation == gen) onChunkLoaded?.invoke(startAddr)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the last good window on an R2 failure; the next edge/jump can retry.
        } finally {
            // No suspension in cleanup. An old request must never remove a newer request's token.
            synchronized(stateLock) {
                if (loading[startAddr] === token) loading.remove(startAddr)
            }
        }
    }

    /** Extend the existing window instead of decoding overlapping chunks at every visible row. */
    suspend fun preloadAround(addr: Long, rangeChunks: Int = 2) {
        val gen = generation
        if (!isAddressRangeLoaded(addr)) loadFromAddress(addr, gen, replace = false)
        val desiredRows = rangeChunks.toLong() * INSTRUCTIONS_PER_CHUNK
        repeat(rangeChunks) {
            if (generation != gen) return
            val window = data.instructions
            val index = coveredIndex(window, addr)
            if (index < 0) return
            // Don't grow the remote edges of a large, already warm window at every dwell.
            val needBefore = index < desiredRows
            val needAfter = window.lastIndex - index < desiredRows
            if (!needBefore && !needAfter) return
            if (needBefore) loadMore(false, gen)
            if (needAfter) loadMore(true, gen)
        }
    }

    suspend fun loadMore(forward: Boolean) = loadMore(forward, generation)

    private suspend fun loadMore(forward: Boolean, gen: Long) {
        if (generation != gen) return
        val snapshot = data.instructions
        if (snapshot.isEmpty()) return
        if (forward) {
            val last = snapshot.last()
            val next = last.addr + last.size.coerceAtLeast(1)
            if (next < endAddress) loadFromAddress(next, gen, replace = false)
        } else {
            val first = snapshot.first().addr
            if (first <= startAddress) return
            val previous = (first - INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE).coerceAtLeast(startAddress)
            loadFromAddress(previous, gen, replace = false)
        }
    }

    /** O(n + m) merge; existing decoded instructions win at duplicate addresses. */
    private fun mergeInstructions(current: List<DisasmInstruction>, incoming: List<DisasmInstruction>): List<DisasmInstruction> {
        if (current.isEmpty()) return if (incoming.size <= MAX_LOADED_INSTRUCTIONS) incoming else incoming.take(MAX_LOADED_INSTRUCTIONS)
        val gap = INSTRUCTIONS_PER_CHUNK.toLong() * AVG_INSTRUCTION_SIZE * 2
        if (incoming.last().addr < current.first().addr - gap || incoming.first().addr > current.last().addr + gap) {
            return if (incoming.size <= MAX_LOADED_INSTRUCTIONS) incoming else incoming.take(MAX_LOADED_INSTRUCTIONS)
        }
        val merged = ArrayList<DisasmInstruction>(current.size + incoming.size)
        var old = 0
        var new = 0
        var added = false
        while (old < current.size || new < incoming.size) {
            when {
                new == incoming.size -> merged.add(current[old++])
                old == current.size -> { merged.add(incoming[new++]); added = true }
                current[old].addr < incoming[new].addr -> merged.add(current[old++])
                current[old].addr > incoming[new].addr -> { merged.add(incoming[new++]); added = true }
                else -> { merged.add(current[old++]); new++ }
            }
        }
        if (!added) return current
        return if (merged.size <= MAX_LOADED_INSTRUCTIONS) merged
        else if (incoming.first().addr < current.first().addr) merged.take(MAX_LOADED_INSTRUCTIONS)
        else merged.takeLast(MAX_LOADED_INSTRUCTIONS)
    }

    private fun buildSnapshot(instructions: List<DisasmInstruction>): DisasmSnapshot {
        val jumps = mutableMapOf<Long, Int>()
        val targets = mutableMapOf<Long, Int>()
        var counter = 1
        for (instr in instructions) {
            val isJump = instr.type == "jmp" || instr.type == "cjmp" || instr.type == "ujmp"
            if (isJump && instr.jump != null && instr.fcnAddr > 0 && instr.jump in instr.fcnAddr..instr.fcnLast) {
                jumps[instr.addr] = counter
                targets[instr.jump] = counter++
            }
        }
        return DisasmSnapshot(instructions, jumps, targets)
    }

    suspend fun resetAndLoadAround(addr: Long) {
        clearCache()
        loadAndFindIndex(addr)
    }

    suspend fun loadAndFindIndex(addr: Long, preloadPrevious: Boolean = true): Int {
        if (showCachedAddress(addr)) return findClosestIndex(addr)
        val gen = generation
        // Keep the old window visible until the new one is ready, and never merge across a far jump.
        loadFromAddress(addr, gen, replace = true)
        if (generation != gen) return -1
        if (preloadPrevious && isAddressRangeLoaded(addr)) {
            // Centered navigation wants preceding rows; the scrollbar publishes its top row
            // first and preloads both neighbors separately after releasing the UI target.
            val previous = (addr - INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE / 2).coerceAtLeast(startAddress)
            if (previous < addr) loadFromAddress(previous, gen, replace = false)
        }
        return if (generation == gen && isAddressRangeLoaded(addr)) findClosestIndex(addr) else -1
    }

    suspend fun jumpToAddress(addr: Long): Int {
        prepareForJump()
        return loadAndFindIndex(addr)
    }

    fun clearCache() = synchronized(stateLock) {
        generation++
        loading.clear()
        cache.clear()
        data = DisasmSnapshot()
    }

    fun getCacheStats(): String = synchronized(stateLock) {
        "Loaded: ${data.instructions.size} instructions, Cache: ${cache.size}/$CACHE_MAX_SIZE chunks"
    }
}

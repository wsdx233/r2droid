package top.wsdx233.r2droid.feature.disasm.data

import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wsdx233.r2droid.core.data.model.DisasmInstruction

/**
 * Manages disassembly data with chunk-based caching for virtualized scrolling.
 *
 * Core design:
 * - File is divided into chunks based on instruction count
 * - LRU cache holds recently accessed chunks
 * - Data is loaded on-demand based on visible rows
 * - Uses estimated total instructions for scrollbar (can be refined as user scrolls)
 *
 * Thread safety: allInstructions is a @Volatile immutable list reference,
 * swapped atomically in mergeInstructions. Reads always see a consistent snapshot.
 */
class DisasmDataManager(
    private val startAddress: Long,
    private val endAddress: Long,
    private val repository: DisasmRepository
) {
    private val addressRange: Long = endAddress - startAddress
    companion object {
        // Instructions per chunk to fetch
        const val INSTRUCTIONS_PER_CHUNK = 100

        // Average instruction size estimate (varies by arch, 4 bytes for ARM, ~3-5 for x86)
        const val AVG_INSTRUCTION_SIZE = 4

        // Cache up to 50 chunks = ~5000 instructions max
        private const val CACHE_MAX_SIZE = 50
    }

    // Sorted list of all loaded instructions (by address)
    // Volatile immutable list - swapped atomically to avoid concurrent modification
    @Volatile
    private var allInstructions: List<DisasmInstruction> = emptyList()
    
    // LRU Cache: key = chunk start address, value = list of instructions
    private val cache = LruCache<Long, List<DisasmInstruction>>(CACHE_MAX_SIZE)
    
    // Track chunks currently being loaded to avoid duplicate requests
    private val loadingSet = mutableSetOf<Long>()
    private val loadingMutex = Mutex()
    
    // Callback to notify when a chunk is loaded (for UI refresh)
    var onChunkLoaded: ((Long) -> Unit)? = null
    
    // Estimated total instruction count based on address range
    val estimatedTotalInstructions: Int
        get() = ((addressRange + AVG_INSTRUCTION_SIZE - 1) / AVG_INSTRUCTION_SIZE).toInt()
    
    // Get the start address of the valid disasm range
    val viewStartAddress: Long
        get() = startAddress
    
    // Get the end address of the valid disasm range
    val viewEndAddress: Long
        get() = endAddress
    
    // Current actual loaded instructions count
    val loadedInstructionCount: Int
        get() = allInstructions.size

    /**
     * Get a consistent snapshot of all loaded instructions.
     * Use this to avoid reading the volatile field multiple times in a single layout pass.
     */
    fun getSnapshot(): List<DisasmInstruction> = allInstructions
    
    /**
     * Get instruction at a specific index in the virtual list.
     * Returns null if the instruction is not yet loaded.
     */
    fun getInstructionAt(index: Int): DisasmInstruction? {
        val snapshot = allInstructions
        if (index < 0 || index >= snapshot.size) return null
        return snapshot.getOrNull(index)
    }
    
    /**
     * Get the address for a specific index.
     * Used for calculating scrollbar position.
     */
    fun getAddressAt(index: Int): Long? {
        return getInstructionAt(index)?.addr
    }
    
    /**
     * Find the index of an instruction by address.
     * Returns -1 if not found.
     */
    fun findIndexByAddress(addr: Long): Int {
        val snapshot = allInstructions
        return snapshot.indexOfFirst { it.addr == addr }
    }
    
    /**
     * Get instruction by address.
     */
    fun getInstructionAtAddress(addr: Long): DisasmInstruction? {
        val snapshot = allInstructions
        return snapshot.find { it.addr == addr }
    }
    
    /**
     * Find the closest index to an address.
     * If the exact address is not found, returns the index of the closest instruction.
     */
    fun findClosestIndex(addr: Long): Int {
        val snapshot = allInstructions
        if (snapshot.isEmpty()) return -1

        // Binary search for efficiency on sorted list
        var low = 0
        var high = snapshot.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midAddr = snapshot[mid].addr
            when {
                midAddr < addr -> low = mid + 1
                midAddr > addr -> high = mid - 1
                else -> return mid
            }
        }
        // low is the insertion point; check neighbors for closest
        val candidates = listOfNotNull(
            snapshot.getOrNull(low),
            snapshot.getOrNull(low - 1)
        )
        if (candidates.isEmpty()) return snapshot.size - 1
        val closest = candidates.minByOrNull { kotlin.math.abs(it.addr - addr) }!!
        return snapshot.indexOf(closest)
    }
    
    /**
     * Estimate the index in the virtual list for a given address.
     * Used for fast scrollbar jumping.
     */
    fun estimateIndexForAddress(addr: Long): Int {
        val snapshot = allInstructions
        if (snapshot.isEmpty()) return 0

        // If we have loaded data, try to find exact match
        val exactIndex = snapshot.indexOfFirst { it.addr == addr }
        if (exactIndex >= 0) return exactIndex

        val firstAddr = snapshot.first().addr
        val lastAddr = snapshot.last().addr

        // Clamp to loaded data boundaries
        if (addr <= firstAddr) return 0
        if (addr >= lastAddr) return snapshot.size - 1

        // Linear interpolation within loaded range
        val range = lastAddr - firstAddr
        if (range > 0) {
            val ratio = (addr - firstAddr).toDouble() / range
            return (ratio * (snapshot.size - 1)).toInt().coerceIn(0, snapshot.size - 1)
        }

        return 0
    }
    
    /**
     * Convert a virtual index to approximate address.
     * Used for scrollbar position calculation.
     */
    fun estimateAddressForIndex(index: Int): Long {
        if (index < 0) return 0L
        val snapshot = allInstructions

        // If we have the instruction, return exact address
        if (index < snapshot.size) {
            return snapshot.getOrNull(index)?.addr ?: 0L
        }

        // Estimate based on index from start address
        return (startAddress + index.toLong() * AVG_INSTRUCTION_SIZE).coerceIn(startAddress, endAddress)
    }
    
    /**
     * Check if instructions around an address are loaded.
     */
    fun isAddressRangeLoaded(addr: Long): Boolean {
        val snapshot = allInstructions
        return snapshot.any {
            kotlin.math.abs(it.addr - addr) < (INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE)
        }
    }
    
    /**
     * Load instructions around an address if not already loaded.
     */
    suspend fun loadChunkAroundAddress(addr: Long) {
        // Align to chunk boundary (approximate)
        val chunkStart = (addr / (INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE)) * 
                        (INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE)
        
        // Already cached
        if (cache.get(chunkStart) != null) return
        
        // Check if already loading
        loadingMutex.withLock {
            if (loadingSet.contains(chunkStart)) return
            loadingSet.add(chunkStart)
        }
        
        try {
            // We need to find a valid starting address
            // First, try to load from the given address
            val result = repository.getDisassembly(addr, INSTRUCTIONS_PER_CHUNK)
            result.onSuccess { instructions ->
                if (instructions.isNotEmpty()) {
                    cache.put(chunkStart, instructions)
                    mergeInstructions(instructions)
                    onChunkLoaded?.invoke(chunkStart)
                }
            }
        } finally {
            loadingMutex.withLock {
                loadingSet.remove(chunkStart)
            }
        }
    }
    
    /**
     * Load instructions starting from a specific address.
     */
    suspend fun loadFromAddress(startAddr: Long, forward: Boolean = true) {
        // Check if already loading this region
        loadingMutex.withLock {
            if (loadingSet.contains(startAddr)) return
            loadingSet.add(startAddr)
        }
        
        try {
            val result = repository.getDisassembly(startAddr, INSTRUCTIONS_PER_CHUNK)
            result.onSuccess { instructions ->
                if (instructions.isNotEmpty()) {
                    cache.put(startAddr, instructions)
                    mergeInstructions(instructions)
                    onChunkLoaded?.invoke(startAddr)
                }
            }
        } finally {
            loadingMutex.withLock {
                loadingSet.remove(startAddr)
            }
        }
    }
    
    /**
     * Preload chunks around the given address.
     */
    suspend fun preloadAround(addr: Long, rangeChunks: Int = 2) {
        // Load the current chunk first
        loadChunkAroundAddress(addr)
        
        // Find current position in loaded list
        val currentIndex = findClosestIndex(addr)
        if (currentIndex < 0) return
        
        // Calculate estimated addresses for prev/next chunks
        val avgChunkSize = INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE
        
        // Preload previous chunks
        for (i in 1..rangeChunks) {
            val prevAddr = (addr - i * avgChunkSize).coerceAtLeast(startAddress)
            if (prevAddr >= startAddress) {
                loadChunkAroundAddress(prevAddr)
            }
        }
        
        // Preload next chunks
        for (i in 1..rangeChunks) {
            val nextAddr = addr + i * avgChunkSize
            if (nextAddr < endAddress) {
                loadChunkAroundAddress(nextAddr)
            }
        }
    }
    
    /**
     * Load more instructions from the end or beginning.
     */
    suspend fun loadMore(forward: Boolean) {
        val snapshot = allInstructions
        if (snapshot.isEmpty()) return

        if (forward) {
            val lastInstr = snapshot.lastOrNull() ?: return
            val nextAddr = lastInstr.addr + lastInstr.size
            if (nextAddr < endAddress) {
                loadFromAddress(nextAddr, true)
            }
        } else {
            val firstInstr = snapshot.firstOrNull() ?: return
            if (firstInstr.addr <= startAddress) return

            // Go back by estimated chunk size
            val prevAddr = (firstInstr.addr - INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE)
                .coerceAtLeast(startAddress)
            loadFromAddress(prevAddr, false)
        }
    }
    
    /**
     * Merge new instructions into the sorted list.
     * Uses atomic reference swap - readers always see a consistent snapshot.
     */
    @Synchronized
    private fun mergeInstructions(newInstructions: List<DisasmInstruction>) {
        val current = allInstructions
        val combined = (current + newInstructions)
            .distinctBy { it.addr }
            .sortedBy { it.addr }
        // Atomic swap - no clear+addAll race condition
        allInstructions = combined
    }
    
    /**
     * Reset and load initial data around an address.
     */
    suspend fun resetAndLoadAround(addr: Long) {
        allInstructions = emptyList()
        cache.evictAll()
        
        // Load centered around address
        // First, seek back a bit to get context
        val startAddr = (addr - INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE / 2)
            .coerceAtLeast(startAddress)
        
        loadFromAddress(startAddr, true)
        
        // If we didn't get the target address, load from target too
        if (!allInstructions.any { it.addr == addr }) {
            loadFromAddress(addr, true)
        }
    }
    
    /**
     * Load data around an address and return the closest index.
     * This ensures data is available before attempting to scroll.
     */
    suspend fun loadAndFindIndex(addr: Long): Int {
        // If already loaded nearby, just find the index
        val existingIndex = findClosestIndex(addr)
        if (existingIndex >= 0) {
            val existingAddr = getAddressAt(existingIndex)
            if (existingAddr != null && kotlin.math.abs(existingAddr - addr) < INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE) {
                return existingIndex
            }
        }

        // Load data at the target address
        loadFromAddress(addr, true)

        // Also try loading slightly before to get context
        val prevAddr = (addr - INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE / 2)
            .coerceAtLeast(startAddress)
        if (prevAddr < addr) {
            loadFromAddress(prevAddr, true)
        }

        return findClosestIndex(addr).coerceAtLeast(0)
    }

    /**
     * Clear all cached data.
     */
    fun clearCache() {
        allInstructions = emptyList()
        cache.evictAll()
    }
    
    /**
     * Get cache statistics for debugging.
     */
    fun getCacheStats(): String {
        return "Loaded: ${allInstructions.size} instructions, Cache: ${cache.size()}/$CACHE_MAX_SIZE chunks"
    }
}

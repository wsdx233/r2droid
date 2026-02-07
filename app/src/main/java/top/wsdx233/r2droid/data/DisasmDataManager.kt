package top.wsdx233.r2droid.data

import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wsdx233.r2droid.data.model.DisasmInstruction
import top.wsdx233.r2droid.data.repository.ProjectRepository
import java.util.Collections

/**
 * Manages disassembly data with chunk-based caching for virtualized scrolling.
 * 
 * Core design:
 * - File is divided into chunks based on instruction count
 * - LRU cache holds recently accessed chunks
 * - Data is loaded on-demand based on visible rows
 * - Uses estimated total instructions for scrollbar (can be refined as user scrolls)
 */
class DisasmDataManager(
    private val startAddress: Long,
    private val endAddress: Long,
    private val repository: ProjectRepository
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
    // This is the canonical source of truth for virtualized display
    private val allInstructions = Collections.synchronizedList(mutableListOf<DisasmInstruction>())
    
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
     * Get instruction at a specific index in the virtual list.
     * Returns null if the instruction is not yet loaded.
     */
    fun getInstructionAt(index: Int): DisasmInstruction? {
        if (index < 0 || index >= allInstructions.size) return null
        return try {
            allInstructions[index]
        } catch (e: Exception) {
            null
        }
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
        return allInstructions.indexOfFirst { it.addr == addr }
    }
    
    /**
     * Find the closest index to an address.
     * If the exact address is not found, returns the index of the closest instruction.
     */
    fun findClosestIndex(addr: Long): Int {
        if (allInstructions.isEmpty()) return -1
        
        var closestIndex = -1
        var closestDiff = Long.MAX_VALUE
        
        allInstructions.forEachIndexed { index, instr ->
            val diff = kotlin.math.abs(instr.addr - addr)
            if (diff < closestDiff) {
                closestDiff = diff
                closestIndex = index
            }
        }
        
        return closestIndex
    }
    
    /**
     * Estimate the index in the virtual list for a given address.
     * Used for fast scrollbar jumping.
     */
    fun estimateIndexForAddress(addr: Long): Int {
        // If we have loaded data, try to find exact match
        val exactIndex = findIndexByAddress(addr)
        if (exactIndex >= 0) return exactIndex
        
        // If we have some instructions, use interpolation
        if (allInstructions.isNotEmpty()) {
            val firstAddr = allInstructions.first().addr
            val lastAddr = allInstructions.last().addr
            
            if (addr <= firstAddr) return 0
            if (addr >= lastAddr) return allInstructions.size - 1
            
            // Linear interpolation
            val range = lastAddr - firstAddr
            if (range > 0) {
                val ratio = (addr - firstAddr).toDouble() / range
                return (ratio * allInstructions.size).toInt().coerceIn(0, allInstructions.size - 1)
            }
        }
        
        // Fallback: estimate based on address offset from start
        return (((addr - startAddress).toDouble() / AVG_INSTRUCTION_SIZE.toDouble())).toInt().coerceIn(0, Int.MAX_VALUE)
    }
    
    /**
     * Convert a virtual index to approximate address.
     * Used for scrollbar position calculation.
     */
    fun estimateAddressForIndex(index: Int): Long {
        if (index < 0) return 0L
        
        // If we have the instruction, return exact address
        if (index < allInstructions.size) {
            return allInstructions.getOrNull(index)?.addr ?: 0L
        }
        
        // Estimate based on index from start address
        return (startAddress + index.toLong() * AVG_INSTRUCTION_SIZE).coerceIn(startAddress, endAddress)
    }
    
    /**
     * Check if instructions around an address are loaded.
     */
    fun isAddressRangeLoaded(addr: Long): Boolean {
        return allInstructions.any { 
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
        if (allInstructions.isEmpty()) return
        
        if (forward) {
            val lastInstr = allInstructions.lastOrNull() ?: return
            val nextAddr = lastInstr.addr + lastInstr.size
            if (nextAddr < endAddress) {
                loadFromAddress(nextAddr, true)
            }
        } else {
            val firstInstr = allInstructions.firstOrNull() ?: return
            if (firstInstr.addr <= 0) return
            
            // Go back by estimated chunk size
            val prevAddr = (firstInstr.addr - INSTRUCTIONS_PER_CHUNK * AVG_INSTRUCTION_SIZE)
                .coerceAtLeast(startAddress)
            loadFromAddress(prevAddr, false)
        }
    }
    
    /**
     * Merge new instructions into the sorted list.
     */
    @Synchronized
    private fun mergeInstructions(newInstructions: List<DisasmInstruction>) {
        // Add new instructions
        val combined = (allInstructions + newInstructions)
            .distinctBy { it.addr }
            .sortedBy { it.addr }
        
        allInstructions.clear()
        allInstructions.addAll(combined)
    }
    
    /**
     * Reset and load initial data around an address.
     */
    suspend fun resetAndLoadAround(addr: Long) {
        allInstructions.clear()
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
     * Clear all cached data.
     */
    fun clearCache() {
        allInstructions.clear()
        cache.evictAll()
    }
    
    /**
     * Get cache statistics for debugging.
     */
    fun getCacheStats(): String {
        return "Loaded: ${allInstructions.size} instructions, Cache: ${cache.size()}/${CACHE_MAX_SIZE} chunks"
    }
}

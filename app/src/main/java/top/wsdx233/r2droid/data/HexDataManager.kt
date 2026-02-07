package top.wsdx233.r2droid.data

import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wsdx233.r2droid.data.repository.ProjectRepository

/**
 * Manages hex data with chunk-based caching for virtualized scrolling.
 * 
 * Core design:
 * - File is divided into chunks (default 4KB each)
 * - LRU cache holds recently accessed chunks
 * - Data is loaded on-demand based on visible rows
 */
class HexDataManager(
    private val startAddress: Long,
    private val endAddress: Long,
    private val repository: ProjectRepository
) {
    private val totalSize: Long = endAddress - startAddress
    companion object {
        // 4KB per chunk = 256 hex rows (each row is 16 bytes)
        const val CHUNK_SIZE = 4096
        const val BYTES_PER_ROW = 16
        const val ROWS_PER_CHUNK = CHUNK_SIZE / BYTES_PER_ROW // 256
        
        // Cache up to 100 chunks = ~400KB memory
        private const val CACHE_MAX_SIZE = 100
    }
    
    // LRU Cache: key = chunk start address, value = byte array
    private val cache = LruCache<Long, ByteArray>(CACHE_MAX_SIZE)
    
    // Track chunks currently being loaded to avoid duplicate requests
    private val loadingSet = mutableSetOf<Long>()
    private val loadingMutex = Mutex()
    
    // Callback to notify when a chunk is loaded (for UI refresh)
    var onChunkLoaded: ((Long) -> Unit)? = null
    
    /**
     * Calculate total number of rows in the file.
     */
    val totalRows: Int
        get() = ((totalSize + BYTES_PER_ROW - 1) / BYTES_PER_ROW).toInt()
    
    /**
     * Get the start address of the hex view (minimum virtual address).
     */
    val viewStartAddress: Long
        get() = startAddress
    
    /**
     * Get the end address of the hex view (maximum virtual address).
     */
    val viewEndAddress: Long
        get() = endAddress
    
    /**
     * Get the chunk start address for a given address.
     */
    private fun getChunkStart(addr: Long): Long {
        return (addr / CHUNK_SIZE) * CHUNK_SIZE
    }
    
    /**
     * Get 16 bytes for a specific row index.
     * Returns null if the data is not cached yet.
     */
    fun getRowData(rowIndex: Int): ByteArray? {
        val addr = startAddress + rowIndex.toLong() * BYTES_PER_ROW
        if (addr >= endAddress) return null
        
        val chunkStart = getChunkStart(addr)
        val chunk = cache.get(chunkStart) ?: return null
        
        val localOffset = (addr - chunkStart).toInt()
        val bytesToRead = minOf(BYTES_PER_ROW, chunk.size - localOffset)
        if (localOffset >= chunk.size || bytesToRead <= 0) return null
        
        return chunk.copyOfRange(localOffset, localOffset + bytesToRead)
    }
    
    /**
     * Get the address for a specific row index.
     */
    fun getRowAddress(rowIndex: Int): Long {
        return startAddress + rowIndex.toLong() * BYTES_PER_ROW
    }
    
    /**
     * Convert an address to row index.
     */
    fun getRowIndexForAddress(addr: Long): Int {
        if (addr < startAddress) return 0
        if (addr >= endAddress) return totalRows - 1
        return ((addr - startAddress) / BYTES_PER_ROW).toInt()
    }
    
    /**
     * Check if a chunk is loaded.
     */
    fun isChunkLoaded(addr: Long): Boolean {
        val chunkStart = getChunkStart(addr)
        return cache.get(chunkStart) != null
    }
    
    /**
     * Load a chunk if not already loaded or loading.
     */
    suspend fun loadChunkIfNeeded(addr: Long) {
        val chunkStart = getChunkStart(addr)
        
        // Already cached
        if (cache.get(chunkStart) != null) return
        
        // Check if already loading
        loadingMutex.withLock {
            if (loadingSet.contains(chunkStart)) return
            loadingSet.add(chunkStart)
        }
        
        try {
            // Calculate actual bytes to read (handle boundaries)
            val effectiveEnd = minOf(chunkStart + CHUNK_SIZE, endAddress)
            val bytesToRead = (effectiveEnd - chunkStart).toInt()
            if (bytesToRead <= 0) return
            
            val result = repository.getHexDump(chunkStart, bytesToRead)
            result.onSuccess { bytes ->
                cache.put(chunkStart, bytes)
                onChunkLoaded?.invoke(chunkStart)
            }
        } finally {
            loadingMutex.withLock {
                loadingSet.remove(chunkStart)
            }
        }
    }
    
    /**
     * Preload chunks around the given address.
     * Useful for smooth scrolling - preloads adjacent chunks.
     */
    suspend fun preloadAround(addr: Long, rangeChunks: Int = 2) {
        val chunkStart = getChunkStart(addr)
        
        // Preload previous chunks
        for (i in 1..rangeChunks) {
            val prevChunk = chunkStart - i * CHUNK_SIZE
            if (prevChunk >= startAddress) {
                loadChunkIfNeeded(prevChunk)
            }
        }
        
        // Preload next chunks
        for (i in 1..rangeChunks) {
            val nextChunk = chunkStart + i * CHUNK_SIZE
            if (nextChunk < endAddress) {
                loadChunkIfNeeded(nextChunk)
            }
        }
    }
    
    /**
     * Clear all cached data.
     */
    fun clearCache() {
        cache.evictAll()
    }
    
    /**
     * Get cache statistics for debugging.
     */
    fun getCacheStats(): String {
        return "Cache: ${cache.size()}/${CACHE_MAX_SIZE} chunks"
    }
}

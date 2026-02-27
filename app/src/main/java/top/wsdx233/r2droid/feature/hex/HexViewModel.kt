package top.wsdx233.r2droid.feature.hex

/**
 * ViewModel for Hex Viewer.
 * Manages HexDataManager and hex-related interactions.
 */
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.core.data.model.Section
import top.wsdx233.r2droid.feature.hex.data.HexDataManager
import top.wsdx233.r2droid.feature.hex.data.HexRepository
import top.wsdx233.r2droid.util.R2PipeManager
import javax.inject.Inject

sealed interface HexEvent {
    data class LoadHex(val sections: List<Section>, val currentFilePath: String?, val currentOffset: Long) : HexEvent
    data class LoadHexChunk(val address: Long) : HexEvent
    data class PreloadHex(val address: Long) : HexEvent
    data class WriteHex(val address: Long, val hex: String) : HexEvent
    data class WriteString(val address: Long, val text: String) : HexEvent
    data class WriteAsm(val address: Long, val asm: String) : HexEvent
    object RefreshData : HexEvent
    object Reset : HexEvent
}

@HiltViewModel
class HexViewModel @Inject constructor(
    private val hexRepository: HexRepository
) : ViewModel() {

    // HexDataManager for virtualized hex viewing
    var hexDataManager: HexDataManager? = null
        private set

    private val _hexDataManagerState = MutableStateFlow<HexDataManager?>(null)
    val hexDataManagerState = _hexDataManagerState.asStateFlow()

    // Cache version counter - increment to trigger UI recomposition when chunks load
    private val _hexCacheVersion = MutableStateFlow(0)
    val hexCacheVersion: StateFlow<Int> = _hexCacheVersion.asStateFlow()

    // Event to notify that data has been modified (so other views like Disasm can refresh)
    private val _dataModifiedEvent = MutableStateFlow(0L) // Timestamp/Sequence
    val dataModifiedEvent: StateFlow<Long> = _dataModifiedEvent.asStateFlow()

    fun onEvent(event: HexEvent) {
        when (event) {
            is HexEvent.LoadHex -> loadHex(event.sections, event.currentFilePath, event.currentOffset)
            is HexEvent.LoadHexChunk -> loadHexChunkForAddress(event.address)
            is HexEvent.PreloadHex -> preloadHexAround(event.address)
            is HexEvent.WriteHex -> writeHex(event.address, event.hex)
            is HexEvent.WriteString -> writeString(event.address, event.text)
            is HexEvent.WriteAsm -> writeAsm(event.address, event.asm)
            is HexEvent.RefreshData -> refreshData()
            is HexEvent.Reset -> reset()
        }
    }

    // 当前数据管理器对应的会话 ID，用于检测会话变更
    private var currentSessionId: Int = -1

    /**
     * 重置所有数据，用于切换项目时清理旧数据。
     */
    fun reset() {
        hexDataManager = null
        _hexDataManagerState.value = null
        _hexCacheVersion.value = 0
        currentSessionId = -1
    }

    /**
     * Initialize hex viewer with virtualization.
     * Uses Section info to calculate virtual address range.
     */
    fun loadHex(sections: List<Section>, currentFilePath: String?, currentOffset: Long) {
        // 检测会话变更，如果是新项目则重置旧数据
        val newSessionId = R2PipeManager.sessionId
        if (newSessionId != currentSessionId) {
            reset()
            currentSessionId = newSessionId
        }
        if (hexDataManager != null) return // Already initialized

        viewModelScope.launch {
            var startAddress = 0L
            var endAddress = 0L

            if (sections.isNotEmpty()) {
                // Filter out non-mapped sections (vAddr=0 are typically debug/metadata sections)
                val mapped = sections.filter { it.vAddr != 0L }
                if (mapped.isNotEmpty()) {
                    startAddress = mapped.minOf { it.vAddr }
                    endAddress = mapped.maxOf { it.vAddr + maxOf(it.vSize, it.size) }
                }
            }

            // r2frida mode: use :dmj for address boundaries
            if (endAddress <= startAddress && R2PipeManager.isR2FridaSession) {
                try {
                    val raw = R2PipeManager.execute(":dmj").getOrNull()?.trim() ?: ""
                    val idx = raw.indexOfFirst { it == '[' }
                    val json = if (idx > 0) raw.substring(idx) else raw
                    if (json.startsWith("[")) {
                        val arr = org.json.JSONArray(json)
                        var minAddr = Long.MAX_VALUE
                        var maxAddr = 0L
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val base = java.lang.Long.decode(obj.optString("base", "0"))
                            val size = obj.optLong("size", 0)
                            if (base > 0 && size > 0) {
                                minAddr = minOf(minAddr, base)
                                maxAddr = maxOf(maxAddr, base + size)
                            }
                        }
                        if (maxAddr > minAddr && minAddr != Long.MAX_VALUE) {
                            startAddress = minAddr
                            endAddress = maxAddr
                        }
                    }
                } catch (_: Exception) {}
            }

            // Fallback if sections are empty or invalid
            if (endAddress <= startAddress) {
                // Try Java File API as fallback (file offset based)
                currentFilePath?.let { path ->
                    try {
                        val file = java.io.File(path)
                        if (file.exists() && file.isFile) {
                            startAddress = 0L
                            endAddress = file.length()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Final fallback - use a reasonable default range
            if (endAddress <= startAddress) {
                startAddress = 0L
                endAddress = 1024L * 1024L // 1MB default
            }

            // Create HexDataManager with virtual address range
            val manager = HexDataManager(startAddress, endAddress, hexRepository).apply {
                onChunkLoaded = { _ ->
                    // Increment version to trigger recomposition
                    _hexCacheVersion.value++
                }
            }
            hexDataManager = manager
            _hexDataManagerState.value = manager

            // Preload initial chunks around cursor
            hexDataManager?.loadChunkIfNeeded(currentOffset)
            hexDataManager?.preloadAround(currentOffset, 3)
            
            // Trigger initial update
            _hexCacheVersion.value++
        }
    }

    /**
     * Load a hex chunk for a specific address (called from UI during scroll).
     */
    fun loadHexChunkForAddress(addr: Long) {
        val manager = hexDataManager ?: return
        viewModelScope.launch {
            manager.loadChunkIfNeeded(addr)
        }
    }

    /**
     * Preload hex chunks around an address (called when user scrolls quickly).
     */
    fun preloadHexAround(addr: Long) {
        val manager = hexDataManager ?: return
        viewModelScope.launch {
            manager.preloadAround(addr, 2)
        }
    }

    fun writeHex(addr: Long, hex: String) {
        viewModelScope.launch {
            // "wx [hex] @ [addr]"
            hexRepository.writeHex(addr, hex)
            // Only invalidate the affected chunk and reload it immediately,
            // so data is ready before recomposition — no placeholder flash
            hexDataManager?.invalidateAndReload(addr)
            _hexCacheVersion.value++

            // Notify others
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }

    fun writeString(addr: Long, text: String) {
        viewModelScope.launch {
            // "w [text] @ [addr]"
            hexRepository.writeString(addr, text)
            
            hexDataManager?.clearCache()
            _hexCacheVersion.value++
            
            // Notify others
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }
    
    fun writeAsm(addr: Long, asm: String) {
        viewModelScope.launch {
            // "wa [asm] @ [addr]"
            val escaped = asm.replace("\"", "\\\"")
            top.wsdx233.r2droid.util.R2PipeManager.execute("wa $escaped @ $addr")
            
            hexDataManager?.clearCache()
            _hexCacheVersion.value++
            
            // Notify others
            _dataModifiedEvent.value = System.currentTimeMillis()
        }
    }
    
    /**
     * Called when other modules modify data (e.g. Disasm writes asm)
     */
    fun refreshData() {
        hexDataManager?.clearCache()
        _hexCacheVersion.value++
    }
}

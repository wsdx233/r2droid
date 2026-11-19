package top.wsdx233.r2droid.feature.hex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.Section
import top.wsdx233.r2droid.feature.hex.data.HexDataManager
import top.wsdx233.r2droid.feature.hex.data.HexRepository
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.util.ViewerByteEdit
import top.wsdx233.r2droid.util.writeViewerBytes
import java.io.File
import javax.inject.Inject

sealed interface HexEvent {
    data class LoadHex(val sections: List<Section>, val currentFilePath: String?, val currentOffset: Long) : HexEvent
    data class WriteHex(val session: R2PipeManager.R2Session, val address: Long, val hex: String) : HexEvent
    data class WriteString(val session: R2PipeManager.R2Session, val address: Long, val text: String) : HexEvent
    data class WriteAsm(val session: R2PipeManager.R2Session, val address: Long, val asm: String) : HexEvent
    object RefreshData : HexEvent
    object Reset : HexEvent
}

@HiltViewModel
class HexViewModel @Inject constructor(private val hexRepository: HexRepository) : ViewModel() {
    private val _hexDataManagerState = MutableStateFlow<HexDataManager?>(null)
    val hexDataManagerState = _hexDataManagerState.asStateFlow()
    val hexDataManager: HexDataManager?
        get() = _hexDataManagerState.value.takeIf { selection?.isActive() == true }

    private val _dataModifiedEvent = MutableStateFlow(0L)
    val dataModifiedEvent: StateFlow<Long> = _dataModifiedEvent.asStateFlow()

    private class Selection(val session: R2PipeManager.R2Session, val generation: Int) {
        fun isActive() = session.pipe === R2PipeManager.activeSessionPipe && generation == R2PipeManager.sessionId
    }

    private var selection: Selection? = null
    private var initialLoad: Job? = null

    init {
        viewModelScope.launch {
            // A reconnect can replace the pipe without changing the drawer entry ID.
            combine(R2PipeManager.activeSessionId, R2PipeManager.sessions) { _, _ -> Unit }.collect {
                if (selection?.isActive() == false) reset()
            }
        }
    }

    fun onEvent(event: HexEvent) {
        when (event) {
            is HexEvent.LoadHex -> loadHex(event.sections, event.currentFilePath, event.currentOffset)
            is HexEvent.WriteHex -> writeBytes(event.session, event.address, ViewerByteEdit.HEX, event.hex)
            is HexEvent.WriteString -> writeBytes(event.session, event.address, ViewerByteEdit.TEXT, event.text)
            is HexEvent.WriteAsm -> writeBytes(event.session, event.address, ViewerByteEdit.ASSEMBLY, event.asm)
            HexEvent.RefreshData -> refreshData()
            HexEvent.Reset -> reset()
        }
    }

    fun reset() {
        initialLoad?.cancel()
        initialLoad = null
        selection = null
        _hexDataManagerState.value?.close()
        _hexDataManagerState.value = null
    }

    override fun onCleared() {
        reset()
        super.onCleared()
    }

    fun loadHex(sections: List<Section>, currentFilePath: String?, currentOffset: Long) {
        val generation = R2PipeManager.sessionId
        val session = R2PipeManager.activeSession() ?: return
        val target = Selection(session, generation)
        if (!target.isActive()) return
        if (selection?.isActive() == true && (hexDataManager != null || initialLoad?.isActive == true)) return
        reset()
        selection = target
        initialLoad = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val (startAddress, endAddress) = resolveBounds(session, sections, currentFilePath, currentOffset)
            if (selection !== target || !target.isActive()) return@launch
            _hexDataManagerState.value = HexDataManager(startAddress, endAddress) { address, length ->
                if (!target.isActive()) throw CancellationException("Hex session changed")
                val result = hexRepository.getHexDump(session, address, length)
                if (!target.isActive()) throw CancellationException("Hex session changed")
                result
            }
        }
        initialLoad?.start()
    }

    private suspend fun resolveBounds(
        session: R2PipeManager.R2Session,
        sections: List<Section>,
        currentFilePath: String?,
        currentOffset: Long
    ): Pair<Long, Long> = withContext(Dispatchers.Default) {
        // vAddr=0 usually denotes non-mapped debug/metadata sections.
        var min = Long.MAX_VALUE
        var max = Long.MIN_VALUE
        for (section in sections) {
            val base = section.vAddr
            val size = maxOf(section.vSize, section.size)
            if (base == 0L || size <= 0L) continue
            min = minOf(min, base)
            max = maxOf(max, if (base > Long.MAX_VALUE - size) Long.MAX_VALUE else base + size)
        }
        if (max > min) return@withContext min to max

        if (session.isFridaSession) {
            fridaBounds(session, currentOffset)?.let { return@withContext it }
        }

        val localSize = currentFilePath?.let { path ->
            withContext(Dispatchers.IO) {
                try {
                    File(path).takeIf { it.isFile }?.length()
                } catch (_: SecurityException) {
                    null
                }
            }
        }
        if (localSize != null) return@withContext 0L to localSize

        // HTTP sessions can have no local path. Ask r2 rather than displaying a made-up extent.
        val info = R2PipeManager.execute(session, "ij", markDirty = false).getOrNull()
        val remoteSize = info?.let {
            runCatching { JSONObject(it).optJSONObject("core")?.optLong("size") }.getOrNull()
        }
        0L to (remoteSize ?: 0L).coerceAtLeast(0L)
    }

    private suspend fun fridaBounds(session: R2PipeManager.R2Session, currentOffset: Long): Pair<Long, Long>? {
        val raw = R2PipeManager.execute(session, ":dmj", markDirty = false).getOrNull() ?: return null
        val start = raw.indexOf('[')
        if (start < 0) return null
        return runCatching {
            val maps = JSONArray(raw.substring(start))
            var min = Long.MAX_VALUE
            var max = 0L
            for (index in 0 until maps.length()) {
                val map = maps.getJSONObject(index)
                val base = java.lang.Long.decode(map.optString("base", "0"))
                val size = map.optLong("size")
                if (base <= 0L || size <= 0L || base > Long.MAX_VALUE - size) continue
                val end = base + size
                if (currentOffset in base until end) return base to end
                min = minOf(min, base)
                max = maxOf(max, end)
            }
            if (max <= min) null
            else if (max - min > 100L * 1024L * 1024L) {
                val window = 10L * 1024L * 1024L
                val windowStart = (currentOffset.coerceIn(min, max - 1) - window / 2).coerceAtLeast(min)
                windowStart to (windowStart + minOf(window, max - windowStart))
            } else min to max
        }.getOrNull()
    }

    private fun writeBytes(session: R2PipeManager.R2Session, addr: Long, type: ViewerByteEdit, value: String) {
        val target = Selection(session, R2PipeManager.sessionId)
        if (!target.isActive()) return
        viewModelScope.launch {
            if (!target.isActive()) return@launch
            if (!session.writeViewerBytes(type, value, listOf(addr)) || R2PipeManager.activeSessionPipe !== session.pipe) return@launch
            hexDataManager?.let { manager ->
                val byteCount = when {
                    type == ViewerByteEdit.HEX -> hexByteCount(value)
                    type == ViewerByteEdit.TEXT && session.writeCacheEnabled -> value.encodeToByteArray().size
                    else -> null // Assembly and r2-parsed strings have no reliable length in the input.
                }
                if (byteCount != null) manager.invalidateRange(addr, byteCount) else manager.clearCache()
            }
            _dataModifiedEvent.value++
        }
    }

    /** Called when another feature modifies data. */
    fun refreshData() {
        hexDataManager?.clearCache()
    }
}

private fun hexByteCount(value: String): Int? {
    var digits = 0
    for (char in value) {
        when {
            char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F' -> digits++
            char.isWhitespace() -> Unit
            else -> return null
        }
    }
    return if (digits > 0 && digits % 2 == 0) digits / 2 else null
}

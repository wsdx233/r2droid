package top.wsdx233.r2droid.feature.hex

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.core.data.model.Section
import top.wsdx233.r2droid.feature.hex.data.HEX_INSPECTOR_MAX_BYTES
import top.wsdx233.r2droid.feature.hex.data.HexCopyFormat
import top.wsdx233.r2droid.feature.hex.data.HexDataManager
import top.wsdx233.r2droid.feature.hex.data.HexEndianness
import top.wsdx233.r2droid.feature.hex.data.HexHashAccumulator
import top.wsdx233.r2droid.feature.hex.data.HexHashResult
import top.wsdx233.r2droid.feature.hex.data.HexRepository
import top.wsdx233.r2droid.feature.hex.data.HexSelectionState
import top.wsdx233.r2droid.feature.hex.data.HexSingleDisasm
import top.wsdx233.r2droid.feature.hex.data.computeHashes
import top.wsdx233.r2droid.feature.hex.data.defaultEndiannessFromBinInfo
import top.wsdx233.r2droid.feature.hex.data.defaultPointerBitsFromBinInfo
import top.wsdx233.r2droid.feature.hex.data.formatBytes
import top.wsdx233.r2droid.feature.hex.data.parseHexBytes
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.util.ViewerByteEdit
import top.wsdx233.r2droid.util.writeViewerBytes
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class HexPanelTab {
    INSPECTOR,
    SELECTION,
    HASH,
    DECODE
}

data class HexInspectorUiState(
    val address: Long = 0L,
    val bytes: ByteArray = ByteArray(0),
    val isBytesLoading: Boolean = false,
    val endianness: HexEndianness = HexEndianness.LITTLE,
    val endiannessOverridden: Boolean = false,
    val pointerBits: Int = 64,
    val pointerBitsOverridden: Boolean = false,
    val disasm: HexSingleDisasm? = null,
    val disasmLoading: Boolean = false,
    val disasmError: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HexInspectorUiState) return false
        return address == other.address &&
            bytes.contentEquals(other.bytes) &&
            isBytesLoading == other.isBytesLoading &&
            endianness == other.endianness &&
            endiannessOverridden == other.endiannessOverridden &&
            pointerBits == other.pointerBits &&
            pointerBitsOverridden == other.pointerBitsOverridden &&
            disasm == other.disasm &&
            disasmLoading == other.disasmLoading &&
            disasmError == other.disasmError
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + isBytesLoading.hashCode()
        result = 31 * result + endianness.hashCode()
        result = 31 * result + endiannessOverridden.hashCode()
        result = 31 * result + pointerBits
        result = 31 * result + pointerBitsOverridden.hashCode()
        result = 31 * result + (disasm?.hashCode() ?: 0)
        result = 31 * result + disasmLoading.hashCode()
        result = 31 * result + (disasmError?.hashCode() ?: 0)
        return result
    }
}

data class HexSelectionDataState(
    val rangeStart: Long = 0L,
    val rangeEnd: Long = 0L,
    val totalByteCount: Long = 0L,
    val bytes: ByteArray = ByteArray(0),
    val isLoading: Boolean = false,
    val exceedsPreviewLimit: Boolean = false,
    val error: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HexSelectionDataState) return false
        return rangeStart == other.rangeStart &&
            rangeEnd == other.rangeEnd &&
            totalByteCount == other.totalByteCount &&
            bytes.contentEquals(other.bytes) &&
            isLoading == other.isLoading &&
            exceedsPreviewLimit == other.exceedsPreviewLimit &&
            error == other.error
    }

    override fun hashCode(): Int {
        var result = rangeStart.hashCode()
        result = 31 * result + rangeEnd.hashCode()
        result = 31 * result + totalByteCount.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + exceedsPreviewLimit.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

data class HexHashUiState(
    val rangeStart: Long = 0L,
    val rangeEnd: Long = 0L,
    val byteCount: Long = 0L,
    val result: HexHashResult? = null,
    val isLoading: Boolean = false,
    val requiresManualCompute: Boolean = false,
    val exceedsLimit: Boolean = false,
    val error: String? = null
)

sealed interface HexExportStatus {
    object Idle : HexExportStatus
    object Exporting : HexExportStatus
    data class Success(val byteCount: Long, val usedWt: Boolean) : HexExportStatus
    data class Error(val message: String) : HexExportStatus
}

sealed interface HexEvent {
    data class LoadHex(val sections: List<Section>, val currentFilePath: String?, val currentOffset: Long) : HexEvent
    data class WriteHex(val session: R2PipeManager.R2Session, val address: Long, val hex: String) : HexEvent
    data class WriteString(val session: R2PipeManager.R2Session, val address: Long, val text: String) : HexEvent
    data class WriteAsm(val session: R2PipeManager.R2Session, val address: Long, val asm: String) : HexEvent
    data class FillSelection(val session: R2PipeManager.R2Session, val hexPattern: String) : HexEvent
    object RefreshData : HexEvent
    object Reset : HexEvent

    // Multi-byte selection
    data class StartSelection(val address: Long) : HexEvent
    data class UpdateSelection(val address: Long) : HexEvent
    data class SetSelectionRange(val startAddress: Long, val length: Long) : HexEvent
    object ClearSelection : HexEvent

    // Docked tools panel & Data Inspector
    data class SetToolsPanelVisible(val visible: Boolean, val tab: HexPanelTab? = null) : HexEvent
    data class SelectToolsPanelTab(val tab: HexPanelTab) : HexEvent
    data class SetEndianness(val endianness: HexEndianness) : HexEvent
    data class SetPointerBits(val bits: Int) : HexEvent
    data class SyncBinaryDefaults(val endian: String?, val bits: Int?) : HexEvent
    data class InspectAddress(val address: Long, val forceRefresh: Boolean = false) : HexEvent
    data class SyncSelectionData(val cursorAddress: Long, val forceRefresh: Boolean = false) : HexEvent
    data class ComputeHashes(val startAddress: Long, val byteCount: Long) : HexEvent
    object ClearExportStatus : HexEvent
}

@HiltViewModel
class HexViewModel @Inject constructor(private val hexRepository: HexRepository) : ViewModel() {
    companion object {
        const val MAX_PREVIEW_AND_COPY_BYTES = 256 * 1024L
        const val MAX_HASH_BYTES = 4 * 1024 * 1024L
        private const val STREAM_CHUNK_SIZE = 16 * 1024
        private const val WRITE_FILL_CHUNK_BYTES = 4096
        private const val DISASM_DEBOUNCE_MS = 80L
    }

    private val _hexDataManagerState = MutableStateFlow<HexDataManager?>(null)
    val hexDataManagerState = _hexDataManagerState.asStateFlow()
    val hexDataManager: HexDataManager?
        get() = _hexDataManagerState.value.takeIf { selection?.isActive() == true }

    private val _dataModifiedEvent = MutableStateFlow(0L)
    val dataModifiedEvent: StateFlow<Long> = _dataModifiedEvent.asStateFlow()

    private val _selectionState = MutableStateFlow(HexSelectionState())
    val selectionState: StateFlow<HexSelectionState> = _selectionState.asStateFlow()

    private val _toolsPanelVisible = MutableStateFlow(false)
    val toolsPanelVisible: StateFlow<Boolean> = _toolsPanelVisible.asStateFlow()

    private val _toolsPanelTab = MutableStateFlow(HexPanelTab.INSPECTOR)
    val toolsPanelTab: StateFlow<HexPanelTab> = _toolsPanelTab.asStateFlow()

    private val _inspectorState = MutableStateFlow(HexInspectorUiState())
    val inspectorState: StateFlow<HexInspectorUiState> = _inspectorState.asStateFlow()

    private val _selectionDataState = MutableStateFlow(HexSelectionDataState())
    val selectionDataState: StateFlow<HexSelectionDataState> = _selectionDataState.asStateFlow()

    private val _hashState = MutableStateFlow(HexHashUiState())
    val hashState: StateFlow<HexHashUiState> = _hashState.asStateFlow()

    private val _exportStatus = MutableStateFlow<HexExportStatus>(HexExportStatus.Idle)
    val exportStatus: StateFlow<HexExportStatus> = _exportStatus.asStateFlow()

    private class Selection(val session: R2PipeManager.R2Session, val generation: Int) {
        fun isActive() = session.pipe === R2PipeManager.activeSessionPipe && generation == R2PipeManager.sessionId
    }

    private var selection: Selection? = null
    private var initialLoad: Job? = null
    private var inspectorBytesJob: Job? = null
    private var inspectorDisasmJob: Job? = null
    private var selectionDataJob: Job? = null
    private var hashJob: Job? = null
    private var exportJob: Job? = null

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
            is HexEvent.FillSelection -> fillSelection(event.session, event.hexPattern)
            HexEvent.RefreshData -> refreshData()
            HexEvent.Reset -> reset()
            is HexEvent.StartSelection -> startSelection(event.address)
            is HexEvent.UpdateSelection -> updateSelection(event.address)
            is HexEvent.SetSelectionRange -> setSelectionRange(event.startAddress, event.length)
            HexEvent.ClearSelection -> clearSelection()
            is HexEvent.SetToolsPanelVisible -> {
                event.tab?.let { _toolsPanelTab.value = it }
                _toolsPanelVisible.value = event.visible
            }
            is HexEvent.SelectToolsPanelTab -> _toolsPanelTab.value = event.tab
            is HexEvent.SetEndianness -> _inspectorState.update {
                it.copy(endianness = event.endianness, endiannessOverridden = true)
            }
            is HexEvent.SetPointerBits -> _inspectorState.update {
                it.copy(pointerBits = if (event.bits == 32) 32 else 64, pointerBitsOverridden = true)
            }
            is HexEvent.SyncBinaryDefaults -> _inspectorState.update { current ->
                current.copy(
                    endianness = if (current.endiannessOverridden) current.endianness else defaultEndiannessFromBinInfo(event.endian),
                    pointerBits = if (current.pointerBitsOverridden) current.pointerBits else defaultPointerBitsFromBinInfo(event.bits)
                )
            }
            is HexEvent.InspectAddress -> inspectAddress(event.address, event.forceRefresh)
            is HexEvent.SyncSelectionData -> syncSelectionData(event.cursorAddress, event.forceRefresh)
            is HexEvent.ComputeHashes -> computeHashesForRange(event.startAddress, event.byteCount)
            HexEvent.ClearExportStatus -> _exportStatus.value = HexExportStatus.Idle
        }
    }

    fun reset() {
        initialLoad?.cancel()
        initialLoad = null
        inspectorBytesJob?.cancel()
        inspectorBytesJob = null
        inspectorDisasmJob?.cancel()
        inspectorDisasmJob = null
        selectionDataJob?.cancel()
        selectionDataJob = null
        hashJob?.cancel()
        hashJob = null
        exportJob?.cancel()
        exportJob = null
        selection = null
        _hexDataManagerState.value?.close()
        _hexDataManagerState.value = null
        _selectionState.value = HexSelectionState()
        _toolsPanelVisible.value = false
        _toolsPanelTab.value = HexPanelTab.INSPECTOR
        _inspectorState.value = HexInspectorUiState()
        _selectionDataState.value = HexSelectionDataState()
        _hashState.value = HexHashUiState()
        _exportStatus.value = HexExportStatus.Idle
    }

    override fun onCleared() {
        reset()
        super.onCleared()
    }

    private fun startSelection(address: Long) {
        val clamped = clampAddressToView(address)
        _selectionState.value = HexSelectionState(active = true, startAddr = clamped, endAddr = clamped)
    }

    private fun updateSelection(address: Long) {
        val current = _selectionState.value
        val clamped = clampAddressToView(address)
        if (!current.active) {
            _selectionState.value = HexSelectionState(active = true, startAddr = clamped, endAddr = clamped)
        } else {
            _selectionState.value = current.copy(endAddr = clamped)
        }
    }

    private fun setSelectionRange(startAddress: Long, length: Long) {
        if (length <= 0L) return
        val manager = hexDataManager
        val start = clampAddressToView(startAddress)
        val end = if (manager != null && manager.viewEndAddress > manager.viewStartAddress) {
            val maxEnd = manager.viewEndAddress - 1L
            val maxLen = (maxEnd.toULong() - start.toULong() + 1uL).coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
            val clampedLen = length.coerceAtMost(maxLen).coerceAtLeast(1L)
            start + clampedLen - 1L
        } else {
            val maxAdd = Long.MAX_VALUE - start
            start + (length - 1L).coerceAtMost(maxAdd.coerceAtLeast(0L))
        }
        _selectionState.value = HexSelectionState(active = true, startAddr = start, endAddr = end)
    }

    private fun clearSelection() {
        _selectionState.value = HexSelectionState()
    }

    private fun clampAddressToView(address: Long): Long {
        val manager = hexDataManager ?: return address
        if (manager.viewEndAddress <= manager.viewStartAddress) return address
        return address.coerceIn(manager.viewStartAddress, manager.viewEndAddress - 1L)
    }

    private fun inspectAddress(address: Long, forceRefresh: Boolean) {
        val target = selection ?: return
        val manager = hexDataManager ?: return
        if (!target.isActive()) return

        val cached = manager.readCachedBytes(address, HEX_INSPECTOR_MAX_BYTES)
        if (cached != null) {
            inspectorBytesJob?.cancel()
            _inspectorState.update {
                it.copy(address = address, bytes = cached, isBytesLoading = false)
            }
        } else {
            inspectorBytesJob?.cancel()
            _inspectorState.update {
                it.copy(address = address, isBytesLoading = true)
            }
            inspectorBytesJob = viewModelScope.launch {
                val result = manager.readBytes(address, HEX_INSPECTOR_MAX_BYTES)
                if (!target.isActive()) return@launch
                _inspectorState.update { current ->
                    if (current.address != address) current
                    else current.copy(
                        bytes = result.getOrDefault(ByteArray(0)),
                        isBytesLoading = false
                    )
                }
            }
        }

        val current = _inspectorState.value
        val needsDisasm = forceRefresh ||
            current.address != address ||
            (current.disasm == null && !current.disasmLoading && current.disasmError == null)
        if (!needsDisasm && inspectorDisasmJob?.isActive == true) return

        inspectorDisasmJob?.cancel()
        _inspectorState.update {
            it.copy(
                address = address,
                disasmLoading = true,
                disasmError = null,
                disasm = if (it.address == address && !forceRefresh) it.disasm else null
            )
        }
        inspectorDisasmJob = viewModelScope.launch {
            if (!forceRefresh) delay(DISASM_DEBOUNCE_MS)
            if (!target.isActive()) return@launch
            val result = hexRepository.getSingleDisasm(target.session, address)
            if (!target.isActive()) return@launch
            _inspectorState.update { state ->
                if (state.address != address) state
                else result.fold(
                    onSuccess = { disasm ->
                        state.copy(disasm = disasm, disasmLoading = false, disasmError = null)
                    },
                    onFailure = { error ->
                        state.copy(disasm = null, disasmLoading = false, disasmError = error.message ?: "Failed")
                    }
                )
            }
        }
    }

    private fun syncSelectionData(cursorAddress: Long, forceRefresh: Boolean) {
        val target = selection ?: return
        val manager = hexDataManager ?: return
        if (!target.isActive()) return

        val sel = _selectionState.value
        val start = if (sel.active) sel.rangeStart else clampAddressToView(cursorAddress)
        val end = if (sel.active) sel.rangeEnd else start
        val totalCount = if (sel.active) sel.byteCount else 1L

        val prev = _selectionDataState.value
        if (!forceRefresh &&
            prev.rangeStart == start &&
            prev.rangeEnd == end &&
            prev.totalByteCount == totalCount &&
            !prev.isLoading &&
            (prev.bytes.isNotEmpty() || prev.exceedsPreviewLimit)
        ) {
            return
        }

        selectionDataJob?.cancel()
        hashJob?.cancel()

        if (totalCount > MAX_PREVIEW_AND_COPY_BYTES) {
            _selectionDataState.value = HexSelectionDataState(
                rangeStart = start,
                rangeEnd = end,
                totalByteCount = totalCount,
                bytes = ByteArray(0),
                isLoading = false,
                exceedsPreviewLimit = true
            )
            _hashState.value = HexHashUiState(
                rangeStart = start,
                rangeEnd = end,
                byteCount = totalCount,
                result = null,
                isLoading = false,
                requiresManualCompute = totalCount <= MAX_HASH_BYTES,
                exceedsLimit = totalCount > MAX_HASH_BYTES
            )
            return
        }

        val countInt = totalCount.toInt()
        val cached = manager.readCachedBytes(start, countInt)
        if (cached != null) {
            _selectionDataState.value = HexSelectionDataState(
                rangeStart = start,
                rangeEnd = end,
                totalByteCount = totalCount,
                bytes = cached,
                isLoading = false,
                exceedsPreviewLimit = false
            )
            computeHashesFromBytes(start, end, cached)
            return
        }

        _selectionDataState.value = HexSelectionDataState(
            rangeStart = start,
            rangeEnd = end,
            totalByteCount = totalCount,
            bytes = ByteArray(0),
            isLoading = true,
            exceedsPreviewLimit = false
        )
        _hashState.value = HexHashUiState(
            rangeStart = start,
            rangeEnd = end,
            byteCount = totalCount,
            isLoading = true
        )

        selectionDataJob = viewModelScope.launch {
            val result = manager.readBytes(start, countInt)
            if (!target.isActive()) return@launch
            result.fold(
                onSuccess = { bytes ->
                    _selectionDataState.value = HexSelectionDataState(
                        rangeStart = start,
                        rangeEnd = end,
                        totalByteCount = totalCount,
                        bytes = bytes,
                        isLoading = false,
                        exceedsPreviewLimit = false
                    )
                    computeHashesFromBytes(start, end, bytes)
                },
                onFailure = { error ->
                    val message = error.message ?: "Read failed"
                    _selectionDataState.value = HexSelectionDataState(
                        rangeStart = start,
                        rangeEnd = end,
                        totalByteCount = totalCount,
                        bytes = ByteArray(0),
                        isLoading = false,
                        error = message
                    )
                    _hashState.value = HexHashUiState(
                        rangeStart = start,
                        rangeEnd = end,
                        byteCount = totalCount,
                        isLoading = false,
                        error = message
                    )
                }
            )
        }
    }

    private fun computeHashesFromBytes(start: Long, end: Long, bytes: ByteArray) {
        hashJob?.cancel()
        if (bytes.size <= 4096) {
            val computed = computeHashes(bytes)
            _hashState.value = HexHashUiState(
                rangeStart = start,
                rangeEnd = end,
                byteCount = bytes.size.toLong(),
                result = computed,
                isLoading = false
            )
            return
        }
        _hashState.value = HexHashUiState(
            rangeStart = start,
            rangeEnd = end,
            byteCount = bytes.size.toLong(),
            isLoading = true
        )
        hashJob = viewModelScope.launch(Dispatchers.Default) {
            val computed = computeHashes(bytes)
            ensureActive()
            _hashState.value = HexHashUiState(
                rangeStart = start,
                rangeEnd = end,
                byteCount = bytes.size.toLong(),
                result = computed,
                isLoading = false
            )
        }
    }

    private fun computeHashesForRange(startAddress: Long, byteCount: Long) {
        val target = selection ?: return
        val manager = hexDataManager ?: return
        if (!target.isActive()) return
        if (byteCount <= 0L) return
        val endAddress = startAddress + byteCount - 1L
        if (byteCount > MAX_HASH_BYTES) {
            _hashState.value = HexHashUiState(
                rangeStart = startAddress,
                rangeEnd = endAddress,
                byteCount = byteCount,
                exceedsLimit = true
            )
            return
        }

        hashJob?.cancel()
        _hashState.value = HexHashUiState(
            rangeStart = startAddress,
            rangeEnd = endAddress,
            byteCount = byteCount,
            isLoading = true
        )
        hashJob = viewModelScope.launch {
            try {
                val acc = HexHashAccumulator()
                var offset = 0L
                while (offset < byteCount) {
                    ensureActive()
                    if (!target.isActive()) return@launch
                    val len = minOf(STREAM_CHUNK_SIZE.toLong(), byteCount - offset).toInt()
                    val chunk = manager.readBytes(startAddress + offset, len).getOrThrow()
                    if (chunk.isEmpty()) break
                    withContext(Dispatchers.Default) {
                        acc.update(chunk)
                    }
                    offset += chunk.size
                    if (chunk.size < len) break
                }
                val result = withContext(Dispatchers.Default) { acc.finish() }
                _hashState.value = HexHashUiState(
                    rangeStart = startAddress,
                    rangeEnd = endAddress,
                    byteCount = result.byteCount,
                    result = result,
                    isLoading = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _hashState.value = HexHashUiState(
                    rangeStart = startAddress,
                    rangeEnd = endAddress,
                    byteCount = byteCount,
                    isLoading = false,
                    error = error.message ?: "Hash calculation failed"
                )
            }
        }
    }

    suspend fun readFormattedSelection(cursorAddress: Long, format: HexCopyFormat): Result<String> {
        val manager = hexDataManager ?: return Result.failure(IllegalStateException("Hex data not loaded"))
        val sel = _selectionState.value
        val start = if (sel.active) sel.rangeStart else clampAddressToView(cursorAddress)
        val count = if (sel.active) sel.byteCount else 1L
        if (count > MAX_PREVIEW_AND_COPY_BYTES) {
            return Result.failure(IllegalStateException("Selection too large for clipboard (>256 KB)"))
        }
        val bytes = manager.readBytes(start, count.toInt()).getOrElse { return Result.failure(it) }
        val formatted = withContext(Dispatchers.Default) { formatBytes(bytes, format) }
        return Result.success(formatted)
    }

    fun exportRangeToUri(context: Context, uri: Uri, startAddress: Long, byteCount: Long) {
        val target = selection ?: return
        val manager = hexDataManager ?: return
        if (!target.isActive() || byteCount <= 0L) return

        exportJob?.cancel()
        _exportStatus.value = HexExportStatus.Exporting
        val appContext = context.applicationContext
        exportJob = viewModelScope.launch {
            val tempFile = File(appContext.cacheDir, "hex_slice_${UUID.randomUUID()}.bin")
            try {
                val wtResult = hexRepository.exportRangeWithWt(target.session, startAddress, byteCount, tempFile)
                if (!target.isActive()) return@launch
                if (wtResult.isSuccess) {
                    val writtenBytes = withContext(Dispatchers.IO) {
                        val out = appContext.contentResolver.openOutputStream(uri)
                            ?: error("Cannot open output document")
                        out.use { outputStream ->
                            tempFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream, bufferSize = 64 * 1024)
                            }
                        }
                    }
                    _exportStatus.value = HexExportStatus.Success(writtenBytes, usedWt = true)
                } else {
                    // Fallback to chunked pxj read when wt is unavailable (e.g. ESIL or remote HTTP).
                    val writtenBytes = withContext(Dispatchers.IO) {
                        val out = appContext.contentResolver.openOutputStream(uri)
                            ?: error("Cannot open output document")
                        out.use { outputStream ->
                            var written = 0L
                            while (written < byteCount) {
                                ensureActive()
                                if (!target.isActive()) error("Session disconnected")
                                val chunkLen = minOf(STREAM_CHUNK_SIZE.toLong(), byteCount - written).toInt()
                                val bytes = manager.readBytes(startAddress + written, chunkLen).getOrThrow()
                                if (bytes.isEmpty()) break
                                outputStream.write(bytes)
                                written += bytes.size
                                if (bytes.size < chunkLen) break
                            }
                            written
                        }
                    }
                    _exportStatus.value = HexExportStatus.Success(writtenBytes, usedWt = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _exportStatus.value = HexExportStatus.Error(error.message ?: "Export failed")
            } finally {
                withContext(Dispatchers.IO) {
                    runCatching { if (tempFile.exists()) tempFile.delete() }
                }
            }
        }
    }

    private fun fillSelection(session: R2PipeManager.R2Session, hexPattern: String) {
        val target = Selection(session, R2PipeManager.sessionId)
        val sel = _selectionState.value
        if (!target.isActive() || !sel.active) return
        val patternBytes = parseHexBytes(hexPattern) ?: return
        val totalBytes = sel.byteCount.coerceAtMost(MAX_PREVIEW_AND_COPY_BYTES).toInt()
        if (totalBytes <= 0) return

        val start = sel.rangeStart
        viewModelScope.launch {
            if (!target.isActive()) return@launch
            var offset = 0
            while (offset < totalBytes) {
                if (!target.isActive()) return@launch
                val chunkLen = minOf(WRITE_FILL_CHUNK_BYTES, totalBytes - offset)
                val chunkHex = withContext(Dispatchers.Default) {
                    buildString(chunkLen * 2) {
                        val digits = "0123456789ABCDEF"
                        for (i in 0 until chunkLen) {
                            val b = patternBytes[(offset + i) % patternBytes.size].toInt() and 0xFF
                            append(digits[b ushr 4])
                            append(digits[b and 0x0F])
                        }
                    }
                }
                if (!session.writeViewerBytes(ViewerByteEdit.HEX, chunkHex, listOf(start + offset)) ||
                    R2PipeManager.activeSessionPipe !== session.pipe
                ) {
                    return@launch
                }
                offset += chunkLen
            }
            hexDataManager?.invalidateRange(start, totalBytes)
            _dataModifiedEvent.value++
            _selectionState.value = HexSelectionState()
        }
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
        _dataModifiedEvent.value++
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

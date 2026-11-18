package top.wsdx233.r2droid.feature.disasm

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.feature.disasm.data.DisasmDataManager

/** The address remains visible even when the display window does not cover it. */
data class DisasmScrollbarPreview(
    val address: Long,
    val isLoading: Boolean = false,
    val failed: Boolean = false
)

/** Main-thread gesture controller. Pointer updates never decode, merge or rebuild jump maps. */
internal class DisasmScrollbarNavigator(
    private val scope: CoroutineScope,
    private val uptimeMillis: () -> Long = SystemClock::uptimeMillis,
    private val onCommitReady: (address: Long, index: Int) -> Unit
) {
    companion object {
        const val DWELL_MILLIS = 300L
        private const val REGION_BYTES = DisasmDataManager.INSTRUCTIONS_PER_CHUNK *
            DisasmDataManager.AVG_INSTRUCTION_SIZE
    }

    private val _preview = MutableStateFlow<DisasmScrollbarPreview?>(null)
    val preview = _preview.asStateFlow()
    val isActive: Boolean get() = _preview.value != null || job?.isActive == true

    private var manager: DisasmDataManager? = null
    private var job: Job? = null
    private var region: Long? = null
    private var dragging = false
    private var committed = false
    private var commitSent = false
    private var loading = false
    private var workId = 0L
    private var dwellDeadline = 0L

    fun begin(manager: DisasmDataManager) {
        cancel()
        this.manager = manager
        dragging = true
    }

    fun preview(address: Long) {
        if (!dragging) return
        val manager = manager ?: return
        val target = address.coerceIn(manager.viewStartAddress, manager.viewEndAddress - 1)
        if (_preview.value?.address == target) return
        val nextRegion = (target - manager.viewStartAddress) / REGION_BYTES
        val before = manager.getDataSnapshot()
        val covered = manager.showCachedAddress(target)
        val switchedWindow = before !== manager.getDataSnapshot()
        // Small finger jitter within one region must not indefinitely postpone its dwell load.
        val changedRegion = nextRegion != region || switchedWindow
        if (changedRegion) {
            if (loading) cancelWork()
            region = nextRegion
        }
        _preview.value = DisasmScrollbarPreview(target, isLoading = loading)
        if (changedRegion || (!covered && job?.isActive != true)) schedule(DWELL_MILLIS)
    }

    /** Release bypasses the dwell timer, but does not cancel a matching in-flight request. */
    fun commit(address: Long) {
        if (!dragging) return
        preview(address)
        committed = true
        publishCommitIfReady()
        if (!loading && (job?.isActive == true || manager?.isAddressRangeLoaded(address) != true)) {
            cancelWork()
            schedule(0)
        }
    }

    fun endGesture() {
        dragging = false
        if (!committed) cancel()
    }

    fun retry() {
        if (_preview.value == null) return
        cancelWork()
        schedule(0)
    }

    /** Clear the overlay only after the UI has positioned the committed, decoded rows. */
    fun acknowledgeCommit(address: Long) {
        if (committed && commitSent && _preview.value?.address == address) _preview.value = null
    }

    fun cancel() {
        cancelWork()
        manager = null
        region = null
        dragging = false
        committed = false
        commitSent = false
        _preview.value = null
    }

    private fun cancelWork() {
        workId++
        job?.cancel()
        job = null
        // Invalidate already-running R2 results before a new window can be displayed.
        if (loading) manager?.prepareForJump()
        loading = false
    }

    private fun schedule(waitMillis: Long) {
        val manager = manager ?: return
        // A fast sweep only moves the deadline. Keep one sleeper instead of cancelling and
        // allocating a coroutine/timer per pointer sample that crosses a 400-byte region.
        dwellDeadline = uptimeMillis() + waitMillis
        if (job?.isActive == true) return
        val id = ++workId
        job = scope.launch {
            try {
                while (true) {
                    val remaining = dwellDeadline - uptimeMillis()
                    if (remaining <= 0) break
                    delay(remaining)
                }
                loading = true
                _preview.value = _preview.value?.copy(isLoading = true, failed = false)
                if (!loadLatestTarget(manager)) return@launch
                // Publish the target before fetching adjacent chunks; a slow neighbor must not
                // delay either the preview or release. The pipe still executes serially.
                val address = _preview.value?.address ?: return@launch
                publishCommitIfReady()
                manager.preloadAround(address, rangeChunks = 1)
                currentCoroutineContext().ensureActive()
                // The finger may have moved backwards within this region during the fetch.
                if (_preview.value != null && loadLatestTarget(manager)) publishCommitIfReady()
            } finally {
                if (id == workId) {
                    loading = false
                    _preview.value = _preview.value?.copy(isLoading = false)
                }
            }
        }
    }

    private suspend fun loadLatestTarget(manager: DisasmDataManager): Boolean {
        while (true) {
            val target = _preview.value?.address ?: return true
            manager.loadAndFindIndex(target, preloadPrevious = false)
            currentCoroutineContext().ensureActive()
            val latest = _preview.value ?: return true
            if (manager.isAddressRangeLoaded(latest.address)) return true
            if (latest.address == target) {
                _preview.value = latest.copy(isLoading = false, failed = true)
                return false
            }
            // Only the latest target matters, not intermediate pointer samples.
        }
    }

    private fun publishCommitIfReady() {
        val manager = manager ?: return
        val target = _preview.value?.address ?: return
        if (!committed || commitSent) return
        val index = DisasmDataManager.coveredIndex(manager.getSnapshot(), target)
        if (index < 0) return
        commitSent = true
        onCommitReady(target, index)
    }
}

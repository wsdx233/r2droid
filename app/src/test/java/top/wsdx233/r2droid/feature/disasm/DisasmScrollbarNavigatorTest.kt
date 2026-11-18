package top.wsdx233.r2droid.feature.disasm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.feature.disasm.data.DisasmDataManager

@OptIn(ExperimentalCoroutinesApi::class)
class DisasmScrollbarNavigatorTest {
    private fun rows(address: Long, count: Int) = List(count) {
        DisasmInstruction(address + it * 4, "nop", "00000000", "nop", 4, "nop", null)
    }

    private class Harness(
        val manager: DisasmDataManager,
        val navigator: DisasmScrollbarNavigator,
        val requests: MutableList<Long>,
        val commits: MutableList<Long>
    )

    private suspend fun TestScope.harness(
        fetch: suspend (Long, Int) -> Result<List<DisasmInstruction>> = { address, count -> Result.success(rows(address, count)) }
    ): Harness {
        val requests = mutableListOf<Long>()
        val commits = mutableListOf<Long>()
        val manager = DisasmDataManager(0, 100000, { address, count ->
            requests.add(address)
            fetch(address, count)
        }, StandardTestDispatcher(testScheduler))
        manager.loadFromAddress(0)
        requests.clear()
        val navigator = DisasmScrollbarNavigator(this, { testScheduler.currentTime }) { address, index ->
            assertEquals(index, DisasmDataManager.coveredIndex(manager.getSnapshot(), address))
            commits.add(address)
        }
        navigator.begin(manager)
        return Harness(manager, navigator, requests, commits)
    }

    @Test fun uncachedPreviewUpdatesImmediatelyWithoutFetchingBeforeDwell() = runTest {
        val h = harness()
        val previous = h.manager.getDataSnapshot()
        h.navigator.preview(8000)
        assertEquals(DisasmScrollbarPreview(8000), h.navigator.preview.value)
        assertSame(previous, h.manager.getDataSnapshot())
        advanceTimeBy(DisasmScrollbarNavigator.DWELL_MILLIS - 1)
        runCurrent()
        assertTrue(h.requests.isEmpty())
        h.navigator.cancel()
    }

    @Test fun dwellingLoadsTheTargetAndBothNeighborsBeforeRelease() = runTest {
        val h = harness()
        h.navigator.preview(8000)
        advanceUntilIdle()
        assertEquals(8000L, h.requests.first())
        assertTrue(h.requests.any { it < 8000 })
        assertTrue(h.requests.any { it > 8000 })
        assertTrue(h.manager.isAddressRangeLoaded(8000))
        assertEquals(DisasmScrollbarPreview(8000), h.navigator.preview.value)
        assertTrue(h.commits.isEmpty()) // Finger still down.
    }

    @Test fun rapidRegionChangesOnlyFetchTheLastRegion() = runTest {
        val h = harness()
        for (address in listOf(4000L, 8000L, 12000L)) {
            h.navigator.preview(address)
            advanceTimeBy(200)
            runCurrent()
            assertTrue(h.requests.isEmpty())
        }
        advanceUntilIdle()
        assertEquals(12000L, h.requests.first())
        assertFalse(h.requests.contains(4000))
        assertFalse(h.requests.contains(8000))
    }

    @Test fun smallJitterInOneRegionDoesNotResetTheDwellDeadline() = runTest {
        val h = harness()
        h.navigator.preview(4100)
        advanceTimeBy(200)
        h.navigator.preview(4120)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(4120L, h.requests.first())
        assertTrue(h.manager.isAddressRangeLoaded(4120))
    }

    @Test fun releaseBypassesDwellAndCommitsOnce() = runTest {
        val h = harness()
        h.navigator.preview(8000)
        h.navigator.commit(8000)
        h.navigator.endGesture()
        runCurrent()
        assertEquals(0L, testScheduler.currentTime)
        assertEquals(listOf(8000L), h.commits)
        assertEquals(1, h.requests.count { it == 8000L })
        h.navigator.acknowledgeCommit(8000)
        assertNull(h.navigator.preview.value)
    }

    @Test fun releaseReusesTheAlreadyRunningDwellFetch() = runTest {
        val response = CompletableDeferred<Unit>()
        val h = harness { address, count ->
            if (address == 8000L) response.await()
            Result.success(rows(address, count))
        }
        h.navigator.preview(8000)
        advanceTimeBy(300)
        runCurrent()
        assertTrue(h.navigator.preview.value!!.isLoading)
        h.navigator.commit(8000)
        h.navigator.endGesture()
        runCurrent()
        assertEquals(listOf(8000L), h.requests)
        response.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, h.requests.count { it == 8000L })
        assertEquals(listOf(8000L), h.commits)
    }

    @Test fun releasePublishesTheTargetWhileThePrecedingChunkIsStillBlocked() = runTest {
        val neighbor = CompletableDeferred<Unit>()
        val h = harness { address, count ->
            if (address in 1L until 8000L) neighbor.await()
            Result.success(rows(address, count))
        }
        h.navigator.commit(8000)
        h.navigator.endGesture()
        runCurrent()
        assertEquals(listOf(8000L), h.commits)
        assertTrue(h.requests.last() in 1L until 8000L)
        h.navigator.acknowledgeCommit(8000)
        assertNull(h.navigator.preview.value)
        neighbor.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(8000L), h.commits)
        assertTrue(h.manager.isAddressRangeLoaded(8000))
    }

    @Test fun releaseAfterDwellDoesNotFetchTheSameRegionOrNeighborsAgain() = runTest {
        val h = harness()
        h.navigator.preview(8000)
        advanceUntilIdle()
        val requests = h.requests.toList()
        h.navigator.commit(8000)
        h.navigator.endGesture()
        advanceUntilIdle()
        assertEquals(requests, h.requests)
        assertEquals(listOf(8000L), h.commits)
    }

    @Test fun movementWithinAnInFlightRegionCommitsTheLatestNotTheOriginalTarget() = runTest {
        val response = CompletableDeferred<Unit>()
        val h = harness { address, count ->
            if (address == 8300L) response.await()
            Result.success(rows(address, count))
        }
        h.navigator.preview(8300)
        advanceTimeBy(300)
        runCurrent()
        h.navigator.preview(8001) // Same 400-byte region, backwards beyond initial context.
        h.navigator.commit(8001)
        h.navigator.endGesture()
        response.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(8001L), h.commits)
        assertEquals(1, h.requests.count { it == 8300L })
        assertTrue(h.manager.isAddressRangeLoaded(8001))
    }

    @Test fun movingAwayInvalidatesEvenAnUncooperativeOldFetch() = runTest {
        val response = CompletableDeferred<Unit>()
        val h = harness { address, count ->
            if (address == 8000L) withContext(NonCancellable) { response.await() }
            Result.success(rows(address, count))
        }
        h.navigator.preview(8000)
        advanceTimeBy(300)
        runCurrent()
        h.navigator.preview(16000)
        advanceTimeBy(300)
        runCurrent()
        assertTrue(h.manager.isAddressRangeLoaded(16000))
        response.complete(Unit)
        advanceUntilIdle()
        assertEquals(16000L, h.navigator.preview.value!!.address)
        assertTrue(h.manager.isAddressRangeLoaded(16000))
        assertFalse(h.manager.showCachedAddress(8000))
        assertTrue(h.commits.isEmpty())
    }

    @Test fun gestureCancellationClearsPlaceholderAndNeverCommitsOrLoadsLater() = runTest {
        val h = harness()
        h.navigator.preview(8000)
        advanceTimeBy(200)
        h.navigator.endGesture()
        advanceUntilIdle()
        assertNull(h.navigator.preview.value)
        assertFalse(h.navigator.isActive)
        assertTrue(h.requests.isEmpty())
        assertTrue(h.commits.isEmpty())
    }

    @Test fun resetCancelsAnInFlightPreviewAndCannotResurrectIt() = runTest {
        val response = CompletableDeferred<Unit>()
        val h = harness { address, count ->
            if (address == 8000L) withContext(NonCancellable) { response.await() }
            Result.success(rows(address, count))
        }
        h.navigator.preview(8000)
        advanceTimeBy(300)
        runCurrent()
        h.navigator.cancel()
        val fresh = harness()
        h.navigator.begin(fresh.manager)
        h.navigator.preview(16000)
        response.complete(Unit)
        advanceUntilIdle()
        assertEquals(16000L, h.navigator.preview.value!!.address)
        assertFalse(h.manager.isAddressRangeLoaded(8000))
        assertTrue(fresh.manager.isAddressRangeLoaded(16000))
        assertTrue(h.commits.isEmpty())
    }

    @Test fun distantCachedAddressesPreviewImmediatelyWithoutExactFetchKey() = runTest {
        val h = harness()
        h.manager.loadFromAddress(8000)
        h.manager.loadFromAddress(16000)
        h.requests.clear()
        h.navigator.preview(8101)
        assertTrue(h.manager.isAddressRangeLoaded(8101))
        assertTrue(h.requests.isEmpty())
        assertEquals(8101L, h.navigator.preview.value!!.address)
        h.navigator.endGesture()
    }

    @Test fun failureKeepsTheTargetPlaceholderAndReleaseCanRetry() = runTest {
        var fail = true
        val h = harness { address, count ->
            if (address == 8000L && fail) Result.failure(IllegalStateException("offline"))
            else Result.success(rows(address, count))
        }
        h.navigator.preview(8000)
        advanceUntilIdle()
        assertEquals(DisasmScrollbarPreview(8000, failed = true), h.navigator.preview.value)
        assertTrue(h.commits.isEmpty())
        fail = false
        h.navigator.commit(8000)
        h.navigator.endGesture()
        advanceUntilIdle()
        assertEquals(listOf(8000L), h.commits)
        assertFalse(h.navigator.preview.value!!.failed)
    }

    @Test fun failedReleasedLoadCanBeRetriedWithoutAnotherGesture() = runTest {
        var fail = true
        val h = harness { address, count ->
            if (address == 8000L && fail) Result.success(emptyList()) else Result.success(rows(address, count))
        }
        h.navigator.preview(8000)
        h.navigator.commit(8000)
        h.navigator.endGesture()
        advanceUntilIdle()
        assertTrue(h.navigator.preview.value!!.failed)
        fail = false
        h.navigator.retry()
        advanceUntilIdle()
        assertEquals(listOf(8000L), h.commits)
        h.navigator.acknowledgeCommit(8000)
        assertNull(h.navigator.preview.value)
        assertFalse(h.navigator.isActive)
    }
}

package top.wsdx233.r2droid.feature.disasm.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import top.wsdx233.r2droid.core.data.model.DisasmInstruction

@OptIn(ExperimentalCoroutinesApi::class)
class DisasmDataManagerTest {
    private fun instruction(addr: Long, size: Int = 4) = DisasmInstruction(
        addr = addr, opcode = "nop", bytes = "00000000", type = "nop", size = size,
        disasm = "nop", family = null
    )

    @Test fun coveredIndexRejectsHolesAndExclusiveEndsIncludingOverflow() {
        val rows = listOf(instruction(100, 7), instruction(110, 2))
        assertEquals(-1, DisasmDataManager.coveredIndex(emptyList(), 100))
        assertEquals(-1, DisasmDataManager.coveredIndex(rows, 99))
        assertEquals(0, DisasmDataManager.coveredIndex(rows, 106))
        assertEquals(-1, DisasmDataManager.coveredIndex(rows, 107))
        assertEquals(1, DisasmDataManager.coveredIndex(rows, 111))
        assertEquals(-1, DisasmDataManager.coveredIndex(rows, 112))
        assertEquals(-1, DisasmDataManager.coveredIndex(listOf(instruction(Long.MIN_VALUE)), Long.MAX_VALUE))
    }

    @Test fun distantPreviewRestoresMatchingCachedInstructionsAndJumpMapsWithoutIo() = runTest {
        var requests = 0
        val manager = DisasmDataManager(0, 100000, { address, _ ->
            requests++
            Result.success(listOf(
                instruction(address).copy(type = "jmp", jump = address + 4, fcnAddr = address, fcnLast = address + 8),
                instruction(address + 4)
            ))
        }, StandardTestDispatcher(testScheduler))
        manager.loadFromAddress(1000)
        val cached = manager.getDataSnapshot()
        manager.loadFromAddress(20000)
        assertFalse(manager.showCachedAddress(1008))
        assertTrue(manager.showCachedAddress(1001))
        assertEquals(2, requests)
        assertSame(cached, manager.getDataSnapshot())
        assertEquals(mapOf(1000L to 1), manager.jumpToIndexMap)
        assertEquals(mapOf(1004L to 1), manager.targetToIndexMap)
        manager.loadFromAddress(20000)
        manager.loadAndFindIndex(1001)
        assertEquals(2, requests)
        assertTrue(manager.isAddressRangeLoaded(1001))
    }

    @Test fun restoringCachedWindowInvalidatesOutstandingPublication() = runTest {
        val response = CompletableDeferred<Unit>()
        val manager = DisasmDataManager(0, 100000, { address, _ ->
            if (address == 50000L) response.await()
            Result.success(listOf(instruction(address)))
        }, StandardTestDispatcher(testScheduler))
        manager.loadFromAddress(1000)
        manager.loadFromAddress(20000)
        val loading = launch { manager.loadFromAddress(50000) }
        runCurrent()
        assertTrue(manager.showCachedAddress(1000))
        response.complete(Unit)
        loading.join()
        assertTrue(manager.isAddressRangeLoaded(1000))
        assertFalse(manager.showCachedAddress(50000))
    }

    @Test fun cachedPreviewRespectsBoundedLruAndClear() = runTest {
        val manager = DisasmDataManager(0, 1000000, { address, _ ->
            Result.success(listOf(instruction(address)))
        }, StandardTestDispatcher(testScheduler))
        repeat(50) { manager.loadFromAddress(it * 4000L) }
        assertTrue(manager.showCachedAddress(1)) // Touch oldest covering entry, not just exact keys.
        manager.loadFromAddress(200000)
        assertTrue(manager.showCachedAddress(1))
        assertFalse(manager.showCachedAddress(4001))
        manager.clearCache()
        assertFalse(manager.showCachedAddress(1))
    }

    @Test fun closestIndexHandlesEmptyExactNeighborsAndClamping() {
        val rows = listOf(10L, 14L, 30L).map { instruction(it) }
        assertEquals(-1, DisasmDataManager.findClosestIndex(emptyList(), 10))
        assertEquals(0, DisasmDataManager.findClosestIndex(rows, Long.MIN_VALUE))
        assertEquals(2, DisasmDataManager.findClosestIndex(rows, Long.MAX_VALUE))
        assertEquals(1, DisasmDataManager.findClosestIndex(rows, 14))
        assertEquals(1, DisasmDataManager.findClosestIndex(rows, 20))
        assertEquals(2, DisasmDataManager.findClosestIndex(rows, 25))
        assertEquals(1, DisasmDataManager.findClosestIndex(rows, 12)) // Tie goes forward.
        val wide = listOf(instruction(Long.MIN_VALUE), instruction(Long.MAX_VALUE))
        assertEquals(1, DisasmDataManager.findClosestIndex(wide, 0))
    }

    @Test fun binarySearchMatchesNearestInstructionAcrossVariableWidths() {
        val rows = List(3000) { instruction(it.toLong() * it + 100) }
        for (address in 0L..9_000_000L step 7919) {
            val expected = rows.indices.minWith(compareBy<Int> { kotlin.math.abs(rows[it].addr - address) }.thenByDescending { it })
            assertEquals(expected, DisasmDataManager.findClosestIndex(rows, address))
        }
    }

    @Test fun nearbyButUnloadedAddressIsFetchedInsteadOfClamped() = runTest {
        val requests = mutableListOf<Long>()
        val manager = DisasmDataManager(0, 10000, { address, count ->
            requests.add(address)
            Result.success(List(count) { instruction(address + it * 4) })
        }, StandardTestDispatcher(testScheduler))
        manager.loadFromAddress(0)
        assertTrue(manager.isAddressRangeLoaded(399))
        assertFalse(manager.isAddressRangeLoaded(400))
        val index = manager.loadAndFindIndex(400)
        assertEquals(400L, manager.getAddressAt(index))
        assertTrue(requests.contains(400))
    }

    @Test fun mergeIsSortedUniqueAndPublishesMatchingJumpMaps() = runTest {
        val manager = DisasmDataManager(0, 10000, { address, _ ->
            Result.success(if (address == 100L) listOf(
                instruction(108), instruction(100).copy(type = "jmp", jump = 108, fcnAddr = 100, fcnLast = 120),
                instruction(104), instruction(104)
            ) else listOf(instruction(104).copy(opcode = "changed"), instruction(112)))
        }, StandardTestDispatcher(testScheduler))
        manager.loadFromAddress(100)
        val original = manager.getDataSnapshot()
        manager.loadFromAddress(104)
        val merged = manager.getDataSnapshot()
        assertEquals(listOf(100L, 104L, 108L, 112L), merged.instructions.map { it.addr })
        assertEquals("nop", merged.instructions[1].opcode)
        assertEquals(mapOf(100L to 1), merged.jumpToIndex)
        assertEquals(mapOf(108L to 1), merged.targetToIndex)
        assertEquals(3, original.instructions.size) // The captured old window never mutates.
    }

    @Test fun activeWindowIsBoundedAndTrimmedChunksCanBeRestoredFromCache() = runTest {
        val requests = mutableListOf<Long>()
        val manager = DisasmDataManager(0, 100000, { address, count ->
            requests.add(address)
            Result.success(List(count) { instruction(address + it * 4) })
        }, StandardTestDispatcher(testScheduler))
        manager.loadFromAddress(0)
        repeat(31) { manager.loadMore(true) }
        assertEquals(DisasmDataManager.MAX_LOADED_INSTRUCTIONS, manager.loadedInstructionCount)
        assertTrue(manager.getSnapshot().first().addr > 0)
        val fetchCount = requests.size
        manager.loadFromAddress(0)
        assertEquals(fetchCount, requests.size)
        assertEquals(0L, manager.getSnapshot().first().addr)
    }

    @Test fun preloadDoesNotFetchDistantEdgesWhenTheTargetAlreadyHasContext() = runTest {
        val requests = mutableListOf<Long>()
        val manager = DisasmDataManager(0, 100000, { address, count ->
            requests += address
            Result.success(List(count) { instruction(address + it * 4) })
        }, StandardTestDispatcher(testScheduler))
        manager.loadFromAddress(0)
        repeat(3) { manager.loadMore(true) }
        requests.clear()
        manager.preloadAround(801, rangeChunks = 1)
        assertTrue(requests.isEmpty())
        manager.preloadAround(1599, rangeChunks = 1)
        assertEquals(listOf(1600L), requests)
    }

    @Test fun staleCompletionCannotPublishOrRemoveTheNewRequestToken() = runTest {
        val oldResponse = CompletableDeferred<List<DisasmInstruction>>()
        val newResponse = CompletableDeferred<List<DisasmInstruction>>()
        var calls = 0
        var publications = 0
        val manager = DisasmDataManager(0, 10000, { _, _ ->
            calls++
            Result.success(if (calls == 1) oldResponse.await() else newResponse.await())
        }, StandardTestDispatcher(testScheduler))
        manager.onChunkLoaded = { publications++ }
        val old = launch { manager.loadFromAddress(100) }
        runCurrent()
        manager.prepareForJump()
        val fresh = launch { manager.loadFromAddress(100) }
        runCurrent()
        oldResponse.complete(listOf(instruction(100).copy(opcode = "old")))
        runCurrent()
        old.join()
        assertTrue(manager.getSnapshot().isEmpty())
        assertEquals(0, publications)
        // Old finally must not clear the fresh in-flight key, allowing a third duplicate fetch.
        manager.loadFromAddress(100)
        assertEquals(2, calls)
        newResponse.complete(listOf(instruction(100).copy(opcode = "fresh")))
        fresh.join()
        assertEquals("fresh", manager.getSnapshot().single().opcode)
        assertEquals(1, publications)
    }

    @Test fun cancellationCleansLoadingTokenSoSameAddressCanBeRetried() = runTest {
        val response = CompletableDeferred<List<DisasmInstruction>>()
        var calls = 0
        val manager = DisasmDataManager(0, 10000, { address, _ ->
            calls++
            Result.success(if (calls == 1) response.await() else listOf(instruction(address)))
        }, StandardTestDispatcher(testScheduler))
        val job = launch { manager.loadFromAddress(100) }
        runCurrent()
        job.cancel()
        job.join()
        manager.loadFromAddress(100)
        assertEquals(2, calls)
        assertEquals(100L, manager.getSnapshot().single().addr)
    }

    @Test fun clearCacheInvalidatesInFlightLoadsAndJumpDecorations() = runTest {
        val response = CompletableDeferred<List<DisasmInstruction>>()
        val manager = DisasmDataManager(0, 10000, { _, _ -> Result.success(response.await()) }, StandardTestDispatcher(testScheduler))
        val job = launch { manager.loadFromAddress(100) }
        runCurrent()
        manager.clearCache()
        response.complete(listOf(instruction(100)))
        job.join()
        assertEquals(DisasmSnapshot(), manager.getDataSnapshot())
    }

    @Test fun failedJumpKeepsTheGoodWindowAndDoesNotEmitAWrongIndex() = runTest {
        val manager = DisasmDataManager(0, 10000, { address, _ ->
            if (address == 100L) Result.success(listOf(instruction(address)))
            else Result.failure(IllegalStateException("offline"))
        }, StandardTestDispatcher(testScheduler))
        manager.loadFromAddress(100)
        val previous = manager.getDataSnapshot()
        assertEquals(-1, manager.loadAndFindIndex(9000))
        assertSame(previous, manager.getDataSnapshot())
    }
}

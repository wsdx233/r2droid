package top.wsdx233.r2droid.feature.hex.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import top.wsdx233.r2droid.feature.hex.data.HexDataManager.Companion.CHUNK_SIZE
import top.wsdx233.r2droid.feature.hex.data.HexDataManager.Companion.ROWS_PER_CHUNK

@OptIn(ExperimentalCoroutinesApi::class)
class HexDataManagerTest {
    @Test fun nonAlignedStartAndPartialEofKeepRowsWithinTheirChunks() = runTest {
        val start = 0x1003L
        val end = start + CHUNK_SIZE + 17
        val requests = mutableListOf<Pair<Long, Int>>()
        val firstPage = ByteArray(CHUNK_SIZE)
        val lastPage = ByteArray(17) { (it + 1).toByte() }
        val manager = HexDataManager(start, end) { address, length ->
            requests += address to length
            Result.success(if (address == start) firstPage else lastPage)
        }

        assertEquals(start, manager.viewStartAddress)
        assertEquals(end, manager.viewEndAddress)
        assertEquals(ROWS_PER_CHUNK + 2, manager.totalRows)
        assertEquals(1, manager.bytesInRow(manager.totalRows - 1))
        assertEquals(start + CHUNK_SIZE, manager.getRowAddress(ROWS_PER_CHUNK))
        assertEquals(ROWS_PER_CHUNK - 1, manager.getRowIndexForAddress(start + CHUNK_SIZE - 1))
        assertEquals(0, manager.getRowIndexForAddress(start - 1))
        assertEquals(ROWS_PER_CHUNK + 1, manager.getRowIndexForAddress(end))

        manager.loadViewport(1, 2)

        assertEquals(listOf((start + CHUNK_SIZE) to 17, start to CHUNK_SIZE), requests)
        assertSame(firstPage, manager.chunkState(0).value)
        assertSame(lastPage, manager.chunkState(1).value)
        assertNull(manager.chunkState(2).value)
    }

    @Test fun emptyRangeDoesNotReadAndHugeRangesClampToIntRows() = runTest {
        val empty = HexDataManager(17, 17) { _, _ -> error("read from empty view") }
        assertEquals(0, empty.totalRows)
        assertNull(empty.chunkState(0).value)
        empty.loadViewport(0, 1)

        val huge = HexDataManager(Long.MIN_VALUE, Long.MAX_VALUE) { _, _ -> error("unexpected read") }
        assertEquals(Int.MAX_VALUE, huge.totalRows)
        assertEquals(16, huge.bytesInRow(0))
        assertEquals(16, huge.bytesInRow(huge.totalRows - 1))
        assertEquals(Int.MAX_VALUE - 1, huge.getRowIndexForAddress(Long.MAX_VALUE))
    }

    @Test fun loadsVisibleChunksBeforeBoundedNeighborsAndDoesNotChangeGeneration() = runTest {
        val requests = mutableListOf<Int>()
        val manager = HexDataManager(0, 20L * CHUNK_SIZE) { address, length ->
            requests += (address / CHUNK_SIZE).toInt()
            Result.success(ByteArray(length))
        }
        val visible = manager.chunkState(4)
        manager.loadViewport(4, 5)
        assertEquals(listOf(4, 5, 3, 6, 2, 7), requests)
        val original = visible.value

        manager.loadViewport(15, 15)
        assertEquals(0, manager.refreshGeneration)
        assertSame(visible, manager.chunkState(4))
        assertSame(original, visible.value)
    }

    @Test fun cancelDuringPrefetchDoesNotQueueMoreOldViewportReads() = runTest {
        val requests = mutableListOf<Int>()
        val blocked = CompletableDeferred<Unit>()
        val manager = HexDataManager(0, 20L * CHUNK_SIZE) { address, length ->
            val chunk = (address / CHUNK_SIZE).toInt()
            requests += chunk
            if (chunk == 3) blocked.await()
            Result.success(ByteArray(length))
        }
        val old = launch { manager.loadViewport(4, 5) }
        runCurrent()
        assertEquals(listOf(4, 5, 3), requests)
        old.cancelAndJoin()

        manager.loadViewport(12, 12)
        assertEquals(listOf(4, 5, 3, 12, 11, 13, 10, 14), requests)
    }

    @Test fun writesInvalidateOnlyIntersectingPagesAndRefreshPreservesHandles() = runTest {
        val start = 27L
        var data: Byte = 1
        val manager = HexDataManager(start, start + 4L * CHUNK_SIZE) { _, length ->
            Result.success(ByteArray(length) { data })
        }
        val states = (0..3).map(manager::chunkState)
        manager.loadViewport(0, 3)
        val original = states.map { it.value }

        data = 2
        manager.invalidateRange(start + CHUNK_SIZE - 1, 1)
        assertNull(states[0].value)
        assertSame(original[1], states[1].value)
        manager.loadViewport(0, 3)
        assertEquals(2.toByte(), states[0].value!![CHUNK_SIZE - 1])

        data = 3
        manager.invalidateRange(start + 2L * CHUNK_SIZE - 1, 2)
        assertNull(states[1].value)
        assertNull(states[2].value)
        assertSame(original[3], states[3].value)
        manager.loadViewport(0, 3)
        assertEquals(3.toByte(), states[1].value!![CHUNK_SIZE - 1])
        assertEquals(3.toByte(), states[2].value!![0])

        data = 4
        manager.clearCache()
        assertEquals(3, manager.refreshGeneration)
        states.forEachIndexed { index, state ->
            assertNull(state.value)
            assertSame(state, manager.chunkState(index))
        }
        manager.loadViewport(0, 3)
        assertEquals(4.toByte(), states[3].value!![0])
        assertEquals(3, manager.refreshGeneration)
    }

    @Test fun delayedPreWriteReadCannotPublishAfterInvalidation() = runTest {
        val stale = CompletableDeferred<ByteArray>()
        val current = CompletableDeferred<ByteArray>()
        var requests = 0
        val manager = HexDataManager(19, 22) { _, _ ->
            Result.success(if (requests++ == 0) stale.await() else current.await())
        }
        val state = manager.chunkState(0)
        val reading = launch { manager.loadViewport(0, 0) }
        runCurrent()
        assertEquals(1, requests)
        manager.invalidateRange(20, 1)
        val reloading = launch { manager.loadViewport(0, 0) }

        stale.complete(byteArrayOf(1, 1, 1))
        runCurrent()
        assertNull(state.value)
        assertEquals(2, requests)
        val replacement = byteArrayOf(1, 2, 1)
        current.complete(replacement)
        reading.join()
        reloading.join()
        assertSame(replacement, state.value)
        assertEquals(1, manager.refreshGeneration)
    }

    @Test fun closedManagerDiscardsInflightReadAndCannotReload() = runTest {
        val response = CompletableDeferred<ByteArray>()
        var requests = 0
        val manager = HexDataManager(0, 2L * CHUNK_SIZE) { _, _ ->
            requests++
            Result.success(response.await())
        }
        val state = manager.chunkState(0)
        val reading = launch { manager.loadViewport(0, 0) }
        runCurrent()
        manager.close()
        response.complete(byteArrayOf(1))
        reading.join()
        manager.loadViewport(0, 1)

        assertEquals(1, requests)
        assertNull(state.value)
    }

    @Test fun lruEvictsOldBytesButAnObservedPageCanReloadThroughItsOldHandle() = runTest {
        var readsAtStart = 0
        val manager = HexDataManager(0, 140L * CHUNK_SIZE) { address, _ ->
            if (address == 0L) readsAtStart++
            Result.success(byteArrayOf(42)) // Even short reads publish exactly the returned bytes.
        }
        val first = manager.chunkState(0)
        manager.loadViewport(0, 0)
        val original = first.value
        assertArrayEquals(byteArrayOf(42), original)
        for (index in 1..120) manager.loadViewport(index, index)
        assertNull(first.value)
        assertEquals(0, manager.refreshGeneration)

        manager.loadViewport(0, 0)
        assertSame(first, manager.chunkState(0))
        assertNotSame(original, first.value)
        assertArrayEquals(byteArrayOf(42), first.value)
        assertEquals(2, readsAtStart)
    }

    @Test fun readCachedBytesAndReadBytesHandleChunkBoundariesAndUncachedSlices() = runTest {
        val start = 0x2000L
        val end = start + 2L * CHUNK_SIZE + 8L
        val remoteReads = mutableListOf<Pair<Long, Int>>()
        val manager = HexDataManager(start, end) { address, length ->
            remoteReads += address to length
            Result.success(ByteArray(length) { idx -> ((address - start + idx) and 0xFFL).toByte() })
        }

        // Uncached before viewport load
        assertNull(manager.readCachedBytes(start, 4))

        // Load only chunk 0
        manager.loadViewport(0, 0)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), manager.readCachedBytes(start, 4))

        // Crossing into chunk 1: chunk 1 was prefetched by loadViewport(0, 0), so cached read succeeds!
        val crossAddr = start + CHUNK_SIZE - 2
        val crossCached = manager.readCachedBytes(crossAddr, 4)
        assertArrayEquals(
            byteArrayOf(
                ((CHUNK_SIZE - 2) and 0xFF).toByte(),
                ((CHUNK_SIZE - 1) and 0xFF).toByte(),
                (CHUNK_SIZE and 0xFF).toByte(),
                ((CHUNK_SIZE + 1) and 0xFF).toByte()
            ),
            crossCached
        )

        // Reading near EOF clamps cleanly to displayEnd
        val eofBytes = manager.readBytes(end - 3, 16).getOrThrow()
        assertEquals(3, eofBytes.size)
        assertArrayEquals(
            byteArrayOf(
                ((end - start - 3) and 0xFFL).toByte(),
                ((end - start - 2) and 0xFFL).toByte(),
                ((end - start - 1) and 0xFFL).toByte()
            ),
            eofBytes
        )
    }
}

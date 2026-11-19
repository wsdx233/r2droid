package top.wsdx233.r2droid.feature.disasm.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import top.wsdx233.r2droid.core.data.model.AddressOverview
import top.wsdx233.r2droid.core.data.model.RegionKind
import top.wsdx233.r2droid.core.data.model.Section
import top.wsdx233.r2droid.feature.r2frida.data.FridaMapping

class DisasmAddressLayoutTest {
    @Test fun virtualBoundsIncludeAllMappedPermissionsAndMappedZero() {
        val layout = DisasmAddressLayout.forFile(listOf(
            Section(".head", 16, 16, "r--", 0, 0, flags = 2),
            Section(".text", 16, 16, "r-x", 0x1000, 32, flags = 6),
            Section(".bss", 0, 128, "rw-", 0x5000, -1, type = "NOBITS", flags = 3),
            Section(".debug_info", 16, 16, "---", 0, 64, flags = 0),
            Section(".debug_line", 16, 16, "---", 0x8000, 80, flags = 0),
            Section("invalid", 16, 16, "rw-", Long.MAX_VALUE - 3, 120, flags = 2)
        ), virtualAddressing = true, fileLength = 1024, isElf = true)

        assertEquals(0L, layout.startAddress)
        assertEquals(0x5080L, layout.endAddress)
        assertEquals(".head", layout.overview.regionAt(0)?.name)
        assertEquals(RegionKind.EXECUTABLE, layout.overview.regionAt(0x1000)?.kind)
        assertEquals(RegionKind.WRITABLE, layout.overview.regionAt(0x507f)?.kind)
        assertNull(layout.overview.regionAt(0x30))
    }

    @Test fun physicalBoundsUseFileLengthAndExcludeUnbackedSections() {
        val layout = DisasmAddressLayout.forFile(listOf(
            Section(".text", 8, 4096, "r-x", 0x1000, 16),
            Section(".debug_info", 4, 0, "---", 0, 40),
            Section(".data", 80, 4096, "rw-", 0x3000, 64),
            Section("bad", 12, 12, "rw-", 0x5000, -1),
            Section("overflow", Long.MAX_VALUE, 12, "rw-", 0x5000, 1),
            Section("past EOF", 4, 4, "r--", 0x5000, 100),
            Section("no bytes", 0, 50, "rw-", 0x6000, 5),
            Section(".bss", 8, 50, "rw-", 0x6000, 32, type = "NOBITS")
        ), virtualAddressing = false, fileLength = 96, isElf = true)

        assertEquals(0L, layout.startAddress)
        assertEquals(96L, layout.endAddress)
        assertNull(layout.overview.regionAt(1))
        assertEquals(".text", layout.overview.regionAt(23)?.name)
        assertNull(layout.overview.regionAt(24))
        assertNull(layout.overview.regionAt(32))
        assertEquals(".debug_info", layout.overview.regionAt(40)?.name)
        assertEquals(".data", layout.overview.regionAt(95)?.name)
        assertNull(layout.overview.regionAt(96))
    }

    @Test fun fridaLayoutUsesMappingProtectionAndSkipsInvalidRanges() {
        val layout = DisasmAddressLayout.forFrida(listOf(
            FridaMapping("0x1000", 16, "r-x", "/lib/test.so"),
            FridaMapping("8192", 8, "rw-", null),
            FridaMapping("invalid", 4, "rw-", null),
            FridaMapping("0x7fffffffffffffff", 8, "r--", null)
        ))
        assertEquals(0x1000L, layout.startAddress)
        assertEquals(0x2008L, layout.endAddress)
        assertEquals("/lib/test.so", layout.overview.regionAt(0x1000)?.name)
        assertEquals(RegionKind.EXECUTABLE, layout.overview.regionAt(0x1000)?.kind)
        assertEquals(RegionKind.WRITABLE, layout.overview.regionAt(0x2000)?.kind)
    }

    @Test fun emptyAndUnknownModeFallBackWithoutInventingRegions() {
        assertEquals(false, DisasmAddressLayout.parseIoVa(" false\n"))
        assertEquals(true, DisasmAddressLayout.parseIoVa("1"))
        assertNull(DisasmAddressLayout.parseIoVa("unsupported"))
        val section = Section(".text", 16, 16, "r-x", 0x1000, 16)
        val fileFallback = DisasmAddressLayout.forFile(listOf(section), null, 1024, isElf = false)
        assertSame(AddressOverview.Empty, fileFallback.overview)
        assertEquals(1024L, fileFallback.endAddress)
        assertEquals(1024L * 1024, DisasmAddressLayout.forFrida(emptyList()).endAddress)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun liveSessionKeepsAnEmptyCaptureAfterItsFirstObserverIsCancelled() = runTest {
        val session = SessionAddressLayout()
        val response = CompletableDeferred<Unit>()
        val (first, started) = session.acquire(backgroundScope) {
            response.await()
            DisasmAddressLayout.forFrida(emptyList())
        }
        assertTrue(started)
        val observer = launch { first.await() }
        runCurrent()
        observer.cancel()
        val (reselected, startedAgain) = session.acquire(backgroundScope) { error("already capturing") }
        assertFalse(startedAgain)
        assertSame(first, reselected)
        response.complete(Unit)
        val empty = reselected.await()
        assertSame(empty, session.acquire(backgroundScope) { error("already frozen") }.first.await())
        assertSame(AddressOverview.Empty, empty.overview)

        val (replacement, startedReplacement) = SessionAddressLayout().acquire(backgroundScope) {
            DisasmAddressLayout.forFrida(listOf(FridaMapping("0x3000", 16, "r-x", null)))
        }
        assertTrue(startedReplacement)
        assertEquals(0x3000L, replacement.await().startAddress)
    }
}

package top.wsdx233.r2droid.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.wsdx233.r2droid.core.data.model.RegionKind.EXECUTABLE
import top.wsdx233.r2droid.core.data.model.RegionKind.READ_ONLY
import top.wsdx233.r2droid.core.data.model.RegionKind.WRITABLE

class AddressOverviewTest {
    @Test fun nestedRegionsWinOnlyInsideTheirHalfOpenRange() {
        val image = AddressRegion(100, 900, "image", READ_ONLY)
        val text = AddressRegion(200, 600, ".text", EXECUTABLE)
        val plt = AddressRegion(200, 240, ".plt", EXECUTABLE)
        val data = AddressRegion(1000, 1100, ".data", WRITABLE)
        val overview = AddressOverview.of(listOf(plt, data, text, image))

        assertEquals(100L, overview.startAddress)
        assertEquals(1100L, overview.endAddress)
        assertNull(overview.regionAt(99))
        assertEquals(image, overview.regionAt(199))
        assertEquals(plt, overview.regionAt(200))
        assertEquals(text, overview.regionAt(240)) // offset label must still use the original .text base
        assertEquals(image, overview.regionAt(600))
        assertNull(overview.regionAt(900))
        assertEquals(data, overview.regionAt(1000))
        assertNull(overview.regionAt(1100))
    }

    @Test fun rasterCombinesSamePermissionCoverageAndKeepsSubpixelSectionsOutOfGaps() {
        val overview = AddressOverview.of(listOf(
            AddressRegion(0, 31, "A", EXECUTABLE), AddressRegion(34, 58, "B", EXECUTABLE),
            AddressRegion(58, 100, "C", WRITABLE), AddressRegion(125, 135, "D", READ_ONLY),
            AddressRegion(200, 600, ".text", EXECUTABLE), AddressRegion(600, 601, "tiny", EXECUTABLE),
            AddressRegion(601, 1001, ".plt", EXECUTABLE)
        ))
        assertEquals(listOf(
            OverviewBand(0, 1, EXECUTABLE, false), // 55% executable, 42% writable, 3% unassigned
            OverviewBand(2, 6, EXECUTABLE, false), // row 1 is mostly a gap
            OverviewBand(6, 10, EXECUTABLE, true)  // .plt is visible; the 1-byte section is not inflated
        ), overview.rasterize(0, 1001, 10))
    }

    @Test fun clippingAtHighVirtualAddressesPreservesSmallRangesAndGaps() {
        val base = Long.MAX_VALUE - 2000
        val overview = AddressOverview.of(listOf(
            AddressRegion(base + 50, base + 300, ".text", EXECUTABLE),
            AddressRegion(base + 500, base + 1000, ".data", WRITABLE)
        ))
        assertEquals(listOf(OverviewBand(0, 2, EXECUTABLE, false), OverviewBand(4, 8, WRITABLE, false)),
            overview.rasterize(base + 100, base + 901, 8))
        assertEquals(listOf(OverviewBand(0, 8, WRITABLE, false)), overview.rasterize(base + 999, base + 1000, 8))
        assertEquals(emptyList<OverviewBand>(), overview.rasterize(base + 1000, Long.MAX_VALUE, 8))
        assertNull(AddressRegion.fromSize(base, 2001, "invalid", "r-x"))
    }
}

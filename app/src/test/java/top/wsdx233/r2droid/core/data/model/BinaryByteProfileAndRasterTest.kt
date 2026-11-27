package top.wsdx233.r2droid.core.data.model

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Random

class BinaryByteProfileAndRasterTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun samplerDistinguishesZeroAsciiAndHighEntropyRegionsAndComputesEntropyFallback() {
        val file = File(temp.root, "sample.bin")
        val bytes = ByteArray(4096)
        // [0..1024): zero-filled
        // [1024..2048): printable ASCII C-string table
        val text = "hello_world\u0000r2droid_scrollbar\u0000section_preview\u0000"
        for (i in 0 until 1024) bytes[1024 + i] = text[i % text.length].code.toByte()
        // [2048..4096): pseudo-random high-entropy payload
        val rng = Random(42L)
        val high = ByteArray(2048).also(rng::nextBytes)
        System.arraycopy(high, 0, bytes, 2048, 2048)
        file.writeBytes(bytes)

        val sections = listOf(
            Section(".bss", size = 0, vSize = 1024, perm = "rw-", vAddr = 0x1000, pAddr = -1, type = "NOBITS", flags = 3),
            Section(".rodata", size = 1024, vSize = 1024, perm = "r--", vAddr = 0x2000, pAddr = 1024, flags = 2),
            Section(".blob", size = 2048, vSize = 2048, perm = "r-x", vAddr = 0x3000, pAddr = 2048, flags = 6)
        )

        val profile = BinaryByteSampler.sampleForLayout(
            file = file,
            sections = sections,
            virtualAddressing = true,
            startAddress = 0x1000,
            endAddress = 0x3800,
            isElf = true,
            buckets = 64,
            sampleWindowBytes = 256
        )
        assertNotNull(profile)
        profile!!

        val bssBucket = profile.bucketForAddress(0x1200)
        assertEquals(255, profile.zeroRatioAt(bssBucket))
        assertEquals(0, profile.entropyAt(bssBucket))

        val roBucket = profile.bucketForAddress(0x2200)
        assertTrue("Expected high ASCII ratio in .rodata, got ${profile.asciiRatioAt(roBucket)}", profile.asciiRatioAt(roBucket) > 200)

        val blobBucket = profile.bucketForAddress(0x3400)
        assertTrue("Expected high entropy in .blob, got ${profile.entropyAt(blobBucket)}", profile.entropyAt(blobBucket) > 200)

        val fallback = BinaryByteSampler.computeEntropyData(file, blockCount = 16)
        assertNotNull(fallback)
        assertEquals(16, fallback!!.entropy.size)
        assertEquals(0, fallback.entropy[0].value)
        assertTrue(fallback.entropy.last().value > 200)
    }

    @Test fun detailedRasterBreaksUpUniformSectionWithFunctionsStringsAndMarkers() {
        val overview = AddressOverview.of(listOf(
            AddressRegion(0x1000, 0x2000, ".text", RegionKind.EXECUTABLE),
            AddressRegion(0x2000, 0x3000, ".rodata", RegionKind.READ_ONLY)
        ))
        val overlay = AddressStructureOverlay.of(
            functions = listOfNotNull(
                FunctionSpan.from(0x1000, 0x200, "sym.imp.printf", 1),
                FunctionSpan.from(0x1200, 0x400, "main", 32),
                FunctionSpan.from(0x1600, 0x300, "helper", 2)
                // [0x1900..0x2000) intentionally unanalyzed gap inside .text
            ),
            strings = listOfNotNull(
                StringSpan.from(0x2100, 0x300)
            ),
            markers = listOf(
                OverviewMarker(0x1200, OverviewMarkerKind.ENTRY),
                OverviewMarker(0x1200, OverviewMarkerKind.BREAKPOINT), // higher priority wins at same row
                OverviewMarker(0x2200, OverviewMarkerKind.ANNOTATION)
            )
        )

        assertEquals("main", overlay.functionAt(0x1350)?.name)
        assertNull(overlay.functionAt(0x1950))

        val raster = OverviewRasterizer.rasterize(
            overview = overview,
            start = 0x1000,
            endExclusive = 0x3000,
            rows = 64,
            overlay = overlay
        )
        assertFalse(raster.isEmpty)

        // PLT thunk [0..3], complex main [4..11], simple helper [12..17], and unanalyzed tail [18..31]
        val pltColor = raster.colors[2]
        val mainColor = raster.colors[8]
        val helperColor = raster.colors[14]
        val gapColor = raster.colors[24]
        assertNotEquals(pltColor, mainColor)
        assertNotEquals(mainColor, helperColor)
        assertNotEquals(helperColor, gapColor)
        assertTrue("Complex function should have wider silhouette than gap", raster.widthFactors[8] > raster.widthFactors[24])

        // String region inside .rodata should differ from plain .rodata
        val strColor = raster.colors[36]
        val plainRoColor = raster.colors[56]
        assertNotEquals(strColor, plainRoColor)

        // Breakpoint outranks Entry at the same row
        assertEquals(2, raster.markers.size)
        assertTrue(raster.markers.any { it.kind == OverviewMarkerKind.BREAKPOINT })
        assertTrue(raster.markers.any { it.kind == OverviewMarkerKind.ANNOTATION })
    }
}

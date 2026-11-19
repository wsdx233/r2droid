package top.wsdx233.r2droid.feature.hex.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexGridGeometryTest {
    @Test
    fun eachCellAnchorHitsTheSameByteInBothPanesAndVisualLines() {
        for (width in listOf(237, 353, 601)) {
            val grid = grid(width)
            for (pane in HexGridPane.entries) {
                for (index in 0 until 16) {
                    val anchor = grid.cellBounds(index, pane)
                    assertEquals(index / 8 * grid.lineHeight, anchor.top)
                    assertEquals(grid.lineHeight, anchor.height)
                    assertEquals(HexGridHit(index, pane), grid.hitTest(
                        (anchor.left + anchor.right) / 2f,
                        (anchor.top + anchor.bottom) / 2f,
                        available = 16
                    ))
                }
            }
        }
    }

    @Test
    fun addressDividersPaddingAndOuterEdgesDoNotSelectBytes() {
        val grid = grid()
        val hex = HexGridPane.Hex
        val ascii = HexGridPane.Ascii
        val y = grid.lineHeight / 2f

        assertNull(grid.hitTest(-1f, y, 16))
        assertNull(grid.hitTest(grid.addressWidth / 2, y, 16))
        assertNull(grid.hitTest(grid.addressWidth, y, 16))
        assertNull(grid.hitTest(grid.groupDividerLeft, y, 16))
        assertNull(grid.hitTest(grid.asciiDividerLeft, y, 16))
        assertNull(grid.hitTest(grid.cellLeft(ascii, 0) - .5f, y, 16))
        assertNull(grid.hitTest(grid.width.toFloat(), y, 16))
        assertNull(grid.hitTest(grid.cellLeft(hex, 0), -.1f, 16))
        assertNull(grid.hitTest(grid.cellLeft(hex, 0), 2f * grid.lineHeight, 16))
        assertEquals(HexGridHit(4, hex), grid.hitTest(grid.cellLeft(hex, 4), y, 16))
        assertEquals(HexGridHit(0, ascii), grid.hitTest(grid.cellLeft(ascii, 0), y, 16))
        assertEquals(HexGridHit(8, hex), grid.hitTest(grid.cellLeft(hex, 0), grid.lineHeight.toFloat(), 16))
    }

    @Test
    fun eofAndUnloadedCellsDoNotClampToTheLastAvailableByte() {
        val grid = grid()
        val y = grid.lineHeight * 1.5f
        for (pane in HexGridPane.entries) {
            val lastLoaded = grid.cellLeft(pane, 1) + 1f
            val missing = grid.cellLeft(pane, 2) + 1f
            assertEquals(HexGridHit(9, pane), grid.hitTest(lastLoaded, y, 10))
            assertNull(grid.hitTest(missing, y, 10))
            assertNull(grid.hitTest(lastLoaded, y, 8))
            assertNull(grid.hitTest(lastLoaded, y, 0))
        }
    }

    @Test
    fun viewExtentDeterminesHeightRatherThanChunkAvailability() {
        val missingFullRow = HexGridRowBytes(null, 128, 16)
        val shortChunk = HexGridRowBytes(ByteArray(140), 128, 16)
        val loadedFullRow = HexGridRowBytes(ByteArray(144), 128, 16)
        assertEquals(0, missingFullRow.available)
        assertEquals(12, shortChunk.available)
        assertEquals(16, loadedFullRow.available)
        assertEquals(listOf(2, 2, 2), listOf(missingFullRow.lineCount, shortChunk.lineCount, loadedFullRow.lineCount))
        assertEquals(2, HexGridRowBytes(null, 0, 9).lineCount)
        assertEquals(1, HexGridRowBytes(null, 0, 8).lineCount)
        assertEquals(0, HexGridRowBytes(null, 0, 0).lineCount)
    }

    @Test
    fun windowsUseOffsetAndRejectUnavailablePopupTargets() {
        val bytes = byteArrayOf(0x37, 0xff.toByte(), 0x80.toByte(), 0x2e)
        val partial = HexGridRowBytes(bytes, 1, 2)
        val address = 0x1000_0000_0000L
        assertEquals(0xff, partial[0])
        assertEquals(0x80, partial[1])
        assertEquals(1, partial.indexOf(address, address + 1))
        assertEquals(-1, partial.indexOf(address, address + 2)) // Past EOF even though the chunk contains it.
        assertEquals(-1, partial.indexOf(address, address - 1))
        assertEquals(-1, HexGridRowBytes(bytes, 2, 8).indexOf(address, address + 2)) // Short chunk.
        assertEquals(-1, HexGridRowBytes(null, 0, 8).indexOf(address, address))
        assertEquals(0, HexGridRowBytes(bytes, bytes.size, 16).available)
    }

    @Test
    fun addressesAreUppercaseZeroPaddedAndNeverTruncated() {
        assertEquals("00002F", formatHexGridAddress(0x2fL))
        assertEquals("1234567ABCDEF", formatHexGridAddress(0x1234567abcdefL))
        assertEquals("8000000000000000", formatHexGridAddress(Long.MIN_VALUE))
        assertEquals("FFFFFFFFFFFFFFFF", formatHexGridAddress(-1L))
    }

    private fun grid(width: Int = 353) = HexGridGeometry(
        width = width,
        lineHeight = 23,
        divider = 1f,
        preferredAddressWidth = 70f,
        preferredAsciiCellWidth = 12f,
        preferredAsciiPadding = 4f,
        minHexCellWidth = 18f
    )
}

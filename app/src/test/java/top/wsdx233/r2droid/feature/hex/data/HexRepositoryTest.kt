package top.wsdx233.r2droid.feature.hex.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HexRepositoryTest {
    @Test fun shortReadsKeepActualBytesIncludingNulAndUnsignedValues() {
        assertArrayEquals(byteArrayOf(0, 127, -128, -1), decodeHexDump("[0,127,128,255]\n", 4096))
        assertArrayEquals(ByteArray(0), decodeHexDump("[]", 15))
    }

    @Test fun malformedOrOversizedResponsesDoNotProduceBytes() {
        for (output in listOf("not json", "[1,2]", "[-1]", "[256]", "[1.5]", "[\"1\"]")) {
            assertTrue(output, runCatching { decodeHexDump(output, 1) }.isFailure)
        }
    }
}

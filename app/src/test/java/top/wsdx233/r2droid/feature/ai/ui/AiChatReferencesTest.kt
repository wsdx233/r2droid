package top.wsdx233.r2droid.feature.ai.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiChatReferencesTest {
    private fun tokens(text: String, start: Int = 0, end: Int = text.length) = buildList {
        forEachChatReference(text, start, end) { a, b -> add(text.substring(a, b)) }
    }

    @Test
    fun matchesWholeReferencesInProseWithoutSwallowingPunctuation() {
        assertEquals(listOf("0x12345", "fcn.001234", "sym.imp.printf", "0XFE", "main", "section..text", "entry0"),
            tokens("查看0x12345，再看(fcn.001234)、sym.imp.printf. [0XFE] main section..text entry0。"))
        assertEquals(emptyList<String>(), tokens("my0x123 0x123z 0x123456789abcdef01 0x name.sym.mainly filename.c sym. loc. ${"sym." + "x".repeat(300)}"))
        assertEquals(emptyList<String>(), tokens("0x12345", end = 5))
        assertEquals(emptyList<String>(), tokens("my0x12345", start = 2))
    }

    @Test
    fun invalidExpressionsNeverReachThePipeAndMissingNamesAreNotZero() = runBlocking {
        for (token in listOf("sym.main;other", "sym.main\n", "0x1+2", "fcn.*", "sym.name @ 1", "0x8000000000000000")) {
            assertNull(resolveChatReference(token) { error("Not a flag lookup: $token") })
        }
        assertEquals(0L, resolveChatReference("0X000") { error("Literal triggered a lookup") })
        assertNull(resolveChatReference("sym.missing") { "" })
        assertEquals(0L, resolveChatReference("sym.base") { "0x0\n" })
    }
}

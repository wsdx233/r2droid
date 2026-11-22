package top.wsdx233.r2droid.feature.annotations

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticOperationsTest {
    @Test
    fun structChoicesRejectCommandSyntaxAndApplyTheResolvedNativeSize() = runBlocking {
        val session = RecordingSession(mapOf(
            "pf." to "'pf.Header dw first second\n'pf.Bad;command b value\n",
            "tsj" to """[{"name":"Pair","format":"dw first second"},
                {"name":"Unsafe","format":"w x;command"},{"name":"Bad@0","format":"w x"}]""",
            "pfs.Header" to "6\n",
            "tss Pair" to "6\n"
        ))
        assertEquals(listOf("Header", "Pair"), session.semanticChoices(AnnotationAction.STRUCT))

        assertTrue(runCatching { session.applySemantic(AnnotationAction.STRUCT, 0x40, "Unsafe") }.isFailure)
        assertEquals(emptyList<String>(), session.mutations)

        session.applySemantic(AnnotationAction.STRUCT, 0x40, "Header")
        session.applySemantic(AnnotationAction.STRUCT, 0x80, "Pair")
        assertEquals(listOf("Cf 6 Header @ 0x40!6", "Cf 6 dw first second @ 0x80!6"),
            session.mutations.filter { it.startsWith("Cf ") })
    }

    @Test
    fun functionsResolveInteriorAddressesWithoutNarrowingUnsignedAddresses() = runBlocking {
        val selected = 0xffff800000000024uL.toLong()
        val start = 0xffff800000000020uL
        val oldEnd = 0xffff800000000030uL
        val newEnd = 0xffff80000000002cuL
        val session = RecordingSession(mapOf(
            "afij @ 0xffff800000000024" to """[{"addr":$start,"maxaddr":$oldEnd}]""",
            "afij @ 0xffff800000000020" to """[{"addr":$start,"maxaddr":$oldEnd}]""",
            "afij @ 0x80" to "[]"
        ), afterResize = mapOf("afij @ 0xffff800000000020" to """[{"addr":$start,"maxaddr":$newEnd}]"""))
        assertEquals(start.toLong() to oldEnd.toLong(), session.functionBounds(selected))

        assertTrue(runCatching { session.applySemantic(AnnotationAction.RESIZE_FUNCTION, selected, start.toString()) }.isFailure)
        assertTrue(runCatching { session.applySemantic(AnnotationAction.RESIZE_FUNCTION, 0x80, "0x90") }.isFailure)
        assertTrue(runCatching { session.applySemantic(AnnotationAction.DELETE_FUNCTION, 0x80, "") }.isFailure)
        assertEquals(emptyList<String>(), session.mutations)

        session.applySemantic(AnnotationAction.RESIZE_FUNCTION, selected, newEnd.toString())
        session.applySemantic(AnnotationAction.DELETE_FUNCTION, selected, "")
        assertEquals(listOf("afu 0xffff80000000002c @ 0xffff800000000020", "af- 0xffff800000000020"), session.mutations)
    }

    @Test
    fun resizeReportsWhenNativeAnalysisKeepsThePreviousEnd() = runBlocking {
        val function = """[{"addr":16,"maxaddr":32}]"""
        val session = RecordingSession(mapOf("afij @ 0x14" to function, "afij @ 0x10" to function))
        val failure = runCatching { session.applySemantic(AnnotationAction.RESIZE_FUNCTION, 0x14, "0x18") }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("actual end: 0x20"))
        assertEquals(listOf("afu 0x18 @ 0x10"), session.mutations)
    }

    @Test
    fun resizeRejectsAnEntrySharedByMultipleFunctions() = runBlocking {
        val function = """{"addr":16,"maxaddr":32}"""
        val session = RecordingSession(mapOf(
            "afij @ 0x14" to "[$function]",
            "afij @ 0x10" to """[$function,{"addr":8,"maxaddr":18}]"""
        ))
        val failure = runCatching { session.applySemantic(AnnotationAction.RESIZE_FUNCTION, 0x14, "0x18") }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("unique function"))
        assertEquals(emptyList<String>(), session.mutations)
    }

    @Test
    fun operandAnnotationsUseNativeEnumAndPointerHints() = runBlocking {
        val session = RecordingSession(mapOf(
            "tej" to """{"Mode":{"On":1},"Bad;command":{"Off":0}}""",
            "ahj 0x40" to "[]",
            "aoj 1 @ 0x40" to """[{"ptr":18446744073709551615,"opex":{"operands":[{"type":"reg","value":"eax"},
                {"type":"imm","value":4294967296}]}}]"""
        ))
        assertEquals(listOf("Mode"), session.semanticChoices(AnnotationAction.ENUM))
        assertEquals("0x100000000", session.suggestOffset(0x40))
        assertTrue(runCatching { session.applySemantic(AnnotationAction.OFFSET, 0x40, "0x10+2") }.isFailure)
        assertTrue(runCatching { session.applySemantic(AnnotationAction.ENUM, 0x40, "Missing") }.isFailure)
        assertEquals(emptyList<String>(), session.mutations)

        session.applySemantic(AnnotationAction.OFFSET, 0x40, "0x100000000")
        session.applySemantic(AnnotationAction.ENUM, 0x40, "Mode")
        assertEquals(listOf("ahp 0x100000000 @ 0x40", "ahi- @ 0x40", "ahie- @ 0x40",
            "ahie Mode 0 @ 0x40", "ahi- @ 0x40", "ahp- @ 0x40"), session.mutations)
    }

    private class RecordingSession(
        private val replies: Map<String, String>,
        private val afterResize: Map<String, String> = emptyMap()
    ) : AnnotationSession {
        val mutations = mutableListOf<String>()

        override suspend fun <T> transaction(operation: String, markDirty: Boolean, block: ((String) -> String) -> T): T =
            block { command ->
                val changedReply = if (mutations.any { it.startsWith("afu ") }) afterResize[command] else null
                changedReply ?: replies[command] ?: run {
                    check(markDirty) { "Unexpected query: $command" }
                    mutations += command
                    ""
                }
            }
    }
}

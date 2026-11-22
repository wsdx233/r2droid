package top.wsdx233.r2droid.feature.disasm.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.wsdx233.r2droid.core.data.model.DisasmInstruction

class DisasmAnnotationsTest {
    private fun row(at: Long, hex: String) = DisasmInstruction(at, "nop", hex, "nop", hex.length / 2, "nop", "cpu")

    @Test fun typedDataAtZeroUsesItsNativeExtentInsteadOfTheRawOpcodes() = runBlocking {
        val annotations = DisasmAnnotations.load { when (it) {
            "C,type/eq/Cs,:json" -> """[{"size":3,"type":"Cs","string":"Hi"}]"""
            "C,type/eq/Cf,:json" -> """[{"addr":3,"size":4,"type":"Cf","string":"Point"}]"""
            "ahj" -> """[{"addr":7,"ptr":0}]"""
            else -> error(it)
        } }
        assertEquals(0L, annotations.rowStart(2))
        val rows = annotations.render(listOf(row(0, "4869"), row(2, "00"), row(3, "1122"), row(5, "3344"), row(7, "90"))) { _, _ -> error("Bytes already present") }
        assertEquals(listOf(0L, 3L, 7L), rows.map { it.addr })
        assertEquals(listOf(".string \"Hi\"", "pf Point", "nop"), rows.map { it.disasm })
        assertEquals(listOf("486900", "11223344", "90"), rows.map { it.bytes })
        assertEquals(listOf(3, 4, 1), rows.map { it.size })
        assertEquals(0L, rows.last().ptr)
    }

    @Test fun subsequentCodeMustBeDecodedFromTheEndOfTheStructure() = runBlocking {
        val annotations = DisasmAnnotations.load { if (it.contains("Cf")) """[{"addr":2,"size":3,"string":"S"}]""" else "[]" }
        val prefix = annotations.render(listOf(row(0, "11223344"), row(4, "55"))) { _, _ -> error("Bytes already present") }
        assertEquals(".hex 1122", prefix.single().disasm)
        assertEquals(2L, annotations.rowStart(4))
        val data = annotations.render(listOf(row(2, "33445566"), row(6, "90"))) { _, _ -> error("Bytes already present") }
        assertEquals("334455", data.single().bytes)
        assertEquals(5L, data.last().addr + data.last().size) // next pdj must start at 5, not 6
    }
}

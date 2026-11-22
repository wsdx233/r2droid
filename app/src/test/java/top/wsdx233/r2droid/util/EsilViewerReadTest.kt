package top.wsdx233.r2droid.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EsilViewerReadTest {
    @Test fun hexDisassemblyAndLayoutQueriesRemainAvailable() {
        listOf(
            "pxj 4096 @ 4194304",
            "pdj 100 @ 4194304",
            "ps @ 0x400008",
            "pdj 1 @ 0xffffffffffff0000",
            "afij @ 4194304",
            "axfj @ 4194304",
            "iSj",
            "ahj",
            "e io.va",
            "ij"
        ).forEach { command -> assertTrue(command, R2PipeManager.isEsilViewerRead(command)) }
    }

    @Test fun noMutationOrGeneralR2SyntaxIsTreatedAsARead() {
        listOf(
            "s 4096",
            "wx aa @ 4096",
            "wci",
            "wc--",
            "e io.cache=false",
            "e asm.bits=64",
            "=h&",
            "pd 1 @ 4096", // Formatted disassembly can emulate instructions when asm.emu is on.
            "pdf @ 4096",
            "",
            "pxj 16 @ 4096;wx aa",
            "pxj 16 @ 4096\nwc--",
            "pxj 16 @ 4096\u0000",
            "pxj 16 @ sym.main",
            "pxj 16 @ 4096+4",
            "pxj 16 @ $(s 0)",
            "pxj 16 @ `s 0`",
            "pxj 16 @ [4096]",
            "pxj 16 @@ 4096",
            "pxj 16 @ 4096 @!8",
            "pxj 16 @ 4096 > copy.hex",
            "2pdj 1 @ 4096",
            "pdj -1 @ 4096",
            "pdj 4097 @ 4096",
            "afij @ 4096 # comment",
            "e io.va = true"
        ).forEach { command -> assertFalse(command, R2PipeManager.isEsilViewerRead(command)) }
    }
}

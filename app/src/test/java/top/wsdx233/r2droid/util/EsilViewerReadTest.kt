package top.wsdx233.r2droid.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EsilViewerReadTest {
    @Test fun readOnlyViewerConsoleAndAiCommandsRemainAvailableDuringEsil() {
        listOf(
            // Viewer & layout queries
            "pxj 4096 @ 4194304",
            "pdj 100 @ 4194304",
            "ps @ 0x400008",
            "pdj 1 @ 0xffffffffffff0000",
            "afij @ 4194304",
            "axfj @ 4194304",
            "iSj",
            "ahj",
            "e io.va",
            "ij",
            "C,type/eq/Cs,:json",
            "C,type/eq/Cf,:json",
            // AI & manual read-only analysis commands
            "afl",
            "aflj",
            "pdf @ main",
            "pd 120 @ 0x401000",
            "pdg @ sym.main",
            "pdgj @ 0x401000",
            "pdc @ main",
            "agj @ main",
            "iz",
            "izj",
            "iI",
            "ii",
            "iE",
            "axt @ sym.main",
            "axf @ sym.main",
            "px 64 @ sp+0x10",
            "pxr 64 @ sp",
            "fd @ 0x401000",
            "?v main+0x10",
            "?v rax>0",
            "aer",
            "aer rip",
            "aerj",
            "ar",
            "arj",
            "omj",
            "oj",
            "dbj",
            "s",
            "s 4096",
            "s main",
            "afl ~main",
            "e~asm",
            "f~sym",
            "dbj@0x1000",
            "f?sym.main;?ne ?v sym.main",
            "tccj",
            "t uint32_t",
            "ts Foo",
            "agf@0x1000 > 0 ; agj @ 0x1000",
            "CCu note @ 0x1000",
            "afn new_name @ 0x1000",
            "e asm.pseudo=true",
            "wt /tmp/out.bin 16 @ 0x1000",
            "ij; aflj"
        ).forEach { command ->
            assertFalse("Expected allowed during ESIL: $command", R2PipeManager.isEsilBlockedCommand(command))
            assertTrue(command, R2PipeManager.isEsilViewerRead(command))
        }
    }

    @Test fun mutatingOrSmuggledCommandsAreBlockedDuringEsil() {
        listOf(
            // Writes & write-cache mutations
            "wx aa @ 4096",
            "wa nop @ 0x1000",
            "w hello @ 0x1000",
            "2wx 90 @ 4096",
            "wci",
            "wc--",
            "wc++",
            "yy",
            "r 1024",
            "rm foo",
            // Conflicting ESIL / IO config mutations
            "e io.cache=false",
            "e io.va = true",
            "e esil.romem=true",
            "e cfg.debug=true",
            "e-",
            // ESIL & register mutations outside lease
            "aes",
            "aeso",
            "aei-",
            "aer rip=0x1000",
            "ar rax=42",
            "ar0",
            "ara-",
            // File/map/debugger/session mutations
            "oo+",
            "ood",
            "o- 3",
            "on malloc://1024 0x1000",
            "om 3 0x1000",
            "ds",
            "dc",
            "db 0x1000",
            "dr rip=0x1000",
            "k aeim.fd=0",
            "q",
            "q!",
            "=h&",
            "!ls",
            ". script.r2",
            // Empty / smuggling
            "",
            "   ",
            "pxj 16 @ 4096;wx aa",
            "pxj 16 @ 4096\nwc--",
            "pxj 16 @ 4096\u0000"
        ).forEach { command ->
            assertTrue("Expected blocked during ESIL: $command", R2PipeManager.isEsilBlockedCommand(command))
            assertFalse(command, R2PipeManager.isEsilViewerRead(command))
        }
    }
}

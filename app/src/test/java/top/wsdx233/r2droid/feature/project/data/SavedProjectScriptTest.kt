package top.wsdx233.r2droid.feature.project.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter

class SavedProjectScriptTest {
    @Test
    fun `cached project serializes analysis but not cache commands or old descriptors`() {
        val script = """
            # r2 rdb project file
            # eval
            'e asm.bits = 64
            'e io.cache = true
            'e io.cache.read = true
            e io.cache.write=true
            o "/project/old target" 0x00400000 rw-
            onnu /project/old-target r--
            o=3
            omu 3 0x00400000 0x00000010 0x00000000 r-x
            # functions
            af+ 0x00400000 entry0
            CCu eHggaXMgbm90IGEgY29tbWFuZA== @ 0x00400000
            wx 9090 @ 0x00400000 # replaces: cccc
            # seek
            s 0x00400000
        """.trimIndent()
        val expected = """
            # r2 rdb project file
            # eval
            'e asm.bits = 64
            omu 3 0x00400000 0x00000010 0x00000000 r-x
            # functions
            af+ 0x00400000 entry0
            CCu eHggaXMgbm90IGEgY29tbWFuZA== @ 0x00400000
            # seek
            s 0x00400000
        """.trimIndent() + "\n"
        val out = StringWriter()
        writeUnpatchedAnalysisScript(script.reader().buffered(), out)
        assertEquals(expected, out.toString())
    }
}

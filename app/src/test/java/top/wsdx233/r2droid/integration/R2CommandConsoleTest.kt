package top.wsdx233.r2droid.integration

import org.junit.Assert.assertEquals
import org.junit.AssumptionViolatedException
import org.junit.Test
import org.radare.r2pipe.kotlin.LaunchSpec
import org.radare.r2pipe.kotlin.R2Pipe
import org.radare.r2pipe.kotlin.R2PipeHttp
import org.radare.r2pipe.kotlin.R2PipeSession
import top.wsdx233.r2droid.util.R2CommandConsole
import java.io.IOException
import java.net.ServerSocket

/** Uses a real local r2 (or R2_TEST_BIN) so the stdout and stderr readers run independently. */
class R2CommandConsoleTest {
    private val binary = System.getenv("R2_TEST_BIN") ?: "r2"
    private val console = R2CommandConsole { _, _ -> }

    private fun launch(vararg args: String): LaunchSpec {
        try {
            check(ProcessBuilder(binary, "-v").start().waitFor() == 0)
        } catch (e: IOException) {
            throw AssumptionViolatedException("Host radare2 not installed", e)
        }
        return LaunchSpec(listOf(binary, "-N", *args, "-"))
    }

    @Test fun stdioConsole() {
        R2Pipe.open(launch("-q0"), logger = console).use(::checkCommandOutput)
    }

    @Test fun localHttpConsole() {
        val port = ServerSocket(0).use { it.localPort }
        R2PipeHttp.spawn(launch("-qc=H", "-e", "http.port=$port"), port, logger = console).use(::checkCommandOutput)
    }

    private fun checkCommandOutput(pipe: R2PipeSession) {
        // Machine queries stay parseable; their warnings must not appear in the next command.
        assertEquals("[1]\n", pipe.cmdj("?e [1];?ee unrelated"))
        assertEquals("[stderr]\n诊断\n", console.execute(pipe, "?ee 诊断").displayText())
        assertEquals("normal\n[stderr]\none\ntwo\n", console.execute(pipe, "?ee one;?e normal;?ee two").displayText())
        assertEquals("normal\n", console.execute(pipe, "?e normal").displayText())
        assertEquals("", console.execute(pipe, "s 0").displayText())
    }
}

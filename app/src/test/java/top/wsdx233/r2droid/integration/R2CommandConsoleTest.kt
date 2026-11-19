package top.wsdx233.r2droid.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import org.radare.r2pipe.kotlin.LaunchSpec
import org.radare.r2pipe.kotlin.R2Pipe
import org.radare.r2pipe.kotlin.R2PipeHttp
import org.radare.r2pipe.kotlin.R2PipeLogLevel
import org.radare.r2pipe.kotlin.R2PipeSession
import top.wsdx233.r2droid.util.R2CommandConsole
import java.io.IOException
import java.io.InputStream
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

    @Test fun strictCaptureSeparatesOutput() {
        R2Pipe.open(launch("-q0"), logger = console).use { pipe ->
            pipe.cmd("?ee previous warning")
            val output = console.execute(pipe, "?e result;?ee diagnostic", requireStderr = true)
            assertEquals("result\n", output.stdout)
            assertEquals("diagnostic\n", output.stderr)
        }
    }

    @Test fun strictCaptureRejectsMergedStderrBeforeExecuting() {
        val commands = mutableListOf<String>()
        val pipe = StubPipe { command ->
            commands += command
            if (command.startsWith(".?e ?ee ")) command.substringAfterLast(' ') + "\n" else "merged output\n"
        }

        val error = assertThrows(IOException::class.java) {
            console.execute(pipe, "test command", requireStderr = true)
        }
        assertTrue(error.message.orEmpty().contains("without -1"))
        assertEquals(1, commands.size)
        assertEquals("merged output\n", console.execute(pipe, "test command").displayText())
    }

    @Test fun strictCaptureDoesNotExecuteWithoutAStartFence() {
        val commands = mutableListOf<String>()
        val pipe = StubPipe { command ->
            commands += command
            ""
        }

        val error = assertThrows(IOException::class.java) {
            console.execute(pipe, "test command", requireStderr = true)
        }
        assertTrue(error.message.orEmpty().contains("missing stderr start marker"))
        assertEquals(1, commands.size)
    }

    @Test fun strictCaptureDoesNotReportSuccessWithoutAnEndFence() {
        val pipe = StubPipe { command ->
            if (command.startsWith(".?e ?ee ")) {
                console.log(R2PipeLogLevel.WARNING, command.substringAfterLast(' '))
                ""
            } else {
                running = false
                "misleading success\n"
            }
        }
        val error = assertThrows(IOException::class.java) {
            console.execute(pipe, "test command", requireStderr = true)
        }
        assertTrue(error.message.orEmpty().contains("missing stderr end marker"))
    }

    private fun checkCommandOutput(pipe: R2PipeSession) {
        // Machine queries stay parseable; their warnings must not appear in the next command.
        assertEquals("[1]\n", pipe.cmdj("?e [1];?ee unrelated"))
        assertEquals("[stderr]\n诊断\n", console.execute(pipe, "?ee 诊断").displayText())
        assertEquals("normal\n[stderr]\none\ntwo\n", console.execute(pipe, "?ee one;?e normal;?ee two").displayText())
        assertEquals("normal\n", console.execute(pipe, "?e normal").displayText())
        assertEquals("", console.execute(pipe, "s 0").displayText())
    }

    private class StubPipe(private val onCommand: StubPipe.(String) -> String) : R2PipeSession {
        var running = true
        override fun cmd(command: String): String = onCommand(command)
        override fun cmdStream(command: String): InputStream = error("Not a stream test")
        override fun isRunning(): Boolean = running
        override fun interrupt() = Unit
        override fun forceClose() = close()
        override fun close() { running = false }
    }
}

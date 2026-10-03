package org.radare.r2pipe

import java.io.EOFException
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class R2PipeTest {
    @Test
    fun activeStreamRejectsCommandsWithoutInvalidatingItsResponse() = withPeer { pipe, _ ->
        pipe.cmdStream("response").use { stream ->
            assertEquals('r'.code, stream.read())
            assertFailsWith<IllegalStateException> { pipe.cmd("response") }
            assertFailsWith<IllegalStateException> { pipe.cmdStream("response") }
            assertEquals("esponse中文", stream.readBytes().decodeToString())
        }
        assertEquals("response中文", pipe.cmd("response"))
    }

    @Test
    fun closingAnOldCompletedStreamDoesNotAbortTheNextResponse() = withPeer { pipe, _ ->
        val completed = pipe.cmdStream("response")
        assertEquals("response中文", completed.readBytes().decodeToString())
        pipe.cmdStream("response").use { next ->
            completed.close()
            assertEquals("response中文", next.readBytes().decodeToString())
        }
        assertTrue(pipe.isRunning())
    }

    @Test
    fun closingAnIncompleteStreamInvalidatesTheSessionAndStopsItsProcess() = withPeer { pipe, process ->
        val stream = pipe.cmdStream("slow")
        assertEquals('O'.code, stream.read())

        stream.close()
        stream.close()
        pipe.close()
        pipe.forceClose()

        assertFalse(pipe.isRunning())
        assertFailsWith<IllegalStateException> { pipe.cmd("fresh") }
        process.onExit().get(5, TimeUnit.SECONDS)
        assertFalse(process.isAlive)
    }

    @Test
    fun truncatedCommandFailsAndCloseStillReleasesItsProcess() = withPeer { pipe, process ->
        val error = assertFailsWith<RuntimeException> { pipe.cmd("truncated") }
        assertIs<EOFException>(error.cause)

        pipe.close()
        pipe.close()

        assertFalse(pipe.isRunning())
        process.onExit().get(5, TimeUnit.SECONDS)
        assertFalse(process.isAlive)
    }

    @Test
    fun truncatedStreamFailsAndReleasesItsProcess() = withPeer { pipe, process ->
        pipe.cmdStream("truncated").use { stream ->
            assertFailsWith<EOFException> { stream.readBytes() }
        }

        assertFalse(pipe.isRunning())
        process.onExit().get(5, TimeUnit.SECONDS)
        assertFalse(process.isAlive)
    }

    @Test
    fun forceCloseCanEscalateAfterGracefulClose() {
        val deferredTermination = object : ProcessKiller {
            override fun terminate(process: Process, force: Boolean) {
                if (force) process.destroyForcibly()
            }
        }
        withPeer(processKiller = deferredTermination) { pipe, process ->
            pipe.cmd("deferred")
            pipe.close()
            assertTrue(process.isAlive)

            pipe.forceClose()

            process.onExit().get(5, TimeUnit.SECONDS)
            assertFalse(process.isAlive)
        }
    }

    @Test
    fun jsonCommandWithArgumentsUsesTheRequestedAddress() = withPeer { pipe, _ ->
        assertEquals("[{\"addr\":4096}]", pipe.cmdj("pdj 1 @ 0x1000"))
    }

    @Test
    fun invalidRequestDoesNotInvalidateAHealthySession() = withPeer { pipe, _ ->
        assertFailsWith<IllegalArgumentException> { pipe.cmd("response\u0000response") }
        assertEquals("response中文", pipe.cmd("response"))
    }

    @Test
    fun forceCloseUnblocksAnExecutingCommand() {
        val blocked = CountDownLatch(1)
        val logger = R2PipeLogger { _, message ->
            if (message == "peer-blocked") blocked.countDown()
        }
        withPeer(logger) { pipe, process ->
            val workers = Executors.newFixedThreadPool(2)
            try {
                val command = workers.submit<String> { pipe.cmd("blocked") }
                assertTrue(blocked.await(5, TimeUnit.SECONDS))
                workers.submit { pipe.forceClose() }.get(5, TimeUnit.SECONDS)
                val error = assertFailsWith<java.util.concurrent.ExecutionException> {
                    command.get(5, TimeUnit.SECONDS)
                }
                assertIs<RuntimeException>(error.cause)
                process.onExit().get(5, TimeUnit.SECONDS)
                assertFalse(process.isAlive)
            } finally {
                process.destroyForcibly()
                workers.shutdownNow()
            }
        }
    }

    private fun withPeer(
        logger: R2PipeLogger? = null,
        processKiller: ProcessKiller = ProcessKiller.DEFAULT,
        block: (R2Pipe, ProcessHandle) -> Unit
    ) {
        val javaName = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        val java = File(System.getProperty("java.home"), "bin/$javaName")
        val classpath = listOf(StdioProtocolPeer::class.java, Unit::class.java).joinToString(File.pathSeparator) {
            File(it.protectionDomain.codeSource.location.toURI()).absolutePath
        }
        val pipe = R2Pipe.open(
            LaunchSpec(listOf(java.absolutePath, "-cp", classpath, StdioProtocolPeer::class.java.name)),
            logger = logger,
            processKiller = processKiller
        )
        var process: ProcessHandle? = null
        try {
            val peer = ProcessHandle.of(pipe.cmd("pid").toLong()).orElseThrow()
            process = peer
            block(pipe, peer)
        } finally {
            process?.destroyForcibly()
            pipe.forceClose()
        }
    }
}

internal object StdioProtocolPeer {
    @JvmStatic
    fun main(args: Array<String>) {
        val input = System.`in`.bufferedReader(StandardCharsets.UTF_8)
        System.out.write(0)
        System.out.flush()
        while (true) {
            when (val command = input.readLine() ?: return) {
                "pid" -> respond(ProcessHandle.current().pid().toString())
                "response" -> respond("response中文")
                "pdj 1 @ 0x1000" -> respond("[{\"addr\":4096}]")
                "slow" -> {
                    System.out.write("OLD-".toByteArray())
                    System.out.flush()
                    if (input.readLine() == null) return
                    respond("TAIL")
                    respond("FRESH")
                }
                "truncated" -> {
                    System.out.write("TRUNCATED".toByteArray())
                    System.out.flush()
                    System.out.close()
                    input.readLine()
                    return
                }
                "blocked" -> {
                    System.err.println("peer-blocked")
                    input.readLine()
                    return
                }
                "deferred" -> {
                    respond("waiting")
                    Thread.sleep(30_000)
                    return
                }
                else -> respond(command)
            }
        }
    }

    private fun respond(text: String) {
        System.out.write(text.toByteArray(StandardCharsets.UTF_8))
        System.out.write(0)
        System.out.flush()
    }
}

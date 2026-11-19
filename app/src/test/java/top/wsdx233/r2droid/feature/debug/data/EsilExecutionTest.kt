package top.wsdx233.r2droid.feature.debug.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wsdx233.r2droid.util.R2CommandConsole.Output

class EsilExecutionTest {
    private val config = DebugSessionConfig(DebugBackend.ESIL, maxEsilSteps = 65, esilTimeoutMs = 0)
    private val execution = EsilExecution(R2DebugCommandRunner())

    @Test
    fun stepReportsFaultingPcAndSeparateWideMemoryAddress() = runTest {
        val stderr = "WARN: unrelated ESIL warning\n" +
            "ERROR: ESIL stopped at 0x00001004: write error (write-err at 0xffffffffff000000)\n"
        val reason = execution.step(over = false) {
            assertEquals("aes", it)
            Output("0x1008\n", stderr)
        }

        assertEquals(DebugStopReasonType.ESIL_ERROR, reason.type)
        assertEquals(0x1004L, reason.address)
        assertEquals(0xffffffffff000000uL.toLong(), reason.accessAddress)
        assertEquals(stderr, reason.message)
    }

    @Test
    fun stepOverReadsTrapDetailsAndDoesNotMistakeReturnAddressForFault() = runTest {
        val stderr = "WARN: ESIL step-over stopped at 0x0000100c before return to 0x00001004: I/O trap\n" +
            "WARN: ESIL trap 'read-err' (1) at 0x0000100c\n" +
            "WARN: ESIL fault address 0x100000000\n"
        val reason = execution.step(over = true) {
            assertEquals("aeso", it)
            Output("", stderr)
        }

        assertEquals(DebugStopReasonType.ESIL_ERROR, reason.type)
        assertEquals(0x100cL, reason.address)
        assertEquals(0x100000000L, reason.accessAddress)
        assertEquals(stderr, reason.message)
    }

    @Test
    fun mergedStdoutTrapStopsContinueOnFirstBatch() = runTest {
        val merged = "INFO: executing\nERROR: ESIL stopped at 0x00002000: unknown (unknown)\n"
        var calls = 0
        val reason = execution.resume(config, command = {
            assertTrue(it.startsWith("aes "))
            calls++
            Output(merged, "")
        })

        assertEquals(1, calls)
        assertEquals(DebugStopReasonType.ESIL_ERROR, reason.type)
        assertEquals(0x2000L, reason.address)
        assertNull(reason.accessAddress)
        assertEquals(merged, reason.message)
    }

    @Test
    fun haltAndNativeStepOverBreakpointHaveDistinctReasons() = runTest {
        val halt = execution.resume(config, command = {
            Output("", "ERROR: ESIL stopped at 0x00001004: halt (halt)\n")
        })
        val hit = execution.step(over = true) {
            Output("", "WARN: ESIL step-over stopped at 0x00001008 before return to 0x00001002: breakpoint hit\n")
        }

        assertEquals(DebugStopReasonType.STOPPED, halt.type)
        assertEquals(0x1004L, halt.address)
        assertEquals(DebugStopReasonType.BREAKPOINT, hit.type)
        assertEquals(0x1008L, hit.address)
    }

    @Test
    fun r2CommandErrorKeepsEntireStderrWithoutTreatingEveryWarningAsError() = runTest {
        val warning = "WARN: trap trace is enabled; this depends on unsupported flags\n"
        val healthyStep = execution.step(false) { Output("", warning) }
        assertEquals(DebugStopReasonType.STEP, healthyStep.type)

        val stderr = warning + "  ERROR: Cannot step without an arch plugin session  \n" +
            "b".repeat(260) + "\nINFO: esil breakpoint hit at 0x1000\n"
        val reason = execution.step(false) { Output("stdout unrelated\n", stderr) }
        assertEquals(DebugStopReasonType.ESIL_ERROR, reason.type)
        assertEquals(stderr, reason.message)
        assertNull(reason.address)
    }

    @Test
    fun healthyBatchesConsumeBudgetWithoutPcOrBreakpointRefresh() = runTest {
        val counts = mutableListOf<Int>()
        val reason = execution.resume(config, command = {
            assertTrue(it.startsWith("aes "))
            counts += it.substringAfter(' ').toInt()
            Output("0x1000\n", "WARN: warning with the word breakpoint\n")
        })

        assertEquals(DebugStopReasonType.STEP_LIMIT, reason.type)
        assertEquals(65, counts.sum())
        assertTrue(counts.all { it in 1..32 })
        assertTrue(counts.any { it > 1 })
    }

    @Test
    fun timeoutUsesMonotonicMillisecondsAndReportsNativeEventsBeforeLimits() = runTest {
        var monotonic = 135L
        var calls = 0
        val helper = EsilExecution(R2DebugCommandRunner()) { monotonic }
        val limited = config.copy(esilTimeoutMs = 20)
        val timedOut = helper.resume(limited, command = {
            calls++
            monotonic += if (calls == 1) 19 else 1
            Output("", "")
        })
        assertEquals(2, calls)
        assertEquals(DebugStopReasonType.TIMEOUT, timedOut.type)

        val fault = helper.resume(limited, command = {
            monotonic += 30
            Output("", "ERROR: ESIL stopped at 0x00001004: invalid instruction (invalid)\n")
        })
        assertEquals(DebugStopReasonType.ESIL_ERROR, fault.type)
        assertEquals(0x1004L, fault.address)
    }

    @Test
    fun pauseIsObservedBetweenBatchesAndNeverLeaksIntoNextRun() = runTest {
        execution.requestPause() // Idle requests must not poison a later operation.
        var calls = 0
        val stopped = execution.resume(config, command = {
            calls++
            execution.requestPause()
            Output("", "")
        })
        assertEquals(1, calls)
        assertEquals(DebugStopReasonType.MANUAL_PAUSE, stopped.type)

        val next = execution.resume(config.copy(maxEsilSteps = 1), command = {
            assertEquals("aes 1", it)
            Output("", "")
        })
        assertEquals(DebugStopReasonType.STEP_LIMIT, next.type)
    }

    @Test
    fun cursorAtCurrentPcDoesNotInstallBreakpointOrExecute() = runTest {
        val reason = execution.resume(config, cursor = 0x1000L, command = {
            assertEquals("aer PC", it)
            Output("0x00001000\n", "")
        })
        assertEquals(DebugStopReasonType.TARGET_REACHED, reason.type)
        assertEquals(0x1000L, reason.address)
    }

    @Test
    fun existingEnabledAndDisabledCursorBreakpointsKeepTheirState() = runTest {
        for (enabled in listOf(true, false)) {
            val native = CursorCommands(enabled)
            val reason = execution.resume(config, native::execute, cursor = 0x2000L)
            assertEquals(DebugStopReasonType.TARGET_REACHED, reason.type)
            assertEquals(enabled, native.cursorEnabled)
            assertFalse(native.calls.any { it == "db 0x2000" || it == "db- 0x2000" })
            assertEquals(!enabled, "dbe 0x2000" in native.calls)
            assertEquals(!enabled, "dbd 0x2000" in native.calls)
        }
    }

    @Test
    fun userBreakpointBeforeCursorStopsAndRemovesOnlyOwnedTarget() = runTest {
        val native = CursorCommands(null) { Output("", "INFO: esil breakpoint hit at 0x1800\n") }
        val reason = execution.resume(config, native::execute, cursor = 0x2000L)
        assertEquals(DebugStopReasonType.BREAKPOINT, reason.type)
        assertEquals(0x1800L, reason.address)
        assertEquals(listOf("db 0x2000", "db- 0x2000"), native.calls.filter { it.startsWith("db ") || it.startsWith("db- ") })
        assertNull(native.cursorEnabled)
    }

    @Test
    fun cursorCleanupAlsoRunsAfterAnEsilFault() = runTest {
        val stderr = "ERROR: ESIL stopped at 0x1004: read error (read-err at 0x0)\n"
        val native = CursorCommands(false) { Output("", stderr) }
        val reason = execution.resume(config, native::execute, cursor = 0x2000L)
        assertEquals(DebugStopReasonType.ESIL_ERROR, reason.type)
        assertEquals(0x1004L, reason.address)
        assertEquals(0L, reason.accessAddress)
        assertEquals(false, native.cursorEnabled)
        assertEquals("dbd 0x2000", native.calls.last())
    }

    @Test
    fun failedCursorInstallDoesNotDeleteAnything() = runTest {
        val native = CursorCommands(null)
        val stderr = "ERROR: Cannot set breakpoint at 0x2000\n"
        val reason = execution.resume(config, cursor = 0x2000L, command = {
            if (it == "db 0x2000") Output("", stderr) else native.execute(it)
        })
        assertEquals(DebugStopReasonType.ESIL_ERROR, reason.type)
        assertEquals(stderr, reason.message)
        assertEquals(listOf("aer PC", "dbj", "dbj"), native.calls)
    }

    @Test
    fun cancellationPropagatesAndCleansUpEvenWhenBreakpointInstallWasNotAcknowledged() = runTest {
        val installing = CompletableDeferred<Unit>()
        val native = CursorCommands(null)
        val job = launch {
            execution.resume(config, cursor = 0x2000L, command = { command ->
                currentCoroutineContext().ensureActive()
                val result = native.execute(command)
                if (command == "db 0x2000") {
                    installing.complete(Unit)
                    awaitCancellation()
                }
                result
            })
        }
        installing.await()
        execution.requestPause()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertNull(native.cursorEnabled)
        assertEquals("db- 0x2000", native.calls.last())
        assertTrue(native.calls.none { it.startsWith("aes ") })
        assertEquals(DebugStopReasonType.STEP_LIMIT, execution.resume(config.copy(maxEsilSteps = 1), command = { Output("", "") }).type)
    }

    @Test
    fun cleanupFailureKeepsOriginalCancellationAndExposesRestoreCommand() = runTest {
        val cancelled = CancellationException("cancel ESIL")
        val native = CursorCommands(false) { throw cancelled }
        try {
            execution.resume(config, cursor = 0x2000L, command = {
                if (it == "dbd 0x2000") Output("", "ERROR: unable to turn off breakpoint\n")
                else native.execute(it)
            })
            error("Expected cancellation")
        } catch (error: CancellationException) {
            assertSame(cancelled, error)
            val cleanup = error.suppressed.single() as EsilExecution.BreakpointCleanupException
            assertEquals("dbd 0x2000", cleanup.restoreCommand)
        }
    }

    private class CursorCommands(
        var cursorEnabled: Boolean?,
        private val batch: suspend () -> Output = { Output("", "INFO: esil breakpoint hit at 0x2000\n") }
    ) {
        val calls = mutableListOf<String>()

        suspend fun execute(command: String): Output {
            calls += command
            return when (command) {
                "aer PC" -> Output("0x1000\n", "")
                "dbj" -> {
                    val cursor = cursorEnabled?.let { ",{\"addr\":8192,\"enabled\":$it,\"name\":\"cursor\"}" }.orEmpty()
                    Output("[{\"addr\":6144,\"enabled\":true,\"name\":\"user\"}$cursor]\n", "")
                }
                "db 0x2000", "dbe 0x2000" -> Output("", "").also { cursorEnabled = true }
                "db- 0x2000" -> Output("", "").also { cursorEnabled = null }
                "dbd 0x2000" -> Output("", "").also { cursorEnabled = false }
                else -> if (command.startsWith("aes ")) batch() else error("Unexpected command: $command")
            }
        }
    }
}

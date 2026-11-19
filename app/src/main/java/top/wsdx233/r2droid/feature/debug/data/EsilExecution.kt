package top.wsdx233.r2droid.feature.debug.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.util.R2CommandConsole.Output
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal class EsilExecution(
    private val runner: R2DebugCommandRunner,
    private val monotonicMs: () -> Long
) {
    constructor(runner: R2DebugCommandRunner) : this(runner, { System.nanoTime() / 1_000_000L })

    private class Run {
        @Volatile var pauseRequested = false
    }

    private val activeRun = AtomicReference<Run?>(null)

    fun requestPause() {
        activeRun.get()?.pauseRequested = true
    }

    suspend fun step(over: Boolean, command: suspend (String) -> Output): DebugStopReason {
        val output = execute(if (over) "aeso" else "aes", command)
        return parseStop(output) ?: DebugStopReason(
            DebugStopReasonType.STEP, if (over) "Step over" else "Step into"
        )
    }

    suspend fun resume(
        config: DebugSessionConfig,
        command: suspend (String) -> Output,
        cursor: Long? = null
    ): DebugStopReason {
        val run = Run()
        check(activeRun.compareAndSet(null, run)) { "ESIL is already running" }
        try {
            val startedAt = monotonicMs()
            val limit = config.maxEsilSteps.coerceAtLeast(0)
            var remaining = limit

            fun boundaryStop(): DebugStopReason? = when {
                run.pauseRequested -> DebugStopReason(DebugStopReasonType.MANUAL_PAUSE, "ESIL paused by user")
                config.esilTimeoutMs > 0 && monotonicMs() - startedAt >= config.esilTimeoutMs ->
                    DebugStopReason(DebugStopReasonType.TIMEOUT, "ESIL time limit reached (${config.esilTimeoutMs} ms)")
                remaining == 0 -> DebugStopReason(DebugStopReasonType.STEP_LIMIT, "ESIL step limit reached ($limit steps)")
                else -> null
            }

            suspend fun batches(): DebugStopReason {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    boundaryStop()?.let { return it }
                    val count = minOf(remaining, BATCH_SIZE)
                    val output = execute("aes $count", command)
                    // r2 6.2.6 stops this loop on the first fault or breakpoint. Only a
                    // batch without a stop event consumes its full requested step count.
                    parseStop(output, cursor)?.let { return it }
                    remaining -= count
                }
            }

            if (cursor == null) return batches()
            val pcText = checkedCommand("aer PC", command)
            val pc = pcText.lineSequence().map(::clean).firstNotNullOfOrNull {
                if (ADDRESS_LINE.matches(it)) parseAddress(it) else null
            } ?: error("Unable to read ESIL program counter")
            if (pc == cursor) return cursorReached(cursor, alreadyThere = true)
            boundaryStop()?.let { return it }
            val reason = withCursorBreakpoint(cursor, command, ::batches)
            currentCoroutineContext().ensureActive()
            return reason
        } catch (error: R2CommandError) {
            if (error.suppressed.isNotEmpty()) throw error
            currentCoroutineContext().ensureActive()
            return DebugStopReason(DebugStopReasonType.ESIL_ERROR, error.message)
        } finally {
            activeRun.set(null)
        }
    }

    private suspend fun withCursorBreakpoint(
        address: Long,
        command: suspend (String) -> Output,
        run: suspend () -> DebugStopReason
    ): DebugStopReason {
        val original = breakpointAt(address, command)
        if (original?.enabled == true) return run()
        val operand = address.hex()
        val apply = if (original == null) "db $operand" else "dbe $operand"
        val restore = if (original == null) "db- $operand" else "dbd $operand"
        var installed = false
        var failure: Throwable? = null
        currentCoroutineContext().ensureActive()
        try {
            checkedCommand(apply, command)
            installed = true
            return run()
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                withContext(NonCancellable) {
                    // A command may have changed r2 state before it threw or was cancelled.
                    val changed = installed || breakpointAt(address, command)?.let {
                        original == null || it.enabled
                    } == true
                    if (changed) {
                        checkedCommand(restore, command)
                    }
                }
            } catch (error: CancellationException) {
                if (failure == null) {
                    error.addSuppressed(BreakpointCleanupException(restore, null))
                    throw error
                }
                failure.addSuppressed(BreakpointCleanupException(restore, error))
            } catch (error: Exception) {
                val cleanup = BreakpointCleanupException(restore, error)
                if (failure == null) {
                    try {
                        currentCoroutineContext().ensureActive()
                    } catch (cancelled: CancellationException) {
                        cancelled.addSuppressed(cleanup)
                        throw cancelled
                    }
                    throw cleanup
                }
                failure.addSuppressed(cleanup)
            }
        }
    }

    private suspend fun breakpointAt(address: Long, command: suspend (String) -> Output): DebugBreakpoint? =
        runner.parseBreakpoints(checkedCommand("dbj", command)).firstOrNull { it.address == address }

    private suspend fun checkedCommand(name: String, command: suspend (String) -> Output): String {
        val output = execute(name, command)
        if (output.stderr.lineSequence().any { isCommandError(clean(it)) }) throw R2CommandError(output.stderr)
        if (output.stdout.lineSequence().any { isCommandError(clean(it)) }) throw R2CommandError(output.stdout)
        return output.stdout
    }

    private suspend fun execute(name: String, command: suspend (String) -> Output): Output {
        currentCoroutineContext().ensureActive()
        val output = command(name)
        currentCoroutineContext().ensureActive()
        return output
    }

    class BreakpointCleanupException(val restoreCommand: String, cause: Throwable?) :
        IllegalStateException("Could not restore the ESIL cursor breakpoint" + (cause?.message?.let { ": $it" } ?: ""), cause)

    private class R2CommandError(message: String) : IllegalStateException(message)

    private companion object {
        const val BATCH_SIZE = 32
        const val HEX = "0x[0-9a-f]+"
        val ADDRESS_LINE = regex("(?:$HEX|-?\\d+)")
        val ANSI = Regex("\u001b\\[[0-?]*[ -/]*[@-~]")
        val SEVERITY = regex("^(?:fatal|error|info|warn(?:ing)?|debug|hint|todo):\\s*(?:\\[(?!ESIL\\]|\\+\\])[^]]+]\\s*)*")
        val ERROR_PREFIX = regex("^(?:error|fatal):")
        val COMMAND_ERROR = regex("^(?:unknown command|invalid command|command failed|invalid ESIL step count)\\b")
        val BREAKPOINT = regex("^(?:ESIL )?(?:breakpoint hit at|hit breakpoint at) ($HEX)\\b")
        val STOPPED = regex("^ESIL (?:step-over )?stopped at ($HEX)(?: before return to $HEX)?(?::\\s*(.*))?$")
        val TRAP = regex("^(?:ESIL )?(?:trap(?: ('[^']+'|[^ (]+))?(?: \\(\\d+\\))?|trap instruction) at ($HEX)\\b")
        val BAD_INSTRUCTION = regex("^(?:(?:(?:invalid|unaligned|unsupported) instruction|failed analysis|couldn't decode instruction) at|couldn't read data to decode from) ($HEX)\\b")
        val INTERRUPTED = regex("^(?:\\[\\+\\] )?ESIL emulation interrupted at ($HEX)\\b")
        val NATIVE_LIMIT = regex("^(?:\\[ESIL\\] )?(timeout exceeded|maximum steps exceeded) at ($HEX)\\b")
        val ACCESS = regex("(?:\\b(?:read|write|execute)-err at |^ESIL fault address )($HEX)\\b")
        val LEGACY_IO_TRAP = regex("^ESIL trap '(?:read|write)-err' \\((\\d+)\\) at $HEX\\b")

        fun regex(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)
        fun Long.hex() = "0x${toULong().toString(16)}"
        fun parseAddress(raw: String) = if (raw.startsWith("0x", ignoreCase = true)) {
            raw.substring(2).toULongOrNull(16)?.toLong()
        } else raw.toLongOrNull() ?: raw.toULongOrNull()?.toLong()

        fun cursorReached(address: Long, alreadyThere: Boolean = false) = DebugStopReason(
            DebugStopReasonType.TARGET_REACHED,
            if (alreadyThere) "Already at ESIL run-to-cursor target ${address.hex()}"
            else "Reached ESIL run-to-cursor target ${address.hex()}",
            address = address
        )

        fun breakpointHit(address: Long, cursor: Long?) = if (cursor == address) cursorReached(address)
        else DebugStopReason(DebugStopReasonType.BREAKPOINT, "ESIL breakpoint hit at ${address.hex()}", address = address)

        fun clean(line: String) = if ('\u001b' in line) ANSI.replace(line, "").trim() else line.trim()
        fun body(cleanLine: String) = SEVERITY.replaceFirst(cleanLine, "")
        fun isCommandError(cleanLine: String, lineBody: String = body(cleanLine)): Boolean =
            ERROR_PREFIX.containsMatchIn(cleanLine) || COMMAND_ERROR.containsMatchIn(lineBody)

        fun parseStop(output: Output, cursor: Long? = null): DebugStopReason? =
            parseStop(output.stderr, cursor) ?: parseStop(output.stdout, cursor)

        fun parseStop(raw: String, cursor: Long?): DebugStopReason? {
            if (raw.isEmpty()) return null
            val lines = raw.lineSequence().iterator()
            while (lines.hasNext()) {
                val original = clean(lines.next())
                if (original.isEmpty()) continue
                val line = body(original)
                BREAKPOINT.find(line)?.groupValues?.get(1)?.let(::parseAddress)?.let {
                    return breakpointHit(it, cursor)
                }
                STOPPED.matchEntire(line)?.let { match ->
                    val address = parseAddress(match.groupValues[1])
                    val reason = match.groupValues[2].lowercase(Locale.ROOT)
                    val kind = when {
                        reason == "breakpoint hit" && address != null -> return breakpointHit(address, cursor)
                        reason == "user interrupt" -> DebugStopReasonType.MANUAL_PAUSE
                        reason == "timeout exceeded" -> DebugStopReasonType.TIMEOUT
                        reason == "maximum steps exceeded" -> DebugStopReasonType.STEP_LIMIT
                        reason.isEmpty() || reason == "halt" || reason.startsWith("halt (") -> DebugStopReasonType.STOPPED
                        else -> DebugStopReasonType.ESIL_ERROR
                    }
                    var access = ACCESS.find(line)?.groupValues?.get(1)?.let(::parseAddress)
                    if (access == null && (reason == "i/o trap" || reason == "esil trap") && lines.hasNext()) {
                        // aeso emits the trap and exact address on the next two lines.
                        val trap = body(clean(lines.next()))
                        val code = LEGACY_IO_TRAP.find(trap)?.groupValues?.get(1)?.let(::parseAddress)
                        access = if (lines.hasNext()) {
                            ACCESS.find(body(clean(lines.next())))?.groupValues?.get(1)?.let(::parseAddress) ?: code
                        } else code
                    }
                    return DebugStopReason(kind, raw, address = address, accessAddress = access)
                }
                INTERRUPTED.find(line)?.let {
                    return DebugStopReason(DebugStopReasonType.MANUAL_PAUSE, raw, address = parseAddress(it.groupValues[1]))
                }
                NATIVE_LIMIT.find(line)?.let {
                    val kind = if (it.groupValues[1].startsWith("timeout", ignoreCase = true)) {
                        DebugStopReasonType.TIMEOUT
                    } else DebugStopReasonType.STEP_LIMIT
                    return DebugStopReason(kind, raw, address = parseAddress(it.groupValues[2]))
                }
                TRAP.find(line)?.let {
                    val kind = if (it.groupValues[1].trim('\'').equals("halt", ignoreCase = true)) {
                        DebugStopReasonType.STOPPED
                    } else DebugStopReasonType.ESIL_ERROR
                    val access = LEGACY_IO_TRAP.find(line)?.groupValues?.get(1)?.let(::parseAddress)
                    return DebugStopReason(kind, raw, address = parseAddress(it.groupValues[2]), accessAddress = access)
                }
                BAD_INSTRUCTION.find(line)?.let {
                    return DebugStopReason(DebugStopReasonType.ESIL_ERROR, raw, address = parseAddress(it.groupValues[1]))
                }
                if (isCommandError(original, line)) return DebugStopReason(DebugStopReasonType.ESIL_ERROR, raw)
            }
            return null
        }
    }
}

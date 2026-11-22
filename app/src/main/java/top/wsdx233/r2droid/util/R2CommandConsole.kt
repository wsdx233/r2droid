package top.wsdx233.r2droid.util

import org.radare.r2pipe.kotlin.R2PipeLogLevel
import org.radare.r2pipe.kotlin.R2PipeLogger
import org.radare.r2pipe.kotlin.R2PipeSession
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One per local r2 process; capture windows are serialized by the session mutex. */
internal class R2CommandConsole(private val logger: R2PipeLogger) : R2PipeLogger {
    @Volatile private var window: Window? = null

    override fun log(level: R2PipeLogLevel, message: String) {
        // Both local transports drain fd 2 at WARNING; HTTP server stdout is INFO.
        val active = window
        if (level == R2PipeLogLevel.WARNING && active != null) {
            active.accept(message)?.let { logger.log(level, it) }
        } else {
            logger.log(level, message)
        }
    }

    fun execute(pipe: R2PipeSession, command: String, requireStderr: Boolean = false): Output {
        val capture = Window(requireStderr)
        window = capture
        try {
            // stdout's NUL (or HTTP response) says nothing about how far the stderr reader has got.
            // Fence fd 2 itself, in separate requests so quotes, comments and compound commands keep
            // their meaning. The dot form executes the echo without replacing r2's last command.
            val beginResponse = pipe.cmd(".?e ?ee ${capture.begin}")
            // r2 -1 merges stderr into stdout. Preserve manual display, but never mistake a
            // combined response for a clean, separately scoped diagnostic stream.
            if (capture.begin in beginResponse) {
                if (requireStderr) throw IOException("ESIL requires separate stderr; reopen radare2 without -1.")
                return Output(pipe.cmd(command), "")
            }
            if (capture.started?.await(FENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS) == false) {
                throw IOException("Could not open the ESIL diagnostic window: missing stderr start marker.")
            }
            val result = try {
                Result.success(pipe.cmd(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
            var closed = false
            if (pipe.isRunning()) {
                try {
                    pipe.cmd(".?e ?ee ${capture.end}")
                    closed = capture.finished.await(FENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (e: RuntimeException) {
                    // For manual commands keep the command's own response/error.
                    if (requireStderr) throw IOException("Could not close the ESIL diagnostic window.", e)
                    result.exceptionOrNull()?.let { if (it !== e) it.addSuppressed(e) }
                }
            }
            if (requireStderr && !closed) {
                throw IOException("Could not close the ESIL diagnostic window: missing stderr end marker.", result.exceptionOrNull())
            }
            if (requireStderr && capture.isTruncated()) {
                throw IOException("The ESIL diagnostic window exceeded its capture limit.", result.exceptionOrNull())
            }
            val stderr = capture.text()
            return Output(result.getOrElse {
                if (stderr.isEmpty()) throw it
                throw IOException(Output(it.message.orEmpty(), stderr).displayText(), it)
            }, stderr)
        } finally {
            window = null
        }
    }

    data class Output(val stdout: String, val stderr: String) {
        // A failed r2 command often returns normally and reports its error on fd 2
        // (or in stdout under -1). Do not mark such a transaction successful/dirty.
        fun checkedStdout(): String {
            val diagnostic = COMMAND_ERROR.find(stderr) ?: COMMAND_ERROR.find(stdout)
            if (diagnostic != null) error(diagnostic.value.trim().take(500))
            return stdout
        }

        fun displayText(): String = if (stderr.isEmpty()) stdout else buildString {
            append(stdout)
            if (stdout.isNotEmpty() && !stdout.endsWith('\n')) append('\n')
            append("[stderr]\n").append(stderr)
        }
    }

    private class Window(requireStart: Boolean) {
        private val id = UUID.randomUUID()
        val begin = "r2droid:$id:begin"
        val end = "r2droid:$id:end"
        val started = if (requireStart) CountDownLatch(1) else null
        val finished = CountDownLatch(1)
        private var recording = false
        private val buffer = StringBuilder()
        private var truncated = false

        /** Return only the original (non-fence) portion for normal logging. */
        @Synchronized fun accept(line: String): String? {
            val marker = when {
                line.endsWith(begin) -> begin
                line.endsWith(end) -> end
                else -> null
            }
            val original = if (marker == null) line else line.dropLast(marker.length)
            val text = if ('\u001b' in original) ANSI_CSI.replace(original, "") else original
            if (recording) {
                // r2 can write terminal controls, or a diagnostic without a final newline, before a fence.
                buffer.append(text, (text.length - MAX_STDERR_CHARS).coerceAtLeast(0), text.length)
                if (marker == null) buffer.append('\n')
                if (text.length > MAX_STDERR_CHARS) truncated = true
                if (buffer.length > MAX_STDERR_CHARS * 2) {
                    buffer.delete(0, buffer.length - MAX_STDERR_CHARS)
                    truncated = true
                }
            }
            if (marker != null) {
                recording = marker == begin
                if (recording) started?.countDown() else finished.countDown()
                return original.takeIf { text.isNotEmpty() }
            }
            return line
        }

        @Synchronized fun isTruncated(): Boolean = truncated || buffer.length > MAX_STDERR_CHARS

        @Synchronized fun text(): String = if (truncated || buffer.length > MAX_STDERR_CHARS) {
            "[earlier stderr omitted]\n" + buffer.substring((buffer.length - MAX_STDERR_CHARS).coerceAtLeast(0))
        } else buffer.toString()
    }

    private companion object {
        const val FENCE_TIMEOUT_SECONDS = 2L
        const val MAX_STDERR_CHARS = 256 * 1024
        val COMMAND_ERROR = Regex("^(?:[ \\t]|\\u001b\\[[0-9;]*m)*(?:(?:error|fatal):|(?:invalid|unknown) command\\b).*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        val ANSI_CSI = Regex("\u001b\\[[0-?]*[ -/]*[@-~]")
    }
}

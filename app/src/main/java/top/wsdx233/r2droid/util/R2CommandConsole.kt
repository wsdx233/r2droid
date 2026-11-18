package top.wsdx233.r2droid.util

import org.radare.r2pipe.kotlin.R2PipeLogLevel
import org.radare.r2pipe.kotlin.R2PipeLogger
import org.radare.r2pipe.kotlin.R2PipeSession
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One per local r2 process. Only manual commands, serialized by the session mutex, open a window. */
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

    fun execute(pipe: R2PipeSession, command: String): Output {
        val capture = Window()
        window = capture
        try {
            // stdout's NUL (or HTTP response) says nothing about how far the stderr reader has got.
            // Fence fd 2 itself, in separate requests so quotes, comments and compound commands keep
            // their meaning. The dot form executes the echo without replacing r2's last command.
            val beginResponse = pipe.cmd(".?e ?ee ${capture.begin}")
            // r2 -1 already sends stderr to stdout.
            if (capture.begin in beginResponse) return Output(pipe.cmd(command), "")
            val result = try {
                Result.success(pipe.cmd(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (pipe.isRunning()) {
                try {
                    pipe.cmd(".?e ?ee ${capture.end}")
                    // A custom runtime can close/redirect fd 2. Don't hang the command UI for it.
                    capture.finished.await(2, TimeUnit.SECONDS)
                } catch (e: RuntimeException) {
                    // The command may have terminated the process. Keep its own response/error.
                    result.exceptionOrNull()?.addSuppressed(e)
                }
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
        fun displayText(): String = if (stderr.isEmpty()) stdout else buildString {
            append(stdout)
            if (stdout.isNotEmpty() && !stdout.endsWith('\n')) append('\n')
            append("[stderr]\n").append(stderr)
        }
    }

    private class Window {
        private val id = UUID.randomUUID()
        val begin = "r2droid:$id:begin"
        val end = "r2droid:$id:end"
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
                if (!recording) finished.countDown()
                return original.takeIf { text.isNotEmpty() }
            }
            return line
        }

        @Synchronized fun text(): String = if (truncated || buffer.length > MAX_STDERR_CHARS) {
            "[earlier stderr omitted]\n" + buffer.substring((buffer.length - MAX_STDERR_CHARS).coerceAtLeast(0))
        } else buffer.toString()
    }

    private companion object {
        const val MAX_STDERR_CHARS = 256 * 1024
        val ANSI_CSI = Regex("\u001b\\[[0-?]*[ -/]*[@-~]")
    }
}

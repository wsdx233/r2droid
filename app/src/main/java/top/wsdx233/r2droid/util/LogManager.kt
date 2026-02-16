package top.wsdx233.r2droid.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogType {
    COMMAND, OUTPUT, INFO, WARNING, ERROR
}

data class LogEntry(
    val id: Long = System.currentTimeMillis() + System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogType,
    val message: String
)

object LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private const val MAX_LOG_MESSAGE_LENGTH = 2000

    fun log(type: LogType, message: String) {
        val truncatedMessage = if (message.length > MAX_LOG_MESSAGE_LENGTH) {
            message.take(MAX_LOG_MESSAGE_LENGTH) + "... (truncated ${message.length - MAX_LOG_MESSAGE_LENGTH} chars)"
        } else {
            message
        }
        val entry = LogEntry(type = type, message = truncatedMessage)
        // Keep only last 1000 logs to prevent memory issues
        val currentLogs = _logs.value
        val newLogs = if (currentLogs.size > 1000) {
            currentLogs.drop(1) + entry
        } else {
            currentLogs + entry
        }
        _logs.value = newLogs
    }

    fun clear() {
        _logs.value = emptyList()
    }
}

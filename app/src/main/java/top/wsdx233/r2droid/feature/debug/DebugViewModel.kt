package top.wsdx233.r2droid.feature.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.util.R2PipeManager

/**
 * Lightweight command console for the currently active project session.
 *
 * Older versions of this screen opened a separate r2pipe session against the r2 binary itself,
 * which made the page unrelated to the project being inspected. Keeping this ViewModel attached
 * to R2PipeManager avoids hidden extra r2 processes and makes it useful as a debug console.
 */
class DebugViewModel : ViewModel() {

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _outputText = MutableStateFlow("")
    val outputText: StateFlow<String> = _outputText.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    fun updateCommandInput(input: String) {
        _commandInput.value = input
    }

    fun executeCommand(context: Context) {
        val command = _commandInput.value.trim()
        if (command.isBlank()) return

        viewModelScope.launch {
            _isExecuting.value = true
            _outputText.value = "Executing '$command' on active session..."
            val result = if (R2PipeManager.isConnected) {
                R2PipeManager.execute(command, includeStderr = true)
            } else {
                Result.failure(IllegalStateException("No active R2 session. Open a project first."))
            }

            _outputText.value = result.fold(
                onSuccess = { output ->
                    if (output.isBlank()) "[$command completed]\n(no text output)" else output
                },
                onFailure = { error -> "Execution error: ${error.message}" }
            )
            _isExecuting.value = false
        }
    }
}

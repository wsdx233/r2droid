package top.wsdx233.r2droid.core.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R

@Composable
fun CustomCommandDialog(
    initialCommand: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var command by remember { mutableStateOf(initialCommand) }
    var output by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    
    // We need a coroutine scope to execute commands and update UI
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Execute r2 Command") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("e.g. iI") },
                        label = { Text("Command") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (command.isNotBlank()) {
                                isExecuting = true
                                scope.launch {
                                    val result = top.wsdx233.r2droid.util.R2PipeManager.execute(command)
                                    output = result.getOrDefault("Error: ${result.exceptionOrNull()?.message}")
                                    isExecuting = false
                                }
                            }
                        },
                        enabled = !isExecuting
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Run")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Output:", 
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                val commandOutputBg = colorResource(R.color.command_output_background)
                val commandOutputText = colorResource(R.color.command_output_text)
                val commandOutputPlaceholder = colorResource(R.color.command_output_placeholder)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(commandOutputBg, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = output.ifEmpty { "No output" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (output.isEmpty()) commandOutputPlaceholder else commandOutputText
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close") // Keep dialog open until explicitly closed
            }
        }
    )
}

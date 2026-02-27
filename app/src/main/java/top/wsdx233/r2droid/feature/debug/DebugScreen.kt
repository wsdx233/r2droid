package top.wsdx233.r2droid.feature.debug

import androidx.compose.ui.res.stringResource
import top.wsdx233.r2droid.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DebugScreen(
    modifier: Modifier,
    viewModel: DebugViewModel = viewModel()
) {
    val context = LocalContext.current
    val commandInput by viewModel.commandInput.collectAsState()
    val outputText by viewModel.outputText.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 命令输入框
        OutlinedTextField(
            value = commandInput,
            onValueChange = { viewModel.updateCommandInput(it) },
            label = { Text(stringResource(R.string.debug_command_label)) },
            placeholder = { Text(stringResource(R.string.debug_command_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExecuting,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 执行按钮
        Button(
            onClick = { viewModel.executeCommand(context) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExecuting && commandInput.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.debug_execute_btn)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isExecuting) stringResource(R.string.debug_executing_btn) else stringResource(R.string.debug_execute_btn))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 结果显示区域标题
        Text(
            text = stringResource(R.string.debug_result_label),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 可滚动、可选择复制的结果显示区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            SelectionContainer {
                val scrollState = rememberScrollState()
                Text(
                    text = outputText.ifEmpty { stringResource(R.string.debug_result_placeholder) },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (outputText.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

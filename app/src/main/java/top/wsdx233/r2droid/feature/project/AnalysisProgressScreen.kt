package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.LogEntry
import top.wsdx233.r2droid.util.LogType

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AnalysisProgressScreen(
    logs: List<LogEntry>,
    isRestoring: Boolean = false,
    onClearLogs: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column {
                            Text(stringResource(if (isRestoring) R.string.analysis_loading else R.string.analysis_analyzing))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(100.dp, 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            LogList(logs, onClearLogs)
        }
    }
}

@Composable
fun LogList(logs: List<LogEntry>, onClearLogs: () -> Unit = {}) {
    val listState = rememberLazyListState()

    // Auto scroll to bottom
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1E1E1E) // Dark background for logs
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { entry ->
                    LogItem(entry)
                }
            }
            
            // Clear button in top-right corner
            FilledTonalIconButton(
                onClick = onClearLogs,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.logs_clear_desc),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun LogItem(entry: LogEntry) {
    val color = when (entry.type) {
        LogType.COMMAND -> MaterialTheme.colorScheme.primary
        LogType.OUTPUT -> Color(0xFFE0E0E0)
        LogType.INFO -> Color.Gray
        LogType.WARNING -> Color(0xFFFFA000) // Amber
        LogType.ERROR -> MaterialTheme.colorScheme.error
    }

    val prefix = when (entry.type) {
        LogType.COMMAND -> "$ "
        LogType.WARNING -> "[WARN] "
        LogType.ERROR -> "[ERR] "
        else -> ""
    }

    SelectionContainer {
        Text(
            text = run {
                val content = "$prefix${entry.message}"
                if (content.length > 2000) {
                     content.take(2000) + "... (truncated ${content.length - 2000} chars)"
                } else {
                     content
                }
            },
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}



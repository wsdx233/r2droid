package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.AutoHideScrollbar
import top.wsdx233.r2droid.util.LogEntry
import top.wsdx233.r2droid.util.LogType

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AnalysisProgressScreen(
    logs: List<LogEntry>,
    isRestoring: Boolean = false,
    onClearLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val tips = remember { loadAnalysisTips(context) }
    val tipSequence = remember(tips) {
        mutableStateListOf<Int>().apply {
            addAll(shuffledTipIndices(tips.size))
        }
    }
    val isChinese = remember(configuration) {
        configuration.locales[0]?.language?.startsWith("zh") == true
    }
    var sequencePosition by rememberSaveable(tips.size) { mutableIntStateOf(0) }

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
            LogList(
                logs = logs,
                onClearLogs = onClearLogs,
                modifier = Modifier.weight(1f)
            )
            AnalysisTipsCarousel(
                tips = tips,
                isChinese = isChinese,
                currentTipIndex = if (tips.isEmpty()) 0 else tipSequence[sequencePosition],
                currentTipNumber = if (tips.isEmpty()) 0 else tipSequence[sequencePosition] + 1,
                canGoPrevious = sequencePosition > 0,
                onPrevious = {
                    if (sequencePosition > 0) {
                        sequencePosition -= 1
                    }
                },
                onNext = {
                    if (tips.isNotEmpty()) {
                        if (sequencePosition < tipSequence.lastIndex) {
                            sequencePosition += 1
                        } else {
                            val nextRound = shuffledTipIndices(
                                count = tips.size,
                                avoidFirst = tipSequence.lastOrNull()
                            )
                            tipSequence.addAll(nextRound)
                            sequencePosition += 1
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun LogList(
    logs: List<LogEntry>,
    onClearLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto scroll to bottom
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = Color(0xFF1E1E1E) // Dark background for logs
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SelectionContainer(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { entry ->
                        LogItem(entry)
                    }
                }
            }
            
            AutoHideScrollbar(
                listState = listState,
                totalItems = logs.size,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
            
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
private fun AnalysisTipsCarousel(
    tips: List<AnalysisTip>,
    isChinese: Boolean,
    currentTipIndex: Int,
    currentTipNumber: Int,
    canGoPrevious: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.analysis_tips_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (tips.isEmpty()) {
                Text(
                    text = stringResource(R.string.analysis_tips_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val tip = tips[currentTipIndex.coerceIn(0, tips.lastIndex)]
                Text(
                    text = if (isChinese) tip.zh else tip.en,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
//                Text(
//                    text = stringResource(
//                        R.string.analysis_tips_index,
//                        currentTipNumber
//                    ),
//                    style = MaterialTheme.typography.labelSmall,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TipNavButton(
                    text = stringResource(R.string.analysis_tips_previous),
                    enabled = canGoPrevious,
                    onClick = onPrevious
                )
                Spacer(modifier = Modifier.width(8.dp))
                TipNavButton(
                    text = stringResource(R.string.analysis_tips_next),
                    enabled = tips.size > 1,
                    onClick = onNext
                )
            }
        }
    }
}

private fun shuffledTipIndices(count: Int, avoidFirst: Int? = null): List<Int> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(0)

    val shuffled = (0 until count).shuffled().toMutableList()
    if (avoidFirst != null && shuffled.first() == avoidFirst) {
        val second = 1
        val first = shuffled[0]
        shuffled[0] = shuffled[second]
        shuffled[second] = first
    }
    return shuffled
}

@Composable
private fun RowScope.TipNavButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text)
    }
}

private data class AnalysisTip(
    val zh: String,
    val en: String
)

private fun loadAnalysisTips(context: android.content.Context): List<AnalysisTip> {
    return runCatching {
        val json = context.assets.open("analysis_tips.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = JSONObject(json)
        val tipsArray = root.optJSONArray("tips") ?: return emptyList()
        buildList {
            for (i in 0 until tipsArray.length()) {
                val item = tipsArray.optJSONObject(i) ?: continue
                val zh = item.optString("zh").trim()
                val en = item.optString("en").trim()
                if (zh.isNotEmpty() && en.isNotEmpty()) {
                    add(AnalysisTip(zh = zh, en = en))
                }
            }
        }
    }.getOrDefault(emptyList())
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



package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.widget.Toast
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
    val tips by produceState(initialValue = cachedAnalysisTips ?: emptyList(), key1 = context.applicationContext) {
        if (value.isEmpty()) {
            value = withContext(Dispatchers.IO) { loadAnalysisTips(context.applicationContext) }
        }
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogList(
    logs: List<LogEntry>,
    onClearLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var rangeSelectionArmed by remember { mutableStateOf(false) }
    var rangeAnchorId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(logs.size, selectionMode) {
        if (!selectionMode && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    LaunchedEffect(logs) {
        val currentIds = logs.map { it.id }.toSet()
        val filteredSelection = selectedIds.intersect(currentIds)
        if (filteredSelection != selectedIds) {
            selectedIds = filteredSelection
        }
        if (selectionMode && filteredSelection.isEmpty()) {
            selectionMode = false
            rangeSelectionArmed = false
            rangeAnchorId = null
        } else if (rangeAnchorId != null && rangeAnchorId !in currentIds) {
            rangeAnchorId = logs.lastOrNull { it.id in filteredSelection }?.id
        }
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
        rangeSelectionArmed = false
        rangeAnchorId = null
    }

    fun toggleItemSelection(entry: LogEntry) {
        val next = if (entry.id in selectedIds) selectedIds - entry.id else selectedIds + entry.id
        selectedIds = next
        if (next.isEmpty()) {
            exitSelectionMode()
        } else {
            selectionMode = true
            rangeAnchorId = entry.id
        }
    }

    fun selectRangeTo(targetIndex: Int) {
        val anchorIndex = logs.indexOfFirst { it.id == rangeAnchorId }
            .takeIf { it >= 0 }
            ?: targetIndex
        val start = minOf(anchorIndex, targetIndex)
        val end = maxOf(anchorIndex, targetIndex)
        selectedIds = selectedIds + logs.subList(start, end + 1).map { it.id }
        selectionMode = true
        rangeSelectionArmed = false
        rangeAnchorId = logs.getOrNull(targetIndex)?.id
    }

    fun copySelection() {
        val selectedText = logs
            .filter { it.id in selectedIds }
            .joinToString("\n") { logEntryDisplayText(it) }
        if (selectedText.isNotEmpty()) {
            clipboardManager.setText(AnnotatedString(selectedText))
            Toast.makeText(context, R.string.ai_copied, Toast.LENGTH_SHORT).show()
        }
    }

    val contentPadding = PaddingValues(
        start = 16.dp,
        top = if (selectionMode) 76.dp else 16.dp,
        end = 16.dp,
        bottom = 16.dp
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF1E1E1E)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(logs, key = { _, entry -> entry.id }) { index, entry ->
                    LogItem(
                        entry = entry,
                        selected = entry.id in selectedIds,
                        selectionMode = selectionMode,
                        onClick = {
                            when {
                                rangeSelectionArmed -> selectRangeTo(index)
                                selectionMode -> toggleItemSelection(entry)
                            }
                        },
                        onLongClick = {
                            selectionMode = true
                            selectedIds = selectedIds + entry.id
                            rangeSelectionArmed = false
                            rangeAnchorId = entry.id
                        }
                    )
                }
            }

            AutoHideScrollbar(
                listState = listState,
                totalItems = logs.size,
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            if (selectionMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (rangeSelectionArmed) {
                                stringResource(R.string.logs_range_pick_end)
                            } else {
                                stringResource(R.string.logs_selected_count, selectedIds.size)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalIconButton(
                            onClick = {
                                rangeSelectionArmed = !rangeSelectionArmed
                                if (rangeAnchorId == null) {
                                    rangeAnchorId = logs.lastOrNull { it.id in selectedIds }?.id
                                }
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (rangeSelectionArmed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                contentColor = if (rangeSelectionArmed) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = stringResource(R.string.logs_range_select)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                val allIds = logs.map { it.id }.toSet()
                                selectedIds = allIds
                                selectionMode = allIds.isNotEmpty()
                                rangeSelectionArmed = false
                                rangeAnchorId = logs.lastOrNull()?.id
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.logs_select_all_desc)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { copySelection() },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.menu_copy)
                            )
                        }

                        FilledTonalIconButton(onClick = { exitSelectionMode() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.dialog_cancel)
                            )
                        }
                    }
                }
            } else {
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

@Volatile
private var cachedAnalysisTips: List<AnalysisTip>? = null

private fun loadAnalysisTips(context: android.content.Context): List<AnalysisTip> {
    cachedAnalysisTips?.let { return it }
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
    }.getOrDefault(emptyList()).also { loaded ->
        if (loaded.isNotEmpty()) cachedAnalysisTips = loaded
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogItem(
    entry: LogEntry,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val color = when (entry.type) {
        LogType.COMMAND -> MaterialTheme.colorScheme.primary
        LogType.OUTPUT -> Color(0xFFE0E0E0)
        LogType.INFO -> Color.Gray
        LogType.WARNING -> Color(0xFFFFA000)
        LogType.ERROR -> MaterialTheme.colorScheme.error
    }

    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        selectionMode -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
        else -> Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = logEntryDisplayText(entry),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

private fun logEntryDisplayText(entry: LogEntry): String {
    val prefix = when (entry.type) {
        LogType.COMMAND -> "$ "
        LogType.WARNING -> "[WARN] "
        LogType.ERROR -> "[ERR] "
        else -> ""
    }
    val content = "$prefix${entry.message}"
    return if (content.length > 2000) {
        content.take(2000) + "... (truncated ${content.length - 2000} chars)"
    } else {
        content
    }
}



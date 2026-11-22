package top.wsdx233.r2droid.feature.annotations

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import org.radare.r2pipe.kotlin.R2PipeSession
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.AutoHideScrollbar
import top.wsdx233.r2droid.core.ui.components.ListItemActions
import top.wsdx233.r2droid.core.ui.components.SearchBar
import top.wsdx233.r2droid.core.ui.components.UnifiedListItemWrapper
import top.wsdx233.r2droid.util.R2PipeManager

internal enum class AnnotationIndex(@StringRes val title: Int, val alias: String, @StringRes val empty: Int) {
    Bookmarks(R.string.annotations_index_bookmarks, "list.bookmarks", R.string.annotations_index_empty_bookmarks),
    Comments(R.string.annotations_index_comments, "list.comments", R.string.annotations_index_empty_comments),
    Names(R.string.annotations_index_names, "list.names", R.string.annotations_index_empty_names);

    companion object {
        const val FIRST_TAB = 9
        val LAST_TAB get() = FIRST_TAB + entries.lastIndex
    }
}

@Composable
internal fun AnnotationIndexScreen(
    index: AnnotationIndex,
    sessionToken: R2PipeSession,
    projectInvalidation: StateFlow<Long>,
    disasmInvalidation: StateFlow<Long>,
    actions: ListItemActions
) = key(index, sessionToken) {
    val activeId by R2PipeManager.activeSessionId.collectAsState()
    val sessions by R2PipeManager.sessions.collectAsState()
    // The project VM may still expose its old Success state during a drawer switch/reconnect.
    val selected = activeId?.let { sessions[it]?.pipe } === sessionToken
    val selectionGeneration = R2PipeManager.sessionId
    val projectVersion by projectInvalidation.collectAsState()
    val disasmVersion by disasmInvalidation.collectAsState()
    val listState = rememberLazyListState()
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var result by remember(selected, selectionGeneration, projectVersion, disasmVersion, refresh) {
        mutableStateOf<Result<List<AnnotationRow>>?>(null)
    }

    LaunchedEffect(selected, selectionGeneration, projectVersion, disasmVersion, refresh) {
        if (!selected) return@LaunchedEffect
        val loaded = try {
            val session = checkNotNull(AnnotationSession.capture(sessionToken))
            Result.success(when (index) {
                AnnotationIndex.Bookmarks -> session.listBookmarks()
                AnnotationIndex.Comments -> session.listComments()
                AnnotationIndex.Names -> session.listNames()
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        currentCoroutineContext().ensureActive()
        if (R2PipeManager.activeSessionPipe === sessionToken && R2PipeManager.sessionId == selectionGeneration) {
            result = loaded
        }
    }

    val sessionActions = remember(actions, sessionToken, selectionGeneration) {
        // Guard actions from a row that was rendered just before a session switch.
        fun ifCurrent(action: (Long) -> Unit): (Long) -> Unit = { address ->
            if (R2PipeManager.activeSessionPipe === sessionToken && R2PipeManager.sessionId == selectionGeneration) {
                action(address)
            }
        }
        actions.copy(
            onQuickJump = actions.onQuickJump?.let { ifCurrent(it) },
            onMarkVisited = actions.onMarkVisited?.let { ifCurrent(it) },
            onJumpToHex = ifCurrent(actions.onJumpToHex),
            onJumpToDisasm = ifCurrent(actions.onJumpToDisasm),
            onShowXrefs = ifCurrent(actions.onShowXrefs),
            onAnnotate = actions.onAnnotate?.let { annotate ->
                { action, address -> ifCurrent { annotate(action, it) }(address) }
            }
        )
    }
    val rows = result?.getOrNull()
    val filteredRows = remember(rows, query) {
        val term = query.trim()
        if (term.isEmpty()) rows.orEmpty()
        else rows.orEmpty().filter {
            it.text.contains(term, ignoreCase = true) || annotationAddress(it.address).contains(term, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.common_search),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { refresh++ }, enabled = selected) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                !selected || result == null -> CircularProgressIndicator()
                result?.isFailure == true -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.annotations_index_error), textAlign = TextAlign.Center)
                        TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.common_retry)) }
                    }
                }
                filteredRows.isEmpty() -> Text(stringResource(if (rows.isNullOrEmpty()) index.empty else R.string.search_no_results))
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRows) { row ->
                            key(row.address, row.text) { AnnotationIndexItem(index, row, sessionActions) }
                        }
                    }
                    AutoHideScrollbar(listState, filteredRows.size, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }
}

@Composable
private fun AnnotationIndexItem(index: AnnotationIndex, row: AnnotationRow, actions: ListItemActions) {
    val address = annotationAddress(row.address)
    UnifiedListItemWrapper(
        title = row.text,
        address = row.address,
        fullText = if (index == AnnotationIndex.Bookmarks) address else "$address  ${row.text}",
        actions = actions
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (index != AnnotationIndex.Bookmarks) {
                Text(text = row.text, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = address,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

package top.wsdx233.r2droid.feature.project.patches

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager

@Composable
internal fun PatchesScreen(
    session: R2PipeManager.R2Session,
    onChanged: () -> Unit,
    onJump: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifyChanged by rememberUpdatedState(onChanged)
    val repository = remember(session.pipe, session.projectPath) { WriteCacheRepository(session) }
    val generation = R2PipeManager.sessionId
    val page = remember(session.pipe, session.projectPath, generation) { PatchPage() }
    val listState = rememberLazyListState()
    val current = {
        val active = R2PipeManager.activeSession()
        active != null && active.pipe === session.pipe && active.projectPath == session.projectPath &&
            R2PipeManager.sessionId == generation
    }
    var running by remember { mutableStateOf(false) }
    // Save only the fact that a result is outstanding. Never restore a ticket or its bytes.
    var awaitingPicker by rememberSaveable { mutableStateOf(false) }
    var exportTicket by remember { mutableStateOf<ExportTicket?>(null) }
    val busy = running || awaitingPicker

    LaunchedEffect(page) {
        if (exportTicket?.page !== page) exportTicket = null
    }

    LaunchedEffect(page, page.reload) {
        try {
            page.snapshot = repository.snapshot()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            page.loadFailure = failure.toNotice(R.string.patches_load_failed)
        } finally {
            page.loading = false
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (awaitingPicker) {
            val ticket = exportTicket
            awaitingPicker = false
            exportTicket = null
            if (uri != null) {
                if (ticket == null || ticket.page !== page || !current() || running) {
                    page.notice = PatchNotice(R.string.patches_export_expired, error = true)
                } else {
                    page.notice = null
                    running = true
                    scope.launch {
                        try {
                            repository.exportPatchedCopy(context, uri, ticket.snapshot)
                            page.notice = PatchNotice(R.string.patches_export_done)
                        } catch (failure: Exception) {
                            if (failure is CancellationException) throw failure
                            page.notice = failure.toNotice(R.string.patches_export_failed)
                            if (failure is StalePatchSnapshotException && current()) page.refresh()
                        } finally {
                            running = false
                        }
                    }
                }
            }
        }
    }

    fun startExport(snapshot: PatchSnapshot) {
        if (running || awaitingPicker || !current() || page.snapshot !== snapshot || !snapshot.canExport) return
        page.confirmation = null
        exportTicket = ExportTicket(page, snapshot)
        awaitingPicker = true
        try {
            val stem = session.projectPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "binary"
            createDocument.launch("${stem.take(160)}.patched")
        } catch (failure: Exception) {
            awaitingPicker = false
            exportTicket = null
            if (failure is CancellationException) throw failure
            page.notice = failure.toNotice(R.string.patches_picker_failed)
        }
    }

    fun mutate(request: PatchConfirmation) {
        if (running || awaitingPicker || page.confirmation !== request) return
        if (!current() || page.snapshot !== request.snapshot) {
            page.confirmation = null
            return
        }
        page.confirmation = null
        page.notice = null
        running = true
        scope.launch {
            var possiblyChanged = true
            try {
                when (request.action) {
                    PatchAction.Undo -> repository.undoLast(request.snapshot)
                    PatchAction.UndoEntry -> repository.undoEntry(request.snapshot, requireNotNull(request.entry).index)
                    PatchAction.Reset -> repository.reset(request.snapshot)
                    PatchAction.Commit -> repository.commit(request.snapshot)
                }
                page.notice = PatchNotice(request.action.success)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (failure is StalePatchSnapshotException) possiblyChanged = false
                page.notice = failure.toNotice(R.string.patches_update_failed)
            } finally {
                running = false
                if (current()) {
                    listState.requestScrollToItem(0)
                    page.refresh()
                    // A failed commit can still have changed some bytes.
                    if (possiblyChanged) notifyChanged()
                }
            }
        }
    }

    fun refresh() {
        if (!running && !awaitingPicker && !page.loading && current()) page.refresh()
    }

    val snapshot = page.snapshot
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PatchToolbar(
                snapshot = snapshot,
                busy = busy || page.loading,
                onRefresh = ::refresh,
                onConfirm = { action ->
                    if (!running && !awaitingPicker && snapshot != null && current() && page.snapshot === snapshot) {
                        page.confirmation = PatchConfirmation(action, snapshot)
                    }
                },
                onExport = { snapshot?.let(::startExport) }
            )
        }
        if (running) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        page.notice?.let { notice -> item { PatchNoticePanel(notice) } }
        when {
            page.loading -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.patches_loading), style = MaterialTheme.typography.bodyMedium)
                }
            }
            page.loadFailure != null -> item {
                page.loadFailure?.let { failure ->
                    PatchNoticePanel(failure) {
                        TextButton(
                            enabled = !busy,
                            onClick = ::refresh
                        ) { Text(stringResource(R.string.common_retry)) }
                    }
                }
            }
            snapshot != null -> {
                if (snapshot.layerCount > 1) {
                    item { PatchNoticePanel(PatchNotice(R.string.patches_layered)) }
                }
                if (snapshot.entries.isEmpty() && snapshot.layerCount <= 1) {
                    item { PatchNoticePanel(PatchNotice(R.string.patches_empty)) }
                } else {
                    items(snapshot.entries) { patch ->
                        PatchCard(
                            patch = patch,
                            snapshot = snapshot,
                            busy = busy,
                            onJump = {
                                if (!running && !awaitingPicker && current() && page.snapshot === snapshot) {
                                    onJump(patch.address.toLong())
                                }
                            },
                            onUndo = {
                                if (!running && !awaitingPicker && current() && page.snapshot === snapshot) {
                                    page.confirmation = PatchConfirmation(PatchAction.UndoEntry, snapshot, patch)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    page.confirmation?.let { request ->
        AlertDialog(
            onDismissRequest = { page.confirmation = null },
            title = { Text(stringResource(request.action.confirmTitle)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(request.action.description))
                    request.entry?.let { entry ->
                        Text(stringResource(R.string.patches_undo_entry_address, entry.address.asHexAddress()))
                    }
                    if (request.action == PatchAction.Commit) {
                        session.projectPath?.let { path ->
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    textDirection = TextDirection.Ltr
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { page.confirmation = null }) { Text(stringResource(R.string.dialog_cancel)) }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = { mutate(request) }
                ) { Text(stringResource(request.action.label)) }
            }
        )
    }
}

@Composable
private fun PatchToolbar(
    snapshot: PatchSnapshot?,
    busy: Boolean,
    onRefresh: () -> Unit,
    onConfirm: (PatchAction) -> Unit,
    onExport: () -> Unit
) {
    var expanded by remember(snapshot) { mutableStateOf(false) }
    val canExport = remember(snapshot) { snapshot?.canExport == true }
    val canUndoLatest = remember(snapshot) { snapshot?.canUndo == true }
    val canCommit = remember(snapshot) { snapshot?.canCommit == true }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(enabled = !busy && canExport, onClick = onExport, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.patches_export))
            }
            IconButton(enabled = !busy, onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
            }
            Box {
                IconButton(enabled = !busy && snapshot?.entries?.isNotEmpty() == true, onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.patches_actions))
                }
                if (!busy && snapshot != null) {
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        for (action in CACHE_ACTIONS) {
                            DropdownMenuItem(
                                text = { Text(stringResource(action.label)) },
                                enabled = when (action) {
                                    PatchAction.Undo -> canUndoLatest
                                    PatchAction.Reset -> snapshot.canReset
                                    PatchAction.Commit -> canCommit
                                    PatchAction.UndoEntry -> false
                                },
                                onClick = {
                                    expanded = false
                                    onConfirm(action)
                                }
                            )
                        }
                        if (snapshot.layerCount == 1 && snapshot.entries.lastOrNull()?.written == false && !canUndoLatest) {
                            Text(
                                text = stringResource(R.string.patches_undo_overlap),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
        if (snapshot != null && snapshot.entries.isNotEmpty()) {
            val written = remember(snapshot) { snapshot.entries.count { it.written } }
            val pending = if (snapshot.layerCount == 1) snapshot.pendingIndices.size else snapshot.entries.size - written
            val superseded = snapshot.entries.size - written - pending
            Text(
                text = if (superseded > 0) {
                    stringResource(R.string.patches_summary_with_superseded, pending, written, superseded)
                } else {
                    stringResource(R.string.patches_summary, pending, written)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        if (canExport) {
            Text(
                text = stringResource(R.string.patches_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PatchCard(
    patch: CachePatch,
    snapshot: PatchSnapshot,
    busy: Boolean,
    onJump: () -> Unit,
    onUndo: () -> Unit
) {
    var expanded by remember(patch) { mutableStateOf(false) }
    val address = remember(patch.address) { patch.address.asHexAddress() }
    val clipboard = LocalClipboardManager.current
    val limit = if (expanded) EXPANDED_BYTES else PREVIEW_BYTES
    val superseded = !patch.written && snapshot.layerCount == 1 && patch.index !in snapshot.pendingIndices
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = address,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = pluralStringResource(R.plurals.patches_byte_count, patch.size, patch.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when {
                        patch.written -> MaterialTheme.colorScheme.secondaryContainer
                        superseded -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Text(
                        text = stringResource(when {
                            patch.written -> R.string.patches_written
                            superseded -> R.string.patches_superseded
                            else -> R.string.patches_pending
                        }),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.patches_hide_details else R.string.patches_show_details))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PatchBytes(R.string.patches_original, patch.original, limit, Modifier.weight(1f))
                PatchBytes(R.string.patches_modified, patch.replacement, limit, Modifier.weight(1f))
            }
            if (patch.size > limit) {
                Text(
                    text = stringResource(R.string.patches_preview, limit, patch.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onJump, enabled = !busy) { Text(stringResource(R.string.patches_jump)) }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(address)) }) {
                        Text(stringResource(R.string.patches_copy_address))
                    }
                }
                val individualUndo = !patch.written && snapshot.layerCount == 1
                if (individualUndo) {
                    val canUndo = remember(snapshot, patch.index) { snapshot.canUndoEntry(patch.index) }
                    if (canUndo) {
                        TextButton(onClick = onUndo, enabled = !busy) {
                            Text(stringResource(R.string.patches_undo_entry))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.patches_undo_overlap),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatchBytes(@StringRes label: Int, hex: String, limit: Int, modifier: Modifier) {
    val visibleBytes = remember(hex, limit) { hex.take(limit * 2).chunked(2).joinToString(" ") }
    Column(modifier) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = visibleBytes,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
            color = if (label == R.string.patches_modified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PatchNoticePanel(notice: PatchNotice, action: (@Composable () -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = if (notice.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (notice.error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(notice.title), style = MaterialTheme.typography.bodyMedium)
            notice.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            action?.invoke()
        }
    }
}

private class PatchPage {
    var snapshot by mutableStateOf<PatchSnapshot?>(null)
    var loading by mutableStateOf(true)
    var loadFailure by mutableStateOf<PatchNotice?>(null)
    var notice by mutableStateOf<PatchNotice?>(null)
    var reload by mutableIntStateOf(0)
    var confirmation by mutableStateOf<PatchConfirmation?>(null)

    fun refresh() {
        confirmation = null
        snapshot = null
        loading = true
        loadFailure = null
        reload++
    }
}

private data class PatchNotice(@StringRes val title: Int, val detail: String? = null, val error: Boolean = false)

private fun Exception.toNotice(@StringRes fallback: Int): PatchNotice =
    if (this is StalePatchSnapshotException) PatchNotice(R.string.patches_stale, error = true)
    else PatchNotice(fallback, message?.take(360)?.takeIf { it.isNotBlank() }, error = true)

private class ExportTicket(val page: PatchPage, val snapshot: PatchSnapshot)

private class PatchConfirmation(val action: PatchAction, val snapshot: PatchSnapshot, val entry: CachePatch? = null)

private enum class PatchAction(
    @StringRes val label: Int,
    @StringRes val confirmTitle: Int,
    @StringRes val description: Int,
    @StringRes val success: Int
) {
    Undo(R.string.patches_undo, R.string.patches_undo_title, R.string.patches_undo_description, R.string.patches_undo_done),
    UndoEntry(
        R.string.patches_undo_entry,
        R.string.patches_undo_entry_title,
        R.string.patches_undo_entry_description,
        R.string.patches_undo_entry_done
    ),
    Reset(R.string.patches_reset, R.string.patches_reset_title, R.string.patches_reset_description, R.string.patches_reset_done),
    Commit(R.string.patches_commit, R.string.patches_commit_title, R.string.patches_commit_description, R.string.patches_commit_done)
}

private fun ULong.asHexAddress(): String = "0x${toString(16)}"

private val CACHE_ACTIONS = listOf(PatchAction.Undo, PatchAction.Reset, PatchAction.Commit)

private const val PREVIEW_BYTES = 16
private const val EXPANDED_BYTES = 128

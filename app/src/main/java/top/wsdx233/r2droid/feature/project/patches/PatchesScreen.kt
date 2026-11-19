package top.wsdx233.r2droid.feature.project.patches

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.AutoHideScrollbar
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.util.R2PipeManager

@Composable
internal fun PatchesScreen(
    session: R2PipeManager.R2Session,
    onChanged: () -> Unit,
    onJump: (Long) -> Unit,
    onJumpToDisasm: ((Long) -> Unit)? = null
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
    val fileName = remember(session.projectPath) {
        session.projectPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

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
                    page.notice = PatchNotice(R.string.patches_export_expired, tone = NoticeTone.Error)
                } else {
                    page.notice = null
                    running = true
                    scope.launch {
                        try {
                            repository.exportPatchedCopy(context, uri, ticket.snapshot)
                            page.notice = PatchNotice(R.string.patches_export_done, tone = NoticeTone.Success)
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
            val stem = fileName ?: "binary"
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
                page.notice = PatchNotice(request.action.success, tone = NoticeTone.Success)
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

    PatchesContent(
        fileName = fileName,
        projectPath = session.projectPath,
        snapshot = page.snapshot,
        loading = page.loading,
        running = running,
        busy = busy,
        notice = page.notice,
        loadFailure = page.loadFailure,
        confirmation = page.confirmation,
        listState = listState,
        onRefresh = ::refresh,
        onDismissNotice = { page.notice = null },
        onExport = { page.snapshot?.let(::startExport) },
        onRequestAction = { action, entry ->
            val currentSnapshot = page.snapshot
            if (!running && !awaitingPicker && currentSnapshot != null && current()) {
                page.confirmation = PatchConfirmation(action, currentSnapshot, entry)
            }
        },
        onDismissConfirmation = { page.confirmation = null },
        onConfirmAction = ::mutate,
        onJumpToHex = { address ->
            val currentSnapshot = page.snapshot
            if (!running && !awaitingPicker && currentSnapshot != null && current()) {
                onJump(address)
            }
        },
        onJumpToDisasm = onJumpToDisasm?.let { jump ->
            { address ->
                val currentSnapshot = page.snapshot
                if (!running && !awaitingPicker && currentSnapshot != null && current()) {
                    jump(address)
                }
            }
        }
    )
}

@Composable
internal fun PatchesContent(
    fileName: String?,
    projectPath: String?,
    snapshot: PatchSnapshot?,
    loading: Boolean,
    running: Boolean,
    busy: Boolean,
    notice: PatchNotice?,
    loadFailure: PatchNotice?,
    confirmation: PatchConfirmation?,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    onRefresh: () -> Unit,
    onDismissNotice: () -> Unit,
    onExport: () -> Unit,
    onRequestAction: (PatchAction, CachePatch?) -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmAction: (PatchConfirmation) -> Unit,
    onJumpToHex: (Long) -> Unit,
    onJumpToDisasm: ((Long) -> Unit)? = null
) {
    var activeFilter by remember(snapshot) { mutableStateOf<PatchStatus?>(null) }
    val visibleEntries = remember(snapshot, activeFilter) {
        val entries = snapshot?.entries.orEmpty()
        val filter = activeFilter
        if (snapshot == null || filter == null) entries
        else entries.filter { it.statusIn(snapshot) == filter }
    }
    val headerItemCount = 1 +
        (if (notice != null) 1 else 0) +
        (if (snapshot != null && snapshot.layerCount > 1) 1 else 0)

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "overview") {
                    PatchOverviewCard(
                        fileName = fileName,
                        snapshot = snapshot,
                        busy = busy || loading,
                        activeFilter = activeFilter,
                        onToggleFilter = { status ->
                            activeFilter = if (activeFilter == status) null else status
                        },
                        onRefresh = onRefresh,
                        onConfirm = { action -> onRequestAction(action, null) },
                        onExport = onExport
                    )
                }

                notice?.let { activeNotice ->
                    item(key = "notice") {
                        PatchBanner(
                            notice = activeNotice,
                            onDismiss = onDismissNotice
                        )
                    }
                }

                when {
                    loading -> item(key = "loading") {
                        PatchLoadingState()
                    }

                    loadFailure != null -> item(key = "failure") {
                        PatchBanner(notice = loadFailure) {
                            FilledTonalButton(
                                enabled = !busy,
                                onClick = onRefresh,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.common_retry))
                            }
                        }
                    }

                    snapshot != null -> {
                        if (snapshot.layerCount > 1) {
                            item(key = "layered") {
                                PatchBanner(
                                    notice = PatchNotice(R.string.patches_layered, tone = NoticeTone.Warning)
                                )
                            }
                        }
                        if (snapshot.entries.isEmpty() && snapshot.layerCount <= 1) {
                            item(key = "empty") {
                                PatchEmptyState()
                            }
                        } else {
                            items(visibleEntries, key = { it.index }) { patch ->
                                PatchCard(
                                    patch = patch,
                                    snapshot = snapshot,
                                    busy = busy,
                                    onJumpToHex = { onJumpToHex(patch.address.toLong()) },
                                    onJumpToDisasm = onJumpToDisasm?.let { jump ->
                                        { jump(patch.address.toLong()) }
                                    },
                                    onUndo = { onRequestAction(PatchAction.UndoEntry, patch) }
                                )
                            }
                            item(key = "bottom_spacer") {
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            if (visibleEntries.isNotEmpty()) {
                AutoHideScrollbar(
                    listState = listState,
                    totalItems = visibleEntries.size + headerItemCount,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    confirmation?.let { request ->
        PatchConfirmationDialog(
            request = request,
            projectPath = projectPath,
            busy = busy,
            onDismiss = onDismissConfirmation,
            onConfirm = { onConfirmAction(request) }
        )
    }
}

@Composable
private fun PatchOverviewCard(
    fileName: String?,
    snapshot: PatchSnapshot?,
    busy: Boolean,
    activeFilter: PatchStatus?,
    onToggleFilter: (PatchStatus) -> Unit,
    onRefresh: () -> Unit,
    onConfirm: (PatchAction) -> Unit,
    onExport: () -> Unit
) {
    var menuExpanded by remember(snapshot) { mutableStateOf(false) }
    val canExport = remember(snapshot) { snapshot?.canExport == true }
    val canUndoLatest = remember(snapshot) { snapshot?.canUndo == true }
    val canCommit = remember(snapshot) { snapshot?.canCommit == true }
    val hasEntries = snapshot != null && snapshot.entries.isNotEmpty()

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Icon + Title/Filename + Refresh + Actions menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Difference,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.proj_tab_patches),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (fileName != null) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = LocalAppFont.current,
                                textDirection = TextDirection.Ltr
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = !busy, onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.common_refresh),
                            tint = if (!busy) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    Box {
                        IconButton(
                            enabled = !busy && hasEntries,
                            onClick = { menuExpanded = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.patches_actions)
                            )
                        }
                        if (!busy && snapshot != null) {
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(PatchAction.Undo.label)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = null
                                        )
                                    },
                                    enabled = canUndoLatest,
                                    onClick = {
                                        menuExpanded = false
                                        onConfirm(PatchAction.Undo)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(PatchAction.Commit.label)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = null
                                        )
                                    },
                                    enabled = canCommit,
                                    onClick = {
                                        menuExpanded = false
                                        onConfirm(PatchAction.Commit)
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(PatchAction.Reset.label),
                                            color = if (snapshot.canReset) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = null,
                                            tint = if (snapshot.canReset) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    },
                                    enabled = snapshot.canReset,
                                    onClick = {
                                        menuExpanded = false
                                        onConfirm(PatchAction.Reset)
                                    }
                                )
                                if (snapshot.layerCount == 1 && snapshot.entries.lastOrNull()?.written == false && !canUndoLatest) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(16.dp)
                                        )
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
            }

            if (snapshot != null && snapshot.entries.isNotEmpty()) {
                val written = remember(snapshot) { snapshot.entries.count { it.written } }
                val pending = if (snapshot.layerCount == 1) snapshot.pendingIndices.size else snapshot.entries.size - written
                val superseded = snapshot.entries.size - written - pending
                val nonEmptyBuckets = listOf(pending, written, superseded).count { it > 0 }
                val canFilter = nonEmptyBuckets > 1
                val summaryDesc = if (superseded > 0) {
                    stringResource(R.string.patches_summary_with_superseded, pending, written, superseded)
                } else {
                    stringResource(R.string.patches_summary, pending, written)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Metrics row (tap to filter when multiple status buckets exist)
                val hasActiveFilter = activeFilter != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = summaryDesc },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PatchStatTile(
                        count = pending,
                        label = stringResource(R.string.patches_pending),
                        accent = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selected = activeFilter == PatchStatus.Pending,
                        dimmed = hasActiveFilter && activeFilter != PatchStatus.Pending,
                        clickable = canFilter && pending > 0,
                        onClick = { onToggleFilter(PatchStatus.Pending) },
                        modifier = Modifier.weight(1f)
                    )
                    PatchStatTile(
                        count = written,
                        label = stringResource(R.string.patches_written),
                        accent = MaterialTheme.colorScheme.tertiary,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        selected = activeFilter == PatchStatus.Written,
                        dimmed = hasActiveFilter && activeFilter != PatchStatus.Written,
                        clickable = canFilter && written > 0,
                        onClick = { onToggleFilter(PatchStatus.Written) },
                        modifier = Modifier.weight(1f)
                    )
                    if (superseded > 0) {
                        PatchStatTile(
                            count = superseded,
                            label = stringResource(R.string.patches_superseded),
                            accent = MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selected = activeFilter == PatchStatus.Superseded,
                            dimmed = hasActiveFilter && activeFilter != PatchStatus.Superseded,
                            clickable = canFilter,
                            onClick = { onToggleFilter(PatchStatus.Superseded) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Action buttons
                if (canExport || canUndoLatest) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            enabled = !busy && canExport,
                            onClick = onExport,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.patches_export),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalIconButton(
                            enabled = !busy && canUndoLatest,
                            onClick = { onConfirm(PatchAction.Undo) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.patches_undo)
                            )
                        }
                    }
                }

                if (canCommit) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { onConfirm(PatchAction.Commit) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.patches_commit),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (!canExport && snapshot.canReset) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { onConfirm(PatchAction.Reset) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.patches_reset),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (canExport) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.patches_export_hint),
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
private fun PatchStatTile(
    count: Int,
    label: String,
    accent: Color,
    containerColor: Color,
    contentColor: Color,
    selected: Boolean,
    dimmed: Boolean,
    clickable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (dimmed) 0.55f else 1f
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = when {
            selected -> containerColor.copy(alpha = 0.9f)
            dimmed -> containerColor.copy(alpha = containerColor.alpha * 0.6f)
            else -> containerColor
        },
        contentColor = contentColor.copy(alpha = contentAlpha),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else accent.copy(alpha = if (dimmed) 0.08f else 0.14f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = LocalAppFont.current,
                        fontWeight = FontWeight.Bold
                    ),
                    color = contentColor.copy(alpha = contentAlpha)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f * contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatchCard(
    patch: CachePatch,
    snapshot: PatchSnapshot,
    busy: Boolean,
    onJumpToHex: () -> Unit,
    onJumpToDisasm: (() -> Unit)?,
    onUndo: () -> Unit
) {
    var expanded by remember(patch) { mutableStateOf(false) }
    val address = remember(patch.address) { patch.address.asHexAddress() }
    val endAddress = remember(patch.address, patch.size) { patch.lastAddress.asHexAddress() }
    val context = LocalContext.current
    val limit = if (expanded) EXPANDED_BYTES else PREVIEW_BYTES
    val status = remember(patch, snapshot) { patch.statusIn(snapshot) }

    val accent = when (status) {
        PatchStatus.Pending -> MaterialTheme.colorScheme.primary
        PatchStatus.Written -> MaterialTheme.colorScheme.tertiary
        PatchStatus.Superseded -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusContainer = when (status) {
        PatchStatus.Pending -> MaterialTheme.colorScheme.primaryContainer
        PatchStatus.Written -> MaterialTheme.colorScheme.tertiaryContainer
        PatchStatus.Superseded -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val statusContent = when (status) {
        PatchStatus.Pending -> MaterialTheme.colorScheme.onPrimaryContainer
        PatchStatus.Written -> MaterialTheme.colorScheme.onTertiaryContainer
        PatchStatus.Superseded -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when (status) {
        PatchStatus.Pending -> R.string.patches_pending
        PatchStatus.Written -> R.string.patches_written
        PatchStatus.Superseded -> R.string.patches_superseded
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "patch_expand_chevron"
    )

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) accent.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Clickable header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = address,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = LocalAppFont.current,
                                fontWeight = FontWeight.SemiBold,
                                textDirection = TextDirection.Ltr
                            ),
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .semantics { heading() }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "#${patch.index}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalAppFont.current),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = pluralStringResource(R.plurals.patches_byte_count, patch.size, patch.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (patch.size > 1) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                            Text(
                                text = endAddress,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = LocalAppFont.current,
                                    textDirection = TextDirection.Ltr
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Status pill badge
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusContainer,
                    contentColor = statusContent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (status) {
                            PatchStatus.Pending -> Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )

                            PatchStatus.Written -> Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )

                            PatchStatus.Superseded -> Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = stringResource(statusLabel),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.patches_hide_details else R.string.patches_show_details
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevronRotation)
                    )
                }
            }

            // Byte diff inset container + expanded actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val diffAccent = if (status == PatchStatus.Superseded) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
                PatchByteDiffBlock(
                    original = patch.original,
                    replacement = patch.replacement,
                    size = patch.size,
                    limit = limit,
                    accent = diffAccent,
                    superseded = status == PatchStatus.Superseded,
                    selectable = expanded,
                    onExpandRequest = if (!expanded) ({ expanded = true }) else null
                )

                if (expanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    val individualUndo = !patch.written && snapshot.layerCount == 1
                    val canUndo = remember(snapshot, patch.index, individualUndo) {
                        individualUndo && snapshot.canUndoEntry(patch.index)
                    }
                    val compactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onJumpToHex,
                            enabled = !busy,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = compactButtonPadding
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.patches_jump),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        if (onJumpToDisasm != null) {
                            FilledTonalButton(
                                onClick = onJumpToDisasm,
                                enabled = !busy,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = compactButtonPadding
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.menu_disassembly),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("address", address))
                                Toast.makeText(context, R.string.ai_copied, Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = compactButtonPadding
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.patches_copy_address),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        if (canUndo) {
                            OutlinedButton(
                                onClick = onUndo,
                                enabled = !busy,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = compactButtonPadding,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.patches_undo_entry),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    if (individualUndo && !canUndo) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .size(15.dp)
                                )
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
    }
}

@Composable
private fun PatchByteDiffBlock(
    original: String,
    replacement: String,
    size: Int,
    limit: Int,
    accent: Color,
    superseded: Boolean,
    selectable: Boolean,
    onExpandRequest: (() -> Unit)? = null
) {
    val errorColor = MaterialTheme.colorScheme.error
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val visibleCount = minOf(size, limit)

    val (originalText, modifiedText) = remember(
        original,
        replacement,
        visibleCount,
        accent,
        superseded,
        errorColor,
        onSurface,
        onSurfaceVariant
    ) {
        buildByteDiffStrings(
            original = original,
            replacement = replacement,
            count = visibleCount,
            accent = accent,
            superseded = superseded,
            errorColor = errorColor,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onExpandRequest != null) Modifier.clickable(onClick = onExpandRequest) else Modifier),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ByteDiffSection(
                marker = "−",
                markerColor = if (superseded) onSurfaceVariant else errorColor,
                label = stringResource(R.string.patches_original),
                labelColor = onSurfaceVariant,
                bytes = originalText,
                selectable = selectable
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            ByteDiffSection(
                marker = "+",
                markerColor = accent,
                label = stringResource(R.string.patches_modified),
                labelColor = if (superseded) onSurfaceVariant else accent,
                bytes = modifiedText,
                selectable = selectable
            )

            if (size > limit) {
                Text(
                    text = stringResource(R.string.patches_preview, limit, size),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ByteDiffSection(
    marker: String,
    markerColor: Color,
    label: String,
    labelColor: Color,
    bytes: AnnotatedString,
    selectable: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = markerColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = marker,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = LocalAppFont.current,
                        fontWeight = FontWeight.Bold
                    ),
                    color = markerColor,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = labelColor
            )
        }
        val byteStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = LocalAppFont.current,
            textDirection = TextDirection.Ltr
        )
        if (selectable) {
            SelectionContainer {
                Text(text = bytes, style = byteStyle)
            }
        } else {
            Text(text = bytes, style = byteStyle)
        }
    }
}

private fun buildByteDiffStrings(
    original: String,
    replacement: String,
    count: Int,
    accent: Color,
    superseded: Boolean,
    errorColor: Color,
    onSurface: Color,
    onSurfaceVariant: Color
): Pair<AnnotatedString, AnnotatedString> {
    var hasUnchanged = false
    for (i in 0 until count) {
        val pos = i * 2
        if (original.regionMatches(pos, replacement, pos, 2, ignoreCase = true)) {
            hasUnchanged = true
            break
        }
    }
    val unchangedColor = onSurfaceVariant.copy(alpha = if (superseded) 0.55f else 0.65f)
    val origChangedStyle = when {
        superseded -> SpanStyle(color = onSurfaceVariant)
        hasUnchanged -> SpanStyle(
            color = errorColor,
            fontWeight = FontWeight.SemiBold,
            background = errorColor.copy(alpha = 0.10f)
        )
        else -> SpanStyle(color = onSurface.copy(alpha = 0.85f))
    }
    val replChangedStyle = when {
        superseded -> SpanStyle(color = onSurfaceVariant, fontWeight = FontWeight.Medium)
        hasUnchanged -> SpanStyle(
            color = accent,
            fontWeight = FontWeight.Bold,
            background = accent.copy(alpha = 0.14f)
        )
        else -> SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
    }
    val unchangedStyle = SpanStyle(color = unchangedColor)

    val origBuilder = buildAnnotatedString {
        for (i in 0 until count) {
            if (i > 0) append(' ')
            val pos = i * 2
            val origByte = original.substring(pos, pos + 2).uppercase()
            val changed = !original.regionMatches(pos, replacement, pos, 2, ignoreCase = true)
            withStyle(if (changed) origChangedStyle else unchangedStyle) {
                append(origByte)
            }
        }
    }
    val replBuilder = buildAnnotatedString {
        for (i in 0 until count) {
            if (i > 0) append(' ')
            val pos = i * 2
            val replByte = replacement.substring(pos, pos + 2).uppercase()
            val changed = !original.regionMatches(pos, replacement, pos, 2, ignoreCase = true)
            withStyle(if (changed) replChangedStyle else unchangedStyle) {
                append(replByte)
            }
        }
    }
    return origBuilder to replBuilder
}

@Composable
private fun PatchBanner(
    notice: PatchNotice,
    onDismiss: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    val containerColor: Color
    val contentColor: Color
    val iconTint: Color
    val icon: ImageVector
    when (notice.tone) {
        NoticeTone.Error -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            iconTint = MaterialTheme.colorScheme.error
            icon = Icons.Default.ErrorOutline
        }

        NoticeTone.Warning -> {
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer
            icon = Icons.Default.WarningAmber
        }

        NoticeTone.Success -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            iconTint = MaterialTheme.colorScheme.primary
            icon = Icons.Default.CheckCircleOutline
        }
    }

    val singleLine = notice.detail == null && action == null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = if (onDismiss != null) 8.dp else 14.dp,
                    top = if (singleLine) 10.dp else 12.dp,
                    bottom = if (singleLine) 10.dp else 12.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(top = if (singleLine) 0.dp else 1.dp)
                    .size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(notice.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                notice.detail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.88f)
                    )
                }
                action?.invoke()
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.xrefs_close),
                        tint = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PatchLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp)
        Text(
            text = stringResource(R.string.patches_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PatchEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Difference,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.patches_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PatchConfirmationDialog(
    request: PatchConfirmation,
    projectPath: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isDestructive = request.action == PatchAction.Commit || request.action == PatchAction.Reset
    val dialogIcon = when (request.action) {
        PatchAction.Commit -> Icons.Default.WarningAmber
        PatchAction.Reset -> Icons.Default.DeleteSweep
        PatchAction.Undo, PatchAction.UndoEntry -> Icons.AutoMirrored.Filled.Undo
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = dialogIcon,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = stringResource(request.action.confirmTitle))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(request.action.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                request.entry?.let { entry ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.patches_undo_entry_address, entry.address.asHexAddress()),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = LocalAppFont.current,
                                    fontWeight = FontWeight.SemiBold,
                                    textDirection = TextDirection.Ltr
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val previewOrig = entry.original.take(16).chunked(2).joinToString(" ").uppercase()
                            val previewRepl = entry.replacement.take(16).chunked(2).joinToString(" ").uppercase()
                            val suffix = if (entry.size > 8) " …" else ""
                            Text(
                                text = "$previewOrig$suffix → $previewRepl$suffix",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = LocalAppFont.current,
                                    textDirection = TextDirection.Ltr
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (request.action == PatchAction.Commit && projectPath != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = projectPath,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = LocalAppFont.current,
                                    textDirection = TextDirection.Ltr
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                onClick = onConfirm
            ) {
                Text(stringResource(request.action.label))
            }
        }
    )
}

private enum class PatchStatus { Pending, Written, Superseded }

private fun CachePatch.statusIn(snapshot: PatchSnapshot): PatchStatus = when {
    written -> PatchStatus.Written
    snapshot.layerCount == 1 && index !in snapshot.pendingIndices -> PatchStatus.Superseded
    else -> PatchStatus.Pending
}

internal enum class NoticeTone { Success, Warning, Error }

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

internal data class PatchNotice(
    @StringRes val title: Int,
    val detail: String? = null,
    val tone: NoticeTone = NoticeTone.Warning
)

private fun Exception.toNotice(@StringRes fallback: Int): PatchNotice =
    if (this is StalePatchSnapshotException) PatchNotice(R.string.patches_stale, tone = NoticeTone.Error)
    else PatchNotice(fallback, message?.take(360)?.takeIf { it.isNotBlank() }, tone = NoticeTone.Error)

private class ExportTicket(val page: PatchPage, val snapshot: PatchSnapshot)

internal class PatchConfirmation(val action: PatchAction, val snapshot: PatchSnapshot, val entry: CachePatch? = null)

internal enum class PatchAction(
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

private const val PREVIEW_BYTES = 16
private const val EXPANDED_BYTES = 128

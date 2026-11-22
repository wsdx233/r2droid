package top.wsdx233.r2droid.feature.types.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.radare.r2pipe.kotlin.R2PipeSession
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.AutoHideScrollbar
import top.wsdx233.r2droid.core.ui.components.rememberHighlightedCCode
import top.wsdx233.r2droid.feature.types.data.EditableTypeKind
import top.wsdx233.r2droid.feature.types.data.EnumTypeInfo
import top.wsdx233.r2droid.feature.types.data.LocalTypesSnapshot
import top.wsdx233.r2droid.feature.types.data.StructTypeInfo
import top.wsdx233.r2droid.feature.types.data.TypeCategory
import top.wsdx233.r2droid.feature.types.data.TypeLinkInfo
import top.wsdx233.r2droid.feature.types.data.TypedefTypeInfo
import top.wsdx233.r2droid.feature.types.data.TypesCommandSession
import top.wsdx233.r2droid.feature.types.data.TypesJsonParser
import top.wsdx233.r2droid.feature.types.data.TypesRepository
import top.wsdx233.r2droid.feature.types.data.UnionTypeInfo
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme
import top.wsdx233.r2droid.util.R2PipeManager
import java.io.File

internal data class TypesNotice(val message: String, val isError: Boolean = false)

private sealed interface TypeEditorRequest {
    data object CreateNew : TypeEditorRequest
    data class EditExisting(val name: String, val previousC: String) : TypeEditorRequest
}

@Composable
internal fun LocalTypesScreen(
    sessionToken: R2PipeSession,
    cursorAddress: Long,
    projectInvalidation: StateFlow<Long>,
    disasmInvalidation: StateFlow<Long>,
    onTypesChanged: () -> Unit,
    onJumpToHex: (Long) -> Unit,
    onJumpToDisasm: (Long) -> Unit
) = key(sessionToken) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeId by R2PipeManager.activeSessionId.collectAsState()
    val sessions by R2PipeManager.sessions.collectAsState()
    val selected = activeId?.let { sessions[it]?.pipe } === sessionToken
    val selectionGeneration = R2PipeManager.sessionId
    val projectVersion by projectInvalidation.collectAsState()
    val disasmVersion by disasmInvalidation.collectAsState()

    var refreshTick by remember { mutableIntStateOf(0) }
    var snapshot by remember(selected, selectionGeneration) { mutableStateOf<LocalTypesSnapshot?>(null) }
    var loadError by remember(selected, selectionGeneration) { mutableStateOf<String?>(null) }
    var mutating by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<TypesNotice?>(null) }

    var selectedCategory by remember { mutableStateOf(TypeCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    // Inspector Sheet State
    var inspectedType by remember { mutableStateOf<InspectedType?>(null) }
    var inspectedCSource by remember { mutableStateOf<String?>(null) }
    var inspectedCLoading by remember { mutableStateOf(false) }

    // Modal Dialogs State
    var editorRequest by remember { mutableStateOf<TypeEditorRequest?>(null) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var linkDialogInitialType by remember { mutableStateOf<String?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<String?>(null) }

    // Live memory preview (`tp`) cache for linked addresses
    val linkPreviews = remember(sessionToken, refreshTick) { mutableStateMapOf<Long, String>() }
    val loadingPreviews = remember(sessionToken, refreshTick) { mutableStateMapOf<Long, Boolean>() }

    fun isCurrentSession(): Boolean =
        R2PipeManager.activeSessionPipe === sessionToken && R2PipeManager.sessionId == selectionGeneration

    fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("r2droid_type", text))
        Toast.makeText(context, R.string.types_copied, Toast.LENGTH_SHORT).show()
    }

    // Load snapshot
    LaunchedEffect(selected, selectionGeneration, projectVersion, disasmVersion, refreshTick) {
        if (!selected) return@LaunchedEffect
        loadError = null
        val loaded = try {
            val session = checkNotNull(TypesCommandSession.capture(sessionToken))
            Result.success(TypesRepository.loadSnapshot(session))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        currentCoroutineContext().ensureActive()
        if (isCurrentSession()) {
            loaded.onSuccess { snapshot = it }
                .onFailure { loadError = it.localizedMessage ?: context.getString(R.string.types_load_error) }
        }
    }

    // Load C definition when opening inspector
    LaunchedEffect(inspectedType) {
        val current = inspectedType ?: return@LaunchedEffect
        inspectedCSource = null
        inspectedCLoading = true
        val fetched = try {
            val session = TypesCommandSession.capture(sessionToken)
            if (session != null) TypesRepository.getTypeCDefinition(session, current.kind, current.name) else ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ""
        }
        currentCoroutineContext().ensureActive()
        if (inspectedType == current) {
            inspectedCSource = fetched
            inspectedCLoading = false
        }
    }

    // SAF .h header picker
    val openHeaderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null || !isCurrentSession()) return@rememberLauncherForActivityResult
        mutating = true
        importError = null
        scope.launch {
            var stagedFile: File? = null
            try {
                val (file, displayName) = withContext(Dispatchers.IO) {
                    val name = resolveDisplayName(context, uri)
                    val safeName = TypesJsonParser.sanitizeHeaderFilename(name)
                    val dir = File(context.cacheDir, "types_headers").apply { mkdirs() }
                    val target = File(dir, "${System.currentTimeMillis()}_$safeName")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Unable to read selected file.")
                    target to name
                }
                stagedFile = file
                val session = checkNotNull(TypesCommandSession.capture(sessionToken)) {
                    context.getString(R.string.proj_session_disconnected)
                }
                TypesRepository.importHeaderFile(session, file.absolutePath)
                if (isCurrentSession()) {
                    showImportDialog = false
                    notice = TypesNotice(context.getString(R.string.types_import_success, displayName))
                    refreshTick++
                    onTypesChanged()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (isCurrentSession()) {
                    val msg = e.localizedMessage ?: context.getString(R.string.common_error)
                    if (showImportDialog) importError = msg
                    else notice = TypesNotice(context.getString(R.string.types_import_failed, msg), isError = true)
                }
            } finally {
                stagedFile?.let { runCatching { it.delete() } }
                mutating = false
            }
        }
    }

    LocalTypesContent(
        snapshot = snapshot,
        loading = !selected || (snapshot == null && loadError == null),
        mutating = mutating,
        loadError = loadError,
        notice = notice,
        selectedCategory = selectedCategory,
        searchQuery = searchQuery,
        linkPreviews = linkPreviews,
        loadingPreviews = loadingPreviews,
        onSelectCategory = { selectedCategory = it },
        onSearchQueryChange = { searchQuery = it },
        onRefresh = { if (!mutating) refreshTick++ },
        onDismissNotice = { notice = null },
        onOpenDefineDialog = {
            editorError = null
            editorRequest = TypeEditorRequest.CreateNew
        },
        onOpenImportDialog = {
            importError = null
            showImportDialog = true
        },
        onOpenLinkDialog = { prefillType ->
            linkError = null
            linkDialogInitialType = prefillType
            showLinkDialog = true
        },
        onInspectType = { inspectedType = it },
        onUnlinkAddress = { link ->
            if (mutating || !isCurrentSession()) return@LocalTypesContent
            mutating = true
            scope.launch {
                try {
                    val session = checkNotNull(TypesCommandSession.capture(sessionToken))
                    TypesRepository.unlinkAddress(session, link.address)
                    if (isCurrentSession()) {
                        notice = TypesNotice(context.getString(R.string.types_unlink_success, link.hexAddress))
                        refreshTick++
                        onTypesChanged()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    if (isCurrentSession()) {
                        notice = TypesNotice(e.localizedMessage ?: context.getString(R.string.common_error), isError = true)
                    }
                } finally {
                    mutating = false
                }
            }
        },
        onToggleLinkPreview = { link ->
            if (linkPreviews.containsKey(link.address)) {
                linkPreviews.remove(link.address)
            } else if (loadingPreviews[link.address] != true && isCurrentSession()) {
                loadingPreviews[link.address] = true
                scope.launch {
                    try {
                        val session = checkNotNull(TypesCommandSession.capture(sessionToken))
                        val rendered = TypesRepository.previewTypeAtAddress(session, link.typeName, link.address)
                        if (isCurrentSession()) {
                            linkPreviews[link.address] = rendered.ifBlank {
                                context.getString(R.string.types_preview_empty)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        if (isCurrentSession()) {
                            linkPreviews[link.address] = e.localizedMessage ?: context.getString(R.string.types_preview_empty)
                        }
                    } finally {
                        loadingPreviews.remove(link.address)
                    }
                }
            }
        },
        onJumpToHex = { addr -> if (isCurrentSession()) onJumpToHex(addr) },
        onJumpToDisasm = { addr -> if (isCurrentSession()) onJumpToDisasm(addr) }
    )

    // Inspector Bottom Sheet
    inspectedType?.let { current ->
        TypeInspectorSheet(
            inspected = current,
            cSource = inspectedCSource,
            cSourceLoading = inspectedCLoading,
            linkedAddresses = snapshot?.linkedAddressesFor(current.name).orEmpty(),
            busy = mutating,
            onDismiss = { inspectedType = null },
            onCopy = ::copyText,
            onEdit = { type, cCode ->
                editorError = null
                editorRequest = TypeEditorRequest.EditExisting(type.name, cCode)
            },
            onRequestLink = { typeName ->
                linkError = null
                linkDialogInitialType = typeName
                showLinkDialog = true
            },
            onRequestDelete = { name -> deleteCandidate = name },
            onJumpToDisasm = { addr -> if (isCurrentSession()) onJumpToDisasm(addr) }
        )
    }

    // C Declaration Editor Dialog (Create / Edit)
    editorRequest?.let { req ->
        val editName = (req as? TypeEditorRequest.EditExisting)?.name
        val initCode = (req as? TypeEditorRequest.EditExisting)?.previousC.orEmpty()
        CDeclarationEditorDialog(
            editingTypeName = editName,
            initialCode = initCode,
            busy = mutating,
            errorMessage = editorError,
            onDismiss = { editorRequest = null; editorError = null },
            onSubmit = { newCode ->
                if (mutating || !isCurrentSession()) return@CDeclarationEditorDialog
                mutating = true
                editorError = null
                scope.launch {
                    try {
                        val session = checkNotNull(TypesCommandSession.capture(sessionToken)) {
                            context.getString(R.string.proj_session_disconnected)
                        }
                        when (req) {
                            is TypeEditorRequest.CreateNew -> {
                                TypesRepository.defineFromC(session, newCode, context.cacheDir)
                                notice = TypesNotice(context.getString(R.string.types_defined_success))
                            }
                            is TypeEditorRequest.EditExisting -> {
                                TypesRepository.updateTypeCDefinition(
                                    session = session,
                                    originalName = req.name,
                                    previousC = req.previousC,
                                    newC = newCode,
                                    cacheDir = context.cacheDir
                                )
                                notice = TypesNotice(context.getString(R.string.types_updated_success, req.name))
                            }
                        }
                        if (isCurrentSession()) {
                            editorRequest = null
                            refreshTick++
                            onTypesChanged()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        editorError = e.localizedMessage ?: context.getString(R.string.common_error)
                    } finally {
                        mutating = false
                    }
                }
            }
        )
    }

    // Import Header Dialog
    if (showImportDialog) {
        ImportHeaderDialog(
            busy = mutating,
            errorMessage = importError,
            onDismiss = { showImportDialog = false; importError = null },
            onBrowseHeaderFile = {
                openHeaderLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
            },
            onImportPath = { path ->
                if (mutating || !isCurrentSession()) return@ImportHeaderDialog
                mutating = true
                importError = null
                scope.launch {
                    try {
                        val session = checkNotNull(TypesCommandSession.capture(sessionToken)) {
                            context.getString(R.string.proj_session_disconnected)
                        }
                        TypesRepository.importHeaderFile(session, path)
                        if (isCurrentSession()) {
                            showImportDialog = false
                            val shortName = path.substringAfterLast('/')
                            notice = TypesNotice(context.getString(R.string.types_import_success, shortName))
                            refreshTick++
                            onTypesChanged()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        importError = e.localizedMessage ?: context.getString(R.string.common_error)
                    } finally {
                        mutating = false
                    }
                }
            }
        )
    }

    // Link Type to Address Dialog
    if (showLinkDialog) {
        LinkTypeDialog(
            initialTypeName = linkDialogInitialType,
            availableTypes = snapshot?.allLinkableTypeNames().orEmpty(),
            cursorAddress = cursorAddress,
            busy = mutating,
            errorMessage = linkError,
            onDismiss = { showLinkDialog = false; linkError = null },
            onConfirm = { typeName, addr ->
                if (mutating || !isCurrentSession()) return@LinkTypeDialog
                mutating = true
                linkError = null
                scope.launch {
                    try {
                        val session = checkNotNull(TypesCommandSession.capture(sessionToken)) {
                            context.getString(R.string.proj_session_disconnected)
                        }
                        TypesRepository.linkTypeToAddress(session, typeName, addr)
                        if (isCurrentSession()) {
                            val hexAddr = "0x${addr.toULong().toString(16).uppercase()}"
                            showLinkDialog = false
                            notice = TypesNotice(context.getString(R.string.types_link_success, typeName, hexAddr))
                            refreshTick++
                            onTypesChanged()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        linkError = e.localizedMessage ?: context.getString(R.string.common_error)
                    } finally {
                        mutating = false
                    }
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteCandidate?.let { nameToDelete ->
        DeleteTypeConfirmDialog(
            typeName = nameToDelete,
            busy = mutating,
            onDismiss = { deleteCandidate = null },
            onConfirm = {
                if (mutating || !isCurrentSession()) return@DeleteTypeConfirmDialog
                mutating = true
                scope.launch {
                    try {
                        val session = checkNotNull(TypesCommandSession.capture(sessionToken))
                        TypesRepository.deleteType(session, nameToDelete)
                        if (isCurrentSession()) {
                            deleteCandidate = null
                            notice = TypesNotice(context.getString(R.string.types_delete_success, nameToDelete))
                            refreshTick++
                            onTypesChanged()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        if (isCurrentSession()) {
                            deleteCandidate = null
                            notice = TypesNotice(e.localizedMessage ?: context.getString(R.string.common_error), isError = true)
                        }
                    } finally {
                        mutating = false
                    }
                }
            }
        )
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx).orEmpty().ifBlank { "header.h" }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "header.h" } ?: "header.h"
}

// ── Pure Hoisted UI Composable (Testable + Screenshot-Ready) ──

@Composable
internal fun LocalTypesContent(
    snapshot: LocalTypesSnapshot?,
    loading: Boolean,
    mutating: Boolean,
    loadError: String?,
    notice: TypesNotice?,
    selectedCategory: TypeCategory,
    searchQuery: String,
    linkPreviews: Map<Long, String> = emptyMap(),
    loadingPreviews: Map<Long, Boolean> = emptyMap(),
    onSelectCategory: (TypeCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismissNotice: () -> Unit,
    onOpenDefineDialog: () -> Unit,
    onOpenImportDialog: () -> Unit,
    onOpenLinkDialog: (prefillType: String?) -> Unit,
    onInspectType: (InspectedType) -> Unit,
    onUnlinkAddress: (TypeLinkInfo) -> Unit,
    onToggleLinkPreview: (TypeLinkInfo) -> Unit,
    onJumpToHex: (Long) -> Unit,
    onJumpToDisasm: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val term = searchQuery.trim()

    val filteredStructs = remember(snapshot?.structs, term) {
        val list = snapshot?.structs.orEmpty()
        if (term.isEmpty()) list else list.filter { s ->
            s.name.contains(term, ignoreCase = true) ||
                s.fields.any { it.name.contains(term, ignoreCase = true) || it.type.contains(term, ignoreCase = true) }
        }
    }
    val filteredEnums = remember(snapshot?.enums, term) {
        val list = snapshot?.enums.orEmpty()
        if (term.isEmpty()) list else list.filter { e ->
            e.name.contains(term, ignoreCase = true) ||
                e.members.any { it.name.contains(term, ignoreCase = true) || it.value.toString().contains(term) }
        }
    }
    val filteredUnions = remember(snapshot?.unions, term) {
        val list = snapshot?.unions.orEmpty()
        if (term.isEmpty()) list else list.filter { u ->
            u.name.contains(term, ignoreCase = true) ||
                u.fields.any { it.name.contains(term, ignoreCase = true) || it.type.contains(term, ignoreCase = true) }
        }
    }
    val filteredTypedefs = remember(snapshot?.typedefs, term) {
        val list = snapshot?.typedefs.orEmpty()
        if (term.isEmpty()) list else list.filter { t ->
            t.name.contains(term, ignoreCase = true) || t.targetType.contains(term, ignoreCase = true)
        }
    }
    val filteredLinks = remember(snapshot?.links, term) {
        val list = snapshot?.links.orEmpty()
        if (term.isEmpty()) list else list.filter { l ->
            l.typeName.contains(term, ignoreCase = true) || l.hexAddress.contains(term, ignoreCase = true)
        }
    }

    val visibleCount = when (selectedCategory) {
        TypeCategory.ALL -> filteredStructs.size + filteredEnums.size + filteredUnions.size + filteredTypedefs.size
        TypeCategory.STRUCT -> filteredStructs.size
        TypeCategory.ENUM -> filteredEnums.size
        TypeCategory.UNION -> filteredUnions.size
        TypeCategory.TYPEDEF -> filteredTypedefs.size
        TypeCategory.LINKS -> filteredLinks.size
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = mutating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Overview & Action Header Card
                item(key = "types_overview") {
                    TypesOverviewCard(
                        snapshot = snapshot,
                        selectedCategory = selectedCategory,
                        busy = mutating || loading,
                        onSelectCategory = onSelectCategory,
                        onRefresh = onRefresh,
                        onOpenDefineDialog = onOpenDefineDialog,
                        onOpenImportDialog = onOpenImportDialog
                    )
                }

                // 2. Notice Banner
                if (notice != null) {
                    item(key = "types_notice") {
                        TypesNoticeBanner(notice = notice, onDismiss = onDismissNotice)
                    }
                }

                // 3. Search Bar (once loaded)
                if (!loading && snapshot != null) {
                    item(key = "types_search") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.types_search_hint),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.common_search),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.common_clear),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }

                // 4. Body states
                when {
                    loading -> item(key = "types_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    loadError != null -> item(key = "types_error") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = loadError,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                                FilledTonalButton(onClick = onRefresh) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.common_retry))
                                }
                            }
                        }
                    }

                    snapshot != null -> {
                        if (selectedCategory == TypeCategory.LINKS) {
                            item(key = "links_cta") {
                                OutlinedButton(
                                    onClick = { onOpenLinkDialog(null) },
                                    enabled = !mutating,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.types_action_link_type))
                                }
                            }
                            if (filteredLinks.isEmpty()) {
                                item(key = "empty_links") {
                                    TypesEmptyStateCard(
                                        title = stringResource(
                                            if (term.isEmpty()) R.string.types_empty_links_title else R.string.search_no_results
                                        ),
                                        body = if (term.isEmpty()) stringResource(R.string.types_empty_links_body) else null,
                                        primaryLabel = if (term.isEmpty()) stringResource(R.string.types_action_link_type) else null,
                                        onPrimaryClick = { onOpenLinkDialog(null) }
                                    )
                                }
                            } else {
                                items(filteredLinks, key = { "link_${it.address}_${it.typeName}" }) { link ->
                                    TypeLinkCard(
                                        link = link,
                                        previewText = linkPreviews[link.address],
                                        previewLoading = loadingPreviews[link.address] == true,
                                        busy = mutating,
                                        onTogglePreview = { onToggleLinkPreview(link) },
                                        onJumpToHex = { onJumpToHex(link.address) },
                                        onJumpToDisasm = { onJumpToDisasm(link.address) },
                                        onUnlink = { onUnlinkAddress(link) },
                                        onOpenType = {
                                            findTypeByName(snapshot, link.typeName)?.let(onInspectType)
                                        }
                                    )
                                }
                            }
                        } else if (visibleCount == 0) {
                            item(key = "empty_types") {
                                if (snapshot.totalTypesCount == 0 && term.isEmpty()) {
                                    TypesEmptyStateCard(
                                        title = stringResource(R.string.types_empty_all_title),
                                        body = stringResource(R.string.types_empty_all_body),
                                        primaryLabel = stringResource(R.string.types_action_define_c),
                                        onPrimaryClick = onOpenDefineDialog,
                                        secondaryLabel = stringResource(R.string.types_action_import_header),
                                        onSecondaryClick = onOpenImportDialog
                                    )
                                } else {
                                    val emptyText = if (term.isNotEmpty()) {
                                        stringResource(R.string.search_no_results)
                                    } else {
                                        val catLabel = when (selectedCategory) {
                                            TypeCategory.STRUCT -> stringResource(R.string.types_cat_structs)
                                            TypeCategory.ENUM -> stringResource(R.string.types_cat_enums)
                                            TypeCategory.UNION -> stringResource(R.string.types_cat_unions)
                                            TypeCategory.TYPEDEF -> stringResource(R.string.types_cat_typedefs)
                                            else -> stringResource(R.string.types_title)
                                        }
                                        stringResource(R.string.types_empty_category, catLabel)
                                    }
                                    TypesEmptyStateCard(title = emptyText, body = null)
                                }
                            }
                        } else {
                            if (selectedCategory == TypeCategory.ALL || selectedCategory == TypeCategory.STRUCT) {
                                items(filteredStructs, key = { "struct_${it.name}" }) { s ->
                                    StructTypeCard(
                                        info = s,
                                        linkedCount = snapshot.linkedAddressesFor(s.name).size,
                                        onClick = { onInspectType(InspectedType.Struct(s)) }
                                    )
                                }
                            }
                            if (selectedCategory == TypeCategory.ALL || selectedCategory == TypeCategory.ENUM) {
                                items(filteredEnums, key = { "enum_${it.name}" }) { e ->
                                    EnumTypeCard(
                                        info = e,
                                        onClick = { onInspectType(InspectedType.Enum(e)) }
                                    )
                                }
                            }
                            if (selectedCategory == TypeCategory.ALL || selectedCategory == TypeCategory.UNION) {
                                items(filteredUnions, key = { "union_${it.name}" }) { u ->
                                    UnionTypeCard(
                                        info = u,
                                        linkedCount = snapshot.linkedAddressesFor(u.name).size,
                                        onClick = { onInspectType(InspectedType.Union(u)) }
                                    )
                                }
                            }
                            if (selectedCategory == TypeCategory.ALL || selectedCategory == TypeCategory.TYPEDEF) {
                                items(filteredTypedefs, key = { "typedef_${it.name}" }) { t ->
                                    TypedefTypeCard(
                                        info = t,
                                        linkedCount = snapshot.linkedAddressesFor(t.name).size,
                                        onClick = { onInspectType(InspectedType.Typedef(t)) }
                                    )
                                }
                            }
                        }

                        item(key = "bottom_pad") { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }

            if (visibleCount > 4) {
                AutoHideScrollbar(
                    listState = listState,
                    totalItems = visibleCount + 3,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

private fun findTypeByName(snapshot: LocalTypesSnapshot, rawName: String): InspectedType? {
    val clean = rawName.removePrefix("struct ").removePrefix("union ").removePrefix("enum ").trim()
    snapshot.structs.firstOrNull { it.name == clean }?.let { return InspectedType.Struct(it) }
    snapshot.unions.firstOrNull { it.name == clean }?.let { return InspectedType.Union(it) }
    snapshot.enums.firstOrNull { it.name == clean }?.let { return InspectedType.Enum(it) }
    snapshot.typedefs.firstOrNull { it.name == clean }?.let { return InspectedType.Typedef(it) }
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypesOverviewCard(
    snapshot: LocalTypesSnapshot?,
    selectedCategory: TypeCategory,
    busy: Boolean,
    onSelectCategory: (TypeCategory) -> Unit,
    onRefresh: () -> Unit,
    onOpenDefineDialog: () -> Unit,
    onOpenImportDialog: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val structColor = typeKindAccent(EditableTypeKind.STRUCT, isDark)
    val enumColor = typeKindAccent(EditableTypeKind.ENUM, isDark)
    val unionColor = typeKindAccent(EditableTypeKind.UNION, isDark)
    val typedefColor = typeKindAccent(EditableTypeKind.TYPEDEF, isDark)
    val linkColor = MaterialTheme.colorScheme.tertiary

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
            // Top bar: Icon + Title/Count + Refresh
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
                        imageVector = Icons.Rounded.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.types_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (snapshot != null) {
                        Text(
                            text = stringResource(
                                R.string.types_subtitle_summary,
                                snapshot.totalTypesCount,
                                snapshot.links.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onRefresh, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.common_refresh),
                        tint = if (!busy) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }

            // Primary Actions: Paste C Declaration (td) & Import .h Header (to)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenDefineDialog,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.types_action_define_c),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = onOpenImportDialog,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.82f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.types_action_import_header),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Interactive Category Stat Chips (all 6 categories visible without hidden horizontal scroll)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryFilterChip(
                    label = stringResource(R.string.types_cat_all),
                    count = snapshot?.totalTypesCount ?: 0,
                    accent = MaterialTheme.colorScheme.primary,
                    selected = selectedCategory == TypeCategory.ALL,
                    onClick = { onSelectCategory(TypeCategory.ALL) }
                )
                CategoryFilterChip(
                    label = stringResource(R.string.types_cat_structs),
                    count = snapshot?.structs?.size ?: 0,
                    accent = structColor,
                    selected = selectedCategory == TypeCategory.STRUCT,
                    onClick = { onSelectCategory(TypeCategory.STRUCT) }
                )
                CategoryFilterChip(
                    label = stringResource(R.string.types_cat_enums),
                    count = snapshot?.enums?.size ?: 0,
                    accent = enumColor,
                    selected = selectedCategory == TypeCategory.ENUM,
                    onClick = { onSelectCategory(TypeCategory.ENUM) }
                )
                CategoryFilterChip(
                    label = stringResource(R.string.types_cat_unions),
                    count = snapshot?.unions?.size ?: 0,
                    accent = unionColor,
                    selected = selectedCategory == TypeCategory.UNION,
                    onClick = { onSelectCategory(TypeCategory.UNION) }
                )
                CategoryFilterChip(
                    label = stringResource(R.string.types_cat_typedefs),
                    count = snapshot?.typedefs?.size ?: 0,
                    accent = typedefColor,
                    selected = selectedCategory == TypeCategory.TYPEDEF,
                    onClick = { onSelectCategory(TypeCategory.TYPEDEF) }
                )
                CategoryFilterChip(
                    label = stringResource(R.string.types_cat_links),
                    count = snapshot?.links?.size ?: 0,
                    accent = linkColor,
                    selected = selectedCategory == TypeCategory.LINKS,
                    onClick = { onSelectCategory(TypeCategory.LINKS) }
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    label: String,
    count: Int,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = if (selected) 1.2.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = "$count",
                    fontFamily = LocalAppFont.current,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

// ── Bespoke Type Cards ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StructTypeCard(
    info: StructTypeInfo,
    linkedCount: Int,
    onClick: () -> Unit
) {
    val accent = typeKindAccent(EditableTypeKind.STRUCT, LocalDarkTheme.current)
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KindPill(stringResource(R.string.types_badge_struct), accent)
                Text(
                    text = info.name,
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (linkedCount > 0) {
                    KindPill(stringResource(R.string.types_badge_linked), MaterialTheme.colorScheme.tertiary)
                }
                if (info.totalSize > 0) {
                    MetaBadge(stringResource(R.string.types_size_bytes, info.totalSize))
                }
            }

            if (info.fields.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    info.fields.take(4).forEach { field ->
                        FieldPreviewChip(
                            prefix = "+0x${field.offset.toString(16)}",
                            label = "${field.displayType} ${field.name}"
                        )
                    }
                    if (info.fields.size > 4) {
                        MetaBadge("+${info.fields.size - 4}")
                    }
                }
            } else if (info.format.isNotBlank()) {
                Text(
                    text = info.format,
                    fontFamily = LocalAppFont.current,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnionTypeCard(
    info: UnionTypeInfo,
    linkedCount: Int,
    onClick: () -> Unit
) {
    val accent = typeKindAccent(EditableTypeKind.UNION, LocalDarkTheme.current)
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KindPill(stringResource(R.string.types_badge_union), accent)
                Text(
                    text = info.name,
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (linkedCount > 0) {
                    KindPill(stringResource(R.string.types_badge_linked), MaterialTheme.colorScheme.tertiary)
                }
                if (info.totalSize > 0) {
                    MetaBadge(stringResource(R.string.types_size_bytes, info.totalSize))
                }
            }

            if (info.fields.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    info.fields.take(4).forEach { field ->
                        FieldPreviewChip(prefix = null, label = "${field.displayType} ${field.name}")
                    }
                    if (info.fields.size > 4) {
                        MetaBadge("+${info.fields.size - 4}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnumTypeCard(
    info: EnumTypeInfo,
    onClick: () -> Unit
) {
    val accent = typeKindAccent(EditableTypeKind.ENUM, LocalDarkTheme.current)
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KindPill(stringResource(R.string.types_badge_enum), accent)
                Text(
                    text = info.name,
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                MetaBadge(stringResource(R.string.types_values_count, info.members.size))
            }

            if (info.members.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    info.members.take(4).forEach { member ->
                        FieldPreviewChip(
                            prefix = "0x${member.value.toULong().toString(16).uppercase()}",
                            label = member.name
                        )
                    }
                    if (info.members.size > 4) {
                        MetaBadge("+${info.members.size - 4}")
                    }
                }
            }
        }
    }
}

@Composable
private fun TypedefTypeCard(
    info: TypedefTypeInfo,
    linkedCount: Int,
    onClick: () -> Unit
) {
    val accent = typeKindAccent(EditableTypeKind.TYPEDEF, LocalDarkTheme.current)
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KindPill(stringResource(R.string.types_badge_typedef), accent)
                Text(
                    text = info.name,
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (linkedCount > 0) {
                    KindPill(stringResource(R.string.types_badge_linked), MaterialTheme.colorScheme.tertiary)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = rememberHighlightedCCode(info.targetType),
                    fontFamily = LocalAppFont.current,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TypeLinkCard(
    link: TypeLinkInfo,
    previewText: String?,
    previewLoading: Boolean,
    busy: Boolean,
    onTogglePreview: () -> Unit,
    onJumpToHex: () -> Unit,
    onJumpToDisasm: () -> Unit,
    onUnlink: () -> Unit,
    onOpenType: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.tertiary
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onJumpToDisasm,
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = link.hexAddress,
                        fontFamily = LocalAppFont.current,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = link.typeName,
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenType)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                IconButton(
                    onClick = onUnlink,
                    enabled = !busy,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LinkOff,
                        contentDescription = stringResource(R.string.types_action_unlink),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Action chips: Disasm / Hex / Memory cast preview (`tp`)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinkActionPill(
                    icon = Icons.Filled.Code,
                    label = stringResource(R.string.types_link_open_disasm),
                    onClick = onJumpToDisasm
                )
                LinkActionPill(
                    icon = Icons.Filled.Memory,
                    label = stringResource(R.string.types_link_open_hex),
                    onClick = onJumpToHex
                )
                Spacer(Modifier.weight(1f))
                LinkActionPill(
                    icon = if (previewText != null) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    label = stringResource(
                        if (previewText != null) R.string.types_action_hide_memory else R.string.types_action_preview_memory
                    ),
                    highlighted = previewText != null,
                    onClick = onTogglePreview
                )
            }

            if (previewLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (previewText != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = previewText,
                            fontFamily = LocalAppFont.current,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KindPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetaBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            fontFamily = LocalAppFont.current,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun FieldPreviewChip(prefix: String?, label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!prefix.isNullOrBlank()) {
                Text(
                    text = prefix,
                    fontFamily = LocalAppFont.current,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = label,
                fontFamily = LocalAppFont.current,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TypesNoticeBanner(
    notice: TypesNotice,
    onDismiss: () -> Unit
) {
    val container = if (notice.isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    val content = if (notice.isError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (notice.isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = notice.message,
                style = MaterialTheme.typography.bodySmall,
                color = content,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TypesEmptyStateCard(
    title: String,
    body: String?,
    primaryLabel: String? = null,
    onPrimaryClick: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (primaryLabel != null && onPrimaryClick != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Button(onClick = onPrimaryClick, shape = RoundedCornerShape(10.dp)) {
                        Text(primaryLabel)
                    }
                    if (secondaryLabel != null && onSecondaryClick != null) {
                        OutlinedButton(onClick = onSecondaryClick, shape = RoundedCornerShape(10.dp)) {
                            Text(secondaryLabel)
                        }
                    }
                }
            }
        }
    }
}

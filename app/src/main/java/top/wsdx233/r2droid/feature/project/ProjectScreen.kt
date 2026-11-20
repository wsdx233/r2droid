package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.tutorial.OnboardingTutorial
import top.wsdx233.r2droid.util.AppCacheCleaner
import top.wsdx233.r2droid.util.R2PipeManager
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64

private enum class SessionAction { Close, Exit, Reconnect }

private data class CacheDiscardRequest(val action: SessionAction, val session: R2PipeManager.R2Session) {
    val requiresActiveSession: Boolean get() = action != SessionAction.Close

    fun isCurrent(): Boolean = R2PipeManager.sessions.value[session.sessionId]?.pipe === session.pipe &&
        (!requiresActiveSession || R2PipeManager.activeSessionPipe === session.pipe)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: ProjectViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val sessions by R2PipeManager.sessions.collectAsState()
    val activeSessionId by R2PipeManager.activeSessionId.collectAsState()
    val tutorialDemoSessionId by OnboardingTutorial.demoSessionId.collectAsState()
    var pendingStartTriggered by remember { mutableStateOf(false) }

    // 在 Activity 接管 configChanges 后，横竖屏切换只会触发重组。
    // 显式关闭抽屉，避免尺寸变化时 ModalNavigationDrawer 误表现为打开状态。
    androidx.compose.runtime.LaunchedEffect(
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp
    ) {
        if (drawerState.isOpen) {
            drawerState.close()
        }
    }

    var selectedUtility by remember { mutableStateOf<UtilityTool?>(null) }
    var toolResultDialog by remember { mutableStateOf<String?>(null) }

    var cacheDiscardRequest by remember { mutableStateOf<CacheDiscardRequest?>(null) }
    var exitSession by remember { mutableStateOf<R2PipeManager.R2Session?>(null) }
    var saveBeforeExit by remember { mutableStateOf(false) }
    var exitProjectName by remember { mutableStateOf("") }
    var pendingExitSave by remember { mutableStateOf<Any?>(null) }
    var exitSaveError by remember { mutableStateOf<String?>(null) }

    fun dismissExit() {
        exitSession = null
        saveBeforeExit = false
        pendingExitSave = null
        exitSaveError = null
    }

    fun perform(request: CacheDiscardRequest, discardWriteCache: Boolean = false) {
        if (!request.isCurrent()) return
        if (request.action == SessionAction.Reconnect) {
            viewModel.reconnectCurrentSession(request.session, discardWriteCache)
            return
        }
        drawerScope.launch {
            if (!R2PipeManager.closeSession(request.session, request.requiresActiveSession, discardWriteCache)) {
                return@launch
            }
            withContext(Dispatchers.IO) { AppCacheCleaner.clearAfterProjectExit(context) }
            if (request.action == SessionAction.Exit || R2PipeManager.sessions.value.isEmpty()) {
                onNavigateBack()
            }
        }
    }

    fun requestAction(action: SessionAction, session: R2PipeManager.R2Session) {
        val request = CacheDiscardRequest(action, session)
        if (!request.isCurrent()) return
        // Project “dirty” tracks analysis, not the cache. Do not probe a busy, disconnected,
        // or ESIL-owned pipe: lack of a readable snapshot is not evidence that it is empty.
        if (session.writeCacheEnabled) cacheDiscardRequest = request else perform(request)
    }

    fun saveAndExit(session: R2PipeManager.R2Session, projectId: String? = null) {
        if (R2PipeManager.activeSessionPipe !== session.pipe || pendingExitSave != null ||
            viewModel.saveProjectState.value is SaveProjectState.Saving) return
        val attempt = Any()
        pendingExitSave = attempt
        exitSaveError = null
        val finished: (Boolean) -> Unit = { success ->
            if (pendingExitSave === attempt) {
                pendingExitSave = null
                if (R2PipeManager.activeSessionPipe === session.pipe) {
                    if (success) {
                        dismissExit()
                        requestAction(SessionAction.Exit, session)
                    } else {
                        exitSaveError = (viewModel.saveProjectState.value as? SaveProjectState.Error)?.message
                    }
                }
            }
        }
        if (projectId != null) viewModel.updateProject(projectId, expectedSession = session, onFinished = finished)
        else viewModel.saveProject(exitProjectName, expectedSession = session, onFinished = finished)
    }

    val activeSession = sessions[activeSessionId]
    androidx.compose.runtime.LaunchedEffect(activeSession?.pipe, sessions) {
        if (exitSession?.let { it.pipe !== activeSession?.pipe } == true) dismissExit()
        if (cacheDiscardRequest?.isCurrent() == false) cacheDiscardRequest = null
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.82f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            drawerScope.launch { drawerState.close() }
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(stringResource(R.string.session_new), style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions.values.toList(), key = { it.sessionId }) { session ->
                            val isSelected = session.sessionId == activeSessionId
                            SessionDrawerItem(
                                title = session.projectInfo.title,
                                subtitle = session.projectInfo.subtitle,
                                selected = isSelected,
                                showFridaBadge = session.isFridaSession,
                                onClick = {
                                    R2PipeManager.switchActiveSession(session.sessionId)
                                    drawerScope.launch { drawerState.close() }
                                },
                                onClose = { requestAction(SessionAction.Close, session) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = stringResource(R.string.session_tools_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false
                    ) {
                        items(UtilityTool.entries) { tool ->
                            UtilityToolCard(
                                tool = tool,
                                onClick = { selectedUtility = tool }
                            )
                        }
                    }
                }
            }
        }
    ) {

            // Re-initialize when active session changes (e.g. switched from sidebar)
            androidx.compose.runtime.LaunchedEffect(activeSessionId) {
                viewModel.onEvent(ProjectEvent.Initialize)
            }

            androidx.compose.runtime.LaunchedEffect(activeSessionId, uiState) {
                if (uiState is ProjectUiState.Success) {
                    OnboardingTutorial.attachDemoSessionIfNeeded(activeSessionId)
                }
            }

    // Handle project restoration or custom command
    androidx.compose.runtime.LaunchedEffect(uiState, R2PipeManager.pendingCustomCommand, R2PipeManager.pendingProjectRestore) {
        if (uiState is ProjectUiState.Analyzing && !pendingStartTriggered) {
            when {
                R2PipeManager.pendingCustomCommand != null -> {
                    viewModel.onEvent(ProjectEvent.StartCustomCommandSession)
                    pendingStartTriggered = true
                }
                R2PipeManager.pendingProjectRestore != null -> {
                    viewModel.onEvent(ProjectEvent.StartRestoreSession)
                    pendingStartTriggered = true
                }
            }
        } else if (uiState !is ProjectUiState.Analyzing) {
            pendingStartTriggered = false
        }
    }

    val canOfferSaveOnBack = uiState is ProjectUiState.Success && activeSession != null &&
        activeSessionId != tutorialDemoSessionId && !activeSession.isFridaSession
    androidx.activity.compose.BackHandler(
        enabled = !drawerState.isOpen && (canOfferSaveOnBack ||
            (activeSession?.writeCacheEnabled == true && uiState !is ProjectUiState.Configuring &&
                uiState !is ProjectUiState.Analyzing))
    ) {
        val session = R2PipeManager.activeSession()
        if (session != null && session.pipe === activeSession?.pipe) {
            when {
                canOfferSaveOnBack && (session.currentProjectId == null || session.isDirtyAfterSave) -> exitSession = session
                session.writeCacheEnabled -> requestAction(SessionAction.Exit, session)
                else -> onNavigateBack()
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = drawerState.isOpen) {
        drawerScope.launch { drawerState.close() }
    }

    exitSession?.takeIf { it.pipe === activeSession?.pipe }?.let { session ->
        val projectId = session.currentProjectId
        val isExistingProject = projectId != null
        val saving = pendingExitSave != null
        AlertDialog(
            onDismissRequest = { dismissExit() },
            title = {
                Text(stringResource(when {
                    saveBeforeExit -> R.string.project_save_title
                    isExistingProject -> R.string.project_exit_update_title
                    else -> R.string.project_exit_title
                }))
            },
            text = {
                Column(modifier = Modifier.focusable(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (saveBeforeExit) {
                        OutlinedTextField(
                            value = exitProjectName,
                            onValueChange = { exitProjectName = it },
                            label = { Text(stringResource(R.string.project_save_name_hint)) },
                            singleLine = true,
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(stringResource(if (isExistingProject) R.string.project_exit_update_message else R.string.project_exit_message))
                    }
                    if (session.writeCacheEnabled) Text(stringResource(R.string.write_cache_save_excludes_patches))
                    exitSaveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        if (R2PipeManager.activeSessionPipe === session.pipe) {
                            if (saveBeforeExit || isExistingProject) {
                                saveAndExit(session, projectId)
                            } else {
                                exitProjectName = session.projectPath?.let { java.io.File(it).name } ?: "Project"
                                saveBeforeExit = true
                            }
                        }
                    }
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(stringResource(if (isExistingProject) R.string.project_exit_update else R.string.project_exit_save))
                }
            },
            dismissButton = {
                if (saveBeforeExit || saving) {
                    TextButton(onClick = { dismissExit() }) {
                        Text(stringResource(R.string.home_delete_cancel))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            dismissExit()
                            if (R2PipeManager.activeSessionPipe === session.pipe) onNavigateBack()
                        }) {
                            Text(stringResource(R.string.project_exit_minimize))
                        }
                        TextButton(onClick = {
                            dismissExit()
                            requestAction(SessionAction.Exit, session)
                        }) {
                            Text(stringResource(R.string.project_exit_discard))
                        }
                    }
                }
            }
        )
    }

            when (val state = uiState) {
                is ProjectUiState.Configuring -> {
                    val tutorialAutoStart = OnboardingTutorial.isAutoStartPendingFor(state.filePath)
                    androidx.compose.runtime.LaunchedEffect(state.filePath) {
                        if (OnboardingTutorial.consumeAutoStartFor(state.filePath)) {
                            viewModel.onEvent(ProjectEvent.StartAnalysisSession("aaa", writable = false, flags = "-e io.cache=true"))
                        }
                    }
                    if (tutorialAutoStart) {
                        AnalysisProgressScreen(logs = logs, isRestoring = false)
                    } else {
                        AnalysisConfigScreen(
                            filePath = state.filePath,
                            onStartAnalysis = { cmd, writable, writeCacheEnabled, flags, saveAsProject, projectName, copyBinary ->
                                viewModel.onEvent(
                                    ProjectEvent.StartAnalysisSession(
                                        cmd = cmd,
                                        writable = writable,
                                        writeCacheEnabled = writeCacheEnabled,
                                        flags = flags,
                                        saveAsProject = saveAsProject,
                                        projectName = projectName,
                                        copyBinary = copyBinary
                                    )
                                )
                            }
                        )
                    }
                }
                is ProjectUiState.Analyzing -> {
                    AnalysisProgressScreen(logs = logs, isRestoring = R2PipeManager.pendingProjectRestore != null)
                }
                else -> {
                    val sid = activeSessionId ?: "legacy-session"
                    key(sid) {
                        ProjectScaffold(
                            sessionId = sid,
                            viewModel = viewModel,
                            onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                            onReconnect = {
                                activeSession?.let { requestAction(SessionAction.Reconnect, it) }
                            }
                        )
                    }
                }
            }
    }

    cacheDiscardRequest?.takeIf { it.isCurrent() }?.let { request ->
        val reconnect = request.action == SessionAction.Reconnect
        WriteCacheDiscardDialog(
            sessionName = request.session.projectInfo.title,
            reconnect = reconnect,
            onDismiss = { cacheDiscardRequest = null },
            onMinimize = if (request.action == SessionAction.Exit) {
                {
                    cacheDiscardRequest = null
                    if (request.isCurrent()) onNavigateBack()
                }
            } else null,
            onDiscard = {
                if (cacheDiscardRequest === request) {
                    cacheDiscardRequest = null
                    perform(request, discardWriteCache = true)
                }
            }
        )
    }

    when (selectedUtility) {
        UtilityTool.VaPa -> VaPaDialog(
            onDismiss = { selectedUtility = null },
            onShowResult = { toolResultDialog = it }
        )
        UtilityTool.Bitwise -> BitwiseDialog(
            onDismiss = { selectedUtility = null },
            onShowResult = { toolResultDialog = it }
        )
        UtilityTool.Assembler -> AssemblerDialog(
            onDismiss = { selectedUtility = null },
            onShowResult = { toolResultDialog = it }
        )
        UtilityTool.Crypto -> CryptoDialog(
            onDismiss = { selectedUtility = null },
            onShowResult = { toolResultDialog = it }
        )
        UtilityTool.ArchRef -> ArchRefDialog { selectedUtility = null }
        null -> Unit
    }

    toolResultDialog?.let { result ->
        ToolResultDialog(result = result, onDismiss = { toolResultDialog = null })
    }
}

@Composable
internal fun WriteCacheDiscardDialog(
    sessionName: String,
    reconnect: Boolean,
    onDismiss: () -> Unit,
    onMinimize: (() -> Unit)? = null,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (reconnect) R.string.write_cache_reconnect_title else R.string.write_cache_close_title)) },
        text = { Text(stringResource(R.string.write_cache_discard_warning, sessionName)) },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(if (reconnect) R.string.write_cache_discard_reconnect else R.string.write_cache_discard_close))
            }
        },
        dismissButton = {
            Row {
                onMinimize?.let {
                    TextButton(onClick = it) { Text(stringResource(R.string.project_exit_minimize)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_cancel)) }
            }
        }
    )
}

@Composable
private fun UtilityToolCard(
    tool: UtilityTool,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景装饰图标 - 放大并半透明
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
            )

            // 前景内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(tool.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun SessionDrawerItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    showFridaBadge: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showFridaBadge) {
                Text(
                    text = "Frida",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.session_close)
            )
        }
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

private enum class UtilityTool(val titleRes: Int, val icon: ImageVector) {
    VaPa(R.string.tool_vapa, Icons.Default.SwapHoriz),
    Bitwise(R.string.tool_bitwise, Icons.Default.Numbers),
    Assembler(R.string.tool_assembler, Icons.Default.Code),
    Crypto(R.string.tool_crypto, Icons.Default.Build),
    ArchRef(R.string.tool_archref, Icons.Default.Memory)
}

@Composable
private fun VaPaDialog(
    onDismiss: () -> Unit,
    onShowResult: (String) -> Unit
) {
    var vaInput by remember { mutableStateOf("") }
    var paInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_vapa)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vaInput,
                    onValueChange = { vaInput = it },
                    label = { Text("VA") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = paInput,
                    onValueChange = { paInput = it },
                    label = { Text("PA") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    if (paInput.isBlank()) return@TextButton
                    scope.launch {
                        vaInput = R2PipeManager.execute("?p $paInput").getOrDefault("error").trim()
                    }
                }) { Text("PA→VA") }
                TextButton(onClick = {
                    if (vaInput.isBlank()) return@TextButton
                    scope.launch {
                        paInput = R2PipeManager.execute("?P $vaInput").getOrDefault("error").trim()
                    }
                }) { Text("VA→PA") }
                TextButton(onClick = { onShowResult("VA=$vaInput\nPA=$paInput") }) {
                    Text(stringResource(R.string.menu_copy))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_cancel)) } }
    )
}

@Composable
private fun BitwiseDialog(
    onDismiss: () -> Unit,
    onShowResult: (String) -> Unit
) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    fun parse(v: String): Long = v.removePrefix("0x").toLongOrNull(16) ?: v.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_bitwise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = a, onValueChange = { a = it }, label = { Text("A") })
                OutlinedTextField(value = b, onValueChange = { b = it }, label = { Text("B") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onShowResult("0x${(parse(a) and parse(b)).toString(16)}") }) { Text("AND") }
                    TextButton(onClick = { onShowResult("0x${(parse(a) or parse(b)).toString(16)}") }) { Text("OR") }
                    TextButton(onClick = { onShowResult("0x${(parse(a) xor parse(b)).toString(16)}") }) { Text("XOR") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_cancel)) } }
    )
}

@Composable
private fun AssemblerDialog(
    onDismiss: () -> Unit,
    onShowResult: (String) -> Unit
) {
    var asm by remember { mutableStateOf("") }
    var hex by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_assembler)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = asm, onValueChange = { asm = it }, label = { Text("ASM") })
                OutlinedTextField(value = hex, onValueChange = { hex = it }, label = { Text("HEX") })
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    scope.launch { onShowResult(R2PipeManager.execute("pa $asm").getOrDefault("error")) }
                }) { Text("ASM→HEX") }
                TextButton(onClick = {
                    scope.launch { onShowResult(R2PipeManager.execute("pad $hex").getOrDefault("error")) }
                }) { Text("HEX→ASM") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_cancel)) } }
    )
}

@Composable
private fun CryptoDialog(
    onDismiss: () -> Unit,
    onShowResult: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    fun md(name: String, text: String): String {
        val bytes = MessageDigest.getInstance(name).digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_crypto)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Input") })
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { onShowResult(Base64.getEncoder().encodeToString(input.toByteArray())) }) { Text("B64") }
                    TextButton(onClick = { onShowResult(runCatching { String(Base64.getDecoder().decode(input)) }.getOrDefault("error")) }) { Text("B64-DEC") }
                    TextButton(onClick = { onShowResult(URLEncoder.encode(input, "UTF-8")) }) { Text("URL") }
                    TextButton(onClick = { onShowResult(URLDecoder.decode(input, "UTF-8")) }) { Text("URL-DEC") }
                    TextButton(onClick = { onShowResult("MD5 ${md("MD5", input)}\nSHA256 ${md("SHA-256", input)}") }) { Text("HASH") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_cancel)) } }
    )
}

@Composable
private fun ToolResultDialog(
    result: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_tools_title)) },
        text = {
            OutlinedTextField(
                value = result,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                clipboardManager.setText(AnnotatedString(result))
            }) {
                Text(stringResource(R.string.menu_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_delete_cancel))
            }
        }
    )
}

@Composable
private fun ArchRefDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_archref)) },
        text = {
            Text(
                text = "ARM64 ABI:\nX0-X7: 参数寄存器\nX8: 间接结果地址\nX9-X15: 临时寄存器\nX19-X28: 被调用者保存\nSP: 栈指针\nLR(X30): 返回地址",
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_cancel)) } }
    )
}

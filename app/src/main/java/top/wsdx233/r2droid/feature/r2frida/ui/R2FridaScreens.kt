package top.wsdx233.r2droid.feature.r2frida.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rosemoe.sora.widget.CodeEditor
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.SoraCodeEditor
import top.wsdx233.r2droid.feature.r2frida.data.*
import top.wsdx233.r2droid.util.LogEntry
import top.wsdx233.r2droid.util.LogType

@Composable
fun FridaOverviewScreen(info: FridaInfo?) {
    if (info == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.r2frida_overview_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        OverviewInfoCard(
            icon = Icons.Default.Memory,
            title = "Runtime",
            items = listOf(
                "Arch" to "${info.arch} (${info.bits}bit)",
                "OS" to info.os,
                "Runtime" to info.runtime,
                "Page Size" to "${info.pageSize}",
                "Pointer Size" to "${info.pointerSize}"
            )
        )
        OverviewInfoCard(
            icon = Icons.Default.Apps,
            title = "Process",
            items = listOf(
                "PID" to "${info.pid}",
                "UID" to "${info.uid}",
                "Module" to info.moduleName,
                "Base" to info.moduleBase
            )
        )
        if (info.packageName.isNotEmpty()) {
            OverviewInfoCard(
                icon = Icons.Default.Android,
                title = "Android",
                items = listOf(
                    "Package" to info.packageName,
                    "Data Dir" to info.dataDir,
                    "CWD" to info.cwd,
                    "Code Path" to info.codePath
                )
            )
        }
        OverviewInfoCard(
            icon = Icons.Default.Extension,
            title = "Features",
            items = listOf(
                "Java" to if (info.java) "Yes" else "No",
                "ObjC" to if (info.objc) "Yes" else "No",
                "Swift" to if (info.swift) "Yes" else "No"
            )
        )
    }
}

@Composable
private fun OverviewInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    items: List<Pair<String, String>>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            items.forEach { (key, value) ->
                if (value.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(key, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer {
                            Text(value, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FridaScriptScreen(
    logs: List<LogEntry>,
    running: Boolean,
    scriptContent: String,
    currentScriptName: String?,
    scriptFiles: List<String>,
    onRun: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onNewScript: () -> Unit,
    onSaveScript: (String, String) -> Unit,
    onOpenScript: (String) -> Unit,
    onDeleteScript: (String) -> Unit,
    onRefreshFiles: () -> Unit
) {
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var logPanelVisible by remember { mutableStateOf(false) }
    var filePanelVisible by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Restore content when editor is ready
    var editorInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(editorRef, scriptContent) {
        val editor = editorRef ?: return@LaunchedEffect
        if (!editorInitialized && scriptContent.isNotEmpty()) {
            editor.setText(scriptContent)
            editorInitialized = true
        }
    }

    // Auto-open log panel when script starts running
    LaunchedEffect(running) {
        if (running) logPanelVisible = true
    }

    // Refresh file list when panel becomes visible
    LaunchedEffect(filePanelVisible) {
        if (filePanelVisible) onRefreshFiles()
    }

    // Save editor content when leaving composition (tab switch)
    DisposableEffect(Unit) {
        onDispose {
            editorRef?.let { onContentChange(it.text.toString()) }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // File management toolbar
        FridaScriptToolbar(
            currentName = currentScriptName,
            filePanelVisible = filePanelVisible,
            onToggleFilePanel = { filePanelVisible = !filePanelVisible },
            onNew = {
                onNewScript()
                editorRef?.setText("")
                editorInitialized = true
            },
            onSave = {
                val editor = editorRef ?: return@FridaScriptToolbar
                val content = editor.text.toString()
                onContentChange(content)
                if (currentScriptName != null) {
                    onSaveScript(currentScriptName, content)
                } else {
                    showSaveDialog = true
                }
            },
            onOpen = { filePanelVisible = !filePanelVisible }
        )

        // File list panel
        AnimatedVisibility(visible = filePanelVisible) {
            FridaFileListPanel(
                files = scriptFiles,
                currentName = currentScriptName,
                onOpen = { name ->
                    onOpenScript(name)
                    editorInitialized = false
                    filePanelVisible = false
                },
                onDelete = onDeleteScript
            )
        }

        // Editor + overlays
        Box(Modifier.weight(1f)) {
            SoraCodeEditor(
                modifier = Modifier.fillMaxSize(),
                scopeName = "source.js",
                onEditorReady = { editorRef = it }
            )

            // Floating action buttons (top-right)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Log toggle button
                FloatingActionButton(
                    onClick = { logPanelVisible = !logPanelVisible },
                    modifier = Modifier.size(40.dp),
                    containerColor = if (logPanelVisible)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    BadgedBox(badge = {
                        if (logs.isNotEmpty()) {
                            Badge {
                                Text(if (logs.size > 99) "99+" else "${logs.size}")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Terminal, null, modifier = Modifier.size(20.dp))
                    }
                }
                // Run button
                FloatingActionButton(
                    onClick = {
                        editorRef?.let {
                            val text = it.text.toString()
                            onContentChange(text)
                            onRun(text)
                        }
                    },
                    modifier = Modifier.size(40.dp),
                    containerColor = if (running)
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.primary
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Log panel (bottom)
            androidx.compose.animation.AnimatedVisibility(
                visible = logPanelVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                FridaLogPanel(logs, onClose = { logPanelVisible = false })
            }
        }

        // Extra keys bar at bottom
        FridaExtraKeysBar(editorRef)
    }

    // Save dialog
    if (showSaveDialog) {
        FridaSaveDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                val content = editorRef?.text?.toString() ?: ""
                onSaveScript(name, content)
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun FridaLogPanel(logs: List<LogEntry>, onClose: () -> Unit) {
    val bg = colorResource(R.color.command_output_background)
    val fg = colorResource(R.color.command_output_text)
    val ph = colorResource(R.color.command_output_placeholder)
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom on new logs
    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.4f),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        shadowElevation = 8.dp,
        color = bg
    ) {
        Column {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.r2frida_script_output),
                    style = MaterialTheme.typography.labelMedium,
                    color = ph, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = ph)
                }
            }
            HorizontalDivider(color = ph.copy(alpha = 0.3f))
            // Log content
            Box(
                Modifier.fillMaxSize().padding(8.dp).verticalScroll(scrollState)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        stringResource(R.string.r2frida_script_no_output),
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ph
                    )
                } else {
                    SelectionContainer {
                        Column {
                            logs.forEach { entry ->
                                val color = when (entry.type) {
                                    LogType.ERROR -> MaterialTheme.colorScheme.error
                                    LogType.WARNING -> ph
                                    else -> fg
                                }
                                Text(
                                    entry.message, fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp, color = color
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
private fun FridaScriptToolbar(
    currentName: String?,
    filePanelVisible: Boolean,
    onToggleFilePanel: () -> Unit,
    onNew: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleFilePanel, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (filePanelVisible) Icons.Default.FolderOpen
                    else Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                currentName ?: stringResource(R.string.frida_script_untitled),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            IconButton(onClick = onNew, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.NoteAdd, null, Modifier.size(20.dp))
            }
            IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(20.dp))
            }
            IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Save, null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FridaFileListPanel(
    files: List<String>,
    currentName: String?,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
        ) {
            HorizontalDivider()
            if (files.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.frida_script_no_files),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(files) { name ->
                        val isCurrent = name == currentName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(name) }
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Javascript,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onDelete(name) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun FridaExtraKeysBar(editor: CodeEditor?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Color(0xFF2D2D2D))
    ) {
        FlatKeyCell("←", Modifier.weight(1f)) {
            editor?.let {
                val c = it.cursor
                if (c.leftColumn > 0) it.setSelection(c.leftLine, c.leftColumn - 1)
                else if (c.leftLine > 0) it.setSelection(c.leftLine - 1, it.text.getColumnCount(c.leftLine - 1))
            }
        }
        FlatKeyCell("→", Modifier.weight(1f)) {
            editor?.let {
                val c = it.cursor
                val len = it.text.getColumnCount(c.leftLine)
                if (c.leftColumn < len) it.setSelection(c.leftLine, c.leftColumn + 1)
                else if (c.leftLine < it.text.lineCount - 1) it.setSelection(c.leftLine + 1, 0)
            }
        }
        FlatKeyCell("fun", Modifier.weight(1f)) { editor?.insertText("function ", 9) }
        FlatKeyCell("(", Modifier.weight(1f)) { editor?.insertText("()", 1) }
        FlatKeyCell("[", Modifier.weight(1f)) { editor?.insertText("[]", 1) }
        FlatKeyCell("{", Modifier.weight(1f)) { editor?.insertText("{}", 1) }
        FlatKeyCell("\"", Modifier.weight(1f)) { editor?.insertText("\"\"", 1) }
        FlatKeyCell("=", Modifier.weight(1f)) { editor?.insertText("=", 1) }
        FlatKeyCell("+", Modifier.weight(1f)) { editor?.insertText("+", 1) }
        FlatKeyCell("-", Modifier.weight(1f)) { editor?.insertText("-", 1) }
        FlatKeyCell(".", Modifier.weight(1f)) { editor?.insertText(".", 1) }
    }
}

@Composable
private fun FlatKeyCell(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (isPressed) Color(0xFF5C6BC0) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun FridaSaveDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Save, contentDescription = null) },
        title = { Text(stringResource(R.string.frida_script_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.frida_script_file_name)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.frida_script_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

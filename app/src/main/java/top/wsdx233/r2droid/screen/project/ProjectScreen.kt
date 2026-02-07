package top.wsdx233.r2droid.screen.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerInput
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.view.R
import top.wsdx233.r2droid.util.R2PipeManager

enum class MainCategory(val title: String, val icon: ImageVector) {
    List("列表", Icons.Filled.List),
    Detail("详情", Icons.Filled.Info),
    Project("项目", Icons.Filled.Build)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    viewModel: ProjectViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val saveState by viewModel.saveProjectState.collectAsState()
    val context = LocalContext.current
    
    // State for exit confirmation dialog
    var showExitDialog by remember { mutableStateOf(false) }
    var showSaveBeforeExitDialog by remember { mutableStateOf(false) }
    var exitProjectName by remember { mutableStateOf("") }
    
    // Initialize saved project repository
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.initializeSavedProjectRepository(context)
    }
    
    // Check intent on entry (handle new file selection)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.initialize()
    }
    
    // Handle project restoration - trigger when Analyzing state is set from a restore
    androidx.compose.runtime.LaunchedEffect(uiState) {
        if (uiState is ProjectUiState.Analyzing && R2PipeManager.pendingRestoreFlags != null) {
            viewModel.startRestoreSession(context)
        }
    }
    
    // Handle back press with save confirmation
    androidx.activity.compose.BackHandler(
        enabled = uiState is ProjectUiState.Success && R2PipeManager.currentProjectId == null
    ) {
        // Show save confirmation dialog if not already saved
        showExitDialog = true
    }
    
    // Handle save completion when exiting
    androidx.compose.runtime.LaunchedEffect(saveState) {
        if (showSaveBeforeExitDialog && saveState is SaveProjectState.Success) {
            showSaveBeforeExitDialog = false
            onNavigateBack()
        }
    }
    
    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(top.wsdx233.r2droid.R.string.project_exit_title)) },
            text = { Text(stringResource(top.wsdx233.r2droid.R.string.project_exit_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        showSaveBeforeExitDialog = true
                        // Set default name from file
                        exitProjectName = R2PipeManager.currentFilePath?.let { 
                            java.io.File(it).name 
                        } ?: "Project"
                    }
                ) {
                    Text(stringResource(top.wsdx233.r2droid.R.string.project_exit_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(top.wsdx233.r2droid.R.string.project_exit_discard))
                }
            }
        )
    }
    
    // Save before exit dialog
    if (showSaveBeforeExitDialog) {
        AlertDialog(
            onDismissRequest = { showSaveBeforeExitDialog = false },
            title = { Text(stringResource(top.wsdx233.r2droid.R.string.project_save_title)) },
            text = {
                OutlinedTextField(
                    value = exitProjectName,
                    onValueChange = { exitProjectName = it },
                    label = { Text(stringResource(top.wsdx233.r2droid.R.string.project_save_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveProject(exitProjectName)
                        // Dialog will be closed by LaunchedEffect when save succeeds
                    },
                    enabled = saveState !is SaveProjectState.Saving
                ) {
                    if (saveState is SaveProjectState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(stringResource(top.wsdx233.r2droid.R.string.project_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showSaveBeforeExitDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(top.wsdx233.r2droid.R.string.home_delete_cancel))
                }
            }
        )
    }
    
    // Config state check
    if (uiState is ProjectUiState.Configuring) {
        val state = uiState as ProjectUiState.Configuring
        AnalysisConfigScreen(
            filePath = state.filePath,
            onStartAnalysis = { cmd, writable, flags ->
                viewModel.startAnalysisSession(context, cmd, writable, flags)
            }
        )
        return
    }

    // Analyzing state check
    if (uiState is ProjectUiState.Analyzing) {
        AnalysisProgressScreen(logs = logs)
        return
    }

    // State for navigation
    var selectedCategory by remember { mutableStateOf(MainCategory.List) }
    var selectedListTabIndex by remember { mutableIntStateOf(0) }
    var selectedDetailTabIndex by remember { mutableIntStateOf(1) } // Default to Disassembly (index 1)
    var selectedProjectTabIndex by remember { mutableIntStateOf(0) } // Default to Settings (index 0)
    var showJumpDialog by remember { mutableStateOf(false) }

    val listTabs = listOf("Overview", "Sections", "Symbols", "Imports", "Relocs", "Strings", "Functions")
    val detailTabs = listOf("Hex", "Disassembly", "Decompile")
    val projectTabs = listOf("Settings", "Terminal", "Command", "Logs")
    
    // Xrefs State
    val xrefsState by viewModel.xrefsState.collectAsState()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    // Define shared actions for list items
    val listItemActions = ListItemActions(
        onCopy = { text -> 
            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
        },
        onJumpToHex = { addr ->
            selectedCategory = MainCategory.Detail
            selectedDetailTabIndex = 0
            viewModel.jumpToAddress(addr)
        },
        onJumpToDisasm = { addr ->
            selectedCategory = MainCategory.Detail
            selectedDetailTabIndex = 1
            viewModel.jumpToAddress(addr)
        },
        onShowXrefs = { addr ->
            viewModel.fetchXrefs(addr)
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                            Text(stringResource(top.wsdx233.r2droid.R.string.app_name))

                        },
                actions = {
                    if (selectedCategory == MainCategory.Detail) {
                         // Show back navigation button (undo)
                         val canGoBack by viewModel.canGoBack.collectAsState()
                         androidx.compose.material3.IconButton(
                             onClick = { viewModel.navigateBack() },
                             enabled = canGoBack
                         ) {
                             Icon(
                                 Icons.AutoMirrored.Filled.ArrowBack,
                                 contentDescription = "Go Back",
                                 tint = if (canGoBack) MaterialTheme.colorScheme.onSurface 
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                             )
                         }
                         // Show scroll-to-selection button for Hex and Disassembly tabs
                         if (selectedDetailTabIndex == 0 || selectedDetailTabIndex == 1 || selectedDetailTabIndex == 2) {
                             androidx.compose.material3.IconButton(onClick = { viewModel.requestScrollToSelection() }) {
                                 Icon(Icons.Filled.MyLocation, contentDescription = "Scroll to Selection")
                             }
                         }
                         androidx.compose.material3.IconButton(onClick = { showJumpDialog = true }) {
                             Icon(Icons.AutoMirrored.Filled.MenuOpen, contentDescription = "Jump")
                         }
                    }
                }
            )
            
            if (showJumpDialog) {
                val currentAddress = (uiState as? ProjectUiState.Success)?.cursorAddress ?: 0L
                JumpDialog(
                    initialAddress = "0x%X".format(currentAddress),
                    onDismiss = { showJumpDialog = false },
                    onJump = { addr ->
                        viewModel.jumpToAddress(addr)
                        showJumpDialog = false
                    }
                )
            }
            
            // Xrefs Dialog
            if (xrefsState.visible) {
                XrefsDialog(
                    xrefsData = xrefsState.data,
                    targetAddress = xrefsState.targetAddress,
                    onDismiss = { viewModel.dismissXrefs() },
                    onJump = { addr ->
                        selectedCategory = MainCategory.Detail
                        selectedDetailTabIndex = 1 // Default to disasm for xref jumps
                        viewModel.jumpToAddress(addr)
                        viewModel.dismissXrefs()
                    }
                )
            }
        },
        bottomBar = {
            // Two-layer Bottom Navigation
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 8.dp
            ) {
                Column {
                    // Level 2: Sub-tabs (Text only)
                    when (selectedCategory) {
                        MainCategory.List -> {
                             ScrollableTabRow(
                                 selectedTabIndex = selectedListTabIndex,
                                 edgePadding = 0.dp,
                                 containerColor = MaterialTheme.colorScheme.surface,
                                 contentColor = MaterialTheme.colorScheme.primary,
                                 indicator = { tabPositions ->
                                     TabRowDefaults.SecondaryIndicator(
                                         modifier = Modifier.tabIndicatorOffset(tabPositions[selectedListTabIndex]),
                                         color = MaterialTheme.colorScheme.primary
                                     )
                                 }
                            ) {
                                listTabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedListTabIndex == index,
                                        onClick = { selectedListTabIndex = index },
                                        text = { Text(text = title) }
                                    )
                                }
                            }
                        }
                        MainCategory.Detail -> {
                             TabRow(
                                selectedTabIndex = selectedDetailTabIndex,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                indicator = { tabPositions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedDetailTabIndex]),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            ) {
                                detailTabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedDetailTabIndex == index,
                                        onClick = { selectedDetailTabIndex = index },
                                        text = { Text(text = title) }
                                    )
                                }
                            }
                        }
                        MainCategory.Project -> {
                            TabRow(
                                selectedTabIndex = selectedProjectTabIndex,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                indicator = { tabPositions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedProjectTabIndex]),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            ) {
                                projectTabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedProjectTabIndex == index,
                                        onClick = { selectedProjectTabIndex = index },
                                        text = { Text(text = title) }
                                    )
                                }
                            }
                        }
                    }

                    // Level 1: Main Category (Icon + Title)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        MainCategory.values().forEach { category ->
                            NavigationBarItem(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                icon = { Icon(category.icon, contentDescription = category.title) },
                                label = { Text(category.title) }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = uiState) {
                is ProjectUiState.Idle -> {
                    Text("Idle", Modifier.align(Alignment.Center))
                }
                is ProjectUiState.Loading -> {
                     CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ProjectUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.retryLoadAll() }) {
                            Text("Retry")
                        }
                    }
                }
                is ProjectUiState.Success -> {
                    // Logic to display content
                    when (selectedCategory) {
                        MainCategory.List -> {
                            // Trigger data load based on selectedListTabIndex
                            // Tabs: Overview, Sections, Symbols, Imports, Relocs, Strings, Functions
                            androidx.compose.runtime.LaunchedEffect(selectedListTabIndex) {
                                when (selectedListTabIndex) {
                                    1 -> viewModel.loadSections()
                                    2 -> viewModel.loadSymbols()
                                    3 -> viewModel.loadImports()
                                    4 -> viewModel.loadRelocations()
                                    5 -> viewModel.loadStrings()
                                    6 -> viewModel.loadFunctions()
                                }
                            }

                            when (selectedListTabIndex) {
                                0 -> state.binInfo?.let { OverviewCard(it) } ?: Text("No Data", Modifier.align(Alignment.Center))
                                1 -> if (state.sections == null) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) else SectionList(state.sections, listItemActions)
                                2 -> if (state.symbols == null) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) else SymbolList(state.symbols, listItemActions)
                                3 -> if (state.imports == null) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) else ImportList(state.imports, listItemActions)
                                4 -> if (state.relocations == null) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) else RelocationList(state.relocations, listItemActions)
                                5 -> if (state.strings == null) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) else StringList(state.strings, listItemActions)
                                6 -> if (state.functions == null) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) else FunctionList(state.functions, listItemActions)
                            }
                        }
                        MainCategory.Detail -> {
                            // Tabs: Hex, Disassembly, Decompile
                             androidx.compose.runtime.LaunchedEffect(selectedDetailTabIndex) {
                                when (selectedDetailTabIndex) {
                                    0 -> viewModel.loadHex()
                                    1 -> viewModel.loadDisassembly()
                                    2 -> viewModel.loadDecompilation()
                                }
                            }
                            
                            when(selectedDetailTabIndex) {
                                0 -> if (!state.hexReady) {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                } else {
                                    HexViewer(
                                        viewModel = viewModel,
                                        cursorAddress = state.cursorAddress,
                                        onByteClick = { addr -> viewModel.updateCursor(addr) }
                                    )
                                }
                                1 -> if (!state.disasmReady) {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                } else {
                                    DisassemblyViewer(
                                        viewModel = viewModel,
                                        cursorAddress = state.cursorAddress,
                                        onInstructionClick = { addr -> viewModel.updateCursor(addr) }
                                    )
                                }
                                2 -> if (state.decompilation == null) {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                } else {
                                    DecompilationViewer(
                                        viewModel = viewModel,
                                        data = state.decompilation,
                                        cursorAddress = state.cursorAddress,
                                        onAddressClick = { addr -> viewModel.updateCursor(addr) }
                                    )
                                }
                            }
                        }
                        MainCategory.Project -> {
                             // Project Category
                            when (selectedProjectTabIndex) {
                                0 -> ProjectSettingsScreen(viewModel)
                                1 -> TerminalScreen()
                                2 -> CommandScreen()
                                3 -> LogList(logs, onClearLogs = { viewModel.clearLogs() })
                            }
                        }
                    }
                }
                else -> {} // Configuring handled above
            }
        }
    }
}

@Composable
fun ProjectSettingsScreen(viewModel: ProjectViewModel) {
    val context = LocalContext.current
    val saveState by viewModel.saveProjectState.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    
    // Initialize repository
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.initializeSavedProjectRepository(context)
    }
    
    // Handle save state changes
    androidx.compose.runtime.LaunchedEffect(saveState) {
        when (saveState) {
            is SaveProjectState.Success -> {
                android.widget.Toast.makeText(
                    context, 
                    (saveState as SaveProjectState.Success).message, 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.resetSaveState()
            }
            is SaveProjectState.Error -> {
                android.widget.Toast.makeText(
                    context, 
                    (saveState as SaveProjectState.Error).message, 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.resetSaveState()
            }
            else -> {}
        }
    }
    
    // Save dialog
    if (showSaveDialog) {
        SaveProjectDialog(
            existingProjectId = viewModel.getCurrentProjectId(),
            onDismiss = { showSaveDialog = false },
            onSaveNew = { name ->
                viewModel.saveProject(name)
                showSaveDialog = false
            },
            onUpdate = { projectId ->
                viewModel.updateProject(projectId)
                showSaveDialog = false
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // File Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Current File",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = R2PipeManager.currentFilePath ?: "No file loaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                if (R2PipeManager.currentProjectId != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Project ID: ${R2PipeManager.currentProjectId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
        
        // Save Project Section
        Text(
            text = stringResource(top.wsdx233.r2droid.R.string.project_save),
            style = MaterialTheme.typography.titleMedium
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = saveState !is SaveProjectState.Saving) { 
                    showSaveDialog = true 
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (saveState is SaveProjectState.Saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (R2PipeManager.currentProjectId != null) 
                            "Update Project" 
                        else 
                            stringResource(top.wsdx233.r2droid.R.string.project_save_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (R2PipeManager.currentProjectId != null)
                            "Save changes to existing project"
                        else
                            "Save analysis results for later",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Startup Flags Section (for saved projects)
        if (R2PipeManager.currentProjectId != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(top.wsdx233.r2droid.R.string.project_startup_flags),
                style = MaterialTheme.typography.titleMedium
            )
            
            var startupFlags by remember { mutableStateOf("") }
            
            OutlinedTextField(
                value = startupFlags,
                onValueChange = { startupFlags = it },
                label = { Text(stringResource(top.wsdx233.r2droid.R.string.project_startup_flags_hint)) },
                placeholder = { Text("-w -n") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Text(
                text = stringResource(top.wsdx233.r2droid.R.string.project_startup_flags_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Session Info
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Session Info",
            style = MaterialTheme.typography.titleMedium
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Status", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (R2PipeManager.isConnected) "Connected" else "Disconnected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (R2PipeManager.isConnected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Saved", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (R2PipeManager.currentProjectId != null) "Yes" else "No",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (R2PipeManager.currentProjectId != null) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SaveProjectDialog(
    existingProjectId: String?,
    onDismiss: () -> Unit,
    onSaveNew: (name: String) -> Unit,
    onUpdate: (projectId: String) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var saveAsNew by remember { mutableStateOf(existingProjectId == null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(top.wsdx233.r2droid.R.string.project_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (existingProjectId != null) {
                    // Options for existing project
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveAsNew = false }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !saveAsNew,
                            onClick = { saveAsNew = false }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update existing project")
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveAsNew = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = saveAsNew,
                            onClick = { saveAsNew = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save as new project")
                    }
                }
                
                if (saveAsNew || existingProjectId == null) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text(stringResource(top.wsdx233.r2droid.R.string.project_save_name_hint)) },
                        placeholder = { 
                            val fileName = R2PipeManager.currentFilePath?.let { 
                                java.io.File(it).name 
                            } ?: "Project"
                            Text(fileName)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (saveAsNew || existingProjectId == null) {
                        val name = projectName.ifBlank {
                            R2PipeManager.currentFilePath?.let { java.io.File(it).name } ?: "Project"
                        }
                        onSaveNew(name)
                    } else {
                        onUpdate(existingProjectId)
                    }
                }
            ) {
                Text(stringResource(top.wsdx233.r2droid.R.string.project_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(top.wsdx233.r2droid.R.string.home_delete_cancel))
            }
        }
    )
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisConfigScreen(
    filePath: String,
    onStartAnalysis: (cmd: String, writable: Boolean, flags: String) -> Unit
) {
    var selectedLevel by remember { mutableStateOf("aaa") }
    var customCmd by remember { mutableStateOf("") }
    var isWritable by remember { mutableStateOf(false) }
    var customFlags by remember { mutableStateOf("") }
    
    val levels = listOf(
        "None" to "none",
        "Auto (aaa)" to "aaa",
        "Experimental (aaaa)" to "aaaa",
        "Custom" to "custom"
    )

    Scaffold(
        topBar = {
             CenterAlignedTopAppBar(title = { Text("Configure Analysis") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // File Info
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Target File", style = MaterialTheme.typography.labelMedium)
                    Text(filePath, style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            // Analysis Level
            Text("Analysis Level", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                levels.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLevel = value }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (selectedLevel == value),
                            onClick = { selectedLevel = value }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
            }
            
            if (selectedLevel == "custom") {
                OutlinedTextField(
                    value = customCmd,
                    onValueChange = { customCmd = it },
                    label = { Text("Custom Analysis Command") },
                    placeholder = { Text("e.g. aa") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            HorizontalDivider()
            
            // Startup Options
            Text("Startup Options", style = MaterialTheme.typography.titleMedium)
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isWritable = !isWritable }) {
                Checkbox(checked = isWritable, onCheckedChange = { isWritable = it })
                Text("Open in Writable Mode (-w)")
            }
            
            OutlinedTextField(
                value = customFlags,
                onValueChange = { customFlags = it },
                label = { Text("Additional Startup Flags") },
                placeholder = { Text("-n -h") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    val finalCmd = if (selectedLevel == "custom") customCmd else selectedLevel
                    onStartAnalysis(finalCmd, isWritable, customFlags)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start Analysis")
            }
        }
    }
}

@Composable
fun LogList(logs: List<top.wsdx233.r2droid.util.LogEntry>, onClearLogs: () -> Unit = {}) {
    val listState = rememberLazyListState()

    // Auto scroll to bottom
    androidx.compose.runtime.LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.ui.graphics.Color(0xFF1E1E1E) // Dark background for logs
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
            androidx.compose.material3.FilledTonalIconButton(
                onClick = onClearLogs,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Logs",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}


@Composable
fun LogItem(entry: top.wsdx233.r2droid.util.LogEntry) {
    val color = when (entry.type) {
        top.wsdx233.r2droid.util.LogType.COMMAND -> MaterialTheme.colorScheme.primary
        top.wsdx233.r2droid.util.LogType.OUTPUT -> androidx.compose.ui.graphics.Color(0xFFE0E0E0)
        top.wsdx233.r2droid.util.LogType.INFO -> androidx.compose.ui.graphics.Color.Gray
        top.wsdx233.r2droid.util.LogType.WARNING -> androidx.compose.ui.graphics.Color(0xFFFFA000) // Amber
        top.wsdx233.r2droid.util.LogType.ERROR -> MaterialTheme.colorScheme.error
    }

    val prefix = when (entry.type) {
        top.wsdx233.r2droid.util.LogType.COMMAND -> "$ "
        top.wsdx233.r2droid.util.LogType.WARNING -> "[WARN] "
        top.wsdx233.r2droid.util.LogType.ERROR -> "[ERR] "
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
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    )
}

@Composable
fun JumpDialog(
    initialAddress: String,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit
) {
    var text by remember { mutableStateOf(initialAddress) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to Address") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { 
                        text = it 
                        error = null
                    },
                    label = { Text("Address (Hex)") },
                    placeholder = { Text("e.g. 0x401000") },
                    isError = error != null,
                    singleLine = true
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    val addrStr = text.removePrefix("0x").trim()
                    if (addrStr.isBlank()) {
                        error = "Address cannot be empty"
                        return@TextButton
                    }
                    try {
                        val addr = addrStr.toLong(16)
                        onJump(addr)
                        onDismiss()
                    } catch (e: NumberFormatException) {
                         error = "Invalid hex address"
                    }
                }
            ) {
                Text("Go")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

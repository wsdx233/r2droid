package top.wsdx233.r2droid.feature.project

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.dialogs.HistoryDialog
import top.wsdx233.r2droid.core.ui.dialogs.FunctionInfoDialog
import top.wsdx233.r2droid.core.ui.dialogs.FunctionVariablesDialog
import top.wsdx233.r2droid.core.ui.dialogs.FunctionXrefsDialog
import top.wsdx233.r2droid.core.ui.dialogs.JumpDialog
import top.wsdx233.r2droid.core.ui.dialogs.XrefsDialog
import top.wsdx233.r2droid.feature.disasm.DisasmEvent
import top.wsdx233.r2droid.feature.disasm.DisasmViewModel
import top.wsdx233.r2droid.feature.hex.HexEvent
import top.wsdx233.r2droid.feature.hex.HexViewModel
import top.wsdx233.r2droid.feature.ai.AiViewModel
import top.wsdx233.r2droid.feature.ai.ui.AiChatScreen
import top.wsdx233.r2droid.feature.ai.ui.AiProviderSettingsScreen
import top.wsdx233.r2droid.feature.terminal.ui.CommandScreen
import kotlinx.coroutines.delay
import top.wsdx233.r2droid.util.R2PipeManager

enum class MainCategory(@StringRes val titleRes: Int, val icon: ImageVector) {
    List(R.string.proj_category_list, Icons.Filled.List),
    Detail(R.string.proj_category_detail, Icons.Filled.Info),
    Project(R.string.proj_category_project, Icons.Filled.Build),
    AI(R.string.proj_category_ai, Icons.Filled.SmartToy)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectScaffold(
    viewModel: ProjectViewModel,
    hexViewModel: HexViewModel = hiltViewModel(),
    disasmViewModel: DisasmViewModel = hiltViewModel(),
    aiViewModel: AiViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Global invalidation listener
    val globalInvalidation by viewModel.globalDataInvalidated.collectAsState()
    androidx.compose.runtime.LaunchedEffect(globalInvalidation) {
        if (globalInvalidation > 0) {
            hexViewModel.onEvent(HexEvent.RefreshData)
            disasmViewModel.onEvent(DisasmEvent.RefreshData)
        }
    }

    // Refresh function list when disasm data is modified (e.g. function rename)
    val dataModified by disasmViewModel.dataModifiedEvent.collectAsState()
    androidx.compose.runtime.LaunchedEffect(dataModified) {
        if (dataModified > 0) {
            viewModel.onEvent(ProjectEvent.LoadFunctions(forceRefresh = true))
        }
    }

    // State for navigation
    var selectedCategory by remember { mutableStateOf(MainCategory.List) }
    var selectedListTabIndex by remember { mutableIntStateOf(0) }
    var selectedDetailTabIndex by remember { mutableIntStateOf(1) }
    var selectedProjectTabIndex by remember { mutableIntStateOf(0) }
    var selectedAiTabIndex by remember { mutableIntStateOf(0) }
    var showJumpDialog by remember { mutableStateOf(false) }

    // Hoisted ListTab state (survives category switches)
    val listSearchQueries = remember { mutableStateMapOf<Int, String>() }
    val listScrollStates = remember { mutableStateMapOf<Int, androidx.compose.foundation.lazy.LazyListState>() }

    // Hoisted CommandScreen state
    var cmdInput by remember { mutableStateOf("") }
    var cmdHistory by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    // R2Pipe busy state for progress indicator
    val r2State by R2PipeManager.state.collectAsState()
    var showBusy by remember { mutableStateOf(false) }
    var busyCommand by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(r2State) {
        when (val s = r2State) {
            is R2PipeManager.State.Executing -> {
                busyCommand = s.command
                delay(300)
                showBusy = true
            }
            else -> {
                showBusy = false
            }
        }
    }

    val listTabs = listOf(
        R.string.proj_tab_overview, R.string.proj_tab_search, R.string.proj_tab_sections, R.string.proj_tab_symbols,
        R.string.proj_tab_imports, R.string.proj_tab_relocs, R.string.proj_tab_strings, R.string.proj_tab_functions
    )
    val detailTabs = listOf(R.string.proj_tab_hex, R.string.proj_tab_disassembly, R.string.proj_tab_decompile, R.string.proj_tab_graph)
    val projectTabs = listOf(R.string.proj_tab_settings, R.string.proj_tab_cmd, R.string.proj_tab_logs)
    val aiTabs = listOf(R.string.ai_tab_chat, R.string.ai_tab_settings)

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = { Text(stringResource(top.wsdx233.r2droid.R.string.app_name)) },
                actions = {
                    if (selectedCategory == MainCategory.Detail) {
                        val canGoBack by viewModel.canGoBack.collectAsState()
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .combinedClickable(
                                    enabled = canGoBack,
                                    onClick = { viewModel.onEvent(ProjectEvent.NavigateBack) },
                                    onLongClick = { viewModel.showHistoryDialog() },
                                    indication = androidx.compose.material3.ripple(
                                        bounded = false,
                                        radius = 24.dp
                                    ),
                                    interactionSource = remember { MutableInteractionSource() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.proj_nav_go_back),
                                tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        if (selectedDetailTabIndex in 0..3) {
                            androidx.compose.material3.IconButton(onClick = { viewModel.onEvent(ProjectEvent.RequestScrollToSelection) }) {
                                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.proj_nav_scroll_to_selection))
                            }
                        }
                        // Decompiler switcher button (only on Decompile tab)
                        if (selectedDetailTabIndex == 2) {
                            var showDecompilerMenu by remember { mutableStateOf(false) }
                            val currentDecompiler by viewModel.currentDecompiler.collectAsState()
                            Box {
                                androidx.compose.material3.IconButton(onClick = { showDecompilerMenu = true }) {
                                    Icon(Icons.Filled.Build, contentDescription = stringResource(R.string.decompiler_switch))
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showDecompilerMenu,
                                    onDismissRequest = { showDecompilerMenu = false }
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                androidx.compose.material3.RadioButton(
                                                    selected = currentDecompiler == "r2ghidra",
                                                    onClick = null
                                                )
                                                Text(stringResource(R.string.decompiler_r2ghidra), modifier = Modifier.padding(start = 8.dp))
                                            }
                                        },
                                        onClick = {
                                            showDecompilerMenu = false
                                            if (currentDecompiler != "r2ghidra") {
                                                viewModel.onEvent(ProjectEvent.SwitchDecompiler("r2ghidra"))
                                            }
                                        }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                androidx.compose.material3.RadioButton(
                                                    selected = currentDecompiler == "native",
                                                    onClick = null
                                                )
                                                Text(stringResource(R.string.decompiler_native), modifier = Modifier.padding(start = 8.dp))
                                            }
                                        },
                                        onClick = {
                                            showDecompilerMenu = false
                                            if (currentDecompiler != "native") {
                                                viewModel.onEvent(ProjectEvent.SwitchDecompiler("native"))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        androidx.compose.material3.IconButton(onClick = { showJumpDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.MenuOpen, contentDescription = stringResource(R.string.menu_jump))
                        }
                    }
                }
            )

            AnimatedVisibility(visible = showBusy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = busyCommand,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 120.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            } // end Column

            if (showJumpDialog) {
                val currentAddress = (uiState as? ProjectUiState.Success)?.cursorAddress ?: 0L
                JumpDialog(
                    initialAddress = "0x%X".format(currentAddress),
                    onDismiss = { showJumpDialog = false },
                    onJump = { addr ->
                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                        showJumpDialog = false
                    },
                    onResolveExpression = { expression ->
                        viewModel.resolveExpression(expression)
                    }
                )
            }
            
            val xrefsState by disasmViewModel.xrefsState.collectAsState()
            if (xrefsState.visible) {
                XrefsDialog(
                    xrefsData = xrefsState.data,
                    targetAddress = xrefsState.targetAddress,
                    onDismiss = { disasmViewModel.onEvent(DisasmEvent.DismissXrefs) },
                    onJump = { addr ->
                        selectedCategory = MainCategory.Detail
                        selectedDetailTabIndex = 1
                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                        disasmViewModel.onEvent(DisasmEvent.DismissXrefs)
                    }
                )
            }

            val historyState by viewModel.historyState.collectAsState()
            if (historyState.visible) {
                HistoryDialog(
                    entries = historyState.entries,
                    isLoading = historyState.isLoading,
                    onDismiss = { viewModel.dismissHistoryDialog() },
                    onJump = { addr ->
                        viewModel.dismissHistoryDialog()
                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                    }
                )
            }

            // Function Info Dialog
            val functionInfoState by disasmViewModel.functionInfoState.collectAsState()
            if (functionInfoState.visible) {
                FunctionInfoDialog(
                    functionInfo = functionInfoState.data,
                    isLoading = functionInfoState.isLoading,
                    targetAddress = functionInfoState.targetAddress,
                    onDismiss = { disasmViewModel.onEvent(DisasmEvent.DismissFunctionInfo) },
                    onRename = { newName ->
                        disasmViewModel.onEvent(
                            DisasmEvent.RenameFunctionFromInfo(functionInfoState.targetAddress, newName)
                        )
                    },
                    onJump = { addr ->
                        selectedCategory = MainCategory.Detail
                        selectedDetailTabIndex = 1
                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                        disasmViewModel.onEvent(DisasmEvent.DismissFunctionInfo)
                    }
                )
            }

            // Function Xrefs Dialog
            val functionXrefsState by disasmViewModel.functionXrefsState.collectAsState()
            if (functionXrefsState.visible) {
                FunctionXrefsDialog(
                    xrefs = functionXrefsState.data,
                    isLoading = functionXrefsState.isLoading,
                    targetAddress = functionXrefsState.targetAddress,
                    onDismiss = { disasmViewModel.onEvent(DisasmEvent.DismissFunctionXrefs) },
                    onJump = { addr ->
                        selectedCategory = MainCategory.Detail
                        selectedDetailTabIndex = 1
                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                        disasmViewModel.onEvent(DisasmEvent.DismissFunctionXrefs)
                    }
                )
            }

            // Function Variables Dialog
            val functionVariablesState by disasmViewModel.functionVariablesState.collectAsState()
            if (functionVariablesState.visible) {
                FunctionVariablesDialog(
                    variables = functionVariablesState.data,
                    isLoading = functionVariablesState.isLoading,
                    targetAddress = functionVariablesState.targetAddress,
                    onDismiss = { disasmViewModel.onEvent(DisasmEvent.DismissFunctionVariables) },
                    onRename = { oldName, newName ->
                        disasmViewModel.onEvent(
                            DisasmEvent.RenameFunctionVariable(
                                functionVariablesState.targetAddress, oldName, newName
                            )
                        )
                    }
                )
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 8.dp
            ) {
                Column {
                    // Level 2: Sub-tabs
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
                                        text = { Text(text = stringResource(title)) }
                                    )
                                }
                            }
                        }
                        MainCategory.Detail -> {
                            ScrollableTabRow(
                                selectedTabIndex = selectedDetailTabIndex,
                                edgePadding = 0.dp,
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
                                        text = { Text(text = stringResource(title)) }
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
                                        text = { Text(text = stringResource(title)) }
                                    )
                                }
                            }
                        }
                        MainCategory.AI -> {
                            TabRow(
                                selectedTabIndex = selectedAiTabIndex,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                indicator = { tabPositions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedAiTabIndex]),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            ) {
                                aiTabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedAiTabIndex == index,
                                        onClick = { selectedAiTabIndex = index },
                                        text = { Text(text = stringResource(title)) }
                                    )
                                }
                            }
                        }
                    }

                    // Level 1: Category
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        MainCategory.values().forEach { category ->
                            NavigationBarItem(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                icon = { Icon(category.icon, contentDescription = stringResource(category.titleRes)) },
                                label = { Text(stringResource(category.titleRes)) }
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
                    Text(stringResource(R.string.common_idle), Modifier.align(Alignment.Center))
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
                            text = stringResource(R.string.common_error),
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
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }
                is ProjectUiState.Success -> {
                    when (selectedCategory) {
                        MainCategory.List -> {
                            ProjectListView(
                                tabIndex = selectedListTabIndex,
                                searchQueries = listSearchQueries,
                                listStates = listScrollStates,
                                onNavigateToDetail = { addr, tabIdx ->
                                    selectedCategory = MainCategory.Detail
                                    selectedDetailTabIndex = tabIdx
                                    viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                                }
                            )
                        }
                        MainCategory.Detail -> {
                            ProjectDetailView(
                                tabIndex = selectedDetailTabIndex
                            )
                        }
                        MainCategory.Project -> {
                            val logs by viewModel.logs.collectAsState()
                            when (selectedProjectTabIndex) {
                                0 -> ProjectSettingsScreen(viewModel)
                                1 -> CommandScreen(
                                    command = cmdInput,
                                    onCommandChange = { cmdInput = it },
                                    commandHistory = cmdHistory,
                                    onCommandHistoryChange = { cmdHistory = it }
                                )
                                2 -> LogList(logs, onClearLogs = { viewModel.onEvent(ProjectEvent.ClearLogs) })
                            }
                        }
                        MainCategory.AI -> {
                            when (selectedAiTabIndex) {
                                0 -> AiChatScreen(aiViewModel)
                                1 -> AiProviderSettingsScreen(aiViewModel)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

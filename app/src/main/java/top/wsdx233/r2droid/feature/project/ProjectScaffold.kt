package top.wsdx233.r2droid.feature.project

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.core.ui.adaptive.LocalWindowWidthClass
import top.wsdx233.r2droid.core.ui.adaptive.WindowWidthClass
import top.wsdx233.r2droid.core.ui.components.CommandSuggestButton
import top.wsdx233.r2droid.core.ui.components.CommandSuggestionPanel
import top.wsdx233.r2droid.core.ui.dialogs.FunctionInfoDialog
import top.wsdx233.r2droid.core.ui.dialogs.FunctionVariablesDialog
import top.wsdx233.r2droid.core.ui.dialogs.FunctionXrefsDialog
import top.wsdx233.r2droid.core.ui.dialogs.HistoryDialog
import top.wsdx233.r2droid.core.ui.dialogs.JumpDialog
import top.wsdx233.r2droid.core.ui.dialogs.XrefsDialog
import top.wsdx233.r2droid.feature.ai.AiViewModel
import top.wsdx233.r2droid.feature.ai.ui.AiChatScreen
import top.wsdx233.r2droid.feature.ai.ui.AiPromptsScreen
import top.wsdx233.r2droid.feature.ai.ui.AiProviderSettingsScreen
import top.wsdx233.r2droid.feature.disasm.DisasmEvent
import top.wsdx233.r2droid.feature.disasm.DisasmViewModel
import top.wsdx233.r2droid.feature.hex.HexEvent
import top.wsdx233.r2droid.feature.hex.HexViewModel
import top.wsdx233.r2droid.feature.r2frida.R2FridaViewModel
import top.wsdx233.r2droid.feature.r2frida.data.*
import top.wsdx233.r2droid.feature.r2frida.ui.*
import top.wsdx233.r2droid.feature.terminal.ui.CommandScreen
import top.wsdx233.r2droid.feature.plugin.PluginManager
import top.wsdx233.r2droid.feature.plugin.PluginPage
import top.wsdx233.r2droid.feature.plugin.PluginPageRenderer
import top.wsdx233.r2droid.feature.plugin.PluginProjectActionDescriptor
import top.wsdx233.r2droid.feature.plugin.PluginRuntime
import top.wsdx233.r2droid.feature.plugin.PluginScreenTabDescriptor
import top.wsdx233.r2droid.util.R2PipeManager

enum class MainCategory(@StringRes val titleRes: Int, val icon: ImageVector) {
    List(R.string.proj_category_list, Icons.AutoMirrored.Filled.List),
    Detail(R.string.proj_category_detail, Icons.Filled.Info),
    R2Frida(R.string.proj_category_r2frida, Icons.Filled.BugReport),
    Project(R.string.proj_category_project, Icons.Filled.Build),
    AI(R.string.proj_category_ai, Icons.Filled.SmartToy)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun ProjectScaffold(
    sessionId: String,
    viewModel: ProjectViewModel,
    hexViewModel: HexViewModel = hiltViewModel(key = "hex-$sessionId"),
    disasmViewModel: DisasmViewModel = hiltViewModel(key = "disasm-$sessionId"),
    aiViewModel: AiViewModel = hiltViewModel(key = "ai-$sessionId"),
    r2fridaViewModel: R2FridaViewModel = hiltViewModel(key = "frida-$sessionId"),
    onOpenDrawer: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
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
            viewModel.onEvent(ProjectEvent.ClearFunctionsCache)
        }
    }

    // Also clear when hex data is modified
    val hexDataModified by hexViewModel.dataModifiedEvent.collectAsState()
    androidx.compose.runtime.LaunchedEffect(hexDataModified) {
        if (hexDataModified > 0) {
            viewModel.onEvent(ProjectEvent.ClearFunctionsCache)
        }
    }

    // State for navigation
    var selectedCategory by remember { mutableStateOf(MainCategory.List) }
    var selectedListTabIndex by remember { mutableIntStateOf(0) }
    var selectedDetailTabIndex by remember { mutableIntStateOf(1) }
    var selectedProjectTabIndex by remember { mutableIntStateOf(0) }
    var selectedAiTabIndex by remember { mutableIntStateOf(0) }
    var selectedR2FridaTabIndex by remember { mutableIntStateOf(0) }
    var showJumpDialog by remember { mutableStateOf(false) }
    val visitedAddresses = remember { mutableStateListOf<Long>() }
    val isR2Frida = R2PipeManager.isR2FridaSession
    val isAiEnabled = SettingsManager.aiEnabled
    val isWide = LocalWindowWidthClass.current != WindowWidthClass.Compact

    // Hoisted list-category scroll states (survive category switches)
    val listOverviewScrollState = rememberScrollState()
    val listSearchResultState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listSectionsState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listSymbolsState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listExportsState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listImportsState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listRelocationsState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listStringsState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listFunctionsState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Hoisted R2Frida list scroll states (survive category switches)
    val fridaLibrariesListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val fridaMappingsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val fridaEntriesListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val fridaExportsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val fridaStringsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val fridaSymbolsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val fridaSectionsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val fridaCustomFunctionsListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Decompiler export state
    var exportDecompCode by remember { mutableStateOf<String?>(null) }

    // Hoisted CommandScreen state
    var cmdInput by remember { mutableStateOf("") }
    var cmdHistory by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    // Command bottom sheet state (swipe-up panel)
    var showCommandSheet by remember { mutableStateOf(false) }
    var sheetCommand by remember { mutableStateOf("") }
    var sheetOutput by remember { mutableStateOf("") }
    var sheetExecuting by remember { mutableStateOf(false) }
    val sheetScope = rememberCoroutineScope()

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

    // Sync detail tab index to ViewModel for conditional decompilation loading
    androidx.compose.runtime.LaunchedEffect(selectedDetailTabIndex) {
        viewModel.currentDetailTab = selectedDetailTabIndex
    }

    val baseListTabs = listOf(
        R.string.proj_tab_overview, R.string.proj_tab_search, R.string.proj_tab_sections, R.string.proj_tab_symbols,
        R.string.proj_tab_exports, R.string.proj_tab_imports, R.string.proj_tab_relocs, R.string.proj_tab_strings, R.string.proj_tab_functions
    )
    val pluginListTabs by rememberPluginTabsForTarget("list")
    val listTabTitles = buildList {
        addAll(baseListTabs.map { stringResource(it) })
        addAll(pluginListTabs.map { it.tab.title })
    }
    androidx.compose.runtime.LaunchedEffect(listTabTitles.size) {
        if (selectedListTabIndex >= listTabTitles.size) {
            selectedListTabIndex = 0
        }
    }

    val baseDetailTabs = listOf(R.string.proj_tab_hex, R.string.proj_tab_disassembly, R.string.proj_tab_decompile, R.string.proj_tab_graph)
    val pluginDetailTabs by rememberPluginTabsForTarget("detail")
    val detailTabs = buildList {
        addAll(baseDetailTabs)
        addAll(pluginDetailTabs.map { -1 })
    }
    val baseProjectTabs = listOf(R.string.proj_tab_settings, R.string.proj_tab_cmd, R.string.proj_tab_logs)
    val pluginProjectTabs by PluginManager.projectTabs.collectAsState()
    val pluginProjectActions by PluginManager.projectActions.collectAsState()
    val projectTabs = buildList {
        addAll(baseProjectTabs.map { stringResource(it) })
        addAll(pluginProjectTabs.map { it.tab.title })
    }
    androidx.compose.runtime.LaunchedEffect(projectTabs.size) {
        if (selectedProjectTabIndex >= projectTabs.size) {
            selectedProjectTabIndex = 0
        }
    }
    val detailTabTitles = buildList {
        addAll(baseDetailTabs.map { stringResource(it) })
        addAll(pluginDetailTabs.map { it.tab.title })
    }
    androidx.compose.runtime.LaunchedEffect(detailTabTitles.size) {
        if (selectedDetailTabIndex >= detailTabTitles.size) {
            selectedDetailTabIndex = 0
        }
    }

    val baseAiTabs = listOf(R.string.ai_tab_chat, R.string.ai_tab_settings, R.string.ai_tab_prompts)
    val pluginAiTabs by rememberPluginTabsForTarget("ai")
    val aiTabTitles = buildList {
        addAll(baseAiTabs.map { stringResource(it) })
        addAll(pluginAiTabs.map { it.tab.title })
    }
    androidx.compose.runtime.LaunchedEffect(aiTabTitles.size) {
        if (selectedAiTabIndex >= aiTabTitles.size) {
            selectedAiTabIndex = 0
        }
    }

    val baseR2fridaTabs = listOf(
        R.string.r2frida_tab_overview, R.string.r2frida_tab_libraries, R.string.r2frida_tab_mappings,
        R.string.r2frida_tab_scripts,
        R.string.r2frida_tab_entries, R.string.r2frida_tab_exports, R.string.r2frida_tab_strings,
        R.string.r2frida_tab_symbols, R.string.r2frida_tab_sections,
        R.string.r2frida_tab_functions, R.string.r2frida_tab_search, R.string.r2frida_tab_monitor
    )
    val pluginR2fridaTabs by rememberPluginTabsForTarget("r2frida")
    val r2fridaTabTitles = buildList {
        addAll(baseR2fridaTabs.map { stringResource(it) })
        addAll(pluginR2fridaTabs.map { it.tab.title })
    }
    androidx.compose.runtime.LaunchedEffect(r2fridaTabTitles.size) {
        if (selectedR2FridaTabIndex >= r2fridaTabTitles.size) {
            selectedR2FridaTabIndex = 0
        }
    }

    val currentPluginActionTabAliases = remember(
        selectedCategory,
        selectedListTabIndex,
        selectedDetailTabIndex,
        selectedProjectTabIndex,
        selectedAiTabIndex,
        selectedR2FridaTabIndex,
        listTabTitles,
        detailTabTitles,
        projectTabs,
        aiTabTitles,
        r2fridaTabTitles,
        pluginListTabs,
        pluginDetailTabs,
        pluginProjectTabs,
        pluginAiTabs,
        pluginR2fridaTabs
    ) {
        when (selectedCategory) {
            MainCategory.List -> buildCurrentTabAliases(
                selectedIndex = selectedListTabIndex,
                allTitles = listTabTitles,
                baseKeys = listOf(
                    "list.overview", "list.search", "list.sections", "list.symbols",
                    "list.exports", "list.imports", "list.relocations", "list.strings", "list.functions"
                ),
                baseSize = baseListTabs.size,
                pluginKeys = pluginListTabs.map { it.tab.key }
            )

            MainCategory.Detail -> buildCurrentTabAliases(
                selectedIndex = selectedDetailTabIndex,
                allTitles = detailTabTitles,
                baseKeys = listOf("detail.hex", "detail.disassembly", "detail.decompile", "detail.graph"),
                baseSize = baseDetailTabs.size,
                pluginKeys = pluginDetailTabs.map { it.tab.key }
            )

            MainCategory.Project -> buildCurrentTabAliases(
                selectedIndex = selectedProjectTabIndex,
                allTitles = projectTabs,
                baseKeys = listOf("project.settings", "project.command", "project.logs"),
                baseSize = baseProjectTabs.size,
                pluginKeys = pluginProjectTabs.map { it.tab.key }
            )

            MainCategory.AI -> buildCurrentTabAliases(
                selectedIndex = selectedAiTabIndex,
                allTitles = aiTabTitles,
                baseKeys = listOf("ai.chat", "ai.settings", "ai.prompts"),
                baseSize = baseAiTabs.size,
                pluginKeys = pluginAiTabs.map { it.tab.key }
            )

            MainCategory.R2Frida -> buildCurrentTabAliases(
                selectedIndex = selectedR2FridaTabIndex,
                allTitles = r2fridaTabTitles,
                baseKeys = listOf(
                    "r2frida.overview", "r2frida.libraries", "r2frida.mappings", "r2frida.scripts",
                    "r2frida.entries", "r2frida.exports", "r2frida.strings", "r2frida.symbols",
                    "r2frida.sections", "r2frida.functions", "r2frida.search", "r2frida.monitor"
                ),
                baseSize = baseR2fridaTabs.size,
                pluginKeys = pluginR2fridaTabs.map { it.tab.key }
            )
        }
    }
    val visibleProjectActions = pluginProjectActions.filter { descriptor ->
        isPluginProjectActionVisible(descriptor, currentPluginActionTabAliases)
    }
    val disasmMultiSelect by disasmViewModel.multiSelectState.collectAsState()
    val isDisasmSelectionMode =
        selectedCategory == MainCategory.Detail &&
            selectedDetailTabIndex == 1 &&
            disasmMultiSelect.active

    val quickJumpToDetail: ((Long) -> Unit)? = when (SettingsManager.defaultJumpTarget) {
        "hex", "disasm" -> { addr: Long ->
            val target = if (SettingsManager.defaultJumpTarget == "hex") 0 else 1
            selectedCategory = MainCategory.Detail
            selectedDetailTabIndex = target
            viewModel.currentDetailTab = target
            viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
        }
        else -> null // "ask" → fall back to dropdown submenu
    }

    val markVisited: (Long) -> Unit = { addr ->
        if (!visitedAddresses.contains(addr)) {
            visitedAddresses.add(addr)
        }
    }

    val isVisited: (Long) -> Boolean = { addr -> visitedAddresses.contains(addr) }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                navigationIcon = {
                    if (!isDisasmSelectionMode) {
                        androidx.compose.material3.IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.session_drawer_open)
                            )
                        }
                    }
                },
                title = {
                    if (!isDisasmSelectionMode) {
                        Text(
                            text = stringResource(top.wsdx233.r2droid.R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (selectedCategory == MainCategory.Detail) {
                        val canGoBack by viewModel.canGoBack.collectAsState()
                        if (!isDisasmSelectionMode) {
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
                        }
                        if (selectedDetailTabIndex in 0..3) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .combinedClickable(
                                        onClick = { viewModel.onEvent(ProjectEvent.RequestScrollToSelection) },
                                        onLongClick = { viewModel.onEvent(ProjectEvent.RefreshAllViews) },
                                        indication = androidx.compose.material3.ripple(
                                            bounded = false,
                                            radius = 24.dp
                                        ),
                                        interactionSource = remember { MutableInteractionSource() }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.proj_nav_scroll_to_selection))
                            }
                        }
                        // Decompiler switcher button (only on Decompile tab)
                        if (selectedDetailTabIndex == 2) {
                            var showDecompilerMenu by remember { mutableStateOf(false) }
                            val currentDecompiler by viewModel.currentDecompiler.collectAsState()
                            val showLineNumbers by viewModel.decompilerShowLineNumbers.collectAsState()
                            val wordWrap by viewModel.decompilerWordWrap.collectAsState()
                            val soraMode by viewModel.decompilerSoraMode.collectAsState()
                            Box {
                                androidx.compose.material3.IconButton(onClick = { showDecompilerMenu = true }) {
                                    Icon(Icons.Filled.Build, contentDescription = stringResource(R.string.decompiler_switch))
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showDecompilerMenu,
                                    onDismissRequest = { showDecompilerMenu = false }
                                ) {
                                    // Section: Decompiler
                                    Text(
                                        stringResource(R.string.decompiler_section_decompiler),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                    listOf("r2ghidra", "r2dec", "native", "aipdg").forEach { type ->
                                        val labelRes = when (type) {
                                            "r2ghidra" -> R.string.decompiler_r2ghidra
                                            "r2dec" -> R.string.decompiler_r2dec
                                            "native" -> R.string.decompiler_native
                                            else -> R.string.decompiler_aipdg
                                        }
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    androidx.compose.material3.RadioButton(selected = currentDecompiler == type, onClick = null)
                                                    Text(stringResource(labelRes), modifier = Modifier.padding(start = 8.dp))
                                                }
                                            },
                                            onClick = {
                                                showDecompilerMenu = false
                                                if (currentDecompiler != type) viewModel.onEvent(ProjectEvent.SwitchDecompiler(type))
                                            }
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    // Section: Display
                                    Text(
                                        stringResource(R.string.decompiler_section_display),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(stringResource(R.string.settings_decompiler_show_line_numbers), modifier = Modifier.weight(1f))
                                                Checkbox(checked = showLineNumbers, onCheckedChange = null, modifier = Modifier.size(24.dp))
                                            }
                                        },
                                        onClick = { viewModel.toggleLineNumbers() }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(stringResource(R.string.settings_decompiler_word_wrap), modifier = Modifier.weight(1f))
                                                Checkbox(checked = wordWrap, onCheckedChange = null, modifier = Modifier.size(24.dp))
                                            }
                                        },
                                        onClick = { viewModel.toggleWordWrap() }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.decompiler_reset_zoom)) },
                                        onClick = {
                                            showDecompilerMenu = false
                                            viewModel.resetDecompilerZoom()
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Text(
                                        stringResource(R.string.decompiler_export_view),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(stringResource(R.string.decompiler_export_builtin), modifier = Modifier.weight(1f))
                                                Checkbox(checked = soraMode, onCheckedChange = null, modifier = Modifier.size(24.dp))
                                            }
                                        },
                                        onClick = { viewModel.toggleSoraMode() }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.decompiler_export_external)) },
                                        onClick = {
                                            showDecompilerMenu = false
                                            val code = (uiState as? ProjectUiState.Success)?.decompilation?.code
                                            if (!code.isNullOrBlank()) exportDecompCode = code
                                        }
                                    )
                                }
                            }
                        }
                        // Multi-select actions for disasm tab
                        if (selectedDetailTabIndex == 1 && disasmMultiSelect.active) {
                            val msClipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                            val msScope = rememberCoroutineScope()
                            // Selected count badge
                            Text(
                                stringResource(R.string.multiselect_count, disasmViewModel.getSelectedCount()),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            // Copy button with dropdown
                            var showCopyMenu by remember { mutableStateOf(false) }
                            Box {
                                androidx.compose.material3.IconButton(onClick = { showCopyMenu = true }) {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Filled.ContentCopy,
                                        contentDescription = stringResource(R.string.multiselect_copy),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showCopyMenu,
                                    onDismissRequest = { showCopyMenu = false }
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.multiselect_copy_instructions)) },
                                        onClick = {
                                            showCopyMenu = false
                                            val instrs = disasmViewModel.getSelectedInstructions()
                                            val text = instrs.joinToString("\n") { it.disasm }
                                            msClipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                                        }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.multiselect_copy_full)) },
                                        onClick = {
                                            showCopyMenu = false
                                            val state = disasmMultiSelect
                                            val instrs = disasmViewModel.getSelectedInstructions()
                                            val n = instrs.size
                                            val start = state.rangeStart
                                            msScope.launch {
                                                val result = top.wsdx233.r2droid.util.R2PipeManager.execute("pd $n @ $start").getOrDefault("")
                                                msClipboard.setText(androidx.compose.ui.text.AnnotatedString(result))
                                            }
                                        }
                                    )
                                }
                            }
                            // Fill button
                            var showFillDialog by remember { mutableStateOf(false) }
                            androidx.compose.material3.IconButton(onClick = { showFillDialog = true }) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Filled.FormatPaint,
                                    contentDescription = stringResource(R.string.multiselect_fill),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (showFillDialog) {
                                var fillValue by remember { mutableStateOf("") }
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { showFillDialog = false },
                                    title = { Text(stringResource(R.string.multiselect_fill_title)) },
                                    text = {
                                        androidx.compose.material3.OutlinedTextField(
                                            value = fillValue,
                                            onValueChange = { fillValue = it },
                                            label = { Text(stringResource(R.string.multiselect_fill_hint)) },
                                            singleLine = true
                                        )
                                    },
                                    confirmButton = {
                                        androidx.compose.material3.TextButton(
                                            onClick = {
                                                showFillDialog = false
                                                disasmViewModel.fillSelectedRange(fillValue)
                                            },
                                            enabled = fillValue.isNotBlank()
                                        ) { Text("OK") }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(onClick = { showFillDialog = false }) {
                                            Text(stringResource(R.string.home_delete_cancel))
                                        }
                                    }
                                )
                            }
                            // Extend to function button
                            androidx.compose.material3.IconButton(
                                onClick = { disasmViewModel.onEvent(DisasmEvent.ExtendToFunction) }
                            ) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Filled.SelectAll,
                                    contentDescription = stringResource(R.string.multiselect_extend),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Close multi-select
                            androidx.compose.material3.IconButton(
                                onClick = { disasmViewModel.onEvent(DisasmEvent.ClearMultiSelect) }
                            ) {
                                Icon(
                                    Icons.Filled.Cancel,
                                    contentDescription = stringResource(R.string.multiselect_close),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            if (selectedDetailTabIndex == 1 && isAiEnabled) {
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        val currentAddress = (uiState as? ProjectUiState.Success)?.cursorAddress ?: 0L
                                        disasmViewModel.onEvent(DisasmEvent.AiPolishDisassembly(currentAddress))
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.AutoFixHigh,
                                        contentDescription = stringResource(R.string.disasm_ai_explain)
                                    )
                                }
                            }
                            androidx.compose.material3.IconButton(onClick = { showJumpDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.MenuOpen, contentDescription = stringResource(R.string.menu_jump))
                            }
                        }
                    } else if (selectedCategory == MainCategory.List && selectedListTabIndex == 7) {
                        var showStringsMenu by remember { mutableStateOf(false) }
                        val stringsUseFullRange by viewModel.stringsUseFullRange.collectAsState()
                        Box {
                            androidx.compose.material3.IconButton(onClick = { showStringsMenu = true }) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.proj_strings_options)
                                )
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = showStringsMenu,
                                onDismissRequest = { showStringsMenu = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(stringResource(R.string.proj_strings_full_range))
                                            Checkbox(
                                                checked = stringsUseFullRange,
                                                onCheckedChange = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        showStringsMenu = false
                                        viewModel.setStringsUseFullRange(!stringsUseFullRange)
                                    }
                                )
                            }
                        }
                    }
                    visibleProjectActions.forEach { descriptor ->
                        IconButton(
                            onClick = {
                                sheetScope.launch {
                                    executePluginProjectAction(
                                        context = context,
                                        descriptor = descriptor
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = pluginProjectActionIcon(descriptor.action.icon),
                                contentDescription = descriptor.action.title.ifBlank { descriptor.action.key }
                            )
                        }
                    }
                }
            )

            AnimatedVisibility(visible = showBusy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.FilledTonalIconButton(
                        onClick = { R2PipeManager.interrupt() },
                        modifier = Modifier.size(32.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            Icons.Filled.Cancel,
                            contentDescription = stringResource(R.string.proj_interrupt_stop),
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                        viewModel.currentDetailTab = 1
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
                        viewModel.currentDetailTab = 1
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
                        viewModel.currentDetailTab = 1
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
            val isFridaScriptScreen = selectedCategory == MainCategory.R2Frida && selectedR2FridaTabIndex == 3
            val hideBottomBar = isFridaScriptScreen && WindowInsets.isImeVisible
            if (!isWide && !hideBottomBar) {
                ProjectBottomBar(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    selectedListTabIndex = selectedListTabIndex,
                    onListTabSelected = { selectedListTabIndex = it },
                    selectedDetailTabIndex = selectedDetailTabIndex,
                    onDetailTabSelected = { selectedDetailTabIndex = it },
                    selectedProjectTabIndex = selectedProjectTabIndex,
                    onProjectTabSelected = { selectedProjectTabIndex = it },
                    selectedAiTabIndex = selectedAiTabIndex,
                    onAiTabSelected = { selectedAiTabIndex = it },
                    selectedR2FridaTabIndex = selectedR2FridaTabIndex,
                    onR2FridaTabSelected = { selectedR2FridaTabIndex = it },
                    listTabs = baseListTabs,
                    listTabTitles = listTabTitles,
                    detailTabs = detailTabs,
                    projectTabs = projectTabs,
                    detailTabTitles = detailTabTitles,
                    aiTabs = baseAiTabs,
                    aiTabTitles = aiTabTitles,
                    r2fridaTabs = baseR2fridaTabs,
                    r2fridaTabTitles = r2fridaTabTitles,
                    isR2Frida = isR2Frida,
                    onSwipeUpCommand = { showCommandSheet = true }
                )
            }
        }
    ) { paddingValues ->
        Row(modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues).fillMaxSize()) {
            // NavigationRail for wide screens
            if (isWide) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Spacer(Modifier.height(8.dp))
                    MainCategory.entries
                        .filter { it != MainCategory.R2Frida || isR2Frida }
                        .filter { it != MainCategory.AI || isAiEnabled }
                        .forEach { category ->
                            NavigationRailItem(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                icon = { Icon(category.icon, contentDescription = stringResource(category.titleRes)) },
                                label = { Text(stringResource(category.titleRes), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Sub-tabs on top for wide mode
                if (isWide) {
                    ProjectSubTabs(
                        selectedCategory = selectedCategory,
                        selectedListTabIndex = selectedListTabIndex,
                        onListTabSelected = { selectedListTabIndex = it },
                        selectedDetailTabIndex = selectedDetailTabIndex,
                        onDetailTabSelected = { selectedDetailTabIndex = it },
                        selectedProjectTabIndex = selectedProjectTabIndex,
                        onProjectTabSelected = { selectedProjectTabIndex = it },
                        selectedAiTabIndex = selectedAiTabIndex,
                        onAiTabSelected = { selectedAiTabIndex = it },
                        selectedR2FridaTabIndex = selectedR2FridaTabIndex,
                        onR2FridaTabSelected = { selectedR2FridaTabIndex = it },
                        listTabs = baseListTabs,
                        listTabTitles = listTabTitles,
                        detailTabs = detailTabs,
                        projectTabs = projectTabs,
                        detailTabTitles = detailTabTitles,
                        aiTabs = baseAiTabs,
                        aiTabTitles = aiTabTitles,
                        r2fridaTabs = baseR2fridaTabs,
                        r2fridaTabTitles = r2fridaTabTitles
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
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
                            if (selectedListTabIndex < baseListTabs.size) {
                                ProjectListView(
                                    viewModel = viewModel,
                                    disasmViewModel = disasmViewModel,
                                    tabIndex = selectedListTabIndex,
                                    overviewScrollState = listOverviewScrollState,
                                    searchResultListState = listSearchResultState,
                                    sectionsListState = listSectionsState,
                                    symbolsListState = listSymbolsState,
                                    exportsListState = listExportsState,
                                    importsListState = listImportsState,
                                    relocationsListState = listRelocationsState,
                                    stringsListState = listStringsState,
                                    functionsListState = listFunctionsState,
                                    onNavigateToDetail = { addr, tabIdx ->
                                        markVisited(addr)
                                        selectedCategory = MainCategory.Detail
                                        selectedDetailTabIndex = tabIdx
                                        viewModel.currentDetailTab = tabIdx
                                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                                    },
                                    onQuickNavigateToDetail = quickJumpToDetail,
                                    onMarkVisited = markVisited,
                                    isVisited = isVisited
                                )
                            } else {
                                val pluginTab = pluginListTabs.getOrNull(selectedListTabIndex - baseListTabs.size)
                                RenderPluginInjectedTab(
                                    pluginTab = pluginTab,
                                    isR2Frida = isR2Frida,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        MainCategory.Detail -> {
                            if (selectedDetailTabIndex < baseDetailTabs.size) {
                                ProjectDetailView(
                                    viewModel = viewModel,
                                    hexViewModel = hexViewModel,
                                    disasmViewModel = disasmViewModel,
                                    tabIndex = selectedDetailTabIndex
                                )
                            } else {
                                val pluginTab = pluginDetailTabs.getOrNull(selectedDetailTabIndex - baseDetailTabs.size)
                                RenderPluginInjectedTab(
                                    pluginTab = pluginTab,
                                    isR2Frida = isR2Frida,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        MainCategory.Project -> {
                            val logs by viewModel.logs.collectAsState()
                            when {
                                selectedProjectTabIndex == 0 -> ProjectSettingsScreen(viewModel, r2fridaViewModel)
                                selectedProjectTabIndex == 1 -> CommandScreen(
                                    command = cmdInput,
                                    onCommandChange = { cmdInput = it },
                                    commandHistory = cmdHistory,
                                    onCommandHistoryChange = { cmdHistory = it }
                                )
                                selectedProjectTabIndex == 2 -> LogList(logs, onClearLogs = { viewModel.onEvent(ProjectEvent.ClearLogs) })
                                else -> {
                                    val pluginTab = pluginProjectTabs.getOrNull(selectedProjectTabIndex - baseProjectTabs.size)
                                    if (pluginTab == null) {
                                        Text(
                                            text = "Plugin tab unavailable",
                                            modifier = Modifier.padding(16.dp),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        val visible = when (pluginTab.tab.visibleWhen.lowercase()) {
                                            "r2frida", "frida" -> isR2Frida
                                            else -> true
                                        }
                                        if (visible) {
                                            PluginPageRenderer(
                                                pluginId = pluginTab.pluginId,
                                                page = pluginTab.tab.page,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = "This tab is only available in r2frida session",
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        MainCategory.AI -> {
                            if (selectedAiTabIndex < baseAiTabs.size) {
                                when (selectedAiTabIndex) {
                                    0 -> AiChatScreen(aiViewModel)
                                    1 -> AiProviderSettingsScreen(aiViewModel)
                                    2 -> AiPromptsScreen()
                                }
                            } else {
                                val pluginTab = pluginAiTabs.getOrNull(selectedAiTabIndex - baseAiTabs.size)
                                RenderPluginInjectedTab(
                                    pluginTab = pluginTab,
                                    isR2Frida = isR2Frida,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        MainCategory.R2Frida -> {
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            val fridaActions = remember(clipboardManager) {
                                top.wsdx233.r2droid.core.ui.components.ListItemActions(
                                    onCopy = { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it)) },
                                    onJumpToHex = { addr ->
                                        markVisited(addr)
                                        selectedCategory = MainCategory.Detail
                                        selectedDetailTabIndex = 0
                                        viewModel.currentDetailTab = 0
                                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                                    },
                                    onJumpToDisasm = { addr ->
                                        markVisited(addr)
                                        selectedCategory = MainCategory.Detail
                                        selectedDetailTabIndex = 1
                                        viewModel.currentDetailTab = 1
                                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                                    },
                                    onQuickJump = quickJumpToDetail,
                                    onMarkVisited = markVisited,
                                    isVisited = isVisited,
                                    onShowXrefs = { addr ->
                                        disasmViewModel.onEvent(top.wsdx233.r2droid.feature.disasm.DisasmEvent.FetchXrefs(addr))
                                    },
                                    onFridaMonitor = { addrHex ->
                                        r2fridaViewModel.setMonitorPrefillAddress(addrHex)
                                        selectedR2FridaTabIndex = 11
                                    },
                                    onFridaCopyCode = { code ->
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(code))
                                    }
                                )
                            }

                            val fridaOverview by r2fridaViewModel.overview.collectAsState()
                            val fridaLibraries by r2fridaViewModel.libraries.collectAsState()
                            val fridaMappings by r2fridaViewModel.mappings.collectAsState()
                            val fridaEntries by r2fridaViewModel.entries.collectAsState()
                            val fridaExports by r2fridaViewModel.exports.collectAsState()
                            val fridaStrings by r2fridaViewModel.strings.collectAsState()
                            val fridaSymbols by r2fridaViewModel.symbols.collectAsState()
                            val fridaSections by r2fridaViewModel.sections.collectAsState()
                            val fridaScriptLogs by r2fridaViewModel.scriptLogs.collectAsState()
                            val fridaScriptRunning by r2fridaViewModel.scriptRunning.collectAsState()
                            val fridaScriptContent by r2fridaViewModel.scriptContent.collectAsState()
                            val fridaCurrentScriptName by r2fridaViewModel.currentScriptName.collectAsState()
                            val fridaScriptFiles by r2fridaViewModel.scriptFiles.collectAsState()

                            // Hoisted search queries (survive category switches via ViewModel)
                            val fridaLibrariesQuery by r2fridaViewModel.librariesSearchQuery.collectAsState()
                            val fridaMappingsQuery by r2fridaViewModel.mappingsSearchQuery.collectAsState()
                            val fridaEntriesQuery by r2fridaViewModel.entriesSearchQuery.collectAsState()
                            val fridaExportsQuery by r2fridaViewModel.exportsSearchQuery.collectAsState()
                            val fridaStringsQuery by r2fridaViewModel.stringsSearchQuery.collectAsState()
                            val fridaSymbolsQuery by r2fridaViewModel.symbolsSearchQuery.collectAsState()
                            val fridaSectionsQuery by r2fridaViewModel.sectionsSearchQuery.collectAsState()

                            val fridaCustomFunctions by r2fridaViewModel.customFunctions.collectAsState()
                            val fridaCustomFunctionsQuery by r2fridaViewModel.customFunctionsSearchQuery.collectAsState()
                            val fridaAutoDemangleExports by r2fridaViewModel.autoDemangleExports.collectAsState()
                            val fridaAutoDemangleCustomFunctions by r2fridaViewModel.autoDemangleCustomFunctions.collectAsState()

                            val fridaSearchResults by r2fridaViewModel.searchResults.collectAsState()
                            val fridaIsSearching by r2fridaViewModel.isSearching.collectAsState()
                            val fridaSearchValueType by r2fridaViewModel.searchValueType.collectAsState()
                            val fridaSearchCompare by r2fridaViewModel.searchCompare.collectAsState()
                            val fridaSelectedRegions by r2fridaViewModel.selectedRegions.collectAsState()
                            val fridaSearchError by r2fridaViewModel.searchError.collectAsState()
                            val fridaFrozenAddresses by r2fridaViewModel.frozenAddresses.collectAsState()
                            val fridaMaxResults by r2fridaViewModel.maxResults.collectAsState()
                            val fridaSearchTimeoutSeconds by r2fridaViewModel.searchTimeoutSeconds.collectAsState()

                            val fridaMonitors by r2fridaViewModel.monitors.collectAsState()
                            val fridaMonitorPrefill by r2fridaViewModel.monitorPrefillAddress.collectAsState()

                            androidx.compose.runtime.LaunchedEffect(selectedR2FridaTabIndex) {
                                when (selectedR2FridaTabIndex) {
                                    0 -> r2fridaViewModel.loadOverview()
                                    1 -> r2fridaViewModel.loadLibraries()
                                    2 -> r2fridaViewModel.loadMappings()
                                    // 3 -> script tab: no auto-clear, persist state
                                    4 -> r2fridaViewModel.loadEntries()
                                    5 -> r2fridaViewModel.loadExports()
                                    6 -> r2fridaViewModel.loadStrings()
                                    7 -> r2fridaViewModel.loadSymbols()
                                    8 -> r2fridaViewModel.loadSections()
                                    9 -> r2fridaViewModel.loadCustomFunctions()
                                }
                            }

                            if (selectedR2FridaTabIndex < baseR2fridaTabs.size) {
                                when (selectedR2FridaTabIndex) {
                                    0 -> FridaOverviewScreen(fridaOverview)
                                    1 -> FridaLibraryList(fridaLibraries, fridaActions,
                                        cursorAddress = (uiState as? ProjectUiState.Success)?.cursorAddress ?: 0L,
                                        onSeek = { addr ->
                                            viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                                            r2fridaViewModel.clearNonLibraryCache()
                                        },
                                        onRefresh = { r2fridaViewModel.loadLibraries(true) },
                                        searchQuery = fridaLibrariesQuery,
                                        onSearchQueryChange = r2fridaViewModel::updateLibrariesSearchQuery,
                                        listState = fridaLibrariesListState)
                                    2 -> FridaMappingList(fridaMappings, fridaActions,
                                        cursorAddress = (uiState as? ProjectUiState.Success)?.cursorAddress ?: 0L,
                                        onSeek = { addr ->
                                            viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                                        },
                                        onRefresh = { r2fridaViewModel.loadMappings(true) },
                                        searchQuery = fridaMappingsQuery,
                                        onSearchQueryChange = r2fridaViewModel::updateMappingsSearchQuery,
                                        listState = fridaMappingsListState)
                                    3 -> FridaScriptScreen(
                                        logs = fridaScriptLogs,
                                        running = fridaScriptRunning,
                                        scriptContent = fridaScriptContent,
                                        currentScriptName = fridaCurrentScriptName,
                                        scriptFiles = fridaScriptFiles,
                                        onRun = { r2fridaViewModel.runScript(it) },
                                        onContentChange = { r2fridaViewModel.updateScriptContent(it) },
                                        onNewScript = { r2fridaViewModel.newScript() },
                                        onSaveScript = { name, content -> r2fridaViewModel.saveScript(name, content) },
                                        onOpenScript = { r2fridaViewModel.openScript(it) },
                                        onDeleteScript = { r2fridaViewModel.deleteScript(it) },
                                        onRefreshFiles = { r2fridaViewModel.refreshScriptFiles() }
                                    )
                                    4 -> FridaEntryList(fridaEntries, fridaActions,
                                        onRefresh = { r2fridaViewModel.loadEntries(true) },
                                        searchQuery = fridaEntriesQuery,
                                        onSearchQueryChange = r2fridaViewModel::updateEntriesSearchQuery,
                                        listState = fridaEntriesListState)
                                    5 -> FridaExportList(fridaExports, fridaActions,
                                        onRefresh = { r2fridaViewModel.loadExports(true) },
                                        searchHint = stringResource(R.string.r2frida_search_exports),
                                        searchQuery = fridaExportsQuery,
                                        onSearchQueryChange = r2fridaViewModel::updateExportsSearchQuery,
                                        listState = fridaExportsListState,
                                        showAutoDemangleToggle = true,
                                        autoDemangleEnabled = fridaAutoDemangleExports,
                                        onAutoDemangleChange = r2fridaViewModel::setAutoDemangleExports)
                                    6 -> FridaStringList(fridaStrings, fridaActions,
                                        onRefresh = { r2fridaViewModel.loadStrings(true) },
                                        searchQuery = fridaStringsQuery,
                                        onSearchQueryChange = r2fridaViewModel::updateStringsSearchQuery,
                                        listState = fridaStringsListState)
                                    7 -> FridaExportList(fridaSymbols, fridaActions,
                                        onRefresh = { r2fridaViewModel.loadSymbols(true) },
                                        searchHint = stringResource(R.string.r2frida_search_symbols),
                                        searchQuery = fridaSymbolsQuery,
                                        onSearchQueryChange = r2fridaViewModel::updateSymbolsSearchQuery,
                                        listState = fridaSymbolsListState)
                                    8 -> FridaExportList(fridaSections, fridaActions,
                                        onRefresh = { r2fridaViewModel.loadSections(true) },
                                        searchHint = stringResource(R.string.r2frida_search_sections),
                                        searchQuery = fridaSectionsQuery,
                                        onSearchQueryChange = r2fridaViewModel::updateSectionsSearchQuery,
                                        listState = fridaSectionsListState)
                                    9 -> FridaCustomFunctionsScreen(fridaCustomFunctions, fridaActions,
                                        onRefresh = { r2fridaViewModel.loadCustomFunctions(true) },
                                        searchQuery = fridaCustomFunctionsQuery,
                                        onSearchChange = r2fridaViewModel::updateCustomFunctionsSearchQuery,
                                        listState = fridaCustomFunctionsListState,
                                        autoDemangleEnabled = fridaAutoDemangleCustomFunctions,
                                        onAutoDemangleChange = r2fridaViewModel::setAutoDemangleCustomFunctions)
                                    10 -> FridaSearchScreen(
                                        results = fridaSearchResults,
                                        isSearching = fridaIsSearching,
                                        searchValueType = fridaSearchValueType,
                                        searchCompare = fridaSearchCompare,
                                        selectedRegions = fridaSelectedRegions,
                                        frozenAddresses = fridaFrozenAddresses,
                                        searchError = fridaSearchError,
                                        onSearch = { input, rMin, rMax -> r2fridaViewModel.performSearch(input, rMin, rMax) },
                                        onRefine = { mode, target, rMin, rMax, expr -> r2fridaViewModel.refineSearch(mode, target, rMin, rMax, expr) },
                                        onClear = { r2fridaViewModel.clearSearchResults() },
                                        onWriteValue = { addr, v -> r2fridaViewModel.writeValue(addr, v) },
                                        onBatchWrite = { v -> r2fridaViewModel.batchWriteAll(v) },
                                        onToggleFreeze = { addr, v -> r2fridaViewModel.toggleFreeze(addr, v) },
                                        onValueTypeChange = { r2fridaViewModel.updateSearchValueType(it) },
                                        onCompareChange = { r2fridaViewModel.updateSearchCompare(it) },
                                        onRegionsChange = { r2fridaViewModel.updateSelectedRegions(it) },
                                        onClearError = { r2fridaViewModel.clearSearchError() },
                                        onRefreshValues = { r2fridaViewModel.refreshSearchValues() },
                                        maxResults = fridaMaxResults,
                                        onMaxResultsChange = { r2fridaViewModel.updateMaxResults(it) },
                                        searchTimeoutSeconds = fridaSearchTimeoutSeconds,
                                        onSearchTimeoutChange = { r2fridaViewModel.updateSearchTimeoutSeconds(it) },
                                        actions = fridaActions
                                    )
                                    11 -> FridaMonitorScreen(
                                        monitors = fridaMonitors,
                                        onAddMonitor = { addr, size -> r2fridaViewModel.addMonitor(addr, size) },
                                        onRemoveMonitor = { r2fridaViewModel.removeMonitor(it) },
                                        onStartMonitor = { r2fridaViewModel.startMonitor(it) },
                                        onStopMonitor = { r2fridaViewModel.stopMonitor(it) },
                                        onFilterChange = { id, f -> r2fridaViewModel.updateMonitorFilter(id, f) },
                                        onClearEvents = { r2fridaViewModel.clearMonitorEvents(it) },
                                        actions = fridaActions,
                                        prefillAddress = fridaMonitorPrefill,
                                        onPrefillConsumed = { r2fridaViewModel.consumeMonitorPrefillAddress() }
                                    )
                                }
                            } else {
                                val pluginTab = pluginR2fridaTabs.getOrNull(selectedR2FridaTabIndex - baseR2fridaTabs.size)
                                RenderPluginInjectedTab(
                                    pluginTab = pluginTab,
                                    isR2Frida = isR2Frida,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        } // Box
            } // Column
        } // Row
    }

    // Command bottom sheet (swipe up from bottom bar)
    if (showCommandSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCommandSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            CommandBottomSheetContent(
                command = sheetCommand,
                onCommandChange = { sheetCommand = it },
                output = sheetOutput,
                isExecuting = sheetExecuting,
                onRun = {
                    if (sheetCommand.isNotBlank()) {
                        sheetExecuting = true
                        sheetScope.launch {
                            val result = R2PipeManager.execute(sheetCommand)
                            sheetOutput = result.getOrDefault("Error: ${result.exceptionOrNull()?.message}")
                            sheetExecuting = false
                        }
                    }
                }
            )
        }
    }

    // External export
    val exportContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(exportDecompCode) {
        val code = exportDecompCode ?: return@LaunchedEffect
        exportDecompCode = null
        try {
            val dir = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "R2droidExport"
            )
            dir.mkdirs()
            val fileName = "decompile_${System.currentTimeMillis()}.c"
            val file = java.io.File(dir, fileName)
            file.writeText(code)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                exportContext, "${exportContext.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/x-csrc")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            exportContext.startActivity(android.content.Intent.createChooser(intent, null))
        } catch (e: Exception) {
            android.widget.Toast.makeText(exportContext, e.message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun rememberPluginTabsForTarget(target: String): androidx.compose.runtime.State<List<PluginScreenTabDescriptor>> {
    val screenTabs by PluginManager.screenTabs.collectAsState()
    return remember(screenTabs, target) {
        androidx.compose.runtime.derivedStateOf {
            screenTabs.filter { it.target.equals(target, ignoreCase = true) }
        }
    }
}

@Composable
private fun RenderPluginInjectedTab(
    pluginTab: PluginScreenTabDescriptor?,
    isR2Frida: Boolean,
    modifier: Modifier = Modifier
) {
    if (pluginTab == null) {
        Text(
            text = "Plugin tab unavailable",
            modifier = modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    val visible = when (pluginTab.tab.visibleWhen.lowercase()) {
        "r2frida", "frida" -> isR2Frida
        else -> true
    }

    if (visible) {
        PluginPageRenderer(
            pluginId = pluginTab.pluginId,
            page = pluginTab.tab.page,
            modifier = modifier
        )
    } else {
        Text(
            text = "This tab is only available in r2frida session",
            modifier = modifier.padding(16.dp)
        )
    }
}

private fun buildCurrentTabAliases(
    selectedIndex: Int,
    allTitles: List<String>,
    baseKeys: List<String>,
    baseSize: Int,
    pluginKeys: List<String>
): Set<String> {
    val title = allTitles.getOrNull(selectedIndex)?.trim().orEmpty()
    if (title.isBlank()) return emptySet()
    val aliases = linkedSetOf(title, title.lowercase())
    if (selectedIndex < baseSize) {
        baseKeys.getOrNull(selectedIndex)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                aliases += it
                aliases += it.lowercase()
            }
    } else {
        pluginKeys.getOrNull(selectedIndex - baseSize)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                aliases += it
                aliases += it.lowercase()
            }
    }
    return aliases
}

private fun isPluginProjectActionVisible(
    descriptor: PluginProjectActionDescriptor,
    currentAliases: Set<String>
): Boolean {
    val visibleTabs = descriptor.action.visibleTabs
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf("*") }
    return visibleTabs.any { visible ->
        visible == "*" || currentAliases.any { alias -> alias.equals(visible, ignoreCase = true) }
    }
}

private fun pluginProjectActionIcon(icon: String): ImageVector {
    return when (icon.trim().lowercase()) {
        "play", "run" -> Icons.Default.PlayArrow
        "refresh", "reload" -> Icons.Default.Refresh
        "search" -> Icons.Default.Search
        "save" -> Icons.Default.Save
        "settings" -> Icons.Default.Settings
        "open", "open_in_new", "external" -> Icons.AutoMirrored.Filled.OpenInNew
        "edit" -> Icons.Default.Edit
        "delete", "remove" -> Icons.Default.Delete
        else -> Icons.Default.MoreVert
    }
}

private suspend fun executePluginProjectAction(
    context: Context,
    descriptor: PluginProjectActionDescriptor
) {
    val pluginId = descriptor.pluginId
    val functionName = descriptor.action.function?.trim().orEmpty()
    val scriptCode = PluginManager.resolvePluginTextReference(pluginId, descriptor.action.script)
        ?: PluginManager.findInstalledPlugin(pluginId)
            ?.manifest
            ?.entry
            ?.script
            ?.let { PluginManager.resolvePluginTextReference(pluginId, it) }
        ?: ""

    val result = when {
        functionName.isNotBlank() && scriptCode.isNotBlank() ->
            PluginRuntime.runPluginScriptFunction(pluginId, scriptCode, functionName)

        scriptCode.isNotBlank() ->
            PluginRuntime.runPluginScript(pluginId, scriptCode)

        else -> Result.failure(IllegalStateException("plugin action missing script or function"))
    }

    result.onSuccess { output ->
        val text = output.trim().takeIf { it.isNotBlank() && it != "(plugin script executed)" } ?: return@onSuccess
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    }.onFailure { throwable ->
        android.widget.Toast.makeText(
            context,
            throwable.message ?: "Plugin action failed",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
private fun ProjectSubTabs(
    selectedCategory: MainCategory,
    selectedListTabIndex: Int,
    onListTabSelected: (Int) -> Unit,
    selectedDetailTabIndex: Int,
    onDetailTabSelected: (Int) -> Unit,
    selectedProjectTabIndex: Int,
    onProjectTabSelected: (Int) -> Unit,
    selectedAiTabIndex: Int,
    onAiTabSelected: (Int) -> Unit,
    selectedR2FridaTabIndex: Int,
    onR2FridaTabSelected: (Int) -> Unit,
    listTabs: List<Int>,
    listTabTitles: List<String>,
    detailTabs: List<Int>,
    projectTabs: List<String>,
    detailTabTitles: List<String>,
    aiTabs: List<Int>,
    aiTabTitles: List<String>,
    r2fridaTabs: List<Int>,
    r2fridaTabTitles: List<String>
) {
    val indicator = @Composable { tabPositions: List<TabPosition>, idx: Int ->
        TabRowDefaults.SecondaryIndicator(
            modifier = Modifier.tabIndicatorOffset(tabPositions[idx]),
            color = MaterialTheme.colorScheme.primary
        )
    }
    when (selectedCategory) {
        MainCategory.List -> ScrollableTabRow(
            selectedTabIndex = selectedListTabIndex, edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { indicator(it, selectedListTabIndex) }
        ) {
            listTabTitles.forEachIndexed { i, t -> Tab(selectedListTabIndex == i, { onListTabSelected(i) }, text = { Text(t) }) }
        }
        MainCategory.Detail -> ScrollableTabRow(
            selectedTabIndex = selectedDetailTabIndex, edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { indicator(it, selectedDetailTabIndex) }
        ) {
            detailTabTitles.forEachIndexed { i, t ->
                Tab(selectedDetailTabIndex == i, { onDetailTabSelected(i) }, text = { Text(t) })
            }
        }
        MainCategory.Project -> TabRow(
            selectedTabIndex = selectedProjectTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { indicator(it, selectedProjectTabIndex) }
        ) {
            projectTabs.forEachIndexed { i, t -> Tab(selectedProjectTabIndex == i, { onProjectTabSelected(i) }, text = { Text(t) }) }
        }
        MainCategory.AI -> TabRow(
            selectedTabIndex = selectedAiTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { indicator(it, selectedAiTabIndex) }
        ) {
            aiTabTitles.forEachIndexed { i, t -> Tab(selectedAiTabIndex == i, { onAiTabSelected(i) }, text = { Text(t) }) }
        }
        MainCategory.R2Frida -> ScrollableTabRow(
            selectedTabIndex = selectedR2FridaTabIndex, edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { indicator(it, selectedR2FridaTabIndex) }
        ) {
            r2fridaTabTitles.forEachIndexed { i, t -> Tab(selectedR2FridaTabIndex == i, { onR2FridaTabSelected(i) }, text = { Text(t) }) }
        }
    }
}

@Composable
private fun CommandBottomSheetContent(
    command: String,
    onCommandChange: (String) -> Unit,
    output: String,
    isExecuting: Boolean,
    onRun: () -> Unit
) {
    var showSuggestions by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = stringResource(R.string.proj_command_panel_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (showSuggestions) {
            CommandSuggestionPanel(
                currentInput = command,
                onSelect = { onCommandChange(it); showSuggestions = false },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CommandSuggestButton(
                expanded = showSuggestions,
                onToggle = { showSuggestions = !showSuggestions }
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("e.g. iI") },
                label = { Text("Command") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onRun, enabled = !isExecuting) {
                if (isExecuting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Run")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val bg = colorResource(R.color.command_output_background)
        val fg = colorResource(R.color.command_output_text)
        val placeholder = colorResource(R.color.command_output_placeholder)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(bg, RoundedCornerShape(4.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SelectionContainer {
                Text(
                    text = output.ifEmpty { "No output" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (output.isEmpty()) placeholder else fg
                )
            }
        }
    }
}

@Composable
private fun ProjectBottomBar(
    selectedCategory: MainCategory,
    onCategorySelected: (MainCategory) -> Unit,
    selectedListTabIndex: Int,
    onListTabSelected: (Int) -> Unit,
    selectedDetailTabIndex: Int,
    onDetailTabSelected: (Int) -> Unit,
    selectedProjectTabIndex: Int,
    onProjectTabSelected: (Int) -> Unit,
    selectedAiTabIndex: Int,
    onAiTabSelected: (Int) -> Unit,
    selectedR2FridaTabIndex: Int,
    onR2FridaTabSelected: (Int) -> Unit,
    listTabs: List<Int>,
    listTabTitles: List<String>,
    detailTabs: List<Int>,
    projectTabs: List<String>,
    detailTabTitles: List<String>,
    aiTabs: List<Int>,
    aiTabTitles: List<String>,
    r2fridaTabs: List<Int>,
    r2fridaTabTitles: List<String>,
    isR2Frida: Boolean,
    onSwipeUpCommand: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp
    ) {
        Column {
            ProjectSubTabs(
                selectedCategory = selectedCategory,
                selectedListTabIndex = selectedListTabIndex,
                onListTabSelected = onListTabSelected,
                selectedDetailTabIndex = selectedDetailTabIndex,
                onDetailTabSelected = onDetailTabSelected,
                selectedProjectTabIndex = selectedProjectTabIndex,
                onProjectTabSelected = onProjectTabSelected,
                selectedAiTabIndex = selectedAiTabIndex,
                onAiTabSelected = onAiTabSelected,
                selectedR2FridaTabIndex = selectedR2FridaTabIndex,
                onR2FridaTabSelected = onR2FridaTabSelected,
                listTabs = listTabs,
                listTabTitles = listTabTitles,
                detailTabs = detailTabs,
                projectTabs = projectTabs,
                detailTabTitles = detailTabTitles,
                aiTabs = aiTabs,
                aiTabTitles = aiTabTitles,
                r2fridaTabs = r2fridaTabs,
                r2fridaTabTitles = r2fridaTabTitles
            )
            NavigationBar(
                modifier = Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -40) onSwipeUpCommand()
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                MainCategory.entries
                    .filter { it != MainCategory.R2Frida || isR2Frida }
                    .filter { it != MainCategory.AI || SettingsManager.aiEnabled }
                    .forEach { category ->
                        NavigationBarItem(
                            selected = selectedCategory == category,
                            onClick = { onCategorySelected(category) },
                            icon = { Icon(category.icon, contentDescription = stringResource(category.titleRes)) },
                            label = { Text(stringResource(category.titleRes)) }
                        )
                    }
            }
        }
    }
}

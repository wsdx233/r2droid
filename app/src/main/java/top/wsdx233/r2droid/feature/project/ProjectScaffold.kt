package top.wsdx233.r2droid.feature.project

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.foundation.layout.fillMaxHeight
import top.wsdx233.r2droid.core.ui.adaptive.LocalWindowWidthClass
import top.wsdx233.r2droid.core.ui.adaptive.WindowWidthClass
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import top.wsdx233.r2droid.feature.ai.ui.AiPromptsScreen
import top.wsdx233.r2droid.feature.ai.ui.AiProviderSettingsScreen
import top.wsdx233.r2droid.feature.terminal.ui.CommandScreen
import top.wsdx233.r2droid.core.ui.components.CommandSuggestButton
import top.wsdx233.r2droid.core.ui.components.CommandSuggestionPanel
import kotlinx.coroutines.delay
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.feature.r2frida.R2FridaViewModel
import top.wsdx233.r2droid.feature.r2frida.ui.*
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.SelectAll

enum class MainCategory(@StringRes val titleRes: Int, val icon: ImageVector) {
    List(R.string.proj_category_list, Icons.Filled.List),
    Detail(R.string.proj_category_detail, Icons.Filled.Info),
    R2Frida(R.string.proj_category_r2frida, Icons.Filled.BugReport),
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
    r2fridaViewModel: R2FridaViewModel = hiltViewModel(),
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
    var selectedR2FridaTabIndex by remember { mutableIntStateOf(0) }
    var showJumpDialog by remember { mutableStateOf(false) }
    val isR2Frida = R2PipeManager.isR2FridaSession
    val isAiEnabled = top.wsdx233.r2droid.data.SettingsManager.aiEnabled
    val isWide = LocalWindowWidthClass.current != WindowWidthClass.Compact

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

    val listTabs = listOf(
        R.string.proj_tab_overview, R.string.proj_tab_search, R.string.proj_tab_sections, R.string.proj_tab_symbols,
        R.string.proj_tab_imports, R.string.proj_tab_relocs, R.string.proj_tab_strings, R.string.proj_tab_functions
    )
    val detailTabs = listOf(R.string.proj_tab_hex, R.string.proj_tab_disassembly, R.string.proj_tab_decompile, R.string.proj_tab_graph)
    val projectTabs = listOf(R.string.proj_tab_settings, R.string.proj_tab_cmd, R.string.proj_tab_logs)
    val aiTabs = listOf(R.string.ai_tab_chat, R.string.ai_tab_settings, R.string.ai_tab_prompts)
    val r2fridaTabs = listOf(
        R.string.r2frida_tab_overview, R.string.r2frida_tab_libraries, R.string.r2frida_tab_scripts,
        R.string.r2frida_tab_entries, R.string.r2frida_tab_exports, R.string.r2frida_tab_strings,
        R.string.r2frida_tab_symbols, R.string.r2frida_tab_sections
    )

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
                                    listOf("r2ghidra", "jsdec", "native", "aipdg").forEach { type ->
                                        val labelRes = when (type) {
                                            "r2ghidra" -> R.string.decompiler_r2ghidra
                                            "jsdec" -> R.string.decompiler_jsdec
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
                                }
                            }
                        }
                        // Multi-select actions for disasm tab
                        val disasmMultiSelect by disasmViewModel.multiSelectState.collectAsState()
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
            if (!isWide) {
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
                    listTabs = listTabs,
                    detailTabs = detailTabs,
                    projectTabs = projectTabs,
                    aiTabs = aiTabs,
                    r2fridaTabs = r2fridaTabs,
                    isR2Frida = isR2Frida,
                    onSwipeUpCommand = { showCommandSheet = true }
                )
            }
        }
    ) { paddingValues ->
        Row(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // NavigationRail for wide screens
            if (isWide) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Spacer(Modifier.height(8.dp))
                    MainCategory.values()
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
                        listTabs = listTabs,
                        detailTabs = detailTabs,
                        projectTabs = projectTabs,
                        aiTabs = aiTabs,
                        r2fridaTabs = r2fridaTabs
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
                            ProjectListView(
                                tabIndex = selectedListTabIndex,
                                onNavigateToDetail = { addr, tabIdx ->
                                    selectedCategory = MainCategory.Detail
                                    selectedDetailTabIndex = tabIdx
                                    viewModel.currentDetailTab = tabIdx
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
                                2 -> AiPromptsScreen()
                            }
                        }
                        MainCategory.R2Frida -> {
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            val fridaActions = remember(clipboardManager) {
                                top.wsdx233.r2droid.core.ui.components.ListItemActions(
                                    onCopy = { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it)) },
                                    onJumpToHex = { addr ->
                                        selectedCategory = MainCategory.Detail
                                        selectedDetailTabIndex = 0
                                        viewModel.currentDetailTab = 0
                                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                                    },
                                    onJumpToDisasm = { addr ->
                                        selectedCategory = MainCategory.Detail
                                        selectedDetailTabIndex = 1
                                        viewModel.currentDetailTab = 1
                                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                                    },
                                    onShowXrefs = { addr ->
                                        disasmViewModel.onEvent(top.wsdx233.r2droid.feature.disasm.DisasmEvent.FetchXrefs(addr))
                                    }
                                )
                            }

                            val fridaOverview by r2fridaViewModel.overview.collectAsState()
                            val fridaLibraries by r2fridaViewModel.libraries.collectAsState()
                            val fridaEntries by r2fridaViewModel.entries.collectAsState()
                            val fridaExports by r2fridaViewModel.exports.collectAsState()
                            val fridaStrings by r2fridaViewModel.strings.collectAsState()
                            val fridaSymbols by r2fridaViewModel.symbols.collectAsState()
                            val fridaSections by r2fridaViewModel.sections.collectAsState()
                            val fridaScriptLogs by r2fridaViewModel.scriptLogs.collectAsState()
                            val fridaScriptRunning by r2fridaViewModel.scriptRunning.collectAsState()

                            androidx.compose.runtime.LaunchedEffect(selectedR2FridaTabIndex) {
                                when (selectedR2FridaTabIndex) {
                                    0 -> r2fridaViewModel.loadOverview()
                                    1 -> r2fridaViewModel.loadLibraries()
                                    2 -> r2fridaViewModel.clearScriptLogs()
                                    3 -> r2fridaViewModel.loadEntries()
                                    4 -> r2fridaViewModel.loadExports()
                                    5 -> r2fridaViewModel.loadStrings()
                                    6 -> r2fridaViewModel.loadSymbols()
                                    7 -> r2fridaViewModel.loadSections()
                                }
                            }

                            when (selectedR2FridaTabIndex) {
                                0 -> FridaOverviewScreen(fridaOverview)
                                1 -> FridaLibraryList(fridaLibraries, fridaActions,
                                    cursorAddress = (uiState as? ProjectUiState.Success)?.cursorAddress ?: 0L,
                                    onSeek = { addr ->
                                        viewModel.onEvent(ProjectEvent.JumpToAddress(addr))
                                        r2fridaViewModel.clearNonLibraryCache()
                                    },
                                    onRefresh = { r2fridaViewModel.loadLibraries(true) })
                                2 -> FridaScriptScreen(fridaScriptLogs, fridaScriptRunning,
                                    onRun = { r2fridaViewModel.runScript(it) })
                                3 -> FridaEntryList(fridaEntries, fridaActions,
                                    onRefresh = { r2fridaViewModel.loadEntries(true) })
                                4 -> FridaExportList(fridaExports, fridaActions,
                                    onRefresh = { r2fridaViewModel.loadExports(true) },
                                    searchHint = stringResource(R.string.r2frida_search_exports))
                                5 -> FridaStringList(fridaStrings, fridaActions,
                                    onRefresh = { r2fridaViewModel.loadStrings(true) })
                                6 -> FridaExportList(fridaSymbols, fridaActions,
                                    onRefresh = { r2fridaViewModel.loadSymbols(true) },
                                    searchHint = stringResource(R.string.r2frida_search_symbols))
                                7 -> FridaExportList(fridaSections, fridaActions,
                                    onRefresh = { r2fridaViewModel.loadSections(true) },
                                    searchHint = stringResource(R.string.r2frida_search_sections))
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
    detailTabs: List<Int>,
    projectTabs: List<Int>,
    aiTabs: List<Int>,
    r2fridaTabs: List<Int>
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
            listTabs.forEachIndexed { i, t -> Tab(selectedListTabIndex == i, { onListTabSelected(i) }, text = { Text(stringResource(t)) }) }
        }
        MainCategory.Detail -> ScrollableTabRow(
            selectedTabIndex = selectedDetailTabIndex, edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { indicator(it, selectedDetailTabIndex) }
        ) {
            detailTabs.forEachIndexed { i, t -> Tab(selectedDetailTabIndex == i, { onDetailTabSelected(i) }, text = { Text(stringResource(t)) }) }
        }
        MainCategory.Project -> TabRow(
            selectedTabIndex = selectedProjectTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { indicator(it, selectedProjectTabIndex) }
        ) {
            projectTabs.forEachIndexed { i, t -> Tab(selectedProjectTabIndex == i, { onProjectTabSelected(i) }, text = { Text(stringResource(t)) }) }
        }
        MainCategory.AI -> TabRow(
            selectedTabIndex = selectedAiTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { indicator(it, selectedAiTabIndex) }
        ) {
            aiTabs.forEachIndexed { i, t -> Tab(selectedAiTabIndex == i, { onAiTabSelected(i) }, text = { Text(stringResource(t)) }) }
        }
        MainCategory.R2Frida -> ScrollableTabRow(
            selectedTabIndex = selectedR2FridaTabIndex, edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { indicator(it, selectedR2FridaTabIndex) }
        ) {
            r2fridaTabs.forEachIndexed { i, t -> Tab(selectedR2FridaTabIndex == i, { onR2FridaTabSelected(i) }, text = { Text(stringResource(t)) }) }
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
    detailTabs: List<Int>,
    projectTabs: List<Int>,
    aiTabs: List<Int>,
    r2fridaTabs: List<Int>,
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
                detailTabs = detailTabs,
                projectTabs = projectTabs,
                aiTabs = aiTabs,
                r2fridaTabs = r2fridaTabs
            )
            NavigationBar(
                modifier = Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -40) onSwipeUpCommand()
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                MainCategory.values()
                    .filter { it != MainCategory.R2Frida || isR2Frida }
                    .filter { it != MainCategory.AI || top.wsdx233.r2droid.data.SettingsManager.aiEnabled }
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

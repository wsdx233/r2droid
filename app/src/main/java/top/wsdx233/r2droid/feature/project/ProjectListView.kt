package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.ListItemActions
import top.wsdx233.r2droid.feature.bininfo.ui.FunctionList
import top.wsdx233.r2droid.feature.bininfo.ui.ImportList
import top.wsdx233.r2droid.feature.bininfo.ui.OverviewCard
import top.wsdx233.r2droid.feature.bininfo.ui.RelocationList
import top.wsdx233.r2droid.feature.bininfo.ui.SectionList
import top.wsdx233.r2droid.feature.bininfo.ui.StringList
import top.wsdx233.r2droid.feature.bininfo.ui.SymbolList
import top.wsdx233.r2droid.feature.disasm.DisasmEvent
import top.wsdx233.r2droid.feature.disasm.DisasmViewModel
import top.wsdx233.r2droid.feature.search.ui.SearchScreen

@Composable
fun ProjectListView(
    viewModel: ProjectViewModel = hiltViewModel(),
    disasmViewModel: DisasmViewModel = hiltViewModel(),
    tabIndex: Int,
    searchQueries: SnapshotStateMap<Int, String>,
    listStates: SnapshotStateMap<Int, LazyListState>,
    onNavigateToDetail: (Long, Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState as? ProjectUiState.Success ?: return

    // Helper to get or create LazyListState for a tab
    fun getListState(tab: Int): LazyListState {
        return listStates.getOrPut(tab) { LazyListState() }
    }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    val listItemActions = remember(viewModel, disasmViewModel, clipboardManager) {
        ListItemActions(
            onCopy = { text ->
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
            },
            onJumpToHex = { addr ->
                onNavigateToDetail(addr, 0)
            },
            onJumpToDisasm = { addr ->
                onNavigateToDetail(addr, 1)
            },
            onShowXrefs = { addr ->
                disasmViewModel.onEvent(DisasmEvent.FetchXrefs(addr))
            }
        )
    }

    val functionListActions = remember(viewModel, disasmViewModel, clipboardManager) {
        ListItemActions(
            onCopy = { text ->
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
            },
            onJumpToHex = { addr ->
                onNavigateToDetail(addr, 0)
            },
            onJumpToDisasm = { addr ->
                onNavigateToDetail(addr, 1)
            },
            onShowXrefs = { addr ->
                disasmViewModel.onEvent(DisasmEvent.FetchXrefs(addr))
            },
            onAnalyzeFunction = { addr ->
                disasmViewModel.onEvent(DisasmEvent.AnalyzeFunction(addr))
            },
            onFunctionInfo = { addr ->
                disasmViewModel.onEvent(DisasmEvent.FetchFunctionInfo(addr))
            },
            onFunctionXrefs = { addr ->
                disasmViewModel.onEvent(DisasmEvent.FetchFunctionXrefs(addr))
            },
            onFunctionVariables = { addr ->
                disasmViewModel.onEvent(DisasmEvent.FetchFunctionVariables(addr))
            }
        )
    }

    androidx.compose.runtime.LaunchedEffect(tabIndex) {
        when (tabIndex) {
            2 -> viewModel.onEvent(ProjectEvent.LoadSections())
            3 -> viewModel.onEvent(ProjectEvent.LoadSymbols())
            4 -> viewModel.onEvent(ProjectEvent.LoadImports())
            5 -> viewModel.onEvent(ProjectEvent.LoadRelocations())
            6 -> viewModel.onEvent(ProjectEvent.LoadStrings())
            7 -> viewModel.onEvent(ProjectEvent.LoadFunctions())
        }
    }

    when (tabIndex) {
        0 -> state.binInfo?.let { OverviewCard(it) }
            ?: Text(stringResource(R.string.hex_no_data), Modifier.fillMaxSize())
        1 -> SearchScreen(actions = listItemActions)
        2 -> if (state.sections == null) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else SectionList(state.sections, listItemActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadSections(forceRefresh = true)) },
                searchQuery = searchQueries[2] ?: "",
                onSearchQueryChange = { searchQueries[2] = it },
                listState = getListState(2))
        3 -> if (state.symbols == null) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else SymbolList(state.symbols, listItemActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadSymbols(forceRefresh = true)) },
                searchQuery = searchQueries[3] ?: "",
                onSearchQueryChange = { searchQueries[3] = it },
                listState = getListState(3))
        4 -> if (state.imports == null) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else ImportList(state.imports, listItemActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadImports(forceRefresh = true)) },
                searchQuery = searchQueries[4] ?: "",
                onSearchQueryChange = { searchQueries[4] = it },
                listState = getListState(4))
        5 -> if (state.relocations == null) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else RelocationList(state.relocations, listItemActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadRelocations(forceRefresh = true)) },
                searchQuery = searchQueries[5] ?: "",
                onSearchQueryChange = { searchQueries[5] = it },
                listState = getListState(5))
        6 -> if (state.strings == null) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else StringList(state.strings, listItemActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadStrings(forceRefresh = true)) },
                searchQuery = searchQueries[6] ?: "",
                onSearchQueryChange = { searchQueries[6] = it },
                listState = getListState(6))
        7 -> if (state.functions == null) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else FunctionList(state.functions, functionListActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadFunctions(forceRefresh = true)) },
                searchQuery = searchQueries[7] ?: "",
                onSearchQueryChange = { searchQueries[7] = it },
                listState = getListState(7))
    }
}

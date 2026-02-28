package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState

import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.wsdx233.r2droid.R
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.core.ui.components.ListItemActions
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.feature.bininfo.ui.OverviewCard
import top.wsdx233.r2droid.feature.bininfo.ui.PagingSectionList
import top.wsdx233.r2droid.feature.bininfo.ui.PagingSymbolList
import top.wsdx233.r2droid.feature.bininfo.ui.PagingImportList
import top.wsdx233.r2droid.feature.bininfo.ui.PagingRelocationList
import top.wsdx233.r2droid.feature.bininfo.ui.PagingStringList
import top.wsdx233.r2droid.feature.bininfo.ui.PagingFunctionList
import top.wsdx233.r2droid.feature.disasm.DisasmEvent
import top.wsdx233.r2droid.feature.disasm.DisasmViewModel
import top.wsdx233.r2droid.feature.search.ui.SearchScreen

@Composable
fun ProjectListView(
    viewModel: ProjectViewModel = hiltViewModel(),
    disasmViewModel: DisasmViewModel = hiltViewModel(),
    tabIndex: Int,
    overviewScrollState: ScrollState? = null,
    searchResultListState: LazyListState? = null,
    sectionsListState: LazyListState? = null,
    symbolsListState: LazyListState? = null,
    importsListState: LazyListState? = null,
    relocationsListState: LazyListState? = null,
    stringsListState: LazyListState? = null,
    functionsListState: LazyListState? = null,
    onNavigateToDetail: (Long, Int) -> Unit,
    onQuickNavigateToDetail: (Long) -> Unit,
    onMarkVisited: (Long) -> Unit,
    isVisited: (Long) -> Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState as? ProjectUiState.Success ?: return

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
            onQuickJump = { addr -> onQuickNavigateToDetail(addr) },
            onMarkVisited = onMarkVisited,
            isVisited = isVisited,
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
            onQuickJump = { addr -> onQuickNavigateToDetail(addr) },
            onMarkVisited = onMarkVisited,
            isVisited = isVisited,
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
        0 -> state.binInfo?.let {
            OverviewCard(
                info = it,
                actions = listItemActions,
                scrollState = overviewScrollState ?: rememberScrollState()
            )
        }
            ?: if (R2PipeManager.isR2FridaSession) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.r2frida_no_overview),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp))
                }
            } else Text(stringResource(R.string.hex_no_data), Modifier.fillMaxSize())
        1 -> SearchScreen(actions = listItemActions, resultListState = searchResultListState)
        2 -> {
            val syncing by viewModel.sectionsSyncing.collectAsState()
            val query by viewModel.sectionsSearchQuery.collectAsState()
            if (state.sections == null || syncing) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else PagingSectionList(viewModel.sectionsPagingData, listItemActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadSections(forceRefresh = true)) },
                searchQuery = query,
                onSearchQueryChange = { viewModel.updateSectionsSearchQuery(it) },
                listState = sectionsListState)
        }
        3 -> {
            val syncing by viewModel.symbolsSyncing.collectAsState()
            val query by viewModel.symbolsSearchQuery.collectAsState()
            if (state.symbols == null || syncing) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else PagingSymbolList(viewModel.symbolsPagingData, listItemActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadSymbols(forceRefresh = true)) },
                searchQuery = query,
                onSearchQueryChange = { viewModel.updateSymbolsSearchQuery(it) },
                listState = symbolsListState)
        }
        4 -> {
            val syncing by viewModel.importsSyncing.collectAsState()
            val query by viewModel.importsSearchQuery.collectAsState()
            if (state.imports == null || syncing) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else PagingImportList(viewModel.importsPagingData, listItemActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadImports(forceRefresh = true)) },
                searchQuery = query,
                onSearchQueryChange = { viewModel.updateImportsSearchQuery(it) },
                listState = importsListState)
        }
        5 -> {
            val syncing by viewModel.relocationsSyncing.collectAsState()
            val query by viewModel.relocationsSearchQuery.collectAsState()
            if (state.relocations == null || syncing) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else PagingRelocationList(viewModel.relocationsPagingData, listItemActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadRelocations(forceRefresh = true)) },
                searchQuery = query,
                onSearchQueryChange = { viewModel.updateRelocationsSearchQuery(it) },
                listState = relocationsListState)
        }
        6 -> {
            val stringsSyncing by viewModel.stringsSyncing.collectAsState()
            val stringsSearchQuery by viewModel.stringsSearchQuery.collectAsState()
            if (state.strings == null || stringsSyncing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                PagingStringList(
                    pagingData = viewModel.stringsPagingData,
                    actions = listItemActions,
                    onRefresh = { viewModel.onEvent(ProjectEvent.LoadStrings(forceRefresh = true)) },
                    searchQuery = stringsSearchQuery,
                    onSearchQueryChange = { viewModel.updateStringsSearchQuery(it) },
                    listState = stringsListState
                )
            }
        }
        7 -> {
            val syncing by viewModel.functionsSyncing.collectAsState()
            val query by viewModel.functionsSearchQuery.collectAsState()
            if (state.functions == null || syncing) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else PagingFunctionList(viewModel.functionsPagingData, functionListActions,
                onRefresh = { viewModel.onEvent(ProjectEvent.LoadFunctions(forceRefresh = true)) },
                searchQuery = query,
                onSearchQueryChange = { viewModel.updateFunctionsSearchQuery(it) },
                listState = functionsListState)
        }
    }
}

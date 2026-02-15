package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.wsdx233.r2droid.feature.decompiler.ui.DecompilationViewer
import top.wsdx233.r2droid.feature.disasm.DisasmEvent
import top.wsdx233.r2droid.feature.disasm.DisasmViewModel
import top.wsdx233.r2droid.feature.disasm.ui.DisassemblyViewer
import top.wsdx233.r2droid.feature.graph.ui.GraphScreen
import top.wsdx233.r2droid.feature.hex.HexEvent
import top.wsdx233.r2droid.feature.hex.HexViewModel
import top.wsdx233.r2droid.feature.hex.ui.HexViewer
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.core.ui.dialogs.InstructionDetailDialog

@Composable
fun ProjectDetailView(
    viewModel: ProjectViewModel = hiltViewModel(),
    hexViewModel: HexViewModel = hiltViewModel(),
    disasmViewModel: DisasmViewModel = hiltViewModel(),
    tabIndex: Int
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState as? ProjectUiState.Success ?: return

    val hexDataManager by hexViewModel.hexDataManagerState.collectAsState()
    val disasmDataManager by disasmViewModel.disasmDataManagerState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(tabIndex) {
        when (tabIndex) {
            0 -> {
                val sections = state.sections ?: emptyList()
                val path = R2PipeManager.currentFilePath
                val cursor = state.cursorAddress
                hexViewModel.onEvent(HexEvent.LoadHex(sections, path, cursor))
            }
            1 -> {
                val sections = state.sections ?: emptyList()
                val path = R2PipeManager.currentFilePath
                val cursor = state.cursorAddress
                disasmViewModel.onEvent(DisasmEvent.LoadDisassembly(sections, path, cursor))
            }
            2 -> viewModel.onEvent(ProjectEvent.LoadDecompilation)
            3 -> viewModel.onEvent(ProjectEvent.LoadGraph(state.graphType))
        }
    }
    
    androidx.compose.runtime.LaunchedEffect(state.cursorAddress) {
        val cursor = state.cursorAddress
        if (tabIndex == 0) hexViewModel.onEvent(HexEvent.PreloadHex(cursor))
        if (tabIndex == 1) disasmViewModel.onEvent(DisasmEvent.Preload(cursor))
        if (tabIndex == 3) viewModel.onEvent(ProjectEvent.LoadGraph(state.graphType))
    }
    
    when(tabIndex) {
        0 -> {
            if (hexDataManager == null) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val sections = state.sections ?: emptyList()
                    val path = R2PipeManager.currentFilePath
                    val cursor = state.cursorAddress
                    hexViewModel.onEvent(HexEvent.LoadHex(sections, path, cursor))
                }
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                HexViewer(
                    viewModel = hexViewModel,
                    cursorAddress = state.cursorAddress,
                    scrollToSelectionTrigger = viewModel.scrollToSelectionTrigger,
                    onByteClick = { addr -> viewModel.onEvent(ProjectEvent.UpdateCursor(addr)) },
                    onShowXrefs = { addr -> disasmViewModel.onEvent(DisasmEvent.FetchXrefs(addr)) }
                )
            }
        }
        1 -> {
            if (disasmDataManager == null) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val sections = state.sections ?: emptyList()
                    val path = R2PipeManager.currentFilePath
                    val cursor = state.cursorAddress
                    disasmViewModel.onEvent(DisasmEvent.LoadDisassembly(sections, path, cursor))
                }
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                DisassemblyViewer(
                    viewModel = disasmViewModel,
                    cursorAddress = state.cursorAddress,
                    scrollToSelectionTrigger = viewModel.scrollToSelectionTrigger,
                    onInstructionClick = { addr -> viewModel.onEvent(ProjectEvent.UpdateCursor(addr)) }
                )
            }
        }
        2 -> {
            if (state.decompilation == null) {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                DecompilationViewer(
                    viewModel = viewModel,
                    data = state.decompilation,
                    cursorAddress = state.cursorAddress,
                    onAddressClick = { addr -> viewModel.onEvent(ProjectEvent.UpdateCursor(addr)) },
                    onJumpAndDecompile = { addr -> viewModel.onEvent(ProjectEvent.JumpAndDecompile(addr)) }
                )
            }
        }
        3 -> {
            GraphScreen(
                graphData = state.graphData,
                graphLoading = state.graphLoading,
                currentGraphType = state.graphType,
                cursorAddress = state.cursorAddress,
                scrollToSelectionTrigger = viewModel.scrollToSelectionTrigger,
                onGraphTypeSelected = { type ->
                    viewModel.onEvent(ProjectEvent.LoadGraph(type))
                },
                onAddressClick = { addr -> viewModel.onEvent(ProjectEvent.JumpToAddress(addr)) },
                onShowXrefs = { addr -> disasmViewModel.onEvent(DisasmEvent.FetchXrefs(addr)) },
                onShowInstructionDetail = { addr -> disasmViewModel.onEvent(DisasmEvent.FetchInstructionDetail(addr)) }
            )
        }
    }

    // Global dialogs - rendered on any tab when triggered from graph views
    // XrefsDialog is handled globally in ProjectScaffold
    // InstructionDetailDialog needs to be shown from graph tab too
    if (tabIndex != 1) {
        val instrDetailState by disasmViewModel.instructionDetailState.collectAsState()
        if (instrDetailState.visible) {
            InstructionDetailDialog(
                detail = instrDetailState.data,
                isLoading = instrDetailState.isLoading,
                targetAddress = instrDetailState.targetAddress,
                onDismiss = { disasmViewModel.onEvent(DisasmEvent.DismissInstructionDetail) },
                onJump = { addr -> viewModel.onEvent(ProjectEvent.JumpToAddress(addr)) }
            )
        }
    }
}

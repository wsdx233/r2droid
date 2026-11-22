package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.wsdx233.r2droid.core.ui.dialogs.InstructionDetailDialog
import top.wsdx233.r2droid.feature.annotations.AnnotationAction
import top.wsdx233.r2droid.feature.decompiler.ui.DecompilationViewer
import top.wsdx233.r2droid.feature.disasm.DisasmEvent
import top.wsdx233.r2droid.feature.disasm.DisasmViewModel
import top.wsdx233.r2droid.feature.disasm.ui.DisassemblyViewer
import top.wsdx233.r2droid.feature.disasm.ui.functionMenuActions
import top.wsdx233.r2droid.feature.graph.ui.GraphScreen
import top.wsdx233.r2droid.feature.hex.HexEvent
import top.wsdx233.r2droid.feature.hex.HexViewModel
import top.wsdx233.r2droid.feature.hex.ui.HexViewer
import top.wsdx233.r2droid.util.R2PipeManager

@Composable
fun ProjectDetailView(
    viewModel: ProjectViewModel = hiltViewModel(),
    hexViewModel: HexViewModel = hiltViewModel(),
    disasmViewModel: DisasmViewModel = hiltViewModel(),
    tabIndex: Int,
    onAnnotate: (AnnotationAction, Long) -> Unit,
    onNavigateToR2Frida: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState as? ProjectUiState.Success ?: return

    val hexDataManager = hexViewModel.hexDataManagerState.collectAsState().value
    val disasmDataManager by disasmViewModel.disasmDataManagerState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(tabIndex) {
        when (tabIndex) {
            2 -> viewModel.onEvent(ProjectEvent.LoadDecompilation)
            3 -> viewModel.onEvent(ProjectEvent.LoadGraph(state.graphType))
        }
    }
    
    androidx.compose.runtime.LaunchedEffect(state.cursorAddress) {
        val cursor = state.cursorAddress
        if (tabIndex == 1) disasmViewModel.onEvent(DisasmEvent.Preload(cursor))
        if (tabIndex == 2) viewModel.onEvent(ProjectEvent.LoadDecompilation)
        if (tabIndex == 3) viewModel.onEvent(ProjectEvent.LoadGraph(state.graphType))
    }
    
    when(tabIndex) {
        0 -> {
            androidx.compose.runtime.LaunchedEffect(state.sessionToken, hexDataManager == null) {
                if (state.sessionToken === R2PipeManager.activeSessionPipe) {
                    hexViewModel.onEvent(HexEvent.LoadHex(state.sections.orEmpty(), R2PipeManager.currentFilePath, state.cursorAddress))
                }
            }
            if (hexDataManager == null || hexDataManager !== hexViewModel.hexDataManager ||
                state.sessionToken !== R2PipeManager.activeSessionPipe
            ) {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else key(hexDataManager) {
                HexViewer(
                    viewModel = hexViewModel,
                    hexDataManager = hexDataManager,
                    cursorAddress = state.cursorAddress,
                    scrollToSelectionTrigger = viewModel.scrollToSelectionTrigger,
                    onByteClick = { addr -> viewModel.onEvent(ProjectEvent.UpdateCursor(addr)) },
                    onShowXrefs = { addr -> disasmViewModel.onEvent(DisasmEvent.FetchXrefs(addr)) },
                    functionActions = remember(disasmViewModel) { disasmViewModel.functionMenuActions() },
                    onAnnotate = onAnnotate
                )
            }
        }
        1 -> {
            val layout = state.disasmLayout
            androidx.compose.runtime.LaunchedEffect(state.sessionToken, layout) {
                if (layout != null) {
                    disasmViewModel.onEvent(DisasmEvent.LoadDisassembly(state.sessionToken, layout, state.cursorAddress))
                }
            }
            if (layout == null || state.sessionToken !== R2PipeManager.activeSessionPipe ||
                disasmDataManager == null || disasmDataManager !== disasmViewModel.disasmDataManager
            ) {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                DisassemblyViewer(
                    viewModel = disasmViewModel,
                    cursorAddress = state.cursorAddress,
                    scrollToSelectionTrigger = viewModel.scrollToSelectionTrigger,
                    onInstructionClick = { addr -> viewModel.onEvent(ProjectEvent.UpdateCursor(addr)) },
                    onAnnotate = onAnnotate,
                    onNavigateToR2Frida = onNavigateToR2Frida
                )
            }
        }
        2 -> {
            val soraMode by viewModel.decompilerSoraMode.collectAsState()
            if (state.decompilation == null) {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (soraMode) {
                val code = state.decompilation.code
                top.wsdx233.r2droid.core.ui.components.SoraCodeEditor(
                    modifier = Modifier.fillMaxSize(),
                    scopeName = "source.cpp",
                    onEditorReady = { editor ->
                        editor.setText(code)
                        editor.isEditable = false
                    }
                )
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
                onNodeClick = { node -> viewModel.jumpToGraphNode(node) },
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

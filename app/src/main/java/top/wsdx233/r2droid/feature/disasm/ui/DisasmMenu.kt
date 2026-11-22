package top.wsdx233.r2droid.feature.disasm.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.core.ui.components.AddressAnnotationMenuItems
import top.wsdx233.r2droid.core.ui.components.DefineMenuItems
import top.wsdx233.r2droid.core.ui.components.FunctionMenuActions
import top.wsdx233.r2droid.core.ui.components.FunctionMenuItems
import top.wsdx233.r2droid.core.ui.components.OperandMenuItems
import top.wsdx233.r2droid.core.ui.components.SubmenuBackItem
import top.wsdx233.r2droid.core.ui.components.SubmenuItem
import top.wsdx233.r2droid.feature.annotations.AnnotationAction
import top.wsdx233.r2droid.feature.disasm.DisasmEvent
import top.wsdx233.r2droid.feature.disasm.DisasmViewModel

internal fun DisasmViewModel.functionMenuActions() = FunctionMenuActions(
    onAnalyze = { onEvent(DisasmEvent.AnalyzeFunction(it)) },
    onInfo = { onEvent(DisasmEvent.FetchFunctionInfo(it)) },
    onXrefs = { onEvent(DisasmEvent.FetchFunctionXrefs(it)) },
    onVariables = { onEvent(DisasmEvent.FetchFunctionVariables(it)) }
)

@Composable
fun DisasmContextMenu(
    expanded: Boolean,
    address: Long,
    instr: DisasmInstruction?,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onModify: (String) -> Unit, // type: hex, string, asm
    onXrefs: () -> Unit,
    onCustomCommand: () -> Unit,
    functionActions: FunctionMenuActions,
    onAnnotate: (AnnotationAction, Long) -> Unit,
    onInstructionDetail: () -> Unit,
    onJumpToTarget: (Long) -> Unit,
    showDebugActions: Boolean,
    isBreakpoint: Boolean,
    onToggleBreakpoint: () -> Unit,
    onRunToCursor: () -> Unit,
    onSetPcHere: () -> Unit,
    offset: DpOffset = DpOffset.Zero
) {
    if (expanded) {
        var currentMenu by remember(address) { mutableStateOf("main") }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            offset = offset
        ) {
            when (currentMenu) {
                "main" -> {
                    SubmenuItem(R.string.menu_copy_submenu) { currentMenu = "copy" }
                    SubmenuItem(R.string.menu_modify_submenu) { currentMenu = "modify" }

                    if (showDebugActions) {
                        SubmenuItem(R.string.debug_menu_submenu) { currentMenu = "debug" }
                    }

                    val jump = instr?.jump
                    if (jump != null) {
                        val jumpAddr = "0x${jump.toULong().toString(16).uppercase()}"
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_jump_to, jumpAddr)) },
                            onClick = { onJumpToTarget(jump) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.annotation_menu_name_jump_target, jumpAddr)) },
                            onClick = {
                                onDismiss()
                                onAnnotate(AnnotationAction.NAME, jump)
                            }
                        )
                    }

                    SubmenuItem(R.string.menu_more_submenu) { currentMenu = "more" }

                    HorizontalDivider()

                    // Xrefs
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_xrefs)) },
                        onClick = { onXrefs() }
                    )

                    // Instruction Detail
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_instruction_detail)) },
                        onClick = { onInstructionDetail() }
                    )

                    // Custom Command
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_custom_command)) },
                        onClick = { onCustomCommand() }
                    )
                }

                "more" -> {
                    SubmenuBackItem { currentMenu = "main" }
                    SubmenuItem(R.string.menu_function_submenu) { currentMenu = "function" }
                    AddressAnnotationMenuItems(address, onAnnotate, onDismiss)
                    SubmenuItem(R.string.annotation_menu_operand_submenu) { currentMenu = "operand" }
                    SubmenuItem(R.string.annotation_menu_define_submenu) { currentMenu = "define" }
                }
                
                "copy" -> {
                    SubmenuBackItem { currentMenu = "main" }
                    
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_address)) },
                        onClick = { onCopy("0x%08x".format(address)) }
                    )
                    
                    if (instr != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_opcodes)) },
                            onClick = { onCopy(instr.disasm) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_bytes)) },
                            onClick = { onCopy(instr.bytes) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_full_row)) },
                            onClick = { 
                                val bytesStr = if (instr.bytes.length > 12) instr.bytes.take(12) + ".." else instr.bytes
                                onCopy("0x%08x  %s  %s".format(address, bytesStr.padEnd(14), instr.disasm)) 
                            }
                        )
                    }
                }
                
                "modify" -> {
                    SubmenuBackItem { currentMenu = "main" }

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hex_modify_hex)) },
                        onClick = { onModify("hex") }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hex_modify_string)) },
                        onClick = { onModify("string") }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hex_modify_opcode)) },
                        onClick = { onModify("asm") }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hex_modify_comment)) },
                        onClick = { onModify("comment") }
                    )
                }

                "define" -> {
                    SubmenuBackItem { currentMenu = "more" }
                    DefineMenuItems(address, onAnnotate, onDismiss)
                }

                "operand" -> {
                    SubmenuBackItem { currentMenu = "more" }
                    OperandMenuItems(address, onAnnotate, onDismiss)
                }

                "function" -> {
                    SubmenuBackItem { currentMenu = "more" }
                    FunctionMenuItems(address, functionActions, onAnnotate, onDismiss)
                }

                "debug" -> {
                    SubmenuBackItem { currentMenu = "main" }

                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isBreakpoint) stringResource(R.string.debug_action_remove_breakpoint)
                                else stringResource(R.string.debug_action_add_breakpoint)
                            )
                        },
                        onClick = { onToggleBreakpoint() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.debug_action_run_to_cursor)) },
                        onClick = { onRunToCursor() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.debug_action_set_pc_here)) },
                        onClick = { onSetPcHere() }
                    )
                }
            }
        }
    }
}

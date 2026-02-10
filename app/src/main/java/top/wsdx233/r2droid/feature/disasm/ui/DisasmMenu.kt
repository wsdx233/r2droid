package top.wsdx233.r2droid.feature.disasm.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.DisasmInstruction

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
    onAnalyzeFunction: () -> Unit = {},
    onFunctionInfo: () -> Unit = {},
    onFunctionXrefs: () -> Unit = {},
    onFunctionVariables: () -> Unit = {}
) {
    if (expanded) {
        // State to track which menu is currently visible: "main", "copy", "modify"
        var currentMenu by remember { mutableStateOf("main") }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            when (currentMenu) {
                "main" -> {
                    // === Main Menu ===
                    
                    // Copy Submenu Trigger
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy_submenu)) },
                        onClick = { currentMenu = "copy" },
                        trailingIcon = { 
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Submenu"
                            ) 
                        }
                    )
                    
                    // Modify Submenu Trigger
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_modify_submenu)) },
                        onClick = { currentMenu = "modify" },
                        trailingIcon = { 
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Submenu"
                            ) 
                        }
                    )
                    
                    // Function Submenu Trigger
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_submenu)) },
                        onClick = { currentMenu = "function" },
                        trailingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Submenu"
                            )
                        }
                    )

                    HorizontalDivider()

                    // Xrefs
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_xrefs)) },
                        onClick = { onXrefs() }
                    )
                    
                    // Custom Command
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_custom_command)) },
                        onClick = { onCustomCommand() }
                    )
                }
                
                "copy" -> {
                    // === Copy Submenu ===
                    DropdownMenuItem(
                        text = { Text("Back") },
                        onClick = { currentMenu = "main" },
                        leadingIcon = { 
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            ) 
                        }
                    )
                    HorizontalDivider()
                    
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
                    // === Modify Submenu ===
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_back)) },
                        onClick = { currentMenu = "main" },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    )
                    HorizontalDivider()

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
                }

                "function" -> {
                    // === Function Submenu ===
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_back)) },
                        onClick = { currentMenu = "main" },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    )
                    HorizontalDivider()

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_analyze)) },
                        onClick = { onAnalyzeFunction() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_info)) },
                        onClick = { onFunctionInfo() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_xrefs)) },
                        onClick = { onFunctionXrefs() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_variables)) },
                        onClick = { onFunctionVariables() }
                    )
                }
            }
        }
    }
}

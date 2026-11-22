package top.wsdx233.r2droid.feature.hex.ui

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
import top.wsdx233.r2droid.core.ui.components.AddressAnnotationMenuItems
import top.wsdx233.r2droid.core.ui.components.DefineMenuItems
import top.wsdx233.r2droid.core.ui.components.FunctionMenuActions
import top.wsdx233.r2droid.core.ui.components.FunctionMenuItems
import top.wsdx233.r2droid.core.ui.components.SubmenuBackItem
import top.wsdx233.r2droid.core.ui.components.SubmenuItem
import top.wsdx233.r2droid.feature.annotations.AnnotationAction

@Composable
fun HexContextMenu(
    expanded: Boolean,
    address: Long,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onModify: (String) -> Unit, // type: hex, string, asm
    onXrefs: () -> Unit,
    onCustomCommand: () -> Unit,
    functionActions: FunctionMenuActions,
    onAnnotate: (AnnotationAction, Long) -> Unit,
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
                    SubmenuItem(R.string.annotation_menu_define_submenu) { currentMenu = "define" }
                    SubmenuItem(R.string.menu_function_submenu) { currentMenu = "function" }
                    HorizontalDivider()
                    AddressAnnotationMenuItems(address, onAnnotate, onDismiss)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_xrefs)) },
                        onClick = { onXrefs() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_custom_command)) },
                        onClick = { onCustomCommand() }
                    )
                }
                "copy" -> {
                    SubmenuBackItem { currentMenu = "main" }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_address)) },
                        onClick = { onCopy("0x%08x".format(address)) }
                    )
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
                }
                "define" -> {
                    SubmenuBackItem { currentMenu = "main" }
                    DefineMenuItems(address, onAnnotate, onDismiss)
                }
                "function" -> {
                    SubmenuBackItem { currentMenu = "main" }
                    FunctionMenuItems(address, functionActions, onAnnotate, onDismiss)
                }
            }
        }
    }
}

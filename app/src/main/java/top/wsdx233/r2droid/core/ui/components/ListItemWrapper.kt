package top.wsdx233.r2droid.core.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import top.wsdx233.r2droid.R

data class ListItemActions(
    val onCopy: (String) -> Unit,
    val onJumpToHex: (Long) -> Unit,
    val onJumpToDisasm: (Long) -> Unit,
    val onShowXrefs: (Long) -> Unit,
    val onAnalyzeFunction: ((Long) -> Unit)? = null,
    val onFunctionInfo: ((Long) -> Unit)? = null,
    val onFunctionXrefs: ((Long) -> Unit)? = null,
    val onFunctionVariables: ((Long) -> Unit)? = null
)

@Composable
fun UnifiedListItemWrapper(
    title: String,
    address: Long?,
    fullText: String,
    actions: ListItemActions,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuScreen by remember { mutableStateOf("Main") }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
               detectTapGestures(
                   onTap = { expanded = true }
               )
            }
    ) {
        content()
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { 
                expanded = false 
                menuScreen = "Main"
            }
        ) {
            when (menuScreen) {
                "Main" -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy)) },
                        onClick = { menuScreen = "Copy" },
                        trailingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    )
                    if (address != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_jump)) },
                            onClick = { menuScreen = "Jump" },
                            trailingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_xrefs)) },
                            onClick = {
                                expanded = false
                                actions.onShowXrefs(address)
                            }
                        )
                        if (actions.onAnalyzeFunction != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_function_submenu)) },
                                onClick = { menuScreen = "Function" },
                                trailingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
                "Copy" -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_back)) },
                        onClick = { menuScreen = "Main" },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_name)) },
                        onClick = { 
                            actions.onCopy(title)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    if (address != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_address)) },
                            onClick = { 
                                actions.onCopy("0x%X".format(address))
                                expanded = false
                                menuScreen = "Main"
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_all)) },
                        onClick = { 
                            actions.onCopy(fullText)
                            expanded = false
                            menuScreen = "Main"
                         }
                    )
                }
                "Jump" -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_back)) },
                        onClick = { menuScreen = "Main" },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_hex_viewer)) },
                        onClick = {
                            if (address != null) actions.onJumpToHex(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_disassembly)) },
                        onClick = {
                            if (address != null) actions.onJumpToDisasm(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                }
                "Function" -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_back)) },
                        onClick = { menuScreen = "Main" },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_analyze)) },
                        onClick = {
                            if (address != null) actions.onAnalyzeFunction?.invoke(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_info)) },
                        onClick = {
                            if (address != null) actions.onFunctionInfo?.invoke(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_xrefs)) },
                        onClick = {
                            if (address != null) actions.onFunctionXrefs?.invoke(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_function_variables)) },
                        onClick = {
                            if (address != null) actions.onFunctionVariables?.invoke(address)
                            expanded = false
                            menuScreen = "Main"
                        }
                    )
                }
            }
        }
    }
}

package top.wsdx233.r2droid.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.annotations.AnnotationAction

private val defineActions = listOf(
    AnnotationAction.CODE,
    AnnotationAction.BYTE,
    AnnotationAction.WORD,
    AnnotationAction.DWORD,
    AnnotationAction.QWORD,
    AnnotationAction.STRING,
    AnnotationAction.STRUCT,
    AnnotationAction.UNDEFINE
)

private val operandActions = listOf(
    AnnotationAction.HEX,
    AnnotationAction.DEC,
    AnnotationAction.BIN,
    AnnotationAction.CHAR,
    AnnotationAction.OFFSET,
    AnnotationAction.ENUM
)

class FunctionMenuActions(
    val onAnalyze: (Long) -> Unit,
    val onInfo: (Long) -> Unit,
    val onXrefs: (Long) -> Unit,
    val onVariables: (Long) -> Unit
)

@Composable
internal fun SubmenuItem(@StringRes label: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
    )
}

@Composable
internal fun SubmenuBackItem(onBack: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_back)) },
        onClick = onBack,
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
    )
    HorizontalDivider()
}

@Composable
internal fun DefineMenuItems(
    address: Long,
    onAnnotate: (AnnotationAction, Long) -> Unit,
    onDismiss: () -> Unit
) {
    defineActions.forEach { AnnotationMenuItem(it, address, onAnnotate, onDismiss) }
}

@Composable
internal fun OperandMenuItems(
    address: Long,
    onAnnotate: (AnnotationAction, Long) -> Unit,
    onDismiss: () -> Unit
) {
    operandActions.forEach { AnnotationMenuItem(it, address, onAnnotate, onDismiss) }
}

@Composable
internal fun AddressAnnotationMenuItems(
    address: Long,
    onAnnotate: (AnnotationAction, Long) -> Unit,
    onDismiss: () -> Unit
) {
    AnnotationMenuItem(AnnotationAction.NAME, address, onAnnotate, onDismiss)
    AnnotationMenuItem(AnnotationAction.TOGGLE_BOOKMARK, address, onAnnotate, onDismiss)
}

@Composable
internal fun FunctionMenuItems(
    address: Long,
    actions: FunctionMenuActions,
    onAnnotate: (AnnotationAction, Long) -> Unit,
    onDismiss: () -> Unit
) {
    FunctionMenuItem(R.string.menu_function_analyze, address, actions.onAnalyze, onDismiss)
    FunctionMenuItem(R.string.menu_function_info, address, actions.onInfo, onDismiss)
    FunctionMenuItem(R.string.menu_function_xrefs, address, actions.onXrefs, onDismiss)
    FunctionMenuItem(R.string.menu_function_variables, address, actions.onVariables, onDismiss)
    AnnotationMenuItem(AnnotationAction.DELETE_FUNCTION, address, onAnnotate, onDismiss)
    AnnotationMenuItem(AnnotationAction.RESIZE_FUNCTION, address, onAnnotate, onDismiss)
}

@Composable
private fun FunctionMenuItem(
    @StringRes label: Int,
    address: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            onDismiss()
            onSelect(address)
        }
    )
}

@Composable
private fun AnnotationMenuItem(
    action: AnnotationAction,
    address: Long,
    onAnnotate: (AnnotationAction, Long) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(action.label)) },
        onClick = {
            onDismiss()
            onAnnotate(action, address)
        }
    )
}

package top.wsdx233.r2droid.feature.ai.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager

private enum class ReferenceAction { CopyAddress, Hex, Disassembly, Xrefs }

@Composable
internal fun AiChatReferenceSheet(
    token: String,
    onCopy: (String) -> Unit,
    onNavigateToDetail: (Long, Int) -> Unit,
    onShowXrefs: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val target = remember(token) { R2PipeManager.captureActiveTarget() }
    var pending by remember(token) { mutableStateOf<ReferenceAction?>(null) }
    var failed by remember(token) { mutableStateOf(false) }
    val dismiss = { pending = null; onDismiss() }
    pending?.let { action ->
        LaunchedEffect(token, action) {
            val address = if (target == null || !target.isCurrent()) null else resolveChatReference(token) {
                target.execute(it, markDirty = false).getOrNull()
            }
            if (pending != action) return@LaunchedEffect
            pending = null
            if (address == null || target?.isCurrent() != true) {
                failed = true
            } else {
                onDismiss()
                when (action) {
                    ReferenceAction.CopyAddress -> onCopy("0x%X".format(address))
                    ReferenceAction.Hex -> onNavigateToDetail(address, 0)
                    ReferenceAction.Disassembly -> onNavigateToDetail(address, 1)
                    ReferenceAction.Xrefs -> onShowXrefs(address)
                }
            }
        }
    }

    WawSheet(title = token, onDismiss = dismiss) {
        WawPopupMenuItem(stringResource(R.string.menu_copy), Icons.Rounded.ContentCopy) { onCopy(token); dismiss() }
        when {
            target?.isCurrent() != true -> Text(stringResource(R.string.proj_session_disconnected))
            pending != null -> Text(stringResource(R.string.dialog_jump_resolving))
            else -> {
                if (failed) Text(stringResource(R.string.dialog_jump_error_resolve), color = MaterialTheme.colorScheme.error)
                if (!token.startsWith("0x", ignoreCase = true)) {
                    WawPopupMenuItem(stringResource(R.string.patches_copy_address), Icons.Rounded.ContentCopy) { pending = ReferenceAction.CopyAddress }
                }
                WawPopupMenuItem(stringResource(R.string.menu_jump_to, stringResource(R.string.menu_hex_viewer)), Icons.Rounded.GridOn) {
                    pending = ReferenceAction.Hex
                }
                WawPopupMenuItem(stringResource(R.string.menu_jump_to, stringResource(R.string.menu_disassembly)), Icons.Rounded.Code) {
                    pending = ReferenceAction.Disassembly
                }
                WawPopupMenuItem(stringResource(R.string.menu_xrefs), Icons.Rounded.AccountTree) { pending = ReferenceAction.Xrefs }
            }
        }
    }
}

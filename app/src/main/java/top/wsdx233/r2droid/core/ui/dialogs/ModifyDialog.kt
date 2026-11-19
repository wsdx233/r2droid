package top.wsdx233.r2droid.core.ui.dialogs

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.json.JSONObject
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager
import top.wsdx233.r2droid.util.isReadOnlyBinary
import top.wsdx233.r2droid.util.viewerCacheSettingsEnabled

/** UX check only; the transport checks again under the session mutex before every write. */
internal fun Context.canOpenPatchEditor(): Boolean {
    if (!R2PipeManager.esilOwnsActivePipe) return true
    Toast.makeText(this, R.string.debug_esil_edit_locked, Toast.LENGTH_LONG).show()
    return false
}

/** A cached editor must never fall back to a writable reopen. */
internal suspend fun Context.requestByteEditor(
    onReopen: (suspend () -> Unit) -> Unit,
    onReady: (R2PipeManager.R2Session) -> Unit
) {
    if (!canOpenPatchEditor()) return
    val session = R2PipeManager.activeSession() ?: return
    val generation = R2PipeManager.sessionId
    fun isCurrent() = R2PipeManager.activeSessionPipe === session.pipe && R2PipeManager.sessionId == generation

    suspend fun coreInfo() = R2PipeManager.execute(session, "ij", markDirty = false)
        .mapCatching { JSONObject(it).getJSONObject("core") }.getOrNull()

    val core = coreInfo()
    if (session.writeCacheEnabled) {
        val ready = isReadOnlyBinary(core) && session.viewerCacheSettingsEnabled()
        if (!isCurrent() || !canOpenPatchEditor()) return
        if (ready) onReady(session) else Toast.makeText(this, R.string.analysis_write_cache_edit_unavailable, Toast.LENGTH_LONG).show()
    } else if (isCurrent() && canOpenPatchEditor()) {
        if (core?.optBoolean("iorw") == true) {
            onReady(session)
        } else {
            onReopen {
                if (isCurrent() && canOpenPatchEditor() &&
                    R2PipeManager.execute(session, "oo+").isSuccess &&
                    isCurrent() && canOpenPatchEditor()
                ) onReady(session)
            }
        }
    }
}

@Composable
fun ModifyDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.focusable()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    onConfirm(text)
                    onDismiss()
                }
            ) {
                Text("Write")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

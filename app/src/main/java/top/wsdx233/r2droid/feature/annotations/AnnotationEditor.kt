package top.wsdx233.r2droid.feature.annotations

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.formatCompactAddress

internal data class AnnotationRequest(val session: AnnotationSession, val action: AnnotationAction, val address: Long)

/** Caller keys this dialog to the captured request, not the current cursor or list selection. */
@Composable
internal fun AnnotationEditor(request: AnnotationRequest, onDismiss: () -> Unit, onApplied: () -> Unit) {
    val context = LocalContext.current
    val (session, action, address) = request
    val addressLabel = remember(address) { "0x${formatCompactAddress(address)}" }
    val needsInput = when (action) {
        AnnotationAction.STRUCT, AnnotationAction.ENUM, AnnotationAction.OFFSET, AnnotationAction.NAME,
        AnnotationAction.RESIZE_FUNCTION, AnnotationAction.DELETE_FUNCTION, AnnotationAction.UNDEFINE -> true
        else -> false
    }
    var loading by remember { mutableStateOf(needsInput) }
    var ready by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(!needsInput) }
    var error by remember { mutableStateOf<String?>(null) }
    var choices by remember { mutableStateOf(emptyList<String>()) }
    var oldName by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableStateOf("") }
    var bounds by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    LaunchedEffect(request) {
        if (!needsInput) return@LaunchedEffect
        try {
            when (action) {
                AnnotationAction.STRUCT, AnnotationAction.ENUM -> choices = session.semanticChoices(action)
                AnnotationAction.NAME -> {
                    choices = session.namesAt(address)
                    oldName = choices.singleOrNull()
                    value = oldName.orEmpty()
                }
                AnnotationAction.RESIZE_FUNCTION, AnnotationAction.DELETE_FUNCTION -> {
                    val range = session.functionBounds(address)
                    bounds = range
                    if (action == AnnotationAction.RESIZE_FUNCTION) value = "0x${formatCompactAddress(range.second)}"
                }
                AnnotationAction.OFFSET -> value = session.suggestOffset(address)
                else -> Unit
            }
            ready = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            error = e.localizedMessage ?: context.getString(R.string.common_error)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(applying) {
        if (!applying) return@LaunchedEffect
        try {
            val notice = when (action) {
                AnnotationAction.TOGGLE_BOOKMARK -> if (session.toggleBookmark(address)) R.string.annot_bookmarked else R.string.annot_unbookmarked
                AnnotationAction.NAME -> { session.setName(address, oldName, value.trim()); R.string.annot_applied }
                else -> { session.applySemantic(action, address, value.trim()); R.string.annot_applied }
            }
            Toast.makeText(context, context.getString(notice, addressLabel), Toast.LENGTH_SHORT).show()
            onApplied()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            error = e.localizedMessage ?: context.getString(R.string.common_error)
        } finally {
            applying = false
        }
    }

    val valid = when (action) {
        AnnotationAction.NAME -> isValidAnnotationName(value.trim()) && oldName != value.trim()
        AnnotationAction.ENUM, AnnotationAction.STRUCT -> value in choices
        AnnotationAction.RESIZE_FUNCTION, AnnotationAction.OFFSET -> value.isNotBlank()
        else -> true
    }
    val edit: (String) -> Unit = { value = it; error = null }
    AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = { Text(stringResource(action.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.annot_address, addressLabel))
                if (loading || applying) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (ready && !applying) {
                    bounds?.let { (start, end) ->
                        Text(stringResource(R.string.annot_range, "0x${formatCompactAddress(start)}", "0x${formatCompactAddress(end)}"))
                    }
                    when (action) {
                        AnnotationAction.NAME -> {
                            if (choices.isNotEmpty()) NameSelector(choices, oldName) { oldName = it; edit(it.orEmpty()) }
                            OutlinedTextField(value, edit, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(stringResource(R.string.annot_name_input)) },
                                isError = value.isNotBlank() && !isValidAnnotationName(value.trim()))
                            if (value.isNotBlank() && !isValidAnnotationName(value.trim())) Text(stringResource(R.string.annot_name_invalid))
                        }
                        AnnotationAction.RESIZE_FUNCTION, AnnotationAction.OFFSET -> OutlinedTextField(
                            value, edit, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(if (action == AnnotationAction.OFFSET) R.string.annot_offset_target else R.string.annot_end)) },
                            placeholder = { Text(stringResource(R.string.dialog_jump_address_hint)) }
                        )
                        AnnotationAction.STRUCT, AnnotationAction.ENUM -> if (choices.isEmpty()) {
                            Text(stringResource(if (action == AnnotationAction.STRUCT) R.string.annot_no_structs else R.string.annot_no_enums))
                        } else TypeSelector(choices, value, edit)
                        AnnotationAction.DELETE_FUNCTION -> Text(stringResource(R.string.annot_delete_warning))
                        AnnotationAction.UNDEFINE -> Text(stringResource(R.string.annot_undefine_warning))
                        else -> Unit
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (needsInput && ready) TextButton(onClick = { error = null; applying = true }, enabled = valid && !applying) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !applying) { Text(stringResource(R.string.dialog_cancel)) } }
    )
}

@Composable
private fun NameSelector(names: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val create = stringResource(R.string.annot_name_new)
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(if (selected == null) create else stringResource(R.string.annot_name_existing, selected))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(create) }, onClick = { expanded = false; onSelect(null) })
            names.forEach { name ->
                DropdownMenuItem(text = { Text(stringResource(R.string.annot_name_existing, name)) }, onClick = { expanded = false; onSelect(name) })
            }
        }
    }
}

@Composable
private fun TypeSelector(choices: List<String>, value: String, onSelect: (String) -> Unit) {
    OutlinedTextField(value, onSelect, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(R.string.annot_type_input)) })
    val matches = remember(choices, value) { if (value.isEmpty()) choices else choices.filter { it.contains(value, ignoreCase = true) } }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
        items(matches, key = { it }) { type ->
            Text(type, Modifier.fillMaxWidth().clickable { onSelect(type) }.padding(10.dp))
        }
    }
}

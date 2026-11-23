package top.wsdx233.r2droid.feature.debug.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.ui.theme.LocalAppFont

private const val MEMORY_ROW_BYTES = 8
private const val MEMORY_PAGE_STEP = 64L

private enum class MemoryWriteMode {
    HEX,
    TEXT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryPanel(
    address: Long?,
    bytes: ByteArray,
    isLoading: Boolean,
    error: String?,
    onRefreshPc: () -> Unit,
    onRefreshSp: () -> Unit,
    onRefreshBp: () -> Unit = {},
    hasBp: Boolean = false,
    isWriting: Boolean = false,
    canWriteMemory: Boolean = false,
    onQueryExpression: (String) -> Unit = {},
    onReadAddress: (Long) -> Unit = {},
    onWriteMemory: (Long, ByteArray) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var queryInput by remember { mutableStateOf(address?.let { formatDebugHex(it) }.orEmpty()) }
    var writeTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    LaunchedEffect(address) {
        if (address != null) {
            queryInput = formatDebugHex(address)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DebugSectionHeader(title = stringResource(R.string.debug_panel_memory_title)) {
            if (canWriteMemory && address != null) {
                FilledTonalButton(
                    onClick = {
                        val initialHex = bytes.take(MEMORY_ROW_BYTES)
                            .joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
                        writeTarget = address to initialHex
                    },
                    enabled = !isLoading && !isWriting,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.debug_memory_write), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        OutlinedTextField(
            value = queryInput,
            onValueChange = { queryInput = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.debug_memory_expr_placeholder),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        val q = queryInput.trim()
                        if (q.isNotEmpty()) onQueryExpression(q)
                    },
                    enabled = queryInput.isNotBlank() && !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.debug_memory_query_go),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalAppFont.current),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    val q = queryInput.trim()
                    if (q.isNotEmpty()) onQueryExpression(q)
                }
            ),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DebugFilterPill(
                    label = "PC",
                    selected = false,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = onRefreshPc
                )
                DebugFilterPill(
                    label = "SP",
                    selected = false,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    onClick = onRefreshSp
                )
                if (hasBp) {
                    DebugFilterPill(
                        label = "BP",
                        selected = false,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        onClick = onRefreshBp
                    )
                }
            }

            if (address != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val prev = (address - MEMORY_PAGE_STEP).coerceAtLeast(0L)
                            queryInput = formatDebugHex(prev)
                            onReadAddress(prev)
                        },
                        enabled = !isLoading,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.debug_memory_prev_page),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val next = address + MEMORY_PAGE_STEP
                            queryInput = formatDebugHex(next)
                            onReadAddress(next)
                        },
                        enabled = !isLoading,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.debug_memory_next_page),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (isWriting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        when {
            isLoading -> {
                DebugLoadingBox()
            }
            error != null -> {
                DebugErrorBanner(
                    message = error,
                    onRetry = if (queryInput.isNotBlank()) {
                        { onQueryExpression(queryInput.trim()) }
                    } else null
                )
            }
            address == null -> {
                DebugEmptyState(
                    icon = Icons.Default.Memory,
                    message = stringResource(R.string.debug_panel_memory_no_address)
                )
            }
            bytes.isEmpty() -> {
                DebugEmptyState(
                    icon = Icons.Default.Memory,
                    message = stringResource(R.string.debug_panel_memory_empty)
                )
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.debug_panel_memory_base, formatDebugHex(address)),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = LocalAppFont.current,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (canWriteMemory) {
                        Text(
                            text = stringResource(R.string.debug_memory_tap_to_edit_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val rows = remember(address, bytes) { formatHexRows(address, bytes) }
                val addrColor = MaterialTheme.colorScheme.primary
                val hexColor = MaterialTheme.colorScheme.onSurface
                val asciiColor = MaterialTheme.colorScheme.tertiary

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(rows, key = { it.address }) { row ->
                            val line = remember(row, addrColor, hexColor, asciiColor) {
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = addrColor, fontWeight = FontWeight.SemiBold)) {
                                        append(row.addressText)
                                    }
                                    append("  ")
                                    withStyle(SpanStyle(color = hexColor)) {
                                        append(row.hexText)
                                    }
                                    append("  ")
                                    withStyle(SpanStyle(color = asciiColor)) {
                                        append(row.asciiText)
                                    }
                                }
                            }
                            Text(
                                text = line,
                                fontFamily = LocalAppFont.current,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (canWriteMemory) {
                                            writeTarget = row.address to row.rawHex
                                        } else {
                                            copyDebugText(context, "${row.addressText} ${row.rawHex}")
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    writeTarget?.let { (initialAddr, initialHex) ->
        WriteMemoryDialog(
            initialAddress = formatDebugHex(initialAddr),
            initialHex = initialHex,
            onDismiss = { writeTarget = null },
            onConfirm = { targetAddr, payload ->
                onWriteMemory(targetAddr, payload)
                writeTarget = null
            }
        )
    }
}

private data class MemoryRow(
    val address: Long,
    val addressText: String,
    val hexText: String,
    val rawHex: String,
    val asciiText: String
)

private fun formatHexRows(base: Long, bytes: ByteArray): List<MemoryRow> {
    return bytes.toList().chunked(MEMORY_ROW_BYTES).mapIndexed { rowIndex, chunk ->
        val addr = base + rowIndex * MEMORY_ROW_BYTES.toLong()
        val rawHex = chunk.joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xff) }
        val hexPadded = rawHex.padEnd(MEMORY_ROW_BYTES * 3 - 1)
        val ascii = chunk.map { byte ->
            val value = byte.toInt() and 0xff
            if (value in 0x20..0x7e) value.toChar() else '.'
        }.joinToString("")
        MemoryRow(
            address = addr,
            addressText = formatDebugHex(addr, minDigits = 8),
            hexText = hexPadded,
            rawHex = rawHex,
            asciiText = ascii
        )
    }
}

@Composable
private fun WriteMemoryDialog(
    initialAddress: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, ByteArray) -> Unit
) {
    var addrText by remember { mutableStateOf(initialAddress) }
    var mode by remember { mutableStateOf(MemoryWriteMode.HEX) }
    var valueText by remember { mutableStateOf(initialHex) }
    var error by remember { mutableStateOf<String?>(null) }

    val invalidAddrMsg = stringResource(R.string.dialog_jump_error_invalid)
    val invalidHexMsg = stringResource(R.string.debug_memory_write_invalid_hex)

    val parsedPreview = remember(mode, valueText) {
        when (mode) {
            MemoryWriteMode.HEX -> parseMemoryHexInput(valueText)
            MemoryWriteMode.TEXT -> valueText.toByteArray(Charsets.UTF_8).takeIf { it.isNotEmpty() }
        }
    }

    DebugDialogContainer(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.debug_memory_write_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        content = {
            OutlinedTextField(
                value = addrText,
                onValueChange = {
                    addrText = it
                    error = null
                },
                label = { Text(stringResource(R.string.dialog_jump_address_label)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == MemoryWriteMode.HEX,
                    onClick = {
                        if (mode != MemoryWriteMode.HEX) {
                            mode = MemoryWriteMode.HEX
                            valueText = initialHex
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.debug_memory_write_mode_hex)) }
                )
                FilterChip(
                    selected = mode == MemoryWriteMode.TEXT,
                    onClick = {
                        if (mode != MemoryWriteMode.TEXT) {
                            mode = MemoryWriteMode.TEXT
                            valueText = ""
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.debug_memory_write_mode_text)) }
                )
            }

            OutlinedTextField(
                value = valueText,
                onValueChange = {
                    valueText = it
                    error = null
                },
                placeholder = {
                    Text(
                        text = stringResource(
                            if (mode == MemoryWriteMode.HEX) R.string.debug_memory_write_hex_hint
                            else R.string.debug_memory_write_text_hint
                        )
                    )
                },
                isError = error != null,
                supportingText = {
                    val msg = error
                    if (msg != null) {
                        Text(msg)
                    } else if (parsedPreview != null) {
                        Text(stringResource(R.string.debug_memory_write_byte_count, parsedPreview.size))
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val targetAddr = parseDebugUserLong(addrText)
                    if (targetAddr == null) {
                        error = invalidAddrMsg
                        return@Button
                    }
                    val bytesToWrite = when (mode) {
                        MemoryWriteMode.HEX -> parseMemoryHexInput(valueText)
                        MemoryWriteMode.TEXT -> valueText.toByteArray(Charsets.UTF_8).takeIf { it.isNotEmpty() }
                    }
                    if (bytesToWrite == null || bytesToWrite.isEmpty()) {
                        error = invalidHexMsg
                        return@Button
                    }
                    onConfirm(targetAddr, bytesToWrite)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.debug_memory_write))
            }
        }
    )
}

internal fun parseMemoryHexInput(input: String): ByteArray? {
    val cleaned = input
        .replace("0x", "", ignoreCase = true)
        .replace(Regex("[\\s,;:_-]+"), "")
    if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
    val out = ByteArray(cleaned.length / 2)
    for (i in out.indices) {
        val value = cleaned.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
        out[i] = value.toByte()
    }
    return out
}

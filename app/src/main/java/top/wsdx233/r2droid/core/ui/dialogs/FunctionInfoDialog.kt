package top.wsdx233.r2droid.core.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.FunctionDetailInfo
import top.wsdx233.r2droid.core.ui.components.rememberHighlightedCCode
import top.wsdx233.r2droid.feature.types.data.CallingConventionInfo
import top.wsdx233.r2droid.feature.types.data.LocalTypesSnapshot
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@Composable
fun FunctionInfoDialog(
    functionInfo: FunctionDetailInfo?,
    isLoading: Boolean,
    targetAddress: Long,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onJump: (Long) -> Unit,
    onUpdateSignature: (String) -> Unit = {},
    onUpdateCallingConvention: (String) -> Unit = {},
    availableCallingConventions: List<CallingConventionInfo> = emptyList(),
    typeSuggestions: List<String> = LocalTypesSnapshot.COMMON_C_TYPES,
    isMutating: Boolean = false,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onOpenVariables: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = { if (!isMutating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FunctionInfoDialogCard(
            functionInfo = functionInfo,
            isLoading = isLoading,
            targetAddress = targetAddress,
            onDismiss = onDismiss,
            onRename = onRename,
            onJump = onJump,
            onUpdateSignature = onUpdateSignature,
            onUpdateCallingConvention = onUpdateCallingConvention,
            availableCallingConventions = availableCallingConventions,
            typeSuggestions = typeSuggestions,
            isMutating = isMutating,
            errorMessage = errorMessage,
            onDismissError = onDismissError,
            onOpenVariables = onOpenVariables,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
        )
    }
}

@Composable
internal fun FunctionInfoDialogCard(
    functionInfo: FunctionDetailInfo?,
    isLoading: Boolean,
    targetAddress: Long,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onJump: (Long) -> Unit,
    onUpdateSignature: (String) -> Unit = {},
    onUpdateCallingConvention: (String) -> Unit = {},
    availableCallingConventions: List<CallingConventionInfo> = emptyList(),
    typeSuggestions: List<String> = LocalTypesSnapshot.COMMON_C_TYPES,
    isMutating: Boolean = false,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onOpenVariables: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Functions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.func_info_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val jumpAddr = functionInfo?.addr ?: targetAddress
                        Surface(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onJump(jumpAddr) },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "0x${jumpAddr.toULong().toString(16).uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = LocalAppFont.current,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Rounded.NearMe,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, enabled = !isMutating) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.func_close)
                        )
                    }
                }

                AnimatedVisibility(visible = isMutating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Error Banner
                AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissError,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 500.dp)
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(40.dp)
                                    .align(Alignment.Center)
                            )
                        }
                        functionInfo == null -> {
                            Text(
                                text = stringResource(R.string.func_info_no_function),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            FunctionInfoContent(
                                info = functionInfo,
                                availableCallingConventions = availableCallingConventions,
                                typeSuggestions = typeSuggestions,
                                busy = isMutating,
                                onRename = onRename,
                                onUpdateSignature = onUpdateSignature,
                                onUpdateCallingConvention = onUpdateCallingConvention,
                                onJump = onJump
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenVariables != null && functionInfo != null) {
                        FilledTonalButton(
                            onClick = {
                                onDismiss()
                                onOpenVariables()
                            },
                            enabled = !isMutating,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.func_info_open_vars))
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    TextButton(onClick = onDismiss, enabled = !isMutating) {
                        Text(stringResource(R.string.func_close))
                    }
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FunctionInfoContent(
    info: FunctionDetailInfo,
    availableCallingConventions: List<CallingConventionInfo>,
    typeSuggestions: List<String>,
    busy: Boolean,
    onRename: (String) -> Unit,
    onUpdateSignature: (String) -> Unit,
    onUpdateCallingConvention: (String) -> Unit,
    onJump: (Long) -> Unit
) {
    var isRenaming by remember(info.name) { mutableStateOf(false) }
    var newName by remember(info.name) { mutableStateOf(info.name) }

    var isEditingSignature by remember(info.signature) { mutableStateOf(false) }
    var signatureInput by remember(info.signature, info.name) {
        mutableStateOf(info.signature.ifBlank { "int ${info.name}()" })
    }

    var isPickingCc by remember(info.callType) { mutableStateOf(false) }
    var ccFilter by remember { mutableStateOf("") }
    val ccCardBringIntoView = remember { BringIntoViewRequester() }

    LaunchedEffect(isPickingCc) {
        if (isPickingCc) {
            ccCardBringIntoView.bringIntoView()
        }
    }

    val activeCcInfo = remember(availableCallingConventions, info.callType) {
        availableCallingConventions.firstOrNull { it.name.equals(info.callType, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Identity & Name Card
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRenaming) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.func_info_rename_hint)) },
                        singleLine = true,
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                newName = info.name
                                isRenaming = false
                            },
                            enabled = !busy
                        ) {
                            Text(stringResource(R.string.func_cancel))
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val trimmed = newName.trim()
                                if (trimmed.isNotEmpty() && trimmed != info.name) {
                                    onRename(trimmed)
                                }
                                isRenaming = false
                            },
                            enabled = !busy && newName.trim().isNotEmpty() && newName.trim() != info.name,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.func_confirm))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.func_info_name),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SelectionContainer {
                                Text(
                                    text = info.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = LocalAppFont.current,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                newName = info.name
                                isRenaming = true
                            },
                            enabled = !busy,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.func_rename),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Semantic tags row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (info.bits > 0) {
                        StatusTag("${info.bits}-bit", MaterialTheme.colorScheme.secondary)
                    }
                    if (info.type.isNotBlank()) {
                        StatusTag(info.type.uppercase(), MaterialTheme.colorScheme.primary)
                    }
                    if (info.noReturn) {
                        StatusTag(stringResource(R.string.func_info_noreturn), MaterialTheme.colorScheme.error)
                    }
                    if (info.isPure) {
                        StatusTag(stringResource(R.string.func_info_pure), MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }

        // 2. Editable C Signature Card (afs)
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(
                width = if (isEditingSignature) 1.2.dp else 1.dp,
                color = if (isEditingSignature) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.func_info_signature),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "afs",
                                fontFamily = LocalAppFont.current,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (!isEditingSignature) {
                        Surface(
                            onClick = {
                                signatureInput = info.signature.ifBlank { "int ${info.name}()" }
                                isEditingSignature = true
                            },
                            enabled = !busy,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.func_info_edit_signature),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                if (isEditingSignature) {
                    OutlinedTextField(
                        value = signatureInput,
                        onValueChange = { signatureInput = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.func_info_signature_hint),
                                fontFamily = LocalAppFont.current,
                                fontSize = 12.sp
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = LocalAppFont.current,
                            lineHeight = 18.sp
                        ),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Text(
                        text = stringResource(R.string.func_info_signature_helper),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val quickTypes = remember(typeSuggestions) {
                        typeSuggestions.ifEmpty { LocalTypesSnapshot.COMMON_C_TYPES }.take(14)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickTypes, key = { it }) { typeToken ->
                            Surface(
                                onClick = {
                                    signatureInput = insertTypeIntoSignature(signatureInput, typeToken)
                                },
                                enabled = !busy,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = typeToken,
                                    fontFamily = LocalAppFont.current,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                signatureInput = info.signature.ifBlank { "int ${info.name}()" }
                                isEditingSignature = false
                            },
                            enabled = !busy
                        ) {
                            Text(stringResource(R.string.func_cancel))
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val trimmed = signatureInput.trim().trimEnd(';').trim()
                                if (trimmed.isNotEmpty()) {
                                    onUpdateSignature(trimmed)
                                }
                                isEditingSignature = false
                            },
                            enabled = !busy && signatureInput.trim().trimEnd(';').isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.func_confirm))
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !busy) {
                                signatureInput = info.signature.ifBlank { "int ${info.name}()" }
                                isEditingSignature = true
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                    ) {
                        if (info.signature.isNotBlank()) {
                            val highlighted = rememberHighlightedCCode(info.signature)
                            SelectionContainer {
                                Text(
                                    text = highlighted,
                                    fontFamily = LocalAppFont.current,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.func_info_signature_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Calling Convention Card (afc)
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(ccCardBringIntoView)
                .animateContentSize(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(
                width = if (isPickingCc) 1.2.dp else 1.dp,
                color = if (isPickingCc) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.func_info_call_type),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = "afc",
                                    fontFamily = LocalAppFont.current,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = info.callType.ifBlank { stringResource(R.string.func_info_no_cc) },
                                    fontFamily = LocalAppFont.current,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (activeCcInfo != null && activeCcInfo.summary.isNotBlank()) {
                                Text(
                                    text = activeCcInfo.summary,
                                    fontFamily = LocalAppFont.current,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            ccFilter = ""
                            isPickingCc = !isPickingCc
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPickingCc) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.func_info_change_cc),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (isPickingCc) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OutlinedTextField(
                        value = ccFilter,
                        onValueChange = { ccFilter = it },
                        placeholder = { Text(stringResource(R.string.func_info_cc_search_hint), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalAppFont.current),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    val filteredCcs = remember(availableCallingConventions, ccFilter) {
                        val needle = ccFilter.trim()
                        if (needle.isEmpty()) availableCallingConventions
                        else availableCallingConventions.filter {
                            it.name.contains(needle, ignoreCase = true) ||
                                it.summary.contains(needle, ignoreCase = true)
                        }
                    }

                    if (filteredCcs.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredCcs, key = { it.name }) { cc ->
                                val selected = cc.name.equals(info.callType, ignoreCase = true)
                                Surface(
                                    onClick = {
                                        isPickingCc = false
                                        if (!selected) onUpdateCallingConvention(cc.name)
                                    },
                                    enabled = !busy,
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cc.name,
                                                fontFamily = LocalAppFont.current,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (cc.summary.isNotBlank()) {
                                                Text(
                                                    text = cc.summary,
                                                    fontFamily = LocalAppFont.current,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val customTrimmed = ccFilter.trim()
                    val exactMatch = availableCallingConventions.any { it.name.equals(customTrimmed, ignoreCase = true) }
                    if (customTrimmed.isNotEmpty() && !exactMatch) {
                        Button(
                            onClick = {
                                isPickingCc = false
                                onUpdateCallingConvention(customTrimmed)
                            },
                            enabled = !busy,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.func_info_cc_apply_custom, customTrimmed))
                        }
                    }
                }
            }
        }

        // 4. Compact Metrics Summary Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                label = stringResource(R.string.func_info_size),
                value = "${info.size} B",
                sub = "real ${info.realSize}",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = stringResource(R.string.func_info_basic_blocks),
                value = "${info.nbbs}",
                sub = "${info.ninstrs} insns",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                label = stringResource(R.string.func_info_stack_frame),
                value = "${info.stackFrame} B",
                sub = "${info.nargs}a · ${info.nlocals}v",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = stringResource(R.string.func_info_complexity),
                value = "CC ${info.cc}",
                sub = "${info.edges} edges",
                modifier = Modifier.weight(1f)
            )
        }

        // 5. Technical Details Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.func_info_metrics_section),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DetailRow(
                        label = stringResource(R.string.func_info_range),
                        value = "0x${info.minAddr.toULong().toString(16).uppercase()} – 0x${info.maxAddr.toULong().toString(16).uppercase()}"
                    )
                    DetailRow(stringResource(R.string.func_info_indegree), "${info.indegree}")
                    DetailRow(stringResource(R.string.func_info_outdegree), "${info.outdegree}")
                    DetailRow(stringResource(R.string.func_info_locals), "${info.nlocals}")
                    DetailRow(stringResource(R.string.func_info_args), "${info.nargs}")
                    DetailRow(stringResource(R.string.func_info_noreturn), if (info.noReturn) "Yes" else "No")
                    DetailRow(stringResource(R.string.func_info_pure), if (info.isPure) "Yes" else "No")
                }
            }
        }
    }
}

private fun insertTypeIntoSignature(current: String, typeToken: String): String {
    val trimmed = current.trim()
    val closeParen = trimmed.lastIndexOf(')')
    if (closeParen > 0) {
        val openParen = trimmed.indexOf('(')
        if (openParen in 0 until closeParen) {
            val inner = trimmed.substring(openParen + 1, closeParen).trim()
            val sep = if (typeToken.endsWith("*")) "" else " "
            val argIdx = if (inner.isEmpty() || inner == "void") 1 else inner.count { it == ',' } + 2
            val newParam = "$typeToken${sep}arg$argIdx"
            val updatedInner = if (inner.isEmpty() || inner == "void") newParam else "$inner, $newParam"
            return trimmed.substring(0, openParen + 1) + updatedInner + trimmed.substring(closeParen)
        }
    }
    return if (trimmed.isEmpty()) typeToken else "$trimmed $typeToken"
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = LocalAppFont.current,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = LocalAppFont.current,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = LocalAppFont.current,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

package top.wsdx233.r2droid.feature.types.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.rememberHighlightedCCode
import top.wsdx233.r2droid.feature.types.data.EditableTypeKind
import top.wsdx233.r2droid.feature.types.data.EnumTypeInfo
import top.wsdx233.r2droid.feature.types.data.StructField
import top.wsdx233.r2droid.feature.types.data.StructTypeInfo
import top.wsdx233.r2droid.feature.types.data.TypeLinkInfo
import top.wsdx233.r2droid.feature.types.data.TypedefTypeInfo
import top.wsdx233.r2droid.feature.types.data.TypesJsonParser
import top.wsdx233.r2droid.feature.types.data.UnionTypeInfo
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme

sealed interface InspectedType {
    val name: String
    val kind: EditableTypeKind

    data class Struct(val info: StructTypeInfo) : InspectedType {
        override val name: String get() = info.name
        override val kind: EditableTypeKind get() = EditableTypeKind.STRUCT
    }

    data class Union(val info: UnionTypeInfo) : InspectedType {
        override val name: String get() = info.name
        override val kind: EditableTypeKind get() = EditableTypeKind.UNION
    }

    data class Enum(val info: EnumTypeInfo) : InspectedType {
        override val name: String get() = info.name
        override val kind: EditableTypeKind get() = EditableTypeKind.ENUM
    }

    data class Typedef(val info: TypedefTypeInfo) : InspectedType {
        override val name: String get() = info.name
        override val kind: EditableTypeKind get() = EditableTypeKind.TYPEDEF
    }
}

private data class CTemplate(val label: String, val code: String)

private val cDeclarationTemplates = listOf(
    CTemplate(
        label = "struct",
        code = "struct MyStruct {\n    uint32_t magic;\n    uint32_t flags;\n    char *name;\n};"
    ),
    CTemplate(
        label = "typedef struct",
        code = "typedef struct _Node {\n    int32_t id;\n    struct _Node *next;\n} Node;"
    ),
    CTemplate(
        label = "enum",
        code = "enum StatusCode {\n    STATUS_OK = 0,\n    STATUS_ERR = 1,\n    STATUS_BUSY = 2\n};"
    ),
    CTemplate(
        label = "union",
        code = "union Value {\n    uint64_t u64;\n    int32_t i32;\n    void *ptr;\n};"
    ),
    CTemplate(
        label = "typedef",
        code = "typedef uint32_t handle_t;"
    ),
    CTemplate(
        label = "func sig",
        code = "int parse_packet(void *ctx, const uint8_t *buf, size_t len);"
    )
)

private val quickCTokens = listOf(
    "uint8_t ", "uint16_t ", "uint32_t ", "uint64_t ",
    "int32_t ", "int64_t ", "char *", "void *",
    "size_t ", "const ", "struct ", ";\n"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TypeInspectorSheet(
    inspected: InspectedType,
    cSource: String?,
    cSourceLoading: Boolean,
    linkedAddresses: List<TypeLinkInfo>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onEdit: (InspectedType, String) -> Unit,
    onRequestLink: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onJumpToDisasm: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        TypeInspectorContent(
            inspected = inspected,
            cSource = cSource,
            cSourceLoading = cSourceLoading,
            linkedAddresses = linkedAddresses,
            busy = busy,
            onDismiss = onDismiss,
            onCopy = onCopy,
            onEdit = onEdit,
            onRequestLink = onRequestLink,
            onRequestDelete = onRequestDelete,
            onJumpToDisasm = onJumpToDisasm
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TypeInspectorContent(
    inspected: InspectedType,
    cSource: String?,
    cSourceLoading: Boolean,
    linkedAddresses: List<TypeLinkInfo>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onEdit: (InspectedType, String) -> Unit,
    onRequestLink: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onJumpToDisasm: (Long) -> Unit
) {
    var selectedTab by remember(inspected) { mutableIntStateOf(0) }
    val isDark = LocalDarkTheme.current
    val accent = typeKindAccent(inspected.kind, isDark)
    val badgeLabel = when (inspected.kind) {
        EditableTypeKind.STRUCT -> stringResource(R.string.types_badge_struct)
        EditableTypeKind.UNION -> stringResource(R.string.types_badge_union)
        EditableTypeKind.ENUM -> stringResource(R.string.types_badge_enum)
        EditableTypeKind.TYPEDEF -> stringResource(R.string.types_badge_typedef)
    }

    val effectiveCSource = remember(inspected, cSource) {
        cSource?.takeIf { it.isNotBlank() } ?: when (inspected) {
            is InspectedType.Struct -> TypesJsonParser.formatStructOrUnionC("struct", inspected.info.name, inspected.info.fields)
            is InspectedType.Union -> TypesJsonParser.formatStructOrUnionC("union", inspected.info.name, inspected.info.fields)
            is InspectedType.Enum -> TypesJsonParser.formatEnumC(inspected.info)
            is InspectedType.Typedef -> "typedef ${inspected.info.targetType} ${inspected.info.name};"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inspected.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = LocalAppFont.current,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when (inspected) {
                    is InspectedType.Struct -> if (inspected.info.totalSize > 0) {
                        SizePill(inspected.info.totalSize, accent)
                    }
                    is InspectedType.Union -> if (inspected.info.totalSize > 0) {
                        SizePill(inspected.info.totalSize, accent)
                    }
                    else -> Unit
                }
            }

            // Linked Addresses Strip
            if (linkedAddresses.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.types_linked_at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        linkedAddresses.forEach { link ->
                            Surface(
                                onClick = {
                                    onDismiss()
                                    onJumpToDisasm(link.address)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = link.hexAddress,
                                        fontFamily = LocalAppFont.current,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.NearMe,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Segmented Toggle: Layout vs C Source (for Struct / Union / Enum)
            if (inspected !is InspectedType.Typedef) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SheetSegmentButton(
                            label = stringResource(R.string.types_tab_layout),
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        SheetSegmentButton(
                            label = stringResource(R.string.types_tab_c_source),
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 360.dp)
            ) {
                when {
                    inspected is InspectedType.Typedef -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.types_alias_target),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = inspected.info.name,
                                            fontFamily = LocalAppFont.current,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = rememberHighlightedCCode(inspected.info.targetType),
                                            fontFamily = LocalAppFont.current,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                            CSourceCodeBox(effectiveCSource)
                        }
                    }

                    selectedTab == 1 -> {
                        if (cSourceLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CSourceCodeBox(effectiveCSource)
                                val pfFormat = when (inspected) {
                                    is InspectedType.Struct -> inspected.info.format
                                    is InspectedType.Union -> inspected.info.format
                                    else -> ""
                                }
                                if (pfFormat.isNotBlank()) {
                                    PfFormatBox(pfFormat, onCopy = { onCopy(pfFormat) })
                                }
                            }
                        }
                    }

                    inspected is InspectedType.Struct -> {
                        StructUnionLayoutView(
                            fields = inspected.info.fields,
                            totalSize = inspected.info.totalSize,
                            isUnion = false,
                            accent = accent,
                            onCopyField = onCopy
                        )
                    }

                    inspected is InspectedType.Union -> {
                        StructUnionLayoutView(
                            fields = inspected.info.fields,
                            totalSize = inspected.info.totalSize,
                            isUnion = true,
                            accent = accent,
                            onCopyField = onCopy
                        )
                    }

                    inspected is InspectedType.Enum -> {
                        EnumLayoutView(
                            enumInfo = inspected.info,
                            accent = accent,
                            onCopyMember = onCopy
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (inspected !is InspectedType.Enum) {
                    Button(
                        onClick = {
                            onDismiss()
                            onRequestLink(inspected.name)
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.types_action_link_short),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        onEdit(inspected, effectiveCSource)
                    },
                    enabled = !busy && !cSourceLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = if (inspected is InspectedType.Enum) Modifier.weight(1f) else Modifier,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.types_action_edit), maxLines = 1)
                }

                FilledTonalIconButton(
                    onClick = { onCopy(effectiveCSource) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.types_action_copy_c),
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        onDismiss()
                        onRequestDelete(inspected.name)
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.types_action_delete),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
}

@Composable
private fun StructUnionLayoutView(
    fields: List<StructField>,
    totalSize: Int,
    isUnion: Boolean,
    accent: Color,
    onCopyField: (String) -> Unit
) {
    if (fields.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.types_no_fields),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }

    val barPalette = listOf(
        Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFFFA726),
        Color(0xFFEC407A), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFAB47BC)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Proportional visual byte layout bar for Structs
        if (!isUnion && totalSize > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                fields.forEachIndexed { idx, field ->
                    val weight = field.size.coerceAtLeast(1).toFloat()
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .height(10.dp)
                            .background(barPalette[idx % barPalette.size].copy(alpha = 0.85f))
                    )
                }
            }
        }

        // Column header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.types_col_offset),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(58.dp)
            )
            Text(
                text = "${stringResource(R.string.types_col_type)} / ${stringResource(R.string.types_col_field)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.types_col_size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(fields.size) { index ->
                val field = fields[index]
                val dotColor = barPalette[index % barPalette.size]
                Surface(
                    onClick = { onCopyField("+0x${field.offset.toString(16)} ${field.displayType} ${field.name}") },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = (if (isUnion) accent else dotColor).copy(alpha = 0.16f),
                            modifier = Modifier.width(58.dp)
                        ) {
                            Text(
                                text = "+0x%02X".format(field.offset),
                                fontFamily = LocalAppFont.current,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isUnion) accent else dotColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = field.name.ifBlank { "(anon)" },
                                fontFamily = LocalAppFont.current,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = rememberHighlightedCCode(field.displayType),
                                fontFamily = LocalAppFont.current,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (field.size > 0) {
                            Text(
                                text = "${field.size}B",
                                fontFamily = LocalAppFont.current,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnumLayoutView(
    enumInfo: EnumTypeInfo,
    accent: Color,
    onCopyMember: (String) -> Unit
) {
    if (enumInfo.members.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.types_no_fields),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.types_col_name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.types_col_dec),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp)
            )
            Text(
                text = stringResource(R.string.types_col_hex),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(enumInfo.members, key = { "${it.name}_${it.value}" }) { member ->
                val hexVal = "0x${member.value.toULong().toString(16).uppercase()}"
                Surface(
                    onClick = { onCopyMember("${member.name} = $hexVal") },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = member.name,
                            fontFamily = LocalAppFont.current,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = member.value.toString(),
                            fontFamily = LocalAppFont.current,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = hexVal,
                                fontFamily = LocalAppFont.current,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CSourceCodeBox(code: String) {
    val highlighted = rememberHighlightedCCode(code)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Text(
                text = highlighted,
                fontFamily = LocalAppFont.current,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun PfFormatBox(format: String, onCopy: () -> Unit) {
    Surface(
        onClick = onCopy,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.types_pf_format),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = format,
                    fontFamily = LocalAppFont.current,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SheetSegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SizePill(bytes: Int, accent: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = "${bytes} B (0x${bytes.toString(16).uppercase()})",
            fontFamily = LocalAppFont.current,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── C Declaration Editor Dialog (td / edit) ──

@Composable
internal fun CDeclarationEditorDialog(
    editingTypeName: String?,
    initialCode: String,
    busy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CDeclarationEditorCard(
            editingTypeName = editingTypeName,
            initialCode = initialCode,
            busy = busy,
            errorMessage = errorMessage,
            onDismiss = onDismiss,
            onSubmit = onSubmit,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 560.dp)
        )
    }
}

@Composable
internal fun CDeclarationEditorCard(
    editingTypeName: String?,
    initialCode: String,
    busy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var code by remember(editingTypeName, initialCode) { mutableStateOf(initialCode) }
    val unbalancedBraces = remember(code) {
        val open = code.count { it == '{' }
        val close = code.count { it == '}' }
        open != close
    }

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
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (editingTypeName != null) {
                                stringResource(R.string.types_editor_edit_title, editingTypeName)
                            } else {
                                stringResource(R.string.types_editor_new_title)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.types_editor_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !busy) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.func_close))
                    }
                }

                AnimatedVisibility(visible = busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Skeleton Templates (for creating new declarations)
                    if (editingTypeName == null) {
                        Text(
                            text = stringResource(R.string.types_editor_templates),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(cDeclarationTemplates, key = { it.label }) { tpl ->
                                Surface(
                                    onClick = {
                                        code = if (code.isBlank()) tpl.code else "$code\n\n${tpl.code}"
                                    },
                                    enabled = !busy,
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                ) {
                                    Text(
                                        text = "+ ${tpl.label}",
                                        fontFamily = LocalAppFont.current,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Code Editor Field
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.types_editor_placeholder),
                                fontFamily = LocalAppFont.current,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        },
                        enabled = !busy,
                        minLines = 6,
                        maxLines = 12,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = LocalAppFont.current,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp
                        ),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Quick-Insert C Token Bar
                    Text(
                        text = stringResource(R.string.types_editor_quick_tokens),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickCTokens, key = { it }) { token ->
                            Surface(
                                onClick = { code += token },
                                enabled = !busy,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = token.replace("\n", "↵").trim(),
                                    fontFamily = LocalAppFont.current,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (unbalancedBraces && code.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = stringResource(R.string.types_editor_unbalanced_braces),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = LocalAppFont.current,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy) {
                        Text(stringResource(R.string.func_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(code) },
                        enabled = !busy && code.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (editingTypeName != null) R.string.types_editor_save else R.string.types_editor_apply
                            )
                        )
                    }
                }
            }
    }
}

// ── Import .h Header Dialog (to) ──

@Composable
internal fun ImportHeaderDialog(
    busy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onBrowseHeaderFile: () -> Unit,
    onImportPath: (String) -> Unit
) {
    var pathInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Filled.FileOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.types_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.types_import_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onBrowseHeaderFile,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.types_import_browse))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    label = { Text(stringResource(R.string.types_import_path_label)) },
                    placeholder = {
                        Text(
                            stringResource(R.string.types_import_path_hint),
                            fontFamily = LocalAppFont.current,
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    enabled = !busy,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalAppFont.current),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImportPath(pathInput.trim()) },
                enabled = !busy && pathInput.isNotBlank()
            ) {
                Text(stringResource(R.string.types_import_load_path))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.func_cancel))
            }
        }
    )
}

// ── Link Type to Address Dialog (tl) ──

@Composable
internal fun LinkTypeDialog(
    initialTypeName: String?,
    availableTypes: List<String>,
    cursorAddress: Long,
    busy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (typeName: String, address: Long) -> Unit
) {
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LinkTypeDialogCard(
            initialTypeName = initialTypeName,
            availableTypes = availableTypes,
            cursorAddress = cursorAddress,
            busy = busy,
            errorMessage = errorMessage,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
        )
    }
}

@Composable
internal fun LinkTypeDialogCard(
    initialTypeName: String?,
    availableTypes: List<String>,
    cursorAddress: Long,
    busy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (typeName: String, address: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by remember(initialTypeName) { mutableStateOf(initialTypeName.orEmpty()) }
    var addrText by remember(cursorAddress) {
        mutableStateOf("0x${cursorAddress.toULong().toString(16).uppercase()}")
    }
    val parsedAddress = remember(addrText) { TypesJsonParser.parseUnsignedAddress(addrText) }
    val matchingTypes = remember(availableTypes, selectedType) {
        val needle = selectedType.trim()
        if (needle.isEmpty()) availableTypes.take(25)
        else availableTypes.filter { it.contains(needle, ignoreCase = true) }.take(25)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.types_link_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.types_link_dialog_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !busy) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.func_close))
                    }
                }

                AnimatedVisibility(visible = busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = { selectedType = it },
                        label = { Text(stringResource(R.string.types_link_type_label)) },
                        placeholder = { Text(stringResource(R.string.types_link_type_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (matchingTypes.isNotEmpty() && selectedType.trim() !in availableTypes) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 148.dp),
                                contentPadding = PaddingValues(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(matchingTypes, key = { it }) { candidate ->
                                    Surface(
                                        onClick = { selectedType = candidate },
                                        enabled = !busy,
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = candidate,
                                            fontFamily = LocalAppFont.current,
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = addrText,
                        onValueChange = { addrText = it },
                        label = { Text(stringResource(R.string.types_link_addr_label)) },
                        placeholder = { Text(stringResource(R.string.types_link_addr_hint)) },
                        isError = addrText.isNotBlank() && parsedAddress == null,
                        supportingText = if (addrText.isNotBlank() && parsedAddress == null) {
                            { Text(stringResource(R.string.types_link_invalid_addr)) }
                        } else null,
                        singleLine = true,
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalAppFont.current),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    val cursorHex = "0x${cursorAddress.toULong().toString(16).uppercase()}"
                    Surface(
                        onClick = { addrText = cursorHex },
                        enabled = !busy,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = stringResource(R.string.types_link_use_cursor, cursorHex),
                            fontFamily = LocalAppFont.current,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy) {
                        Text(stringResource(R.string.func_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val addr = parsedAddress ?: return@Button
                            val cleanType = selectedType.trim()
                            if (cleanType.isNotEmpty()) onConfirm(cleanType, addr)
                        },
                        enabled = !busy && selectedType.isNotBlank() && parsedAddress != null,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.types_link_confirm))
                    }
                }
            }
    }
}

@Composable
internal fun DeleteTypeConfirmDialog(
    typeName: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.types_delete_title)) },
        text = { Text(stringResource(R.string.types_delete_message, typeName)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.types_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.func_cancel))
            }
        }
    )
}

internal fun typeKindAccent(kind: EditableTypeKind, isDark: Boolean): Color = when (kind) {
    EditableTypeKind.STRUCT -> if (isDark) Color(0xFF7986CB) else Color(0xFF3949AB)
    EditableTypeKind.ENUM -> if (isDark) Color(0xFF4DB6AC) else Color(0xFF00796B)
    EditableTypeKind.UNION -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
    EditableTypeKind.TYPEDEF -> if (isDark) Color(0xFFBA68C8) else Color(0xFF7B1FA2)
}

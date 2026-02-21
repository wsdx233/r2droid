package top.wsdx233.r2droid.feature.r2frida.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.FilterableList
import top.wsdx233.r2droid.core.ui.components.ListItemActions
import top.wsdx233.r2droid.core.ui.components.UnifiedListItemWrapper
import top.wsdx233.r2droid.feature.r2frida.data.*

private fun parseHexAddr(addr: String): Long? = try {
    java.lang.Long.decode(addr)
} catch (_: Exception) { null }

// ── Library List ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FridaLibraryList(
    items: List<FridaLibrary>?, actions: ListItemActions,
    cursorAddress: Long, onSeek: (Long) -> Unit, onRefresh: () -> Unit
) {
    if (items == null) { LoadingBox(); return }
    FilterableList(
        items = items,
        filterPredicate = { item, q -> item.name.contains(q, true) || item.path.contains(q, true) },
        placeholder = stringResource(R.string.r2frida_search_libraries),
        onRefresh = onRefresh
    ) { lib ->
        val base = parseHexAddr(lib.base)
        val isCurrent = base != null && cursorAddress >= base && cursorAddress < base + lib.size
        val fullText = "Library: ${lib.name}, Base: ${lib.base}, Size: ${lib.size}, Path: ${lib.path}"
        FridaLibraryItemWrapper(
            title = lib.name, address = base, fullText = fullText,
            actions = actions, isCurrent = isCurrent,
            onClick = { base?.let(onSeek) }
        ) {
            FridaItemContent(lib.name, MaterialTheme.colorScheme.primary,
                tags = listOf("0x${lib.size.toString(16)} bytes", lib.base),
                subtitle = lib.path)
        }
    }
}

// ── Entry List ──

@Composable
fun FridaEntryList(items: List<FridaEntry>?, actions: ListItemActions, onRefresh: () -> Unit) {
    if (items == null) { LoadingBox(); return }
    FilterableList(
        items = items,
        filterPredicate = { item, q -> item.name.contains(q, true) },
        placeholder = stringResource(R.string.r2frida_search_entries),
        onRefresh = onRefresh
    ) { entry ->
        val addr = parseHexAddr(entry.address)
        FridaItemWrapper(entry.name, addr, "Entry: ${entry.name}, Addr: ${entry.address}, Module: ${entry.moduleName}", actions) {
            FridaItemContent(entry.name, MaterialTheme.colorScheme.tertiary,
                tags = listOf(entry.moduleName), badge = entry.address)
        }
    }
}

// ── Export List (reused for exports, symbols, sections) ──

@Composable
fun FridaExportList(items: List<FridaExport>?, actions: ListItemActions, onRefresh: () -> Unit, searchHint: String) {
    if (items == null) { LoadingBox(); return }
    FilterableList(
        items = items,
        filterPredicate = { item, q -> item.name.contains(q, true) },
        placeholder = searchHint,
        onRefresh = onRefresh
    ) { export ->
        val addr = parseHexAddr(export.address)
        FridaItemWrapper(export.name, addr, "${export.type}: ${export.name}, Addr: ${export.address}", actions) {
            FridaItemContent(export.name, MaterialTheme.colorScheme.secondary,
                tags = listOf(export.type), badge = export.address)
        }
    }
}

// ── String List ──

@Composable
fun FridaStringList(items: List<FridaString>?, actions: ListItemActions, onRefresh: () -> Unit) {
    if (items == null) { LoadingBox(); return }
    FilterableList(
        items = items,
        filterPredicate = { item, q -> item.text.contains(q, true) },
        placeholder = stringResource(R.string.r2frida_search_strings),
        onRefresh = onRefresh
    ) { str ->
        val addr = parseHexAddr(str.base)
        FridaItemWrapper(str.text, addr, "String: ${str.text}, Base: ${str.base}", actions) {
            FridaItemContent(str.text, MaterialTheme.colorScheme.tertiary,
                badge = str.base, maxTitleLines = 2)
        }
    }
}

// ── Shared helpers ──

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun FridaItemWrapper(
    title: String, address: Long?, fullText: String,
    actions: ListItemActions, content: @Composable () -> Unit
) {
    UnifiedListItemWrapper(
        title = title, address = address, fullText = fullText,
        actions = actions, shape = RoundedCornerShape(12.dp), elevation = 1.dp
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FridaLibraryItemWrapper(
    title: String, address: Long?, fullText: String,
    actions: ListItemActions, isCurrent: Boolean,
    onClick: () -> Unit, content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    var expanded by remember { mutableStateOf(false) }
    var menuScreen by remember { mutableStateOf("Main") }
    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val borderMod = if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape)
            .then(borderMod)
            .clip(shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed && !change.previousPressed) {
                                tapOffset = change.position
                            }
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { expanded = true }
            )
    ) {
        content()

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false; menuScreen = "Main" },
            offset = with(density) { DpOffset(tapOffset.x.toDp(), tapOffset.y.toDp()) }
        ) {
            LibraryDropdownContent(title, address, fullText, actions, menuScreen,
                onMenuChange = { menuScreen = it },
                onDismiss = { expanded = false; menuScreen = "Main" })
        }
    }
}

@Composable
private fun LibraryDropdownContent(
    title: String, address: Long?, fullText: String,
    actions: ListItemActions, menuScreen: String,
    onMenuChange: (String) -> Unit, onDismiss: () -> Unit
) {
    when (menuScreen) {
        "Main" -> {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_copy)) },
                onClick = { onMenuChange("Copy") },
                trailingIcon = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
            )
            if (address != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_jump)) },
                    onClick = { onMenuChange("Jump") },
                    trailingIcon = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_xrefs)) },
                    onClick = { onDismiss(); actions.onShowXrefs(address) }
                )
            }
        }
        "Copy" -> {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_back)) },
                onClick = { onMenuChange("Main") },
                leadingIcon = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, null) }
            )
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_name)) },
                onClick = { actions.onCopy(title); onDismiss() })
            if (address != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_address)) },
                    onClick = { actions.onCopy("0x%X".format(address)); onDismiss() })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_all)) },
                onClick = { actions.onCopy(fullText); onDismiss() })
        }
        "Jump" -> {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_back)) },
                onClick = { onMenuChange("Main") },
                leadingIcon = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, null) }
            )
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_hex_viewer)) },
                onClick = { if (address != null) actions.onJumpToHex(address); onDismiss() })
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_disassembly)) },
                onClick = { if (address != null) actions.onJumpToDisasm(address); onDismiss() })
        }
    }
}

@Composable
private fun FridaItemContent(
    title: String, accent: Color,
    tags: List<String> = emptyList(), badge: String? = null,
    subtitle: String? = null, maxTitleLines: Int = 1
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    color = accent, maxLines = maxTitleLines, overflow = TextOverflow.Ellipsis)
                if (tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        tags.forEach { Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (badge != null) {
                Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.12f)) {
                    Text(badge, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = accent)
                }
            }
        }
    }
}

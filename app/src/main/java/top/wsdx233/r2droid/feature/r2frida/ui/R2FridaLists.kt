package top.wsdx233.r2droid.feature.r2frida.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun FridaLibraryList(items: List<FridaLibrary>?, actions: ListItemActions, onRefresh: () -> Unit) {
    if (items == null) { LoadingBox(); return }
    FilterableList(
        items = items,
        filterPredicate = { item, q -> item.name.contains(q, true) || item.path.contains(q, true) },
        placeholder = stringResource(R.string.r2frida_search_libraries),
        onRefresh = onRefresh
    ) { lib ->
        val addr = parseHexAddr(lib.base)
        FridaItemWrapper(lib.name, addr, "Library: ${lib.name}, Base: ${lib.base}, Size: ${lib.size}, Path: ${lib.path}", actions) {
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

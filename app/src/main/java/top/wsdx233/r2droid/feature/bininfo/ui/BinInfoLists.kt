package top.wsdx233.r2droid.feature.bininfo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.FunctionInfo
import top.wsdx233.r2droid.core.data.model.ImportInfo
import top.wsdx233.r2droid.core.data.model.Relocation
import top.wsdx233.r2droid.core.data.model.Section
import top.wsdx233.r2droid.core.data.model.StringInfo
import top.wsdx233.r2droid.core.data.model.Symbol
import androidx.compose.foundation.lazy.LazyListState
import top.wsdx233.r2droid.core.ui.components.FilterableList
import top.wsdx233.r2droid.core.ui.components.ListItemActions
import top.wsdx233.r2droid.core.ui.components.UnifiedListItemWrapper
import top.wsdx233.r2droid.ui.theme.LocalAppFont

// ── Shared helper composables ──

@Composable
private fun AddressBadge(address: Long, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = "0x${address.toString(16)}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = LocalAppFont.current,
            color = color
        )
    }
}

@Composable
private fun InfoTag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}


@Composable
private fun AccentBar(color: Color) {
    Box(
        modifier = Modifier
            .padding(top = 1.dp)
            .width(8.dp)
            .fillMaxHeight()
            .background(color)
            .shadow(elevation = 1.dp)

    )
}


// a simple preview of Tinted Item Surface
@Preview(backgroundColor = 0xFFFFFF)
@Composable
private fun TintedItemSurfacePreview() {
    TintedItemSurface(Color.Red) {
        Box(modifier = Modifier.fillMaxWidth().padding(10.dp))
    }
}

@Composable
private fun TintedItemSurface(
    accentColor: Color,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    content: @Composable () -> Unit
) {
    Surface(
        color = accentColor.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
        shape = shape
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

// ── Section ──

@Composable
fun SectionList(
    sections: List<Section>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    listState: LazyListState? = null
) {
    FilterableList(
        items = sections,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = stringResource(R.string.search_sections_hint),
        onRefresh = onRefresh,
        externalSearchQuery = searchQuery,
        onExternalSearchQueryChange = onSearchQueryChange,
        externalListState = listState
    ) { section ->
        SectionItem(section, actions)
    }
}

@Composable
fun SectionItem(section: Section, actions: ListItemActions) {
    val accent = MaterialTheme.colorScheme.tertiary
    UnifiedListItemWrapper(
        title = section.name,
        address = section.vAddr,
        fullText = "Section: ${section.name}, Size: ${section.size}, Perm: ${section.perm}, VAddr: 0x${section.vAddr.toString(16)}",
        actions = actions,
        shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = section.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoTag(section.perm, accent)
                        InfoTag("Size: ${section.size}")
                    }
                }
                AddressBadge(section.vAddr, accent)
            }
        }
    }
}

@Composable
fun SymbolList(
    symbols: List<Symbol>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    listState: LazyListState? = null
) {
    FilterableList(
        items = symbols,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = stringResource(R.string.search_symbols_hint),
        onRefresh = onRefresh,
        externalSearchQuery = searchQuery,
        onExternalSearchQueryChange = onSearchQueryChange,
        externalListState = listState
    ) { symbol ->
        SymbolItem(symbol, actions)
    }
}

@Composable
fun SymbolItem(symbol: Symbol, actions: ListItemActions) {
    val accent = MaterialTheme.colorScheme.primary
    UnifiedListItemWrapper(
        title = symbol.name,
        address = symbol.vAddr,
        fullText = "Symbol: ${symbol.name}, Type: ${symbol.type}, VAddr: 0x${symbol.vAddr.toString(16)}",
        actions = actions,
        shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = symbol.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    InfoTag(symbol.type)
                }
                AddressBadge(symbol.vAddr, accent)
            }
        }
    }
}

@Composable
fun ImportList(
    imports: List<ImportInfo>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    listState: LazyListState? = null
) {
    FilterableList(
        items = imports,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = stringResource(R.string.search_imports_hint),
        onRefresh = onRefresh,
        externalSearchQuery = searchQuery,
        onExternalSearchQueryChange = onSearchQueryChange,
        externalListState = listState
    ) { item ->
        ImportItem(item, actions)
    }
}

@Composable
fun ImportItem(importInfo: ImportInfo, actions: ListItemActions) {
    val accent = MaterialTheme.colorScheme.error
    UnifiedListItemWrapper(
        title = importInfo.name,
        address = if(importInfo.plt != 0L) importInfo.plt else null,
        fullText = "Import: ${importInfo.name}, Type: ${importInfo.type}, PLT: 0x${importInfo.plt.toString(16)}",
        actions = actions,
        shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = importInfo.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    InfoTag(importInfo.type)
                }
                if (importInfo.plt != 0L) {
                    AddressBadge(importInfo.plt, accent)
                }
            }
        }
    }
}

@Composable
fun RelocationList(
    relocations: List<Relocation>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    listState: LazyListState? = null
) {
    FilterableList(
        items = relocations,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = stringResource(R.string.search_relocations_hint),
        onRefresh = onRefresh,
        externalSearchQuery = searchQuery,
        onExternalSearchQueryChange = onSearchQueryChange,
        externalListState = listState
    ) { relocation ->
        RelocationItem(relocation, actions)
    }
}

@Composable
fun RelocationItem(relocation: Relocation, actions: ListItemActions) {
    val accent = MaterialTheme.colorScheme.secondary
    UnifiedListItemWrapper(
        title = relocation.name,
        address = relocation.vAddr,
        fullText = "Relocation: ${relocation.name}, Type: ${relocation.type}, VAddr: 0x${relocation.vAddr.toString(16)}",
        actions = actions,
        shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = relocation.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    InfoTag(relocation.type)
                }
                AddressBadge(relocation.vAddr, accent)
            }
        }
    }
}

@Composable
fun StringList(
    strings: List<StringInfo>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    listState: LazyListState? = null
) {
    FilterableList(
        items = strings,
        filterPredicate = { item, query -> item.string.contains(query, ignoreCase = true) },
        placeholder = stringResource(R.string.search_strings_hint),
        onRefresh = onRefresh,
        externalSearchQuery = searchQuery,
        onExternalSearchQueryChange = onSearchQueryChange,
        externalListState = listState
    ) { str ->
        StringItem(str, actions)
    }
}

@Composable
fun StringItem(stringInfo: StringInfo, actions: ListItemActions) {
    val accent = MaterialTheme.colorScheme.tertiary
    UnifiedListItemWrapper(
        title = stringInfo.string,
        address = stringInfo.vAddr,
        fullText = "String: ${stringInfo.string}, Section: ${stringInfo.section}, VAddr: 0x${stringInfo.vAddr.toString(16)}",
        actions = actions,
        shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringInfo.string,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoTag(stringInfo.section)
                        InfoTag(stringInfo.type, accent)
                    }
                }
                AddressBadge(stringInfo.vAddr, accent)
            }
        }
    }
}

@Composable
fun FunctionList(
    functions: List<FunctionInfo>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    listState: LazyListState? = null
) {
    FilterableList(
        items = functions,
        filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) },
        placeholder = stringResource(R.string.search_functions_hint),
        onRefresh = onRefresh,
        externalSearchQuery = searchQuery,
        onExternalSearchQueryChange = onSearchQueryChange,
        externalListState = listState
    ) { func ->
        FunctionItem(func, actions)
    }
}

@Composable
fun FunctionItem(func: FunctionInfo, actions: ListItemActions) {
    val accent = MaterialTheme.colorScheme.primary
    UnifiedListItemWrapper(
        title = func.name,
        address = func.addr,
        fullText = "Function: ${func.name}, Addr: 0x${func.addr.toString(16)}, Size: ${func.size}, BBs: ${func.nbbs}, Signature: ${func.signature}",
        actions = actions,
        shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = func.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoTag("sz:${func.size}")
                        InfoTag("bbs:${func.nbbs}")
                    }
                    if (func.signature.isNotEmpty()) {
                        Text(
                            text = func.signature,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                AddressBadge(func.addr, accent)
            }
        }
    }
}

package top.wsdx233.r2droid.feature.bininfo.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.db.FunctionEntity
import top.wsdx233.r2droid.core.data.db.ImportEntity
import top.wsdx233.r2droid.core.data.db.RelocationEntity
import top.wsdx233.r2droid.core.data.db.SectionEntity
import top.wsdx233.r2droid.core.data.db.StringEntity
import top.wsdx233.r2droid.core.data.db.SymbolEntity
import top.wsdx233.r2droid.core.data.model.FunctionInfo
import top.wsdx233.r2droid.core.data.model.ImportInfo
import top.wsdx233.r2droid.core.data.model.Relocation
import top.wsdx233.r2droid.core.data.model.Section
import top.wsdx233.r2droid.core.data.model.StringInfo
import top.wsdx233.r2droid.core.data.model.Symbol
import top.wsdx233.r2droid.core.ui.components.AutoHideScrollbar
import top.wsdx233.r2droid.core.ui.components.FilterableList
import top.wsdx233.r2droid.core.ui.components.ListItemActions
import top.wsdx233.r2droid.core.ui.components.SearchBar
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
        shape = RoundedCornerShape(12.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(12.dp)) {
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
        filterPredicate = { item, query -> (item.realname ?: item.name).contains(query, ignoreCase = true) },
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
    val displayName = symbol.realname ?: symbol.name
    UnifiedListItemWrapper(
        title = displayName,
        address = symbol.vAddr,
        fullText = "Symbol: ${symbol.name}, Type: ${symbol.type}, VAddr: 0x${symbol.vAddr.toString(16)}",
        actions = actions,
        shape = RoundedCornerShape(12.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(12.dp)) {
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
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (symbol.realname != null) {
                        Text(
                            text = symbol.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
        shape = RoundedCornerShape(12.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(12.dp)) {
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
        shape = RoundedCornerShape(12.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(12.dp)) {
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
        shape = RoundedCornerShape(12.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(12.dp)) {
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
fun PagingStringList(
    pagingData: Flow<PagingData<StringEntity>>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    GenericPagingList(pagingData, stringResource(R.string.search_strings_hint), onRefresh, searchQuery, onSearchQueryChange, itemKey = { it.vAddr }) { item ->
        StringItem(StringInfo(item.string, item.vAddr, item.section, item.type), actions)
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
        shape = RoundedCornerShape(12.dp),
        elevation = 1.dp
    ) {
        TintedItemSurface(accent, shape = RoundedCornerShape(12.dp)) {
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
                        maxLines = 5,
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

@Composable
private fun ShimmerPlaceholder() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceContainerLow
    val shimmerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, shimmerColor, baseColor),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 300f, 0f)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
    )
}

@Composable
private fun <T : Any> GenericPagingList(
    pagingData: Flow<PagingData<T>>,
    placeholder: String,
    onRefresh: (() -> Unit)?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    itemKey: (T) -> Any,
    itemContent: @Composable (T) -> Unit
) {
    val lazyPagingItems = pagingData.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    // Debounce: only trigger Paging loads after scroll position stays stable for 200ms.
    // Using firstVisibleItemIndex instead of isScrollInProgress because
    // scrollToItem() (used by the scrollbar) is instant and doesn't set isScrollInProgress.
    var scrollSettled by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { idx ->
            scrollSettled = false
            delay(75)
            scrollSettled = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SearchBar(query = searchQuery, onQueryChange = onSearchQueryChange, placeholder = placeholder, modifier = Modifier.weight(1f))
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(count = lazyPagingItems.itemCount, key = lazyPagingItems.itemKey { itemKey(it) }) { index ->
                    val cached = lazyPagingItems.peek(index)
                    if (cached != null) {
                        itemContent(cached)
                    } else {
                        // Only call [index] once to trigger the Paging load,
                        // then rely on peek() on subsequent recompositions.
                        val triggered = remember { mutableStateOf(false) }
                        if (scrollSettled && !triggered.value) {
                            lazyPagingItems[index]
                            triggered.value = true
                        }
                        ShimmerPlaceholder()
                    }
                }
            }
            if (lazyPagingItems.itemCount > 0) {
                AutoHideScrollbar(listState = listState, totalItems = lazyPagingItems.itemCount, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
fun PagingSectionList(
    pagingData: Flow<PagingData<SectionEntity>>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    GenericPagingList(pagingData, stringResource(R.string.search_sections_hint), onRefresh, searchQuery, onSearchQueryChange, itemKey = { it.vAddr }) { item ->
        SectionItem(Section(item.name, item.size, item.vSize, item.perm, item.vAddr, item.pAddr), actions)
    }
}

@Composable
fun PagingSymbolList(
    pagingData: Flow<PagingData<SymbolEntity>>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    GenericPagingList(pagingData, stringResource(R.string.search_symbols_hint), onRefresh, searchQuery, onSearchQueryChange, itemKey = { it.id }) { item ->
        SymbolItem(Symbol(item.name, item.type, item.vAddr, item.pAddr, item.isImported, item.realname), actions)
    }
}

@Composable
fun PagingImportList(
    pagingData: Flow<PagingData<ImportEntity>>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    GenericPagingList(pagingData, stringResource(R.string.search_imports_hint), onRefresh, searchQuery, onSearchQueryChange, itemKey = { it.id }) { item ->
        ImportItem(ImportInfo(item.name, item.ordinal, item.type, item.plt), actions)
    }
}

@Composable
fun PagingRelocationList(
    pagingData: Flow<PagingData<RelocationEntity>>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    GenericPagingList(pagingData, stringResource(R.string.search_relocations_hint), onRefresh, searchQuery, onSearchQueryChange, itemKey = { it.id }) { item ->
        RelocationItem(Relocation(item.name, item.type, item.vAddr, item.pAddr), actions)
    }
}

@Composable
fun PagingFunctionList(
    pagingData: Flow<PagingData<FunctionEntity>>,
    actions: ListItemActions,
    onRefresh: (() -> Unit)? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    GenericPagingList(pagingData, stringResource(R.string.search_functions_hint), onRefresh, searchQuery, onSearchQueryChange, itemKey = { it.addr }) { item ->
        FunctionItem(FunctionInfo(item.name, item.addr, item.size, item.nbbs, item.signature), actions)
    }
}

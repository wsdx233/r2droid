package top.wsdx233.r2droid.feature.r2frida.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.ListItemActions
import top.wsdx233.r2droid.core.ui.components.AutoHideScrollbar
import top.wsdx233.r2droid.core.ui.components.UnifiedListItemWrapper
import top.wsdx233.r2droid.feature.r2frida.data.*

@Composable
private fun CustomLoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun CustomEmptyBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Empty") }
}

// ── Functions Screen (unchanged) ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridaCustomFunctionsScreen(
    functions: List<FridaFunction>?,
    actions: ListItemActions,
    onRefresh: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    if (functions == null) { CustomLoadingBox(); return }
    val filtered = remember(functions, searchQuery) {
        if (searchQuery.isBlank()) functions else functions.filter { it.name.contains(searchQuery, true) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery, onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${filtered.size} items", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, null) }
        }
        if (filtered.isEmpty()) { CustomEmptyBox() }
        else {
            LazyColumn(modifier = Modifier.weight(1f), state = listState) {
                items(filtered) { f ->
                    UnifiedListItemWrapper(
                        title = f.name, fullText = "${f.name} ${f.address}",
                        actions = actions, address = f.address.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0L
                    ) {
                        ListItem(
                            headlineContent = { Text(f.name, style = MaterialTheme.typography.bodyMedium) },
                            supportingContent = { Text(f.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) },
                        )
                    }
                }
            }
        }
    }
}

// ── Toolbar Row 1: Type + Compare + Region ──

@Composable
private fun SearchToolbarRow1(
    searchValueType: SearchValueType,
    searchCompare: SearchCompare,
    onValueTypeChange: (SearchValueType) -> Unit,
    onCompareChange: (SearchCompare) -> Unit,
    onRegionClick: () -> Unit,
    hasResults: Boolean,
    onRefreshValues: () -> Unit,
    maxResults: Int,
    onMaxResultsChange: (Int) -> Unit
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var compareExpanded by remember { mutableStateOf(false) }
    var limitExpanded by remember { mutableStateOf(false) }
    var showCustomLimitDialog by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Type dropdown
        Box {
            FilterChip(
                selected = true,
                onClick = { typeExpanded = true },
                label = { Text(searchValueType.shortLabel, fontSize = 12.sp) },
                leadingIcon = { Text(stringResource(R.string.fsearch_type) + ":", fontSize = 10.sp) }
            )
            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                SearchValueType.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text("${t.shortLabel}: ${t.label}") },
                        onClick = { onValueTypeChange(t); typeExpanded = false },
                        leadingIcon = if (t == searchValueType) {{ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }} else null
                    )
                }
            }
        }

        // Compare dropdown
        Box {
            FilterChip(
                selected = true,
                onClick = { compareExpanded = true },
                label = { Text(searchCompare.symbol, fontSize = 14.sp) }
            )
            DropdownMenu(expanded = compareExpanded, onDismissRequest = { compareExpanded = false }) {
                SearchCompare.entries.forEach { c ->
                    DropdownMenuItem(
                        text = { Text("${c.symbol}  ${c.name}") },
                        onClick = { onCompareChange(c); compareExpanded = false },
                        leadingIcon = if (c == searchCompare) {{ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }} else null
                    )
                }
            }
        }

        // Region button
        FilterChip(
            selected = false,
            onClick = onRegionClick,
            label = { Text(stringResource(R.string.fsearch_select_regions), fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Filled.Memory, null, Modifier.size(16.dp)) }
        )

        // Max results limit button
        Box {
            FilterChip(
                selected = true,
                onClick = { limitExpanded = true },
                label = {
                    val label = if (maxResults >= 1000000) "${maxResults / 1000}K" else maxResults.toString()
                    Text(label, fontSize = 12.sp)
                },
                leadingIcon = { Text(stringResource(R.string.fsearch_limit) + ":", fontSize = 10.sp) }
            )
            DropdownMenu(expanded = limitExpanded, onDismissRequest = { limitExpanded = false }) {
                listOf(1000, 5000, 10000, 50000, 100000).forEach { n ->
                    DropdownMenuItem(
                        text = { Text(n.toString()) },
                        onClick = { onMaxResultsChange(n); limitExpanded = false },
                        leadingIcon = if (n == maxResults) {{ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }} else null
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.fsearch_limit_custom)) },
                    onClick = { limitExpanded = false; showCustomLimitDialog = true }
                )
            }
        }

        // Refresh values button
        if (hasResults) {
            IconButton(
                onClick = onRefreshValues,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
            }
        }
    }

    if (showCustomLimitDialog) {
        var customValue by remember { mutableStateOf(maxResults.toString()) }
        AlertDialog(
            onDismissRequest = { showCustomLimitDialog = false },
            title = { Text(stringResource(R.string.fsearch_limit_custom_title)) },
            text = {
                OutlinedTextField(
                    value = customValue,
                    onValueChange = { customValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("50000") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = customValue.toIntOrNull()
                        if (n != null && n > 0) onMaxResultsChange(n)
                        showCustomLimitDialog = false
                    },
                    enabled = customValue.toIntOrNull()?.let { it > 0 } == true
                ) { Text(stringResource(R.string.fsearch_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomLimitDialog = false }) {
                    Text(stringResource(R.string.fsearch_cancel))
                }
            }
        )
    }
}

// ── GG-Style Memory Search Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridaSearchScreen(
    results: List<FridaSearchResult>?,
    isSearching: Boolean,
    searchValueType: SearchValueType,
    searchCompare: SearchCompare,
    selectedRegions: Set<MemoryRegion>,
    frozenAddresses: Map<String, String>,
    searchError: String?,
    onSearch: (input: String, rangeMin: String, rangeMax: String) -> Unit,
    onRefine: (filterMode: String, targetVal: String, rangeMin: String, rangeMax: String, expression: String) -> Unit,
    onClear: () -> Unit,
    onWriteValue: (address: String, value: String) -> Unit,
    onBatchWrite: (value: String) -> Unit,
    onToggleFreeze: (address: String, value: String) -> Unit,
    onValueTypeChange: (SearchValueType) -> Unit,
    onCompareChange: (SearchCompare) -> Unit,
    onRegionsChange: (Set<MemoryRegion>) -> Unit,
    onClearError: () -> Unit,
    onRefreshValues: () -> Unit,
    maxResults: Int,
    onMaxResultsChange: (Int) -> Unit,
    actions: ListItemActions
) {
    var searchInput by remember { mutableStateOf("") }
    var advancedExpanded by remember { mutableStateOf(false) }
    var rangeMin by remember { mutableStateOf("") }
    var rangeMax by remember { mutableStateOf("") }
    var expression by remember { mutableStateOf("") }
    var showRegionDialog by remember { mutableStateOf(false) }
    var showBatchWriteDialog by remember { mutableStateOf(false) }
    var editingResult by remember { mutableStateOf<FridaSearchResult?>(null) }

    Column(Modifier.fillMaxSize()) {
        // ── Row 1: Type selector + Compare + Region button ──
        SearchToolbarRow1(
            searchValueType = searchValueType,
            searchCompare = searchCompare,
            onValueTypeChange = onValueTypeChange,
            onCompareChange = onCompareChange,
            onRegionClick = { showRegionDialog = true },
            hasResults = results != null && results.isNotEmpty(),
            onRefreshValues = onRefreshValues,
            maxResults = maxResults,
            onMaxResultsChange = onMaxResultsChange
        )

        // ── Row 2: Action buttons ──
        SearchToolbarRow2(
            hasResults = results != null && results.isNotEmpty(),
            isSearching = isSearching,
            onNewSearch = {
                if (rangeMin.isNotBlank() && rangeMax.isNotBlank()) {
                    onSearch(searchInput, rangeMin, rangeMax)
                } else {
                    onSearch(searchInput, "", "")
                }
            },
            onRefineExact = { onRefine("exact", searchInput, "", "", "") },
            onRefineIncreased = { onRefine("increased", "", "", "", "") },
            onRefineDecreased = { onRefine("decreased", "", "", "", "") },
            onRefineUnchanged = { onRefine("unchanged", "", "", "", "") },
            onRefineRange = { onRefine("range", "", rangeMin, rangeMax, "") },
            onRefineExpression = { onRefine("expression", "", "", "", expression) },
            onBatchWrite = { showBatchWriteDialog = true },
            onClear = onClear,
            searchInput = searchInput,
            rangeMin = rangeMin,
            rangeMax = rangeMax,
            expression = expression
        )

        // ── Search input ──
        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            placeholder = { Text(stringResource(R.string.fsearch_value_hint)) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
        )

        // ── Collapsible advanced options ──
        AdvancedOptionsSection(
            expanded = advancedExpanded,
            onToggle = { advancedExpanded = !advancedExpanded },
            rangeMin = rangeMin,
            rangeMax = rangeMax,
            expression = expression,
            onRangeMinChange = { rangeMin = it },
            onRangeMaxChange = { rangeMax = it },
            onExpressionChange = { expression = it }
        )

        // ── Error banner ──
        if (searchError != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(searchError, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    IconButton(onClick = onClearError) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
        }

        // ── Results ──
        if (isSearching) {
            CustomLoadingBox()
        } else if (results != null) {
            Text(
                stringResource(R.string.fsearch_result_count, results.size),
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium
            )
            if (results.isEmpty()) {
                CustomEmptyBox()
            } else {
                SearchResultList(
                    results = results,
                    frozenAddresses = frozenAddresses,
                    actions = actions,
                    onEdit = { editingResult = it },
                    onToggleFreeze = onToggleFreeze,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // ── Dialogs ──
    if (showRegionDialog) {
        MemoryRegionDialog(
            selected = selectedRegions,
            onDismiss = { showRegionDialog = false },
            onConfirm = { onRegionsChange(it); showRegionDialog = false }
        )
    }
    if (showBatchWriteDialog) {
        BatchWriteDialog(
            count = results?.size ?: 0,
            onDismiss = { showBatchWriteDialog = false },
            onConfirm = { onBatchWrite(it); showBatchWriteDialog = false }
        )
    }
    editingResult?.let { result ->
        EditValueDialog(
            result = result,
            searchValueType = searchValueType,
            isFrozen = frozenAddresses.containsKey(result.address),
            onDismiss = { editingResult = null },
            onSave = { newVal, freeze ->
                onWriteValue(result.address, newVal)
                if (freeze) onToggleFreeze(result.address, newVal)
                editingResult = null
            }
        )
    }
}

// ── Toolbar Row 2: Action buttons (scrollable) ──

@Composable
private fun SearchToolbarRow2(
    hasResults: Boolean,
    isSearching: Boolean,
    onNewSearch: () -> Unit,
    onRefineExact: () -> Unit,
    onRefineIncreased: () -> Unit,
    onRefineDecreased: () -> Unit,
    onRefineUnchanged: () -> Unit,
    onRefineRange: () -> Unit,
    onRefineExpression: () -> Unit,
    onBatchWrite: () -> Unit,
    onClear: () -> Unit,
    searchInput: String,
    rangeMin: String,
    rangeMax: String,
    expression: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // New Search
        FilledTonalButton(
            onClick = onNewSearch,
            enabled = !isSearching && searchInput.isNotBlank(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Filled.Search, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.fsearch_new_search), fontSize = 12.sp)
        }

        // Refine (exact)
        FilledTonalButton(
            onClick = onRefineExact,
            enabled = !isSearching && hasResults && searchInput.isNotBlank(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Filled.FilterAlt, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.fsearch_refine), fontSize = 12.sp)
        }

        // Fuzzy: Increased
        OutlinedButton(
            onClick = onRefineIncreased,
            enabled = !isSearching && hasResults,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.fsearch_fuzzy_increased), fontSize = 11.sp) }

        // Fuzzy: Decreased
        OutlinedButton(
            onClick = onRefineDecreased,
            enabled = !isSearching && hasResults,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.fsearch_fuzzy_decreased), fontSize = 11.sp) }

        // Fuzzy: Unchanged
        OutlinedButton(
            onClick = onRefineUnchanged,
            enabled = !isSearching && hasResults,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.fsearch_fuzzy_unchanged), fontSize = 11.sp) }

        // Range refine
        if (rangeMin.isNotBlank() && rangeMax.isNotBlank()) {
            OutlinedButton(
                onClick = onRefineRange,
                enabled = !isSearching && hasResults,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text(stringResource(R.string.fsearch_range_search), fontSize = 11.sp) }
        }

        // Expression refine
        if (expression.isNotBlank()) {
            OutlinedButton(
                onClick = onRefineExpression,
                enabled = !isSearching && hasResults,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("fx", fontSize = 11.sp) }
        }

        // Batch write
        if (hasResults) {
            FilledTonalButton(
                onClick = onBatchWrite,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.fsearch_batch_write), fontSize = 12.sp)
            }
        }

        // Reset
        if (hasResults) {
            OutlinedButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.Clear, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.fsearch_reset), fontSize = 12.sp)
            }
        }
    }
}

// ── Collapsible Advanced Options ──

@Composable
private fun AdvancedOptionsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    rangeMin: String,
    rangeMax: String,
    expression: String,
    onRangeMinChange: (String) -> Unit,
    onRangeMaxChange: (String) -> Unit,
    onExpressionChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.fsearch_advanced),
                    style = MaterialTheme.typography.labelMedium
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    // Range search
                    Text(
                        stringResource(R.string.fsearch_range_search),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = rangeMin,
                            onValueChange = onRangeMinChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.fsearch_range_min)) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        OutlinedTextField(
                            value = rangeMax,
                            onValueChange = onRangeMaxChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.fsearch_range_max)) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Custom expression
                    Text(
                        stringResource(R.string.fsearch_expression),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = expression,
                        onValueChange = onExpressionChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.fsearch_expression_hint)) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ── Search Result List ──

@Composable
private fun SearchResultList(
    results: List<FridaSearchResult>,
    frozenAddresses: Map<String, String>,
    actions: ListItemActions,
    onEdit: (FridaSearchResult) -> Unit,
    onToggleFreeze: (address: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        items(results) { r ->
            val isFrozen = frozenAddresses.containsKey(r.address)
            UnifiedListItemWrapper(
                title = r.address,
                fullText = "${r.address} = ${r.value}",
                actions = actions,
                address = r.address.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0L
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            r.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    supportingContent = {
                        Text(
                            r.value,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Freeze toggle
                            IconButton(
                                onClick = { onToggleFreeze(r.address, r.value) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (isFrozen) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isFrozen) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Edit button
                            IconButton(
                                onClick = { onEdit(r) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
        AutoHideScrollbar(
            listState = listState,
            totalItems = results.size,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

// ── Memory Region Selection Dialog ──

@Composable
private fun MemoryRegionDialog(
    selected: Set<MemoryRegion>,
    onDismiss: () -> Unit,
    onConfirm: (Set<MemoryRegion>) -> Unit
) {
    var current by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fsearch_select_regions_title)) },
        text = {
            Column {
                MemoryRegion.entries.forEach { region ->
                    val label = when (region) {
                        MemoryRegion.ALL -> stringResource(R.string.fsearch_mem_all)
                        MemoryRegion.JAVA_HEAP -> stringResource(R.string.fsearch_mem_java_heap)
                        MemoryRegion.C_ALLOC -> stringResource(R.string.fsearch_mem_c_alloc)
                        MemoryRegion.C_BSS -> stringResource(R.string.fsearch_mem_c_bss)
                        MemoryRegion.C_DATA -> stringResource(R.string.fsearch_mem_c_data)
                        MemoryRegion.STACK -> stringResource(R.string.fsearch_mem_stack)
                        MemoryRegion.CODE_APP -> stringResource(R.string.fsearch_mem_code_app)
                        MemoryRegion.CODE_SYS -> stringResource(R.string.fsearch_mem_code_sys)
                        MemoryRegion.VIDEO -> stringResource(R.string.fsearch_mem_video)
                        MemoryRegion.OTHER -> stringResource(R.string.fsearch_mem_other)
                        MemoryRegion.BAD -> stringResource(R.string.fsearch_mem_bad)
                    }
                    val checked = current.contains(region)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                current = if (region == MemoryRegion.ALL) {
                                    setOf(MemoryRegion.ALL)
                                } else {
                                    val next = current.toMutableSet()
                                    next.remove(MemoryRegion.ALL)
                                    if (checked) next.remove(region) else next.add(region)
                                    if (next.isEmpty()) setOf(MemoryRegion.ALL) else next
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) {
                Text(stringResource(R.string.fsearch_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.fsearch_cancel))
            }
        }
    )
}

// ── Batch Write Dialog ──

@Composable
private fun BatchWriteDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fsearch_batch_write_title, count)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.fsearch_new_value_hint)) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) { Text(stringResource(R.string.fsearch_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.fsearch_cancel))
            }
        }
    )
}

// ── Edit Value Dialog (GG-style) ──

@Composable
private fun EditValueDialog(
    result: FridaSearchResult,
    searchValueType: SearchValueType,
    isFrozen: Boolean,
    onDismiss: () -> Unit,
    onSave: (newValue: String, freeze: Boolean) -> Unit
) {
    var value by remember { mutableStateOf(result.value) }
    var freeze by remember { mutableStateOf(isFrozen) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fsearch_edit_single_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    result.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${stringResource(R.string.fsearch_type)}: ${searchValueType.shortLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.fsearch_new_value_hint)) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
                Row(
                    Modifier.fillMaxWidth().clickable { freeze = !freeze }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = freeze, onCheckedChange = { freeze = it })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (freeze) stringResource(R.string.fsearch_freeze)
                        else stringResource(R.string.fsearch_freeze_normal),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value, freeze) },
                enabled = value.isNotBlank()
            ) { Text(stringResource(R.string.fsearch_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.fsearch_cancel))
            }
        }
    )
}

// ── Monitor Screen ──

@Composable
fun FridaMonitorScreen(
    events: List<FridaMonitorEvent>,
    isMonitoring: Boolean,
    onStart: (address: String, size: Int) -> Unit,
    onStop: () -> Unit,
    actions: ListItemActions
) {
    var address by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("4096") }

    Column(Modifier.fillMaxSize()) {
        // Config row
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("0x...") },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            )
            OutlinedTextField(
                value = size,
                onValueChange = { size = it },
                modifier = Modifier.width(80.dp),
                placeholder = { Text("Size") },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )
            if (isMonitoring) {
                FilledTonalButton(
                    onClick = onStop,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) { Text("Stop") }
            } else {
                FilledTonalButton(
                    onClick = {
                        if (address.isNotBlank()) {
                            onStart(address, size.toIntOrNull() ?: 4096)
                        }
                    },
                    enabled = address.isNotBlank()
                ) { Text("Start") }
            }
        }

        // Status
        if (isMonitoring) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("Monitoring... ${events.size} events", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Events list
        if (events.isEmpty()) {
            CustomEmptyBox()
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(events) { e ->
                    UnifiedListItemWrapper(
                        title = e.address,
                        fullText = "${e.address} ${e.operation} from ${e.from}",
                        actions = actions,
                        address = e.address.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0L
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "${e.operation.uppercase()} @ ${e.address}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            supportingContent = {
                                Text(
                                    "from: ${e.from}  size: ${e.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
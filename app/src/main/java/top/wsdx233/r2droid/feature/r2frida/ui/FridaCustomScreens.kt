package top.wsdx233.r2droid.feature.r2frida.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.ListItemActions
import top.wsdx233.r2droid.core.ui.components.UnifiedListItemWrapper
import top.wsdx233.r2droid.feature.r2frida.data.FridaFunction
import top.wsdx233.r2droid.feature.r2frida.data.FridaMonitorEvent
import top.wsdx233.r2droid.feature.r2frida.data.FridaSearchResult

@Composable
private fun CustomLoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun CustomEmptyBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Empty") }
}

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
    if (functions == null) {
        CustomLoadingBox()
        return
    }

    val filtered = remember(functions, searchQuery) {
        if (searchQuery.isBlank()) functions else functions.filter {
            it.name.contains(searchQuery, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${filtered.size} items", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, null) }
        }

        if (filtered.isEmpty()) {
            CustomEmptyBox()
        } else {
            LazyColumn(modifier = Modifier.weight(1f), state = listState) {
                items(filtered) { f ->
                    UnifiedListItemWrapper(
                        title = f.name,
                        fullText = "${f.name} ${f.address}",
                        actions = actions,
                        address = f.address.toLongOrNull(16) ?: 0L
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridaSearchScreen(
    results: List<FridaSearchResult>?,
    isSearching: Boolean,
    onSearch: (pattern: String, value: String) -> Unit,
    onRefine: (type: String, value: String) -> Unit,
    onClear: () -> Unit,
    actions: ListItemActions
) {
    var searchVal by remember { mutableStateOf("") }
    var searchType by remember { mutableStateOf("u32") } // u8, u16, u32, u64
    
    val types = listOf("u8", "u16", "u32", "u64")

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.padding(8.dp)) {
            Column(Modifier.padding(16.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = searchType == type,
                            onClick = { searchType = type },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size)
                        ) {
                            Text(type)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchVal,
                    onValueChange = { searchVal = it },
                    label = { Text("Value to Search/Refine") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { 
                            val v = searchVal.toLongOrNull() ?: 0L
                            val pattern = when (searchType) {
                                "u8" -> String.format("%02x", v.toByte())
                                "u16" -> String.format("%02x %02x", (v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())
                                "u32" -> String.format("%02x %02x %02x %02x", (v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(), ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte())
                                else -> String.format("%02x", v.toByte()) // placeholder
                            }
                            onSearch(pattern, searchVal) 
                        },
                        enabled = !isSearching && searchVal.isNotBlank()
                    ) { Text("Search") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onRefine(searchType, searchVal) },
                        enabled = !isSearching && searchVal.isNotBlank() && results != null
                    ) { Text("Refine") }
                }
                if (results != null) {
                    TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear Results")
                    }
                }
            }
        }

        if (isSearching) {
            CustomLoadingBox()
        } else if (results != null) {
            Text("${results.size} matches", Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
            if (results.isEmpty()) {
                CustomEmptyBox()
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(results) { r ->
                        UnifiedListItemWrapper(
                            title = r.address,
                            fullText = "${r.address} = ${r.value}",
                            actions = actions,
                            address = r.address.toLongOrNull(16) ?: 0L
                        ) {
                            ListItem(
                                headlineContent = { Text(r.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) },
                                supportingContent = { Text("Value: ${r.value}", style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FridaMonitorScreen(
    events: List<FridaMonitorEvent>,
    isMonitoring: Boolean,
    onStart: (address: String, size: Int) -> Unit,
    onStop: () -> Unit,
    actions: ListItemActions
) {
    var monitorAddr by remember { mutableStateOf("") }
    var monitorSize by remember { mutableStateOf("4") }

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.padding(8.dp)) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = monitorAddr,
                    onValueChange = { monitorAddr = it },
                    label = { Text("Target Address (e.g. 0x1234)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isMonitoring,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = monitorSize,
                    onValueChange = { monitorSize = it },
                    label = { Text("Size (bytes)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isMonitoring,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                if (isMonitoring) {
                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Stop Monitoring")
                    }
                } else {
                    Button(
                        onClick = { onStart(monitorAddr, monitorSize.toIntOrNull() ?: 4) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = monitorAddr.isNotBlank()
                    ) {
                        Text("Start Monitoring")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${events.size} events", style = MaterialTheme.typography.labelMedium)
        }

        if (events.isEmpty()) {
            CustomEmptyBox()
        } else {
            LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
                items(events) { e ->
                    UnifiedListItemWrapper(
                        title = e.address,
                        fullText = "Op: ${e.operation} From: ${e.from} at ${e.address}",
                        actions = actions,
                        address = e.address.toLongOrNull(16) ?: 0L
                    ) {
                        ListItem(
                            headlineContent = { Text(e.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) },
                            supportingContent = { Text("Op: ${e.operation} From: ${e.from} Time: ${e.time}", style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        }
    }
}

package top.wsdx233.r2droid.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.SearchResult
import top.wsdx233.r2droid.core.ui.components.AutoHideScrollbar
import top.wsdx233.r2droid.core.ui.components.ListItemActions
import top.wsdx233.r2droid.core.ui.components.UnifiedListItemWrapper
import top.wsdx233.r2droid.feature.search.SearchEvent
import top.wsdx233.r2droid.feature.search.SearchType
import top.wsdx233.r2droid.feature.search.SearchUiState
import top.wsdx233.r2droid.feature.search.SearchViewModel
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    actions: ListItemActions,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SearchType.STRING) }
    var useCustomFlags by remember { mutableStateOf(false) }
    var customFlags by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchInputPanel(
            query = query,
            onQueryChange = { query = it },
            selectedType = selectedType,
            onTypeChange = { selectedType = it },
            useCustomFlags = useCustomFlags,
            onUseCustomFlagsChange = { useCustomFlags = it },
            customFlags = customFlags,
            onCustomFlagsChange = { customFlags = it },
            isSearching = uiState is SearchUiState.Searching,
            onSearch = {
                viewModel.onEvent(
                    SearchEvent.ExecuteSearch(
                        query, selectedType,
                        if (useCustomFlags) customFlags else ""
                    )
                )
            },
            onClear = { viewModel.onEvent(SearchEvent.ClearResults) }
        )

        SearchResultArea(uiState = uiState, actions = actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedType: SearchType,
    onTypeChange: (SearchType) -> Unit,
    useCustomFlags: Boolean,
    onUseCustomFlagsChange: (Boolean) -> Unit,
    customFlags: String,
    onCustomFlagsChange: (String) -> Unit,
    isSearching: Boolean,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search type dropdown
        SearchTypeDropdown(
            selectedType = selectedType,
            onTypeChange = onTypeChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Query input + search button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (selectedType == SearchType.CUSTOM)
                            stringResource(R.string.search_custom_hint)
                        else selectedType.hint
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_clear))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSearch,
                enabled = !isSearching && (query.isNotBlank() || selectedType == SearchType.CUSTOM)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .width(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.search_btn))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Custom flags checkbox + input
        if (selectedType != SearchType.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = useCustomFlags,
                    onCheckedChange = onUseCustomFlagsChange
                )
                Text(
                    stringResource(R.string.search_custom_flags),
                    style = MaterialTheme.typography.bodySmall
                )
                if (useCustomFlags) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = customFlags,
                        onValueChange = onCustomFlagsChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.search_custom_flags_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTypeDropdown(
    selectedType: SearchType,
    onTypeChange: (SearchType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedType.label,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = { Text(stringResource(R.string.search_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SearchType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onTypeChange(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchResultArea(
    uiState: SearchUiState,
    actions: ListItemActions
) {
    when (uiState) {
        is SearchUiState.Idle -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.search_idle_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is SearchUiState.Searching -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is SearchUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        is SearchUiState.Success -> {
            if (uiState.results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                SearchResultList(results = uiState.results, actions = actions)
            }
        }
    }
}

@Composable
private fun SearchResultList(
    results: List<SearchResult>,
    actions: ListItemActions
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { result ->
                SearchResultItem(result = result, actions = actions)
            }
        }

        if (results.isNotEmpty()) {
            AutoHideScrollbar(
                listState = listState,
                totalItems = results.size,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    actions: ListItemActions
) {
    UnifiedListItemWrapper(
        title = result.data,
        address = result.addr,
        fullText = "Addr: 0x${result.addr.toString(16)}, Type: ${result.type}, Data: ${result.data}",
        actions = actions
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = result.data,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0x${result.addr.toString(16)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = LocalAppFont.current,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = result.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

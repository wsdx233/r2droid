package top.wsdx233.r2droid.core.ui.components

import androidx.compose.ui.res.stringResource
import top.wsdx233.r2droid.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun <T> FilterableList(
    items: List<T>,
    filterPredicate: (T, String) -> Boolean,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onRefresh: (() -> Unit)? = null,
    externalSearchQuery: String? = null,
    onExternalSearchQueryChange: ((String) -> Unit)? = null,
    externalListState: androidx.compose.foundation.lazy.LazyListState? = null,
    itemContent: @Composable (T) -> Unit
) {
    val actualPlaceholder = placeholder ?: stringResource(R.string.common_search)
    var internalQuery by remember { mutableStateOf("") }
    val searchQuery = externalSearchQuery ?: internalQuery
    val onQueryChange: (String) -> Unit = if (onExternalSearchQueryChange != null) {
        onExternalSearchQueryChange
    } else {
        { internalQuery = it }
    }
    var debouncedQuery by remember { mutableStateOf("") }

    // Debounce logic
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            debouncedQuery = ""
            return@LaunchedEffect
        }
        delay(300) // 300ms debounce
        debouncedQuery = searchQuery
    }

    val filteredItems = remember(debouncedQuery, items) {
        if (debouncedQuery.isBlank()) items
        else items.filter { filterPredicate(it, debouncedQuery) }
    }

    Column(modifier = modifier.fillMaxSize()) {

        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onQueryChange,
                placeholder = actualPlaceholder,
                modifier = Modifier.weight(1f)
            )
            
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.common_refresh),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))

        // Wrap LazyColumn in Box for scrollbar overlay
        Box(modifier = Modifier.weight(1f)) {
            val listState = externalListState ?: rememberLazyListState()
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredItems) { item ->
                    itemContent(item)
                }
            }
            
            // Auto-hiding scrollbar
            if (filteredItems.isNotEmpty()) {
                AutoHideScrollbar(
                    listState = listState,
                    totalItems = filteredItems.size,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search)) },
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
}

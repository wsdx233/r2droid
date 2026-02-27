package top.wsdx233.r2droid.feature.manual

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.ManualNode
import top.wsdx233.r2droid.util.R2CommandHelp
import top.wsdx233.r2droid.util.R2HelpEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R2ManualScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { R2CommandHelp.load(context); true }

    // Navigation stack: empty = root
    val pathStack = remember { mutableStateListOf<String>() }
    var searchQuery by remember { mutableStateOf("") }

    val currentPrefix = pathStack.lastOrNull()
    val title = currentPrefix ?: stringResource(R.string.manual_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (pathStack.isNotEmpty()) pathStack.removeAt(pathStack.lastIndex)
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.manual_search_hint)) },
                singleLine = true
            )

            if (searchQuery.isNotBlank()) {
                SearchResults(searchQuery) { prefix ->
                    pathStack.clear()
                    // Build path to this prefix
                    for (i in 1..prefix.length) {
                        pathStack.add(prefix.substring(0, i))
                    }
                    searchQuery = ""
                }
            } else if (currentPrefix == null) {
                RootList(onNavigate = { pathStack.add(it) })
            } else {
                NodeDetail(currentPrefix, onNavigate = { pathStack.add(it) })
            }
        }
    }
}

@Composable
private fun RootList(onNavigate: (String) -> Unit) {
    val nodes = remember { R2CommandHelp.getCommandTree() }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(nodes) { node ->
            NodeRow(node, onClick = { onNavigate(node.prefix) })
        }
    }
}

@Composable
private fun NodeDetail(prefix: String, onNavigate: (String) -> Unit) {
    val exactEntries = remember(prefix) {
        R2CommandHelp.search(prefix).filter { it.command == prefix }
    }
    val children = remember(prefix) { R2CommandHelp.getChildNodes(prefix) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Show exact match details
        if (exactEntries.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        exactEntries.forEach { entry ->
                            EntryDetail(entry)
                        }
                    }
                }
            }
        }

        // Show children
        if (children.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.manual_subcommands),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(children) { node ->
                NodeRow(node, onClick = { onNavigate(node.prefix) })
            }
        }
    }
}

@Composable
private fun NodeRow(node: ManualNode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            node.prefix,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            node.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (node.hasChildren) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

@Composable
private fun EntryDetail(entry: R2HelpEntry) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            entry.command + (if (entry.args.isNotEmpty()) " ${entry.args}" else ""),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        if (entry.description.isNotEmpty()) {
            Text(
                entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SearchResults(query: String, onNavigate: (String) -> Unit) {
    val results = remember(query) { R2CommandHelp.search(query) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(entry.command) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.command,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (entry.args.isNotEmpty()) {
                        Text(
                            entry.args,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry.description.isNotEmpty()) {
                        Text(
                            entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

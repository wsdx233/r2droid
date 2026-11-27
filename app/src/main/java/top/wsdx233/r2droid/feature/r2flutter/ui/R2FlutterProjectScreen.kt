package top.wsdx233.r2droid.feature.r2flutter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.ui.components.FilterableList
import top.wsdx233.r2droid.feature.r2flutter.R2FlutterViewModel
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterClass
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterComponent
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterDataState
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterFunction
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterInstruction
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterOverview
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterString
import top.wsdx233.r2droid.feature.r2flutter.data.FlutterXref

private enum class FlutterTab(val title: Int) {
    OVERVIEW(R.string.r2flutter_tab_overview),
    FUNCTIONS(R.string.r2flutter_tab_functions),
    CLASSES(R.string.r2flutter_tab_classes),
    STRINGS(R.string.r2flutter_tab_strings),
    XREFS(R.string.r2flutter_tab_xrefs),
    INSTRUCTIONS(R.string.r2flutter_tab_instructions),
    COMPONENTS(R.string.r2flutter_tab_components)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R2FlutterProjectScreen(
    viewModel: R2FlutterViewModel,
    onJumpToDisasm: (Long) -> Unit,
    onAnalysisApplied: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val message by viewModel.operationMessage.collectAsState()

    LaunchedEffect(selectedTab) {
        when (FlutterTab.entries[selectedTab]) {
            FlutterTab.OVERVIEW -> viewModel.loadOverview()
            FlutterTab.FUNCTIONS -> viewModel.loadFunctions()
            FlutterTab.CLASSES -> viewModel.loadClasses()
            FlutterTab.STRINGS -> viewModel.loadStrings()
            FlutterTab.XREFS -> viewModel.loadXrefs()
            FlutterTab.INSTRUCTIONS -> viewModel.loadInstructions()
            FlutterTab.COMPONENTS -> viewModel.loadComponents()
        }
    }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.clearOperationMessage()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                FlutterTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(tab.title)) }
                    )
                }
            }
            when (FlutterTab.entries[selectedTab]) {
                FlutterTab.OVERVIEW -> FlutterOverviewScreen(viewModel, onAnalysisApplied)
                FlutterTab.FUNCTIONS -> FlutterFunctionsScreen(viewModel, onJumpToDisasm)
                FlutterTab.CLASSES -> FlutterClassesScreen(viewModel, onJumpToDisasm)
                FlutterTab.STRINGS -> FlutterStringsScreen(viewModel, onJumpToDisasm)
                FlutterTab.XREFS -> FlutterXrefsScreen(viewModel, onJumpToDisasm)
                FlutterTab.INSTRUCTIONS -> FlutterInstructionsScreen(viewModel, onJumpToDisasm)
                FlutterTab.COMPONENTS -> FlutterComponentsScreen(viewModel)
            }
        }
    }
}

@Composable
private fun FlutterOverviewScreen(viewModel: R2FlutterViewModel, onAnalysisApplied: () -> Unit) {
    val state by viewModel.overview.collectAsState()
    val running by viewModel.analysisRunning.collectAsState()
    val namePool by viewModel.namePool.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val mapFileName by viewModel.mapFileName.collectAsState()
    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importObfuscationMap)
    }

    DataStateContent(state = state, onRetry = { viewModel.loadOverview(force = true) }) { overview ->
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = { viewModel.runAnalysis(1, onAnalysisApplied) }, enabled = !running) {
                        Text("A")
                    }
                    FilledTonalButton(onClick = { viewModel.runAnalysis(2, onAnalysisApplied) }, enabled = !running) {
                        Text("AA")
                    }
                    Button(onClick = { viewModel.runAnalysis(3, onAnalysisApplied) }, enabled = !running) {
                        Text("AAA")
                    }
                    OutlinedButton(onClick = { viewModel.applyClasses(onAnalysisApplied) }, enabled = !running) {
                        Icon(Icons.Default.DataObject, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.r2flutter_apply_classes))
                    }
                }
            }
            if (running) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.r2flutter_analysis_running))
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.r2flutter_name_pool)) },
                    supportingContent = { Text(stringResource(R.string.r2flutter_name_pool_desc)) },
                    trailingContent = {
                        Switch(checked = namePool, onCheckedChange = viewModel::setNamePool)
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.r2flutter_obfuscation_map)) },
                    supportingContent = {
                        Text(mapFileName ?: stringResource(R.string.r2flutter_obfuscation_map_none))
                    },
                    trailingContent = {
                        OutlinedButton(onClick = { mapPicker.launch(arrayOf("application/json", "text/json", "*/*")) }) {
                            Text(stringResource(R.string.r2flutter_choose_map))
                        }
                    }
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = profile,
                        onValueChange = viewModel::updateProfile,
                        label = { Text(stringResource(R.string.r2flutter_profile)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::applyProfile) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.r2flutter_apply_profile))
                    }
                }
            }
            items(overview.values) { (key, value) ->
                ListItem(
                    headlineContent = { Text(key.ifBlank { "snapshot" }) },
                    supportingContent = {
                        Text(value, fontFamily = FontFamily.Monospace)
                    },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FlutterFunctionsScreen(viewModel: R2FlutterViewModel, onJump: (Long) -> Unit) {
    val state by viewModel.functions.collectAsState()
    DataStateContent(state, { viewModel.loadFunctions(force = true) }) { values ->
        FilterableList(
            items = values,
            filterPredicate = { item, query -> item.name.contains(query, ignoreCase = true) || formatAddress(item.address).contains(query, true) },
            onRefresh = { viewModel.loadFunctions(force = true) },
            placeholder = stringResource(R.string.r2flutter_search_functions)
        ) { FunctionRow(it, onJump) }
    }
}

@Composable
private fun FunctionRow(item: FlutterFunction, onJump: (Long) -> Unit) {
    ListItem(
        headlineContent = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                buildString {
                    append(formatAddress(item.address))
                    item.size?.let { append("  size=0x${it.toString(16)}") }
                },
                fontFamily = FontFamily.Monospace
            )
        },
        leadingContent = { Icon(Icons.Default.Functions, contentDescription = null) },
        modifier = Modifier.clickable { onJump(item.address) }
    )
}

@Composable
private fun FlutterClassesScreen(viewModel: R2FlutterViewModel, onJump: (Long) -> Unit) {
    val state by viewModel.classes.collectAsState()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    DataStateContent(state, { viewModel.loadClasses(force = true) }) { values ->
        FilterableList(
            items = values,
            filterPredicate = { item, query ->
                item.name.contains(query, true) || item.library.contains(query, true) ||
                    item.fields.any { it.name.contains(query, true) } || item.methods.any { it.name.contains(query, true) }
            },
            onRefresh = { viewModel.loadClasses(force = true) },
            placeholder = stringResource(R.string.r2flutter_search_classes)
        ) { item ->
            val classKey = "${item.library}|${item.name}"
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                item.library.takeIf(String::isNotBlank),
                                item.superClass.takeIf(String::isNotBlank)?.let { "extends $it" },
                                "${item.fields.size} fields",
                                "${item.methods.size} methods"
                            ).joinToString("  ")
                        )
                    },
                    leadingContent = { Icon(Icons.Default.DataObject, contentDescription = null) },
                    modifier = Modifier.clickable { expanded[classKey] = expanded[classKey] != true }
                )
                if (expanded[classKey] == true) {
                    item.fields.forEach { field ->
                        Text(
                            "+0x${field.offset.toString(16)}  ${field.type} ${field.name} ${field.flags}",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    item.methods.forEach { method ->
                        Text(
                            "${formatAddress(method.address)}  ${method.name} ${method.signature}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = method.address != 0L) { onJump(method.address) }
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlutterStringsScreen(viewModel: R2FlutterViewModel, onJump: (Long) -> Unit) {
    val state by viewModel.strings.collectAsState()
    val fuzzy by viewModel.fuzzyStrings.collectAsState()
    DataStateContent(state, { viewModel.loadStrings(force = true) }) { values ->
        FilterableList(
            items = values,
            filterPredicate = { item, query -> item.value.contains(query, true) || item.category.contains(query, true) },
            onRefresh = { viewModel.loadStrings(force = true) },
            placeholder = stringResource(R.string.r2flutter_search_strings),
            headerTrailingContent = {
                Switch(checked = fuzzy, onCheckedChange = viewModel::setFuzzyStrings)
            }
        ) { item -> StringRow(item, onJump) }
    }
}

@Composable
private fun StringRow(item: FlutterString, onJump: (Long) -> Unit) {
    ListItem(
        headlineContent = { Text(item.value, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text("${item.category}  len=${item.length}${item.address?.let { "  ${formatAddress(it)}" }.orEmpty()}")
        },
        leadingContent = { Icon(Icons.Default.TextFields, contentDescription = null) },
        modifier = Modifier.clickable(enabled = item.address != null) { item.address?.let(onJump) }
    )
}

@Composable
private fun FlutterXrefsScreen(viewModel: R2FlutterViewModel, onJump: (Long) -> Unit) {
    val state by viewModel.xrefs.collectAsState()
    DataStateContent(state, { viewModel.loadXrefs(force = true) }) { values ->
        FilterableList(
            items = values,
            filterPredicate = { item, query ->
                item.kind.contains(query, true) || item.source.name.contains(query, true) || item.destination.name.contains(query, true)
            },
            onRefresh = { viewModel.loadXrefs(force = true) },
            placeholder = stringResource(R.string.r2flutter_search_xrefs)
        ) { item -> XrefRow(item, onJump) }
    }
}

@Composable
private fun XrefRow(item: FlutterXref, onJump: (Long) -> Unit) {
    val target = item.destination.address ?: item.source.address
    ListItem(
        headlineContent = { Text("${item.kind}  ${item.origin}") },
        supportingContent = {
            Text("${nodeLabel(item.source)}  ->  ${nodeLabel(item.destination)}", fontFamily = FontFamily.Monospace)
        },
        leadingContent = { Icon(Icons.Default.AccountTree, contentDescription = null) },
        modifier = Modifier.clickable(enabled = target != null) { target?.let(onJump) }
    )
}

@Composable
private fun FlutterInstructionsScreen(viewModel: R2FlutterViewModel, onJump: (Long) -> Unit) {
    val state by viewModel.instructions.collectAsState()
    DataStateContent(state, { viewModel.loadInstructions(force = true) }) { values ->
        FilterableList(
            items = values,
            filterPredicate = { item, query -> item.name.contains(query, true) || formatAddress(item.address).contains(query, true) },
            onRefresh = { viewModel.loadInstructions(force = true) },
            placeholder = stringResource(R.string.r2flutter_search_instructions)
        ) { item -> InstructionRow(item, onJump) }
    }
}

@Composable
private fun InstructionRow(item: FlutterInstruction, onJump: (Long) -> Unit) {
    ListItem(
        headlineContent = { Text(item.name.ifBlank { "#${item.index}" }) },
        supportingContent = {
            Text("${formatAddress(item.address)}  pc+0x${item.pcOffset.toString(16)}  ${item.kind}", fontFamily = FontFamily.Monospace)
        },
        leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
        modifier = Modifier.clickable { onJump(item.address) }
    )
}

@Composable
private fun FlutterComponentsScreen(viewModel: R2FlutterViewModel) {
    val state by viewModel.components.collectAsState()
    DataStateContent(state, { viewModel.loadComponents(force = true) }) { values ->
        FilterableList(
            items = values,
            filterPredicate = { item, query -> item.name.contains(query, true) || item.type.contains(query, true) || item.source.contains(query, true) },
            onRefresh = { viewModel.loadComponents(force = true) },
            placeholder = stringResource(R.string.r2flutter_search_components)
        ) { ComponentRow(it) }
    }
}

@Composable
private fun ComponentRow(item: FlutterComponent) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = {
            Text(
                "${item.type}${item.version.takeIf(String::isNotBlank)?.let { "  $it" }.orEmpty()}  confidence=${item.confidence}%\n${item.source}",
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = { Icon(Icons.Default.Inventory2, contentDescription = null) }
    )
}

@Composable
private fun <T> DataStateContent(
    state: FlutterDataState<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit
) {
    when (state) {
        FlutterDataState.Idle, FlutterDataState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is FlutterDataState.Error -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.size(12.dp))
            Text(state.message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.size(16.dp))
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.common_retry))
            }
        }
        is FlutterDataState.Ready -> content(state.value)
    }
}

private fun formatAddress(address: Long): String = "0x${address.toULong().toString(16)}"

private fun nodeLabel(node: top.wsdx233.r2droid.feature.r2flutter.data.FlutterXrefNode): String {
    val value = node.name.ifBlank { node.type }
    return node.address?.let { "$value@${formatAddress(it)}" } ?: value
}

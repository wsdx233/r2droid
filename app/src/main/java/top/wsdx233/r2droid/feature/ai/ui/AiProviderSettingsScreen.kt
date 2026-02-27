package top.wsdx233.r2droid.feature.ai.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.ai.AiEvent
import top.wsdx233.r2droid.feature.ai.AiViewModel
import top.wsdx233.r2droid.feature.ai.data.AiProvider
import top.wsdx233.r2droid.feature.ai.data.AiSettingsManager
import java.util.UUID

@Composable
fun AiProviderSettingsScreen(viewModel: AiViewModel) {
    val config by AiSettingsManager.configFlow.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AiProvider?>(null) }
    var deletingProvider by remember { mutableStateOf<AiProvider?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ai_provider_add))
            }
        }
    ) { padding ->
        if (config.providers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.ai_no_provider),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ai_no_provider_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(config.providers, key = { it.id }) { provider ->
                    val isActive = provider.id == config.activeProviderId
                    ProviderCard(
                        provider = provider,
                        isActive = isActive,
                        activeModel = if (isActive) config.activeModelName else null,
                        onActivate = { model ->
                            viewModel.onEvent(AiEvent.SetProvider(provider.id, model))
                        },
                        onEdit = { editingProvider = provider },
                        onDelete = { deletingProvider = provider }
                    )
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        ProviderEditDialog(
            provider = null,
            onDismiss = { showAddDialog = false },
            onSave = { provider ->
                AiSettingsManager.addProvider(provider)
                showAddDialog = false
            }
        )
    }

    // Edit dialog
    editingProvider?.let { provider ->
        ProviderEditDialog(
            provider = provider,
            onDismiss = { editingProvider = null },
            onSave = { updated ->
                AiSettingsManager.updateProvider(updated)
                editingProvider = null
            }
        )
    }

    // Delete confirmation
    deletingProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { deletingProvider = null },
            title = { Text(stringResource(R.string.ai_provider_delete_title)) },
            text = { Text(stringResource(R.string.ai_provider_delete_message, provider.name)) },
            confirmButton = {
                TextButton(onClick = {
                    AiSettingsManager.deleteProvider(provider.id)
                    deletingProvider = null
                }) {
                    Text(stringResource(R.string.ai_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProvider = null }) {
                    Text(stringResource(R.string.ai_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: AiProvider,
    isActive: Boolean,
    activeModel: String?,
    onActivate: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = provider.baseUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (provider.useResponsesApi) {
                            stringResource(R.string.ai_provider_response_api_on)
                        } else {
                            stringResource(R.string.ai_provider_response_api_off)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.ai_edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ai_delete))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Model chips
            Text(
                text = stringResource(R.string.ai_provider_models),
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                provider.models.forEach { model ->
                    val isModelActive = isActive && model == activeModel
                    androidx.compose.material3.FilterChip(
                        selected = isModelActive,
                        onClick = { onActivate(model) },
                        label = { Text(model, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (isModelActive) {
                            { Icon(Icons.Default.Check, null, Modifier.height(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderEditDialog(
    provider: AiProvider?,
    onDismiss: () -> Unit,
    onSave: (AiProvider) -> Unit
) {
    var name by remember { mutableStateOf(provider?.name ?: "") }
    var baseUrl by remember { mutableStateOf(provider?.baseUrl ?: "https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf(provider?.apiKey ?: "") }
    var modelsText by remember { mutableStateOf(provider?.models?.joinToString(", ") ?: "") }
    var useResponsesApi by remember { mutableStateOf(provider?.useResponsesApi ?: false) }

    var showModelSelector by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val isEdit = provider != null
    val title = if (isEdit) stringResource(R.string.ai_provider_edit)
    else stringResource(R.string.ai_provider_add)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.focusable(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ai_provider_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.ai_provider_base_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://api.openai.com/v1") }
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.ai_provider_api_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ai_provider_response_api),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.ai_provider_response_api_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = useResponsesApi,
                        onCheckedChange = { useResponsesApi = it },
                        thumbContent = if (useResponsesApi) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        } else {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = modelsText,
                        onValueChange = { modelsText = it },
                        label = { Text(stringResource(R.string.ai_provider_models_hint)) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("gpt-4o, gpt-4o-mini, ...") },
                        minLines = 2
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (baseUrl.isBlank() || apiKey.isBlank()) {
                                fetchError = null // will show need_url_key toast via state
                                return@IconButton
                            }
                            fetchError = null
                            isFetching = true
                            scope.launch {
                                try {
                                    val client = OpenAI(
                                        OpenAIConfig(
                                            token = apiKey.trim(),
                                            host = OpenAIHost(baseUrl = baseUrl.trim().trimEnd('/') + "/")
                                        )
                                    )
                                    val models = client.models().map { it.id.id }.sorted()
                                    fetchedModels = models
                                    isFetching = false
                                    if (models.isNotEmpty()) {
                                        showModelSelector = true
                                    } else {
                                        fetchError = "empty"
                                    }
                                } catch (e: Exception) {
                                    isFetching = false
                                    fetchError = e.message ?: "Unknown error"
                                }
                            }
                        },
                        enabled = !isFetching && baseUrl.isNotBlank() && apiKey.isNotBlank()
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.ai_provider_fetch_models))
                        }
                    }
                }
                if (fetchError != null) {
                    Text(
                        text = if (fetchError == "empty") stringResource(R.string.ai_provider_no_models_found)
                        else stringResource(R.string.ai_provider_fetch_error, fetchError!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val models = modelsText.split(",", "\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    if (name.isNotBlank() && baseUrl.isNotBlank() && models.isNotEmpty()) {
                        onSave(
                            AiProvider(
                                id = provider?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                baseUrl = baseUrl.trim(),
                                apiKey = apiKey.trim(),
                                models = models,
                                useResponsesApi = useResponsesApi
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && modelsText.isNotBlank()
            ) {
                Text(stringResource(R.string.ai_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_cancel))
            }
        }
    )

    // Model selection dialog
    if (showModelSelector && fetchedModels != null) {
        ModelSelectionDialog(
            availableModels = fetchedModels!!,
            currentModels = modelsText.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() },
            onDismiss = { showModelSelector = false },
            onConfirm = { selected ->
                val existing = modelsText.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
                val merged = (existing + selected).distinct()
                modelsText = merged.joinToString(", ")
                showModelSelector = false
            }
        )
    }
}

@Composable
private fun ModelSelectionDialog(
    availableModels: List<String>,
    currentModels: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selectedModels = remember {
        availableModels.map { it in currentModels }.toMutableStateList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_provider_select_models)) },
        text = {
            if (availableModels.isEmpty()) {
                Text(stringResource(R.string.ai_provider_no_models_found))
            } else {
                LazyColumn {
                    items(availableModels.size) { index ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = selectedModels[index],
                                onCheckedChange = { selectedModels[index] = it }
                            )
                            Text(
                                text = availableModels[index],
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = availableModels.filterIndexed { i, _ -> selectedModels[i] }
                    onConfirm(selected)
                }
            ) {
                Text(stringResource(R.string.ai_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_cancel))
            }
        }
    )
}

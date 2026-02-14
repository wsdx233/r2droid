package top.wsdx233.r2droid.feature.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    val isEdit = provider != null
    val title = if (isEdit) stringResource(R.string.ai_provider_edit)
    else stringResource(R.string.ai_provider_add)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedTextField(
                    value = modelsText,
                    onValueChange = { modelsText = it },
                    label = { Text(stringResource(R.string.ai_provider_models_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("gpt-4o, gpt-4o-mini, ...") },
                    minLines = 2
                )
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
                                models = models
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
}

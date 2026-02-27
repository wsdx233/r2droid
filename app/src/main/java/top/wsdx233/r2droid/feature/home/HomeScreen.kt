package top.wsdx233.r2droid.feature.home

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import top.wsdx233.r2droid.core.data.model.SavedProject
import top.wsdx233.r2droid.core.ui.adaptive.LocalWindowWidthClass
import top.wsdx233.r2droid.core.ui.adaptive.WindowWidthClass

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToProject: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFeatures: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf<SavedProject?>(null) }

    // Initialize ViewModel with context
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFileSelected(context, it) }
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is HomeUiEvent.NavigateToProject -> {
                    onNavigateToProject()
                }
                is HomeUiEvent.NavigateToAbout -> {
                    onNavigateToAbout()
                }
                is HomeUiEvent.NavigateToSettings -> {
                    onNavigateToSettings()
                }
                is HomeUiEvent.NavigateToFeatures -> {
                    onNavigateToFeatures()
                }
                is HomeUiEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is HomeUiEvent.ShowMessage -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { project ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.home_delete_project_title)) },
            text = { 
                Text(stringResource(R.string.home_delete_project_message, project.name)) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDeleteProject(context, project)
                        showDeleteDialog = null
                    }
                ) {
                    Text(
                        stringResource(R.string.home_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.home_delete_cancel))
                }
            }
        )
    }

    val widthClass = LocalWindowWidthClass.current
    val isWide = widthClass != WindowWidthClass.Compact

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isWide) {
            // --- Landscape / Tablet: two-pane layout ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left pane: header + actions + bottom bar
                HomeLeftPane(
                    modifier = Modifier.weight(0.4f),
                    onOpenFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onFeatures = viewModel::onFeaturesClicked,
                    onSettings = viewModel::onSettingsClicked,
                    onAbout = viewModel::onAboutClicked
                )
                // Right pane: saved projects
                HomeSavedProjectsPane(
                    modifier = Modifier.weight(0.6f),
                    viewModel = viewModel,
                    context = context,
                    onDeleteRequest = { showDeleteDialog = it }
                )
            }
        } else {
            // --- Portrait / Phone: original single-column layout ---
            HomeCompactLayout(
                onOpenFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                viewModel = viewModel,
                context = context,
                onDeleteRequest = { showDeleteDialog = it }
            )
        }
    }
}

@Composable
private fun HomeLeftPane(
    modifier: Modifier,
    onOpenFile: () -> Unit,
    onFeatures: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.secondary
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        HomeActionButton(
            title = stringResource(R.string.home_open_file_title),
            description = stringResource(R.string.home_open_file_desc),
            icon = Icons.Default.FolderOpen,
            onClick = onOpenFile
        )
        Spacer(modifier = Modifier.height(12.dp))
        HomeActionButton(
            title = stringResource(R.string.home_features_title),
            description = stringResource(R.string.home_features_desc),
            icon = Icons.Default.Build,
            onClick = onFeatures,
            containerColor = Color(0xFFE0F2F1),
            contentColor = Color(0xFF00695C),
            iconContainerColor = Color(0xFF009688).copy(alpha = 0.12f),
            iconColor = Color(0xFF009688)
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAbout) {
                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.home_about), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HomeSavedProjectsPane(
    modifier: Modifier,
    viewModel: HomeViewModel,
    context: Context,
    onDeleteRequest: (SavedProject) -> Unit
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_saved_projects),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            if (viewModel.isLoadingProjects) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (viewModel.savedProjects.isEmpty() && !viewModel.isLoadingProjects) {
            EmptyHistoryState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(viewModel.savedProjects, key = { it.id }) { project ->
                    SavedProjectCard(
                        project = project,
                        onRestore = { viewModel.onRestoreProject(context, project) },
                        onDelete = { onDeleteRequest(project) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCompactLayout(
    onOpenFile: () -> Unit,
    viewModel: HomeViewModel,
    context: Context,
    onDeleteRequest: (SavedProject) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.secondary
            )
        )
        Spacer(modifier = Modifier.height(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeActionButton(
                title = stringResource(R.string.home_open_file_title),
                description = stringResource(R.string.home_open_file_desc),
                icon = Icons.Default.FolderOpen,
                onClick = onOpenFile,
                modifier = Modifier.weight(1f)
            )
            HomeActionButton(
                title = stringResource(R.string.home_features_title),
                description = stringResource(R.string.home_features_desc),
                icon = Icons.Default.Build,
                onClick = viewModel::onFeaturesClicked,
                modifier = Modifier.weight(1f),
                containerColor = Color(0xFFE0F2F1),
                contentColor = Color(0xFF00695C),
                iconContainerColor = Color(0xFF009688).copy(alpha = 0.12f),
                iconColor = Color(0xFF009688)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_saved_projects),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            if (viewModel.isLoadingProjects) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (viewModel.savedProjects.isEmpty() && !viewModel.isLoadingProjects) {
            EmptyHistoryState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(viewModel.savedProjects, key = { it.id }) { project ->
                    SavedProjectCard(
                        project = project,
                        onRestore = { viewModel.onRestoreProject(context, project) },
                        onDelete = { onDeleteRequest(project) }
                    )
                }
            }
        }
        if (viewModel.savedProjects.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::onSettingsClicked) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.home_settings), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_about), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = viewModel::onAboutClicked) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.home_about), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SavedProjectCard(
    project: SavedProject,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val isBinaryAccessible = project.isBinaryAccessible()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBinaryAccessible) 
                MaterialTheme.colorScheme.surface 
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isBinaryAccessible) { onRestore() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isBinaryAccessible)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBinaryAccessible) Icons.Default.Memory else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isBinaryAccessible)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${project.archType} | ${project.binType} | ${project.getFormattedFileSize()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = project.getFormattedLastModified(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                if (!isBinaryAccessible) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_binary_not_accessible),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Actions
            if (isBinaryAccessible) {
                IconButton(onClick = onRestore) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.home_restore_project),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.home_delete_project),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun HomeActionButton(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    iconContainerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    iconColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = contentColor
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
@Composable
fun EmptyHistoryState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_no_saved_projects),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}


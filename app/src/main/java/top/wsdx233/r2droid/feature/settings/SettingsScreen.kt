package top.wsdx233.r2droid.screen.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.data.SettingsManager
import top.wsdx233.r2droid.util.UriUtils

class SettingsViewModel : androidx.lifecycle.ViewModel() {
    // R2RC Content State
    private val _r2rcContent = MutableStateFlow("")
    val r2rcContent = _r2rcContent.asStateFlow()

    private val _fontPath = MutableStateFlow(SettingsManager.fontPath)
    val fontPath = _fontPath.asStateFlow()

    private val _language = MutableStateFlow(SettingsManager.language)
    val language = _language.asStateFlow()

    private val _projectHome = MutableStateFlow(SettingsManager.projectHome)
    val projectHome = _projectHome.asStateFlow()

    private val _darkMode = MutableStateFlow(SettingsManager.darkMode)
    val darkMode = _darkMode.asStateFlow()
    
    // Initialize r2rc content
    fun loadR2rcContent(context: Context) {
        _r2rcContent.value = SettingsManager.getR2rcContent(context)
    }
    
    fun saveR2rcContent(context: Context, content: String) {
        SettingsManager.setR2rcContent(context, content)
        _r2rcContent.value = content
    }
    
    fun setFontPath(path: String?) {
        SettingsManager.fontPath = path
        _fontPath.value = path
    }
    
    fun setLanguage(lang: String) {
        SettingsManager.language = lang
        _language.value = lang
    }
    
    fun setProjectHome(path: String?) {
        SettingsManager.projectHome = path
        _projectHome.value = path
    }

    fun migrateProjects(context: Context, oldPath: String?, newPath: String) {
        val oldDir = java.io.File(oldPath ?: context.filesDir.absolutePath, "projects")
        val newDir = java.io.File(newPath, "projects")
        if (oldDir.exists() && oldDir.isDirectory) {
            oldDir.copyRecursively(newDir, overwrite = true)
            oldDir.deleteRecursively()
        }
    }

    fun setDarkMode(mode: String) {
        SettingsManager.darkMode = mode
        _darkMode.value = mode
    }

    fun resetAll(context: Context) {
        SettingsManager.fontPath = null
        SettingsManager.language = "system"
        SettingsManager.projectHome = null
        SettingsManager.darkMode = "system"
        SettingsManager.setR2rcContent(context, "")
        _fontPath.value = null
        _language.value = "system"
        _projectHome.value = null
        _darkMode.value = "system"
        _r2rcContent.value = ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val r2rcContent by viewModel.r2rcContent.collectAsState()
    val fontPath by viewModel.fontPath.collectAsState()
    val language by viewModel.language.collectAsState()
    val projectHome by viewModel.projectHome.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()

    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.loadR2rcContent(context)
    }
    
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showR2rcDialog by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showMigrateDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var pendingNewProjectHome by remember { mutableStateOf<String?>(null) }
    var oldProjectHome by remember { mutableStateOf<String?>(null) }
    
    // R2RC Dialog state
    var tempR2rcContent by remember { mutableStateOf("") }
    
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
             viewModel.setFontPath(UriUtils.getPath(context, it))
        }
    }
    
    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val newPath = UriUtils.getTreePath(context, it)
            if (newPath != null) {
                oldProjectHome = projectHome
                pendingNewProjectHome = newPath
                showMigrateDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SettingsSectionHeader(stringResource(R.string.settings_general))
            }
            
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_r2rc),
                    subtitle = if (r2rcContent.isBlank()) stringResource(R.string.settings_default_value) else stringResource(R.string.settings_customized_value),
                    icon = Icons.Default.Settings,
                    onClick = { 
                        tempR2rcContent = r2rcContent
                        showR2rcDialog = true 
                    }
                )
            }
            
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_project_dir),
                    subtitle = projectHome ?: stringResource(R.string.settings_project_dir_desc),
                    icon = Icons.Default.Folder,
                    onClick = { dirPicker.launch(null) }
                )
            }
            
            item {
                HorizontalDivider()
                SettingsSectionHeader(stringResource(R.string.settings_appearance))
            }
            
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_font),
                    subtitle = fontPath ?: stringResource(R.string.settings_font_default),
                    icon = Icons.Default.FontDownload,
                    onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "*/*")) }
                )
            }
            
            item {
                val languageLabel = when(language) {
                    "en" -> stringResource(R.string.settings_language_english)
                    "zh" -> stringResource(R.string.settings_language_chinese)
                    else -> stringResource(R.string.settings_font_default) // "Default" (System)
                }
                SettingsItem(
                    title = stringResource(R.string.settings_language),
                    subtitle = languageLabel,
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true }
                )
            }

            item {
                val darkModeLabel = when(darkMode) {
                    "light" -> stringResource(R.string.settings_dark_mode_light)
                    "dark" -> stringResource(R.string.settings_dark_mode_dark)
                    else -> stringResource(R.string.settings_dark_mode_system)
                }
                SettingsItem(
                    title = stringResource(R.string.settings_dark_mode),
                    subtitle = darkModeLabel,
                    icon = Icons.Default.DarkMode,
                    onClick = { showDarkModeDialog = true }
                )
            }

            item {
                HorizontalDivider()
                SettingsSectionHeader(stringResource(R.string.settings_about))
            }

            item {
                val isChecking by top.wsdx233.r2droid.util.UpdateManager.isChecking.collectAsState()
                val checkingText = if (isChecking) stringResource(R.string.update_checking) else stringResource(R.string.update_check_desc)

                SettingsItem(
                    title = stringResource(R.string.update_check_title),
                    subtitle = checkingText,
                    icon = Icons.Default.SystemUpdate,
                    onClick = {
                        if (!isChecking) {
                            kotlinx.coroutines.GlobalScope.launch {
                                try {
                                    val update = top.wsdx233.r2droid.util.UpdateManager.checkForUpdate()
                                    if (update == null) {
                                        // Show "no update available" message
                                        kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.Main) {
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.update_no_update),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Show error message
                                    kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.update_check_failed),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                )
            }

            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                SettingsItem(
                    title = stringResource(R.string.settings_reset_all),
                    subtitle = stringResource(R.string.settings_reset_all_desc),
                    icon = Icons.Default.Restore,
                    onClick = { showResetDialog = true }
                )
            }
        }
    }
    
    if (showR2rcDialog) {
        AlertDialog(
            onDismissRequest = { showR2rcDialog = false },
            title = { Text(stringResource(R.string.settings_r2rc)) },
            text = {
                OutlinedTextField(
                    value = tempR2rcContent,
                    onValueChange = { tempR2rcContent = it },
                    label = { Text(stringResource(R.string.settings_content_label)) },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    maxLines = 20
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.saveR2rcContent(context, tempR2rcContent)
                    showR2rcDialog = false 
                }) {
                    Text(stringResource(R.string.settings_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showR2rcDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
    
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    LanguageOption(stringResource(R.string.settings_language_system), "system", language) { viewModel.setLanguage(it); showLanguageDialog = false; showRestartDialog = true }
                    LanguageOption(stringResource(R.string.settings_language_english), "en", language) { viewModel.setLanguage(it); showLanguageDialog = false; showRestartDialog = true }
                    LanguageOption(stringResource(R.string.settings_language_chinese), "zh", language) { viewModel.setLanguage(it); showLanguageDialog = false; showRestartDialog = true }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.settings_restart_title)) },
            text = { Text(stringResource(R.string.settings_restart_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    (context as? Activity)?.recreate()
                }) {
                    Text(stringResource(R.string.settings_restart_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(R.string.settings_restart_later))
                }
            }
        )
    }

    if (showMigrateDialog && pendingNewProjectHome != null) {
        AlertDialog(
            onDismissRequest = {
                showMigrateDialog = false
                viewModel.setProjectHome(pendingNewProjectHome)
                pendingNewProjectHome = null
            },
            title = { Text(stringResource(R.string.settings_migrate_title)) },
            text = { Text(stringResource(R.string.settings_migrate_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showMigrateDialog = false
                    val newPath = pendingNewProjectHome!!
                    viewModel.migrateProjects(context, oldProjectHome, newPath)
                    viewModel.setProjectHome(newPath)
                    pendingNewProjectHome = null
                }) {
                    Text(stringResource(R.string.settings_migrate_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMigrateDialog = false
                    viewModel.setProjectHome(pendingNewProjectHome)
                    pendingNewProjectHome = null
                }) {
                    Text(stringResource(R.string.settings_migrate_no))
                }
            }
        )
    }

    if (showDarkModeDialog) {
        AlertDialog(
            onDismissRequest = { showDarkModeDialog = false },
            title = { Text(stringResource(R.string.settings_dark_mode)) },
            text = {
                Column {
                    LanguageOption(stringResource(R.string.settings_dark_mode_system), "system", darkMode) { viewModel.setDarkMode(it); showDarkModeDialog = false }
                    LanguageOption(stringResource(R.string.settings_dark_mode_light), "light", darkMode) { viewModel.setDarkMode(it); showDarkModeDialog = false }
                    LanguageOption(stringResource(R.string.settings_dark_mode_dark), "dark", darkMode) { viewModel.setDarkMode(it); showDarkModeDialog = false }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDarkModeDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_all)) },
            text = { Text(stringResource(R.string.settings_reset_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.resetAll(context)
                    showRestartDialog = true
                }) {
                    Text(stringResource(R.string.settings_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun LanguageOption(label: String, value: String, currentValue: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = (value == currentValue),
            onClick = { onSelect(value) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

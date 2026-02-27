package top.wsdx233.r2droid.feature.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.ai.AiViewModel
import top.wsdx233.r2droid.feature.ai.ui.AiProviderSettingsScreen
import top.wsdx233.r2droid.util.UriUtils
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.MainScope
import org.json.JSONArray
import org.json.JSONObject
import top.wsdx233.r2droid.service.KeepAliveService
import top.wsdx233.r2droid.util.UpdateManager
import java.io.File

class SettingsViewModel : ViewModel() {
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

    private val _menuAtTouch = MutableStateFlow(SettingsManager.menuAtTouch)
    val menuAtTouch = _menuAtTouch.asStateFlow()

    private val _aiEnabled = MutableStateFlow(SettingsManager.aiEnabled)
    val aiEnabled = _aiEnabled.asStateFlow()

    private val _aiOutputTruncateLimit = MutableStateFlow(SettingsManager.aiOutputTruncateLimit)
    val aiOutputTruncateLimit = _aiOutputTruncateLimit.asStateFlow()

    private val _useHttpMode = MutableStateFlow(SettingsManager.useHttpMode)
    val useHttpMode = _useHttpMode.asStateFlow()

    private val _httpPort = MutableStateFlow(SettingsManager.httpPort)
    val httpPort = _httpPort.asStateFlow()

    private val _decompilerShowLineNumbers = MutableStateFlow(SettingsManager.decompilerShowLineNumbers)
    val decompilerShowLineNumbers = _decompilerShowLineNumbers.asStateFlow()

    private val _decompilerWordWrap = MutableStateFlow(SettingsManager.decompilerWordWrap)
    val decompilerWordWrap = _decompilerWordWrap.asStateFlow()

    private val _decompilerDefault = MutableStateFlow(SettingsManager.decompilerDefault)
    val decompilerDefault = _decompilerDefault.asStateFlow()

    private val _maxLogEntries = MutableStateFlow(SettingsManager.maxLogEntries)
    val maxLogEntries = _maxLogEntries.asStateFlow()

    private val _keepAlive = MutableStateFlow(SettingsManager.keepAliveNotification)
    val keepAlive = _keepAlive.asStateFlow()

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
        val oldDir = File(oldPath ?: context.filesDir.absolutePath, "projects")
        val newDir = File(newPath, "projects")
        if (oldDir.exists() && oldDir.isDirectory) {
            oldDir.copyRecursively(newDir, overwrite = true)
            oldDir.deleteRecursively()
            // Update scriptPath in JSON files to reflect new directory
            val oldPrefix = oldDir.absolutePath
            val newPrefix = newDir.absolutePath
            fun rewriteJson(file: File, isIndex: Boolean) {
                if (!file.exists()) return
                try {
                    val text = file.readText()
                    if (isIndex) {
                        val arr = JSONArray(text)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val sp = obj.optString("scriptPath", "")
                            if (sp.startsWith(oldPrefix)) {
                                obj.put("scriptPath", sp.replace(oldPrefix, newPrefix))
                            }
                        }
                        file.writeText(arr.toString(2))
                    } else {
                        val obj = JSONObject(text)
                        val sp = obj.optString("scriptPath", "")
                        if (sp.startsWith(oldPrefix)) {
                            obj.put("scriptPath", sp.replace(oldPrefix, newPrefix))
                        }
                        file.writeText(obj.toString(2))
                    }
                } catch (_: Exception) {}
            }
            rewriteJson(File(newDir, "index.json"), true)
            newDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                rewriteJson(File(dir, "project.json"), false)
            }
        }
    }

    fun setDarkMode(mode: String) {
        SettingsManager.darkMode = mode
        _darkMode.value = mode
    }

    fun setMenuAtTouch(value: Boolean) {
        SettingsManager.menuAtTouch = value
        _menuAtTouch.value = value
    }

    fun setAiEnabled(value: Boolean) {
        SettingsManager.aiEnabled = value
        _aiEnabled.value = value
    }

    fun setAiOutputTruncateLimit(value: Int) {
        SettingsManager.aiOutputTruncateLimit = value
        _aiOutputTruncateLimit.value = value
    }

    fun setUseHttpMode(value: Boolean) {
        SettingsManager.useHttpMode = value
        _useHttpMode.value = value
    }

    fun setHttpPort(value: Int) {
        SettingsManager.httpPort = value
        _httpPort.value = value
    }

    fun setDecompilerShowLineNumbers(value: Boolean) {
        SettingsManager.decompilerShowLineNumbers = value
        _decompilerShowLineNumbers.value = value
    }

    fun setDecompilerWordWrap(value: Boolean) {
        SettingsManager.decompilerWordWrap = value
        _decompilerWordWrap.value = value
    }

    fun setDecompilerDefault(value: String) {
        SettingsManager.decompilerDefault = value
        _decompilerDefault.value = value
    }

    fun setMaxLogEntries(value: Int) {
        SettingsManager.maxLogEntries = value
        _maxLogEntries.value = value
    }

    fun setKeepAlive(context: Context, value: Boolean) {
        SettingsManager.keepAliveNotification = value
        _keepAlive.value = value
        if (value) {
            KeepAliveService.start(context)
        } else {
            KeepAliveService.stop(context)
        }
    }

    fun resetAll(context: Context) {
        SettingsManager.fontPath = null
        SettingsManager.language = "system"
        SettingsManager.projectHome = null
        SettingsManager.darkMode = "system"
        SettingsManager.setR2rcContent(context, "")
        SettingsManager.decompilerShowLineNumbers = true
        SettingsManager.decompilerWordWrap = false
        SettingsManager.decompilerDefault = "r2ghidra"
        SettingsManager.maxLogEntries = 100
        SettingsManager.keepAliveNotification = true
        SettingsManager.menuAtTouch = true
        SettingsManager.aiEnabled = true
        SettingsManager.aiOutputTruncateLimit = 100000
        SettingsManager.useHttpMode = false
        SettingsManager.httpPort = 9090
        _fontPath.value = null
        _language.value = "system"
        _projectHome.value = null
        _darkMode.value = "system"
        _r2rcContent.value = ""
        _decompilerShowLineNumbers.value = true
        _decompilerWordWrap.value = false
        _decompilerDefault.value = "r2ghidra"
        _maxLogEntries.value = 100
        _keepAlive.value = true
        _menuAtTouch.value = true
        _aiEnabled.value = true
        _aiOutputTruncateLimit.value = 100000
        _useHttpMode.value = false
        _httpPort.value = 9090
    }
}

@SuppressLint("BatteryLife")
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
    val decompilerShowLineNumbers by viewModel.decompilerShowLineNumbers.collectAsState()
    val decompilerWordWrap by viewModel.decompilerWordWrap.collectAsState()
    val decompilerDefault by viewModel.decompilerDefault.collectAsState()
    val maxLogEntries by viewModel.maxLogEntries.collectAsState()
    val keepAlive by viewModel.keepAlive.collectAsState()
    val menuAtTouch by viewModel.menuAtTouch.collectAsState()
    val aiEnabled by viewModel.aiEnabled.collectAsState()
    val aiOutputTruncateLimit by viewModel.aiOutputTruncateLimit.collectAsState()
    val useHttpMode by viewModel.useHttpMode.collectAsState()
    val httpPort by viewModel.httpPort.collectAsState()

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
    var showDecompilerDialog by remember { mutableStateOf(false) }
    var showAiProviderSettings by remember { mutableStateOf(false) }
    var showMaxLogDialog by remember { mutableStateOf(false) }
    var tempMaxLog by remember { mutableStateOf("") }
    var showAiTruncateDialog by remember { mutableStateOf(false) }
    var tempAiTruncateLimit by remember { mutableStateOf("") }
    var showHttpPortDialog by remember { mutableStateOf(false) }
    var tempHttpPort by remember { mutableStateOf("") }
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
                SettingsItem(
                    title = stringResource(R.string.settings_max_log_entries),
                    subtitle = maxLogEntries.toString(),
                    icon = Icons.Default.Settings,
                    onClick = {
                        tempMaxLog = maxLogEntries.toString()
                        showMaxLogDialog = true
                    }
                )
            }

            item {
                SettingsToggleItem(
                    title = stringResource(R.string.settings_ai_enabled),
                    subtitle = stringResource(R.string.settings_ai_enabled_desc),
                    checked = aiEnabled,
                    onCheckedChange = { viewModel.setAiEnabled(it) }
                )
            }

            item {
                SettingsItem(
                    title = stringResource(R.string.settings_ai_provider),
                    subtitle = stringResource(R.string.settings_ai_provider_desc),
                    icon = Icons.Default.SmartToy,
                    onClick = { showAiProviderSettings = true }
                )
            }

            item {
                SettingsItem(
                    title = stringResource(R.string.settings_ai_truncate_limit),
                    subtitle = aiOutputTruncateLimit.toString(),
                    icon = Icons.Default.SmartToy,
                    onClick = {
                        tempAiTruncateLimit = aiOutputTruncateLimit.toString()
                        showAiTruncateDialog = true
                    }
                )
            }

            item {
                HorizontalDivider()
                SettingsSectionHeader(stringResource(R.string.settings_r2pipe_mode))
            }

            item {
                SettingsToggleItem(
                    title = stringResource(R.string.settings_use_http_mode),
                    subtitle = stringResource(R.string.settings_use_http_mode_desc),
                    checked = useHttpMode,
                    onCheckedChange = { viewModel.setUseHttpMode(it) }
                )
            }

            item {
                SettingsItem(
                    title = stringResource(R.string.settings_http_port),
                    subtitle = httpPort.toString(),
                    icon = Icons.Default.Settings,
                    onClick = {
                        tempHttpPort = httpPort.toString()
                        showHttpPortDialog = true
                    }
                )
            }

            item {
                HorizontalDivider()
                SettingsSectionHeader(stringResource(R.string.settings_background))
            }

            item {
                SettingsToggleItem(
                    title = stringResource(R.string.settings_keep_alive),
                    subtitle = stringResource(R.string.settings_keep_alive_desc),
                    checked = keepAlive,
                    onCheckedChange = { viewModel.setKeepAlive(context, it) }
                )
            }

            item {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
                SettingsItem(
                    title = stringResource(R.string.settings_ignore_battery),
                    subtitle = if (isIgnoring) stringResource(R.string.settings_ignore_battery_already)
                        else stringResource(R.string.settings_ignore_battery_desc),
                    icon = Icons.Default.BatteryAlert,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        context.startActivity(intent)
                    }
                )
            }

            item {
                SettingsItem(
                    title = stringResource(R.string.settings_dontkillmyapp),
                    subtitle = stringResource(R.string.settings_dontkillmyapp_desc),
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://dontkillmyapp.com/".toUri()))
                    }
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
                SettingsToggleItem(
                    title = stringResource(R.string.settings_menu_at_touch),
                    subtitle = stringResource(R.string.settings_menu_at_touch_desc),
                    checked = menuAtTouch,
                    onCheckedChange = { viewModel.setMenuAtTouch(it) }
                )
            }

            item {
                HorizontalDivider()
                SettingsSectionHeader(stringResource(R.string.settings_decompiler))
            }

            item {
                SettingsToggleItem(
                    title = stringResource(R.string.settings_decompiler_show_line_numbers),
                    subtitle = stringResource(R.string.settings_decompiler_show_line_numbers_desc),
                    checked = decompilerShowLineNumbers,
                    onCheckedChange = { viewModel.setDecompilerShowLineNumbers(it) }
                )
            }

            item {
                SettingsToggleItem(
                    title = stringResource(R.string.settings_decompiler_word_wrap),
                    subtitle = stringResource(R.string.settings_decompiler_word_wrap_desc),
                    checked = decompilerWordWrap,
                    onCheckedChange = { viewModel.setDecompilerWordWrap(it) }
                )
            }

            item {
                val decompilerLabel = when(decompilerDefault) {
                    "native" -> stringResource(R.string.decompiler_native)
                    "r2dec" -> stringResource(R.string.decompiler_r2dec)
                    "aipdg" -> stringResource(R.string.decompiler_aipdg)
                    else -> stringResource(R.string.decompiler_r2ghidra)
                }
                SettingsItem(
                    title = stringResource(R.string.settings_decompiler_default),
                    subtitle = decompilerLabel,
                    icon = Icons.Default.Settings,
                    onClick = { showDecompilerDialog = true }
                )
            }

            item {
                HorizontalDivider()
                SettingsSectionHeader(stringResource(R.string.settings_about))
            }

            item {
                val isChecking by UpdateManager.isChecking.collectAsState()
                val checkingText = if (isChecking) stringResource(R.string.update_checking) else stringResource(R.string.update_check_desc)

                SettingsItem(
                    title = stringResource(R.string.update_check_title),
                    subtitle = checkingText,
                    icon = Icons.Default.SystemUpdate,
                    onClick = {
                        if (!isChecking) {
                            // 使用 CoroutineScope 启动协程
                            MainScope().launch {
                                try {
                                    val update = UpdateManager.checkForUpdate(context)
                                    if (update == null) {
                                        // Show "no update available" message
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.update_no_update),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    // Show error message
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.update_check_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                Column(modifier = Modifier.focusable()) {
                    OutlinedTextField(
                        value = tempR2rcContent,
                        onValueChange = { tempR2rcContent = it },
                        label = { Text(stringResource(R.string.settings_content_label)) },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        maxLines = 20
                    )
                }
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

    if (showDecompilerDialog) {
        AlertDialog(
            onDismissRequest = { showDecompilerDialog = false },
            title = { Text(stringResource(R.string.settings_decompiler_default)) },
            text = {
                Column {
                    LanguageOption(stringResource(R.string.decompiler_r2ghidra), "r2ghidra", decompilerDefault) { viewModel.setDecompilerDefault(it); showDecompilerDialog = false }
                    LanguageOption(stringResource(R.string.decompiler_r2dec), "r2dec", decompilerDefault) { viewModel.setDecompilerDefault(it); showDecompilerDialog = false }
                    LanguageOption(stringResource(R.string.decompiler_native), "native", decompilerDefault) { viewModel.setDecompilerDefault(it); showDecompilerDialog = false }
                    LanguageOption(stringResource(R.string.decompiler_aipdg), "aipdg", decompilerDefault) { viewModel.setDecompilerDefault(it); showDecompilerDialog = false }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDecompilerDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showMaxLogDialog) {
        AlertDialog(
            onDismissRequest = { showMaxLogDialog = false },
            title = { Text(stringResource(R.string.settings_max_log_entries)) },
            text = {
                Column(modifier = Modifier.focusable()) {
                    OutlinedTextField(
                        value = tempMaxLog,
                        onValueChange = { tempMaxLog = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.settings_max_log_entries_desc)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = tempMaxLog.toIntOrNull()
                    if (value != null && value > 0) {
                        viewModel.setMaxLogEntries(value)
                    }
                    showMaxLogDialog = false
                }) {
                    Text(stringResource(R.string.settings_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMaxLogDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showAiTruncateDialog) {
        AlertDialog(
            onDismissRequest = { showAiTruncateDialog = false },
            title = { Text(stringResource(R.string.settings_ai_truncate_limit)) },
            text = {
                Column(modifier = Modifier.focusable()) {
                    OutlinedTextField(
                        value = tempAiTruncateLimit,
                        onValueChange = { tempAiTruncateLimit = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.settings_ai_truncate_limit_desc)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = tempAiTruncateLimit.toIntOrNull()
                    if (value != null && value > 0) {
                        viewModel.setAiOutputTruncateLimit(value)
                    }
                    showAiTruncateDialog = false
                }) {
                    Text(stringResource(R.string.settings_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiTruncateDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showHttpPortDialog) {
        AlertDialog(
            onDismissRequest = { showHttpPortDialog = false },
            title = { Text(stringResource(R.string.settings_http_port)) },
            text = {
                Column(modifier = Modifier.focusable()) {
                    OutlinedTextField(
                        value = tempHttpPort,
                        onValueChange = { tempHttpPort = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.settings_http_port_desc)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = tempHttpPort.toIntOrNull()
                    if (value != null && value in 1024..65535) {
                        viewModel.setHttpPort(value)
                    }
                    showHttpPortDialog = false
                }) {
                    Text(stringResource(R.string.settings_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHttpPortDialog = false }) {
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

    if (showAiProviderSettings) {
        Dialog(
            onDismissRequest = { showAiProviderSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                val aiViewModel: AiViewModel = hiltViewModel()
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.settings_ai_provider)) },
                            navigationIcon = {
                                IconButton(onClick = { showAiProviderSettings = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AiProviderSettingsScreen(viewModel = aiViewModel)
                    }
                }
            }
        }
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
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
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
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
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

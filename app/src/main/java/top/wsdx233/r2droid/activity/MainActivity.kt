package top.wsdx233.r2droid.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.feature.project.ProjectScreen
import top.wsdx233.r2droid.feature.about.AboutScreen
import top.wsdx233.r2droid.feature.home.HomeScreen
import top.wsdx233.r2droid.feature.install.InstallScreen
import top.wsdx233.r2droid.feature.permission.PermissionScreen
import top.wsdx233.r2droid.feature.settings.SettingsScreen
import top.wsdx233.r2droid.ui.theme.R2droidTheme
import top.wsdx233.r2droid.util.IntentFileResolver
import top.wsdx233.r2droid.util.PermissionManager
import top.wsdx233.r2droid.util.R2Installer
import top.wsdx233.r2droid.util.R2PipeManager

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val pendingFileUri = mutableStateOf<Uri?>(null)

    override fun attachBaseContext(newBase: Context) {
        // Apply language from settings
        val prefs = newBase.getSharedPreferences("r2droid_settings", MODE_PRIVATE)
        val language = prefs.getString("language", "system")

        val context = if (language != null && language != "system") {
            val locale = java.util.Locale(language)
            val config = android.content.res.Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SettingsManager
        SettingsManager.initialize(applicationContext)
        top.wsdx233.r2droid.feature.ai.data.AiSettingsManager.initialize(applicationContext)

        // 启动常驻通知保活服务
        if (SettingsManager.keepAliveNotification) {
            top.wsdx233.r2droid.service.KeepAliveService.start(applicationContext)
        }

        // 启动应用时检查并安装
        lifecycleScope.launch {
            R2Installer.checkAndInstall(applicationContext)
        }

        // 启动时自动检查更新（静默失败）
        lifecycleScope.launch {
            top.wsdx233.r2droid.util.UpdateManager.checkForUpdateSilently(applicationContext)
        }

        // 处理外部 Intent 传入的文件 URI
        pendingFileUri.value = extractFileUri(intent)

        enableEdgeToEdge()
        setContent {
            // Load custom font if available
            val customFont = remember {
                SettingsManager.getCustomFont() ?: androidx.compose.ui.text.font.FontFamily.Monospace
            }

            val windowWidthClass = top.wsdx233.r2droid.core.ui.adaptive.calculateWindowWidthClass()
            androidx.compose.runtime.CompositionLocalProvider(
                top.wsdx233.r2droid.ui.theme.LocalAppFont provides customFont,
                top.wsdx233.r2droid.core.ui.adaptive.LocalWindowWidthClass provides windowWidthClass
            ) {
                val darkModeSetting by SettingsManager.darkModeFlow.collectAsState()

                val darkTheme = when (darkModeSetting) {
                    "light" -> false
                    "dark" -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }
                R2droidTheme(darkTheme = darkTheme) {
                    // 监听全局安装状态
                    val installState by R2Installer.installState.collectAsState()

                    if (installState.isInstalling) {
                        InstallScreen(installState = installState)
                    } else {
                        MainAppContent(
                            pendingFileUri = pendingFileUri.value,
                            onPendingFileUriConsumed = { pendingFileUri.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingFileUri.value = extractFileUri(intent)
    }

    private fun extractFileUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
    }
}

enum class AppScreen {
    Home,
    Project,
    About,
    Settings,
    Features,
    R2Frida
}

@Composable
fun MainAppContent(
    pendingFileUri: Uri? = null,
    onPendingFileUriConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check permission state
    var hasPermission by remember { mutableStateOf(PermissionManager.hasStoragePermission(context)) }

    // Check for updates
    val updateInfo by top.wsdx233.r2droid.util.UpdateManager.updateInfo.collectAsState()

    // Re-check permission when app resumes (in case user went to settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = PermissionManager.hasStoragePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Show update dialog if update is available
    updateInfo?.let { update ->
        top.wsdx233.r2droid.core.ui.dialogs.UpdateDialog(
            updateInfo = update,
            onDismiss = { top.wsdx233.r2droid.util.UpdateManager.clearUpdateInfo() }
        )
    }

    if (!hasPermission) {
        PermissionScreen(
            onPermissionGranted = { hasPermission = true }
        )
    } else {
        MainAppNavigation(
            pendingFileUri = pendingFileUri,
            onPendingFileUriConsumed = onPendingFileUriConsumed
        )
    }
}

@Composable
fun MainAppNavigation(
    pendingFileUri: Uri? = null,
    onPendingFileUriConsumed: () -> Unit = {}
) {
    var currentScreen by remember {
        mutableStateOf(if (R2PipeManager.isConnected) AppScreen.Project else AppScreen.Home)
    }
    val context = LocalContext.current

    // 处理从外部 Intent 传入的文件 URI
    LaunchedEffect(pendingFileUri) {
        if (pendingFileUri != null) {
            val filePath = IntentFileResolver.resolve(context, pendingFileUri)
            if (filePath != null) {
                if (R2PipeManager.isConnected) {
                    R2PipeManager.forceClose()
                }
                R2PipeManager.pendingFilePath = filePath
                R2PipeManager.pendingRestoreFlags = null
                currentScreen = AppScreen.Project
            }
            onPendingFileUriConsumed()
        }
    }

    when (currentScreen) {
        AppScreen.Home -> {
            HomeScreen(
                onNavigateToProject = { currentScreen = AppScreen.Project },
                onNavigateToAbout = { currentScreen = AppScreen.About },
                onNavigateToSettings = { currentScreen = AppScreen.Settings },
                onNavigateToFeatures = { currentScreen = AppScreen.Features }
            )
        }
        AppScreen.About -> {
            BackHandler {
                currentScreen = AppScreen.Home
            }
            AboutScreen(
                onBackClick = { currentScreen = AppScreen.Home }
            )
        }
        AppScreen.Settings -> {
            BackHandler {
                currentScreen = AppScreen.Home
            }
            SettingsScreen(
                onBackClick = { currentScreen = AppScreen.Home }
            )
        }
        AppScreen.Features -> {
            BackHandler {
                currentScreen = AppScreen.Home
            }
            top.wsdx233.r2droid.screen.home.FeaturesScreen(
                onBackClick = { currentScreen = AppScreen.Home },
                onNavigateToR2Frida = { currentScreen = AppScreen.R2Frida },
                onCustomStart = { command ->
                    if (R2PipeManager.isConnected) {
                        R2PipeManager.forceClose()
                    }
                    val rawArgs = command.trim().removePrefix("r2 ").removePrefix("r2").trim()
                    R2PipeManager.pendingCustomCommand = rawArgs.ifBlank { "-" }
                    R2PipeManager.pendingFilePath = null
                    R2PipeManager.pendingRestoreFlags = null
                    currentScreen = AppScreen.Project
                }
            )
        }
        AppScreen.R2Frida -> {
            BackHandler {
                currentScreen = AppScreen.Features
            }
            top.wsdx233.r2droid.feature.r2frida.R2FridaScreen(
                onBack = { currentScreen = AppScreen.Features },
                onConnect = { command ->
                    if (R2PipeManager.isConnected) {
                        R2PipeManager.forceClose()
                    }
                    R2PipeManager.pendingCustomCommand = command
                    R2PipeManager.pendingFilePath = null
                    R2PipeManager.pendingRestoreFlags = null
                    currentScreen = AppScreen.Project
                }
            )
        }
        AppScreen.Project -> {
            // BackHandler is now handled inside ProjectScreen for unsaved project confirmation
            // This BackHandler only handles already-saved projects (or bypassed dialogs)
            BackHandler {
                currentScreen = AppScreen.Home
            }
            ProjectScreen(
                onNavigateBack = { currentScreen = AppScreen.Home }
            )
        }
    }
}

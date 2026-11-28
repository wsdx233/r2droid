package top.wsdx233.r2droid.feature.r2flutter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlutterDash
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.util.FlutterTargetResolver
import top.wsdx233.r2droid.util.R2FlutterInstallState
import top.wsdx233.r2droid.util.R2FlutterInstaller

private val FlutterAccent = Color(0xFF00897B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R2FlutterScreen(
    onBack: () -> Unit,
    onOpenTarget: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installState by R2FlutterInstaller.state.collectAsState()
    var installed by remember { mutableStateOf(R2FlutterInstaller.isInstalled(context)) }
    var selectedTarget by remember { mutableStateOf<FlutterTargetResolver.Target?>(null) }
    var targetError by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        resolving = true
        targetError = null
        scope.launch {
            FlutterTargetResolver.resolve(context, uri)
                .onSuccess { selectedTarget = it }
                .onFailure { targetError = it.message ?: context.getString(R.string.common_error) }
            resolving = false
        }
    }

    LaunchedEffect(installState.status) {
        if (installState.status == R2FlutterInstallState.Status.DONE) {
            installed = R2FlutterInstaller.isInstalled(context)
        }
    }
    DisposableEffect(Unit) {
        onDispose { R2FlutterInstaller.resetState() }
    }

    val installing = installState.status in setOf(
        R2FlutterInstallState.Status.FETCHING,
        R2FlutterInstallState.Status.DOWNLOADING,
        R2FlutterInstallState.Status.VERIFYING,
        R2FlutterInstallState.Status.INSTALLING
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.r2flutter_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !installing && !resolving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.menu_back))
                    }
                },
                actions = {
                    if (installed) {
                        IconButton(
                            onClick = {
                                installed = false
                                R2FlutterInstaller.resetState()
                            },
                            enabled = !installing && !resolving
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.r2flutter_reinstall))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!installed) {
                R2FlutterInstallContent(
                    state = installState,
                    installing = installing,
                    onInstall = { scope.launch { R2FlutterInstaller.install(context) } }
                )
            } else {
                R2FlutterReadyContent(
                    selectedTarget = selectedTarget,
                    resolving = resolving,
                    targetError = targetError,
                    onPickTarget = {
                        picker.launch(
                            arrayOf(
                                "application/vnd.android.package-archive",
                                "application/zip",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    onOpenTarget = onOpenTarget
                )
            }
        }
    }
}

@Composable
private fun R2FlutterReadyContent(
    selectedTarget: FlutterTargetResolver.Target?,
    resolving: Boolean,
    targetError: String?,
    onPickTarget: () -> Unit,
    onOpenTarget: (String) -> Unit
) {
    FlutterStatusBannerCard(
        icon = Icons.Default.CheckCircle,
        iconTint = FlutterAccent,
        title = stringResource(R.string.r2flutter_backend_ready),
        subtitle = stringResource(
            if (SettingsManager.useProotMode) {
                R.string.r2flutter_backend_proot
            } else {
                R.string.r2flutter_backend_native
            }
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            selectedTarget?.let { target ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(FlutterAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (target.extractedFromApk) Icons.Default.Archive else Icons.Default.FlutterDash,
                                contentDescription = null,
                                tint = FlutterAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = target.path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (resolving) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.r2flutter_resolving_target),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = onPickTarget,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !resolving
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.r2flutter_choose_target))
            }
        }
    }

    targetError?.let { error ->
        FlutterErrorCard(
            title = stringResource(R.string.common_error),
            message = error
        )
    }

    selectedTarget?.let { target ->
        Button(
            onClick = { onOpenTarget(target.path) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !resolving
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.r2flutter_open_analysis))
        }
    }
}

@Composable
private fun R2FlutterInstallContent(
    state: R2FlutterInstallState,
    installing: Boolean,
    onInstall: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(FlutterAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FlutterDash,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = FlutterAccent
            )
        }
    }

    FlutterStatusBannerCard(
        icon = Icons.Default.CloudDownload,
        iconTint = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.r2flutter_backend_not_installed),
        subtitle = stringResource(
            if (SettingsManager.useProotMode) {
                R.string.r2flutter_install_proot_desc
            } else {
                R.string.r2flutter_install_native_desc
            }
        )
    )

    AnimatedContent(
        targetState = state.status,
        label = "r2flutter_install_status"
    ) { status ->
        when (status) {
            R2FlutterInstallState.Status.FETCHING,
            R2FlutterInstallState.Status.DOWNLOADING,
            R2FlutterInstallState.Status.VERIFYING,
            R2FlutterInstallState.Status.INSTALLING -> {
                val hasDeterminateProgress = state.progress > 0f
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (hasDeterminateProgress) {
                            LinearProgressIndicator(
                                progress = { state.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            Text(
                                text = "${(state.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }
            }

            R2FlutterInstallState.Status.ERROR -> {
                FlutterErrorCard(
                    title = stringResource(R.string.r2flutter_install_failed),
                    message = state.message
                )
            }

            R2FlutterInstallState.Status.IDLE,
            R2FlutterInstallState.Status.DONE -> Unit
        }
    }

    Button(
        onClick = onInstall,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        enabled = !installing
    ) {
        if (installing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.r2flutter_install))
        } else {
            Icon(
                imageVector = if (state.status == R2FlutterInstallState.Status.ERROR) {
                    Icons.Default.Refresh
                } else {
                    Icons.Default.Download
                },
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.status == R2FlutterInstallState.Status.ERROR) {
                    stringResource(R.string.common_retry)
                } else {
                    stringResource(R.string.r2flutter_install)
                }
            )
        }
    }
}

@Composable
private fun FlutterStatusBannerCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FlutterErrorCard(
    title: String,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

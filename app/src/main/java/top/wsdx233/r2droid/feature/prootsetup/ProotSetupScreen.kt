package top.wsdx233.r2droid.feature.prootsetup

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.activity.TerminalActivity
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.util.ProotInstallState
import top.wsdx233.r2droid.util.ProotInstaller
import top.wsdx233.r2droid.util.ProotRootfsCatalog
import top.wsdx233.r2droid.util.ProotRootfsOption

private enum class ProotSetupMode {
    AUTO,
    MANUAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProotSetupScreen(
    onBackClick: () -> Unit,
    onContinue: (mode: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val installState by ProotInstaller.state.collectAsState()
    var rootfsOptions by remember(context) { mutableStateOf(ProotRootfsCatalog.load(context)) }
    var selectedMode by remember {
        mutableStateOf(
            if (SettingsManager.prootBuildMode == "manual") ProotSetupMode.MANUAL else ProotSetupMode.AUTO
        )
    }
    var selectedRootfsAlias by remember {
        mutableStateOf(SettingsManager.prootRootfsAlias.ifBlank { ProotRootfsCatalog.defaultOption().alias })
    }
    var installStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!installState.isWorking) {
            ProotInstaller.resetState()
        }
        rootfsOptions = ProotRootfsCatalog.refresh(context)
    }

    val canGoBack = !installState.isWorking
    BackHandler(enabled = canGoBack) {
        if (installStarted && installState.status != ProotInstallState.Status.IDLE) {
            ProotInstaller.resetState()
            installStarted = false
        } else {
            onBackClick()
        }
    }

    val selectedRootfs = rootfsOptions.firstOrNull { it.alias == selectedRootfsAlias }
        ?: rootfsOptions.firstOrNull()
        ?: ProotRootfsCatalog.defaultOption()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.proot_setup_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (installStarted && installState.status != ProotInstallState.Status.IDLE && canGoBack) {
                            ProotInstaller.resetState()
                            installStarted = false
                        } else if (canGoBack) {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        when {
            installStarted && installState.status == ProotInstallState.Status.DONE -> {
                ProotSetupCompletedPage(
                    modifier = Modifier.padding(padding),
                    mode = selectedMode,
                    message = installState.message,
                    onContinue = {
                        ProotInstaller.resetState()
                        installStarted = false
                        onContinue(if (selectedMode == ProotSetupMode.AUTO) "auto" else "manual")
                    },
                    onOpenTerminal = {
                        context.startActivity(Intent(context, TerminalActivity::class.java))
                    }
                )
            }

            installStarted && installState.status != ProotInstallState.Status.IDLE -> {
                ProotSetupProgressPage(
                    modifier = Modifier.padding(padding),
                    state = installState,
                    onRetry = {
                        coroutineScope.launch {
                            SettingsManager.prootRootfsAlias = selectedRootfs.alias
                            SettingsManager.prootBuildMode = if (selectedMode == ProotSetupMode.AUTO) "auto" else "manual"
                            val result = if (selectedMode == ProotSetupMode.AUTO) {
                                ProotInstaller.install(context, rootfsAlias = selectedRootfs.alias, forceReinstall = true)
                            } else {
                                ProotInstaller.installManual(context, rootfsAlias = selectedRootfs.alias, forceReinstall = true)
                            }
                            SettingsManager.useProotMode = result.isSuccess || SettingsManager.useProotMode
                        }
                    },
                    onChangeOptions = {
                        ProotInstaller.resetState()
                        installStarted = false
                    }
                )
            }

            else -> {
                ProotSetupOptionsPage(
                    modifier = Modifier.padding(padding),
                    selectedMode = selectedMode,
                    selectedRootfs = selectedRootfs,
                    rootfsOptions = rootfsOptions,
                    onModeSelected = { selectedMode = it },
                    onRootfsSelected = { selectedRootfsAlias = it.alias },
                    onStartInstall = {
                        SettingsManager.useProotMode = true
                        SettingsManager.prootBuildMode = if (selectedMode == ProotSetupMode.AUTO) "auto" else "manual"
                        SettingsManager.prootRootfsAlias = selectedRootfs.alias
                        installStarted = true
                        coroutineScope.launch {
                            val result = if (selectedMode == ProotSetupMode.AUTO) {
                                ProotInstaller.install(context, rootfsAlias = selectedRootfs.alias)
                            } else {
                                ProotInstaller.installManual(context, rootfsAlias = selectedRootfs.alias)
                            }
                            SettingsManager.useProotMode = result.isSuccess || SettingsManager.useProotMode
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProotSetupOptionsPage(
    modifier: Modifier = Modifier,
    selectedMode: ProotSetupMode,
    selectedRootfs: ProotRootfsOption,
    rootfsOptions: List<ProotRootfsOption>,
    onModeSelected: (ProotSetupMode) -> Unit,
    onRootfsSelected: (ProotRootfsOption) -> Unit,
    onStartInstall: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.proot_setup_screen_headline),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.proot_setup_screen_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.proot_setup_choose_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                SetupModeRow(
                    selected = selectedMode == ProotSetupMode.AUTO,
                    title = stringResource(R.string.proot_build_mode_auto),
                    description = stringResource(R.string.proot_build_mode_auto_desc),
                    onClick = { onModeSelected(ProotSetupMode.AUTO) }
                )
                SetupModeRow(
                    selected = selectedMode == ProotSetupMode.MANUAL,
                    title = stringResource(R.string.proot_build_mode_manual),
                    description = stringResource(R.string.proot_build_mode_manual_desc),
                    onClick = { onModeSelected(ProotSetupMode.MANUAL) }
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.proot_setup_choose_rootfs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.proot_setup_choose_rootfs_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rootfsOptions.forEach { option ->
                    RootfsOptionRow(
                        option = option,
                        selected = selectedRootfs.alias == option.alias,
                        onClick = { onRootfsSelected(option) }
                    )
                }
            }
        }

        Button(
            onClick = onStartInstall,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(
                    if (selectedMode == ProotSetupMode.AUTO) {
                        R.string.proot_setup_start_auto
                    } else {
                        R.string.proot_setup_start_manual
                    }
                )
            )
        }
    }
}

@Composable
private fun SetupModeRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(top = 10.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RootfsOptionRow(
    option: ProotRootfsOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.padding(top = 2.dp)) {
                Text(text = option.displayName, style = MaterialTheme.typography.bodyLarge)
                val summary = buildString {
                    append(option.alias)
                    if (option.comment.isNotBlank()) {
                        append(" · ")
                        append(option.comment)
                    }
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (option.isRecommended) {
            Text(
                text = stringResource(R.string.proot_setup_rootfs_recommended),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 52.dp)
            )
        }
    }
}

@Composable
private fun ProotSetupProgressPage(
    modifier: Modifier = Modifier,
    state: ProotInstallState,
    onRetry: () -> Unit,
    onChangeOptions: () -> Unit
) {
    val logsScroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.proot_setup_progress_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = state.message.ifBlank { stringResource(R.string.proot_setup_preparing) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.isWorking) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        LinearProgressIndicator(
            progress = { state.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${(state.progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = stringResource(R.string.proot_setup_logs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(logsScroll)
                ) {
                    Text(
                        text = if (state.logs.isEmpty()) stringResource(R.string.proot_setup_logs_empty) else state.logs.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (state.status == ProotInstallState.Status.ERROR) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onChangeOptions, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.proot_setup_change_options))
                }
                Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
    }
}

@Composable
private fun ProotSetupCompletedPage(
    modifier: Modifier = Modifier,
    mode: ProotSetupMode,
    message: String,
    onContinue: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.proot_setup_done_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message.ifBlank {
                    stringResource(
                        if (mode == ProotSetupMode.AUTO) {
                            R.string.proot_setup_done_auto_desc
                        } else {
                            R.string.proot_setup_done_manual_desc
                        }
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (mode == ProotSetupMode.MANUAL) {
                OutlinedButton(
                    onClick = onOpenTerminal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.proot_open_terminal))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.proot_setup_done_continue))
            }
        }
    }
}

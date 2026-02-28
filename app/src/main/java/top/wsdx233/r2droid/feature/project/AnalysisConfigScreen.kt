package top.wsdx233.r2droid.feature.project

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.core.data.prefs.SettingsManager
import top.wsdx233.r2droid.R
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisConfigScreen(
    filePath: String,
    onStartAnalysis: (cmd: String, writable: Boolean, flags: String) -> Unit
) {
    var selectedLevel by remember { mutableStateOf("aaa") }
    var customCmd by remember { mutableStateOf("") }
    var isWritable by remember { mutableStateOf(false) }
    var customFlags by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    var fileSize by remember { mutableStateOf(0L) }
    var benchmarkScore by remember { mutableStateOf(SettingsManager.analysisBenchmarkScore) }
    var benchmarking by remember { mutableStateOf(false) }
    
    LaunchedEffect(filePath) {
        try {
            val file = java.io.File(filePath)
            if (file.exists()) {
                fileSize = file.length()
            } else if (filePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(filePath)
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        val benchmarkExpired = System.currentTimeMillis() - SettingsManager.analysisBenchmarkAt > TimeUnit.DAYS.toMillis(7)
        if (benchmarkScore <= 0f || benchmarkExpired) {
            benchmarking = true
            benchmarkScore = runAndSaveAnalysisBenchmark()
            benchmarking = false
        }
    }
    
    val isHeavyAnalysis = selectedLevel == "aaa" || selectedLevel == "aaaa"
    val isLargeFile = fileSize > 1024 * 1024 // 1MB
    val showWarning = isHeavyAnalysis && isLargeFile
    val estimateSeconds = estimateAnalysisSeconds(
        fileSizeBytes = fileSize,
        selectedLevel = selectedLevel,
        customCmd = customCmd,
        benchmarkScore = benchmarkScore
    )

    Scaffold(
        topBar = {
             CenterAlignedTopAppBar(title = { Text(stringResource(R.string.analysis_config_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // File Info
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.analysis_target_file), style = MaterialTheme.typography.labelMedium)
                    Text(filePath, style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            // Analysis Level
            Text(stringResource(R.string.analysis_level_title), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                AnalysisConfigLevelCard(
                    title = stringResource(R.string.analysis_level_none),
                    desc = stringResource(R.string.proj_analysis_aa_desc),
                    icon = Icons.Default.Block,
                    selected = selectedLevel == "none",
                    onClick = { selectedLevel = "none" }
                )
                AnalysisConfigLevelCard(
                    title = stringResource(R.string.proj_analysis_aa),
                    desc = stringResource(R.string.proj_analysis_aa_desc),
                    icon = Icons.Default.FlashOn,
                    selected = selectedLevel == "aa",
                    onClick = { selectedLevel = "aa" }
                )
                AnalysisConfigLevelCard(
                    title = stringResource(R.string.proj_analysis_aaa),
                    desc = stringResource(R.string.proj_analysis_aaa_desc),
                    icon = Icons.Default.Speed,
                    selected = selectedLevel == "aaa",
                    onClick = { selectedLevel = "aaa" }
                )
                AnalysisConfigLevelCard(
                    title = stringResource(R.string.proj_analysis_aaaa),
                    desc = stringResource(R.string.proj_analysis_aaaa_desc),
                    icon = Icons.Default.Psychology,
                    selected = selectedLevel == "aaaa",
                    onClick = { selectedLevel = "aaaa" }
                )
                AnalysisConfigLevelCard(
                    title = stringResource(R.string.analysis_level_custom),
                    desc = stringResource(R.string.analysis_custom_cmd_hint),
                    icon = Icons.Default.Code,
                    selected = selectedLevel == "custom",
                    onClick = { selectedLevel = "custom" }
                )
            }
            
            if (selectedLevel == "custom") {
                OutlinedTextField(
                    value = customCmd,
                    onValueChange = { customCmd = it },
                    label = { Text(stringResource(R.string.analysis_custom_cmd_label)) },
                    placeholder = { Text(stringResource(R.string.analysis_custom_cmd_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Warning Card
            if (showWarning) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.analysis_large_file_warning_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.analysis_large_file_warning_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                val estimateText = when {
                    benchmarking -> stringResource(R.string.analysis_estimated_time_benchmarking)
                    estimateSeconds == null -> stringResource(R.string.analysis_estimated_time_unavailable)
                    estimateSeconds == 0L -> stringResource(R.string.analysis_estimated_time_none)
                    else -> stringResource(
                        R.string.analysis_estimated_time_value,
                        formatDuration(estimateSeconds)
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
                                    shape = CircleShape
                                )
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (benchmarking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.analysis_estimated_time_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                            Text(
                                text = estimateText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Text(
                        text = stringResource(
                            R.string.analysis_estimated_time_details,
                            formatFileSize(fileSize),
                            benchmarkScore
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            
            HorizontalDivider()
            
            // Startup Options
            Text(stringResource(R.string.analysis_startup_options), style = MaterialTheme.typography.titleMedium)
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isWritable = !isWritable }) {
                Checkbox(checked = isWritable, onCheckedChange = { isWritable = it })
                Text(stringResource(R.string.analysis_writable_mode))
            }
            
            OutlinedTextField(
                value = customFlags,
                onValueChange = { customFlags = it },
                label = { Text(stringResource(R.string.analysis_startup_flags_label)) },
                placeholder = { Text(stringResource(R.string.analysis_startup_flags_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    val finalCmd = if (selectedLevel == "custom") customCmd else selectedLevel
                    onStartAnalysis(finalCmd, isWritable, customFlags)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.analysis_start_btn))
            }
        }
    }
}

private suspend fun runAndSaveAnalysisBenchmark(): Float = withContext(Dispatchers.Default) {
    val iterations = maxOf(6_000_000, Runtime.getRuntime().availableProcessors() * 1_500_000)
    val seed = 0x9E3779B97F4A7C15UL.toLong()
    val mulA = 0xC2B2AE3D27D4EB4FUL.toLong()
    val mulB = 0x165667B19E3779F9UL.toLong()
    var acc = seed
    val startNanos = System.nanoTime()

    for (i in 0 until iterations) {
        acc = acc xor ((i.toLong() + 0x9E37L) * mulA)
        acc = java.lang.Long.rotateLeft(acc, 13)
        acc *= mulB
    }

    val elapsedNanos = (System.nanoTime() - startNanos).coerceAtLeast(1L)
    val opsPerSecond = iterations / (elapsedNanos / 1_000_000_000f)
    val score = ((opsPerSecond / 18_000_000f) + ((acc and 0x7L) * 0f)).coerceIn(0.2f, 5f)

    SettingsManager.analysisBenchmarkScore = score
    SettingsManager.analysisBenchmarkAt = System.currentTimeMillis()
    score
}

private fun estimateAnalysisSeconds(
    fileSizeBytes: Long,
    selectedLevel: String,
    customCmd: String,
    benchmarkScore: Float
): Long? {
    val cmd = (if (selectedLevel == "custom") customCmd.trim() else selectedLevel).lowercase()
    if (cmd.isBlank()) return null
    if (cmd == "none") return 0L
    if (fileSizeBytes <= 0L || benchmarkScore <= 0f) return null

    val complexityFactor = when {
        cmd.contains("aaaa") -> 20.0f
        cmd.contains("aaa") -> 5.0f
        cmd.contains("aa") -> 3.0f
        cmd.contains("af") -> 1.0f
        else -> 1.8f
    }

    val fileMb = (fileSizeBytes / (1024f * 1024f)).coerceAtLeast(0.1f)
    val cpuScore = benchmarkScore.coerceIn(0.2f, 5f)
    val seconds = 2.0f + (fileMb * complexityFactor * 3.4f / cpuScore)
    return seconds.roundToLong().coerceAtLeast(1L)
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format("%dh %dm", hours, minutes)
        minutes > 0 -> String.format("%dm %ds", minutes, seconds)
        else -> String.format("%ds", seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "-"
    val kb = 1024f
    val mb = kb * 1024f
    val gb = mb * 1024f
    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.2f MB", bytes / mb)
        bytes >= kb -> String.format("%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

@Composable
private fun AnalysisConfigLevelCard(
    title: String,
    desc: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

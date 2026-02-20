package top.wsdx233.r2droid.feature.r2frida.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.r2frida.data.*
import top.wsdx233.r2droid.util.LogEntry
import top.wsdx233.r2droid.util.LogType

@Composable
fun FridaOverviewScreen(info: FridaInfo?) {
    if (info == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.r2frida_overview_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        OverviewInfoCard(
            icon = Icons.Default.Memory,
            title = "Runtime",
            items = listOf(
                "Arch" to "${info.arch} (${info.bits}bit)",
                "OS" to info.os,
                "Runtime" to info.runtime,
                "Page Size" to "${info.pageSize}",
                "Pointer Size" to "${info.pointerSize}"
            )
        )
        OverviewInfoCard(
            icon = Icons.Default.Apps,
            title = "Process",
            items = listOf(
                "PID" to "${info.pid}",
                "UID" to "${info.uid}",
                "Module" to info.moduleName,
                "Base" to info.moduleBase
            )
        )
        if (info.packageName.isNotEmpty()) {
            OverviewInfoCard(
                icon = Icons.Default.Android,
                title = "Android",
                items = listOf(
                    "Package" to info.packageName,
                    "Data Dir" to info.dataDir,
                    "CWD" to info.cwd,
                    "Code Path" to info.codePath
                )
            )
        }
        OverviewInfoCard(
            icon = Icons.Default.Extension,
            title = "Features",
            items = listOf(
                "Java" to if (info.java) "Yes" else "No",
                "ObjC" to if (info.objc) "Yes" else "No",
                "Swift" to if (info.swift) "Yes" else "No"
            )
        )
    }
}

@Composable
private fun OverviewInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    items: List<Pair<String, String>>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            items.forEach { (key, value) ->
                if (value.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(key, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer {
                            Text(value, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FridaScriptScreen(
    logs: List<LogEntry>,
    running: Boolean,
    onRun: (String) -> Unit
) {
    var script by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = script,
            onValueChange = { script = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text(stringResource(R.string.r2frida_script_hint)) },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            shape = RoundedCornerShape(12.dp)
        )
        Button(
            onClick = { onRun(script) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !running && script.isNotBlank(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (running) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.r2frida_script_running))
            } else {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.r2frida_script_run))
            }
        }
        Text(stringResource(R.string.r2frida_script_output),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val bg = colorResource(R.color.command_output_background)
        val fg = colorResource(R.color.command_output_text)
        val ph = colorResource(R.color.command_output_placeholder)
        Box(
            modifier = Modifier
                .fillMaxWidth().weight(1f)
                .background(bg, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (logs.isEmpty()) {
                Text(stringResource(R.string.r2frida_script_no_output),
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ph)
            } else {
                SelectionContainer {
                    Column {
                        logs.forEach { entry ->
                            val color = when (entry.type) {
                                LogType.ERROR -> MaterialTheme.colorScheme.error
                                LogType.WARNING -> colorResource(R.color.command_output_placeholder)
                                else -> fg
                            }
                            Text(entry.message, fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp, color = color)
                        }
                    }
                }
            }
        }
    }
}

package top.wsdx233.r2droid.feature.project

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportReportScreen(onDismiss: () -> Unit) {
    var format by remember { mutableStateOf(ExportFormat.MARKDOWN) }
    var includeFunctions by remember { mutableStateOf(true) }
    var maxFunctions by remember { mutableFloatStateOf(100f) }
    var includeStrings by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf("") }
    
    val baseFileName = R2PipeManager.currentFilePath?.substringAfterLast("/") ?: "report"
    
    val mimeType = when (format) {
        ExportFormat.MARKDOWN -> "text/markdown"
        ExportFormat.HTML -> "text/html"
        ExportFormat.JSON -> "application/json"
        ExportFormat.FRIDA -> "text/javascript"
    }

    val ext = when (format) {
        ExportFormat.MARKDOWN -> ".md"
        ExportFormat.HTML -> ".html"
        ExportFormat.JSON -> ".json"
        ExportFormat.FRIDA -> ".js"
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType)
    ) { uri ->
        if (uri != null) {
            isExporting = true
            coroutineScope.launch {
                val opts = ExportOptions(
                    format = format,
                    includeFunctions = includeFunctions,
                    maxFunctions = maxFunctions.toInt(),
                    includeStrings = includeStrings,
                    includeSymbols = includeSymbols
                )
                val res = ReportExporter.exportReport(context, uri, opts) { status ->
                    exportStatus = status
                }
                isExporting = false
                res.onSuccess {
                    android.widget.Toast.makeText(context, context.getString(R.string.export_report_success), android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                }.onFailure { err ->
                    android.widget.Toast.makeText(context, context.getString(R.string.export_report_error, err.message), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isExporting) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.export_report_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Format Section
            Text(
                text = stringResource(R.string.export_report_format),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            FormatCard(
                title = stringResource(R.string.export_report_format_md),
                desc = stringResource(R.string.export_report_format_md_desc),
                icon = Icons.Default.Description,
                selected = format == ExportFormat.MARKDOWN,
                onClick = { format = ExportFormat.MARKDOWN }
            )
            FormatCard(
                title = stringResource(R.string.export_report_format_html),
                desc = stringResource(R.string.export_report_format_html_desc),
                icon = Icons.Default.Language,
                selected = format == ExportFormat.HTML,
                onClick = { format = ExportFormat.HTML }
            )
            FormatCard(
                title = stringResource(R.string.export_report_format_json),
                desc = stringResource(R.string.export_report_format_json_desc),
                icon = Icons.Default.DataObject,
                selected = format == ExportFormat.JSON,
                onClick = { format = ExportFormat.JSON }
            )
            FormatCard(
                title = stringResource(R.string.export_report_format_frida),
                desc = stringResource(R.string.export_report_format_frida_desc),
                icon = Icons.Default.Code,
                selected = format == ExportFormat.FRIDA,
                onClick = { format = ExportFormat.FRIDA }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Content Section
            Text(
                text = stringResource(R.string.export_report_content),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeFunctions, onCheckedChange = { includeFunctions = it })
                Text(stringResource(R.string.export_report_include_functions), style = MaterialTheme.typography.bodyLarge)
            }
            if (includeFunctions) {
                Column(modifier = Modifier.padding(start = 48.dp, end = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.export_report_max_export),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (maxFunctions >= 1000f) stringResource(R.string.export_report_all_functions) else stringResource(R.string.export_report_n_functions, maxFunctions.toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = maxFunctions,
                        onValueChange = { maxFunctions = it },
                        valueRange = 10f..1000f,
                        steps = 98 
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeStrings, onCheckedChange = { includeStrings = it })
                Text(stringResource(R.string.export_report_include_strings), style = MaterialTheme.typography.bodyLarge)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeSymbols, onCheckedChange = { includeSymbols = it })
                Text(stringResource(R.string.export_report_include_symbols), style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Actions
            if (isExporting) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = exportStatus, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            createDocLauncher.launch("${baseFileName}_report$ext")
                        }
                    ) {
                        Text(stringResource(R.string.export_report_confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun FormatCard(
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
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.5f) else Color.Transparent
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

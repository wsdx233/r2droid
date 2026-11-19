package top.wsdx233.r2droid.feature.debug.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.debug.data.DebugTraceEntry
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@Composable
fun TracePanel(
    traceEntries: List<DebugTraceEntry>,
    onClearTrace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.debug_panel_trace_title),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = LocalAppFont.current,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onClearTrace, enabled = traceEntries.isNotEmpty()) {
                Text(stringResource(R.string.debug_panel_trace_clear))
            }
        }
        Spacer(Modifier.height(8.dp))

        if (traceEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.debug_panel_trace_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(traceEntries, key = { it.index }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "#${entry.index}  0x${entry.pc.toULong().toString(16).uppercase()}  ${entry.action}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = LocalAppFont.current,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (entry.changedRegisters.isNotEmpty()) {
                        Text(
                            text = entry.changedRegisters.entries.joinToString("  ") { (name, change) ->
                                "$name: 0x${change.first.toString(16)}→0x${change.second.toString(16)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = LocalAppFont.current,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    entry.reason?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

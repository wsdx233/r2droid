package top.wsdx233.r2droid.feature.debug.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.debug.data.DebugThread
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import java.util.Locale

@Composable
fun ThreadsPanel(
    threads: List<DebugThread>,
    isLoading: Boolean,
    error: String?,
    isSupported: Boolean,
    onRefresh: () -> Unit,
    onSelectThread: (String) -> Unit,
    onJumpToAddress: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DebugSectionHeader(
            title = stringResource(R.string.debug_threads_title),
            count = threads.size.takeIf { isSupported }
        ) {
            if (isSupported) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.common_refresh),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        when {
            !isSupported -> {
                DebugEmptyState(
                    icon = Icons.Default.AccountTree,
                    message = stringResource(R.string.debug_threads_unsupported)
                )
            }
            isLoading && threads.isEmpty() -> {
                DebugLoadingBox()
            }
            error != null -> {
                DebugErrorBanner(message = error, onRetry = onRefresh)
            }
            threads.isEmpty() -> {
                DebugEmptyState(
                    icon = Icons.Default.AccountTree,
                    message = stringResource(R.string.debug_threads_empty)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(threads, key = { it.id }) { thread ->
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !thread.selected) { onSelectThread(thread.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (thread.selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (thread.selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        DebugBadgePill(
                                            text = "TID ${thread.id}",
                                            containerColor = if (thread.selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                            contentColor = if (thread.selected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            },
                                            monospace = true
                                        )
                                        if (thread.selected) {
                                            DebugBadgePill(
                                                text = stringResource(R.string.debug_threads_current),
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                        thread.status?.let { rawStatus ->
                                            val label = formatThreadStatus(rawStatus)
                                            DebugBadgePill(
                                                text = label,
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (thread.name.isNotBlank() && thread.name != thread.id) {
                                        Text(
                                            text = thread.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    thread.pc?.let { pc ->
                                        Surface(
                                            onClick = { onJumpToAddress(pc) },
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = "PC ${formatDebugHex(pc)}",
                                                fontFamily = LocalAppFont.current,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (!thread.selected) {
                                    FilledTonalButton(
                                        onClick = { onSelectThread(thread.id) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.debug_threads_switch),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatThreadStatus(raw: String): String {
    return when (raw.trim().lowercase(Locale.ROOT)) {
        "r" -> "RUNNING"
        "s" -> "SLEEPING"
        "t" -> "STOPPED"
        "z" -> "ZOMBIE"
        "d" -> "DISK"
        "x" -> "DEAD"
        else -> raw.trim().uppercase(Locale.ROOT)
    }
}

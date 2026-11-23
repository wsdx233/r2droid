package top.wsdx233.r2droid.feature.debug.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
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
import top.wsdx233.r2droid.feature.debug.data.DebugFrame
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FramesPanel(
    frames: List<DebugFrame>,
    isLoading: Boolean,
    error: String?,
    isSupported: Boolean,
    onRefresh: () -> Unit,
    onJumpToAddress: (Long) -> Unit,
    onInspectMemory: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DebugSectionHeader(
            title = stringResource(R.string.debug_frames_title),
            count = frames.size.takeIf { isSupported }
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
                    icon = Icons.Default.Layers,
                    message = stringResource(R.string.debug_frames_unsupported)
                )
            }
            isLoading && frames.isEmpty() -> {
                DebugLoadingBox()
            }
            error != null -> {
                DebugErrorBanner(message = error, onRetry = onRefresh)
            }
            frames.isEmpty() -> {
                DebugEmptyState(
                    icon = Icons.Default.Layers,
                    message = stringResource(R.string.debug_frames_empty)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(frames, key = { "${it.index}_${it.address}" }) { frame ->
                        val isTop = frame.index == 0
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToAddress(frame.address) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isTop) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isTop) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DebugBadgePill(
                                        text = "#${frame.index}",
                                        containerColor = if (isTop) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        contentColor = if (isTop) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        monospace = true
                                    )
                                    Text(
                                        text = frame.functionName ?: stringResource(R.string.debug_frames_unknown_func),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatDebugHex(frame.address),
                                        fontFamily = LocalAppFont.current,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }

                                val hasMeta = frame.sp != null || frame.bp != null || frame.size != null || !frame.description.isNullOrBlank()
                                if (hasMeta) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        frame.sp?.let { sp ->
                                            FramePointerChip(
                                                label = "SP ${formatDebugHex(sp)}",
                                                onClick = { onInspectMemory(sp) }
                                            )
                                        }
                                        frame.bp?.let { bp ->
                                            FramePointerChip(
                                                label = "BP ${formatDebugHex(bp)}",
                                                onClick = { onInspectMemory(bp) }
                                            )
                                        }
                                        frame.size?.let { size ->
                                            DebugBadgePill(
                                                text = "$size B",
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                monospace = true
                                            )
                                        }
                                        frame.description?.let { desc ->
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.align(Alignment.CenterVertically)
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
}

@Composable
private fun FramePointerChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                fontFamily = LocalAppFont.current,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1
            )
        }
    }
}

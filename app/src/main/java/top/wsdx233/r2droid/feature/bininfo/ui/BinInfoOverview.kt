package top.wsdx233.r2droid.feature.bininfo.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.ArchInfo
import top.wsdx233.r2droid.core.data.model.BinInfo
import top.wsdx233.r2droid.core.data.model.BlockStatsData
import top.wsdx233.r2droid.core.data.model.EntropyData
import top.wsdx233.r2droid.core.data.model.EntryPoint
import top.wsdx233.r2droid.core.data.model.HashInfo
import top.wsdx233.r2droid.core.data.model.HeaderInfo
import top.wsdx233.r2droid.core.ui.components.ListItemActions
import top.wsdx233.r2droid.core.ui.components.UnifiedListItemWrapper
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@Composable
fun OverviewCard(info: BinInfo, actions: ListItemActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BasicInfoCard(info, actions)
        
        info.hashes?.let { if (it.md5.isNotBlank()) HashesCard(it, actions) }
        
        if (info.archs.isNotEmpty()) {
            ArchsCard(info.archs, actions)
        }
        
        if (info.entryPoints.isNotEmpty()) {
            EntryPointsCard(info.entryPoints, actions)
        }
        
        if (info.headers.isNotEmpty() || !info.headersString.isNullOrBlank()) {
            HeadersCard(info.headers, info.headersString, actions)
        }
        
        info.entropy?.let { if (it.entropy.isNotEmpty()) EntropyCard(it, actions) }
        
        info.blockStats?.let { if (it.blocks.isNotEmpty()) BlockStatsCard(it, actions) }
        
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
fun OverviewSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            content()
        }
    }
}

@Composable
fun ExpandableContentList(
    itemCount: Int,
    initialVisibleCount: Int = 5,
    content: @Composable (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val visibleCount = if (expanded) itemCount else minOf(itemCount, initialVisibleCount)
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until visibleCount) {
            content(i)
        }
        
        if (itemCount > initialVisibleCount) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (expanded) stringResource(R.string.binary_show_less) 
                           else stringResource(R.string.binary_and_more, itemCount - initialVisibleCount)
                )
            }
        }
    }
}

@Composable
fun BasicInfoCard(info: BinInfo, actions: ListItemActions) {
    OverviewSectionCard(
        title = stringResource(id = R.string.binary_overview_title),
        icon = Icons.Default.Info
    ) {
        InfoRow(stringResource(R.string.binary_arch), info.arch, actions = actions)
        InfoRow(stringResource(R.string.binary_bits), "${info.bits}", actions = actions)
        InfoRow(stringResource(R.string.binary_os), info.os, actions = actions)
        InfoRow(stringResource(R.string.binary_type), info.type, actions = actions)
        InfoRow(stringResource(R.string.binary_machine), info.machine, actions = actions)
        InfoRow(stringResource(R.string.binary_language), info.language, actions = actions)
        if (info.compiled.isNotBlank()) InfoRow(stringResource(R.string.binary_compiled), info.compiled, actions = actions)
        if (info.subSystem.isNotBlank()) InfoRow(stringResource(R.string.binary_subsystem), info.subSystem, actions = actions)
        if (info.size > 0) InfoRow(stringResource(R.string.binary_size), stringResource(R.string.binary_size_bytes, info.size), actions = actions)
        
        info.guessedSize?.let { if (it > 0) InfoRow(stringResource(R.string.binary_guessed_size), stringResource(R.string.binary_size_bytes, it), actions = actions) }
        info.mainAddr?.let { if (it.vaddr > 0) InfoRow(stringResource(R.string.binary_main_address), "0x${it.vaddr.toString(16)}", address = it.vaddr, actions = actions) }
    }
}

@Composable
fun HashesCard(hashes: HashInfo, actions: ListItemActions) {
    OverviewSectionCard(title = stringResource(R.string.binary_hashes), icon = Icons.Default.Security) {
        if (hashes.md5.isNotBlank()) InfoRow(stringResource(R.string.binary_md5), hashes.md5, actions = actions)
        if (hashes.sha1.isNotBlank()) InfoRow(stringResource(R.string.binary_sha1), hashes.sha1, actions = actions)
        if (hashes.sha256.isNotBlank()) InfoRow(stringResource(R.string.binary_sha256), hashes.sha256, actions = actions)
    }
}

@Composable
fun ArchsCard(archs: List<ArchInfo>, actions: ListItemActions) {
    OverviewSectionCard(title = stringResource(R.string.binary_architectures), icon = Icons.Default.Memory) {
        ExpandableContentList(itemCount = archs.size, initialVisibleCount = 3) { index ->
            val arch = archs[index]
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                InfoRow(stringResource(R.string.binary_arch), arch.arch, actions = actions)
                InfoRow(stringResource(R.string.binary_bits), "${arch.bits}", actions = actions)
                InfoRow(stringResource(R.string.binary_machine), arch.machine, actions = actions)
            }
            if (index < archs.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f))
            }
        }
    }
}

@Composable
fun EntryPointsCard(entryPoints: List<EntryPoint>, actions: ListItemActions) {
    OverviewSectionCard(title = stringResource(R.string.binary_entry_points), icon = Icons.Default.MyLocation) {
        ExpandableContentList(itemCount = entryPoints.size, initialVisibleCount = 5) { index ->
            val ep = entryPoints[index]
            InfoRow(ep.type.ifBlank { "Entry" }.replaceFirstChar { it.uppercase() }, "0x${ep.vAddr.toString(16)}", address = ep.vAddr, actions = actions)
        }
    }
}

@Composable
fun HeadersCard(headers: List<HeaderInfo>, headersString: String?, actions: ListItemActions) {
    OverviewSectionCard(title = stringResource(R.string.binary_headers), icon = Icons.AutoMirrored.Filled.List) {
        if (!headersString.isNullOrBlank()) {
            var expanded by remember { mutableStateOf(false) }
            val lines = headersString.lines()
            val visibleLines = if (expanded) lines else lines.take(10)
            
            UnifiedListItemWrapper(
                title = "Headers",
                address = null,
                fullText = headersString,
                actions = actions,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(
                        text = visibleLines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = LocalAppFont.current,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (lines.size > 10) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (expanded) stringResource(R.string.binary_show_less) 
                                       else stringResource(R.string.binary_and_more, lines.size - 10)
                            )
                        }
                    }
                }
            }
        } else if (headers.isNotEmpty()) {
            ExpandableContentList(itemCount = headers.size, initialVisibleCount = 10) { index ->
                val h = headers[index]
                InfoRow(h.name, "0x${h.value.toString(16)}", address = h.value, actions = actions) 
            }
        }
    }
}

@Composable
fun EntropyCard(entropyData: EntropyData, actions: ListItemActions) {
    OverviewSectionCard(title = stringResource(R.string.binary_entropy), icon = Icons.Default.Analytics) {
        InfoRow(stringResource(R.string.binary_block_size), stringResource(R.string.binary_size_bytes, entropyData.blocksize), actions = actions)
        Spacer(Modifier.height(12.dp))
        val values = entropyData.entropy.map { it.value.toFloat() }
        val maxVal = 255f
        val color = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val y = size.height - (value / maxVal * size.height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            
            val fillPath = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height
                )
            )

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
fun BlockStatsCard(stats: BlockStatsData, actions: ListItemActions) {
    OverviewSectionCard(title = stringResource(R.string.binary_block_stats), icon = Icons.Default.Map) {
        InfoRow(stringResource(R.string.binary_block_size), stringResource(R.string.binary_size_bytes, stats.blocksize), actions = actions)
        Spacer(Modifier.height(12.dp))
        val blocks = stats.blocks
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            val stepX = size.width / blocks.size.coerceAtLeast(1)
            blocks.forEachIndexed { index, block ->
                val color = when {
                    "x" in block.perm -> Color(0xFF5DADE2)
                    "w" in block.perm -> Color(0xFF58D68D)
                    "r" in block.perm -> Color(0xFFCACFD2)
                    else -> Color(0xFFFACC14)
                }
                drawRect(
                    color = color,
                    topLeft = Offset(index * stepX, 0f),
                    size = Size(stepX, size.height)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LegendItem(stringResource(R.string.binary_exec), Color(0xFF5DADE2))
            LegendItem(stringResource(R.string.binary_write), Color(0xFF58D68D))
            LegendItem(stringResource(R.string.binary_read), Color(0xFFCACFD2))
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoRow(label: String, value: String, address: Long? = null, actions: ListItemActions) {
    UnifiedListItemWrapper(
        title = label,
        address = address,
        fullText = value,
        actions = actions,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            val isAddress = address != null
            if (isAddress) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    onClick = { actions.onJumpToDisasm(address!!) }
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontFamily = LocalAppFont.current,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        textAlign = TextAlign.End
                    )
                }
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = LocalAppFont.current,
                    modifier = Modifier.weight(2f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}


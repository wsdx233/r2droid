package top.wsdx233.r2droid.core.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.XrefWithDisasm
import top.wsdx233.r2droid.core.data.model.XrefsData
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@Composable
fun XrefsDialog(
    xrefsData: XrefsData,
    targetAddress: Long,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit
) {
    val hasRefsFrom = xrefsData.refsFrom.isNotEmpty()
    val hasRefsTo = xrefsData.refsTo.isNotEmpty()
    val hasNoRefs = !hasRefsFrom && !hasRefsTo
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(stringResource(R.string.xrefs_title))
                Text(
                    text = "@ 0x${targetAddress.toULong().toString(16).uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = LocalAppFont.current,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            if (hasNoRefs) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.xrefs_no_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left column: Refs FROM (axfj) - references from current address to other addresses
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        // Header
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.xrefs_refs_from),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "(${xrefsData.refsFrom.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        // List
                        if (xrefsData.refsFrom.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.xrefs_no_outgoing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(xrefsData.refsFrom) { xrefWithDisasm ->
                                    XrefItem(
                                        xref = xrefWithDisasm,
                                        isRefsFrom = true,
                                        onClick = { onJump(xrefWithDisasm.xref.to) }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Divider
                    VerticalDivider()
                    
                    // Right column: Refs TO (axtj) - references from other addresses to current address
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        // Header
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.xrefs_refs_to),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "(${xrefsData.refsTo.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        // List
                        if (xrefsData.refsTo.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.xrefs_no_incoming),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(xrefsData.refsTo) { xrefWithDisasm ->
                                    XrefItem(
                                        xref = xrefWithDisasm,
                                        isRefsFrom = false,
                                        onClick = { onJump(xrefWithDisasm.xref.from) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.xrefs_close))
            }
        }
    )
}

/**
 * Individual xref item with detailed info.
 */
@Composable
private fun XrefItem(
    xref: XrefWithDisasm,
    isRefsFrom: Boolean,
    onClick: () -> Unit
) {
    val address = if (isRefsFrom) xref.xref.to else xref.xref.from
    
    // Color based on type
    val typeColor = when (xref.xref.type.uppercase()) {
        "CALL" -> Color(0xFF42A5F5) // Blue
        "JMP", "CJMP" -> Color(0xFF66BB6A) // Green
        "DATA" -> Color(0xFFFFCA28) // Yellow/Orange
        "CODE" -> Color(0xFFAB47BC) // Purple
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Address and Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "0x${address.toULong().toString(16).uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = typeColor.copy(alpha = 0.2f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = xref.xref.type,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = typeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            // Disassembly
            if (xref.disasm.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = xref.disasm,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = LocalAppFont.current,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            
            // Function name (show for both refs from and refs to)
            if (xref.xref.fcnName.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isRefsFrom) "→ ${xref.xref.fcnName}" else "in ${xref.xref.fcnName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1
                )
            }
            
            // Bytes
            if (xref.bytes.isNotBlank()) {
                Text(
                    text = xref.bytes.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = LocalAppFont.current,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
        }
    }
}

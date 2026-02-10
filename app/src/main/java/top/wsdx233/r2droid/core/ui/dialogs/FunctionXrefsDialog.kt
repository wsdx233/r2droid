package top.wsdx233.r2droid.core.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.FunctionXref

@Composable
fun FunctionXrefsDialog(
    xrefs: List<FunctionXref>,
    isLoading: Boolean,
    targetAddress: Long,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${stringResource(R.string.func_xrefs_title)} @ 0x${targetAddress.toString(16).uppercase()}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center)
                        )
                    }
                    xrefs.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.func_xrefs_no_found),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        FunctionXrefsContent(
                            xrefs = xrefs,
                            targetAddress = targetAddress,
                            onJump = onJump
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.func_close))
            }
        }
    )
}

@Composable
private fun FunctionXrefsContent(
    xrefs: List<FunctionXref>,
    targetAddress: Long,
    onJump: (Long) -> Unit
) {
    // Separate into calls FROM this function and calls TO this function
    val callsFrom = xrefs.filter { it.from == targetAddress }
    val callsTo = xrefs.filter { it.to == targetAddress }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Calls From section
        if (callsFrom.isNotEmpty()) {
            SectionHeader(
                text = "${stringResource(R.string.func_xrefs_calls_from)} (${callsFrom.size})",
                color = Color(0xFF42A5F5)
            )
            Spacer(Modifier.height(4.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (callsTo.isNotEmpty()) 180.dp else 360.dp)
        ) {
            items(callsFrom) { xref ->
                FunctionXrefItem(
                    xref = xref,
                    targetAddress = targetAddress,
                    onClick = { onJump(xref.to) }
                )
            }
        }

        if (callsFrom.isNotEmpty() && callsTo.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Calls To section
        if (callsTo.isNotEmpty()) {
            SectionHeader(
                text = "${stringResource(R.string.func_xrefs_calls_to)} (${callsTo.size})",
                color = Color(0xFF66BB6A)
            )
            Spacer(Modifier.height(4.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (callsFrom.isNotEmpty()) 180.dp else 360.dp)
        ) {
            items(callsTo) { xref ->
                FunctionXrefItem(
                    xref = xref,
                    targetAddress = targetAddress,
                    onClick = { onJump(xref.from) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FunctionXrefItem(
    xref: FunctionXref,
    targetAddress: Long,
    onClick: () -> Unit
) {
    val typeColor = when (xref.type.uppercase()) {
        "CALL" -> Color(0xFF42A5F5)
        "JMP", "CJMP" -> Color(0xFF66BB6A)
        "DATA" -> Color(0xFFFFCA28)
        "CODE" -> Color(0xFFAB47BC)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type badge
            Surface(
                color = typeColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = xref.type,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = typeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // From address
            Text(
                text = "0x${xref.from.toString(16).uppercase()}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (xref.from == targetAddress)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary
            )

            Text(
                text = " \u2192 ",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // To address
            Text(
                text = "0x${xref.to.toString(16).uppercase()}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (xref.to == targetAddress)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary
            )
        }
    }
}

package top.wsdx233.r2droid.core.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.InstructionDetail
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@Composable
fun InstructionDetailDialog(
    detail: InstructionDetail?,
    isLoading: Boolean,
    targetAddress: Long,
    onDismiss: () -> Unit,
    onJump: ((Long) -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.instr_detail_title))
                Text(
                    text = "@ 0x${targetAddress.toString(16).uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = LocalAppFont.current,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                detail == null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.instr_detail_no_data),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    InstructionDetailContent(detail, onJump = onJump?.let { jump ->
                        { addr: Long -> jump(addr); onDismiss() }
                    })
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
private fun InstructionDetailContent(
    detail: InstructionDetail,
    onJump: ((Long) -> Unit)?
) {
    SelectionContainer {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Opcode - primary highlight
        DetailHighlightCard(
            label = stringResource(R.string.instr_detail_opcode),
            value = detail.opcode
        )

        // Description - primary highlight
        DetailHighlightCard(
            label = stringResource(R.string.instr_detail_description),
            value = detail.description.ifBlank { "-" }
        )

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        // Other fields
        DetailRow(stringResource(R.string.instr_detail_disasm), detail.disasm)
        DetailRow(stringResource(R.string.instr_detail_pseudo), detail.pseudo.ifBlank { "-" })
        DetailRow(stringResource(R.string.instr_detail_mnemonic), detail.mnemonic)
        DetailRow(
            stringResource(R.string.instr_detail_address),
            "0x${detail.addr.toString(16).uppercase()}"
        )
        DetailRow(stringResource(R.string.instr_detail_bytes), detail.bytes.uppercase())
        DetailRow(stringResource(R.string.instr_detail_size), "${detail.size} bytes")
        DetailRow(stringResource(R.string.instr_detail_type), detail.type)
        DetailRow(stringResource(R.string.instr_detail_family), detail.family)

        if (detail.jump != null) {
            DetailAddressRow(
                label = stringResource(R.string.instr_detail_jump),
                address = detail.jump,
                onJump = onJump
            )
        }
        if (detail.fail != null) {
            DetailAddressRow(
                label = stringResource(R.string.instr_detail_fail),
                address = detail.fail,
                onJump = onJump
            )
        }

        DetailRow(stringResource(R.string.instr_detail_esil), detail.esil.ifBlank { "-" })
        DetailRow(stringResource(R.string.instr_detail_cycles), detail.cycles.toString())
        DetailRow(
            stringResource(R.string.instr_detail_sign),
            if (detail.sign) stringResource(R.string.common_yes)
            else stringResource(R.string.common_no)
        )
    }
    }
}

@Composable
private fun DetailHighlightCard(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = LocalAppFont.current,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = LocalAppFont.current,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DetailAddressRow(
    label: String,
    address: Long,
    onJump: ((Long) -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        if (onJump != null) {
            TextButton(onClick = { onJump(address) }) {
                Text(
                    text = "0x${address.toString(16).uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = LocalAppFont.current
                )
            }
        } else {
            Text(
                text = "0x${address.toString(16).uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = LocalAppFont.current,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

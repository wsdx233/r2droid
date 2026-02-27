package top.wsdx233.r2droid.feature.project

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.util.R2PipeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchArchBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSwitching by remember { mutableStateOf(false) }
    
    val toastMessage = stringResource(R.string.switch_arch_toast)

    val applyArch = { commands: List<String> ->
        if (!isSwitching) {
            isSwitching = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    commands.forEach { cmd ->
                        R2PipeManager.execute(cmd)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                }
                isSwitching = false
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.switch_arch_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 2x2 Grid for archs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArchCard(
                    modifier = Modifier.weight(1f),
                    title = "32位 ARM",
                    icon = Icons.Filled.Smartphone,
                    commands = listOf("e asm.arch=arm", "e anal.arch=arm", "e asm.bits=32", "e asm.endian=little"),
                    onClick = applyArch,
                    isSwitching = isSwitching,
                    isSelected = false
                )
                ArchCard(
                    modifier = Modifier.weight(1f),
                    title = "32位 ARM Thumb",
                    icon = Icons.Filled.Memory,
                    commands = listOf("e anal.arch=arm", "e anal.bits=16", "e asm.endian=little"),
                    onClick = applyArch,
                    isSwitching = isSwitching,
                    isSelected = false
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArchCard(
                    modifier = Modifier.weight(1f),
                    title = "64位 ARM",
                    icon = Icons.Filled.Language,
                    commands = listOf("e asm.arch=arm", "e anal.arch=arm", "e asm.bits=64", "e asm.endian=little"),
                    onClick = applyArch,
                    isSwitching = isSwitching,
                    isSelected = false
                )
                ArchCard(
                    modifier = Modifier.weight(1f),
                    title = "x64",
                    icon = Icons.Filled.Computer,
                    commands = listOf("e asm.arch=x86", "e asm.bits=64", "e asm.syntax=intel", "e asm.endian=little"),
                    onClick = applyArch,
                    isSwitching = isSwitching,
                    isSelected = false
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Restore Default Option
            ListItem(
                headlineContent = { Text(stringResource(R.string.switch_arch_default)) },
                supportingContent = { Text(stringResource(R.string.switch_arch_default_desc)) },
                leadingContent = {
                    Icon(Icons.Filled.Restore, contentDescription = null)
                },
                modifier = Modifier.clickable(enabled = !isSwitching) {
                    // Restore to some defaults that just unset user options
                    applyArch(listOf("e asm.arch=", "e asm.bits=", "e anal.arch=", "e anal.bits=", "e asm.endian="))
                }
            )

        }
    }
}

@Composable
private fun ArchCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    commands: List<String>,
    onClick: (List<String>) -> Unit,
    isSwitching: Boolean,
    isSelected: Boolean
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable(enabled = !isSwitching) { onClick(commands) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
        }
    }
}

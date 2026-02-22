package top.wsdx233.r2droid.feature.debug.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterBottomSheet(
    registers: JSONObject,
    onDismissRequest: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = bottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Registers",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
                fontFamily = LocalAppFont.current
            )

            val keys = mutableListOf<String>()
            registers.keys().forEach { keys.add(it) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(keys) { key ->
                    val valueStr = when (val value = registers.opt(key)) {
                        is Number -> "0x${value.toLong().toString(16).padStart(8, '0')}"
                        else -> "$value"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = key.uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontFamily = LocalAppFont.current,
                            modifier = Modifier.weight(0.4f),
                            maxLines = 1
                        )
                        Text(
                            text = valueStr,
                            fontFamily = LocalAppFont.current,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(0.6f),
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

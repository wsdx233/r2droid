package top.wsdx233.r2droid.feature.disasm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.feature.disasm.DisasmScrollbarPreview
import top.wsdx233.r2droid.feature.disasm.data.DisasmDataManager

/**
 * Keep high-frequency preview state out of the viewer/menu/debug composition. The original list
 * stays mounted underneath, preserving its position and the scrollbar's ongoing pointer gesture.
 */
@Composable
internal fun DisasmScrollbarPreviewLayer(
    previewState: StateFlow<DisasmScrollbarPreview?>,
    instructions: List<DisasmInstruction>,
    listState: LazyListState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val preview by previewState.collectAsState()
    val target = preview ?: return
    val index = DisasmDataManager.coveredIndex(instructions, target.address)
    // Wait for the decoded target to actually reach layout, not just for a cache publication.
    if (index >= 0 && listState.layoutInfo.visibleItemsInfo.any { it.key == instructions[index].addr }) return

    Column(
        Modifier.fillMaxSize()
            .testTag("disasm_scrollbar_placeholder")
            .background(MaterialTheme.colorScheme.surface)
            // Own this hit-test area so hidden rows cannot receive taps/drags. A normal tap
            // detector (rather than consuming every event) still allows the retry/back buttons.
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            }
            .padding(8.dp)
    ) {
        if (target.failed) {
            Text(
                text = stringResource(R.string.disasm_scrollbar_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.disasm_scrollbar_back)) }
            }
        }
        DisasmPlaceholderContent(
            modifier = Modifier.fillMaxWidth().weight(1f),
            animate = !target.failed
        )
    }
}

package top.wsdx233.r2droid.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Termux-style extra keys bar with two rows of shortcut keys.
 *
 * Row 1: ESC  /  -  HOME  ↑  END  PGUP
 * Row 2: TAB  CTRL  ALT  ←  ↓  →  PGDN
 */
@Composable
fun ExtraKeysBar(
    onSendKey: (String) -> Unit,
    ctrlActive: Boolean,
    altActive: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        // Row 1: ESC / - HOME ↑ END PGUP
        ExtraKeyRow(
            keys = ROW_1_KEYS,
            onSendKey = onSendKey,
            ctrlActive = ctrlActive,
            altActive = altActive,
            onCtrlToggle = onCtrlToggle,
            onAltToggle = onAltToggle
        )
        Spacer(modifier = Modifier.height(3.dp))
        // Row 2: TAB CTRL ALT ← ↓ → PGDN
        ExtraKeyRow(
            keys = ROW_2_KEYS,
            onSendKey = onSendKey,
            ctrlActive = ctrlActive,
            altActive = altActive,
            onCtrlToggle = onCtrlToggle,
            onAltToggle = onAltToggle
        )
    }
}

@Composable
private fun ExtraKeyRow(
    keys: List<ExtraKeyDef>,
    onSendKey: (String) -> Unit,
    ctrlActive: Boolean,
    altActive: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        keys.forEach { key ->
            val isActive = when (key.modifier) {
                ModifierType.CTRL -> ctrlActive
                ModifierType.ALT -> altActive
                null -> false
            }
            ExtraKeyButton(
                label = key.label,
                isActive = isActive,
                modifier = Modifier.weight(1f),
                onClick = {
                    when (key.modifier) {
                        ModifierType.CTRL -> onCtrlToggle()
                        ModifierType.ALT -> onAltToggle()
                        null -> onSendKey(key.sequence)
                    }
                }
            )
        }
    }
}

@Composable
private fun ExtraKeyButton(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) Color(0xFF5C6BC0) else Color(0xFF424242)
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// --- Key definitions ---

private enum class ModifierType { CTRL, ALT }

private data class ExtraKeyDef(
    val label: String,
    val sequence: String = "",
    val modifier: ModifierType? = null
)

private val ROW_1_KEYS = listOf(
    ExtraKeyDef("ESC", "\u001b"),
    ExtraKeyDef("/", "/"),
    ExtraKeyDef("—", "-"),
    ExtraKeyDef("HOME", "\u001b[H"),
    ExtraKeyDef("↑", "\u001b[A"),
    ExtraKeyDef("END", "\u001b[F"),
    ExtraKeyDef("PGUP", "\u001b[5~"),
)

private val ROW_2_KEYS = listOf(
    ExtraKeyDef("⇥", "\t"),
    ExtraKeyDef("CTRL", modifier = ModifierType.CTRL),
    ExtraKeyDef("ALT", modifier = ModifierType.ALT),
    ExtraKeyDef("←", "\u001b[D"),
    ExtraKeyDef("↓", "\u001b[B"),
    ExtraKeyDef("→", "\u001b[C"),
    ExtraKeyDef("PGDN", "\u001b[6~"),
)

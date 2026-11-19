package top.wsdx233.r2droid.feature.disasm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.core.data.model.OverviewBand
import top.wsdx233.r2droid.core.data.model.formatCompactAddress
import top.wsdx233.r2droid.core.ui.components.BinaryPermissionColors
import top.wsdx233.r2droid.feature.disasm.DisasmScrollbarPreview
import top.wsdx233.r2droid.feature.disasm.data.DisasmDataManager
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import kotlin.math.roundToInt

internal val DisasmThumbWidth = 8.dp
internal const val PREVIEW_ROWS = 2048
private const val PREVIEW_WINDOW = 64 // Show 1/32 of the address space around the target.
private val BubbleBlue = Color(0xFF175BA6)
private val PreviewBackground = Color(0xFF163453)
private val PreviewShape = RoundedCornerShape(4.dp)

/** Non-interactive sibling of the hit track, also visible on cache hits where no placeholder exists. */
@Composable
internal fun DisasmDragBubble(
    previewState: StateFlow<DisasmScrollbarPreview?>,
    manager: DisasmDataManager,
    instructions: List<DisasmInstruction>,
    bands: List<OverviewBand>,
    thumbCenter: FloatState
) {
    val preview by previewState.collectAsState()
    val address = preview?.address ?: return
    val region = manager.addressOverview.regionAt(address)
    val instruction = instructions.getOrNull(DisasmDataManager.coveredIndex(instructions, address))
    val op = instruction?.run { opcode.ifBlank { disasm } }.orEmpty()
    val detail = if (region == null || region.name.isBlank()) op.ifBlank { "—" } else buildString {
        append(region.name).append("+0x").append(formatCompactAddress(address - region.start))
        if (op.isNotBlank()) append(" · ").append(op)
    }
    val range = (manager.viewEndAddress - 1 - manager.viewStartAddress).coerceAtLeast(1)
    val centerRow = ((address - manager.viewStartAddress).toDouble() / range * PREVIEW_ROWS).toFloat()
    Layout(modifier = Modifier.fillMaxSize(), content = {
        Row(
            Modifier.testTag("disasm_drag_bubble").widthIn(min = 112.dp, max = 252.dp)
                .background(BubbleBlue, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            RegionPreview(bands, centerRow)
            Column {
                Text("0x${formatCompactAddress(address)}", color = Color.White, fontFamily = LocalAppFont.current,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, softWrap = false)
                Text(detail, color = Color.White.copy(alpha = .9f), fontFamily = LocalAppFont.current,
                    fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.size(8.dp, 12.dp).drawWithCache {
            val tip = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }
            onDrawBehind { drawPath(tip, BubbleBlue) }
        })
    }) { children, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val tipX = (width - DisasmThumbWidth.roundToPx()).coerceAtLeast(0)
        val tip = children[1].measure(Constraints(maxWidth = tipX, maxHeight = height))
        val body = children[0].measure(Constraints(maxWidth = tipX - tip.width, maxHeight = height))
        layout(width, height) {
            // Clamp the body and pointer separately. At both ends the tip still points at the thumb.
            val y = thumbCenter.floatValue.roundToInt()
            body.place(tipX - tip.width - body.width, (y - body.height / 2).coerceIn(0, height - body.height))
            tip.place(tipX - tip.width, (y - tip.height / 2).coerceIn(0, height - tip.height))
        }
    }
}

/** The text sets the row height; the preview takes that same dimension on both axes. */
@Composable
private fun RegionPreview(bands: List<OverviewBand>, centerRow: Float) {
    Canvas(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)
        .clip(PreviewShape).background(PreviewBackground)) {
        val top = centerRow - PREVIEW_WINDOW / 2
        val scale = size.height / PREVIEW_WINDOW
        // Find the first intersecting band, not the first band in the binary.
        var index = bands.binarySearch { it.endRow.compareTo(top.toInt()) }
        index = if (index < 0) -index - 1 else index + 1
        while (index < bands.size) {
            val band = bands[index++]
            if (band.firstRow >= top + PREVIEW_WINDOW) break
            val y = (band.firstRow - top) * scale
            drawRect(BinaryPermissionColors[band.kind], Offset(0f, y), Size(size.width, (band.endRow - band.firstRow) * scale))
            if (band.separatorBefore) drawLine(PreviewBackground, Offset(0f, y), Offset(size.width, y))
        }
        drawLine(Color.White, Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
    }
}

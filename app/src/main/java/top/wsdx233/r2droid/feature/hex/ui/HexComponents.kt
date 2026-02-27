package top.wsdx233.r2droid.feature.hex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.ui.theme.LocalAppFont

@Composable
fun HexPlaceholderRow(
    addr: Long,
    hexAddressBackground: Color = Color(0xFFDDDDDD),
    hexAddressText: Color = Color.Black,
    hexDivider: Color = Color(0xFFBDBDBD)
) {
    val hexPlaceholderRow = colorResource(R.color.hex_placeholder_row)
    val hexPlaceholderBlock = colorResource(R.color.hex_placeholder_block)
    val appFont = LocalAppFont.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(hexPlaceholderRow)
            .height(IntrinsicSize.Min)
    ) {
        // Address
        Box(
            modifier = Modifier
                .width(70.dp)
                .fillMaxHeight()
                .background(hexAddressBackground)
                .padding(start = 4.dp, top = 2.dp)
        ) {
            Text(
                text = "%06X".format(addr),
                color = hexAddressText,
                fontFamily = appFont,
                fontSize = 12.sp,
                lineHeight = 14.sp
            )
        }
        
        VerticalDivider()
        
        // Placeholder hex area
        Row(Modifier.weight(1f)) {
            repeat(8) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .padding(2.dp)
                        .background(hexPlaceholderBlock, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                )
            }
        }
        
        VerticalDivider()
        
        // Placeholder ASCII area
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(20.dp)
                .padding(4.dp)
                .background(hexPlaceholderBlock, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun HexVisualRow(
    addr: Long, 
    bytes: List<Byte>, 
    index: Int, 
    cursorAddress: Long,
    selectedColumn: Int,
    highlightColor: Color,
    onByteClick: (Long) -> Unit,
    onByteLongClick: (Long) -> Unit = {},
    showMenu: Boolean = false,
    menuTargetAddress: Long? = null,
    menuContent: @Composable () -> Unit = {},
    editingBuffer: String = "",
    hexAddressBackground: Color = Color(0xFFDDDDDD),
    hexAddressText: Color = Color(0xFF424242),
    hexRowEven: Color = Color.White,
    hexRowOdd: Color = Color(0xFFE8EAF6),
    hexDivider: Color = Color(0xFFBDBDBD),
    hexByteText: Color = Color.Black
) {
    // 8 bytes row
    val oddRow = (addr / 8) % 2 == 1L
    val appFont = LocalAppFont.current
    
    // Check if this row contains the cursor
    val rowStartAddr = addr
    val rowEndAddr = addr + bytes.size - 1
    val isRowSelected = cursorAddress in rowStartAddr..rowEndAddr
    
    // Base background: alternating colors (zebra stripes)
    val baseBgColor = if (oddRow) hexRowOdd else hexRowEven

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(baseBgColor)
            .height(IntrinsicSize.Min)
    ) {
        // Address with gray background - CENTERED, BOLD, DARK GRAY
        Box(
            modifier = Modifier
                .width(70.dp)
                .fillMaxHeight()
                .background(hexAddressBackground), // Gray background for address
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "%06X".format(addr), 
                color = hexAddressText, // Dark gray
                fontFamily = appFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp
            )
        }
        
        VerticalDivider()
        
        // Hex area — single gesture handler at Row level instead of per-byte pointerInput.
        // Column is computed from tap X coordinate, reducing gesture node count from 8 to 1.
        val primaryContainer = MaterialTheme.colorScheme.primaryContainer
        val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        val density = LocalDensity.current
        val dividerPx = with(density) { 1.dp.toPx() }

        var hexRowWidthPx by remember { mutableIntStateOf(0) }

        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .onSizeChanged { hexRowWidthPx = it.width }
                .pointerInput(addr, bytes.size) {
                    detectTapGestures(
                        onTap = { offset ->
                            val col = computeHexColumn(offset.x, hexRowWidthPx.toFloat(), dividerPx, bytes.size)
                            if (col in 0 until bytes.size) onByteClick(addr + col)
                        },
                        onLongPress = { offset ->
                            val col = computeHexColumn(offset.x, hexRowWidthPx.toFloat(), dividerPx, bytes.size)
                            if (col in 0 until bytes.size) onByteLongClick(addr + col)
                        }
                    )
                }
        ) {
             bytes.forEachIndexed { i, b ->
                val byteAddr = addr + i
                val isSelected = (byteAddr == cursorAddress)
                val isColumnHighlighted = (i == selectedColumn)

                if (i == 4) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(hexDivider))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isSelected) primaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isSelected && (isRowSelected || isColumnHighlighted)) {
                        Box(Modifier.matchParentSize().background(highlightColor))
                    }

                    val displayText = if (isSelected && editingBuffer.isNotEmpty()) {
                         editingBuffer
                    } else {
                         "%02X".format(b)
                    }
                    val textColor = if (isSelected) {
                        if (editingBuffer.isNotEmpty()) tertiaryColor else onPrimaryContainer
                    } else {
                        hexByteText
                    }

                    Text(
                         text = displayText,
                         fontFamily = appFont,
                         fontSize = 13.sp,
                         color = textColor,
                         textAlign = TextAlign.Center,
                         fontWeight = FontWeight.Medium
                    )

                    if (showMenu && byteAddr == menuTargetAddress) {
                         menuContent()
                    }
                }
             }
             repeat(8 - bytes.size) { padIndex ->
                 val actualIndex = bytes.size + padIndex
                 if (actualIndex == 4) {
                     Box(Modifier.width(1.dp).fillMaxHeight().background(hexDivider))
                 }
                 Spacer(Modifier.weight(1f))
             }
        }
        
        VerticalDivider()
        
        // ASCII area — single gesture handler instead of per-char clickable
        val charWidthDp = 12.dp
        val asciiPaddingDp = 4.dp
        val charWidthPx = with(density) { charWidthDp.toPx() }
        val asciiPaddingPx = with(density) { asciiPaddingDp.toPx() }

        Row(
            Modifier
                .width(100.dp)
                .padding(start = asciiPaddingDp)
                .pointerInput(addr, bytes.size) {
                    detectTapGestures(
                        onTap = { offset ->
                            val col = ((offset.x) / charWidthPx).toInt().coerceIn(0, bytes.size - 1)
                            onByteClick(addr + col)
                        }
                    )
                }
        ) {
            bytes.forEachIndexed { i, b ->
                val byteAddr = addr + i
                val isSelected = (byteAddr == cursorAddress)
                val isColumnHighlighted = (i == selectedColumn)
                val c = b.toInt().toChar()
                val charStr = if (c.isISOControl() || !c.isDefined()) "." else c.toString()

                Box(
                    modifier = Modifier
                        .width(charWidthDp)
                        .background(if (isSelected) primaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isSelected && (isRowSelected || isColumnHighlighted)) {
                        Box(Modifier.matchParentSize().background(highlightColor))
                    }
                    Text(
                        text = charStr,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (isSelected) onPrimaryContainer else hexByteText
                    )
                }
            }
        }
    }
}

/**
 * Compute which byte column (0..byteCount-1) was tapped in the hex area.
 * Accounts for the 1dp divider between columns 3 and 4.
 */
private fun computeHexColumn(tapX: Float, totalWidth: Float, dividerPx: Float, byteCount: Int): Int {
    if (byteCount <= 0 || totalWidth <= 0f) return -1
    val cellWidth = (totalWidth - dividerPx) / 8f
    // Left half: columns 0-3
    if (tapX < cellWidth * 4) {
        return (tapX / cellWidth).toInt().coerceIn(0, minOf(3, byteCount - 1))
    }
    // Divider zone
    if (tapX < cellWidth * 4 + dividerPx) return 3.coerceAtMost(byteCount - 1)
    // Right half: columns 4-7
    val rightX = tapX - cellWidth * 4 - dividerPx
    return (4 + (rightX / cellWidth).toInt()).coerceIn(4, minOf(7, byteCount - 1))
}

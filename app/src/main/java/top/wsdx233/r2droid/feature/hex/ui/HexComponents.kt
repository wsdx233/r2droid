package top.wsdx233.r2droid.feature.hex.ui

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.ui.theme.LocalAppFont
import top.wsdx233.r2droid.ui.theme.LocalDarkTheme

// Reserve the entire scrollbar touch track so the last ASCII cell remains tappable.
internal val HexScrollbarWidth = 16.dp

private const val COLUMNS = 8
private const val GROUP_SIZE = 4
private const val MIN_ADDRESS_DIGITS = 6
private const val HEX_DIGITS = "0123456789ABCDEF"
private val HexLabels = Array(256) { "${HEX_DIGITS[it shr 4]}${HEX_DIGITS[it and 15]}" }
private val AsciiLabels = Array(256) { if (it in 0x20..0x7e) it.toChar().toString() else "." }
private val ColumnLabels = Array(COLUMNS) { it.toString() }

@Composable
internal fun rememberHexGridStyle(): HexGridStyle {
    val density = LocalDensity.current
    val font = LocalAppFont.current
    val resolver = LocalFontFamilyResolver.current
    val byteTypeface = resolver.resolve(font, FontWeight.Medium).value as Typeface
    val addressTypeface = resolver.resolve(font, FontWeight.Bold).value as Typeface
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme

    val palette = remember(context, configuration, dark, scheme) {
        val nightMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val resourceContext = if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == nightMode) {
            context
        } else {
            context.createConfigurationContext(Configuration(configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            })
        }
        fun color(id: Int) = Color(resourceContext.getColor(id))
        HexGridPalette(
            headerBackground = color(R.color.hex_header_background),
            contentBackground = color(R.color.hex_content_background),
            footerBackground = color(R.color.hex_footer_background),
            separator = color(R.color.hex_separator_line),
            addressBackground = color(R.color.hex_address_background),
            rowEven = color(R.color.hex_row_even),
            rowOdd = color(R.color.hex_row_odd),
            divider = color(R.color.hex_divider),
            placeholderRow = color(R.color.hex_placeholder_row),
            placeholderBlock = color(R.color.hex_placeholder_block),
            highlight = Color(0x4DFFFDE7),
            selected = scheme.primaryContainer,
            headerText = color(R.color.hex_column_header_text).toArgb(),
            addressText = color(R.color.hex_address_text).toArgb(),
            byteText = color(R.color.hex_byte_text).toArgb(),
            selectedText = scheme.onPrimaryContainer.toArgb(),
            editingText = scheme.tertiary.toArgb()
        )
    }
    return remember(density, byteTypeface, addressTypeface, palette) {
        HexGridStyle(density, byteTypeface, addressTypeface, palette)
    }
}

internal data class HexGridPalette(
    val headerBackground: Color,
    val contentBackground: Color,
    val footerBackground: Color,
    val separator: Color,
    val addressBackground: Color,
    val rowEven: Color,
    val rowOdd: Color,
    val divider: Color,
    val placeholderRow: Color,
    val placeholderBlock: Color,
    val highlight: Color,
    val selected: Color,
    val headerText: Int,
    val addressText: Int,
    val byteText: Int,
    val selectedText: Int,
    val editingText: Int
)

/** Shared by the header and all rows of one viewer. Paints are only mutated during drawing. */
@Stable
internal class HexGridStyle(
    density: Density,
    byteTypeface: Typeface,
    addressTypeface: Typeface,
    val palette: HexGridPalette
) {
    val bytePaint = centeredPaint(byteTypeface, with(density) { 13.sp.toPx() })
    val asciiPaint = centeredPaint(Typeface.MONOSPACE, bytePaint.textSize)
    val addressPaint = centeredPaint(addressTypeface, with(density) { 12.sp.toPx() })
    val hexGlyphWidth = HEX_DIGITS.maxOf { bytePaint.measureText(it.toString()) }
    val asciiGlyphWidth = (0x20..0x7e).maxOf { asciiPaint.measureText(AsciiLabels[it]) }
    val addressGlyphWidth = HEX_DIGITS.maxOf { addressPaint.measureText(it.toString()) }
    val inset = with(density) { 2.dp.toPx() }
    private val byteMetrics = bytePaint.fontMetrics
    private val asciiMetrics = asciiPaint.fontMetrics
    private val addressMetrics = addressPaint.fontMetrics
    private val fontTop = minOf(byteMetrics.top, asciiMetrics.top, addressMetrics.top)
    private val fontBottom = maxOf(byteMetrics.bottom, asciiMetrics.bottom, addressMetrics.bottom)
    private val lineHeight = maxOf(with(density) { 20.dp.roundToPx() }, ceil(fontBottom - fontTop + inset).toInt())
    val baseline = (lineHeight - fontBottom - fontTop) / 2f
    private val divider = with(density) { 1.dp.roundToPx().coerceAtLeast(1).toFloat() }
    private val addressWidth = maxOf(with(density) { 70.dp.toPx() }, addressGlyphWidth * MIN_ADDRESS_DIGITS + 2 * inset)
    private val asciiCellWidth = maxOf(with(density) { 12.dp.toPx() }, asciiGlyphWidth + inset)
    private val asciiPadding = with(density) { 4.dp.toPx() }
    private val minHexCellWidth = 2 * hexGlyphWidth + inset
    val lineHeightDp = with(density) { lineHeight.toDp() }

    // Most callers have the same width; switching widths (e.g. resizing) cannot leave stale geometry.
    private var lastGeometry: HexGridGeometry? = null

    fun geometry(width: Int): HexGridGeometry = lastGeometry?.takeIf { it.width == width }
        ?: HexGridGeometry(width, lineHeight, divider, addressWidth, asciiCellWidth, asciiPadding, minHexCellWidth)
            .also { lastGeometry = it }
}

private fun centeredPaint(font: Typeface, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    textSize = size
    typeface = font
    textAlign = Paint.Align.CENTER
}

@Composable
internal fun HexGridHeader(selectedColumn: Int, style: HexGridStyle) {
    Canvas(
        Modifier.fillMaxWidth()
            .background(style.palette.headerBackground)
            .padding(end = HexScrollbarWidth)
            .height(style.lineHeightDp)
            .semantics { text = AnnotatedString("0 1 2 3 4 5 6 7") }
    ) {
        val grid = style.geometry(size.width.roundToInt())
        drawGridDividers(grid, 0f, size.height, style.palette.divider)
        val paint = style.bytePaint
        val canvas = drawContext.canvas.nativeCanvas
        for (pane in HexGridPane.entries) {
            val cellWidth = grid.cellWidth(pane)
            paint.textScaleX = fitText(cellWidth, style.hexGlyphWidth, style.inset)
            for (column in 0 until COLUMNS) {
                val left = grid.cellLeft(pane, column)
                if (column == selectedColumn) {
                    drawRect(style.palette.highlight, Offset(left, 0f), Size(cellWidth, size.height))
                }
                paint.color = style.palette.headerText
                canvas.drawText(ColumnLabels[column], left + cellWidth / 2f, style.baseline, paint)
            }
        }
    }
}

/** [byteCount] is the view extent at this address, not the amount available in the chunk. */
@Composable
internal fun HexGridRow(
    addr: Long,
    chunk: ByteArray?,
    offset: Int,
    byteCount: Int,
    cursorAddress: Long,
    selectedColumn: Int,
    editingBuffer: String,
    style: HexGridStyle,
    onByteClick: (Long) -> Unit,
    onByteLongClick: (Long) -> Unit,
    menuAddress: Long?,
    menuContent: @Composable (Long) -> Unit
) {
    val row = remember(chunk, offset, byteCount) { HexGridRowBytes(chunk, offset, byteCount) }
    val firstAddress = remember(addr) { formatHexGridAddress(addr) }
    val secondAddress = if (row.lineCount == 2) remember(addr) { formatHexGridAddress(addr + COLUMNS) } else ""
    val selectedIndex = row.indexOf(addr, cursorAddress)
    val editing = if (selectedIndex >= 0) editingBuffer else ""
    val missingText = if (row.available == row.expected) "" else
        stringResource(if (chunk == null) R.string.analysis_loading else R.string.hex_no_data)

    val click by rememberUpdatedState(onByteClick)
    val longClick by rememberUpdatedState(onByteLongClick)
    var lastHit by remember(addr, row.available) { mutableStateOf<HexGridHit?>(null) }
    val menuIndex = if (menuAddress == null) -1 else row.indexOf(addr, menuAddress)
    val menuPane = if (menuIndex < 0) HexGridPane.Hex
        else lastHit?.takeIf { it.index == menuIndex }?.pane ?: HexGridPane.Hex
    val canvasModifier = Modifier.fillMaxWidth()
        .height(style.lineHeightDp * row.lineCount)
        .semantics {
            // Build the spoken row when semantics are requested, not while binding a scrolling item.
            text = AnnotatedString(buildString {
                for (line in 0 until row.lineCount) {
                    if (line > 0) append('\n')
                    append(if (line == 0) firstAddress else secondAddress)
                    val start = line * COLUMNS
                    val end = minOf(start + COLUMNS, row.available)
                    for (index in start until end) {
                        append(' ')
                        append(if (index == selectedIndex && editing.isNotEmpty()) editing else HexLabels[row[index]])
                    }
                    if (end > start) {
                        append("  ")
                        for (index in start until end) append(AsciiLabels[row[index]])
                    }
                    if (end < minOf(start + COLUMNS, row.expected)) append(' ').append(missingText)
                }
            })
        }
        .then(if (row.available == 0) Modifier else Modifier.pointerInput(addr, row.available, style) {
            fun hitAt(position: Offset, width: Int) = style.geometry(width)
                .hitTest(position.x, position.y, row.available)

            fun dispatch(hit: HexGridHit, callback: (Long) -> Unit) {
                lastHit = hit
                callback(addr + hit.index)
            }

            awaitEachGesture {
                val down = awaitFirstDown()
                val hit = hitAt(down.position, size.width) ?: return@awaitEachGesture
                down.consume()
                val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        if (hitAt(up.position, size.width) == hit) dispatch(hit, click)
                    }
                    true
                }
                if (ended == null) {
                    // Recheck the geometry if the grid resized while the pointer was held.
                    if (hitAt(down.position, size.width) != hit) return@awaitEachGesture
                    dispatch(hit, longClick)
                    do {
                        val event = awaitPointerEvent()
                        for (change in event.changes) change.consume()
                    } while (event.changes.any { it.pressed })
                }
            }
        })

    // The drawn row is also the popup's parent: no child to compose or measure during scrolling.
    Layout(modifier = canvasModifier.drawBehind {
        drawHexGridRow(style, addr, row, firstAddress, secondAddress, selectedIndex, selectedColumn, editing)
    }, content = {
        if (menuAddress != null && menuIndex >= 0) {
            key(menuAddress) { Box { menuContent(menuAddress) } }
        }
    }) { measurables, constraints ->
        // fillMaxWidth/height on the row modifier have already fixed its dimensions.
        val width = constraints.minWidth
        val anchor = if (measurables.isEmpty()) null else style.geometry(width).cellBounds(menuIndex, menuPane)
        val menu = anchor?.let { measurables.single().measure(Constraints.fixed(it.width, it.height)) }
        layout(width, constraints.minHeight) {
            if (menu != null) menu.place(anchor.left, anchor.top)
        }
    }
}

private fun DrawScope.drawHexGridRow(
    style: HexGridStyle,
    address: Long,
    row: HexGridRowBytes,
    firstAddress: String,
    secondAddress: String,
    selectedIndex: Int,
    selectedColumn: Int,
    editing: String
) {
    val grid = style.geometry(size.width.roundToInt())
    val palette = style.palette
    val canvas = drawContext.canvas.nativeCanvas
    val lineHeight = grid.lineHeight.toFloat()
    val addressPaint = style.addressPaint
    addressPaint.color = palette.addressText
    for (line in 0 until row.lineCount) {
        val start = line * COLUMNS
        val top = line * lineHeight
        val y = top + style.baseline
        val addressLabel = if (line == 0) firstAddress else secondAddress
        val cursorOnLine = selectedIndex in start until start + COLUMNS
        val background = when {
            row.available <= start -> palette.placeholderRow
            ((address + start) ushr 3) and 1L == 0L -> palette.rowEven
            else -> palette.rowOdd
        }
        drawRect(background, Offset(0f, top), Size(size.width, lineHeight))
        drawRect(palette.addressBackground, Offset(0f, top), Size(grid.addressWidth, lineHeight))
        drawGridDividers(grid, top, lineHeight, palette.divider)

        addressPaint.textScaleX = fitText(grid.addressWidth, style.addressGlyphWidth * addressLabel.length, style.inset)
        canvas.drawText(addressLabel, grid.addressWidth / 2f, y, addressPaint)
        for (pane in HexGridPane.entries) {
            val isHex = pane == HexGridPane.Hex
            val paint = if (isHex) style.bytePaint else style.asciiPaint
            val cellWidth = grid.cellWidth(pane)
            val labelWidth = if (isHex) style.hexGlyphWidth * 2 else style.asciiGlyphWidth
            paint.textScaleX = fitText(cellWidth, labelWidth, style.inset)
            val inset = minOf(style.inset, cellWidth / 4f)
            for (index in start until minOf(start + COLUMNS, row.expected)) {
                val column = index - start
                val left = grid.cellLeft(pane, column)
                if (index >= row.available) {
                    drawRoundRect(
                        palette.placeholderBlock,
                        Offset(left + inset, top + style.inset),
                        Size(cellWidth - 2 * inset, lineHeight - 2 * style.inset),
                        CornerRadius(inset)
                    )
                    continue
                }
                val selected = index == selectedIndex
                val isEditing = isHex && selected && editing.isNotEmpty()
                if (selected || cursorOnLine || column == selectedColumn) {
                    drawRect(if (selected) palette.selected else palette.highlight, Offset(left, top), Size(cellWidth, lineHeight))
                }
                paint.color = when {
                    isEditing -> palette.editingText
                    selected -> palette.selectedText
                    else -> palette.byteText
                }
                val value = row[index]
                val label = when {
                    isEditing -> editing
                    isHex -> HexLabels[value]
                    else -> AsciiLabels[value]
                }
                canvas.drawText(label, left + cellWidth / 2f, y, paint)
            }
        }
    }
}

private fun fitText(cellWidth: Float, labelWidth: Float, inset: Float): Float =
    ((cellWidth - minOf(inset, cellWidth / 4f)) / labelWidth).coerceAtMost(1f)

private fun DrawScope.drawGridDividers(grid: HexGridGeometry, top: Float, height: Float, color: Color) {
    val size = Size(grid.dividerWidth, height)
    drawRect(color, Offset(grid.addressWidth, top), size)
    drawRect(color, Offset(grid.groupDividerLeft, top), size)
    drawRect(color, Offset(grid.asciiDividerLeft, top), size)
}

internal enum class HexGridPane { Hex, Ascii }

internal data class HexGridHit(val index: Int, val pane: HexGridPane)

/** Pixel geometry used for drawing, hit testing, and popup placement. */
internal class HexGridGeometry(
    val width: Int,
    val lineHeight: Int,
    divider: Float,
    preferredAddressWidth: Float,
    preferredAsciiCellWidth: Float,
    preferredAsciiPadding: Float,
    minHexCellWidth: Float
) {
    val dividerWidth = minOf(divider, width / 4f)
    private val contentWidth = width - 3 * dividerWidth
    private val preferredWidth = preferredAddressWidth + preferredAsciiPadding +
        COLUMNS * (preferredAsciiCellWidth + minHexCellWidth)
    // On narrow displays, preserve all columns and share the width reduction across panes.
    private val widthScale = (contentWidth / preferredWidth).coerceAtMost(1f)
    val addressWidth = preferredAddressWidth * widthScale
    private val asciiCellWidth = preferredAsciiCellWidth * widthScale
    private val asciiPadding = preferredAsciiPadding * widthScale
    private val asciiStart = width - COLUMNS * asciiCellWidth
    val asciiDividerLeft = asciiStart - asciiPadding - dividerWidth
    private val hexStart = addressWidth + dividerWidth
    private val hexCellWidth = (asciiDividerLeft - hexStart - dividerWidth) / COLUMNS
    val groupDividerLeft = hexStart + GROUP_SIZE * hexCellWidth

    fun cellWidth(pane: HexGridPane): Float = if (pane == HexGridPane.Hex) hexCellWidth else asciiCellWidth

    fun cellLeft(pane: HexGridPane, column: Int): Float = when (pane) {
        HexGridPane.Hex -> if (column < GROUP_SIZE) hexStart + column * hexCellWidth
            else groupDividerLeft + dividerWidth + (column - GROUP_SIZE) * hexCellWidth
        HexGridPane.Ascii -> asciiStart + column * asciiCellWidth
    }

    private fun cellRight(pane: HexGridPane, column: Int): Float = when {
        pane == HexGridPane.Hex && column == GROUP_SIZE - 1 -> groupDividerLeft
        pane == HexGridPane.Hex && column == COLUMNS - 1 -> asciiDividerLeft
        column == COLUMNS - 1 -> width.toFloat()
        else -> cellLeft(pane, column + 1)
    }

    fun cellBounds(index: Int, pane: HexGridPane): IntRect {
        val column = index % COLUMNS
        val top = (index / COLUMNS) * lineHeight
        return IntRect(cellLeft(pane, column).roundToInt(), top, cellRight(pane, column).roundToInt(), top + lineHeight)
    }

    fun hitTest(x: Float, y: Float, available: Int): HexGridHit? {
        if (!(x >= 0f && x < width && y >= 0f && y < 2 * lineHeight)) return null
        val pane = when {
            x >= asciiStart -> HexGridPane.Ascii
            x >= hexStart && x < asciiDividerLeft -> HexGridPane.Hex
            else -> return null
        }
        val start = if (y >= lineHeight) COLUMNS else 0
        for (index in start until minOf(start + COLUMNS, available)) {
            val column = index - start
            if (x >= cellLeft(pane, column) && x < cellRight(pane, column)) return HexGridHit(index, pane)
        }
        return null
    }
}

/** Read-only window of the shared chunk, which may not yet contain the whole view extent. */
internal class HexGridRowBytes(private val chunk: ByteArray?, private val offset: Int, byteCount: Int) {
    val expected = byteCount.coerceIn(0, 2 * COLUMNS)
    val available = if (chunk != null && offset in 0 until chunk.size) minOf(expected, chunk.size - offset) else 0
    val lineCount = (expected + COLUMNS - 1) / COLUMNS

    operator fun get(index: Int): Int = chunk!![offset + index].toInt() and 0xff

    fun indexOf(address: Long, target: Long?): Int {
        if (target == null) return -1
        val index = target - address
        return if (index >= 0 && index < available) index.toInt() else -1
    }
}

private val gridAddressFormat = HexFormat {
    upperCase = true
    number.removeLeadingZeros = true
    number.minLength = MIN_ADDRESS_DIGITS
}

internal fun formatHexGridAddress(address: Long): String = address.toHexString(gridAddressFormat)

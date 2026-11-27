package top.wsdx233.r2droid.core.ui.components

/** Address ranges are end-exclusive, just like the mapped sections backing the viewer. */
internal fun scrollbarAddressAtRatio(start: Long, end: Long, ratio: Float): Long {
    val uStart = start.toULong()
    val uEnd = end.toULong()
    if (uEnd <= uStart) return start
    val span = uEnd - 1UL - uStart
    val clamped = if (ratio.isNaN()) 0.0 else ratio.toDouble().coerceIn(0.0, 1.0)
    // Subtract in unsigned space before converting to Double to preserve small ranges at high 64-bit addresses.
    val offset = if (clamped >= 1.0) span else (span.toDouble() * clamped).toULong().coerceAtMost(span)
    return (uStart + offset).toLong()
}

internal fun scrollbarRatioAtPosition(y: Float, trackHeight: Float, thumbHeight: Float, grabOffset: Float): Float {
    val travel = trackHeight - thumbHeight
    return if (travel > 0f) ((y - grabOffset) / travel).coerceIn(0f, 1f) else 0f
}

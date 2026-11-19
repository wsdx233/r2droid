package top.wsdx233.r2droid.core.ui.components

import androidx.compose.ui.graphics.Color
import top.wsdx233.r2droid.core.data.model.RegionKind

/** Shared palette for binary permission maps. */
internal object BinaryPermissionColors {
    operator fun get(kind: RegionKind): Color = when (kind) {
        RegionKind.EXECUTABLE -> Color(0xFF5DADE2)
        RegionKind.WRITABLE -> Color(0xFF58D68D)
        RegionKind.READ_ONLY -> Color(0xFFCACFD2)
        RegionKind.OTHER -> Color(0xFFFACC14)
    }
}

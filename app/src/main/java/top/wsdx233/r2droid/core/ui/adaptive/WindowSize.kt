package top.wsdx233.r2droid.core.ui.adaptive

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

enum class WindowWidthClass { Compact, Medium, Expanded }

val LocalWindowWidthClass = compositionLocalOf { WindowWidthClass.Compact }

@Composable
fun calculateWindowWidthClass(): WindowWidthClass {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    return when {
        widthDp >= 840 -> WindowWidthClass.Expanded
        widthDp >= 600 -> WindowWidthClass.Medium
        else -> WindowWidthClass.Compact
    }
}

@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

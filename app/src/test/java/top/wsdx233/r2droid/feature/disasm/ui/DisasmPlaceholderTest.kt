package top.wsdx233.r2droid.feature.disasm.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.core.data.model.DisasmInstruction
import top.wsdx233.r2droid.ui.theme.R2droidTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DisasmPlaceholderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var hostView: View

    @Test fun rowHeightMatchesDecodedInstruction() = assertRowHeightMatches(1f)

    @Test fun rowHeightMatchesDecodedInstructionWithLargeFonts() = assertRowHeightMatches(1.7f)

    private fun assertRowHeightMatches(fontScale: Float) {
        compose.setContent {
            R2droidTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    Column(Modifier.width(360.dp)) {
                        Box(Modifier.testTag("decoded")) {
                            DisasmRow(
                                instr = DisasmInstruction(0, "nop", "00", "nop", 1, "nop", null),
                                style = rememberDisasmRowStyle(selected = false),
                                onClick = { _, _ -> },
                                onLongClick = { _, _ -> }
                            )
                        }
                        Box(Modifier.testTag("placeholder")) { DisasmPlaceholderRow() }
                    }
                }
            }
        }
        val decoded = compose.onNodeWithTag("decoded").getUnclippedBoundsInRoot()
        val placeholder = compose.onNodeWithTag("placeholder").getUnclippedBoundsInRoot()
        assertEquals(decoded.right - decoded.left, placeholder.right - placeholder.left)
        assertEquals(decoded.bottom - decoded.top, placeholder.bottom - placeholder.top)
    }

    @Test fun wrappedInstructionKeepsGutterAndRowHitAreasSeparateAtFullHeight() {
        var gutterClicks = 0
        var rowClicks = 0
        compose.setContent {
            R2droidTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.width(280.dp).testTag("decoded")) {
                        DisasmRow(
                            instr = DisasmInstruction(16, "mov", "00", "mov", 1,
                                "mov rax, [a_long_symbol_name + a_long_displacement]", null, ptr = 16),
                            style = rememberDisasmRowStyle(selected = false),
                            onGutterClick = { gutterClicks++ },
                            onClick = { _, _ -> rowClicks++ }, onLongClick = { _, _ -> }
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("decoded").performTouchInput {
            click(Offset(8f, height - 2f))
            click(Offset(centerX, height - 2f))
        }
        assertEquals(1, gutterClicks)
        assertEquals(1, rowClicks)
    }

    @Test fun lightThemeHasASubtleMovingSweep() = assertSweep(dark = false)

    @Test fun darkThemeHasASubtleMovingSweep() = assertSweep(dark = true)

    private fun assertSweep(dark: Boolean) {
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            R2droidTheme(darkTheme = dark, dynamicColor = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    val view = LocalView.current
                    SideEffect { hostView = view; compositions++ }
                    DisasmPlaceholderContent(
                        Modifier.width(360.dp).height(480.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("skeleton")
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        val node = compose.onNodeWithTag("skeleton")
        val bounds = node.getUnclippedBoundsInRoot()
        val before = captureSkeleton()
        val compositionCount = compositions
        compose.mainClock.advanceTimeBy(1000)
        val during = captureSkeleton()
        assertTrue("The highlight should sweep across the skeleton", differingPixels(before, during) > 100)
        assertEquals("Sweep must not change the layout", bounds, node.getUnclippedBoundsInRoot())
        assertEquals("Sweep must not recompose the viewer", compositionCount, compositions)

        // The first address bar is right-aligned in the same 26 + 56 dp columns as real rows.
        // Its contrast is deliberately much lower than the old solid grey blocks.
        val pixels = before.toPixelMap()
        val background = pixels[64, 2]
        val bar = pixels[64, 12]
        val contrast = maxOf(
            kotlin.math.abs(bar.red - background.red),
            kotlin.math.abs(bar.green - background.green),
            kotlin.math.abs(bar.blue - background.blue)
        )
        assertTrue("Skeleton should be visible but quiet: $contrast", contrast in 0.015f..0.10f)
        assertEquals("Address placeholder must not fill the gutter", pixels[28, 12], pixels[28, 2])
    }

    @Test fun disabledSweepStaysStatic() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            R2droidTheme(dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                DisasmPlaceholderContent(
                    Modifier.width(360.dp).height(480.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("skeleton"),
                    animate = false
                )
            }
        }
        val before = captureSkeleton()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(0, differingPixels(before, captureSkeleton()))
    }

    @Test fun narrowViewportClipsBarsWithoutOverflow() {
        compose.setContent {
            R2droidTheme(dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                DisasmPlaceholderContent(
                    Modifier.width(100.dp).height(400.dp).testTag("skeleton"),
                    animate = false
                )
            }
        }
        val bounds = compose.onNodeWithTag("skeleton").getUnclippedBoundsInRoot()
        assertEquals(100.dp, bounds.right - bounds.left)
    }

    // Draw directly in Robolectric: window PixelCopy waits for a hardware frame that the
    // paused test looper cannot deliver while the animation clock is controlled manually.
    private fun captureSkeleton(): ImageBitmap {
        val bounds = compose.onNodeWithTag("skeleton").fetchSemanticsNode().boundsInRoot
        return compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(hostView.width, hostView.height, Bitmap.Config.ARGB_8888)
            hostView.draw(Canvas(bitmap))
            Bitmap.createBitmap(
                bitmap, bounds.left.toInt(), bounds.top.toInt(),
                bounds.width.toInt(), bounds.height.toInt()
            ).asImageBitmap()
        }
    }

    private fun differingPixels(first: ImageBitmap, second: ImageBitmap): Int {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        var count = 0
        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                if (a[x, y] != b[x, y]) count++
            }
        }
        return count
    }
}

package top.wsdx233.r2droid.feature.decompiler.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.core.data.model.DecompilationData
import top.wsdx233.r2droid.ui.theme.R2droidTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DecompilerLineNumbersTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lineNumbersStayVisibleAndAlignedAcrossZoomScalesUnderAppTheme() {
        val code = (1..25).joinToString("\n") { idx ->
            "    int var_$idx = helper_$idx(arg0, arg1);"
        }
        var scale by mutableFloatStateOf(0.5f)
        lateinit var hostView: View

        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val view = LocalView.current
                    SideEffect { hostView = view }
                    DecompilationViewerContent(
                        data = DecompilationData(code = code, annotations = emptyList()),
                        cursorAddress = 0L,
                        showLineNumbers = true,
                        wordWrap = false,
                        scale = scale
                    )
                }
            }
        }

        val density = compose.density
        val gutterBgArgb = 0xFF252526.toInt()
        for (targetScale in listOf(0.5f, 0.65f, 1.0f, 2.0f, 3.0f)) {
            compose.runOnIdle { scale = targetScale }
            compose.waitForIdle()

            val codeNode = compose.onNodeWithTag("decompiler_code_text")
            val codeLayout = codeNode.textLayoutResult()
            val codeTopPx = with(density) { codeNode.getUnclippedBoundsInRoot().top.toPx() }
            val gutterBounds = compose.onNodeWithTag("decompiler_line_number_gutter").getBoundsInRoot()
            val frameBitmap = compose.runOnIdle {
                Bitmap.createBitmap(hostView.width, hostView.height, Bitmap.Config.ARGB_8888).also {
                    hostView.draw(Canvas(it))
                }
            }

            for (lineNum in listOf(1, 5, 10, 25)) {
                val lineNode = compose.onNodeWithTag("decompiler_line_number_$lineNum")
                val lineLayout = lineNode.textLayoutResult()
                val lineBounds = lineNode.getUnclippedBoundsInRoot()

                assertEquals("scale=$targetScale line=$lineNum must stay single-line", 1, lineLayout.lineCount)
                assertFalse("scale=$targetScale line=$lineNum must not overflow height", lineLayout.didOverflowHeight)
                assertTrue(
                    "scale=$targetScale line=$lineNum glyphs must fit within measured width",
                    lineLayout.getLineLeft(0) >= -0.5f &&
                        lineLayout.getLineRight(0) <= lineLayout.size.width + 0.5f &&
                        lineLayout.getLineRight(0) <= lineLayout.layoutInput.constraints.maxWidth + 0.5f
                )
                assertTrue(
                    "scale=$targetScale line=$lineNum baseline must sit inside laid-out height",
                    lineLayout.getLineBaseline(0) > 0f &&
                        lineLayout.getLineBaseline(0) < lineLayout.size.height.toFloat()
                )
                assertTrue(
                    "scale=$targetScale line=$lineNum must fit horizontally within gutter",
                    lineBounds.left >= gutterBounds.left && lineBounds.right <= gutterBounds.right
                )

                val expectedTopPx = codeTopPx + codeLayout.getLineTop(lineNum - 1)
                val actualTopPx = with(density) { lineBounds.top.toPx() }
                assertEquals(
                    "scale=$targetScale line=$lineNum top must align with code line",
                    expectedTopPx,
                    actualTopPx,
                    2.0f
                )

                // Verify actual non-background glyph pixels render visibly for on-screen lines
                val rect = lineNode.fetchSemanticsNode().boundsInRoot
                if (rect.height >= 4f && rect.width >= 2f &&
                    rect.bottom <= frameBitmap.height && rect.right <= frameBitmap.width
                ) {
                    val drawnPixels = countNonBackgroundPixels(frameBitmap, rect, gutterBgArgb)
                    assertTrue(
                        "scale=$targetScale line=$lineNum expected visible glyph pixels in gutter, got $drawnPixels",
                        drawnPixels > 0
                    )
                }
            }
        }
    }

    @Test
    fun lineNumbersDoNotWrapOrClipUnderLargeSystemFontScaleAndThreeDigitLineCounts() {
        val code = (1..120).joinToString("\n") { idx -> "x = $idx;" }
        var scale by mutableFloatStateOf(0.5f)
        val highFontScaleDensity = Density(density = 2f, fontScale = 1.8f)

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides highFontScaleDensity) {
                R2droidTheme(darkTheme = true, dynamicColor = false) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        DecompilationViewerContent(
                            data = DecompilationData(code = code, annotations = emptyList()),
                            cursorAddress = 0L,
                            showLineNumbers = true,
                            wordWrap = false,
                            scale = scale
                        )
                    }
                }
            }
        }

        for (targetScale in listOf(0.5f, 1.0f)) {
            compose.runOnIdle { scale = targetScale }
            compose.waitForIdle()

            val gutterBounds = compose.onNodeWithTag("decompiler_line_number_gutter").getUnclippedBoundsInRoot()
            for (lineNum in listOf(1, 99, 120)) {
                val lineNode = compose.onNodeWithTag("decompiler_line_number_$lineNum")
                val lineLayout = lineNode.textLayoutResult()
                val lineBounds = lineNode.getUnclippedBoundsInRoot()

                assertEquals("scale=$targetScale line=$lineNum single line", 1, lineLayout.lineCount)
                assertFalse("scale=$targetScale line=$lineNum height overflow", lineLayout.didOverflowHeight)
                assertTrue(
                    "scale=$targetScale line=$lineNum glyphs must fit within width constraints",
                    lineLayout.getLineLeft(0) >= -0.5f &&
                        lineLayout.getLineRight(0) <= lineLayout.size.width + 0.5f &&
                        lineLayout.getLineRight(0) <= lineLayout.layoutInput.constraints.maxWidth + 0.5f
                )
                assertTrue(
                    "scale=$targetScale line=$lineNum inside gutter horizontally",
                    lineBounds.left >= gutterBounds.left && lineBounds.right <= gutterBounds.right
                )
            }
        }
    }

    @Test
    fun wrappedLinesKeepLineNumbersTopAlignedWithFirstVisualLine() {
        val longLine = "    result = " + (1..24).joinToString(" + ") { "very_long_operand_$it" } + ";"
        val code = listOf(
            "int demo(void) {",
            longLine,
            "    return result;",
            "}"
        ).joinToString("\n")
        var scale by mutableFloatStateOf(0.5f)
        var wordWrap by mutableStateOf(true)

        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DecompilationViewerContent(
                        data = DecompilationData(code = code, annotations = emptyList()),
                        cursorAddress = 0L,
                        showLineNumbers = true,
                        wordWrap = wordWrap,
                        scale = scale
                    )
                }
            }
        }

        val density = compose.density
        val rawStarts = mutableListOf(0).apply {
            for (i in code.indices) if (code[i] == '\n') add(i + 1)
        }

        for (targetScale in listOf(0.5f, 1.0f)) {
            compose.runOnIdle { scale = targetScale }
            compose.waitForIdle()

            val codeNode = compose.onNodeWithTag("decompiler_code_text")
            val codeLayout = codeNode.textLayoutResult()
            assertTrue(
                "Expected wrapped code to span more than 4 visual lines at scale=$targetScale",
                codeLayout.lineCount > 4
            )
            val codeTopPx = with(density) { codeNode.getUnclippedBoundsInRoot().top.toPx() }

            for (i in rawStarts.indices) {
                val lineNum = i + 1
                val vLine = codeLayout.getLineForOffset(rawStarts[i])
                val expectedTopPx = codeTopPx + codeLayout.getLineTop(vLine)
                val lineNode = compose.onNodeWithTag("decompiler_line_number_$lineNum")
                val lineLayout = lineNode.textLayoutResult()
                val actualTopPx = with(density) { lineNode.getUnclippedBoundsInRoot().top.toPx() }

                assertEquals(1, lineLayout.lineCount)
                assertFalse(lineLayout.didOverflowHeight)
                assertEquals(
                    "scale=$targetScale wrapped line=$lineNum must align with first visual line",
                    expectedTopPx,
                    actualTopPx,
                    2.0f
                )
            }
        }
    }

    @Test
    fun trailingNewlineDoesNotCollapsePenultimateLineHeight() {
        val code = "int a = 1;\nint b = 2;\n"

        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DecompilationViewerContent(
                        data = DecompilationData(code = code, annotations = emptyList()),
                        cursorAddress = 0L,
                        showLineNumbers = true,
                        wordWrap = true,
                        scale = 0.5f
                    )
                }
            }
        }

        compose.waitForIdle()
        val line1Bounds = compose.onNodeWithTag("decompiler_line_number_1").getUnclippedBoundsInRoot()
        val line2Bounds = compose.onNodeWithTag("decompiler_line_number_2").getUnclippedBoundsInRoot()
        val line3Bounds = compose.onNodeWithTag("decompiler_line_number_3").getUnclippedBoundsInRoot()

        assertTrue("Line 2 must appear below Line 1", line2Bounds.top > line1Bounds.top)
        assertTrue("Line 3 must appear below Line 2", line3Bounds.top > line2Bounds.top)
        for (lineNum in 1..3) {
            val layout = compose.onNodeWithTag("decompiler_line_number_$lineNum").textLayoutResult()
            assertFalse("Line $lineNum must not overflow height", layout.didOverflowHeight)
            assertTrue("Line $lineNum must have positive height", layout.size.height > 0)
        }
    }

    private fun countNonBackgroundPixels(
        bitmap: Bitmap,
        rect: androidx.compose.ui.geometry.Rect,
        backgroundArgb: Int
    ): Int {
        val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = rect.right.toInt().coerceIn(left, bitmap.width)
        val bottom = rect.bottom.toInt().coerceIn(top, bitmap.height)
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                if (bitmap.getPixel(x, y) != backgroundArgb) count++
            }
        }
        return count
    }

    private fun SemanticsNodeInteraction.textLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            action(results)
        }
        assertTrue("Expected TextLayoutResult from semantics action", results.isNotEmpty())
        return results.first()
    }
}

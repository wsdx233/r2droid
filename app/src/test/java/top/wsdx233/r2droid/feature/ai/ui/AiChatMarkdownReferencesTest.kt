package top.wsdx233.r2droid.feature.ai.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AiChatMarkdownReferencesTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tapsDispatchExactTokensFromInlineMarkdownAndTableCells() {
        val calls = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                AiMarkdown(
                    text = "# **main**\n> Check sym._start_\nSee `0X10 sym.read` next\n" +
                        "| Section | Target |\n| --- | --- |\n| *entry0* | 0x22 |",
                    onReferenceClick = { calls += it }
                )
            }
        }

        tapText("main", "main")
        tapText("Check sym._start_", "sym._start_")
        tapText("0X10 sym.read", "sym.read")
        tapText("0X10 sym.read", "0X10")
        tapText("entry0", "entry0")
        tapText("0x22", "0x22")
        compose.runOnIdle {
            assertEquals(listOf("main", "sym._start_", "sym.read", "0X10", "entry0", "0x22"), calls)
        }
    }

    @Test
    fun uriLabelsWinOverReferencesAlongsideAChip() {
        val calls = mutableListOf<String>()
        val uris = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) { uris += uri }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                MaterialTheme {
                    AiMarkdown(
                        text = "[0x42](https://example.test/0x42) and `0x43 0x44`\n" +
                            "[main](mailto:help@example.test)\nhttps://example.test/0x55",
                        onReferenceClick = { calls += it }
                    )
                }
            }
        }

        tapText("0x42 and 0x43 0x44", "0x42")
        tapText("0x43 0x44", "0x44")
        tapText("main", "main")
        tapText("https://example.test/0x55", "0x55")
        compose.runOnIdle {
            assertEquals(listOf("https://example.test/0x42", "mailto:help@example.test"), uris)
            assertEquals(listOf("0x44"), calls)
        }
    }

    @Test
    fun streamingTailAndOpenFenceWaitWithoutDisablingCompletedPrefixOrCopy() {
        val message = mutableStateOf("Stable main\nTail 0x1")
        val streaming = mutableStateOf(true)
        val calls = mutableListOf<String>()
        val copies = mutableListOf<String>()
        val code = "  pd @ 0x123   "
        val openFence = "Stable main\nTail 0x12\n```r2\n$code"
        val copyLabel = ApplicationProvider.getApplicationContext<Application>().getString(R.string.ai_copy)
        compose.setContent {
            MaterialTheme {
                AiMarkdown(
                    text = message.value,
                    streaming = streaming.value,
                    onCopy = { copies += it },
                    onReferenceClick = { calls += it }
                )
            }
        }

        tapText("Stable main", "main")
        tapText("Tail 0x1", "0x1")
        compose.runOnIdle { message.value = openFence }
        tapText("Tail 0x12", "0x12")
        tapText(code, "0x123")
        compose.onNodeWithContentDescription(copyLabel).performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("main", "0x12"), calls)
            assertEquals(listOf(code), copies)
            message.value = "$openFence\n```"
        }
        tapText(code, "0x123") // A closing marker on an unfinished line is still provisional.
        compose.runOnIdle {
            assertEquals(listOf("main", "0x12"), calls)
            message.value = "$openFence\n```\nFinal 0x88"
        }
        tapText(code, "0x123") // Completed fence is now linked, even while streaming.
        tapText("Final 0x88", "0x88")
        compose.runOnIdle {
            assertEquals(listOf("main", "0x12", "0x123"), calls)
            streaming.value = false
        }
        tapText("Final 0x88", "0x88")
        compose.runOnIdle { assertEquals(listOf("main", "0x12", "0x123", "0x88"), calls) }
    }

    @Test
    fun denseFenceUsesOneTextNodeAndDistinguishesTaps() {
        val calls = mutableListOf<String>()
        val code = "0x2a / 0x3b\n" + List(127) { "0x77" }.joinToString("\n")
        compose.setContent {
            MaterialTheme {
                AiMarkdown(text = "```asm\n$code\n```", onReferenceClick = { calls += it })
            }
        }

        val node = compose.onNodeWithText(code, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(node.config[SemanticsProperties.Text].single().getLinkAnnotations(0, code.length).isEmpty())
        tapText(code, "0x2a")
        tapText(code, "/")
        tapText(code, "0x3b")
        compose.runOnIdle { assertEquals(listOf("0x2a", "0x3b"), calls) }
    }

    private fun tapText(displayed: String, target: String) {
        val node = compose.onNodeWithText(displayed, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout -> getLayout(layouts) }
        val index = displayed.indexOf(target)
        check(index >= 0)
        val center = layouts.single().getBoundingBox(index + target.length / 2).center
        node.performTouchInput { click(center) }
    }
}

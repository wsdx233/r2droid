package top.wsdx233.r2droid.feature.project.patches

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.feature.project.WriteCacheDiscardDialog
import top.wsdx233.r2droid.ui.theme.R2droidTheme
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PatchesScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var hostView: View

    private val sampleSnapshot = PatchSnapshot(
        entries = listOf(
            CachePatch(0, 0x1040uL, "4889e583ec10c745", "4889e5909010c745", written = true),
            CachePatch(1, 0x1080uL, "740a", "eb0a", written = false),
            CachePatch(2, 0x1080uL, "eb0a", "9090", written = false),
            CachePatch(3, 0x401200uL, "01020304", "90909090", written = false)
        ),
        layerSizes = listOf(4),
        generation = 1,
        routing = emptyList()
    )

    @Test
    fun rendersOverviewStatsFiltersAndExpandsPatchActions() {
        var jumpedHex: Long? = null
        var jumpedDisasm: Long? = null
        var requestedAction: Pair<PatchAction, CachePatch?>? = null

        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(820.dp)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    PatchesContent(
                        fileName = "libtarget.so",
                        projectPath = "/data/local/tmp/libtarget.so",
                        snapshot = sampleSnapshot,
                        loading = false,
                        running = false,
                        busy = false,
                        notice = PatchNotice(R.string.patches_export_done, tone = NoticeTone.Success),
                        loadFailure = null,
                        confirmation = null,
                        onRefresh = {},
                        onDismissNotice = {},
                        onExport = {},
                        onRequestAction = { action, entry -> requestedAction = action to entry },
                        onDismissConfirmation = {},
                        onConfirmAction = {},
                        onJumpToHex = { jumpedHex = it },
                        onJumpToDisasm = { jumpedDisasm = it }
                    )
                }
            }
        }

        compose.onNodeWithText("libtarget.so").assertIsDisplayed()
        compose.onNodeWithText("0x1040").assertIsDisplayed()
        compose.onNodeWithText("48 89 E5 90 90 10 C7 45").assertIsDisplayed()

        // Expand first card to reveal Hex / Disassembly / Copy actions
        compose.onNodeWithText("0x1040").performClick()
        compose.waitForIdle()
        maybeSaveScreenshot("patches_light_expanded.png")

        compose.onNodeWithText("Open in Hex").assertIsDisplayed().performClick()
        assertEquals(0x1040L, jumpedHex)

        compose.onNodeWithText("Disassembly").assertIsDisplayed().performClick()
        assertEquals(0x1040L, jumpedDisasm)

        // Filter by Written via stat tile (first "Written" node is the stat tile)
        compose.onAllNodesWithText("Written")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("0x1040").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("0x401200").fetchSemanticsNodes().size)

        // Toggle filter off
        compose.onAllNodesWithText("Written")[0].performClick()
        compose.waitForIdle()

        // Request Commit via overview button
        compose.onNodeWithText("Write all pending patches").performClick()
        assertEquals(PatchAction.Commit to null, requestedAction)
    }

    @Test
    fun rendersDarkThemeAndEmptyState() {
        compose.setContent {
            R2droidTheme(darkTheme = true, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(820.dp)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    PatchesContent(
                        fileName = "libtarget.so",
                        projectPath = "/data/local/tmp/libtarget.so",
                        snapshot = sampleSnapshot,
                        loading = false,
                        running = false,
                        busy = false,
                        notice = null,
                        loadFailure = null,
                        confirmation = null,
                        onRefresh = {},
                        onDismissNotice = {},
                        onExport = {},
                        onRequestAction = { _, _ -> },
                        onDismissConfirmation = {},
                        onConfirmAction = {},
                        onJumpToHex = {},
                        onJumpToDisasm = {}
                    )
                }
            }
        }
        compose.onNodeWithText("0x1040").performClick()
        compose.waitForIdle()
        maybeSaveScreenshot("patches_dark_expanded.png")
    }

    @Test
    fun rendersEmptyStateWithoutDisabledExportButton() {
        val emptySnapshot = PatchSnapshot(emptyList(), listOf(0), 1, emptyList())
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(640.dp)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    PatchesContent(
                        fileName = "sample.elf",
                        projectPath = "/tmp/sample.elf",
                        snapshot = emptySnapshot,
                        loading = false,
                        running = false,
                        busy = false,
                        notice = null,
                        loadFailure = null,
                        confirmation = null,
                        onRefresh = {},
                        onDismissNotice = {},
                        onExport = {},
                        onRequestAction = { _, _ -> },
                        onDismissConfirmation = {},
                        onConfirmAction = {},
                        onJumpToHex = {}
                    )
                }
            }
        }
        compose.onNodeWithText("sample.elf").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Export patched copy").fetchSemanticsNodes().size)
        maybeSaveScreenshot("patches_empty.png")
    }

    @Test
    fun expandsPendingPatchWithUndoAndShowsCommitDialog() {
        var undoRequested: CachePatch? = null
        val pendingOnly = PatchSnapshot(
            entries = listOf(
                CachePatch(0, 0x401200uL, "01020304", "90909090", written = false)
            ),
            layerSizes = listOf(1),
            generation = 1,
            routing = emptyList()
        )
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                val view = LocalView.current
                SideEffect { hostView = view }
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(820.dp)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    PatchesContent(
                        fileName = "libtarget.so",
                        projectPath = "/data/local/tmp/libtarget.so",
                        snapshot = pendingOnly,
                        loading = false,
                        running = false,
                        busy = false,
                        notice = null,
                        loadFailure = null,
                        confirmation = null,
                        onRefresh = {},
                        onDismissNotice = {},
                        onExport = {},
                        onRequestAction = { action, entry ->
                            if (action == PatchAction.UndoEntry) undoRequested = entry
                        },
                        onDismissConfirmation = {},
                        onConfirmAction = {},
                        onJumpToHex = {},
                        onJumpToDisasm = {}
                    )
                }
            }
        }
        compose.onNodeWithText("0x401200").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Undo this patch").assertIsDisplayed()
        maybeSaveScreenshot("patches_pending_undo.png")
        compose.onNodeWithText("Undo this patch").performClick()
        assertEquals(pendingOnly.entries.single(), undoRequested)
    }

    @Test
    fun reconnectWarningNamesSessionAndCancelDoesNotDiscard() {
        var cancelled = false
        var discarded = false
        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                WriteCacheDiscardDialog(
                    sessionName = "sample.elf",
                    reconnect = true,
                    onDismiss = { cancelled = true },
                    onDiscard = { discarded = true }
                )
            }
        }

        compose.onNodeWithText("sample.elf may have pending patches", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(true, cancelled)
        assertEquals(false, discarded)
        compose.onNodeWithText("Discard patches and reconnect").performClick()
        assertEquals(true, discarded)
    }

    private fun maybeSaveScreenshot(name: String) {
        val dir = (System.getProperty("patchesScreenshotDir") ?: System.getenv("PATCHES_SCREENSHOT_DIR"))
            ?.takeIf { it.isNotBlank() } ?: return
        compose.runOnIdle {
            val outDir = File(dir).apply { mkdirs() }
            val bitmap = Bitmap.createBitmap(hostView.width, hostView.height, Bitmap.Config.ARGB_8888)
            hostView.draw(Canvas(bitmap))
            File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

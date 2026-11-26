package top.wsdx233.r2droid.feature.home

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.core.data.model.SavedProject
import top.wsdx233.r2droid.ui.theme.R2droidTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HomeScreenCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun savedProjectCardReplacesPlayButtonWithCircularLoaderWhileRestoring() {
        val project = SavedProject(
            id = "proj-1",
            name = "sample.so",
            binaryPath = "/tmp/sample.so",
            scriptPath = "/tmp/project.r2",
            createdAt = 1700000000000L,
            lastModified = 1700000000000L,
            fileSize = 2048L,
            archType = "arm",
            binType = "elf"
        )
        var isRestoring by mutableStateOf(false)
        var restoreClicks = 0
        var deleteClicks = 0

        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                SavedProjectCard(
                    project = project,
                    isBinaryAccessible = true,
                    isRestoring = isRestoring,
                    interactionEnabled = !isRestoring,
                    onRestore = {
                        restoreClicks++
                        isRestoring = true
                    },
                    onDelete = { deleteClicks++ }
                )
            }
        }

        compose.onNodeWithContentDescription("Restore Project").assertIsDisplayed().assertIsEnabled()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()

        compose.onNodeWithContentDescription("Restore Project").performClick()

        compose.runOnIdle { assertEquals(1, restoreClicks) }
        compose.onNodeWithContentDescription("Restore Project").assertDoesNotExist()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete Project").assertIsNotEnabled()

        // Tapping the card title while restoring must not trigger a second restore.
        compose.onNodeWithText("sample.so").performClick()
        compose.runOnIdle {
            assertEquals(1, restoreClicks)
            assertEquals(0, deleteClicks)
        }
    }

    @Test
    fun homeActionButtonShowsCircularLoaderAndDisablesClickWhileLoading() {
        var loading by mutableStateOf(false)
        var clicks = 0

        compose.setContent {
            R2droidTheme(darkTheme = false, dynamicColor = false) {
                HomeActionButton(
                    title = "Open File",
                    description = "Pick a binary",
                    icon = Icons.Default.FolderOpen,
                    isLoading = loading,
                    onClick = {
                        clicks++
                        loading = true
                    }
                )
            }
        }

        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        compose.onNodeWithText("Open File").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()

        compose.onNodeWithText("Open File").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}

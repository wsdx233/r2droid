package top.wsdx233.r2droid.feature.terminal.ui

import android.app.Application
import android.view.WindowManager.LayoutParams
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w390dp-h844dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class CommandScreenImeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun commandScreenUsesAdjustResizeWhileActiveAndRestoresOriginalSoftInputMode() {
        val initialMode = LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or LayoutParams.SOFT_INPUT_ADJUST_PAN
        @Suppress("DEPRECATION")
        val expectedActiveMode = LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        compose.runOnUiThread {
            compose.activity.window.setSoftInputMode(initialMode)
        }

        var showCommandScreen by mutableStateOf(true)
        var command by mutableStateOf("")
        var history by mutableStateOf(listOf("iI" to "arch arm"))

        compose.setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        Box(Modifier.fillMaxWidth().height(64.dp).testTag("topBar")) {
                            Text("TopBar")
                        }
                    },
                    bottomBar = {
                        Box(Modifier.fillMaxWidth().height(128.dp).testTag("bottomBar")) {
                            Text("BottomBar")
                        }
                    }
                ) { paddingValues ->
                    Row(
                        modifier = Modifier
                            .padding(paddingValues)
                            .consumeWindowInsets(paddingValues)
                            .fillMaxSize()
                    ) {
                        if (showCommandScreen) {
                            CommandScreen(
                                command = command,
                                onCommandChange = { command = it },
                                commandHistory = history,
                                onCommandHistoryChange = { history = it }
                            )
                        }
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("topBar").assertIsDisplayed()
        assertEquals(expectedActiveMode, compose.activity.window.attributes.softInputMode)

        compose.runOnIdle { showCommandScreen = false }
        compose.waitForIdle()
        assertEquals(initialMode, compose.activity.window.attributes.softInputMode)
    }
}

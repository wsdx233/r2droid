package top.wsdx233.r2droid.core.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AutoHideAddressScrollbarTest {
    @get:Rule val compose = createComposeRule()

    private fun showScrollbar(
        previews: MutableList<Long>,
        commits: MutableList<Long>,
        dragStates: MutableList<Boolean> = mutableListOf()
    ) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.height(300.dp)) {
                    AutoHideAddressScrollbar(
                        listState = rememberLazyListState(), totalItems = 100,
                        viewStartAddress = 100, viewEndAddress = 10000, currentAddress = { 100 },
                        modifier = Modifier.testTag("scrollbar"), alwaysShow = true,
                        onScrollToAddress = { previews.add(it) },
                        onDragComplete = { commits.add(it) },
                        onDragStateChange = { dragStates.add(it) }
                    )
                }
            }
        }
    }

    @Test fun draggingOnlyPreviewsAndCommitsTheLatestAddressOnceOnRelease() {
        val previews = mutableListOf<Long>()
        val commits = mutableListOf<Long>()
        val states = mutableListOf<Boolean>()
        showScrollbar(previews, commits, states)
        val bar = compose.onNodeWithTag("scrollbar")
        bar.performTouchInput {
            down(Offset(centerX, height * .2f))
            moveTo(Offset(centerX, height * .8f))
            moveTo(Offset(centerX, height * .4f))
        }
        compose.runOnIdle {
            assertTrue(previews.size >= 2)
            assertTrue(commits.isEmpty())
            assertEquals(listOf(true), states)
        }
        bar.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(listOf(previews.last()), commits)
            assertEquals(listOf(true, false), states)
        }
    }

    @Test fun tapCommitsOnlyOnceAndNeverTargetsTheExclusiveEnd() {
        val previews = mutableListOf<Long>()
        val commits = mutableListOf<Long>()
        showScrollbar(previews, commits)
        compose.onNodeWithTag("scrollbar").performTouchInput { click(Offset(centerX, height - 1f)) }
        compose.runOnIdle {
            assertEquals(listOf(9999L), commits)
            assertEquals(1, previews.size)
        }
    }

    @Test fun cancelledGestureDoesNotCommitAndClearsDraggingState() {
        val previews = mutableListOf<Long>()
        val commits = mutableListOf<Long>()
        val states = mutableListOf<Boolean>()
        showScrollbar(previews, commits, states)
        compose.onNodeWithTag("scrollbar").performTouchInput {
            down(center)
            moveTo(Offset(centerX, height * .8f))
            cancel()
        }
        compose.runOnIdle {
            assertTrue(previews.isNotEmpty())
            assertTrue(commits.isEmpty())
            assertEquals(listOf(true, false), states)
        }
    }

    @Test fun callbackCanChangeDuringGestureWithoutRestartingTheDetector() {
        val version = mutableIntStateOf(1)
        val calls = mutableListOf<Int>()
        compose.setContent {
            val currentVersion = version.intValue
            MaterialTheme {
                Box(Modifier.height(300.dp)) {
                    AutoHideAddressScrollbar(
                        listState = rememberLazyListState(), totalItems = 100,
                        viewStartAddress = 100, viewEndAddress = 10000, currentAddress = { 100 },
                        modifier = Modifier.testTag("scrollbar"), alwaysShow = true,
                        onDragComplete = { calls.add(currentVersion) }
                    )
                }
            }
        }
        val bar = compose.onNodeWithTag("scrollbar")
        bar.performTouchInput { down(center) }
        compose.runOnIdle { version.intValue = 2 }
        bar.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(2), calls) }
    }
}

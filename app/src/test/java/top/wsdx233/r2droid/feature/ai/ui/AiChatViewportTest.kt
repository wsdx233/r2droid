package top.wsdx233.r2droid.feature.ai.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import top.wsdx233.r2droid.feature.ai.data.ChatMessage
import top.wsdx233.r2droid.feature.ai.data.ChatRole

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w390dp-h844dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class AiChatViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun readingRestoresMessageAndOffsetAcrossTabAndHistorySwitches() {
        val host = showChats()
        scrollUpToRead(index = 4)
        val firstChatOffset = top("first-4")
        assertTrue(firstChatOffset < top(Feed))

        compose.runOnIdle { host.showChat = false }
        compose.onNodeWithTag(Feed).assertDoesNotExist()
        compose.runOnIdle {
            // Move the saved message to a new index and append an incoming reply while away.
            host.first = listOf(message("prepended")) + host.first + reply("first-reply", height = 90)
            host.showChat = true
        }
        assertEquals(firstChatOffset, top("first-4"))
        compose.onNodeWithTag("first-reply").assertDoesNotExist()

        compose.runOnIdle { host.chatId = "second" }
        assertAtTail("second-19")
        scrollUpToRead(index = 8)
        val secondChatOffset = top("second-8")
        assertTrue(secondChatOffset < top(Feed))
        compose.runOnIdle { host.chatId = "first" }
        assertEquals(firstChatOffset, top("first-4"))
        compose.runOnIdle { host.chatId = "second" }
        assertEquals(secondChatOffset, top("second-8"))
    }

    @Test
    fun followingRestoresTheNewTailAndFollowsGrowthAfterReturn() {
        val host = showChats()
        assertAtTail("first-19")

        compose.runOnIdle { host.showChat = false }
        compose.onNodeWithTag(Feed).assertDoesNotExist()
        compose.runOnIdle {
            host.first = host.first + reply("new-reply", height = 580)
            host.showChat = true
        }
        assertAtTail("new-reply")

        compose.runOnIdle {
            host.first = host.first.dropLast(1) + reply("new-reply", height = 820)
        }
        assertAtTail("new-reply")
    }

    @Test
    fun emptyMountKeepsTheAnchorAndRemovedMessageFallsBackToItsOldSlot() {
        val host = showChats()
        scrollUpToRead(index = 4)
        val offset = top("first-4")
        val original = host.first

        compose.runOnIdle { host.showChat = false }
        compose.onNodeWithTag(Feed).assertDoesNotExist()
        compose.runOnIdle {
            host.first = emptyList()
            host.showChat = true
        }
        compose.onNodeWithTag("first-4").assertDoesNotExist()
        compose.runOnIdle { host.showChat = false }
        compose.onNodeWithTag(Feed).assertDoesNotExist()
        compose.runOnIdle { host.showChat = true }
        compose.onNodeWithTag("first-4").assertDoesNotExist()
        compose.runOnIdle { host.first = original }
        assertEquals(offset, top("first-4"))

        compose.runOnIdle { host.showChat = false }
        compose.onNodeWithTag(Feed).assertDoesNotExist()
        compose.runOnIdle {
            host.first = original.filterNot { it.id == "first-4" }
            host.showChat = true
        }
        assertEquals(top(Feed) + 8.dp, top("first-5"))
    }

    private fun showChats(): Host {
        val host = Host()
        val memory = ChatScreenMemory()
        compose.setContent {
            MaterialTheme {
                if (host.showChat) {
                    val viewport = memory.viewportFor(host.chatId)
                    key(viewport) {
                        ChatFeed(
                            messages = if (host.chatId == "first") host.first else host.second,
                            viewport = viewport,
                            bottomPadding = 10.dp,
                            onScrolledChanged = {},
                            modifier = Modifier.testTag(Feed).height(320.dp)
                        ) { message, modifier ->
                            Box(modifier.fillMaxWidth().height(message.content.toInt().dp).testTag(message.id))
                        }
                    }
                }
            }
        }
        return host
    }

    private fun scrollUpToRead(index: Int) {
        // Use a real upward-in-history gesture to disengage tail following, then fix the
        // exact reading position without depending on touch slop or fling distance.
        compose.onNodeWithTag(Feed).performTouchInput {
            swipe(center, center + Offset(0f, 100.dp.toPx()), durationMillis = 1000L)
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToIndex(index)
            .performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                scrollBy(0f, with(compose.density) { 30.dp.toPx() })
            }
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    private fun assertAtTail(lastId: String) {
        val feed = compose.onNodeWithTag(Feed).getUnclippedBoundsInRoot()
        val last = compose.onNodeWithTag(lastId).getUnclippedBoundsInRoot()
        // The final row ends above 10 dp of bottom padding, 18 dp of spacing and the 1 dp footer.
        assertEquals((feed.bottom - 29.dp).value, last.bottom.value, 0.5f)
    }

    private class Host {
        var showChat by mutableStateOf(true)
        var chatId by mutableStateOf("first")
        var first by mutableStateOf(List(20) { message("first-$it") })
        val second = List(20) { message("second-$it") }
    }

    private companion object {
        const val Feed = "chat-feed"

        fun message(id: String, height: Int = 56) = ChatMessage(
            id = id,
            role = ChatRole.User,
            content = height.toString()
        )

        fun reply(id: String, height: Int) = message(id, height).copy(role = ChatRole.Assistant)
    }
}

package io.rikka.agent.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.ui.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatBubbleActionsTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Before
  fun setUp() {
    stopKoin()
  }

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun `assistant plain output can expand truncated code card`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val longOutput = (1..20).joinToString("\n") { "line-$it" }

    composeRule.setContent {
      MaterialTheme {
        ChatBubble(
          message = ChatMessage(
            id = "assistant-1",
            role = ChatRole.Assistant,
            content = longOutput,
            timestampMs = 1L,
            status = MessageStatus.Final,
          ),
        )
      }
    }

    composeRule.onNodeWithText(context.getString(R.string.show_all_lines, 20))
      .assertIsDisplayed()
      .performClick()

    composeRule.onNodeWithText("line-20", substring = true).assertIsDisplayed()
    composeRule.onNodeWithText(context.getString(R.string.collapse_lines, 20)).assertIsDisplayed()
  }

  @Test
  fun `assistant action row exposes expand and share full callbacks`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    var expanded = false
    var sharedFull = false

    composeRule.setContent {
      MaterialTheme {
        ChatBubble(
          message = ChatMessage(
            id = "assistant-2",
            role = ChatRole.Assistant,
            content = "tail output",
            timestampMs = 1L,
            status = MessageStatus.Final,
          ),
          showExpand = true,
          onExpand = { expanded = true },
          onShareFull = { sharedFull = true },
        )
      }
    }

    composeRule.onNodeWithText(context.getString(R.string.expand_output))
      .assertIsDisplayed()
      .performClick()
    composeRule.onNodeWithContentDescription(context.getString(R.string.cd_share_full))
      .assertIsDisplayed()
      .performClick()

    assertTrue(expanded)
    assertTrue(sharedFull)
  }

  @Test
  fun `user action row rerun button replays command`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    var rerunCommand: String? = null

    composeRule.setContent {
      MaterialTheme {
        ChatBubble(
          message = ChatMessage(
            id = "user-1",
            role = ChatRole.User,
            content = "ls -la",
            timestampMs = 1L,
            status = MessageStatus.Final,
          ),
          onRerun = { rerunCommand = it },
        )
      }
    }

    composeRule.onNodeWithContentDescription(context.getString(R.string.cd_rerun))
      .assertIsDisplayed()
      .performClick()

    assertEquals("ls -la", rerunCommand)
  }
}

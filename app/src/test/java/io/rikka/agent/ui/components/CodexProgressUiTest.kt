package io.rikka.agent.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessageStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CodexProgressUiTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `codex progress markdown renders list items`() {
    val markdown = buildString {
      appendLine("- Thread: plan parsing")
      appendLine("- Turn: run diagnostics")
      appendLine("- Item: ssh handshake")
      appendLine()
      appendLine("Output starts here")
    }

    composeRule.setContent {
      MaterialTheme {
        ChatBubble(
          message = ChatMessage(
            id = "assistant-1",
            role = ChatRole.Assistant,
            content = markdown,
            timestampMs = 1L,
            status = MessageStatus.Final,
          ),
        )
      }
    }

    composeRule.onNodeWithText("Thread: plan parsing").assertIsDisplayed()
    composeRule.onNodeWithText("Turn: run diagnostics").assertIsDisplayed()
    composeRule.onNodeWithText("Item: ssh handshake").assertIsDisplayed()
    composeRule.onNodeWithText("Output starts here").assertIsDisplayed()
  }
}

package io.rikka.agent.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [MessagePartsBlock].
 *
 * Each test constructs a [ChatMessage] with specific [MessagePart] subtypes and
 * verifies that the correct child composables are rendered with expected text content.
 *
 * Requires:
 * - `testImplementation(libs.robolectric)`
 * - `testImplementation("androidx.compose.ui:ui-test-junit4")`
 * - `testImplementation("androidx.compose.ui:ui-test-manifest")`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], instrumentedPackages = ["androidx.loader.content"])
class MessagePartsBlockTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper ───────────────────────────────────────────────────────────────

    private fun messageWith(vararg parts: MessagePart): ChatMessage = ChatMessage(
        id = "test-msg",
        role = ChatRole.Assistant,
        parts = parts.toList(),
        timestampMs = 1700000000000,
        status = MessageStatus.Final,
    )

    // ── 1. TextPart rendering ────────────────────────────────────────────────

    @Test
    fun `TextPart renders plain text content`() {
        val message = messageWith(MessagePart.Text("Hello, world!"))

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("Hello, world!").assertIsDisplayed()
    }

    @Test
    fun `empty TextPart renders nothing`() {
        val message = messageWith(MessagePart.Text(""))

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        // Empty text should not produce any visible node with empty string.
        // The TextPartRenderer early-returns on empty text.
        composeTestRule.onNode(hasText(""), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `TextPart with markdown-like content renders`() {
        val message = messageWith(MessagePart.Text("## Heading\n\nSome **bold** text"))

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        // The text should be rendered (either via MarkdownText or plain Text).
        // We verify the raw text content is present somewhere in the tree.
        composeTestRule.onNode(
            hasText("Heading", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    // ── 2. CodePart rendering (with language label) ─────────────────────────

    @Test
    fun `CodePart renders code content`() {
        val message = messageWith(
            MessagePart.Code(code = "println(\"hello\")", language = "kotlin"),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("println(\"hello\")").assertIsDisplayed()
    }

    @Test
    fun `CodePart with language shows language label`() {
        val message = messageWith(
            MessagePart.Code(code = "val x = 1", language = "kotlin"),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("kotlin").assertIsDisplayed()
    }

    @Test
    fun `CodePart without language shows fallback label`() {
        val message = messageWith(
            MessagePart.Code(code = "echo hi", language = null),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("echo hi").assertIsDisplayed()
        // When language is null, CodeCard falls back to R.string.label_output ("output")
        composeTestRule.onNodeWithText("output").assertIsDisplayed()
    }

    // ── 3. CommandPart rendering (with exit code) ────────────────────────────

    @Test
    fun `CommandPart renders command text`() {
        val message = messageWith(
            MessagePart.Command(command = "ls -la", exitCode = 0),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("ls -la").assertIsDisplayed()
    }

    @Test
    fun `CommandPart with exit code 0 shows success badge`() {
        val message = messageWith(
            MessagePart.Command(command = "whoami", exitCode = 0),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("exit 0").assertIsDisplayed()
    }

    @Test
    fun `CommandPart with non-zero exit code shows failure badge`() {
        val message = messageWith(
            MessagePart.Command(command = "cat /nonexistent", exitCode = 1),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("exit 1").assertIsDisplayed()
    }

    @Test
    fun `CommandPart with null exit code shows running indicator`() {
        val message = messageWith(
            MessagePart.Command(command = "sleep 100", exitCode = null),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        // Running commands show "..." in the exit code badge
        composeTestRule.onNodeWithText("...").assertIsDisplayed()
    }

    @Test
    fun `CommandPart shows dollar prompt`() {
        val message = messageWith(
            MessagePart.Command(command = "uname -a", exitCode = 0),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("$").assertIsDisplayed()
    }

    // ── 4. StdoutPart / StderrPart rendering ─────────────────────────────────

    @Test
    fun `StdoutPart renders output text`() {
        val message = messageWith(
            MessagePart.Stdout("total 42\nfile.txt\n"),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNode(
            hasText("total 42", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `empty StdoutPart renders nothing`() {
        val message = messageWith(MessagePart.Stdout(""))

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        // Empty Stdout is filtered out by the `if (part.text.isNotEmpty())` guard.
        composeTestRule.onNodeWithText("stdout").assertDoesNotExist()
    }

    @Test
    fun `StderrPart renders error output text`() {
        val message = messageWith(
            MessagePart.Stderr("Permission denied\n"),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNode(
            hasText("Permission denied", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `empty StderrPart renders nothing`() {
        val message = messageWith(MessagePart.Stderr(""))

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("stderr").assertDoesNotExist()
    }

    @Test
    fun `StdoutPart shows stdout label`() {
        val message = messageWith(
            MessagePart.Stdout("some output"),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("stdout").assertIsDisplayed()
    }

    @Test
    fun `StderrPart shows stderr label`() {
        val message = messageWith(
            MessagePart.Stderr("some error"),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("stderr").assertIsDisplayed()
    }

    // ── 5. ErrorPart rendering ───────────────────────────────────────────────

    @Test
    fun `ErrorPart renders error message`() {
        val message = messageWith(
            MessagePart.Error(message = "Connection refused"),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("Connection refused").assertIsDisplayed()
    }

    @Test
    fun `ErrorPart with cause renders cause text`() {
        val message = messageWith(
            MessagePart.Error(
                message = "Connection refused",
                cause = "ECONNREFUSED 127.0.0.1:22",
            ),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNode(
            hasText("ECONNREFUSED 127.0.0.1:22", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `ErrorPart with code shows code badge`() {
        val message = messageWith(
            MessagePart.Error(
                message = "SSH failed",
                code = 255,
            ),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("code: 255").assertIsDisplayed()
    }

    @Test
    fun `ErrorPart without cause does not show cause section`() {
        val message = messageWith(
            MessagePart.Error(message = "Something went wrong", cause = null),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cause:", substring = true).assertDoesNotExist()
    }

    @Test
    fun `ErrorPart without code does not show code badge`() {
        val message = messageWith(
            MessagePart.Error(message = "Generic error", code = null),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("Generic error").assertIsDisplayed()
        composeTestRule.onNode(hasText("code:"), useUnmergedTree = true).assertDoesNotExist()
    }

    // ── 6. ReasoningPart rendering (collapsible) ────────────────────────────

    @Test
    fun `ReasoningPart renders collapsed by default when not streaming`() {
        val message = messageWith(
            MessagePart.Reasoning(text = "Let me think about this...", stepId = "step-1"),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message, isStreaming = false)
        }

        // The header "Step step-1" should always be visible
        composeTestRule.onNodeWithText("Step step-1").assertIsDisplayed()

        // Content should be collapsed by default (not visible)
        composeTestRule.onNode(
            hasText("Let me think about this...", substring = true),
            useUnmergedTree = true,
        ).assertIsNotDisplayed()
    }

    @Test
    fun `ReasoningPart expanded by default when streaming`() {
        val message = messageWith(
            MessagePart.Reasoning(text = "Analyzing the code...", stepId = null),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message, isStreaming = true)
        }

        // When streaming, the reasoning content is auto-expanded
        composeTestRule.onNodeWithText("Thinking").assertIsDisplayed()
        composeTestRule.onNode(
            hasText("Analyzing the code...", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `ReasoningPart toggle expands on click`() {
        val message = messageWith(
            MessagePart.Reasoning(text = "Deep reasoning here", stepId = "s1"),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message, isStreaming = false)
        }

        // Initially collapsed
        composeTestRule.onNode(
            hasText("Deep reasoning here", substring = true),
            useUnmergedTree = true,
        ).assertIsNotDisplayed()

        // Click the header to expand
        composeTestRule.onNodeWithText("Step s1").performClick()

        // Now content should be visible
        composeTestRule.onNode(
            hasText("Deep reasoning here", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `ReasoningPart without stepId shows generic Thinking label`() {
        val message = messageWith(
            MessagePart.Reasoning(text = "Thinking out loud", stepId = null),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message, isStreaming = false)
        }

        composeTestRule.onNodeWithText("Thinking").assertIsDisplayed()
    }

    // ── 7. Mixed type Part list rendering ────────────────────────────────────

    @Test
    fun `mixed parts all render correctly`() {
        val message = messageWith(
            MessagePart.Text("Here is the analysis:"),
            MessagePart.Command(command = "uname -a", exitCode = 0),
            MessagePart.Stdout("Linux host 5.15.0\n"),
            MessagePart.Code(code = "val x = 42", language = "kotlin"),
            MessagePart.Reasoning(text = "The system is healthy.", stepId = "s1"),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        // Text
        composeTestRule.onNodeWithText("Here is the analysis:").assertIsDisplayed()

        // Command + exit code
        composeTestRule.onNodeWithText("uname -a").assertIsDisplayed()
        composeTestRule.onNodeWithText("exit 0").assertIsDisplayed()

        // Stdout
        composeTestRule.onNode(
            hasText("Linux host 5.15.0", substring = true),
            useUnmergedTree = true,
        ).assertExists()

        // Code
        composeTestRule.onNodeWithText("val x = 42").assertIsDisplayed()
        composeTestRule.onNodeWithText("kotlin").assertIsDisplayed()

        // Reasoning header (collapsed by default)
        composeTestRule.onNodeWithText("Step s1").assertIsDisplayed()
    }

    @Test
    fun `command with stdout and stderr renders all parts`() {
        val message = ChatMessage.command(
            id = "test-cmd",
            command = "cat /etc/hostname",
            stdout = "myhost\n",
            stderr = "",
            exitCode = 0,
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("cat /etc/hostname").assertIsDisplayed()
        composeTestRule.onNodeWithText("exit 0").assertIsDisplayed()
        composeTestRule.onNode(
            hasText("myhost", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `error after command renders both command and error cards`() {
        val message = messageWith(
            MessagePart.Command(command = "ssh badhost", exitCode = 255),
            MessagePart.Error(
                message = "Connection failed",
                cause = "Name or service not known",
                code = 255,
            ),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        composeTestRule.onNodeWithText("ssh badhost").assertIsDisplayed()
        composeTestRule.onNodeWithText("exit 255").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connection failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("code: 255").assertIsDisplayed()
    }

    @Test
    fun `empty parts list renders empty block`() {
        val message = messageWith()

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        // The Column should exist but have no children
        composeTestRule.onNode(hasText(""), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `isStreaming flag applies to text parts`() {
        val message = messageWith(
            MessagePart.Text("Streaming content..."),
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message, isStreaming = true)
        }

        // Text is still rendered; the difference is SelectionContainer wrapping
        // which is internal behavior, but the text itself must be visible.
        composeTestRule.onNodeWithText("Streaming content...").assertIsDisplayed()
    }

    @Test
    fun `message with Streaming status treats as streaming`() {
        val message = ChatMessage(
            id = "stream-msg",
            role = ChatRole.Assistant,
            parts = listOf(
                MessagePart.Reasoning(text = "Still thinking...", stepId = null),
            ),
            timestampMs = 1700000000000,
            status = MessageStatus.Streaming,
        )

        composeTestRule.setContent {
            MessagePartsBlock(message = message)
        }

        // Reasoning auto-expands when message status is Streaming
        composeTestRule.onNodeWithText("Thinking").assertIsDisplayed()
        composeTestRule.onNode(
            hasText("Still thinking...", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }
}

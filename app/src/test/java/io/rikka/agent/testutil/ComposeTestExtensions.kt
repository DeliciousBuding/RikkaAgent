package io.rikka.agent.testutil

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput

/**
 * Extension functions for [ComposeTestRule] and [ComposeContentTestRule].
 *
 * Provides convenient assertion and interaction helpers for Jetpack Compose
 * UI tests.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyScreenTest {
 *     @get:Rule
 *     val composeTestRule = createComposeRule()
 *
 *     @Test
 *     fun testScreen() {
 *         composeTestRule.setContent { MyScreen() }
 *
 *         composeTestRule.assertNodeWithText("Hello").isDisplayed()
 *         composeTestRule.assertNodeWithTag("button").performClick()
 *     }
 * }
 * ```
 */

// ── Node Assertion Extensions ─────────────────────────────────────────────────

/**
 * Assert that a node with the given text is displayed.
 *
 * ```kotlin
 * composeTestRule.assertTextIsDisplayed("Hello, World!")
 * ```
 */
fun ComposeTestRule.assertTextIsDisplayed(text: String) {
    onNodeWithText(text).assertIsDisplayed()
}

/**
 * Assert that a node with the given text is NOT displayed.
 *
 * ```kotlin
 * composeTestRule.assertTextIsNotDisplayed("Hidden text")
 * ```
 */
fun ComposeTestRule.assertTextIsNotDisplayed(text: String) {
    onNodeWithText(text).assertIsNotDisplayed()
}

/**
 * Assert that a node with the given test tag is displayed.
 *
 * ```kotlin
 * composeTestRule.assertTagIsDisplayed("my_button")
 * ```
 */
fun ComposeTestRule.assertTagIsDisplayed(tag: String) {
    onNodeWithTag(tag).assertIsDisplayed()
}

/**
 * Assert that a node with the given test tag is NOT displayed.
 *
 * ```kotlin
 * composeTestRule.assertTagIsNotDisplayed("hidden_element")
 * ```
 */
fun ComposeTestRule.assertTagIsNotDisplayed(tag: String) {
    onNodeWithTag(tag).assertIsNotDisplayed()
}

/**
 * Assert that a node with the given content description is displayed.
 *
 * ```kotlin
 * composeTestRule.assertContentDescriptionIsDisplayed("Close button")
 * ```
 */
fun ComposeTestRule.assertContentDescriptionIsDisplayed(description: String) {
    onNode(hasContentDescription(description)).assertIsDisplayed()
}

// ── Node Interaction Extensions ───────────────────────────────────────────────

/**
 * Click on a node with the given text.
 *
 * ```kotlin
 * composeTestRule.clickOnText("Submit")
 * ```
 */
fun ComposeTestRule.clickOnText(text: String) {
    onNodeWithText(text).performClick()
}

/**
 * Click on a node with the given test tag.
 *
 * ```kotlin
 * composeTestRule.clickOnTag("submit_button")
 * ```
 */
fun ComposeTestRule.clickOnTag(tag: String) {
    onNodeWithTag(tag).performClick()
}

/**
 * Click on a node with the given content description.
 *
 * ```kotlin
 * composeTestRule.clickOnContentDescription("Close")
 * ```
 */
fun ComposeTestRule.clickOnContentDescription(description: String) {
    onNode(hasContentDescription(description)).performClick()
}

/**
 * Type text into a node with the given test tag.
 *
 * ```kotlin
 * composeTestRule.typeText("input_field", "Hello, World!")
 * ```
 */
fun ComposeTestRule.typeText(tag: String, text: String) {
    onNodeWithTag(tag).performTextInput(text)
}

/**
 * Type text into a node with the given label.
 *
 * ```kotlin
 * composeTestRule.typeTextByLabel("Email", "test@example.com")
 * ```
 */
fun ComposeTestRule.typeTextByLabel(label: String, text: String) {
    onNode(hasContentDescription(label)).performTextInput(text)
}

// ── SemanticNodeInteraction Extensions ────────────────────────────────────────

/**
 * Assert that the node contains the given text.
 *
 * ```kotlin
 * onNodeWithTag("output").assertContainsText("Hello")
 * ```
 */
fun SemanticsNodeInteraction.assertContainsText(text: String): SemanticsNodeInteraction =
    assertTextContains(text)

/**
 * Assert that the node has exactly the given text.
 *
 * ```kotlin
 * onNodeWithTag("title").assertHasText("Welcome")
 * ```
 */
fun SemanticsNodeInteraction.assertHasText(text: String): SemanticsNodeInteraction =
    assertTextEquals(text)

/**
 * Assert that the node is clickable.
 *
 * ```kotlin
 * onNodeWithTag("button").assertIsClickable()
 * ```
 */
fun SemanticsNodeInteraction.assertIsClickable(): SemanticsNodeInteraction =
    assert(hasClickAction)

/**
 * Assert that the node is NOT clickable.
 *
 * ```kotlin
 * onNodeWithTag("label").assertIsNotClickable()
 * ```
 */
fun SemanticsNodeInteraction.assertIsNotClickable(): SemanticsNodeInteraction =
    assert(hasNoClickAction)

/**
 * Assert that the node is enabled.
 *
 * ```kotlin
 * onNodeWithTag("button").assertIsEnabled()
 * ```
 */
fun SemanticsNodeInteraction.assertIsEnabled(): SemanticsNodeInteraction =
    assertIsEnabled()

/**
 * Assert that the node is NOT enabled.
 *
 * ```kotlin
 * onNodeWithTag("button").assertIsDisabled()
 * ```
 */
fun SemanticsNodeInteraction.assertIsDisabled(): SemanticsNodeInteraction =
    assertIsNotEnabled()

/**
 * Assert that the node is selected.
 *
 * ```kotlin
 * onNodeWithTag("tab_1").assertIsSelected()
 * ```
 */
fun SemanticsNodeInteraction.assertIsSelected(): SemanticsNodeInteraction =
    assertIsSelected()

/**
 * Assert that the node is NOT selected.
 *
 * ```kotlin
 * onNodeWithTag("tab_2").assertIsNotSelected()
 * ```
 */
fun SemanticsNodeInteraction.assertIsNotSelected(): SemanticsNodeInteraction =
    assertIsNotSelected()

// ── Collection Extensions ─────────────────────────────────────────────────────

/**
 * Assert that exactly [count] nodes match the given matcher.
 *
 * ```kotlin
 * onAllNodes(hasTestTag("item")).assertCount(5)
 * ```
 */
fun SemanticsNodeInteractionCollection.assertCount(count: Int): SemanticsNodeInteractionCollection {
    val size = fetchSemanticsNodes().size
    assert(size == count) { "Expected $count nodes but found $size" }
    return this
}

/**
 * Assert that at least [count] nodes match the given matcher.
 *
 * ```kotlin
 * onAllNodes(hasTestTag("item")).assertCountAtLeast(3)
 * ```
 */
fun SemanticsNodeInteractionCollection.assertCountAtLeast(count: Int): SemanticsNodeInteractionCollection {
    val size = fetchSemanticsNodes().size
    assert(size >= count) { "Expected at least $count nodes but found $size" }
    return this
}

/**
 * Get the first node in the collection.
 *
 * ```kotlin
 * onAllNodes(hasTestTag("item")).first()
 * ```
 */
fun SemanticsNodeInteractionCollection.first(): SemanticsNodeInteraction = onFirst()

/**
 * Get the last node in the collection.
 *
 * ```kotlin
 * onAllNodes(hasTestTag("item")).last()
 * ```
 */
fun SemanticsNodeInteractionCollection.last(): SemanticsNodeInteraction = onLast()

// ── Wait Extensions ───────────────────────────────────────────────────────────

/**
 * Wait until a node with the given text appears.
 *
 * **Note**: This is a busy-wait and should be used sparingly.
 * Prefer using `waitUntil` with a timeout for production tests.
 *
 * ```kotlin
 * composeTestRule.waitForText("Success!")
 * ```
 */
fun ComposeTestRule.waitForText(
    text: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis) {
        onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Wait until a node with the given test tag appears.
 *
 * ```kotlin
 * composeTestRule.waitForTag("result_view")
 * ```
 */
fun ComposeTestRule.waitForTag(
    tag: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis) {
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Wait until a node with the given text disappears.
 *
 * ```kotlin
 * composeTestRule.waitForTextToDisappear("Loading...")
 * ```
 */
fun ComposeTestRule.waitForTextToDisappear(
    text: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis) {
        onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
    }
}

/**
 * Wait until a node with the given test tag disappears.
 *
 * ```kotlin
 * composeTestRule.waitForTagToDisappear("loading_indicator")
 * ```
 */
fun ComposeTestRule.waitForTagToDisappear(
    tag: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis) {
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()
    }
}

// ── Helper Extensions ─────────────────────────────────────────────────────────

/**
 * Get the root semantics node.
 *
 * ```kotlin
 * val root = composeTestRule.root()
 * ```
 */
fun ComposeTestRule.root(): SemanticsNodeInteraction = onRoot()

/**
 * Assert that no node with the given text exists.
 *
 * ```kotlin
 * composeTestRule.assertNoText("Error occurred")
 * ```
 */
fun ComposeTestRule.assertNoText(text: String) {
    onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty().let { isEmpty ->
        assert(isEmpty) { "Expected no node with text '$text' but found one" }
    }
}

/**
 * Assert that the screen is empty (no meaningful content).
 *
 * Useful for testing loading or empty states.
 *
 * ```kotlin
 * composeTestRule.assertScreenIsEmpty()
 * ```
 */
fun ComposeTestRule.assertScreenIsEmpty() {
    val nodes = onRoot().fetchSemanticsNodes()
    assert(nodes.size <= 1) { "Expected empty screen but found ${nodes.size} nodes" }
}

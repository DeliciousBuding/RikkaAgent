package io.rikka.agent.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.rikka.agent.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FullOutputDialogTest {

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
  fun `dialog shows complete output and routes share plus dismiss callbacks`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    var shared = 0
    var dismissed = 0
    val fullText = "line-1\nline-2\nline-3"

    composeRule.setContent {
      MaterialTheme {
        FullOutputDialog(
          fullText = fullText,
          onShare = { shared++ },
          onDismiss = { dismissed++ },
        )
      }
    }

    composeRule.onNodeWithText(context.getString(R.string.full_output_title)).assertIsDisplayed()
    composeRule.onNodeWithText(fullText, substring = true).assertIsDisplayed()

    composeRule.onNodeWithText(context.getString(R.string.share_full_output))
      .assertIsDisplayed()
      .performClick()
    composeRule.onNodeWithText(context.getString(R.string.close))
      .assertIsDisplayed()
      .performClick()

    assertEquals(1, shared)
    assertEquals(1, dismissed)
  }
}

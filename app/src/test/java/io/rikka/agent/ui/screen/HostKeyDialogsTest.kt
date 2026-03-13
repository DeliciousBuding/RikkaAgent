package io.rikka.agent.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.rikka.agent.R
import io.rikka.agent.vm.HostKeyEvent
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
class HostKeyDialogsTest {

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
  fun `mismatch dialog shows replace action and triggers accept`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val event = HostKeyEvent.Mismatch(
      host = "example.test",
      port = 22,
      expectedFingerprint = "SHA256:old",
      actualFingerprint = "SHA256:new",
      keyType = "ED25519",
    )
    var accepted = false

    composeRule.setContent {
      MaterialTheme {
        HostKeyDialog(
          event = event,
          onAccept = { accepted = true },
          onReject = {},
        )
      }
    }

    composeRule.onNodeWithText(context.getString(R.string.host_key_changed_title)).assertIsDisplayed()
    composeRule.onNodeWithText(context.getString(R.string.btn_replace_trust)).assertIsDisplayed().performClick()

    assertTrue(accepted)
  }

  @Test
  fun `replacement confirmation dialog shows warning copy and routes decisions`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val event = HostKeyEvent.Mismatch(
      host = "example.test",
      port = 2222,
      expectedFingerprint = "SHA256:old",
      actualFingerprint = "SHA256:new",
      keyType = "ED25519",
    )
    var decisions = mutableListOf<String>()

    composeRule.setContent {
      MaterialTheme {
        HostKeyReplacementConfirmDialog(
          event = event,
          onConfirm = { decisions += "confirm" },
          onReject = { decisions += "reject" },
        )
      }
    }

    composeRule.onNodeWithText(context.getString(R.string.host_key_replace_confirm_title)).assertIsDisplayed()
    composeRule.onNodeWithText(
      context.getString(R.string.host_key_replace_confirm_msg, event.host, event.port)
    ).assertIsDisplayed()
    composeRule.onNodeWithText(context.getString(R.string.host_key_replace_confirm_action))
      .assertIsDisplayed()
      .performClick()

    assertEquals(listOf("confirm"), decisions)
  }
}

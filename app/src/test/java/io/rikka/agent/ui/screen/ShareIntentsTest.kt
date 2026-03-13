package io.rikka.agent.ui.screen

import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareIntentsTest {

  @Before
  fun setUp() {
    stopKoin()
  }

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun `plainText builds chooser with text payload`() {
    val chooser = ShareIntents.plainText(
      text = "hello world",
      chooserTitle = "Share output",
    )

    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    assertEquals("Share output", chooser.getStringExtra(Intent.EXTRA_TITLE))

    @Suppress("DEPRECATION")
    val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
    assertNotNull(target)
    assertEquals(Intent.ACTION_SEND, target!!.action)
    assertEquals("text/plain", target.type)
    assertEquals("hello world", target.getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test
  fun `sessionExport includes subject and transcript text`() {
    val chooser = ShareIntents.sessionExport(
      text = "# Session: demo",
      subject = "SSH Session - demo",
      chooserTitle = "Export session",
    )

    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    assertEquals("Export session", chooser.getStringExtra(Intent.EXTRA_TITLE))

    @Suppress("DEPRECATION")
    val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
    assertNotNull(target)
    assertEquals("# Session: demo", target!!.getStringExtra(Intent.EXTRA_TEXT))
    assertEquals("SSH Session - demo", target.getStringExtra(Intent.EXTRA_SUBJECT))
  }
}

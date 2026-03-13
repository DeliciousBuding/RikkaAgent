package io.rikka.agent.ui.screen

import android.net.Uri
import io.rikka.agent.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyImportSupportTest {

  @Test
  fun `importPickedPrivateKeyUri stores key ref when permission succeeds`() {
    val captured = Capture()
    val uri = Uri.parse("content://keys/one")

    val selection = importPickedPrivateKeyUri(
      takePersistablePermission = { targetUri, flags ->
        captured.uri = targetUri
        captured.flags = flags
      },
      uri = uri,
    )

    assertEquals(uri.toString(), selection.keyRef)
    assertNull(selection.messageResId)
    assertEquals(uri, captured.uri)
    assertEquals(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION, captured.flags)
  }

  @Test
  fun `importPickedPrivateKeyUri keeps key ref with warning on security exception`() {
    val uri = Uri.parse("content://keys/blocked")

    val selection = importPickedPrivateKeyUri(
      takePersistablePermission = { _, _ -> throw SecurityException("blocked") },
      uri = uri,
    )

    assertEquals(uri.toString(), selection.keyRef)
    assertEquals(R.string.snackbar_key_permission_not_persisted, selection.messageResId)
  }

  @Test
  fun `importPickedPrivateKeyUri reports failure on generic exception`() {
    val uri = Uri.parse("content://keys/bad")

    val selection = importPickedPrivateKeyUri(
      takePersistablePermission = { _, _ -> throw IllegalArgumentException("broken") },
      uri = uri,
    )

    assertNull(selection.keyRef)
    assertEquals(R.string.snackbar_key_selection_failed, selection.messageResId)
  }

  @Test
  fun `extractKeyDisplayName handles internal keys and SAF style segments`() {
    assertEquals("App key", extractKeyDisplayName("internal-key://abc"))
    assertEquals("id_rsa", extractKeyDisplayName("content://docs/primary:keys/id_rsa"))
    assertNotNull(extractKeyDisplayName("content://docs/"))
    assertTrue(extractKeyDisplayName("content://docs/").isNotBlank())
  }

  private class Capture {
    var uri: Uri? = null
    var flags: Int? = null
  }

  @Before
  fun setUp() {
    stopKoin()
  }

  @After
  fun tearDown() {
    stopKoin()
  }
}

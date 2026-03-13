package io.rikka.agent.ui.screen

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import io.rikka.agent.R
import io.rikka.agent.ssh.ContentUriKeyContentProvider

internal data class PickedPrivateKeySelection(
  val keyRef: String?,
  val messageResId: Int? = null,
)

internal fun importPickedPrivateKeyUri(
  takePersistablePermission: (Uri, Int) -> Unit,
  uri: Uri,
  flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION,
): PickedPrivateKeySelection {
  return try {
    takePersistablePermission(uri, flags)
    PickedPrivateKeySelection(keyRef = uri.toString())
  } catch (_: SecurityException) {
    PickedPrivateKeySelection(
      keyRef = uri.toString(),
      messageResId = R.string.snackbar_key_permission_not_persisted,
    )
  } catch (_: Exception) {
    PickedPrivateKeySelection(
      keyRef = null,
      messageResId = R.string.snackbar_key_selection_failed,
    )
  }
}

internal fun importPickedPrivateKeyUri(
  contentResolver: ContentResolver,
  uri: Uri,
): PickedPrivateKeySelection =
  importPickedPrivateKeyUri(
    takePersistablePermission = { targetUri, flags ->
      contentResolver.takePersistableUriPermission(targetUri, flags)
    },
    uri = uri,
  )

/** Extract a readable display name from a key URI string (content:// or internal-key://). */
internal fun extractKeyDisplayName(uriString: String): String {
  if (uriString.startsWith(ContentUriKeyContentProvider.INTERNAL_KEY_SCHEME)) return "App key"
  val uri = Uri.parse(uriString)
  val lastSegment = uri.lastPathSegment ?: return uriString
  // SAF URIs often have "primary:path/to/file" format.
  return lastSegment.substringAfterLast('/')
    .substringAfterLast(':')
    .ifBlank { lastSegment }
}

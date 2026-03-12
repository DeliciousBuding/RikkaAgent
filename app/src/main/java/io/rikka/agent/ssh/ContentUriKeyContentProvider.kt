package io.rikka.agent.ssh

import android.content.Context
import io.rikka.agent.model.SshProfile
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID

/**
 * Reads private key content from:
 * - content:// URIs (Android SAF file picker)
 * - internal-key:// URIs (pasted keys stored in app-private files)
 */
class ContentUriKeyContentProvider(
  private val context: Context,
) : KeyContentProvider {

  private val keysDir: File
    get() = File(context.filesDir, "ssh_keys").also { it.mkdirs() }

  override suspend fun getKeyContent(profile: SshProfile): String? {
    val uriString = profile.keyRef ?: return null
    return try {
      if (uriString.startsWith(INTERNAL_KEY_SCHEME)) {
        val keyId = uriString.removePrefix(INTERNAL_KEY_SCHEME)
        File(keysDir, keyId).takeIf { it.exists() }?.readText(Charsets.UTF_8)
      } else {
        val uri = android.net.Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { input ->
          BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
      }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Save pasted key content to app-internal storage.
   * Returns the keyRef string to store in the profile.
   */
  fun savePastedKey(content: String): String {
    val keyId = UUID.randomUUID().toString()
    File(keysDir, keyId).writeText(content, Charsets.UTF_8)
    return "$INTERNAL_KEY_SCHEME$keyId"
  }

  /**
   * Delete an internally stored key file.
   */
  fun deleteKey(keyRef: String) {
    if (keyRef.startsWith(INTERNAL_KEY_SCHEME)) {
      val keyId = keyRef.removePrefix(INTERNAL_KEY_SCHEME)
      File(keysDir, keyId).delete()
    }
  }

  companion object {
    const val INTERNAL_KEY_SCHEME = "internal-key://"
  }
}

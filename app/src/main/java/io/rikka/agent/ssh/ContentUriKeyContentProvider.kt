package io.rikka.agent.ssh

import android.content.Context
import io.rikka.agent.model.SshProfile
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Reads private key content from Android content URIs (SAF).
 * The profile's keyRef stores a persistent content:// URI string.
 */
class ContentUriKeyContentProvider(
  private val context: Context,
) : KeyContentProvider {

  override suspend fun getKeyContent(profile: SshProfile): String? {
    val uriString = profile.keyRef ?: return null
    return try {
      val uri = android.net.Uri.parse(uriString)
      context.contentResolver.openInputStream(uri)?.use { input ->
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
      }
    } catch (e: Exception) {
      null
    }
  }
}

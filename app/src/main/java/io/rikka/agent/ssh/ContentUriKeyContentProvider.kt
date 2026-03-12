package io.rikka.agent.ssh

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import io.rikka.agent.model.SshProfile
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.UUID

/**
 * Reads private key content from:
 * - content:// URIs (Android SAF file picker)
 * - internal-key:// URIs (keys stored encrypted in app-private files)
 */
class ContentUriKeyContentProvider(
  private val context: Context,
) : KeyContentProvider {

  @Suppress("DEPRECATION")
  private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
  private val keysDir: File
    get() = File(context.filesDir, "ssh_keys").also { it.mkdirs() }

  @Suppress("DEPRECATION")
  private fun encryptedFile(file: File): EncryptedFile =
    EncryptedFile.Builder(
      file,
      context,
      masterKeyAlias,
      EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()

  override suspend fun getKeyContent(profile: SshProfile): String? {
    val uriString = profile.keyRef ?: return null
    return try {
      if (uriString.startsWith(INTERNAL_KEY_SCHEME)) {
        val keyId = uriString.removePrefix(INTERNAL_KEY_SCHEME)
        val file = File(keysDir, keyId)
        if (!file.exists()) return null
        try {
          // Try reading as encrypted file (new format)
          val ef = encryptedFile(file)
          ef.openFileInput().use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
          // Fallback: read as plain text (legacy keys from before encryption)
          file.readText(Charsets.UTF_8)
        }
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
   * Save key content encrypted to app-internal storage.
   * Returns the keyRef string to store in the profile.
   */
  fun savePastedKey(content: String): String {
    val keyId = UUID.randomUUID().toString()
    val file = File(keysDir, keyId)
    val ef = encryptedFile(file)
    ef.openFileOutput().use { it.write(content.toByteArray(Charsets.UTF_8)) }
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

package io.rikka.agent.ssh

import io.rikka.agent.ssh.StoredHostKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object KnownHostsEntryCodec {
  @Serializable
  private data class Entry(
    val fingerprint: String,
    val keyType: String,
    val addedAtMs: Long,
  )

  fun encode(key: StoredHostKey): String {
    return Json.encodeToString(Entry(key.fingerprint, key.keyType, key.addedAtMs))
  }

  fun decode(json: String): StoredHostKey? {
    return try {
      val e = Json.decodeFromString<Entry>(json)
      StoredHostKey(e.fingerprint, e.keyType, e.addedAtMs)
    } catch (_: Exception) {
      null
    }
  }
}

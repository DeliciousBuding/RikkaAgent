package io.rikka.agent.ssh

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.ssh.StoredHostKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.knownHostsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "known_hosts",
)

class DataStoreKnownHostsStore(private val context: Context) : KnownHostsStore {

  @Serializable
  private data class Entry(
    val fingerprint: String,
    val keyType: String,
    val addedAtMs: Long,
  )

  override suspend fun getFingerprint(host: String, port: Int): StoredHostKey? {
    val prefs = context.knownHostsDataStore.data.first()
    val json = prefs[prefKey(host, port)] ?: return null
    return try {
      val e = Json.decodeFromString<Entry>(json)
      StoredHostKey(e.fingerprint, e.keyType, e.addedAtMs)
    } catch (_: Exception) {
      null
    }
  }

  override suspend fun store(host: String, port: Int, key: StoredHostKey) {
    val json = Json.encodeToString(Entry(key.fingerprint, key.keyType, key.addedAtMs))
    context.knownHostsDataStore.edit { it[prefKey(host, port)] = json }
  }

  override suspend fun remove(host: String, port: Int) {
    context.knownHostsDataStore.edit { it.remove(prefKey(host, port)) }
  }

  override suspend fun getAll(): List<Pair<String, StoredHostKey>> {
    val prefs = context.knownHostsDataStore.data.first()
    return prefs.asMap().mapNotNull { (key, value) ->
      if (value !is String) return@mapNotNull null
      try {
        val e = Json.decodeFromString<Entry>(value)
        key.name to StoredHostKey(e.fingerprint, e.keyType, e.addedAtMs)
      } catch (_: Exception) {
        null
      }
    }
  }

  private fun prefKey(host: String, port: Int) =
    stringPreferencesKey("[$host]:$port")
}

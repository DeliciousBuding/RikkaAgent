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

private val Context.knownHostsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "known_hosts",
)

class DataStoreKnownHostsStore(private val context: Context) : KnownHostsStore {

  override suspend fun getFingerprint(host: String, port: Int): StoredHostKey? {
    val prefs = context.knownHostsDataStore.data.first()
    val json = prefs[prefKey(host, port)] ?: return null
    return KnownHostsEntryCodec.decode(json)
  }

  override suspend fun store(host: String, port: Int, key: StoredHostKey) {
    val json = KnownHostsEntryCodec.encode(key)
    context.knownHostsDataStore.edit { it[prefKey(host, port)] = json }
  }

  override suspend fun remove(host: String, port: Int) {
    context.knownHostsDataStore.edit { it.remove(prefKey(host, port)) }
  }

  override suspend fun getAll(): List<Pair<String, StoredHostKey>> {
    val prefs = context.knownHostsDataStore.data.first()
    return prefs.asMap().mapNotNull { (key, value) ->
      if (value !is String) return@mapNotNull null
      KnownHostsEntryCodec.decode(value)?.let { key.name to it }
    }
  }

  private fun prefKey(host: String, port: Int) =
    stringPreferencesKey("[$host]:$port")
}

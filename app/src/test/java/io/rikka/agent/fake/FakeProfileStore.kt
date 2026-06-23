package io.rikka.agent.fake

import io.rikka.agent.model.ProfileSearchFilter
import io.rikka.agent.model.SshProfile
import io.rikka.agent.model.filterAndSort
import io.rikka.agent.storage.ProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ProfileStore] for tests.
 *
 * Supports configurable failure injection via [throwOnUpsert] and [throwOnDelete].
 */
class FakeProfileStore : ProfileStore {

  private val profiles = mutableMapOf<String, SshProfile>()
  private val profilesFlow = MutableStateFlow<List<SshProfile>>(emptyList())

  // ── Configurable hooks ─────────────────────────────────────────────────────

  /** When non-null, [upsert] throws this exception. */
  var throwOnUpsert: Throwable? = null

  /** When non-null, [delete] throws this exception. */
  var throwOnDelete: Throwable? = null

  // ── ProfileStore ───────────────────────────────────────────────────────────

  override suspend fun listProfiles(): List<SshProfile> =
    profiles.values.toList()

  override suspend fun getById(id: String): SshProfile? =
    profiles[id]

  override suspend fun upsert(profile: SshProfile) {
    throwOnUpsert?.let { throw it }
    profiles[profile.id] = profile
    profilesFlow.value = profiles.values.toList()
  }

  override suspend fun delete(profileId: String) {
    throwOnDelete?.let { throw it }
    profiles.remove(profileId)
    profilesFlow.value = profiles.values.toList()
  }

  override fun observeFiltered(filter: ProfileSearchFilter): Flow<List<SshProfile>> =
    profilesFlow.map { it.filterAndSort(filter) }

  override fun observeByGroup(group: String): Flow<List<SshProfile>> =
    profilesFlow.map { all ->
      all.filter { it.group.name == group }
    }

  override suspend fun allIds(): Set<String> =
    profiles.keys.toSet()

  // ── Test helpers ───────────────────────────────────────────────────────────

  /** Seed profiles directly (bypasses hooks). */
  fun seed(vararg items: SshProfile) {
    items.forEach { profiles[it.id] = it }
    profilesFlow.value = profiles.values.toList()
  }

  /** Return a snapshot of all stored profiles. */
  fun snapshot(): List<SshProfile> = profiles.values.toList()

  /** Clear all data. */
  fun reset() {
    profiles.clear()
    profilesFlow.value = emptyList()
  }

  companion object {
    /** Create a store pre-populated with the given profiles. */
    fun of(vararg items: SshProfile): FakeProfileStore =
      FakeProfileStore().also { it.seed(*items) }
  }
}

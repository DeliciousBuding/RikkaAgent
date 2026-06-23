package io.rikka.agent.fake

import io.rikka.agent.model.SshProfile
import io.rikka.agent.storage.ProfileStore

/**
 * In-memory [ProfileStore] for tests.
 *
 * Supports configurable failure injection via [throwOnUpsert] and [throwOnDelete].
 */
class FakeProfileStore : ProfileStore {

  private val profiles = mutableMapOf<String, SshProfile>()

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
  }

  override suspend fun delete(profileId: String) {
    throwOnDelete?.let { throw it }
    profiles.remove(profileId)
  }

  // ── Test helpers ───────────────────────────────────────────────────────────

  /** Seed profiles directly (bypasses hooks). */
  fun seed(vararg items: SshProfile) {
    items.forEach { profiles[it.id] = it }
  }

  /** Return a snapshot of all stored profiles. */
  fun snapshot(): List<SshProfile> = profiles.values.toList()

  /** Clear all data. */
  fun reset() {
    profiles.clear()
  }

  companion object {
    /** Create a store pre-populated with the given profiles. */
    fun of(vararg items: SshProfile): FakeProfileStore =
      FakeProfileStore().also { it.seed(*items) }
  }
}

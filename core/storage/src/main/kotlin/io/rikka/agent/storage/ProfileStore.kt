package io.rikka.agent.storage

import io.rikka.agent.model.SshProfile

/**
 * Storage is defined early so UI can be built against it with fake implementations.
 *
 * v1 plan:
 * - DataStore for preferences
 * - Room for structured tables
 * - Encrypted storage for private keys/credentials
 */
interface ProfileStore {
  suspend fun listProfiles(): List<SshProfile>
  suspend fun upsert(profile: SshProfile)
  suspend fun delete(profileId: String)
}

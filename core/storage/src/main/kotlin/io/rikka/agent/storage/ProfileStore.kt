package io.rikka.agent.storage

import io.rikka.agent.model.ProfileSearchFilter
import io.rikka.agent.model.SshProfile
import kotlinx.coroutines.flow.Flow

/**
 * Storage interface for SSH profiles.
 *
 * Provides CRUD operations plus search/filter capabilities.
 *
 * v1 plan:
 * - DataStore for preferences
 * - Room for structured tables
 * - Encrypted storage for private keys/credentials
 */
interface ProfileStore {
  suspend fun listProfiles(): List<SshProfile>
  suspend fun getById(id: String): SshProfile?
  suspend fun upsert(profile: SshProfile)
  suspend fun delete(profileId: String)

  /**
   * Observe profiles matching the given [filter].
   *
   * Combines server-side text search (via DAO) with client-side
   * group/tag filtering and sorting.
   */
  fun observeFiltered(filter: ProfileSearchFilter): Flow<List<SshProfile>>

  /**
   * Observe profiles belonging to a specific group.
   */
  fun observeByGroup(group: String): Flow<List<SshProfile>>

  /**
   * Get all profile IDs currently in the store.
   *
   * Used during import to detect ID conflicts.
   */
  suspend fun allIds(): Set<String>
}

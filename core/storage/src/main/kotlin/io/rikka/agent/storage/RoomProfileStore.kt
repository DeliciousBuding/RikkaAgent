package io.rikka.agent.storage

import io.rikka.agent.model.ProfileSearchFilter
import io.rikka.agent.model.SshProfile
import io.rikka.agent.model.filterAndSort
import io.rikka.agent.storage.db.SshProfileDao
import io.rikka.agent.storage.db.toEntity
import io.rikka.agent.storage.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomProfileStore(
  private val dao: SshProfileDao,
) : ProfileStore {

  override suspend fun listProfiles(): List<SshProfile> =
    dao.observeAll().first().map { it.toModel() }

  fun observeProfiles(): Flow<List<SshProfile>> =
    dao.observeAll().map { list -> list.map { it.toModel() } }

  override suspend fun getById(id: String): SshProfile? =
    dao.getById(id)?.toModel()

  override suspend fun upsert(profile: SshProfile) {
    dao.upsert(profile.toEntity())
  }

  override suspend fun delete(profileId: String) {
    dao.delete(profileId)
  }

  /**
   * Observe profiles matching the given [filter].
   *
   * Uses the DAO's server-side text search when a [ProfileSearchFilter.query]
   * is active, then applies client-side group/tag filtering and sorting.
   * Falls back to observing all profiles when no query is set.
   */
  override fun observeFiltered(filter: ProfileSearchFilter): Flow<List<SshProfile>> {
    val baseFlow = if (filter.query.isNotBlank()) {
      dao.search(filter.query)
    } else {
      dao.observeAll()
    }
    return baseFlow.map { entities ->
      entities.map { it.toModel() }.filterAndSort(filter)
    }
  }

  override fun observeByGroup(group: String): Flow<List<SshProfile>> =
    dao.observeByGroup(group).map { list -> list.map { it.toModel() } }

  override suspend fun allIds(): Set<String> =
    dao.observeAll().first().map { it.id }.toSet()
}

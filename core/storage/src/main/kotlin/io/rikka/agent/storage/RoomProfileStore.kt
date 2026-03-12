package io.rikka.agent.storage

import io.rikka.agent.model.SshProfile
import io.rikka.agent.storage.db.SshProfileDao
import io.rikka.agent.storage.db.toEntity
import io.rikka.agent.storage.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProfileStore(
  private val dao: SshProfileDao,
) : ProfileStore {

  override suspend fun listProfiles(): List<SshProfile> =
    dao.observeAll().let { flow ->
      // Single snapshot; callers who need reactive updates should use observeAll().
      var result = emptyList<SshProfile>()
      flow.collect { entities ->
        result = entities.map { it.toModel() }
        return@collect
      }
      result
    }

  fun observeProfiles(): Flow<List<SshProfile>> =
    dao.observeAll().map { list -> list.map { it.toModel() } }

  suspend fun getById(id: String): SshProfile? =
    dao.getById(id)?.toModel()

  override suspend fun upsert(profile: SshProfile) {
    dao.upsert(profile.toEntity())
  }

  override suspend fun delete(profileId: String) {
    dao.delete(profileId)
  }
}

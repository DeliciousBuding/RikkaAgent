package io.rikka.agent.storage

import io.rikka.agent.model.SshProfile
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
}

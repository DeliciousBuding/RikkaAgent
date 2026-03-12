package io.rikka.agent.storage.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SshProfileDao {
  @Query("SELECT * FROM ssh_profiles ORDER BY name ASC")
  fun observeAll(): Flow<List<SshProfileEntity>>

  @Query("SELECT * FROM ssh_profiles WHERE id = :id")
  suspend fun getById(id: String): SshProfileEntity?

  @Upsert
  suspend fun upsert(entity: SshProfileEntity)

  @Query("DELETE FROM ssh_profiles WHERE id = :id")
  suspend fun delete(id: String)
}

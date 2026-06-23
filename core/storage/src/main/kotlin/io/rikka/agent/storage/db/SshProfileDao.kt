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

  /**
   * Search profiles by name, host, or tag substring (case-insensitive).
   *
   * Matches against:
   * - [name] (display name)
   * - [host] (hostname or IP)
   * - [tags] (comma-separated tag string)
   *
   * @param query The search term. `%query%` pattern is applied server-side.
   */
  @Query(
    """
    SELECT * FROM ssh_profiles
    WHERE name LIKE '%' || :query || '%'
       OR host LIKE '%' || :query || '%'
       OR tags LIKE '%' || :query || '%'
    ORDER BY name ASC
    """
  )
  fun search(query: String): Flow<List<SshProfileEntity>>

  /**
   * Filter profiles by [group].
   *
   * @param group The group name (e.g. "Production", "Development").
   */
  @Query("SELECT * FROM ssh_profiles WHERE group = :group ORDER BY name ASC")
  fun observeByGroup(group: String): Flow<List<SshProfileEntity>>
}

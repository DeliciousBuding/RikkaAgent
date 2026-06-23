package io.rikka.agent.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.model.SshProfile
import io.rikka.agent.storage.RoomProfileStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for the SSH profiles list screen.
 *
 * ## Responsibilities
 * - Exposes the full list of saved SSH profiles as an observable [StateFlow].
 * - Provides profile deletion and duplication operations.
 *
 * ## Thread Safety
 * - The [profiles] flow is created via [StateFlow.stateIn] with [SharingStarted.WhileSubscribed],
 *   which is safe for multi-collector scenarios.
 * - [delete] and [duplicate] launch coroutines in [viewModelScope]; the underlying
 *   [RoomProfileStore] Room DAO operations are inherently thread-safe.
 *
 * ## Exposed State
 * | StateFlow   | Type                          | Description                              |
 * |-------------|-------------------------------|------------------------------------------|
 * | [profiles]  | `StateFlow<List<SshProfile>>` | Observable list of all saved SSH profiles. |
 *
 * @param store The [RoomProfileStore] for profile persistence.
 */
class ProfilesViewModel(
  private val store: RoomProfileStore,
) : ViewModel() {

  /**
   * Observable list of all saved SSH profiles.
   *
   * Uses [SharingStarted.WhileSubscribed] with a 5-second replay timeout so the
   * upstream Flow stops collecting when no UI subscribers are active.
   */
  val profiles: StateFlow<List<SshProfile>> = store.observeProfiles()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /**
   * Delete a profile by its ID.
   *
   * @param profileId The ID of the profile to delete.
   */
  fun delete(profileId: String) {
    viewModelScope.launch { store.delete(profileId) }
  }

  /**
   * Duplicate an existing profile with a new ID and " (copy)" appended to the name.
   *
   * @param profile The [SshProfile] to duplicate.
   */
  fun duplicate(profile: SshProfile) {
    viewModelScope.launch {
      store.upsert(
        profile.copy(
          id = UUID.randomUUID().toString(),
          name = "${profile.name} (copy)",
        ),
      )
    }
  }
}

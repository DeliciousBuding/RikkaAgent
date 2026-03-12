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

class ProfilesViewModel(
  private val store: RoomProfileStore,
) : ViewModel() {

  val profiles: StateFlow<List<SshProfile>> = store.observeProfiles()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun delete(profileId: String) {
    viewModelScope.launch { store.delete(profileId) }
  }

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

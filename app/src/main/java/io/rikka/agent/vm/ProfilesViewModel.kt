package io.rikka.agent.vm

import androidx.lifecycle.ViewModel
import io.rikka.agent.model.AuthType
import io.rikka.agent.model.SshProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory profile list. Will be backed by Room/DataStore once storage layer lands.
 */
class ProfilesViewModel : ViewModel() {

  private val _profiles = MutableStateFlow(
    listOf(
      SshProfile(
        id = "demo-1",
        name = "Dev Server",
        host = "192.168.1.100",
        port = 22,
        username = "admin",
        authType = AuthType.PublicKey,
      ),
    )
  )

  val profiles: StateFlow<List<SshProfile>> = _profiles

  fun delete(profileId: String) {
    _profiles.update { list -> list.filter { it.id != profileId } }
  }
}

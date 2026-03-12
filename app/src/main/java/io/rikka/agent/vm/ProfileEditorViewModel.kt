package io.rikka.agent.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.model.AuthType
import io.rikka.agent.model.SshProfile
import io.rikka.agent.storage.RoomProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ProfileForm(
  val name: String = "",
  val host: String = "",
  val port: String = "22",
  val username: String = "root",
  val authType: AuthType = AuthType.PublicKey,
)

class ProfileEditorViewModel(
  private val profileId: String?,
  private val store: RoomProfileStore,
) : ViewModel() {

  private val _form = MutableStateFlow(ProfileForm())
  val form: StateFlow<ProfileForm> = _form

  private val _saved = MutableStateFlow(false)
  val saved: StateFlow<Boolean> = _saved

  init {
    if (profileId != null) {
      viewModelScope.launch {
        store.getById(profileId)?.let { p ->
          _form.value = ProfileForm(
            name = p.name,
            host = p.host,
            port = p.port.toString(),
            username = p.username,
            authType = p.authType,
          )
        }
      }
    }
  }

  fun updateForm(updated: ProfileForm) {
    _form.value = updated
  }

  fun save() {
    val f = _form.value
    if (f.host.isBlank() || f.username.isBlank()) return
    viewModelScope.launch {
      val profile = SshProfile(
        id = profileId ?: UUID.randomUUID().toString(),
        name = f.name,
        host = f.host.trim(),
        port = f.port.toIntOrNull() ?: 22,
        username = f.username.trim(),
        authType = f.authType,
      )
      store.upsert(profile)
      _saved.value = true
    }
  }
}

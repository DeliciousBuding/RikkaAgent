package io.rikka.agent.vm

import androidx.lifecycle.ViewModel
import io.rikka.agent.model.AuthType
import io.rikka.agent.model.SshProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ProfileForm(
  val name: String = "",
  val host: String = "",
  val port: String = "22",
  val username: String = "root",
  val authType: AuthType = AuthType.PublicKey,
)

/**
 * Editor for creating / editing an SSH profile.
 * [profileId] is null for new profiles.
 */
class ProfileEditorViewModel(profileId: String?) : ViewModel() {

  private val _form = MutableStateFlow(ProfileForm())
  val form: StateFlow<ProfileForm> = _form

  init {
    if (profileId != null) {
      // TODO: load existing profile from storage
    }
  }

  fun updateForm(updated: ProfileForm) {
    _form.value = updated
  }

  fun save() {
    val f = _form.value
    val profile = SshProfile(
      id = "p-${System.currentTimeMillis()}",
      name = f.name,
      host = f.host,
      port = f.port.toIntOrNull() ?: 22,
      username = f.username,
      authType = f.authType,
    )
    // TODO: persist via ProfileStore
  }
}

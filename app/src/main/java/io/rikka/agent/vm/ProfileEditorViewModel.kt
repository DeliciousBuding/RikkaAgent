package io.rikka.agent.vm

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.R
import io.rikka.agent.model.AuthType
import io.rikka.agent.model.SshProfile
import io.rikka.agent.storage.RoomProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

data class ProfileForm(
  val name: String = "",
  val host: String = "",
  val port: String = "22",
  val username: String = "root",
  val authType: AuthType = AuthType.PublicKey,
  val keyRef: String? = null,
  val codexMode: Boolean = false,
  val codexWorkDir: String = "",
  val codexApiKey: String = "",
)

class ProfileEditorViewModel(
  private val profileId: String?,
  private val store: RoomProfileStore,
  private val app: Application,
) : ViewModel() {

  private val _form = MutableStateFlow(ProfileForm())
  val form: StateFlow<ProfileForm> = _form

  private val _saved = MutableStateFlow(false)
  val saved: StateFlow<Boolean> = _saved

  private val _testResult = MutableStateFlow<String?>(null)
  val testResult: StateFlow<String?> = _testResult

  private val _testing = MutableStateFlow(false)
  val testing: StateFlow<Boolean> = _testing

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
            keyRef = p.keyRef,
            codexMode = p.codexMode,
            codexWorkDir = p.codexWorkDir ?: "",
            codexApiKey = p.codexApiKey ?: "",
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
      val autoName = f.name.ifBlank {
        val portSuffix = if ((f.port.toIntOrNull() ?: 22) != 22) ":${f.port}" else ""
        "${f.username.trim()}@${f.host.trim()}$portSuffix"
      }
      val profile = SshProfile(
        id = profileId ?: UUID.randomUUID().toString(),
        name = autoName,
        host = f.host.trim(),
        port = f.port.toIntOrNull() ?: 22,
        username = f.username.trim(),
        authType = f.authType,
        keyRef = f.keyRef,
        codexMode = f.codexMode,
        codexWorkDir = f.codexWorkDir.trim().ifBlank { null },
        codexApiKey = f.codexApiKey.trim().ifBlank { null },
      )
      store.upsert(profile)
      _saved.value = true
    }
  }

  /** TCP-level connection test: verifies host:port reachable + reads SSH banner. */
  fun testConnection() {
    val f = _form.value
    val host = f.host.trim()
    val port = f.port.toIntOrNull() ?: 22
    if (host.isBlank()) {
      _testResult.value = app.getString(R.string.test_host_empty)
      return
    }
    _testing.value = true
    _testResult.value = null
    viewModelScope.launch {
      _testResult.value = withContext(Dispatchers.IO) {
        try {
          Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 3000
            val banner = BufferedReader(InputStreamReader(socket.getInputStream()))
              .readLine() ?: app.getString(R.string.test_no_banner)
            app.getString(R.string.test_ok, banner)
          }
        } catch (e: java.net.ConnectException) {
          app.getString(R.string.test_refused, host, port)
        } catch (e: java.net.SocketTimeoutException) {
          app.getString(R.string.test_timeout, host)
        } catch (e: java.net.UnknownHostException) {
          app.getString(R.string.test_host_not_found)
        } catch (e: Exception) {
          app.getString(R.string.test_failed, e.message ?: "unknown")
        }
      }
      _testing.value = false
    }
  }

  fun clearTestResult() {
    _testResult.value = null
  }
}

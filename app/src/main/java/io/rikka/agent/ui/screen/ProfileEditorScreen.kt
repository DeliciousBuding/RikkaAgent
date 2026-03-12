package io.rikka.agent.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.AuthType
import io.rikka.agent.ssh.ContentUriKeyContentProvider
import io.rikka.agent.vm.ProfileEditorViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
  profileId: String?,
  onBack: () -> Unit,
  onSaved: () -> Unit,
) {
  val vm: ProfileEditorViewModel = koinViewModel { parametersOf(profileId) }
  val keyProvider: ContentUriKeyContentProvider = koinInject()
  val form by vm.form.collectAsState()
  val saved by vm.saved.collectAsState()
  var attempted by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  // SAF file picker for private key
  val keyPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri: Uri? ->
    if (uri != null) {
      // Take persistable permission so the URI survives app restarts
      context.contentResolver.takePersistableUriPermission(
        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
      )
      vm.updateForm(form.copy(keyRef = uri.toString()))
    }
  }

  LaunchedEffect(saved) {
    if (saved) onSaved()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (profileId == null) "New Profile" else "Edit Profile") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = {
        attempted = true
        vm.save()
      }) {
        Icon(Icons.Default.Check, contentDescription = "Save")
      }
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .imePadding()
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      // --- Connection section ---
      SectionLabel("Connection")
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
            value = form.name,
            onValueChange = { vm.updateForm(form.copy(name = it)) },
            label = { Text("Name") },
            placeholder = { Text("My Server") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          OutlinedTextField(
            value = form.host,
            onValueChange = { vm.updateForm(form.copy(host = it)) },
            label = { Text("Host") },
            placeholder = { Text("192.168.1.100") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            isError = attempted && form.host.isBlank(),
            supportingText = if (attempted && form.host.isBlank()) {
              { Text("Host is required") }
            } else null,
          )

          Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = form.port,
              onValueChange = { vm.updateForm(form.copy(port = it)) },
              label = { Text("Port") },
              singleLine = true,
              modifier = Modifier.width(120.dp),
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
              ),
              isError = attempted && (form.port.toIntOrNull()?.let { it !in 1..65535 } ?: true),
              supportingText = if (attempted && (form.port.toIntOrNull()?.let { it !in 1..65535 } ?: true)) {
                { Text("1–65535") }
              } else null,
            )
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
              value = form.username,
              onValueChange = { vm.updateForm(form.copy(username = it)) },
              label = { Text("Username") },
              placeholder = { Text("root") },
              singleLine = true,
              modifier = Modifier.weight(1f),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
              isError = attempted && form.username.isBlank(),
              supportingText = if (attempted && form.username.isBlank()) {
                { Text("Required") }
              } else null,
            )
          }
        }
      }

      // --- Authentication section ---
      SectionLabel("Authentication")
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          AuthTypeDropdown(
            selected = form.authType,
            onSelect = { vm.updateForm(form.copy(authType = it)) },
          )

          // Private key file selector (visible when PublicKey auth)
          if (form.authType == AuthType.PublicKey) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                FilledTonalButton(
                  onClick = { keyPickerLauncher.launch(arrayOf("*/*")) },
                ) {
                  Text("Select File")
                }
                OutlinedButton(
                  onClick = {
                    val clip = clipboardManager.getText()?.text ?: ""
                    if (clip.contains("PRIVATE KEY") || clip.startsWith("-----BEGIN")) {
                      val ref = keyProvider.savePastedKey(clip)
                      vm.updateForm(form.copy(keyRef = ref))
                    }
                  },
                ) {
                  Text("Paste Key")
                }
              }
              if (form.keyRef != null) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = extractKeyDisplayName(form.keyRef!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  IconButton(
                    onClick = {
                      keyProvider.deleteKey(form.keyRef!!)
                      vm.updateForm(form.copy(keyRef = null))
                    },
                    modifier = Modifier.size(24.dp),
                  ) {
                    Icon(
                      Icons.Default.Close,
                      contentDescription = "Remove key",
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              } else {
                Text(
                  text = "No key selected (will prompt for password)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(80.dp)) // FAB clearance
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 4.dp),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthTypeDropdown(
  selected: AuthType,
  onSelect: (AuthType) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
  ) {
    OutlinedTextField(
      value = selected.name,
      onValueChange = {},
      readOnly = true,
      label = { Text("Auth Type") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        .fillMaxWidth(),
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      AuthType.entries.forEach { type ->
        DropdownMenuItem(
          text = { Text(type.name) },
          onClick = {
            onSelect(type)
            expanded = false
          },
        )
      }
    }
  }
}

/** Extract a readable display name from a key URI string (content:// or internal-key://). */
private fun extractKeyDisplayName(uriString: String): String {
  if (uriString.startsWith("internal-key://")) return "Pasted key"
  val uri = Uri.parse(uriString)
  val lastSegment = uri.lastPathSegment ?: return uriString
  // SAF URIs often have "primary:path/to/file" format
  return lastSegment.substringAfterLast('/')
    .substringAfterLast(':')
    .ifBlank { lastSegment }
}

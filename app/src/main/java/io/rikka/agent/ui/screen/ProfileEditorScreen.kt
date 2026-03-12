package io.rikka.agent.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.AuthType
import io.rikka.agent.vm.ProfileEditorViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
  profileId: String?,
  onBack: () -> Unit,
  onSaved: () -> Unit,
) {
  val vm: ProfileEditorViewModel = koinViewModel { parametersOf(profileId) }
  val form by vm.form.collectAsState()
  val saved by vm.saved.collectAsState()
  var attempted by remember { mutableStateOf(false) }

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
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Spacer(modifier = Modifier.height(4.dp))

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

      OutlinedTextField(
        value = form.port,
        onValueChange = { vm.updateForm(form.copy(port = it)) },
        label = { Text("Port") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
      )

      OutlinedTextField(
        value = form.username,
        onValueChange = { vm.updateForm(form.copy(username = it)) },
        label = { Text("Username") },
        placeholder = { Text("root") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        isError = attempted && form.username.isBlank(),
        supportingText = if (attempted && form.username.isBlank()) {
          { Text("Username is required") }
        } else null,
      )

      AuthTypeDropdown(
        selected = form.authType,
        onSelect = { vm.updateForm(form.copy(authType = it)) },
      )

      Spacer(modifier = Modifier.height(80.dp)) // FAB clearance
    }
  }
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

package io.rikka.agent.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rikka.agent.vm.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
) {
  val vm: SettingsViewModel = koinViewModel()
  val theme by vm.theme.collectAsState()
  val defaultShell by vm.defaultShell.collectAsState()
  var showThemePicker by remember { mutableStateOf(false) }

  if (showThemePicker) {
    ThemePickerDialog(
      current = theme,
      onSelect = { vm.setTheme(it); showThemePicker = false },
      onDismiss = { showThemePicker = false },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState()),
    ) {
      SectionHeader("General")
      SettingsItem(
        title = "Theme",
        subtitle = theme.replaceFirstChar { it.uppercase() },
        onClick = { showThemePicker = true },
      )
      SettingsItem(
        title = "Default shell",
        subtitle = defaultShell,
        onClick = { /* TODO: shell picker */ },
      )

      SectionHeader("Security")
      SettingsItem(
        title = "Known hosts",
        subtitle = "Manage trusted server fingerprints",
        onClick = { /* TODO */ },
      )

      SectionHeader("About")
      SettingsItem(
        title = "Version",
        subtitle = "0.1.0-dev",
        onClick = {},
      )
      SettingsItem(
        title = "Open source licenses",
        subtitle = "View third-party licenses",
        onClick = { /* TODO */ },
      )
    }
  }
}

@Composable
private fun ThemePickerDialog(
  current: String,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val options = listOf("system", "light", "dark")
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Theme") },
    text = {
      Column {
        options.forEach { option ->
          ListItem(
            headlineContent = { Text(option.replaceFirstChar { it.uppercase() }) },
            leadingContent = {
              RadioButton(
                selected = current == option,
                onClick = { onSelect(option) },
              )
            },
            modifier = Modifier.clickable { onSelect(option) },
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun SectionHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
  )
}

@Composable
private fun SettingsItem(
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(subtitle) },
    modifier = Modifier.clickable(onClick = onClick),
  )
}

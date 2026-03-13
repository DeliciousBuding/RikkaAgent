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
import androidx.compose.material3.Switch
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
import io.rikka.agent.BuildConfig
import io.rikka.agent.R
import io.rikka.agent.vm.SettingsViewModel
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onOpenKnownHosts: () -> Unit = {},
  onOpenAbout: () -> Unit = {},
) {
  val vm: SettingsViewModel = koinViewModel()
  val theme by vm.theme.collectAsState()
  val defaultShell by vm.defaultShell.collectAsState()
  val enableMermaid by vm.enableMermaid.collectAsState()
  var showThemePicker by remember { mutableStateOf(false) }
  var showShellPicker by remember { mutableStateOf(false) }

  if (showThemePicker) {
    ThemePickerDialog(
      current = theme,
      onSelect = { vm.setTheme(it); showThemePicker = false },
      onDismiss = { showThemePicker = false },
    )
  }

  if (showShellPicker) {
    ShellPickerDialog(
      current = defaultShell,
      onSelect = { vm.setDefaultShell(it); showShellPicker = false },
      onDismiss = { showShellPicker = false },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
      SectionHeader(stringResource(R.string.section_general))
      SettingsItem(
        title = stringResource(R.string.theme),
        subtitle = theme.replaceFirstChar { it.uppercase() },
        onClick = { showThemePicker = true },
      )
      SettingsItem(
        title = stringResource(R.string.default_shell),
        subtitle = defaultShell,
        onClick = { showShellPicker = true },
      )
      SettingsSwitchItem(
        title = stringResource(R.string.enable_mermaid_rendering),
        subtitle = stringResource(R.string.enable_mermaid_rendering_subtitle),
        checked = enableMermaid,
        onCheckedChange = vm::setEnableMermaid,
      )

      SectionHeader(stringResource(R.string.section_security))
      SettingsItem(
        title = stringResource(R.string.known_hosts),
        subtitle = stringResource(R.string.known_hosts_subtitle),
        onClick = onOpenKnownHosts,
      )

      SectionHeader(stringResource(R.string.section_about))
      SettingsItem(
        title = stringResource(R.string.version),
        subtitle = BuildConfig.VERSION_NAME,
        onClick = onOpenAbout,
      )
      SettingsItem(
        title = stringResource(R.string.license),
        subtitle = stringResource(R.string.apache_license),
        onClick = onOpenAbout,
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
  val options = listOf("system", "light", "dark", "amoled")
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.theme)) },
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
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
  )
}

@Composable
private fun ShellPickerDialog(
  current: String,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val options = listOf("/bin/bash", "/bin/sh", "/bin/zsh", "/bin/fish")
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.default_shell_title)) },
    text = {
      Column {
        options.forEach { option ->
          ListItem(
            headlineContent = { Text(option) },
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
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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

@Composable
private fun SettingsSwitchItem(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(subtitle) },
    trailingContent = {
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
      )
    },
    modifier = Modifier.clickable { onCheckedChange(!checked) },
  )
}

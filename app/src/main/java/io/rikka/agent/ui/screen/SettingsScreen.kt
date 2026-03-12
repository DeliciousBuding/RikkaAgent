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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
) {
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
        subtitle = "System default",
        onClick = { /* TODO: Theme picker */ },
      )
      SettingsItem(
        title = "Default shell",
        subtitle = "/bin/bash",
        onClick = { /* TODO */ },
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

package io.rikka.agent.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rikka.agent.BuildConfig
import io.rikka.agent.R
import io.rikka.agent.ui.theme.ChatFont
import io.rikka.agent.ui.theme.PresetTheme
import io.rikka.agent.vm.SettingsViewModel
import lucide.icons.Lucide
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
  val presetThemeId by vm.presetTheme.collectAsState()
  val dynamicColor by vm.dynamicColor.collectAsState()
  val defaultShell by vm.defaultShell.collectAsState()
  val enableMermaid by vm.enableMermaid.collectAsState()
  val showUserAvatar by vm.showUserAvatar.collectAsState()
  val showModelIcon by vm.showModelIcon.collectAsState()
  val chatFontId by vm.chatFont.collectAsState()
  val fontSizeRatio by vm.fontSizeRatio.collectAsState()
  var showThemePicker by remember { mutableStateOf(false) }
  var showPresetThemePicker by remember { mutableStateOf(false) }
  var showShellPicker by remember { mutableStateOf(false) }
  var showChatFontPicker by remember { mutableStateOf(false) }

  if (showThemePicker) {
    ThemePickerDialog(
      current = theme,
      onSelect = { vm.setTheme(it); showThemePicker = false },
      onDismiss = { showThemePicker = false },
    )
  }

  if (showPresetThemePicker) {
    PresetThemePickerDialog(
      currentId = presetThemeId,
      onSelect = { vm.setPresetTheme(it); showPresetThemePicker = false },
      onDismiss = { showPresetThemePicker = false },
    )
  }

  if (showShellPicker) {
    ShellPickerDialog(
      current = defaultShell,
      onSelect = { vm.setDefaultShell(it); showShellPicker = false },
      onDismiss = { showShellPicker = false },
    )
  }

  if (showChatFontPicker) {
    ChatFontPickerDialog(
      currentId = chatFontId,
      onSelect = { vm.setChatFont(it); showChatFontPicker = false },
      onDismiss = { showChatFontPicker = false },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back))
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
        title = "Preset Theme",
        subtitle = PresetTheme.findById(presetThemeId).displayName,
        onClick = { showPresetThemePicker = true },
      )
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        SettingsSwitchItem(
          title = stringResource(R.string.dynamic_color),
          subtitle = stringResource(R.string.dynamic_color_subtitle),
          checked = dynamicColor,
          onCheckedChange = vm::setDynamicColor,
        )
      }
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
      SettingsSwitchItem(
        title = stringResource(R.string.show_user_avatar),
        subtitle = stringResource(R.string.show_user_avatar_subtitle),
        checked = showUserAvatar,
        onCheckedChange = vm::setShowUserAvatar,
      )
      SettingsSwitchItem(
        title = stringResource(R.string.show_model_icon),
        subtitle = stringResource(R.string.show_model_icon_subtitle),
        checked = showModelIcon,
        onCheckedChange = vm::setShowModelIcon,
      )

      SectionHeader(stringResource(R.string.section_chat_font))
      SettingsItem(
        title = stringResource(R.string.chat_font),
        subtitle = ChatFont.findById(chatFontId).displayName,
        onClick = { showChatFontPicker = true },
      )
      FontSizeRatioItem(
        ratio = fontSizeRatio,
        onRatioChange = vm::setFontSizeRatio,
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
private fun PresetThemePickerDialog(
  currentId: String,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val presets = PresetTheme.all
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Preset Theme") },
    text = {
      Column {
        presets.forEach { preset ->
          ListItem(
            headlineContent = { Text(preset.displayName) },
            supportingContent = {
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val lightPrimary = preset.lightScheme.primary
                val lightSecondary = preset.lightScheme.secondary
                val lightTertiary = preset.lightScheme.tertiary
                val darkPrimary = preset.darkScheme.primary
                Surface(
                  modifier = Modifier.size(16.dp),
                  shape = CircleShape,
                  color = lightPrimary,
                ) {}
                Surface(
                  modifier = Modifier.size(16.dp),
                  shape = CircleShape,
                  color = lightSecondary,
                ) {}
                Surface(
                  modifier = Modifier.size(16.dp),
                  shape = CircleShape,
                  color = lightTertiary,
                ) {}
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                  modifier = Modifier.size(16.dp),
                  shape = CircleShape,
                  color = darkPrimary,
                ) {}
              }
            },
            leadingContent = {
              RadioButton(
                selected = currentId == preset.id,
                onClick = { onSelect(preset.id) },
              )
            },
            modifier = Modifier.clickable { onSelect(preset.id) },
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
private fun ChatFontPickerDialog(
  currentId: String,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val fonts = ChatFont.entries
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.chat_font_title)) },
    text = {
      Column {
        fonts.forEach { font ->
          ListItem(
            headlineContent = { Text(font.displayName) },
            supportingContent = {
              Text(
                text = stringResource(R.string.chat_font_preview),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = font.fontFamily),
              )
            },
            leadingContent = {
              RadioButton(
                selected = currentId == font.id,
                onClick = { onSelect(font.id) },
              )
            },
            modifier = Modifier.clickable { onSelect(font.id) },
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
private fun FontSizeRatioItem(
  ratio: Float,
  onRatioChange: (Float) -> Unit,
) {
  var sliderValue by remember { mutableFloatStateOf(ratio) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.font_size_ratio),
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        text = "%.1fx".format(sliderValue),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.font_size_small),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onRatioChange(sliderValue) },
        valueRange = 0.8f..1.5f,
        steps = 6,
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
      )
      Text(
        text = stringResource(R.string.font_size_large),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

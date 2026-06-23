package io.rikka.agent.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rikka.agent.R
import io.rikka.agent.vm.SettingsViewModel
import lucide.icons.Lucide
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(
  onBack: () -> Unit,
) {
  val vm: SettingsViewModel = koinViewModel()
  val bubbleOpacity by vm.bubbleOpacity.collectAsStateWithLifecycle()
  val showAssistantBubble by vm.showAssistantBubble.collectAsStateWithLifecycle()
  val showUserAvatar by vm.showUserAvatar.collectAsStateWithLifecycle()
  val showModelIcon by vm.showModelIcon.collectAsStateWithLifecycle()
  val showTimestamp by vm.showTimestamp.collectAsStateWithLifecycle()
  val fontSizeRatio by vm.fontSizeRatio.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.display_settings)) },
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
      // -- Bubbles --
      DisplaySectionHeader(stringResource(R.string.section_display))

      BubbleOpacityItem(
        opacity = bubbleOpacity,
        onOpacityChange = vm::setBubbleOpacity,
      )

      DisplaySwitchItem(
        title = stringResource(R.string.show_assistant_bubble),
        subtitle = stringResource(R.string.show_assistant_bubble_subtitle),
        checked = showAssistantBubble,
        onCheckedChange = vm::setShowAssistantBubble,
      )

      DisplaySwitchItem(
        title = stringResource(R.string.show_user_avatar),
        subtitle = stringResource(R.string.show_user_avatar_subtitle),
        checked = showUserAvatar,
        onCheckedChange = vm::setShowUserAvatar,
      )

      DisplaySwitchItem(
        title = stringResource(R.string.show_model_icon),
        subtitle = stringResource(R.string.show_model_icon_subtitle),
        checked = showModelIcon,
        onCheckedChange = vm::setShowModelIcon,
      )

      DisplaySwitchItem(
        title = stringResource(R.string.show_timestamp),
        subtitle = stringResource(R.string.show_timestamp_subtitle),
        checked = showTimestamp,
        onCheckedChange = vm::setShowTimestamp,
      )

      // -- Font --
      DisplaySectionHeader(stringResource(R.string.section_chat_font))

      FontSizeRatioItem(
        ratio = fontSizeRatio,
        onRatioChange = vm::setFontSizeRatio,
      )
    }
  }
}

// ---------------------------------------------------------------------------
// Private composables
// ---------------------------------------------------------------------------

@Composable
private fun BubbleOpacityItem(
  opacity: Float,
  onOpacityChange: (Float) -> Unit,
) {
  var sliderValue by remember { mutableFloatStateOf(opacity) }
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
        text = stringResource(R.string.bubble_opacity),
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        text = "%.0f%%".format(sliderValue * 100),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Slider(
      value = sliderValue,
      onValueChange = { sliderValue = it },
      onValueChangeFinished = { onOpacityChange(sliderValue) },
      valueRange = 0.5f..1.0f,
      steps = 9,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp),
    )
  }
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
private fun DisplaySectionHeader(title: String) {
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
private fun DisplaySwitchItem(
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

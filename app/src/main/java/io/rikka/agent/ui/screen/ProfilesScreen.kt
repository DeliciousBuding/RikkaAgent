package io.rikka.agent.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Server
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rikka.agent.model.AuthType
import io.rikka.agent.model.SshProfile
import io.rikka.agent.R
import io.rikka.agent.vm.ProfilesViewModel
import io.rikka.agent.ui.components.MeshGradientBackground
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfilesScreen(
  onOpenSession: (profileId: String) -> Unit,
  onEditProfile: (profileId: String?) -> Unit,
  onOpenSettings: () -> Unit,
) {
  val vm: ProfilesViewModel = koinViewModel()
  val profiles by vm.profiles.collectAsState()

  MeshGradientBackground {
    Box(modifier = Modifier.fillMaxSize()) {
      // Settings icon top-right
      IconButton(
        onClick = onOpenSettings,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
      ) {
        Icon(Lucide.Settings, contentDescription = stringResource(R.string.settings))
      }

      // Title top-left
      androidx.compose.foundation.text.BasicText(
        text = stringResource(R.string.app_name),
        style = androidx.compose.ui.text.TextStyle(
          fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
          fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
          fontSize = 18.sp,
          lineHeight = 24.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        ),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp),
      )

      // Content
      if (profiles.isEmpty()) {
        EmptyState(
          modifier = Modifier.fillMaxSize(),
          onAdd = { onEditProfile(null) },
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(profiles, key = { it.id }) { profile ->
            val dismissState = rememberSwipeToDismissBoxState(
              confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                  vm.delete(profile.id)
                  true
                } else false
              },
            )
            SwipeToDismissBox(
              state = dismissState,
              backgroundContent = {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                  contentAlignment = Alignment.CenterEnd,
                ) {
                  Icon(
                    Lucide.Trash2,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              },
              enableDismissFromStartToEnd = false,
            ) {
              ProfileCard(
                profile = profile,
                onClick = { onOpenSession(profile.id) },
                onEdit = { onEditProfile(profile.id) },
                onDuplicate = { vm.duplicate(profile) },
              )
            }
          }
        }
      }

      // FAB
      FloatingActionButton(
        onClick = { onEditProfile(null) },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
      ) {
        Icon(Lucide.Plus, contentDescription = stringResource(R.string.new_profile))
      }
    } // Box
  } // MeshGradientBackground
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileCard(
  profile: SshProfile,
  onClick: () -> Unit,
  onEdit: () -> Unit,
  onDuplicate: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = onClick,
        onLongClick = { showMenu = true },
      ),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface,
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Letter avatar
      val displayChar = profile.name.firstOrNull()?.uppercaseChar()
        ?: profile.host.firstOrNull()?.uppercaseChar()
        ?: '?'
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = displayChar.toString(),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = profile.name.ifBlank { "${profile.username}@${profile.host}" },
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "${profile.username}@${profile.host}:${profile.port}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      // Auth type label
      Text(
        text = if (profile.authType == AuthType.PublicKey) "Key" else "Pass",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
          .clip(MaterialTheme.shapes.small)
          .background(MaterialTheme.colorScheme.secondaryContainer)
          .padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }

    DropdownMenu(
      expanded = showMenu,
      onDismissRequest = { showMenu = false },
    ) {
      DropdownMenuItem(
        text = { Text(stringResource(R.string.edit)) },
        onClick = { showMenu = false; onEdit() },
      )
      DropdownMenuItem(
        text = { Text(stringResource(R.string.duplicate)) },
        onClick = { showMenu = false; onDuplicate() },
      )
    }
  }
}

@Composable
private fun EmptyState(
  modifier: Modifier = Modifier,
  onAdd: () -> Unit,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      imageVector = Lucide.Server,
      contentDescription = null,
      modifier = Modifier.size(48.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = stringResource(R.string.no_profiles_title),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.no_profiles_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
  }
}

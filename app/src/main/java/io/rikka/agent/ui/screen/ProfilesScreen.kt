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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.AuthType
import io.rikka.agent.model.SshProfile
import io.rikka.agent.ui.R
import io.rikka.agent.vm.ProfilesViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
  onOpenSession: (profileId: String) -> Unit,
  onEditProfile: (profileId: String?) -> Unit,
  onOpenSettings: () -> Unit,
) {
  val vm: ProfilesViewModel = koinViewModel()
  val profiles by vm.profiles.collectAsState()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      LargeTopAppBar(
        title = { Text("Rikka Agent") },
        actions = {
          IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { onEditProfile(null) }) {
        Icon(Icons.Default.Add, contentDescription = "New profile")
      }
    },
  ) { innerPadding ->
    if (profiles.isEmpty()) {
      EmptyState(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        onAdd = { onEditProfile(null) },
      )
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                  Icons.Default.Delete,
                  contentDescription = "Delete",
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
  }
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
        Spacer(modifier = Modifier.height(2.dp))
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
        text = { Text("Edit") },
        onClick = { showMenu = false; onEdit() },
      )
      DropdownMenuItem(
        text = { Text("Duplicate") },
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
    Text(
      text = "No SSH profiles yet",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Tap + to add your first server",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
  return PaddingValues(
    start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
      other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    top = calculateTopPadding() + other.calculateTopPadding(),
    end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
      other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    bottom = calculateBottomPadding() + other.calculateBottomPadding(),
  )
}

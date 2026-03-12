package io.rikka.agent.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.SshProfile
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
          ProfileCard(
            profile = profile,
            onClick = { onOpenSession(profile.id) },
            onEdit = { onEditProfile(profile.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun ProfileCard(
  profile: SshProfile,
  onClick: () -> Unit,
  onEdit: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
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
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = profile.authType.name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
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

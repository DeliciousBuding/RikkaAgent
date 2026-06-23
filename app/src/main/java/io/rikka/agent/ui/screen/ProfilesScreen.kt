package io.rikka.agent.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rikka.agent.R
import io.rikka.agent.model.AuthType
import io.rikka.agent.model.ProfileGroup
import io.rikka.agent.model.ProfileSearchFilter
import io.rikka.agent.model.ProfileTemplate
import io.rikka.agent.model.SshProfile
import io.rikka.agent.vm.ProfilesViewModel
import lucide.icons.Lucide
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
  onOpenSession: (profileId: String) -> Unit,
  onEditProfile: (profileId: String?) -> Unit,
  onOpenSettings: () -> Unit,
) {
  val vm: ProfilesViewModel = koinViewModel()
  val profiles by vm.profiles.collectAsStateWithLifecycle()
  val filteredProfiles by vm.filteredProfiles.collectAsStateWithLifecycle()
  val searchFilter by vm.searchFilter.collectAsStateWithLifecycle()
  val importResult by vm.importResult.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val context = LocalContext.current

  var showSearch by remember { mutableStateOf(false) }
  var showTemplates by remember { mutableStateOf(false) }
  var showImportDialog by remember { mutableStateOf(false) }
  var importJson by remember { mutableStateOf("") }

  // SAF file picker for import
  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri: android.net.Uri? ->
    if (uri != null) {
      vm.importFromUri(context, uri)
    }
  }

  // SAF create document for export
  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json"),
  ) { uri: android.net.Uri? ->
    if (uri != null) {
      // Export will be handled via coroutine in a LaunchedEffect or callback
    }
  }

  // Show import result dialog
  importResult?.let { result ->
    AlertDialog(
      onDismissRequest = { vm.clearImportResult() },
      title = { Text("Import Result") },
      text = {
        Column {
          Text("Imported: ${result.imported}")
          if (result.skipped > 0) Text("Skipped (ID conflict): ${result.skipped}")
          if (result.errors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Errors:", style = MaterialTheme.typography.labelMedium)
            result.errors.forEach { error ->
              Text("- $error", style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { vm.clearImportResult() }) {
          Text("OK")
        }
      },
    )
  }

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      LargeTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
          // Search toggle
          IconButton(onClick = { showSearch = !showSearch }) {
            Icon(
              if (showSearch) Lucide.SearchX else Lucide.Search,
              contentDescription = "Search",
            )
          }
          // Import/Export menu
          var showIoMenu by remember { mutableStateOf(false) }
          IconButton(onClick = { showIoMenu = true }) {
            Icon(Lucide.MoreVertical, contentDescription = "More")
            DropdownMenu(
              expanded = showIoMenu,
              onDismissRequest = { showIoMenu = false },
            ) {
              DropdownMenuItem(
                text = { Text("Import from JSON") },
                onClick = {
                  showIoMenu = false
                  importLauncher.launch(arrayOf("application/json"))
                },
                leadingIcon = { Icon(Lucide.Upload, contentDescription = null) },
              )
              DropdownMenuItem(
                text = { Text("Export all profiles") },
                onClick = {
                  showIoMenu = false
                  // Trigger export via share intent
                  exportLauncher.launch("rikkaagent-profiles.json")
                },
                leadingIcon = { Icon(Lucide.Download, contentDescription = null) },
              )
              DropdownMenuItem(
                text = { Text("Templates") },
                onClick = {
                  showIoMenu = false
                  showTemplates = true
                },
                leadingIcon = { Icon(Lucide.FileText, contentDescription = null) },
              )
            }
          }
          IconButton(onClick = onOpenSettings) {
            Icon(Lucide.Settings, contentDescription = stringResource(R.string.settings))
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { onEditProfile(null) }) {
        Icon(Lucide.Plus, contentDescription = stringResource(R.string.new_profile))
      }
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize(),
    ) {
      // Search bar
      if (showSearch) {
        SearchBar(
          filter = searchFilter,
          onFilterChange = { vm.updateFilter(it) },
          onClear = { vm.clearFilter() },
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }

      // Group filter chips
      if (!showSearch && profiles.isNotEmpty()) {
        GroupFilterRow(
          selectedGroup = searchFilter.group,
          onSelect = { group ->
            vm.updateFilter(searchFilter.copy(group = if (group == searchFilter.group) null else group))
          },
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
      }

      // Profile list
      val displayProfiles = if (searchFilter.isActive) filteredProfiles else profiles
      if (displayProfiles.isEmpty()) {
        EmptyState(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
          onAdd = { onEditProfile(null) },
          isFiltered = searchFilter.isActive,
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(displayProfiles, key = { it.id }) { profile ->
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
    }

    // Templates bottom sheet
    if (showTemplates) {
      TemplatesBottomSheet(
        templates = vm.templates,
        onDismiss = { showTemplates = false },
        onSelect = { template ->
          val id = vm.createFromTemplate(template)
          showTemplates = false
          onEditProfile(id)
        },
      )
    }
  }
}

@Composable
private fun SearchBar(
  filter: ProfileSearchFilter,
  onFilterChange: (ProfileSearchFilter) -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    OutlinedTextField(
      value = filter.query,
      onValueChange = { onFilterChange(filter.copy(query = it)) },
      label = { Text("Search profiles...") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      trailingIcon = {
        if (filter.query.isNotEmpty()) {
          IconButton(onClick = { onFilterChange(filter.copy(query = "")) }) {
            Icon(Lucide.X, contentDescription = "Clear search")
          }
        }
      },
    )
    if (filter.isActive) {
      Spacer(Modifier.height(4.dp))
      Text(
        text = "Filtering by: " + buildList {
          if (filter.query.isNotBlank()) add("text")
          if (filter.group != null) add("group=${filter.group.name}")
          if (filter.tags.isNotEmpty()) add("tags=${filter.tags.joinToString()}")
        }.joinToString(", "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupFilterRow(
  selectedGroup: ProfileGroup?,
  onSelect: (ProfileGroup) -> Unit,
  modifier: Modifier = Modifier,
) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    ProfileGroup.entries.forEach { group ->
      FilterChip(
        selected = group == selectedGroup,
        onClick = { onSelect(group) },
        label = {
          Text(
            when (group) {
              ProfileGroup.None -> "All"
              ProfileGroup.Development -> "Dev"
              ProfileGroup.Production -> "Prod"
              ProfileGroup.Testing -> "Test"
              ProfileGroup.Personal -> "Personal"
            }
          )
        },
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("UnusedParameter")
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
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
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
            .background(
              when (profile.group) {
                ProfileGroup.Production -> MaterialTheme.colorScheme.errorContainer
                ProfileGroup.Testing -> MaterialTheme.colorScheme.tertiaryContainer
                ProfileGroup.Development -> MaterialTheme.colorScheme.primaryContainer
                ProfileGroup.Personal -> MaterialTheme.colorScheme.secondaryContainer
                ProfileGroup.None -> MaterialTheme.colorScheme.surfaceVariant
              }
            ),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = displayChar.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = when (profile.group) {
              ProfileGroup.Production -> MaterialTheme.colorScheme.onErrorContainer
              ProfileGroup.Testing -> MaterialTheme.colorScheme.onTertiaryContainer
              ProfileGroup.Development -> MaterialTheme.colorScheme.onPrimaryContainer
              ProfileGroup.Personal -> MaterialTheme.colorScheme.onSecondaryContainer
              ProfileGroup.None -> MaterialTheme.colorScheme.onSurfaceVariant
            },
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

      // Tags and group row
      if (profile.tags.isNotEmpty() || profile.group != ProfileGroup.None) {
        Spacer(Modifier.height(8.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          // Group badge
          if (profile.group != ProfileGroup.None) {
            Text(
              text = when (profile.group) {
                ProfileGroup.Development -> "Dev"
                ProfileGroup.Production -> "Prod"
                ProfileGroup.Testing -> "Test"
                ProfileGroup.Personal -> "Personal"
                ProfileGroup.None -> ""
              },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
          // Tag chips
          profile.tags.forEach { tag ->
            Text(
              text = tag,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
      }
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
  isFiltered: Boolean = false,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = if (isFiltered) "No matching profiles" else stringResource(R.string.no_profiles_title),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = if (isFiltered) "Try adjusting your search or filter criteria."
             else stringResource(R.string.no_profiles_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatesBottomSheet(
  templates: List<ProfileTemplate>,
  onDismiss: () -> Unit,
  onSelect: (ProfileTemplate) -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.padding(16.dp),
    ) {
      Text(
        text = "Profile Templates",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 16.dp),
      )
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(templates) { template ->
          Card(
            onClick = { onSelect(template) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
            ) {
              Text(
                text = template.name,
                style = MaterialTheme.typography.titleMedium,
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (template.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  template.tags.forEach { tag ->
                    Text(
                      text = tag,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
      Spacer(Modifier.height(32.dp)) // Bottom sheet clearance
    }
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

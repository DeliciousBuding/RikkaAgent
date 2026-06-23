package io.rikka.agent.vm

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.model.ProfileGroup
import io.rikka.agent.model.ProfileImportExport
import io.rikka.agent.model.ProfileImportResult
import io.rikka.agent.model.ProfileSearchFilter
import io.rikka.agent.model.ProfileTemplate
import io.rikka.agent.model.ProfileTemplates
import io.rikka.agent.model.SshProfile
import io.rikka.agent.model.filterAndSort
import io.rikka.agent.storage.RoomProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for the SSH profiles list screen.
 *
 * ## Responsibilities
 * - Exposes the full list of saved SSH profiles as an observable [StateFlow].
 * - Provides profile deletion and duplication operations.
 * - Supports search, filtering by group/tags, and sorting.
 * - Handles JSON import/export of profiles.
 * - Exposes available profile templates.
 *
 * ## Thread Safety
 * - The [profiles] flow is created via [StateFlow.stateIn] with [SharingStarted.WhileSubscribed],
 *   which is safe for multi-collector scenarios.
 * - All mutable UI-facing state is held in [MutableStateFlow] instances, safe for concurrent access.
 * - [delete], [duplicate], [importProfiles], and [exportProfiles] launch coroutines
 *   in [viewModelScope]; the underlying [RoomProfileStore] Room DAO operations are inherently thread-safe.
 *
 * ## Exposed State
 * | StateFlow          | Type                              | Description                                    |
 * |--------------------|-----------------------------------|------------------------------------------------|
 * | [profiles]         | `StateFlow<List<SshProfile>>`     | Observable list of all saved SSH profiles.      |
 * | [filteredProfiles] | `StateFlow<List<SshProfile>>`     | Profiles matching the current [searchFilter].   |
 * | [searchFilter]     | `StateFlow<ProfileSearchFilter>`  | Current search/filter criteria.                 |
 * | [templates]        | `List<ProfileTemplate>`           | Available profile templates (static).           |
 * | [importResult]     | `StateFlow<ProfileImportResult?>` | Result of the last import operation.            |
 *
 * @param store The [RoomProfileStore] for profile persistence.
 * @param app   Android [Application] for content resolver access (import/export).
 */
class ProfilesViewModel(
  private val store: RoomProfileStore,
  private val app: Application,
) : ViewModel() {

  /** Backing field for the search filter. */
  private val _searchFilter = MutableStateFlow(ProfileSearchFilter())
  /** Current search/filter criteria. */
  val searchFilter: StateFlow<ProfileSearchFilter> = _searchFilter

  /** Backing field for the import result. */
  private val _importResult = MutableStateFlow<ProfileImportResult?>(null)
  /** Result of the last import operation, or null. */
  val importResult: StateFlow<ProfileImportResult?> = _importResult

  /**
   * Observable list of all saved SSH profiles.
   *
   * Uses [SharingStarted.WhileSubscribed] with a 5-second replay timeout so the
   * upstream Flow stops collecting when no UI subscribers are active.
   */
  val profiles: StateFlow<List<SshProfile>> = store.observeProfiles()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /**
   * Profiles matching the current [searchFilter].
   *
   * Combines the full profile list with the active filter to produce
   * a filtered and sorted result set.
   */
  val filteredProfiles: StateFlow<List<SshProfile>> =
    combine(profiles, _searchFilter) { all, filter ->
      all.filterAndSort(filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** Available profile templates. */
  val templates: List<ProfileTemplate> = ProfileTemplates.templates

  /** All distinct tags across all profiles. */
  val allTags: StateFlow<List<String>> = profiles
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    .let { profileFlow ->
      combine(profileFlow, MutableStateFlow(Unit)) { all, _ ->
        all.flatMap { it.tags }.distinct().sorted()
      }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

  /**
   * Update the search filter.
   *
   * @param filter The new [ProfileSearchFilter].
   */
  fun updateFilter(filter: ProfileSearchFilter) {
    _searchFilter.value = filter
  }

  /**
   * Clear the search filter (reset to default).
   */
  fun clearFilter() {
    _searchFilter.value = ProfileSearchFilter()
  }

  /**
   * Delete a profile by its ID.
   *
   * @param profileId The ID of the profile to delete.
   */
  fun delete(profileId: String) {
    viewModelScope.launch { store.delete(profileId) }
  }

  /**
   * Duplicate an existing profile with a new ID and " (copy)" appended to the name.
   *
   * @param profile The [SshProfile] to duplicate.
   */
  fun duplicate(profile: SshProfile) {
    viewModelScope.launch {
      store.upsert(
        profile.copy(
          id = UUID.randomUUID().toString(),
          name = "${profile.name} (copy)",
        ),
      )
    }
  }

  /**
   * Create a new profile from a template.
   *
   * The template's [ProfileTemplate.toProfile] is used to generate a
   * base profile, which is then saved to the store.
   *
   * @param template The template to instantiate.
   * @return The new profile's ID.
   */
  fun createFromTemplate(template: ProfileTemplate): String {
    val id = UUID.randomUUID().toString()
    viewModelScope.launch {
      store.upsert(template.toProfile(id))
    }
    return id
  }

  /**
   * Export all profiles (or a filtered subset) to a JSON string.
   *
   * @param profilesToExport The profiles to export. Defaults to the current full list.
   * @return A formatted JSON string.
   */
  suspend fun exportProfiles(
    profilesToExport: List<SshProfile> = profiles.value,
  ): String {
    return ProfileImportExport.exportToJson(profilesToExport)
  }

  /**
   * Import profiles from a JSON string.
   *
   * Parses the JSON, validates profiles, assigns new IDs, and inserts
   * them into the store.
   *
   * @param jsonStr The JSON string to import.
   */
  fun importProfiles(jsonStr: String) {
    viewModelScope.launch {
      val existingIds = store.allIds()
      val (valid, result) = ProfileImportExport.importFromJson(jsonStr, existingIds)
        ?: run {
          _importResult.value = ProfileImportResult(
            imported = 0,
            skipped = 0,
            errors = listOf("Failed to parse JSON. Ensure the file is a valid profile export."),
          )
          return@launch
        }

      for (profile in valid) {
        store.upsert(profile)
      }
      _importResult.value = result
    }
  }

  /**
   * Import profiles from a content URI (Android SAF).
   *
   * @param context The Android context.
   * @param uri     The content URI to read from.
   */
  fun importFromUri(context: Context, uri: Uri) {
    viewModelScope.launch {
      try {
        val jsonStr = context.contentResolver.openInputStream(uri)?.use { stream ->
          stream.bufferedReader().readText()
        } ?: run {
          _importResult.value = ProfileImportResult(
            imported = 0,
            skipped = 0,
            errors = listOf("Could not read file."),
          )
          return@launch
        }
        importProfiles(jsonStr)
      } catch (e: Exception) {
        _importResult.value = ProfileImportResult(
          imported = 0,
          skipped = 0,
          errors = listOf("Import failed: ${e.message}"),
        )
      }
    }
  }

  /**
   * Clear the import result.
   */
  fun clearImportResult() {
    _importResult.value = null
  }
}

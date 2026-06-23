package io.rikka.agent.model

import kotlinx.serialization.Serializable

/**
 * Search and filter criteria for SSH profiles.
 *
 * Combines multiple filter dimensions (text search, group, tags) with
 * logical AND semantics: a profile must match ALL active criteria to be
 * included in the result set.
 *
 * ## Thread safety
 *
 * Immutable data class. Safe to share across threads without synchronization.
 *
 * @property query Free-text search term. Matches against profile name,
 *   host, and tags (case-insensitive substring). Empty or blank means
 *   no text filter.
 * @property group If non-null, restrict results to this [ProfileGroup].
 *   `null` means all groups.
 * @property tags If non-empty, restrict results to profiles whose tags
 *   contain ALL of these values (intersection semantics). Empty means
 *   no tag filter.
 * @property sortBy How to sort the results. Defaults to [SortBy.Name].
 * @property sortAscending Whether to sort ascending (`true`) or descending
 *   (`true` by default).
 */
@Serializable
data class ProfileSearchFilter(
  val query: String = "",
  val group: ProfileGroup? = null,
  val tags: List<String> = emptyList(),
  val sortBy: SortBy = SortBy.Name,
  val sortAscending: Boolean = true,
) {
  /** Whether any filter criterion is active. */
  val isActive: Boolean
    get() = query.isNotBlank() || group != null || tags.isNotEmpty()

  /**
   * Test whether [profile] matches this filter.
   *
   * Used for client-side filtering when the DAO only supports partial
   * filtering (e.g. text search only).
   */
  fun matches(profile: SshProfile): Boolean {
    // Text search
    if (query.isNotBlank()) {
      val q = query.lowercase()
      val nameMatch = profile.name.lowercase().contains(q)
      val hostMatch = profile.host.lowercase().contains(q)
      val tagMatch = profile.tags.any { it.lowercase().contains(q) }
      if (!nameMatch && !hostMatch && !tagMatch) return false
    }

    // Group filter
    if (group != null && profile.group != group) return false

    // Tag filter (all specified tags must be present)
    if (tags.isNotEmpty()) {
      val profileTagsLower = profile.tags.map { it.lowercase() }.toSet()
      if (!tags.all { it.lowercase() in profileTagsLower }) return false
    }

    return true
  }

  /**
   * Sort a list of profiles according to [sortBy] and [sortAscending].
   */
  fun sort(profiles: List<SshProfile>): List<SshProfile> {
    val comparator = when (sortBy) {
      SortBy.Name -> compareBy<SshProfile> { it.name.lowercase() }
      SortBy.Host -> compareBy<SshProfile> { it.host.lowercase() }
      SortBy.Group -> compareBy<SshProfile> { it.group.ordinal }
      SortBy.LastUsed -> compareBy<SshProfile> { it.id } // placeholder; no lastUsed field yet
    }
    return if (sortAscending) profiles.sortedWith(comparator) else profiles.sortedWith(comparator.reversed())
  }
}

/**
 * Sort dimensions for profile lists.
 */
@Serializable
enum class SortBy {
  /** Sort by display name (alphabetical). */
  Name,

  /** Sort by hostname (alphabetical). */
  Host,

  /** Sort by group (enum ordinal). */
  Group,

  /** Sort by last-used timestamp (requires external tracking). */
  LastUsed,
}

/**
 * Extension to apply a [ProfileSearchFilter] to a list of profiles.
 *
 * Combines [ProfileSearchFilter.matches] (filtering) and
 * [ProfileSearchFilter.sort] (ordering) in a single pass.
 */
fun List<SshProfile>.filterAndSort(filter: ProfileSearchFilter): List<SshProfile> {
  val filtered = if (filter.isActive) filter { filter.matches(it) } else this
  return filter.sort(filtered)
}

package io.rikka.agent.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Import/export container for SSH profiles.
 *
 * Wraps a list of [SshProfile] instances for JSON serialization.
 * Used by the import/export feature to exchange profiles between devices
 * or as backup/restore mechanism.
 *
 * ## Format
 *
 * ```json
 * {
 *   "version": 1,
 *   "exportedAt": "2026-06-23T12:00:00Z",
 *   "profiles": [ ... ]
 * }
 * ```
 *
 * @property version Schema version for forward compatibility.
 * @property exportedAt ISO-8601 timestamp of when the export was created.
 * @property profiles The exported profile list.
 */
@Serializable
data class ProfileExportBundle(
  val version: Int = 1,
  val exportedAt: String = "",
  val profiles: List<SshProfile> = emptyList(),
)

/**
 * Result of a profile import operation.
 *
 * @property imported Number of profiles successfully imported.
 * @property skipped Number of profiles skipped due to ID conflicts.
 * @property errors List of error messages for profiles that failed validation.
 */
data class ProfileImportResult(
  val imported: Int,
  val skipped: Int,
  val errors: List<String> = emptyList(),
)

/**
 * Handles JSON serialization/deserialization of SSH profiles for import/export.
 *
 * ## Thread safety
 *
 * All methods are pure functions or operate on the shared [json] instance,
 * which is thread-safe for read operations.
 */
object ProfileImportExport {

  /** JSON configuration for profile serialization. */
  val json: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  /**
   * Export a list of profiles to a JSON string.
   *
   * @param profiles The profiles to export.
   * @param exportedAt Optional ISO-8601 timestamp. Defaults to the current time.
   * @return A formatted JSON string.
   */
  fun exportToJson(
    profiles: List<SshProfile>,
    exportedAt: String = java.time.Instant.now().toString(),
  ): String {
    val bundle = ProfileExportBundle(
      version = 1,
      exportedAt = exportedAt,
      profiles = profiles,
    )
    return json.encodeToString(bundle)
  }

  /**
   * Import profiles from a JSON string.
   *
   * Returns the parsed [ProfileExportBundle] on success, or `null` if the
   * JSON is malformed.
   *
   * @param jsonStr The JSON string to parse.
   * @return The parsed bundle, or `null` on parse failure.
   */
  fun parseImport(jsonStr: String): ProfileImportBundle? {
    return try {
      json.decodeFromString<ProfileExportBundle>(jsonStr).let { bundle ->
        ProfileImportBundle(
          profiles = bundle.profiles,
          version = bundle.version,
        )
      }
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Prepare imported profiles for insertion.
   *
   * - Assigns new UUIDs to avoid ID conflicts with existing profiles.
   * - Validates required fields (host, username).
   * - Returns a [ProfileImportResult] summarizing the outcome.
   *
   * @param imported The raw profiles from the import bundle.
   * @param existingIds Set of profile IDs already in the store.
   * @return A pair of (valid profiles to insert, import result summary).
   */
  fun prepareForInsert(
    imported: List<SshProfile>,
    existingIds: Set<String>,
  ): Pair<List<SshProfile>, ProfileImportResult> {
    val valid = mutableListOf<SshProfile>()
    val errors = mutableListOf<String>()
    var skipped = 0

    for ((index, profile) in imported.withIndex()) {
      // Validate required fields
      if (profile.host.isBlank()) {
        errors.add("Profile #${index + 1} ('${profile.name}'): host is blank, skipped.")
        continue
      }
      if (profile.username.isBlank()) {
        errors.add("Profile #${index + 1} ('${profile.name}'): username is blank, skipped.")
        continue
      }

      // Check for ID conflict
      if (profile.id in existingIds) {
        skipped++
      }

      // Always assign a new ID to avoid conflicts
      valid.add(profile.copy(id = UUID.randomUUID().toString()))
    }

    return valid to ProfileImportResult(
      imported = valid.size,
      skipped = skipped,
      errors = errors,
    )
  }

  /**
   * Convenience: full import pipeline.
   *
   * Parses JSON, validates, and prepares profiles for insertion.
   *
   * @param jsonStr The JSON string to import.
   * @param existingIds Set of profile IDs already in the store.
   * @return A pair of (valid profiles, result summary), or `null` if parse fails.
   */
  fun importFromJson(
    jsonStr: String,
    existingIds: Set<String> = emptySet(),
  ): Pair<List<SshProfile>, ProfileImportResult>? {
    val bundle = parseImport(jsonStr) ?: return null
    return prepareForInsert(bundle.profiles, existingIds)
  }
}

/**
 * Internal representation of a parsed import bundle.
 */
data class ProfileImportBundle(
  val profiles: List<SshProfile>,
  val version: Int,
)

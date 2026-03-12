package io.rikka.agent.nav

import kotlinx.serialization.Serializable

/** Route definitions using kotlinx.serialization for type-safe navigation. */
sealed interface Screen {
  @Serializable
  data object Profiles : Screen

  @Serializable
  data class ProfileEditor(val profileId: String? = null) : Screen

  @Serializable
  data class Session(val profileId: String) : Screen

  @Serializable
  data object Settings : Screen

  @Serializable
  data object KnownHosts : Screen

  @Serializable
  data object About : Screen
}

package io.rikka.agent.model

import java.util.UUID

/**
 * Pre-built SSH profile templates for common server configurations.
 *
 * Templates provide a starting point when creating new profiles.
 * The user can select a template and customize it before saving.
 *
 * ## Usage
 *
 * ```kotlin
 * val template = ProfileTemplates.templates.first { it.name == "Ubuntu Server" }
 * val profile = template.toProfile(id = UUID.randomUUID().toString())
 * ```
 *
 * @property name Display name for the template.
 * @property description Brief explanation of the template's intended use.
 * @property group The [ProfileGroup] this template belongs to.
 * @property tags Suggested tags for the profile.
 * @property defaultUsername Default SSH username.
 * @property defaultPort Default SSH port.
 * @property authType Default authentication method.
 * @property codexMode Whether Codex mode is enabled by default.
 */
data class ProfileTemplate(
  val name: String,
  val description: String,
  val group: ProfileGroup,
  val tags: List<String>,
  val defaultUsername: String = "root",
  val defaultPort: Int = 22,
  val authType: AuthType = AuthType.PublicKey,
  val codexMode: Boolean = false,
) {
  /**
   * Convert this template to a concrete [SshProfile] with the given [id].
   *
   * The [SshProfile.host] is left blank for the user to fill in.
   * The [SshProfile.name] is set to the template [name].
   */
  fun toProfile(id: String = UUID.randomUUID().toString()): SshProfile = SshProfile(
    id = id,
    name = name,
    host = "",
    port = defaultPort,
    username = defaultUsername,
    authType = authType,
    codexMode = codexMode,
    group = group,
    tags = tags,
  )
}

/**
 * Built-in profile templates for common SSH server scenarios.
 */
object ProfileTemplates {

  /** All available templates. */
  val templates: List<ProfileTemplate> = listOf(
    // Development
    ProfileTemplate(
      name = "Local Dev Server",
      description = "Local or LAN development machine with full sudo access.",
      group = ProfileGroup.Development,
      tags = listOf("local", "dev"),
      defaultUsername = "dev",
    ),
    ProfileTemplate(
      name = "Docker Host",
      description = "Server running Docker for container-based development.",
      group = ProfileGroup.Development,
      tags = listOf("docker", "dev"),
      defaultUsername = "deploy",
    ),
    ProfileTemplate(
      name = "GPU Workstation",
      description = "Machine with NVIDIA GPU for ML/AI workloads.",
      group = ProfileGroup.Development,
      tags = listOf("gpu", "ml", "nvidia"),
      defaultUsername = "user",
    ),

    // Production
    ProfileTemplate(
      name = "Ubuntu Server",
      description = "Standard Ubuntu LTS production server.",
      group = ProfileGroup.Production,
      tags = listOf("ubuntu", "prod"),
      defaultUsername = "deploy",
    ),
    ProfileTemplate(
      name = "Web Server (Nginx)",
      description = "Production web server behind Nginx reverse proxy.",
      group = ProfileGroup.Production,
      tags = listOf("web", "nginx", "prod"),
      defaultUsername = "www-data",
    ),
    ProfileTemplate(
      name = "Database Server",
      description = "Dedicated database host (PostgreSQL / MySQL).",
      group = ProfileGroup.Production,
      tags = listOf("database", "prod"),
      defaultUsername = "dbadmin",
    ),
    ProfileTemplate(
      name = "Edge Node",
      description = "Lightweight edge / IoT device with limited resources.",
      group = ProfileGroup.Production,
      tags = listOf("edge", "iot"),
      defaultUsername = "root",
    ),

    // Testing
    ProfileTemplate(
      name = "Staging Server",
      description = "Pre-production staging environment for QA.",
      group = ProfileGroup.Testing,
      tags = listOf("staging", "qa"),
      defaultUsername = "tester",
    ),
    ProfileTemplate(
      name = "CI Runner",
      description = "Self-hosted CI/CD runner (GitHub Actions / GitLab CI).",
      group = ProfileGroup.Testing,
      tags = listOf("ci", "runner"),
      defaultUsername = "runner",
    ),

    // Personal
    ProfileTemplate(
      name = "Home Server",
      description = "Personal home server (NAS, media, homelab).",
      group = ProfileGroup.Personal,
      tags = listOf("home", "homelab"),
      defaultUsername = "admin",
    ),
    ProfileTemplate(
      name = "Raspberry Pi",
      description = "Raspberry Pi for experiments and prototyping.",
      group = ProfileGroup.Personal,
      tags = listOf("rpi", "arm"),
      defaultUsername = "pi",
      defaultPort = 22,
    ),

    // Codex-enabled
    ProfileTemplate(
      name = "Codex Remote Dev",
      description = "Remote dev machine with Codex AI tool integration.",
      group = ProfileGroup.Development,
      tags = listOf("codex", "ai", "dev"),
      defaultUsername = "dev",
      codexMode = true,
    ),
  )
}

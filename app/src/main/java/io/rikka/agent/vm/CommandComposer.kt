package io.rikka.agent.vm

object CommandComposer {
  fun shellQuote(input: String): String {
    val escaped = input.replace("'", "'\\''")
    return "'$escaped'"
  }

  fun wrapWithShell(command: String, shell: String): String {
    if (shell == "/bin/sh" || shell.isBlank()) return command
    val escaped = command.replace("'", "'\\''")
    return "$shell -c '$escaped'"
  }

  fun wrapForCodex(task: String, workDir: String?, apiKey: String?): String {
    val escapedTask = task.replace("\"", "\\\"")
    val cdPart = if (!workDir.isNullOrBlank()) "cd ${shellQuote(workDir)} && " else ""
    val envPart = if (!apiKey.isNullOrBlank()) "OPENAI_API_KEY=${shellQuote(apiKey)} " else ""
    return "${cdPart}${envPart}codex exec --json --full-auto \"$escapedTask\""
  }
}

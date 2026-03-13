package io.rikka.agent.vm

object CancelMessageHelper {
  fun mergeCanceledContent(current: String?, canceledText: String): String {
    val base = current?.trimEnd().orEmpty()
    return when {
      base.isBlank() -> canceledText
      base.contains(canceledText) -> base
      else -> "$base\n$canceledText"
    }
  }
}

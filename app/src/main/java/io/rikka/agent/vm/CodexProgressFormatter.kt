package io.rikka.agent.vm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class CodexProgressState(
  val thread: String? = null,
  val turn: String? = null,
  val item: String? = null,
) {
  fun isEmpty(): Boolean = thread == null && turn == null && item == null
}

internal object CodexProgressFormatter {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  private enum class Scope { THREAD, TURN, ITEM }

  private data class ProgressUpdate(
    val scope: Scope,
    val summary: String,
  )

  fun update(state: CodexProgressState, rawJson: String): CodexProgressState {
    val progress = parse(rawJson) ?: return state
    return when (progress.scope) {
      Scope.THREAD -> state.copy(thread = progress.summary)
      Scope.TURN -> state.copy(turn = progress.summary)
      Scope.ITEM -> state.copy(item = progress.summary)
    }
  }

  private fun parse(rawJson: String): ProgressUpdate? {
    val obj = try {
      json.parseToJsonElement(rawJson).jsonObject
    } catch (_: Exception) {
      return null
    }
    val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
    val action = type.substringAfter('.', missingDelimiterValue = type).replace('_', ' ')

    return when {
      type.startsWith("thread.") -> {
        ProgressUpdate(Scope.THREAD, composeSummary(action, extractIdentifier(obj, "thread")))
      }
      type.startsWith("turn.") -> {
        ProgressUpdate(Scope.TURN, composeSummary(action, extractIdentifier(obj, "turn")))
      }
      type.startsWith("item.") -> {
        val itemKind = extractItemKind(obj)
        ProgressUpdate(Scope.ITEM, composeSummary(action, extractIdentifier(obj, "item"), itemKind))
      }
      else -> null
    }
  }

  private fun composeSummary(action: String, id: String?, detail: String? = null): String {
    val parts = buildList {
      add(action.replaceFirstChar { it.uppercase() })
      detail?.takeIf { it.isNotBlank() }?.let { add(it) }
      id?.takeIf { it.isNotBlank() }?.let { add("#$it") }
    }
    return parts.joinToString(" • ")
  }

  private fun extractIdentifier(obj: JsonObject, scope: String): String? {
    return firstString(
      obj["${scope}_id"],
      obj[scope]?.jsonObjectOrNull()?.get("id"),
      obj["id"],
    )
  }

  private fun extractItemKind(obj: JsonObject): String? {
    val item = obj["item"]?.jsonObjectOrNull()
    return firstString(
      item?.get("type"),
      item?.get("role"),
      obj["role"],
    )
  }

  private fun firstString(vararg elements: JsonElement?): String? {
    return elements.firstNotNullOfOrNull { element ->
      element?.let {
        runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
      }?.takeIf { value -> !value.isNullOrBlank() }
    }
  }

  private fun JsonElement.jsonObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()
}

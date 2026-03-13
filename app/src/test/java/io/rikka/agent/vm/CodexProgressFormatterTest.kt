package io.rikka.agent.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class CodexProgressFormatterTest {

  @Test
  fun `update tracks latest thread turn and item summaries`() {
    var state = CodexProgressState()

    state = CodexProgressFormatter.update(
      state,
      """{"type":"thread.started","thread_id":"thread-1"}"""
    )
    state = CodexProgressFormatter.update(
      state,
      """{"type":"turn.completed","turn":{"id":"turn-2"}}"""
    )
    state = CodexProgressFormatter.update(
      state,
      """{"type":"item.started","item":{"id":"item-9","type":"tool_call"}}"""
    )

    assertEquals("Started • #thread-1", state.thread)
    assertEquals("Completed • #turn-2", state.turn)
    assertEquals("Started • tool_call • #item-9", state.item)
  }

  @Test
  fun `update ignores non progress json payloads`() {
    val original = CodexProgressState(thread = "Started • #thread-1")

    val updated = CodexProgressFormatter.update(
      original,
      """{"status":"running","delta":"hello"}"""
    )

    assertEquals(original, updated)
  }
}

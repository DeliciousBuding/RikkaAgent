package io.rikka.agent.vm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputFormatterTest {

  private val texts = OutputTexts(
    stderrLabel = "stderr:",
    truncatedHint = "[truncated]",
    noOutputOk = "(no output)",
    noOutputFailed = "(no output, failed)",
    exitCodeLabel = { c -> "exit: $c" },
  )

  @Test
  fun `format marks truncation and preserves full output`() {
    val long = "a".repeat(20)
    val out = OutputFormatter.format(
      stdout = long,
      stderr = "",
      exitCode = 0,
      capChars = 8,
      texts = texts,
    )

    assertTrue(out.truncated)
    assertTrue(out.display.contains("[truncated]"))
    assertTrue(out.full.contains(long))
  }

  @Test
  fun `format keeps non-truncated output unchanged`() {
    val out = OutputFormatter.format(
      stdout = "ok",
      stderr = "",
      exitCode = 0,
      capChars = 80,
      texts = texts,
    )

    assertFalse(out.truncated)
    assertTrue(out.display.contains("ok"))
    assertTrue(out.display.contains("exit: 0"))
  }

  @Test
  fun `format truncates stderr independently`() {
    val err = "e".repeat(32)
    val out = OutputFormatter.format(
      stdout = "",
      stderr = err,
      exitCode = 1,
      capChars = 10,
      texts = texts,
    )

    assertTrue(out.truncated)
    assertTrue(out.display.contains("stderr:"))
    assertTrue(out.display.contains("[truncated]"))
    assertTrue(out.full.contains(err))
  }

  @Test
  fun `format emits failed no-output line`() {
    val out = OutputFormatter.format(
      stdout = "",
      stderr = "",
      exitCode = 2,
      capChars = 16,
      texts = texts,
    )

    assertFalse(out.truncated)
    assertTrue(out.display.contains("(no output, failed)"))
    assertTrue(out.display.contains("exit: 2"))
  }
}

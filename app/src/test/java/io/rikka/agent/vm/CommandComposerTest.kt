package io.rikka.agent.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandComposerTest {

  @Test
  fun `shell quote escapes single quote`() {
    assertEquals("'a'\\''b'", CommandComposer.shellQuote("a'b"))
  }

  @Test
  fun `wrapWithShell returns plain command for sh`() {
    assertEquals("echo hi", CommandComposer.wrapWithShell("echo hi", "/bin/sh"))
  }

  @Test
  fun `wrapWithShell wraps command for custom shell`() {
    assertEquals("/bin/bash -c 'echo hi'", CommandComposer.wrapWithShell("echo hi", "/bin/bash"))
  }

  @Test
  fun `wrapWithShell returns plain command for blank shell`() {
    assertEquals("echo hi", CommandComposer.wrapWithShell("echo hi", ""))
  }

  @Test
  fun `wrapForCodex includes workdir and api key`() {
    val cmd = CommandComposer.wrapForCodex("fix bug", "/tmp/proj", "sk-123")
    assertEquals("cd '/tmp/proj' && OPENAI_API_KEY='sk-123' codex exec --json --full-auto \"fix bug\"", cmd)
  }

  @Test
  fun `wrapForCodex without optional values`() {
    val cmd = CommandComposer.wrapForCodex("run test", null, null)
    assertEquals("codex exec --json --full-auto \"run test\"", cmd)
  }

  @Test
  fun `wrapForCodex escapes double quote in task`() {
    val cmd = CommandComposer.wrapForCodex("say \"hi\"", null, null)
    assertEquals("codex exec --json --full-auto \"say \\\"hi\\\"\"", cmd)
  }

  @Test
  fun `wrapForCodex shell-quotes workdir and api key`() {
    val cmd = CommandComposer.wrapForCodex("do it", "/tmp/o'hare", "sk'a")
    assertEquals("cd '/tmp/o'\\''hare' && OPENAI_API_KEY='sk'\\''a' codex exec --json --full-auto \"do it\"", cmd)
  }
}

package io.rikka.agent.ssh

import io.rikka.agent.model.MessagePart
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SshOutputMapperTest {

  private lateinit var mapper: SshOutputMapper

  @Before
  fun setUp() {
    mapper = SshOutputMapper()
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private fun stdout(text: String) =
    ExecEvent.StdoutChunk(text.toByteArray(Charsets.UTF_8))

  private fun stderr(text: String) =
    ExecEvent.StderrChunk(text.toByteArray(Charsets.UTF_8))

  private fun exit(code: Int) = ExecEvent.Exit(code)

  private suspend fun collect(command: String, vararg events: ExecEvent): List<MessagePart> {
    return mapper.map(flowOf(*events), command).toList()
  }

  // ── 1. Plain stdout → TextPart ─────────────────────────────────────────────

  @Test
  fun `plain stdout emits Command then Text`() = runTest {
    val parts = collect("echo hello", stdout("hello\n"))

    // First part: initial Command
    val cmd = parts.filterIsInstance<MessagePart.Command>().first()
    assertEquals("echo hello", cmd.command)
    assertNull(cmd.exitCode)

    // Stdout becomes Text
    val texts = parts.filterIsInstance<MessagePart.Text>()
    assertEquals(1, texts.size)
    assertEquals("hello", texts[0].text)
  }

  @Test
  fun `plain multiline stdout emits Text parts`() = runTest {
    val parts = collect("ls", stdout("file1\nfile2\n"))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    // "file1\nfile2\n" — first line flushed at \n, second at final buffer flush
    val allText = texts.joinToString("\n") { it.text }
    assertTrue(allText.contains("file1"))
    assertTrue(allText.contains("file2"))
  }

  // ── 2. Markdown stdout → TextPart (preserves Markdown) ────────────────────

  @Test
  fun `markdown with bold and headers emitted as Text`() = runTest {
    val md = "# Title\n\nSome **bold** text\n"
    val parts = collect("cat README.md", stdout(md))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    val allText = texts.joinToString("\n") { it.text }
    assertTrue(allText.contains("# Title"))
    assertTrue(allText.contains("**bold**"))
  }

  @Test
  fun `markdown list items preserved in Text`() = runTest {
    val md = "- item one\n- item two\n- item three\n"
    val parts = collect("cat list.md", stdout(md))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    val allText = texts.joinToString("\n") { it.text }
    assertTrue(allText.contains("- item one"))
    assertTrue(allText.contains("- item two"))
  }

  // ── 3. ANSI escape code stripping ─────────────────────────────────────────

  @Test
  fun `ANSI color codes stripped from stdout`() = runTest {
    // ESC[31m = red, ESC[0m = reset
    val raw = "[31mred text[0m"
    val parts = collect("echo colored", stdout(raw + "\n"))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    assertEquals("red text", texts[0].text)
  }

  @Test
  fun `ANSI cursor movement codes stripped`() = runTest {
    // ESC[2K = erase line, ESC[1A = cursor up
    val raw = "[2K[1Aclean line\n"
    val parts = collect("tput clear", stdout(raw))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    assertEquals("clean line", texts[0].text)
  }

  @Test
  fun `ANSI OSC title sequences stripped`() = runTest {
    // ESC]0;title BEL
    val raw = "]0;window titlevisible text\n"
    val parts = collect("echo test", stdout(raw))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    assertEquals("visible text", texts[0].text)
  }

  @Test
  fun `stripAnsi removes complex nested sequences`() = runTest {
    val input = "[1;32;40mGreen on black[0m normal"
    val result = mapper.stripAnsi(input)
    assertEquals("Green on black normal", result)
  }

  @Test
  fun `stripAnsi on empty string returns empty`() = runTest {
    assertEquals("", mapper.stripAnsi(""))
  }

  @Test
  fun `stripAnsi on plain text returns unchanged`() = runTest {
    assertEquals("no escapes here", mapper.stripAnsi("no escapes here"))
  }

  // ── 4. stderr → StderrPart ────────────────────────────────────────────────

  @Test
  fun `stderr emits Stderr part`() = runTest {
    val parts = collect("grep pattern file", stderr("grep: file: No such file\n"))

    val stderrParts = parts.filterIsInstance<MessagePart.Stderr>()
    assertEquals(1, stderrParts.size)
    assertEquals("grep: file: No such file", stderrParts[0].text)
  }

  @Test
  fun `ANSI codes also stripped from stderr`() = runTest {
    val raw = "[31merror[0m: something failed\n"
    val parts = collect("failing-cmd", stderr(raw))

    val stderrParts = parts.filterIsInstance<MessagePart.Stderr>()
    assertEquals("error: something failed", stderrParts[0].text)
  }

  // ── 5. Exit code → CommandPart update ─────────────────────────────────────

  @Test
  fun `exit code 0 updates final Command part`() = runTest {
    val parts = collect("echo ok", stdout("ok\n"), exit(0))

    val commands = parts.filterIsInstance<MessagePart.Command>()
    assertEquals(2, commands.size)

    // First command: running (no exit code)
    assertNull(commands[0].exitCode)

    // Second command: finished with exit code 0
    assertEquals(0, commands[1].exitCode)
    assertTrue(commands[1].isSuccess)
    assertTrue(commands[1].isFinished)
  }

  @Test
  fun `non-zero exit code reflected in final Command`() = runTest {
    val parts = collect("false", exit(1))

    val commands = parts.filterIsInstance<MessagePart.Command>()
    assertEquals(2, commands.size)
    assertEquals(1, commands[1].exitCode)
    assertTrue(!commands[1].isSuccess)
  }

  @Test
  fun `exit code 127 for command not found`() = runTest {
    val parts = collect("nonexistent", stderr("command not found\n"), exit(127))

    val commands = parts.filterIsInstance<MessagePart.Command>()
    assertEquals(127, commands[1].exitCode)
  }

  @Test
  fun `initial and final Command parts share same command string and timestamp`() = runTest {
    val parts = collect("ls -la", stdout("total 0\n"), exit(0))

    val commands = parts.filterIsInstance<MessagePart.Command>()
    assertEquals(commands[0].command, commands[1].command)
    assertEquals(commands[0].startedAtEpochMs, commands[1].startedAtEpochMs)
  }

  // ── 6. Mixed stdout+stderr alternating output ─────────────────────────────

  @Test
  fun `stdout then stderr then stdout preserves ordering`() = runTest {
    val parts = collect(
      "mixed-cmd",
      stdout("line1\n"),
      stderr("warning1\n"),
      stdout("line2\n"),
      exit(0),
    )

    // Strip out Command parts for ordering check
    val nonCommand = parts.filter { it !is MessagePart.Command }

    assertEquals(3, nonCommand.size)
    assertTrue(nonCommand[0] is MessagePart.Text)
    assertEquals("line1", (nonCommand[0] as MessagePart.Text).text)

    assertTrue(nonCommand[1] is MessagePart.Stderr)
    assertEquals("warning1", (nonCommand[1] as MessagePart.Stderr).text)

    assertTrue(nonCommand[2] is MessagePart.Text)
    assertEquals("line2", (nonCommand[2] as MessagePart.Text).text)
  }

  @Test
  fun `buffer flushed before stderr emission`() = runTest {
    // Stdout without newline (buffered), then stderr forces flush
    val parts = collect(
      "cmd",
      stdout("partial"),
      stderr("err\n"),
    )

    val texts = parts.filterIsInstance<MessagePart.Text>()
    assertTrue(texts.any { it.text == "partial" })

    val stderrParts = parts.filterIsInstance<MessagePart.Stderr>()
    assertEquals("err", stderrParts[0].text)
  }

  @Test
  fun `multiple stderr chunks interleaved with stdout`() = runTest {
    val parts = collect(
      "build",
      stdout("Compiling...\n"),
      stderr("warning: unused var\n"),
      stderr("warning: unused import\n"),
      stdout("Done.\n"),
      exit(0),
    )

    val stderrParts = parts.filterIsInstance<MessagePart.Stderr>()
    assertEquals(2, stderrParts.size)
    assertEquals("warning: unused var", stderrParts[0].text)
    assertEquals("warning: unused import", stderrParts[1].text)

    val texts = parts.filterIsInstance<MessagePart.Text>()
    assertTrue(texts.any { it.text.contains("Compiling") })
    assertTrue(texts.any { it.text.contains("Done") })
  }

  // ── 7. Long output (>10KB) streaming ──────────────────────────────────────

  @Test
  fun `long output without boundaries triggers size flush`() = runTest {
    // Generate a long string without newlines (> MAX_BUFFER_CHARS = 16000)
    val longText = "A".repeat(17_000)
    val parts = collect("cat huge-file", stdout(longText))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    assertTrue("Long output should be split into at least one Text part", texts.isNotEmpty())

    val total = texts.sumOf { it.text.length }
    assertEquals(longText.length, total)
  }

  @Test
  fun `long line-based output flushes at line boundaries`() = runTest {
    // Many lines, each short but total > 16KB
    val lines = (1..500).map { "line-$it" }.joinToString("\n")
    val parts = collect("seq 500", stdout(lines + "\n"))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    assertTrue("Should produce multiple Text parts from many lines", texts.size > 1)

    val allText = texts.joinToString("\n") { it.text }
    assertTrue(allText.contains("line-1"))
    assertTrue(allText.contains("line-500"))
  }

  @Test
  fun `streaming chunks accumulate across multiple StdoutChunk events`() = runTest {
    // Simulate chunked streaming: word arrives in pieces
    val parts = collect(
      "curl url",
      stdout("Hel"),
      stdout("lo "),
      stdout("World\n"),
    )

    val texts = parts.filterIsInstance<MessagePart.Text>()
    val allText = texts.joinToString(" ") { it.text }
    assertTrue(allText.contains("Hello"))
    assertTrue(allText.contains("World"))
  }

  // ── 8. Empty output handling ───────────────────────────────────────────────

  @Test
  fun `empty output with exit 0 emits only Command parts`() = runTest {
    val parts = collect("true", exit(0))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    assertTrue("No Text parts for empty output", texts.isEmpty())

    val stderrParts = parts.filterIsInstance<MessagePart.Stderr>()
    assertTrue("No Stderr parts for empty output", stderrParts.isEmpty())

    val commands = parts.filterIsInstance<MessagePart.Command>()
    assertEquals(2, commands.size)
    assertEquals(0, commands[1].exitCode)
  }

  @Test
  fun `empty stdout chunk is ignored`() = runTest {
    val parts = collect("cmd", stdout(""), exit(0))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    assertTrue(texts.isEmpty())
  }

  @Test
  fun `empty stderr chunk is ignored`() = runTest {
    val parts = collect("cmd", stderr(""), exit(0))

    val stderrParts = parts.filterIsInstance<MessagePart.Stderr>()
    assertTrue(stderrParts.isEmpty())
  }

  @Test
  fun `whitespace-only stdout still emitted`() = runTest {
    // "   \n" has content after ANSI strip, and the buffer will flush at \n
    val parts = collect("cmd", stdout("   \n"))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    // The content "   " is not empty, so it gets emitted as Text
    assertTrue(texts.any { it.text == "   " })
  }

  // ── Additional edge cases ─────────────────────────────────────────────────

  @Test
  fun `fenced code block detected and emitted as Code`() = runTest {
    val input = "Here is code:\n```python\nprint('hi')\n```\nEnd.\n"
    val parts = collect("cat demo.py", stdout(input))

    val codes = parts.filterIsInstance<MessagePart.Code>()
    assertEquals(1, codes.size)
    assertEquals("print('hi')", codes[0].code)
    assertEquals("python", codes[0].language)
  }

  @Test
  fun `fenced code block without language tag has null language`() = runTest {
    val input = "```\nsome code\n```\n"
    val parts = collect("cat file", stdout(input))

    val codes = parts.filterIsInstance<MessagePart.Code>()
    assertEquals(1, codes.size)
    assertNull(codes[0].language)
  }

  @Test
  fun `text before and after code block emitted as Text`() = runTest {
    val input = "Before\n```bash\necho hi\n```\nAfter\n"
    val parts = collect("cat script", stdout(input))

    val texts = parts.filterIsInstance<MessagePart.Text>()
    val allText = texts.joinToString("\n") { it.text }
    assertTrue(allText.contains("Before"))
    assertTrue(allText.contains("After"))
  }

  @Test
  fun `ExecEvent Error emits Error part`() = runTest {
    val parts = collect(
      "cmd",
      ExecEvent.Error(category = "connection", message = "Connection refused"),
    )

    val errors = parts.filterIsInstance<MessagePart.Error>()
    assertEquals(1, errors.size)
    assertEquals("Connection refused", errors[0].message)
    assertEquals("connection", errors[0].cause)
  }

  @Test
  fun `Canceled event emits Command with null exitCode`() = runTest {
    val parts = collect(
      "long-running-cmd",
      stdout("working...\n"),
      ExecEvent.Canceled,
    )

    val commands = parts.filterIsInstance<MessagePart.Command>()
    assertEquals(2, commands.size)
    // Final command should still have null exitCode for cancellation
    assertNull(commands[1].exitCode)
  }

  @Test
  fun `mapper is reusable across multiple calls`() = runTest {
    val parts1 = collect("echo first", stdout("first\n"), exit(0))
    val parts2 = collect("echo second", stdout("second\n"), exit(0))

    val texts1 = parts1.filterIsInstance<MessagePart.Text>()
    val texts2 = parts2.filterIsInstance<MessagePart.Text>()

    assertTrue(texts1.any { it.text == "first" })
    assertTrue(texts2.any { it.text == "second" })
    // No cross-contamination
    assertTrue(texts2.none { it.text.contains("first") })
  }

  @Test
  fun `StructuredEvent is ignored by mapper`() = runTest {
    val parts = collect(
      "cmd",
      stdout("before\n"),
      ExecEvent.StructuredEvent(kind = "json", rawJson = """{"key":"value"}"""),
      stdout("after\n"),
      exit(0),
    )

    // StructuredEvent should not produce any extra parts
    val texts = parts.filterIsInstance<MessagePart.Text>()
    val allText = texts.joinToString("\n") { it.text }
    assertTrue(allText.contains("before"))
    assertTrue(allText.contains("after"))
  }

  @Test
  fun `stderr with ANSI escape codes stripped and emitted as Stderr`() = runTest {
    val raw = "[1;31m[Kcritical failure[0m\n"
    val parts = collect("run-test", stderr(raw), exit(1))

    val stderrParts = parts.filterIsInstance<MessagePart.Stderr>()
    assertEquals("critical failure", stderrParts[0].text)
  }
}

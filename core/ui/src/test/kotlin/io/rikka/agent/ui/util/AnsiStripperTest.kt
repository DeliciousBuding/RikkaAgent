package io.rikka.agent.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AnsiStripperTest {

  @Test
  fun `plain text passes through unchanged`() {
    assertEquals("hello world", AnsiStripper.strip("hello world"))
  }

  @Test
  fun `strips CSI color codes`() {
    assertEquals("hello", AnsiStripper.strip("\u001B[31mhello\u001B[0m"))
  }

  @Test
  fun `strips bold and reset`() {
    assertEquals("bold text", AnsiStripper.strip("\u001B[1mbold text\u001B[0m"))
  }

  @Test
  fun `strips multiple SGR parameters`() {
    assertEquals("styled", AnsiStripper.strip("\u001B[1;31;42mstyled\u001B[0m"))
  }

  @Test
  fun `strips cursor movement codes`() {
    assertEquals("AB", AnsiStripper.strip("A\u001B[2CB"))
  }

  @Test
  fun `strips OSC sequences with BEL`() {
    assertEquals("before after", AnsiStripper.strip("before \u001B]0;title\u0007after"))
  }

  @Test
  fun `strips OSC sequences with ST`() {
    assertEquals("before after", AnsiStripper.strip("before \u001B]0;title\u001B\\after"))
  }

  @Test
  fun `empty string returns empty`() {
    assertEquals("", AnsiStripper.strip(""))
  }

  @Test
  fun `multiline with mixed ANSI`() {
    val input = "\u001B[32m$ uptime\u001B[0m\n 12:00:00 up 5 days\n\u001B[31merror\u001B[0m"
    val expected = "$ uptime\n 12:00:00 up 5 days\nerror"
    assertEquals(expected, AnsiStripper.strip(input))
  }
}

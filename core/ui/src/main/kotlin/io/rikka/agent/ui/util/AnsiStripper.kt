package io.rikka.agent.ui.util

/**
 * v1 helper: best-effort stripping of common ANSI escape sequences so exec output
 * doesn't render as gibberish in chat bubbles.
 *
 * This is NOT a terminal emulator.
 */
object AnsiStripper {
  private val ansiRegex =
    Regex("""\u001B\[[0-9;]*[A-Za-z]|\u001B\][^\u0007]*(\u0007|\u001B\\)""")

  fun strip(input: String): String = input.replace(ansiRegex, "")
}


package io.rikka.agent.vm

data class OutputTexts(
  val stderrLabel: String,
  val truncatedHint: String,
  val noOutputOk: String,
  val noOutputFailed: String,
  val exitCodeLabel: (Int) -> String,
)

data class FormattedOutput(
  val display: String,
  val full: String,
  val truncated: Boolean,
)

object OutputFormatter {
  fun format(
    stdout: String,
    stderr: String,
    exitCode: Int?,
    capChars: Int,
    texts: OutputTexts,
  ): FormattedOutput {
    val full = buildOutput(stdout, stderr, exitCode, capChars = null, texts = texts)
    val display = buildOutput(stdout, stderr, exitCode, capChars = capChars, texts = texts)
    return FormattedOutput(
      display = display,
      full = full,
      truncated = display != full,
    )
  }

  private fun buildOutput(
    stdout: String,
    stderr: String,
    exitCode: Int?,
    capChars: Int?,
    texts: OutputTexts,
  ): String = buildString {
    val stdoutTruncated = capChars != null && stdout.length > capChars
    val stderrTruncated = capChars != null && stderr.length > capChars

    if (stdout.isNotEmpty()) {
      if (stdoutTruncated) {
        append(stdout.takeLast(capChars!!))
        append("\n")
        append(texts.truncatedHint)
        append("\n")
      } else {
        append(stdout)
      }
      if (!endsWith("\n")) append("\n")
    }

    if (stderr.isNotEmpty()) {
      if (isNotEmpty()) append("\n")
      append(texts.stderrLabel)
      append("\n")
      if (stderrTruncated) {
        append(stderr.takeLast(capChars!!))
        append("\n")
        append(texts.truncatedHint)
        append("\n")
      } else {
        append(stderr)
      }
      if (!endsWith("\n")) append("\n")
    }

    if (exitCode != null) {
      if (stdout.isEmpty() && stderr.isEmpty()) {
        append(if (exitCode == 0) texts.noOutputOk else texts.noOutputFailed)
        append("\n")
      }
      if (isNotEmpty()) append("\n")
      append(texts.exitCodeLabel(exitCode))
    }
  }
}

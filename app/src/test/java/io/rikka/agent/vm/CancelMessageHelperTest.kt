package io.rikka.agent.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class CancelMessageHelperTest {

  @Test
  fun `returns canceled text when current is null`() {
    assertEquals("[canceled]", CancelMessageHelper.mergeCanceledContent(null, "[canceled]"))
  }

  @Test
  fun `returns canceled text when current is blank`() {
    assertEquals("[canceled]", CancelMessageHelper.mergeCanceledContent("  ", "[canceled]"))
  }

  @Test
  fun `appends canceled text when current has output`() {
    assertEquals(
      "line1\n[canceled]",
      CancelMessageHelper.mergeCanceledContent("line1", "[canceled]"),
    )
  }

  @Test
  fun `does not append duplicate canceled text`() {
    assertEquals(
      "line1\n[canceled]",
      CancelMessageHelper.mergeCanceledContent("line1\n[canceled]", "[canceled]"),
    )
  }
}

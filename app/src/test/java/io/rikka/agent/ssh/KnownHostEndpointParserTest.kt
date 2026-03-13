package io.rikka.agent.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnownHostEndpointParserTest {

  @Test
  fun `parse supports bracketed hostname endpoint`() {
    assertEquals(
      KnownHostEndpoint("example.test", 22),
      KnownHostEndpointParser.parse("[example.test]:22"),
    )
  }

  @Test
  fun `parse supports bracketed ipv6 endpoint`() {
    assertEquals(
      KnownHostEndpoint("2001:db8::10", 2222),
      KnownHostEndpointParser.parse("[2001:db8::10]:2222"),
    )
  }

  @Test
  fun `parse returns null for malformed keys`() {
    assertNull(KnownHostEndpointParser.parse("example.test:22"))
    assertNull(KnownHostEndpointParser.parse("[]:22"))
    assertNull(KnownHostEndpointParser.parse("[example.test]:abc"))
  }
}

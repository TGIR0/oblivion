package org.bepass.oblivion.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostPortParserTest {
  @Test
  fun parseOrNull_null_returnsNull() {
    assertNull(HostPortParser.parseOrNull(null))
  }

  @Test
  fun parseOrNull_hostOnly_returnsPortMinusOne() {
    val parsed = HostPortParser.parseOrNull("example.com")
    requireNotNull(parsed)
    assertEquals("example.com", parsed.host())
    assertEquals(-1, parsed.port())
  }

  @Test
  fun parseOrNull_ipv4WithPort() {
    val parsed = HostPortParser.parseOrNull("127.0.0.1:8080")
    requireNotNull(parsed)
    assertEquals("127.0.0.1", parsed.host())
    assertEquals(8080, parsed.port())
  }

  @Test
  fun parseOrNull_hostnameWithPort() {
    val parsed = HostPortParser.parseOrNull("example.com:443")
    requireNotNull(parsed)
    assertEquals("example.com", parsed.host())
    assertEquals(443, parsed.port())
  }

  @Test
  fun parseOrNull_bracketedIpv6WithPort() {
    val parsed = HostPortParser.parseOrNull("[2001:db8::1]:443")
    requireNotNull(parsed)
    assertEquals("2001:db8::1", parsed.host())
    assertEquals(443, parsed.port())
  }

  @Test
  fun parseOrNull_bracketedIpv6WithoutPort() {
    val parsed = HostPortParser.parseOrNull("[2001:db8::1]")
    requireNotNull(parsed)
    assertEquals("2001:db8::1", parsed.host())
    assertEquals(-1, parsed.port())
  }

  @Test
  fun parseOrNull_unbracketedIpv6_treatedAsHostOnly() {
    val parsed = HostPortParser.parseOrNull("2001:db8::1")
    requireNotNull(parsed)
    assertEquals("2001:db8::1", parsed.host())
    assertEquals(-1, parsed.port())
  }

  @Test
  fun parseOrNull_emptyHost_invalid() {
    assertNull(HostPortParser.parseOrNull(":443"))
  }

  @Test
  fun parseOrNull_missingPort_invalid() {
    assertNull(HostPortParser.parseOrNull("example.com:"))
  }

  @Test
  fun parseOrNull_invalidPort_invalid() {
    assertNull(HostPortParser.parseOrNull("example.com:99999"))
  }
}

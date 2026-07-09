package org.bepass.oblivion.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [HostPortParser.parseOrNull]. */
class HostPortParserTest {

  @Test
  fun `null input returns null`() {
    assertNull("Parsing null should result in null", HostPortParser.parseOrNull(null))
  }

  @Test
  fun `host with no port returns host and port -1`() {
    val parsed = HostPortParser.parseOrNull("example.com")
    assertNotNull("Parsed result must not be null for a valid host", parsed)
    val hostPort = parsed!!
    assertEquals("Host should be extracted correctly", "example.com", hostPort.host)
    assertEquals("Missing port should be represented as -1", -1, hostPort.port)
  }

  @Test
  fun `IPv4 address with port is parsed correctly`() {
    val parsed = HostPortParser.parseOrNull("127.0.0.1:8080")
    assertNotNull("Parsed result must not be null", parsed)
    val hostPort = parsed!!
    assertEquals("IPv4 address should be returned as host", "127.0.0.1", hostPort.host)
    assertEquals("Port should be extracted and converted to int", 8080, hostPort.port)
  }

  @Test
  fun `hostname with port is parsed correctly`() {
    val parsed = HostPortParser.parseOrNull("example.com:443")
    assertNotNull("Parsed result must not be null", parsed)
    val hostPort = parsed!!
    assertEquals("Hostname should be returned", "example.com", hostPort.host)
    assertEquals("Port should be 443", 443, hostPort.port)
  }

  @Test
  fun `bracketed IPv6 with port strips brackets and extracts port`() {
    val parsed = HostPortParser.parseOrNull("[2001:db8::1]:443")
    assertNotNull("Parsed result must not be null", parsed)
    val hostPort = parsed!!
    assertEquals("IPv6 address without brackets", "2001:db8::1", hostPort.host)
    assertEquals("Port should be 443", 443, hostPort.port)
  }

  @Test
  fun `bracketed IPv6 without port returns port -1`() {
    val parsed = HostPortParser.parseOrNull("[2001:db8::1]")
    assertNotNull("Parsed result must not be null", parsed)
    val hostPort = parsed!!
    assertEquals("IPv6 address without brackets", "2001:db8::1", hostPort.host)
    assertEquals("Missing port should be -1", -1, hostPort.port)
  }

  @Test
  fun `unbracketed IPv6 is treated as host with port -1`() {
    val parsed = HostPortParser.parseOrNull("2001:db8::1")
    assertNotNull("Parsed result must not be null for bare IPv6", parsed)
    val hostPort = parsed!!
    assertEquals("Bare IPv6 address is preserved as host", "2001:db8::1", hostPort.host)
    assertEquals("No port means -1", -1, hostPort.port)
  }

  @Test
  fun `empty host before colon is invalid`() {
    assertNull(
      "Input ':443' has an empty host and must return null",
      HostPortParser.parseOrNull(":443"),
    )
  }

  @Test
  fun `host with trailing colon but no port is invalid`() {
    assertNull(
      "Input 'example.com:' is missing a port number and must return null",
      HostPortParser.parseOrNull("example.com:"),
    )
  }

  @Test
  fun `port number out of valid range is invalid`() {
    assertNull(
      "Port 99999 is outside the valid TCP/UDP range and must be rejected",
      HostPortParser.parseOrNull("example.com:99999"),
    )
  }
}

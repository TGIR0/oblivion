package org.bepass.oblivion.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [DnsUriParser]. */
class DnsUriParserTest {

  // ---------------------------------------------------------------
  // Single‑URI parsing
  // ---------------------------------------------------------------

  @Test
  fun `plain IPv4 address is recognised as PLAIN transport with correct host`() {
    val endpoint = DnsUriParser.parse("1.1.1.1")

    assertEquals(
      "Transport of a bare IPv4 address must be PLAIN",
      DnsTransport.PLAIN,
      endpoint.transport,
    )
    assertEquals("Host of a bare IPv4 address must be the address itself", "1.1.1.1", endpoint.host)
    assertTrue("A bare IPv4 address must be flagged as IP literal", endpoint.isIpLiteral)
  }

  @Test
  fun `DoH URL extracts transport, host, port, and path correctly`() {
    val endpoint = DnsUriParser.parse("https://cloudflare-dns.com/dns-query")

    assertEquals("Transport for an https URI must be DOH", DnsTransport.DOH, endpoint.transport)
    assertEquals(
      "Host must be extracted without scheme or path",
      "cloudflare-dns.com",
      endpoint.host,
    )
    assertEquals("Port must default to 443 for HTTPS", 443, endpoint.port)
    assertEquals("Path must be preserved", "/dns-query", endpoint.path)
  }

  @Test
  fun `TLS and QUIC URIs are recognised as DOT and DOQ respectively with correct ports`() {
    val dot = DnsUriParser.parse("tls://dns.quad9.net")
    val doq = DnsUriParser.parse("quic://dns.quad9.net")

    assertEquals("Transport for tls:// must be DOT", DnsTransport.DOT, dot.transport)
    assertEquals("Default port for DOT must be 853", 853, dot.port)

    assertEquals("Transport for quic:// must be DOQ", DnsTransport.DOQ, doq.transport)
    assertEquals("Default port for DOQ must be 853", 853, doq.port)
  }

  // ---------------------------------------------------------------
  // Bulk parsing
  // ---------------------------------------------------------------

  @Test
  fun `bulk parsing returns valid endpoints and collects errors for invalid entries`() {
    val bulkInput = "1.1.1.1; https://dns.google/dns-query; bad://value"
    val result = DnsUriParser.parseBulk(bulkInput)

    assertEquals("Only two of the three entries should be valid", 2, result.endpoints.size)
    assertTrue(
      "Errors list must contain at least one entry for the malformed URI",
      result.errors.isNotEmpty(),
    )
  }

  // ---------------------------------------------------------------
  // Validation
  // ---------------------------------------------------------------

  @Test
  fun `validate returns null for a properly formatted list of URIs`() {
    val validInput = "udp://1.1.1.1:53, tcp://8.8.8.8:53"

    assertNull(
      "A well‑formed list of DNS URIs must produce no validation error",
      DnsUriParser.validate(validInput),
    )
  }
}

package org.bepass.oblivion.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsUriParserTest {
  @Test
  fun parsePlainIpv4() {
    val endpoint = DnsUriParser.parse("1.1.1.1")
    assertEquals(DnsTransport.PLAIN, endpoint.transport)
    assertEquals("1.1.1.1", endpoint.host)
    assertTrue(endpoint.isIpLiteral)
  }

  @Test
  fun parseDohUri() {
    val endpoint = DnsUriParser.parse("https://cloudflare-dns.com/dns-query")
    assertEquals(DnsTransport.DOH, endpoint.transport)
    assertEquals("cloudflare-dns.com", endpoint.host)
    assertEquals(443, endpoint.port)
    assertEquals("/dns-query", endpoint.path)
  }

  @Test
  fun parseDotAndDoq() {
    val dot = DnsUriParser.parse("tls://dns.quad9.net")
    val doq = DnsUriParser.parse("quic://dns.quad9.net")
    assertEquals(DnsTransport.DOT, dot.transport)
    assertEquals(853, dot.port)
    assertEquals(DnsTransport.DOQ, doq.transport)
    assertEquals(853, doq.port)
  }

  @Test
  fun parseBulkCollectsErrors() {
    val result = DnsUriParser.parseBulk("1.1.1.1; https://dns.google/dns-query; bad://value")
    assertEquals(2, result.endpoints.size)
    assertTrue(result.errors.isNotEmpty())
  }

  @Test
  fun validateReturnsNullForValidInput() {
    assertNull(DnsUriParser.validate("udp://1.1.1.1:53, tcp://8.8.8.8:53"))
  }
}

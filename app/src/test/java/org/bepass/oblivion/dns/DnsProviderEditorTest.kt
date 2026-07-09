package org.bepass.oblivion.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsProviderEditorTest {
  @Test
  fun `plain provider accepts multiple IP addresses`() {
    val provider =
      DnsProviderEditor.build(
        existing = null,
        providerId = "custom-plain",
        label = "Local resolver",
        transport = DnsTransport.PLAIN,
        endpointInput = "1.1.1.1, 2606:4700:4700::1111",
      )

    assertEquals(listOf("1.1.1.1", "2606:4700:4700::1111"), provider.plainIps)
    assertTrue(provider.supports(DnsTransport.PLAIN))
    assertTrue(provider.supports(DnsTransport.UDP))
    assertTrue(provider.supports(DnsTransport.TCP))
  }

  @Test
  fun `DoH provider requires and stores an HTTPS endpoint`() {
    val provider =
      DnsProviderEditor.build(
        existing = null,
        providerId = "custom-doh",
        label = "Secure resolver",
        transport = DnsTransport.DOH,
        endpointInput = "https://dns.example/dns-query",
      )

    assertEquals("https://dns.example:443/dns-query", provider.dohUrl)
    assertEquals(443, provider.ports[DnsTransport.DOH])
  }

  @Test
  fun `DoT provider accepts a hostname and applies its default port`() {
    val provider =
      DnsProviderEditor.build(
        existing = null,
        providerId = "custom-dot",
        label = "TLS resolver",
        transport = DnsTransport.DOT,
        endpointInput = "dns.example",
      )

    assertEquals("dns.example", provider.dotHost)
    assertEquals(853, provider.ports[DnsTransport.DOT])
  }

  @Test
  fun `editing one transport preserves other provider endpoints`() {
    val existing =
      DnsProvider(
        providerId = "multi",
        label = "Original",
        transports = setOf(DnsTransport.PLAIN, DnsTransport.DOH),
        plainIps = listOf("9.9.9.9"),
        dohUrl = "https://dns.quad9.net/dns-query",
      )

    val edited =
      DnsProviderEditor.build(
        existing = existing,
        providerId = existing.providerId,
        label = "Renamed",
        transport = DnsTransport.DOT,
        endpointInput = "dns.quad9.net:8853",
      )

    assertEquals("Renamed", edited.label)
    assertEquals(existing.plainIps, edited.plainIps)
    assertEquals(existing.dohUrl, edited.dohUrl)
    assertEquals("dns.quad9.net", edited.dotHost)
    assertEquals(8853, edited.ports[DnsTransport.DOT])
  }
}

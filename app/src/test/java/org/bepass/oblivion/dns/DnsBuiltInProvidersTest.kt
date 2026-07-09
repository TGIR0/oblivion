package org.bepass.oblivion.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsBuiltInProvidersTest {
  @Test
  fun `merged catalog contains at least forty unique providers`() {
    val base =
      DnsCatalog(
        providers =
          (1..15).map { index ->
            DnsProvider(providerId = "base-$index", label = "Base $index")
          }
      )

    val merged = DnsCatalogRepository.withBuiltIns(base)

    assertTrue(merged.providers.size >= 40)
    assertEquals(
      merged.providers.size,
      merged.providers.map { it.providerId.lowercase() }.distinct().size,
    )
  }

  @Test
  fun `additional public resolvers support plain doh and dot`() {
    DnsBuiltInProviders.additional.forEach { provider ->
      assertTrue("${provider.providerId} missing Plain", provider.supports(DnsTransport.PLAIN))
      assertTrue("${provider.providerId} missing DoH", provider.supports(DnsTransport.DOH))
      assertTrue("${provider.providerId} missing DoT", provider.supports(DnsTransport.DOT))
      assertTrue("${provider.providerId} missing IP", provider.plainIps.isNotEmpty())
      assertTrue("${provider.providerId} missing DoH URL", !provider.dohUrl.isNullOrBlank())
      assertTrue("${provider.providerId} missing DoT host", !provider.dotHost.isNullOrBlank())
    }
  }
}

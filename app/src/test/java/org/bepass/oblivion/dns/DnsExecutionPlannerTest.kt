package org.bepass.oblivion.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [DnsExecutionPlanner]. */
class DnsExecutionPlannerTest {

  private val planner = DnsExecutionPlanner()

  // یک کاتالوگ ثابت برای تست‌ها
  private val catalog =
    DnsCatalog(
      version = 1,
      providers =
        listOf(
          DnsProvider(
            providerId = "cloudflare",
            label = "Cloudflare",
            regionGroup = "International",
            transports =
              setOf(DnsTransport.PLAIN, DnsTransport.DOH, DnsTransport.DOT, DnsTransport.DOQ),
            plainIps = listOf("1.1.1.1", "1.0.0.1"),
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1"),
            dohUrl = "https://cloudflare-dns.com/dns-query",
          ),
          DnsProvider(
            providerId = "shecan",
            label = "Shecan",
            country = "IR",
            regionGroup = "Iran",
            transports = setOf(DnsTransport.PLAIN, DnsTransport.UDP, DnsTransport.TCP),
            plainIps = listOf("178.22.122.100", "185.51.200.2"),
            bootstrapIps = listOf("178.22.122.100", "185.51.200.2"),
          ),
        ),
    )

  // --- تست‌ها ---

  @Test
  fun `global provider with plain IP should populate both tunnel core and VPN interface as CSV`() {
    val profile =
      DnsProfile.defaults().copy(global = DnsSelection.provider("shecan", DnsTransport.PLAIN))

    val plan = planner.compile(profile, catalog)

    assertEquals(
      "Global provider plain IPs must be used for tunnel core",
      "178.22.122.100,185.51.200.2",
      plan.tunnelCoreCsv,
    )
    assertEquals(
      "Global provider plain IPs must be used for VPN interface",
      "178.22.122.100,185.51.200.2",
      plan.vpnInterfaceCsv,
    )
  }

  @Test
  fun `APP_HTTP with DOT should fall back to DOH and produce a warning`() {
    val profile =
      DnsProfile.defaults()
        .withSelection(DnsSection.APP_HTTP, DnsSelection.provider("cloudflare", DnsTransport.DOT))

    val plan = planner.compile(profile, catalog)

    assertEquals("Fallback mode must be DOH", AppDnsMode.DOH, plan.appHttpConfig.mode)
    assertEquals(
      "Fallback DOH URL must match provider",
      "https://cloudflare-dns.com/dns-query",
      plan.appHttpConfig.dohUrl,
    )
    assertTrue(
      "A warning about APP_HTTP falling back to DoH should be present",
      plan.warnings.any { it.contains("APP_HTTP") && it.contains("DoH") },
    )
  }

  @Test
  fun `DIAGNOSTICS inherits APP_HTTP when set to inherit`() {
    val profile =
      DnsProfile.defaults()
        .withSelection(DnsSection.APP_HTTP, DnsSelection.provider("cloudflare", DnsTransport.DOH))
        .withSelection(DnsSection.DIAGNOSTICS, DnsSelection.inherit())

    val plan = planner.compile(profile, catalog)

    assertEquals(
      "Diagnostics must inherit DOH URL from APP_HTTP",
      plan.appHttpConfig.dohUrl,
      plan.diagnosticsConfig.dohUrl,
    )
    assertEquals(
      "Diagnostics must inherit mode from APP_HTTP",
      plan.appHttpConfig.mode,
      plan.diagnosticsConfig.mode,
    )
  }

  @Test
  fun `manual DOH should keep IPv4 fallback in tunnel core and set DOH for APP_HTTP`() {
    val profile =
      DnsProfile.defaults()
        .copy(global = DnsSelection.manual("https://cloudflare-dns.com/dns-query,1.1.1.1"))

    val plan = planner.compile(profile, catalog)

    assertTrue(
      "Tunnel core CSV must contain the fallback IP",
      plan.tunnelCoreCsv.contains("1.1.1.1"),
    )
    assertEquals("APP_HTTP mode must be DOH", AppDnsMode.DOH, plan.appHttpConfig.mode)
    assertTrue(
      "APP_HTTP DOH URL must use the provided domain",
      plan.appHttpConfig.dohUrl?.contains("cloudflare-dns.com") == true,
    )
  }
}

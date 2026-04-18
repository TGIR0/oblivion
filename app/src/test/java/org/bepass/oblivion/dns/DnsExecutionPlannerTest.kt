package org.bepass.oblivion.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsExecutionPlannerTest {
  private val catalog =
    DnsCatalog(
      version = 1,
      providers =
        listOf(
          DnsProvider(
            providerId = "cloudflare",
            label = "Cloudflare",
            regionGroup = "International",
            transports = setOf(DnsTransport.PLAIN, DnsTransport.DOH, DnsTransport.DOT, DnsTransport.DOQ),
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

  private val planner = DnsExecutionPlanner()

  @Test
  fun globalProviderAppliesToCoreAndVpnWithIpCsv() {
    val profile =
      DnsProfile.defaults().copy(
        global = DnsSelection.provider("shecan", DnsTransport.PLAIN),
      )

    val plan = planner.compile(profile, catalog)

    assertEquals("178.22.122.100,185.51.200.2", plan.tunnelCoreCsv)
    assertEquals("178.22.122.100,185.51.200.2", plan.vpnInterfaceCsv)
  }

  @Test
  fun appHttpDotFallsBackToDoh() {
    val profile =
      DnsProfile.defaults()
        .withSelection(DnsSection.APP_HTTP, DnsSelection.provider("cloudflare", DnsTransport.DOT))

    val plan = planner.compile(profile, catalog)

    assertEquals(AppDnsMode.DOH, plan.appHttpConfig.mode)
    assertEquals("https://cloudflare-dns.com/dns-query", plan.appHttpConfig.dohUrl)
    assertTrue(plan.warnings.any { it.contains("APP_HTTP") && it.contains("DoH") })
  }

  @Test
  fun diagnosticsInheritsAppHttpByDefault() {
    val profile =
      DnsProfile.defaults()
        .withSelection(DnsSection.APP_HTTP, DnsSelection.provider("cloudflare", DnsTransport.DOH))
        .withSelection(DnsSection.DIAGNOSTICS, DnsSelection.inherit())

    val plan = planner.compile(profile, catalog)

    assertEquals(plan.appHttpConfig.dohUrl, plan.diagnosticsConfig.dohUrl)
    assertEquals(plan.appHttpConfig.mode, plan.diagnosticsConfig.mode)
  }

  @Test
  fun manualDohDoesNotBreakVpnCoreAndFallsBackForVpnIp() {
    val profile =
      DnsProfile.defaults().copy(
        global = DnsSelection.manual("https://cloudflare-dns.com/dns-query,1.1.1.1"),
      )

    val plan = planner.compile(profile, catalog)

    assertTrue(plan.tunnelCoreCsv.contains("1.1.1.1"))
    assertEquals(AppDnsMode.DOH, plan.appHttpConfig.mode)
    assertTrue(plan.appHttpConfig.dohUrl.orEmpty().contains("cloudflare-dns.com"))
  }
}

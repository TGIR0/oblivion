package org.bepass.oblivion.dns

enum class DnsTransport {
  SYSTEM,
  PLAIN,
  UDP,
  TCP,
  DOH,
  DOT,
  DOQ,
  DOH3,
}

enum class DnsSection {
  GLOBAL,
  TUNNEL_CORE,
  VPN_INTERFACE,
  APP_HTTP,
  DIAGNOSTICS,
}

enum class DnsSelectionMode {
  INHERIT,
  SYSTEM,
  PROVIDER,
  MANUAL,
}

enum class AppDnsMode {
  SYSTEM,
  DOH,
}

data class DnsEndpoint(
  val raw: String,
  val normalized: String,
  val transport: DnsTransport,
  val host: String? = null,
  val port: Int? = null,
  val path: String? = null,
  val isIpLiteral: Boolean = false,
)

data class DnsProvider(
  val providerId: String,
  val label: String,
  val country: String? = null,
  val regionGroup: String = "International",
  val transports: Set<DnsTransport> = emptySet(),
  val plainIps: List<String> = emptyList(),
  val bootstrapIps: List<String> = emptyList(),
  val dohUrl: String? = null,
  val dotHost: String? = null,
  val doqHost: String? = null,
  val ports: Map<DnsTransport, Int> = emptyMap(),
  val tags: List<String> = emptyList(),
  val sourceUrl: String? = null,
  val verifiedAt: String? = null,
  val supportsInLayers: Set<DnsSection> = DnsSection.entries.toSet(),
  val unverified: Boolean = false,
) {
  fun supports(transport: DnsTransport): Boolean = transport in transports

  fun effectiveBootstrapIps(): List<String> = (bootstrapIps + plainIps).distinct()
}

data class DnsHealthResult(
  val providerId: String,
  val transport: DnsTransport,
  val isAvailable: Boolean,
  val latencyMs: Long? = null,
  val errorMessage: String? = null,
)

data class DnsCatalog(
  val version: Int = 1,
  val updatedAt: String? = null,
  val providers: List<DnsProvider> = emptyList(),
) {
  fun byId(providerId: String?): DnsProvider? = providers.firstOrNull {
    it.providerId.equals(providerId, ignoreCase = true)
  }
}

data class DnsSelection(
  val mode: DnsSelectionMode = DnsSelectionMode.INHERIT,
  val providerId: String? = null,
  val transport: DnsTransport? = null,
  val manualInput: String = "",
) {
  companion object {
    fun inherit(): DnsSelection = DnsSelection(mode = DnsSelectionMode.INHERIT)

    fun system(): DnsSelection =
      DnsSelection(mode = DnsSelectionMode.SYSTEM, transport = DnsTransport.SYSTEM)

    fun provider(providerId: String, transport: DnsTransport? = null): DnsSelection =
      DnsSelection(
        mode = DnsSelectionMode.PROVIDER,
        providerId = providerId,
        transport = transport,
      )

    fun manual(input: String): DnsSelection =
      DnsSelection(
        mode = DnsSelectionMode.MANUAL,
        manualInput = input,
      )
  }
}

data class DnsProfile(
  val version: Int = CURRENT_VERSION,
  val global: DnsSelection = DnsSelection.provider(DEFAULT_PROVIDER_ID, DnsTransport.PLAIN),
  val overrides: Map<DnsSection, DnsSelection> = defaultOverrides(),
) {
  companion object {
    const val CURRENT_VERSION = 1
    const val DEFAULT_PROVIDER_ID = "cloudflare"

    fun defaultOverrides(): Map<DnsSection, DnsSelection> =
      linkedMapOf(
        DnsSection.TUNNEL_CORE to DnsSelection.inherit(),
        DnsSection.VPN_INTERFACE to DnsSelection.inherit(),
        DnsSection.APP_HTTP to DnsSelection.inherit(),
        DnsSection.DIAGNOSTICS to DnsSelection.inherit(),
      )

    fun defaults(): DnsProfile = DnsProfile()
  }

  fun selectionFor(section: DnsSection): DnsSelection =
    if (section == DnsSection.GLOBAL) global else overrides[section] ?: DnsSelection.inherit()

  fun withSelection(section: DnsSection, selection: DnsSelection): DnsProfile =
    if (section == DnsSection.GLOBAL) {
      copy(global = selection)
    } else {
      copy(overrides = overrides.toMutableMap().apply { this[section] = selection })
    }
}

data class AppDnsRuntimeConfig(
  val mode: AppDnsMode = AppDnsMode.SYSTEM,
  val dohUrl: String? = null,
  val bootstrapIps: List<String> = emptyList(),
  val label: String = "System DNS",
)

data class DnsRuntimePlan(
  val tunnelCoreCsv: String,
  val vpnInterfaceCsv: String,
  val appHttpConfig: AppDnsRuntimeConfig,
  val diagnosticsConfig: AppDnsRuntimeConfig,
  val sectionSummaries: Map<DnsSection, String> = emptyMap(),
  val warnings: List<String> = emptyList(),
) {
  companion object {
    fun defaults(): DnsRuntimePlan =
      DnsRuntimePlan(
        tunnelCoreCsv = "1.1.1.1,1.0.0.1",
        vpnInterfaceCsv = "1.1.1.1,1.0.0.1",
        appHttpConfig =
          AppDnsRuntimeConfig(
            mode = AppDnsMode.DOH,
            dohUrl = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1"),
            label = "Cloudflare (DoH)",
          ),
        diagnosticsConfig =
          AppDnsRuntimeConfig(
            mode = AppDnsMode.DOH,
            dohUrl = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1"),
            label = "Cloudflare (DoH)",
          ),
      )
  }
}

data class DnsCatalogRefreshResult(
  val catalog: DnsCatalog,
  val updated: Boolean,
  val errorMessage: String? = null,
)

data class DnsUiCatalogFilters(
  val region: DnsCatalogRegionFilter = DnsCatalogRegionFilter.ALL,
  val transport: DnsTransport? = null,
  val query: String = "",
)

enum class DnsCatalogRegionFilter {
  ALL,
  IRAN,
  INTERNATIONAL,
}

val USER_CONFIGURABLE_DNS_SECTIONS: List<DnsSection> =
  listOf(
    DnsSection.TUNNEL_CORE,
    DnsSection.VPN_INTERFACE,
    DnsSection.APP_HTTP,
    DnsSection.DIAGNOSTICS,
  )

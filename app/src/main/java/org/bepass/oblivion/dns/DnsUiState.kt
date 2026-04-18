package org.bepass.oblivion.dns

data class DnsUiState(
  val profile: DnsProfile = DnsProfile.defaults(),
  val catalog: DnsCatalog = DnsCatalog(),
  val runtimePlan: DnsRuntimePlan = DnsRuntimePlan.defaults(),
  val isRefreshing: Boolean = false,
  val refreshMessage: String? = null,
  val lastRefreshEpochMs: Long? = null,
) {
  val warnings: List<String>
    get() = runtimePlan.warnings

  val pinnedProviders: List<DnsProvider>
    get() = catalog.providers.filter { provider -> provider.tags.any { it.equals("pinned", ignoreCase = true) } }

  fun filteredProviders(filters: DnsUiCatalogFilters): List<DnsProvider> {
    val normalizedQuery = filters.query.trim().lowercase()
    return catalog.providers.filter { provider ->
      val regionMatch =
        when (filters.region) {
          DnsCatalogRegionFilter.ALL -> true
          DnsCatalogRegionFilter.IRAN -> provider.regionGroup.equals("Iran", ignoreCase = true) || provider.country.equals("IR", ignoreCase = true)
          DnsCatalogRegionFilter.INTERNATIONAL ->
            !provider.regionGroup.equals("Iran", ignoreCase = true) && !provider.country.equals("IR", ignoreCase = true)
        }

      val transportMatch = filters.transport == null || provider.transports.contains(filters.transport)

      val queryMatch =
        normalizedQuery.isBlank() ||
          provider.label.lowercase().contains(normalizedQuery) ||
          provider.providerId.lowercase().contains(normalizedQuery) ||
          provider.tags.any { it.lowercase().contains(normalizedQuery) } ||
          provider.plainIps.any { it.contains(normalizedQuery) } ||
          (provider.dohUrl?.lowercase()?.contains(normalizedQuery) == true)

      regionMatch && transportMatch && queryMatch
    }
  }
}

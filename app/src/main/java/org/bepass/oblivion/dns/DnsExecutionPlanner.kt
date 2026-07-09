package org.bepass.oblivion.dns

import org.bepass.oblivion.logging.SecureLog as Log

class DnsExecutionPlanner(private val parser: DnsUriParser = DnsUriParser) {
  fun compile(profile: DnsProfile, catalog: DnsCatalog): DnsRuntimePlan =
    PlannerState(profile = profile, catalog = catalog, parser = parser).compile()

  private class PlannerState(
    private val profile: DnsProfile,
    catalog: DnsCatalog,
    private val parser: DnsUriParser,
  ) {
    private val providers = catalog.providers.associateBy { it.providerId.lowercase() }
    private val warnings = mutableListOf<String>()
    private val sectionSummaries = linkedMapOf<DnsSection, String>()
    private val defaultProvider =
      providers[DnsProfile.DEFAULT_PROVIDER_ID] ?: catalog.providers.firstOrNull()

    fun compile(): DnsRuntimePlan {
      val tunnelSelection = resolvedSelection(DnsSection.TUNNEL_CORE)
      val vpnSelection = resolvedSelection(DnsSection.VPN_INTERFACE)
      val appSelection = resolvedSelection(DnsSection.APP_HTTP)
      val diagnosticsSelection = resolvedSelection(DnsSection.DIAGNOSTICS)
      val globalSelection = normalizeGlobal(profile.global)
      sectionSummaries[DnsSection.GLOBAL] =
        buildGlobalSummary(globalSelection, providerFor(globalSelection))

      val tunnelCsv = plainDnsCsv(DnsSection.TUNNEL_CORE, tunnelSelection)
      val vpnCsv = plainDnsCsv(DnsSection.VPN_INTERFACE, vpnSelection)
      recordEmptyDnsFallbacks(tunnelCsv = tunnelCsv, vpnCsv = vpnCsv)

      val plan =
        DnsRuntimePlan(
          tunnelCoreCsv = tunnelCsv.ifBlank { fallbackIps().joinToString(",") },
          vpnInterfaceCsv = vpnCsv.ifBlank { fallbackIps().joinToString(",") },
          appHttpConfig = resolveAppConfig(DnsSection.APP_HTTP, appSelection),
          diagnosticsConfig = resolveAppConfig(DnsSection.DIAGNOSTICS, diagnosticsSelection),
          sectionSummaries = sectionSummaries,
          warnings = warnings.distinct(),
        )
      logWarnings(plan.warnings)
      return plan
    }

    private fun fallbackIps(): List<String> =
      defaultProvider?.plainIps?.ifEmpty { CLOUDFLARE_FALLBACK_IPS } ?: CLOUDFLARE_FALLBACK_IPS

    private fun providerFor(selection: DnsSelection): DnsProvider? =
      selection.providerId?.let { providers[it.lowercase()] }

    private fun normalizeGlobal(selection: DnsSelection): DnsSelection =
      when {
        selection.mode == DnsSelectionMode.INHERIT -> defaultGlobalSelection()

        selection.mode == DnsSelectionMode.PROVIDER && providerFor(selection) == null -> {
          warnings +=
            "Global DNS provider not found: ${selection.providerId}. Falling back to Cloudflare."
          defaultGlobalSelection()
        }

        selection.mode == DnsSelectionMode.MANUAL && selection.manualInput.isBlank() -> {
          warnings += "Global manual DNS is empty. Falling back to Cloudflare."
          defaultGlobalSelection()
        }

        else -> selection
      }

    private fun defaultGlobalSelection(): DnsSelection =
      DnsSelection.provider(DnsProfile.DEFAULT_PROVIDER_ID, DnsTransport.PLAIN)

    private fun resolvedSelection(section: DnsSection): DnsSelection {
      if (section == DnsSection.GLOBAL) return normalizeGlobal(profile.global)
      val direct = profile.selectionFor(section)
      if (direct.mode != DnsSelectionMode.INHERIT) return direct
      if (section != DnsSection.DIAGNOSTICS) return normalizeGlobal(profile.global)

      val appSelection = profile.selectionFor(DnsSection.APP_HTTP)
      return if (appSelection.mode == DnsSelectionMode.INHERIT) normalizeGlobal(profile.global)
      else appSelection
    }

    private fun plainDnsCsv(section: DnsSection, selection: DnsSelection): String =
      extractPlainIps(section, selection).ifEmpty { fallbackIps() }.distinct().joinToString(",")

    private fun extractPlainIps(
      section: DnsSection,
      selection: DnsSelection,
    ): List<String> =
      when (selection.mode) {
        DnsSelectionMode.SYSTEM -> systemPlainIps(section)
        DnsSelectionMode.PROVIDER -> providerPlainIps(section, selection)
        DnsSelectionMode.MANUAL -> manualPlainIps(section, selection)
        DnsSelectionMode.INHERIT -> emptyList()
      }.distinct()

    private fun systemPlainIps(section: DnsSection): List<String> {
      warnings +=
        "$section uses SYSTEM DNS, but only IP DNS is supported here. Falling back to provider IPs."
      return emptyList()
    }

    private fun providerPlainIps(
      section: DnsSection,
      selection: DnsSelection,
    ): List<String> {
      val provider = providerFor(selection)
      if (provider == null) {
        warnings +=
          "$section provider missing (${selection.providerId}). Falling back to provider IPs."
        return emptyList()
      }

      val requestedTransport = selection.transport ?: DnsTransport.PLAIN
      val plainIps = provider.plainIps
      val ips =
        if (plainIps.isNotEmpty()) {
          if (requestedTransport != DnsTransport.PLAIN) {
            warnings +=
              "$section transport ${requestedTransport.name} is not native in this layer; using provider IPs."
          }
          plainIps
        } else {
          provider.effectiveBootstrapIps().also {
            if (it.isNotEmpty()) {
              warnings +=
                "$section transport requires fallback to bootstrap/plain IPs because native transport is unsupported."
            }
          }
        }
      sectionSummaries[section] =
        buildSummaryForProvider(provider, requestedTransport, "IP fallback")
      return ips
    }

    private fun manualPlainIps(
      section: DnsSection,
      selection: DnsSelection,
    ): List<String> {
      val parsed = parser.parseBulk(selection.manualInput)
      if (parsed.errors.isNotEmpty()) {
        warnings += "$section manual DNS has invalid entries. Using valid IP entries only."
      }
      val ips =
        parsed.endpoints.mapNotNull { endpoint ->
          when {
            endpoint.transport == DnsTransport.PLAIN && !endpoint.host.isNullOrBlank() ->
              endpoint.host

            endpoint.isIpLiteral && !endpoint.host.isNullOrBlank() -> {
              warnings +=
                "$section manual ${endpoint.transport.name} entry downgraded to IP-only for this layer."
              endpoint.host
            }

            else -> {
              warnings +=
                "$section manual entry '${endpoint.normalized}' cannot be used here; ignored."
              null
            }
          }
        }
      sectionSummaries[section] =
        if (parsed.endpoints.isEmpty()) "Manual (invalid)"
        else "Manual (${parsed.endpoints.size} entries, IP-only)"
      return ips
    }

    private fun resolveAppConfig(
      section: DnsSection,
      selection: DnsSelection,
    ): AppDnsRuntimeConfig =
      when (selection.mode) {
        DnsSelectionMode.SYSTEM -> systemAppConfig(section, "System DNS")
        DnsSelectionMode.PROVIDER -> providerAppConfig(section, selection)
        DnsSelectionMode.MANUAL -> manualAppConfig(section, selection)
        DnsSelectionMode.INHERIT -> {
          warnings += "$section selection unexpectedly unresolved. Falling back to system DNS."
          systemAppConfig(section, "System DNS")
        }
      }

    private fun providerAppConfig(
      section: DnsSection,
      selection: DnsSelection,
    ): AppDnsRuntimeConfig {
      val provider = providerFor(selection)
      if (provider == null) {
        warnings +=
          "$section provider missing (${selection.providerId}). Falling back to system DNS."
        return systemAppConfig(section, "System DNS (provider missing)")
      }

      val requestedTransport = selection.transport ?: DnsTransport.DOH
      if (requestedTransport == DnsTransport.SYSTEM) {
        return systemAppConfig(
          section = section,
          summary = "${provider.label} (system)",
          label = "${provider.label} via System DNS",
        )
      }
      if (!provider.dohUrl.isNullOrBlank()) {
        return dohProviderAppConfig(section, provider, requestedTransport)
      }

      warnings +=
        "$section provider ${provider.label} has no DoH endpoint. Falling back to system DNS."
      return systemAppConfig(
        section = section,
        summary = "${provider.label} (System fallback)",
        label = "${provider.label} via System DNS",
      )
    }

    private fun dohProviderAppConfig(
      section: DnsSection,
      provider: DnsProvider,
      requestedTransport: DnsTransport,
    ): AppDnsRuntimeConfig {
      val downgraded =
        requestedTransport != DnsTransport.DOH && requestedTransport != DnsTransport.PLAIN
      if (downgraded) {
        warnings +=
          "$section transport ${requestedTransport.name} is not native in app HTTP; using DoH for ${provider.label}."
      }
      val label =
        if (downgraded) "${provider.label} (${requestedTransport.name} -> DoH fallback)"
        else "${provider.label} (DoH)"
      sectionSummaries[section] = label
      return AppDnsRuntimeConfig(
        mode = AppDnsMode.DOH,
        dohUrl = provider.dohUrl,
        bootstrapIps = provider.effectiveBootstrapIps(),
        label = label,
      )
    }

    private fun manualAppConfig(
      section: DnsSection,
      selection: DnsSelection,
    ): AppDnsRuntimeConfig {
      val parsed = parser.parseBulk(selection.manualInput)
      if (parsed.errors.isNotEmpty()) warnings += "$section manual DNS contains invalid entries."

      if (parsed.endpoints.any { it.transport == DnsTransport.SYSTEM }) {
        return systemAppConfig(section, "Manual (System)", "Manual System DNS")
      }
      val dohEndpoint = parsed.endpoints.firstOrNull { it.transport == DnsTransport.DOH }
      if (dohEndpoint != null) {
        sectionSummaries[section] = "Manual DoH"
        return AppDnsRuntimeConfig(
          mode = AppDnsMode.DOH,
          dohUrl = dohEndpoint.normalized,
          bootstrapIps =
            parsed.endpoints
              .filter { it.isIpLiteral && !it.host.isNullOrBlank() }
              .mapNotNull { it.host }
              .distinct(),
          label = "Manual DoH",
        )
      }

      warnings +=
        if (parsed.endpoints.isNotEmpty()) {
          "$section manual transports are not natively supported in app HTTP. Falling back to system DNS."
        } else {
          "$section manual DNS is empty/invalid. Falling back to system DNS."
        }
      return systemAppConfig(section, "System DNS (manual fallback)")
    }

    private fun systemAppConfig(
      section: DnsSection,
      summary: String,
      label: String = "System DNS",
    ): AppDnsRuntimeConfig {
      sectionSummaries[section] = summary
      return AppDnsRuntimeConfig(mode = AppDnsMode.SYSTEM, label = label)
    }

    private fun recordEmptyDnsFallbacks(tunnelCsv: String, vpnCsv: String) {
      if (tunnelCsv.isBlank()) {
        warnings += "Tunnel/Core DNS was empty after planning; using Cloudflare fallback."
      }
      if (vpnCsv.isBlank()) {
        warnings += "VPN Interface DNS was empty after planning; using Cloudflare fallback."
      }
    }

    private fun buildGlobalSummary(
      selection: DnsSelection,
      provider: DnsProvider?,
    ): String =
      when (selection.mode) {
        DnsSelectionMode.SYSTEM -> "System DNS"
        DnsSelectionMode.PROVIDER -> {
          val name = provider?.label ?: (selection.providerId ?: "Unknown")
          val transport = (selection.transport ?: DnsTransport.PLAIN).name
          "$name ($transport)"
        }

        DnsSelectionMode.MANUAL -> {
          val count = parser.parseBulk(selection.manualInput).endpoints.size
          if (count > 0) "Manual ($count entries)" else "Manual (invalid)"
        }

        DnsSelectionMode.INHERIT -> "Cloudflare (PLAIN)"
      }

    private fun buildSummaryForProvider(
      provider: DnsProvider,
      transport: DnsTransport,
      suffix: String,
    ): String = "${provider.label} (${transport.name}, $suffix)"

    private fun logWarnings(values: List<String>) {
      for (warning in values) {
        try {
          Log.w(TAG, warning)
        } catch (_: RuntimeException) {
          // Android logging is a stub in local JVM tests.
        }
      }
    }
  }

  private companion object {
    const val TAG = "DnsPlanner"
    val CLOUDFLARE_FALLBACK_IPS = listOf("1.1.1.1", "1.0.0.1")
  }
}

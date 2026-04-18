package org.bepass.oblivion.dns

import android.util.Log

class DnsExecutionPlanner(
  private val parser: DnsUriParser = DnsUriParser,
) {
  fun compile(profile: DnsProfile, catalog: DnsCatalog): DnsRuntimePlan {
    val providers = catalog.providers.associateBy { it.providerId.lowercase() }
    val warnings = mutableListOf<String>()
    val sectionSummaries = linkedMapOf<DnsSection, String>()

    val defaultProvider =
      providers[DnsProfile.DEFAULT_PROVIDER_ID] ?: catalog.providers.firstOrNull()

    fun fallbackIps(): List<String> = defaultProvider?.plainIps?.ifEmpty { listOf("1.1.1.1", "1.0.0.1") } ?: listOf("1.1.1.1", "1.0.0.1")

    fun providerFor(selection: DnsSelection): DnsProvider? =
      selection.providerId?.let { providers[it.lowercase()] }

    fun normalizeGlobal(selection: DnsSelection): DnsSelection {
      if (selection.mode == DnsSelectionMode.INHERIT) return DnsSelection.provider(DnsProfile.DEFAULT_PROVIDER_ID, DnsTransport.PLAIN)
      if (selection.mode == DnsSelectionMode.PROVIDER && providerFor(selection) == null) {
        warnings += "Global DNS provider not found: ${selection.providerId}. Falling back to Cloudflare."
        return DnsSelection.provider(DnsProfile.DEFAULT_PROVIDER_ID, DnsTransport.PLAIN)
      }
      if (selection.mode == DnsSelectionMode.MANUAL && selection.manualInput.isBlank()) {
        warnings += "Global manual DNS is empty. Falling back to Cloudflare."
        return DnsSelection.provider(DnsProfile.DEFAULT_PROVIDER_ID, DnsTransport.PLAIN)
      }
      return selection
    }

    fun resolvedSelection(section: DnsSection): DnsSelection {
      if (section == DnsSection.GLOBAL) return normalizeGlobal(profile.global)
      val direct = profile.selectionFor(section)
      if (direct.mode != DnsSelectionMode.INHERIT) return direct
      if (section == DnsSection.DIAGNOSTICS) {
        val appSelection = profile.selectionFor(DnsSection.APP_HTTP)
        return if (appSelection.mode == DnsSelectionMode.INHERIT) normalizeGlobal(profile.global) else appSelection
      }
      return normalizeGlobal(profile.global)
    }

    fun extractPlainIps(section: DnsSection, selection: DnsSelection): List<String> {
      val ips = mutableListOf<String>()
      when (selection.mode) {
        DnsSelectionMode.SYSTEM -> {
          warnings += "$section uses SYSTEM DNS, but only IP DNS is supported here. Falling back to provider IPs."
        }

        DnsSelectionMode.PROVIDER -> {
          val provider = providerFor(selection)
          if (provider == null) {
            warnings += "$section provider missing (${selection.providerId}). Falling back to provider IPs."
          } else {
            ips += provider.plainIps
            if (ips.isEmpty()) {
              ips += provider.effectiveBootstrapIps()
              if (ips.isNotEmpty()) {
                warnings += "$section transport requires fallback to bootstrap/plain IPs because native transport is unsupported."
              }
            } else if ((selection.transport ?: DnsTransport.PLAIN) != DnsTransport.PLAIN) {
              warnings += "$section transport ${(selection.transport ?: DnsTransport.PLAIN).name} is not native in this layer; using provider IPs."
            }
            sectionSummaries[section] = buildSummaryForProvider(provider, selection.transport ?: DnsTransport.PLAIN, "IP fallback")
          }
        }

        DnsSelectionMode.MANUAL -> {
          val parsed = parser.parseBulk(selection.manualInput)
          if (parsed.errors.isNotEmpty()) {
            warnings += "$section manual DNS has invalid entries. Using valid IP entries only."
          }
          for (endpoint in parsed.endpoints) {
            when {
              endpoint.transport == DnsTransport.PLAIN && !endpoint.host.isNullOrBlank() -> ips += endpoint.host
              endpoint.isIpLiteral && !endpoint.host.isNullOrBlank() -> {
                ips += endpoint.host
                warnings += "$section manual ${endpoint.transport.name} entry downgraded to IP-only for this layer."
              }
              else -> warnings += "$section manual entry '${endpoint.normalized}' cannot be used here; ignored."
            }
          }
          sectionSummaries[section] =
            if (parsed.endpoints.isEmpty()) "Manual (invalid)"
            else "Manual (${parsed.endpoints.size} entries, IP-only)"
        }

        DnsSelectionMode.INHERIT -> {
          // Resolved earlier.
        }
      }
      return ips.distinct()
    }

    fun resolveAppConfig(section: DnsSection, selection: DnsSelection): AppDnsRuntimeConfig {
      when (selection.mode) {
        DnsSelectionMode.SYSTEM -> {
          sectionSummaries[section] = "System DNS"
          return AppDnsRuntimeConfig(mode = AppDnsMode.SYSTEM, label = "System DNS")
        }

        DnsSelectionMode.PROVIDER -> {
          val provider = providerFor(selection)
          if (provider == null) {
            warnings += "$section provider missing (${selection.providerId}). Falling back to system DNS."
            sectionSummaries[section] = "System DNS (provider missing)"
            return AppDnsRuntimeConfig(mode = AppDnsMode.SYSTEM, label = "System DNS")
          }

          val requestedTransport = selection.transport ?: DnsTransport.DOH
          if (requestedTransport == DnsTransport.SYSTEM) {
            sectionSummaries[section] = "${provider.label} (system)"
            return AppDnsRuntimeConfig(mode = AppDnsMode.SYSTEM, label = "${provider.label} via System DNS")
          }

          if (!provider.dohUrl.isNullOrBlank()) {
            val downgraded =
              requestedTransport != DnsTransport.DOH && requestedTransport != DnsTransport.PLAIN
            if (downgraded) {
              warnings += "$section transport ${requestedTransport.name} is not native in app HTTP; using DoH for ${provider.label}."
            }
            val label =
              if (downgraded) "${provider.label} (${requestedTransport.name} -> DoH fallback)" else "${provider.label} (DoH)"
            sectionSummaries[section] = label
            return AppDnsRuntimeConfig(
              mode = AppDnsMode.DOH,
              dohUrl = provider.dohUrl,
              bootstrapIps = provider.effectiveBootstrapIps(),
              label = label,
            )
          }

          warnings += "$section provider ${provider.label} has no DoH endpoint. Falling back to system DNS."
          sectionSummaries[section] = "${provider.label} (System fallback)"
          return AppDnsRuntimeConfig(mode = AppDnsMode.SYSTEM, label = "${provider.label} via System DNS")
        }

        DnsSelectionMode.MANUAL -> {
          val parsed = parser.parseBulk(selection.manualInput)
          if (parsed.errors.isNotEmpty()) {
            warnings += "$section manual DNS contains invalid entries."
          }
          val systemToken = parsed.endpoints.firstOrNull { it.transport == DnsTransport.SYSTEM }
          if (systemToken != null) {
            sectionSummaries[section] = "Manual (System)"
            return AppDnsRuntimeConfig(mode = AppDnsMode.SYSTEM, label = "Manual System DNS")
          }
          val dohEndpoint =
            parsed.endpoints.firstOrNull {
              it.transport == DnsTransport.DOH
            }
          if (dohEndpoint != null) {
            sectionSummaries[section] = "Manual DoH"
            return AppDnsRuntimeConfig(
              mode = AppDnsMode.DOH,
              dohUrl = dohEndpoint.normalized,
              bootstrapIps = parsed.endpoints.filter { it.isIpLiteral && !it.host.isNullOrBlank() }.mapNotNull { it.host }.distinct(),
              label = "Manual DoH",
            )
          }

          if (parsed.endpoints.isNotEmpty()) {
            warnings += "$section manual transports are not natively supported in app HTTP. Falling back to system DNS."
          } else {
            warnings += "$section manual DNS is empty/invalid. Falling back to system DNS."
          }
          sectionSummaries[section] = "System DNS (manual fallback)"
          return AppDnsRuntimeConfig(mode = AppDnsMode.SYSTEM, label = "System DNS")
        }

        DnsSelectionMode.INHERIT -> {
          warnings += "$section selection unexpectedly unresolved. Falling back to system DNS."
          sectionSummaries[section] = "System DNS"
          return AppDnsRuntimeConfig(mode = AppDnsMode.SYSTEM, label = "System DNS")
        }
      }
    }

    val tunnelSelection = resolvedSelection(DnsSection.TUNNEL_CORE)
    val vpnSelection = resolvedSelection(DnsSection.VPN_INTERFACE)
    val appSelection = resolvedSelection(DnsSection.APP_HTTP)
    val diagnosticsSelection = resolvedSelection(DnsSection.DIAGNOSTICS)
    val globalSelection = normalizeGlobal(profile.global)
    sectionSummaries[DnsSection.GLOBAL] = buildGlobalSummary(globalSelection, providerFor(globalSelection))

    val tunnelCsv = extractPlainIps(DnsSection.TUNNEL_CORE, tunnelSelection).ifEmpty { fallbackIps() }.distinct().joinToString(",")
    val vpnCsv = extractPlainIps(DnsSection.VPN_INTERFACE, vpnSelection).ifEmpty { fallbackIps() }.distinct().joinToString(",")
    if (tunnelCsv.isBlank()) {
      warnings += "Tunnel/Core DNS was empty after planning; using Cloudflare fallback."
    }
    if (vpnCsv.isBlank()) {
      warnings += "VPN Interface DNS was empty after planning; using Cloudflare fallback."
    }

    val appConfig = resolveAppConfig(DnsSection.APP_HTTP, appSelection)
    val diagnosticsConfig = resolveAppConfig(DnsSection.DIAGNOSTICS, diagnosticsSelection)

    val plan =
      DnsRuntimePlan(
        tunnelCoreCsv = if (tunnelCsv.isBlank()) fallbackIps().joinToString(",") else tunnelCsv,
        vpnInterfaceCsv = if (vpnCsv.isBlank()) fallbackIps().joinToString(",") else vpnCsv,
        appHttpConfig = appConfig,
        diagnosticsConfig = diagnosticsConfig,
        sectionSummaries = sectionSummaries,
        warnings = warnings.distinct(),
      )

    for (warning in plan.warnings) {
      runCatching { Log.w(TAG, warning) }
    }

    return plan
  }

  private fun buildGlobalSummary(selection: DnsSelection, provider: DnsProvider?): String =
    when (selection.mode) {
      DnsSelectionMode.SYSTEM -> "System DNS"
      DnsSelectionMode.PROVIDER -> {
        val name = provider?.label ?: (selection.providerId ?: "Unknown")
        val transport = (selection.transport ?: DnsTransport.PLAIN).name
        "$name ($transport)"
      }

      DnsSelectionMode.MANUAL -> {
        val count =
          parser.parseBulk(selection.manualInput).endpoints.size
        if (count > 0) "Manual ($count entries)" else "Manual (invalid)"
      }

      DnsSelectionMode.INHERIT -> "Cloudflare (PLAIN)"
    }

  private fun buildSummaryForProvider(provider: DnsProvider, transport: DnsTransport, suffix: String): String =
    "${provider.label} (${transport.name}, $suffix)"

  companion object {
    private const val TAG = "DnsPlanner"
  }
}

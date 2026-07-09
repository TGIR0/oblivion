package org.bepass.oblivion.dns

import org.json.JSONArray
import org.json.JSONObject

object DnsJsonCodec {
  fun profileToJson(profile: DnsProfile): String {
    val root =
      JSONObject()
        .put("version", profile.version)
        .put("global", selectionToJson(profile.global))
        .put(
          "overrides",
          JSONObject().apply {
            for ((section, selection) in profile.overrides) {
              put(section.name, selectionToJson(selection))
            }
          },
        )
    return root.toString()
  }

  fun profileFromJson(raw: String): DnsProfile {
    val root = JSONObject(raw)
    val overridesJson = root.optJSONObject("overrides")
    val overrides = DnsProfile.defaultOverrides().toMutableMap()
    if (overridesJson != null) {
      for (section in USER_CONFIGURABLE_DNS_SECTIONS) {
        val selectionJson = overridesJson.optJSONObject(section.name) ?: continue
        overrides[section] = selectionFromJson(selectionJson)
      }
    }

    return DnsProfile(
      version = root.optInt("version", DnsProfile.CURRENT_VERSION),
      global =
        root.optJSONObject("global")?.let(::selectionFromJson) ?: DnsProfile.defaults().global,
      overrides = overrides,
    )
  }

  fun catalogToJson(catalog: DnsCatalog): String {
    val providers =
      JSONArray().apply {
        catalog.providers.forEach { provider ->
          put(
            JSONObject()
              .put("providerId", provider.providerId)
              .put("label", provider.label)
              .put("country", provider.country)
              .put("regionGroup", provider.regionGroup)
              .put("transports", JSONArray(provider.transports.map { it.name }))
              .put("plainIps", JSONArray(provider.plainIps))
              .put("bootstrapIps", JSONArray(provider.bootstrapIps))
              .put("dohUrl", provider.dohUrl)
              .put("dotHost", provider.dotHost)
              .put("doqHost", provider.doqHost)
              .put(
                "ports",
                JSONObject().apply { provider.ports.forEach { (k, v) -> put(k.name, v) } },
              )
              .put("tags", JSONArray(provider.tags))
              .put("sourceUrl", provider.sourceUrl)
              .put("verifiedAt", provider.verifiedAt)
              .put("supportsInLayers", JSONArray(provider.supportsInLayers.map { it.name }))
              .put("unverified", provider.unverified)
          )
        }
      }

    return JSONObject()
      .put("version", catalog.version)
      .put("updatedAt", catalog.updatedAt)
      .put("providers", providers)
      .toString()
  }

  fun catalogFromJson(raw: String): DnsCatalog {
    val root = JSONObject(raw)
    val providersJson = root.optJSONArray("providers") ?: JSONArray()
    val providers = buildList {
      for (i in 0 until providersJson.length()) {
        val item = providersJson.optJSONObject(i) ?: continue
        add(providerFromJson(item))
      }
    }

    return DnsCatalog(
      version = root.optInt("version", 1),
      updatedAt = root.nullableString("updatedAt"),
      providers = providers,
    )
  }

  fun runtimePlanToJson(plan: DnsRuntimePlan): String =
    JSONObject()
      .put("tunnelCoreCsv", plan.tunnelCoreCsv)
      .put("vpnInterfaceCsv", plan.vpnInterfaceCsv)
      .put("appHttpConfig", appRuntimeToJson(plan.appHttpConfig))
      .put("diagnosticsConfig", appRuntimeToJson(plan.diagnosticsConfig))
      .put(
        "sectionSummaries",
        JSONObject().apply {
          plan.sectionSummaries.forEach { (section, summary) -> put(section.name, summary) }
        },
      )
      .put("warnings", JSONArray(plan.warnings))
      .toString()

  fun runtimePlanFromJson(raw: String): DnsRuntimePlan {
    val root = JSONObject(raw)
    val sectionSummaries =
      linkedMapOf<DnsSection, String>().apply {
        val summariesJson = root.optJSONObject("sectionSummaries") ?: JSONObject()
        for (section in DnsSection.entries) {
          val summary = summariesJson.optString(section.name)
          if (summary.isNotBlank()) {
            this[section] = summary
          }
        }
      }
    return DnsRuntimePlan(
      tunnelCoreCsv = root.optString("tunnelCoreCsv", "1.1.1.1,1.0.0.1"),
      vpnInterfaceCsv = root.optString("vpnInterfaceCsv", "1.1.1.1,1.0.0.1"),
      appHttpConfig = appRuntimeFromJson(root.optJSONObject("appHttpConfig")),
      diagnosticsConfig = appRuntimeFromJson(root.optJSONObject("diagnosticsConfig")),
      sectionSummaries = sectionSummaries,
      warnings = root.optJSONArray("warnings").toStringList(),
    )
  }

  private fun appRuntimeToJson(config: AppDnsRuntimeConfig): JSONObject =
    JSONObject()
      .put("mode", config.mode.name)
      .put("dohUrl", config.dohUrl)
      .put("bootstrapIps", JSONArray(config.bootstrapIps))
      .put("label", config.label)

  private fun appRuntimeFromJson(json: JSONObject?): AppDnsRuntimeConfig {
    if (json == null) return DnsRuntimePlan.defaults().appHttpConfig
    return AppDnsRuntimeConfig(
      mode = enumOrDefault(json.optString("mode"), AppDnsMode.SYSTEM),
      dohUrl = json.nullableString("dohUrl"),
      bootstrapIps = json.optJSONArray("bootstrapIps").toStringList(),
      label = json.optString("label", "System DNS"),
    )
  }

  private fun selectionToJson(selection: DnsSelection): JSONObject =
    JSONObject()
      .put("mode", selection.mode.name)
      .put("providerId", selection.providerId)
      .put("transport", selection.transport?.name)
      .put("manualInput", selection.manualInput)

  private fun selectionFromJson(json: JSONObject): DnsSelection =
    DnsSelection(
      mode = enumOrDefault(json.optString("mode"), DnsSelectionMode.INHERIT),
      providerId = json.nullableString("providerId"),
      transport =
        json.nullableString("transport")?.let {
          enumOrNull<DnsTransport>(it)
        },
      manualInput = json.optString("manualInput", ""),
    )

  private fun providerFromJson(json: JSONObject): DnsProvider =
    DnsProvider(
      providerId = json.optString("providerId"),
      label = json.optString("label"),
      country = json.nullableString("country"),
      regionGroup = json.optString("regionGroup", "International"),
      transports =
        json
          .optJSONArray("transports")
          .toStringList()
          .mapNotNull { enumOrNull<DnsTransport>(it) }
          .toSet(),
      plainIps = json.optJSONArray("plainIps").toStringList(),
      bootstrapIps = json.optJSONArray("bootstrapIps").toStringList(),
      dohUrl = json.nullableString("dohUrl"),
      dotHost = json.nullableString("dotHost"),
      doqHost = json.nullableString("doqHost"),
      ports =
        buildMap {
          val portsJson = json.optJSONObject("ports") ?: JSONObject()
          for (key in portsJson.keys()) {
            val transport = enumOrNull<DnsTransport>(key) ?: continue
            put(transport, portsJson.optInt(key))
          }
        },
      tags = json.optJSONArray("tags").toStringList(),
      sourceUrl = json.nullableString("sourceUrl"),
      verifiedAt = json.nullableString("verifiedAt"),
      supportsInLayers =
        json
          .optJSONArray("supportsInLayers")
          .toStringList()
          .mapNotNull { enumOrNull<DnsSection>(it) }
          .toSet()
          .ifEmpty { DnsSection.entries.toSet() },
      unverified = json.optBoolean("unverified", false),
    )

  private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
      for (i in 0 until length()) {
        val value = optString(i)
        if (value.isNotBlank()) add(value)
      }
    }
  }

  private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) {
      null
    } else {
      optString(key).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

  private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }

  private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
    enumOrNull<T>(value) ?: default
}

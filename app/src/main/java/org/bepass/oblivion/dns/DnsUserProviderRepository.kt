package org.bepass.oblivion.dns

import org.bepass.oblivion.utils.FileManager
import timber.log.Timber

object DnsUserProviderRepository {
  internal const val KEY_CUSTOM_PROVIDERS = "USERSETTING_dns_custom_providers"
  internal const val KEY_PROVIDER_OVERRIDES = "USERSETTING_dns_provider_overrides"
  internal const val KEY_HIDDEN_PROVIDERS = "USERSETTING_dns_hidden_providers"

  fun applyTo(baseCatalog: DnsCatalog): DnsCatalog {
    val overrides = loadProviders(KEY_PROVIDER_OVERRIDES).associateBy { it.providerId.lowercase() }
    val hidden =
      FileManager.getStringSet(KEY_HIDDEN_PROVIDERS, emptySet()).map { it.lowercase() }.toSet()
    val custom = loadProviders(KEY_CUSTOM_PROVIDERS)
    val providers =
      baseCatalog.providers
        .asSequence()
        .filterNot { it.providerId.lowercase() in hidden }
        .map { overrides[it.providerId.lowercase()] ?: it }
        .plus(custom)
        .distinctBy { it.providerId.lowercase() }
        .sortedWith(
          compareByDescending<DnsProvider> { provider ->
              provider.tags.any { it.equals("pinned", ignoreCase = true) }
            }
            .thenBy { it.label.lowercase() }
        )
        .toList()
    return baseCatalog.copy(providers = providers)
  }

  fun save(baseCatalog: DnsCatalog, provider: DnsProvider) {
    val isDefault =
      baseCatalog.providers.any { it.providerId.equals(provider.providerId, ignoreCase = true) }
    val key = if (isDefault) KEY_PROVIDER_OVERRIDES else KEY_CUSTOM_PROVIDERS
    val updated =
      loadProviders(key).filterNot {
        it.providerId.equals(provider.providerId, ignoreCase = true)
      } + provider
    saveProviders(key, updated)

    if (isDefault) {
      val hidden = FileManager.getStringSet(KEY_HIDDEN_PROVIDERS, emptySet()).toMutableSet()
      hidden.removeAll { it.equals(provider.providerId, ignoreCase = true) }
      FileManager.set(KEY_HIDDEN_PROVIDERS, hidden)
    }
  }

  fun delete(baseCatalog: DnsCatalog, providerId: String) {
    val isDefault =
      baseCatalog.providers.any { it.providerId.equals(providerId, ignoreCase = true) }
    if (isDefault) {
      val hidden = FileManager.getStringSet(KEY_HIDDEN_PROVIDERS, emptySet()).toMutableSet()
      hidden += providerId
      FileManager.set(KEY_HIDDEN_PROVIDERS, hidden)
      saveProviders(
        KEY_PROVIDER_OVERRIDES,
        loadProviders(KEY_PROVIDER_OVERRIDES).filterNot {
          it.providerId.equals(providerId, ignoreCase = true)
        },
      )
    } else {
      saveProviders(
        KEY_CUSTOM_PROVIDERS,
        loadProviders(KEY_CUSTOM_PROVIDERS).filterNot {
          it.providerId.equals(providerId, ignoreCase = true)
        },
      )
    }
  }

  fun restoreDefaults() {
    FileManager.set(KEY_PROVIDER_OVERRIDES, "")
    FileManager.set(KEY_HIDDEN_PROVIDERS, emptySet<String>())
  }

  fun isCustom(baseCatalog: DnsCatalog, providerId: String): Boolean =
    baseCatalog.providers.none { it.providerId.equals(providerId, ignoreCase = true) }

  private fun loadProviders(key: String): List<DnsProvider> {
    val raw = FileManager.getString(key).trim()
    if (raw.isBlank()) return emptyList()
    return runCatching { DnsJsonCodec.catalogFromJson(raw).providers }
      .onFailure { Timber.w(it, "Failed to parse user DNS providers") }
      .getOrDefault(emptyList())
  }

  private fun saveProviders(key: String, providers: List<DnsProvider>) {
    val raw =
      if (providers.isEmpty()) {
        ""
      } else {
        DnsJsonCodec.catalogToJson(DnsCatalog(providers = providers))
      }
    FileManager.set(key, raw)
  }
}

package org.bepass.oblivion.dns

import android.content.Context
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bepass.oblivion.utils.FileManager

object DnsCatalogRepository {
  private const val TAG = "DnsCatalogRepo"
  private const val ASSET_FILE_NAME = "dns_catalog.v1.json"
  private const val REFRESH_URL =
    "https://raw.githubusercontent.com/bepass-org/oblivion/main/app/src/main/assets/dns_catalog.v1.json"

  const val KEY_CATALOG_CACHE_JSON = "USERSETTING_dns_catalog_cache_json"
  const val KEY_CATALOG_ETAG = "USERSETTING_dns_catalog_etag"
  const val KEY_CATALOG_LAST_REFRESH_MS = "USERSETTING_dns_catalog_last_refresh_epoch_ms"

  private val initLock = Any()
  @Volatile private var initialized = false

  private val _catalogFlow = MutableStateFlow(fallbackCatalog())
  val catalogFlow: StateFlow<DnsCatalog> = _catalogFlow.asStateFlow()

  private val _lastRefreshEpochMsFlow = MutableStateFlow<Long?>(null)
  val lastRefreshEpochMsFlow: StateFlow<Long?> = _lastRefreshEpochMsFlow.asStateFlow()

  private val httpClient by lazy {
    OkHttpClient.Builder().build()
  }

  fun initialize(context: Context) {
    if (initialized) return
    synchronized(initLock) {
      if (initialized) return
      FileManager.initialize(context.applicationContext)
      _catalogFlow.value = loadBestCatalog(context.applicationContext)
      val lastRefresh = FileManager.getLong(KEY_CATALOG_LAST_REFRESH_MS, 0L)
      _lastRefreshEpochMsFlow.value = lastRefresh.takeIf { it > 0L }
      initialized = true
    }
  }

  fun currentCatalog(): DnsCatalog = _catalogFlow.value

  suspend fun refreshCatalog(context: Context): DnsCatalogRefreshResult =
    withContext(Dispatchers.IO) {
      initialize(context.applicationContext)

      val existing = _catalogFlow.value
      val etag = FileManager.getString(KEY_CATALOG_ETAG).trim().ifBlank { null }
      val requestBuilder = Request.Builder().url(REFRESH_URL)
      if (etag != null) {
        requestBuilder.header("If-None-Match", etag)
      }

      return@withContext try {
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
          if (response.code == 304) {
            val now = System.currentTimeMillis()
            FileManager.set(KEY_CATALOG_LAST_REFRESH_MS, now)
            _lastRefreshEpochMsFlow.value = now
            DnsCatalogRefreshResult(
              catalog = existing,
              updated = false,
            )
          } else if (!response.isSuccessful) {
            DnsCatalogRefreshResult(
              catalog = existing,
              updated = false,
              errorMessage = "HTTP ${response.code}",
            )
          } else {
            val body = response.body.string()
            val parsed = DnsJsonCodec.catalogFromJson(body)
            if (parsed.providers.isEmpty()) {
              DnsCatalogRefreshResult(
                catalog = existing,
                updated = false,
                errorMessage = "Catalog was empty",
              )
            } else {
              val now = System.currentTimeMillis()
              FileManager.set(KEY_CATALOG_CACHE_JSON, body)
              response.header("ETag")?.trim()?.takeIf { it.isNotBlank() }?.let {
                FileManager.set(KEY_CATALOG_ETAG, it)
              }
              FileManager.set(KEY_CATALOG_LAST_REFRESH_MS, now)
              _catalogFlow.value = parsed
              _lastRefreshEpochMsFlow.value = now
              DnsCatalogRefreshResult(
                catalog = parsed,
                updated = true,
              )
            }
          }
        }
      } catch (e: Throwable) {
        Log.w(TAG, "Catalog refresh failed", e)
        DnsCatalogRefreshResult(
          catalog = existing,
          updated = false,
          errorMessage = e.message ?: e::class.java.simpleName,
        )
      }
    }

  private fun loadBestCatalog(context: Context): DnsCatalog {
    val cached = FileManager.getString(KEY_CATALOG_CACHE_JSON).trim()
    if (cached.isNotBlank()) {
      runCatching { DnsJsonCodec.catalogFromJson(cached) }
        .onSuccess { parsed ->
          if (parsed.providers.isNotEmpty()) return parsed
        }
        .onFailure { Log.w(TAG, "Failed to parse cached DNS catalog", it) }
    }

    val bundled =
      runCatching { context.assets.open(ASSET_FILE_NAME).bufferedReader().use { it.readText() } }
        .onFailure { Log.w(TAG, "Failed to read bundled DNS catalog asset", it) }
        .getOrNull()

    if (!bundled.isNullOrBlank()) {
      runCatching { DnsJsonCodec.catalogFromJson(bundled) }
        .onSuccess { parsed ->
          if (parsed.providers.isNotEmpty()) return parsed
        }
        .onFailure { Log.w(TAG, "Failed to parse bundled DNS catalog", it) }
    }

    return fallbackCatalog()
  }

  private fun fallbackCatalog(): DnsCatalog =
    DnsCatalog(
      version = 1,
      updatedAt = null,
      providers =
        listOf(
          DnsProvider(
            providerId = "cloudflare",
            label = "Cloudflare",
            regionGroup = "International",
            transports = setOf(DnsTransport.PLAIN, DnsTransport.UDP, DnsTransport.TCP, DnsTransport.DOH, DnsTransport.DOT, DnsTransport.DOQ),
            plainIps = listOf("1.1.1.1", "1.0.0.1"),
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1"),
            dohUrl = "https://cloudflare-dns.com/dns-query",
            dotHost = "1dot1dot1dot1.cloudflare-dns.com",
            doqHost = "1dot1dot1dot1.cloudflare-dns.com",
            ports = mapOf(DnsTransport.DOT to 853, DnsTransport.DOQ to 853, DnsTransport.DOH to 443),
            tags = listOf("fast", "default", "global"),
          ),
          DnsProvider(
            providerId = "adguard-unfiltered",
            label = "AdGuard Unfiltered",
            regionGroup = "International",
            transports = setOf(DnsTransport.PLAIN, DnsTransport.UDP, DnsTransport.TCP, DnsTransport.DOH, DnsTransport.DOT, DnsTransport.DOQ),
            plainIps = listOf("94.140.14.140", "94.140.14.141"),
            bootstrapIps = listOf("94.140.14.140", "94.140.14.141"),
            dohUrl = "https://unfiltered.adguard-dns.com/dns-query",
            dotHost = "unfiltered.adguard-dns.com",
            doqHost = "unfiltered.adguard-dns.com",
            tags = listOf("unfiltered", "privacy"),
          ),
          DnsProvider(
            providerId = "shecan",
            label = "Shecan",
            country = "IR",
            regionGroup = "Iran",
            transports = setOf(DnsTransport.PLAIN, DnsTransport.UDP, DnsTransport.TCP),
            plainIps = listOf("178.22.122.100", "185.51.200.2"),
            bootstrapIps = listOf("178.22.122.100", "185.51.200.2"),
            tags = listOf("iran", "sanctions-bypass"),
          ),
          DnsProvider(
            providerId = "begzar",
            label = "Begzar",
            country = "IR",
            regionGroup = "Iran",
            transports = setOf(DnsTransport.PLAIN, DnsTransport.UDP, DnsTransport.TCP),
            plainIps = listOf("185.55.226.26", "185.55.225.25"),
            bootstrapIps = listOf("185.55.226.26", "185.55.225.25"),
            tags = listOf("iran", "sanctions-bypass"),
          ),
        ),
    )
}

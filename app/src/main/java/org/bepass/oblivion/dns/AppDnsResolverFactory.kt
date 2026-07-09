package org.bepass.oblivion.dns

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.bepass.oblivion.logging.SecureLog as Log

object AppDnsResolverFactory {
  fun createAppHttpDns(): Dns = ConfigurableAppDns(DnsSection.APP_HTTP)

  fun createDiagnosticsDns(): Dns = ConfigurableAppDns(DnsSection.DIAGNOSTICS)

  private class ConfigurableAppDns(private val section: DnsSection) : Dns {
    private var lastRawConfigKey: String? = null
    private var lastDelegate: Dns? = null

    override fun lookup(hostname: String): List<InetAddress> {
      val runtime =
        runCatching { DnsProfileRepository.loadPersistedRuntimePlanOrDefault() }
          .onFailure { Log.w(TAG, "Failed to load runtime DNS plan; using system DNS", it) }
          .getOrElse { DnsRuntimePlan.defaults() }

      val config =
        when (section) {
          DnsSection.APP_HTTP -> runtime.appHttpConfig
          DnsSection.DIAGNOSTICS -> runtime.diagnosticsConfig
          else -> runtime.appHttpConfig
        }

      val configKey =
        "${config.mode}|${config.dohUrl.orEmpty()}|${config.bootstrapIps.joinToString(",")}"
      val delegate =
        if (configKey == lastRawConfigKey && lastDelegate != null) {
          lastDelegate!!
        } else {
          buildDelegate(config).also {
            lastRawConfigKey = configKey
            lastDelegate = it
          }
        }

      return try {
        delegate.lookup(hostname)
      } catch (lookupFailure: UnknownHostException) {
        Log.w(
          TAG,
          "DNS lookup failed for ${section.name}; falling back to system DNS",
          lookupFailure,
        )
        Dns.SYSTEM.lookup(hostname)
      }
    }

    private fun buildDelegate(config: AppDnsRuntimeConfig): Dns {
      if (config.mode != AppDnsMode.DOH || config.dohUrl.isNullOrBlank()) {
        return Dns.SYSTEM
      }

      val key = "${config.dohUrl}|${config.bootstrapIps.joinToString(",")}"
      return dohDelegates.getOrPut(key) {
        val dnsClient = OkHttpClient.Builder().build()
        val builder = DnsOverHttps.Builder().client(dnsClient).url(config.dohUrl.toHttpUrl())

        val bootstrapHosts =
          config.bootstrapIps
            .mapNotNull { ip ->
              runCatching { InetAddress.getByName(ip) }
                .onFailure { Log.w(TAG, "Invalid DoH bootstrap IP: $ip", it) }
                .getOrNull()
            }
            .distinctBy { it.hostAddress }
        if (bootstrapHosts.isNotEmpty()) {
          builder.bootstrapDnsHosts(bootstrapHosts)
        }
        builder.build()
      }
    }
  }

  private const val TAG = "AppDnsResolver"
  private val dohDelegates = ConcurrentHashMap<String, Dns>()
}

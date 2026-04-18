package org.bepass.oblivion.utils

import android.util.Log
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bepass.oblivion.model.IPDetails
import org.json.JSONObject

/** Utility for fetching public IP details over the in-app SOCKS proxy (no network leaks). */
@Singleton
class PublicIPUtils @Inject constructor(private val okHttpClient: OkHttpClient) {
  suspend fun fetchIpDetails(): IPDetails =
    withContext(Dispatchers.IO) {
      val startTime = System.currentTimeMillis()
      var retryDelayMillis = RETRY_DELAY_MILLIS_INITIAL

      while (System.currentTimeMillis() - startTime < TIMEOUT_MILLIS) {
        try {
          return@withContext fetchOnce()
        } catch (expected: Exception) {
          Log.w(TAG, "Failed to fetch IP details; will retry", expected)
        }

        delay(retryDelayMillis)
        retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(RETRY_DELAY_MILLIS_MAX)
      }

      Log.d(TAG, "Timeout reached without successful IP details retrieval")
      IPDetails()
    }

  private fun fetchOnce(): IPDetails {
    val socksPort = resolveSocksPort()
    val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(LOCALHOST, socksPort))

    val client =
      okHttpClient
        .newBuilder()
        .proxy(proxy)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .fastFallback(true) // Ensure Happy Eyeballs is enabled here too
        .build()

    val request = Request.Builder().url(URL_COUNTRY_API).build()

    client.newCall(request).execute().use { response ->
      check(response.isSuccessful) { "HTTP ${response.code} when fetching IP details" }

      val body = response.body.string()
      check(body.isNotBlank()) { "Empty response body when fetching IP details" }

      val json = JSONObject(body)
      val ip = json.optString("ip").takeIf { it.isNotBlank() }
      val country = json.optString("country").takeIf { it.isNotBlank() }
      val flag = country?.let { CountryCode(it).toCountryFlagEmoji() }?.takeIf { it.isNotBlank() }

      return IPDetails(ip = ip, country = country, flag = flag)
    }
  }

  private fun resolveSocksPort(): Int {
    val runtimePort = FileManager.getInt(FileManager.KeyHolder.RUNTIME_SOCKS_PORT, 0)
    if (runtimePort in MIN_PORT..MAX_PORT) return runtimePort

    val port =
      FileManager.getString("USERSETTING_port").toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }
    return port ?: error("SOCKS port is not configured (RUNTIME_SOCKS_PORT/USERSETTING_port)")
  }

  private companion object {
    private const val TAG = "PublicIPUtils"
    private const val URL_COUNTRY_API = "https://api.country.is/"
    private const val LOCALHOST = "127.0.0.1"

    private const val MIN_PORT = 1
    private const val MAX_PORT = 65535

    private const val TIMEOUT_SECONDS = 5L
    private const val TIMEOUT_MILLIS = 30_000L

    private const val RETRY_DELAY_MILLIS_INITIAL = 1_000L
    private const val RETRY_DELAY_MILLIS_MAX = 5_000L
  }
}

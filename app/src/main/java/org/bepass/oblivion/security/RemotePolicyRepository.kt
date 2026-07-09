package org.bepass.oblivion.security

import android.content.Context
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bepass.oblivion.BuildConfig
import org.bepass.oblivion.logging.SecureLog as Log
import org.bepass.oblivion.model.RemotePolicyConfigV2
import org.bepass.oblivion.model.TunnelMode
import tun2socks.Tun2socks

class RemotePolicyRepository(context: Context) {
  private val applicationContext = context.applicationContext
  private val secureStore = AndroidSecureStore(applicationContext)
  private val directory =
    applicationContext.noBackupFilesDir.resolve(CACHE_DIRECTORY).also { it.mkdirs() }
  private val cacheFile = directory.resolve(CACHE_FILE_NAME)
  private val client =
    OkHttpClient.Builder()
      .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .callTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .followRedirects(false)
      .followSslRedirects(false)
      .build()

  suspend fun resolve(mode: TunnelMode): RemotePolicyConfigV2 =
    withContext(Dispatchers.IO) {
      val publicKeysJson = BuildConfig.FEATURE_MANIFEST_KEYS_JSON
      requireConfiguredKeyset(publicKeysJson)
      val cached = cacheFile.takeIf(File::isFile)
      val downloaded =
        runCatching {
            downloadAndValidateCandidate(
              url = BuildConfig.FEATURE_MANIFEST_URL,
              publicKeysJson = publicKeysJson,
              mode = mode,
            )
          }
          .onFailure { Log.w(TAG, "Signed feature manifest refresh failed", it) }
          .getOrNull()
      val selected = downloaded ?: cached ?: error("No valid signed feature manifest is available")
      RemotePolicyConfigV2(
        required = true,
        envelopePath = selected.canonicalPath,
        publicKeysJson = publicKeysJson,
        stateKeyRef = POLICY_STATE_KEY,
      )
    }

  private fun downloadAndValidateCandidate(
    url: String,
    publicKeysJson: String,
    mode: TunnelMode,
  ): File {
    requireHttpsUrl(url)
    val request = Request.Builder().url(url).get().build()
    val envelope =
      client.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "Feature manifest HTTP ${response.code}" }
        val body = checkNotNull(response.body)
        val declaredLength = body.contentLength()
        check(declaredLength == -1L || declaredLength in 1..MAX_ENVELOPE_BYTES.toLong()) {
          "Invalid feature manifest length"
        }
        val bytes = body.source().use { it.readByteArray(MAX_ENVELOPE_BYTES + 1L) }
        check(bytes.isNotEmpty() && bytes.size <= MAX_ENVELOPE_BYTES) {
          "Invalid feature manifest body"
        }
        String(bytes, StandardCharsets.UTF_8)
      }

    val storedState = readStoredState()
    val minimumSequence = if (storedState.sequence > 0) storedState.sequence - 1 else 0
    val result =
      Tun2socks.verifyFeatureManifestKeyset(
        envelope,
        publicKeysJson,
        minimumSequence,
        System.currentTimeMillis() / MILLIS_PER_SECOND,
      )
    val payload = Json.decodeFromString<RemotePolicyPayload>(result.payloadJSON)
    check(payload.killSwitch.containsKey(mode.name)) { "Feature manifest omits selected mode" }
    val payloadHash = sha256(result.payloadJSON)
    check(
      result.sequence > storedState.sequence ||
        storedState.payloadSha256.isBlank() ||
        storedState.payloadSha256 == payloadHash
    ) {
      "Feature manifest equivocation"
    }

    writeAtomically(cacheFile, envelope)
    return cacheFile
  }

  private fun readStoredState(): RemotePolicyState {
    val value = secureStore.get(POLICY_STATE_KEY)
    if (value.isBlank()) return RemotePolicyState()
    return Json.decodeFromString(value)
  }

  private fun requireConfiguredKeyset(value: String) {
    val keys = Json.decodeFromString<List<String>>(value)
    require(keys.size in 1..MAX_ACTIVE_KEYS) { "Signed feature manifest keys are not configured" }
    require(keys.all { it.matches(BASE64URL_ED25519_KEY) }) {
      "Signed feature manifest keyset is invalid"
    }
  }

  private fun requireHttpsUrl(value: String) {
    val uri = URI(value)
    require(
      uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        uri.fragment == null
    ) {
      "Feature manifest URL must be HTTPS"
    }
  }

  private fun writeAtomically(destination: File, value: String) {
    val temporary = File.createTempFile("manifest-", ".tmp", directory)
    try {
      FileOutputStream(temporary).use { output ->
        output.write(value.toByteArray(StandardCharsets.UTF_8))
        output.fd.sync()
      }
      Os.chmod(temporary.absolutePath, OWNER_READ_WRITE_MODE)
      Os.rename(temporary.absolutePath, destination.absolutePath)
    } finally {
      temporary.delete()
    }
  }

  private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray(StandardCharsets.UTF_8))
      .joinToString("") { "%02x".format(it) }

  @Serializable
  private data class RemotePolicyPayload(
    val schemaVersion: Int,
    val sequence: Long,
    val issuedAt: Long,
    val expiresAt: Long,
    val killSwitch: Map<String, Boolean>,
    val sponsorId: String = "",
    val propagationChannel: String = "",
    val fronts: List<String> = emptyList(),
    val endpoints: List<String> = emptyList(),
  )

  @Serializable
  private data class RemotePolicyState(
    val sequence: Long = 0,
    val payloadSha256: String = "",
  )

  private companion object {
    const val CACHE_DIRECTORY = "remote-policy-v1"
    const val CACHE_FILE_NAME = "feature-manifest.json"
    const val POLICY_STATE_KEY = "feature.manifest.state.v1"
    const val MAX_ENVELOPE_BYTES = 256 * 1024
    const val MAX_ACTIVE_KEYS = 3
    const val NETWORK_TIMEOUT_SECONDS = 8L
    const val MILLIS_PER_SECOND = 1_000L
    const val OWNER_READ_WRITE_MODE = 0x180
    const val TAG = "RemotePolicy"
    val BASE64URL_ED25519_KEY = Regex("[A-Za-z0-9_-]{43}")
  }
}

package org.bepass.oblivion.wireguard

import android.content.Context
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import org.bepass.oblivion.model.WireGuardConfigV2
import org.bepass.oblivion.security.AndroidSecureStore

class WireGuardProfileRepository(
  context: Context,
  private val secureStore: AndroidSecureStore = AndroidSecureStore(context),
) {
  private val directory =
    context.noBackupFilesDir.resolve("wireguard-profiles-v2").also {
      check(it.exists() || it.mkdirs()) { "Unable to create WireGuard profile directory" }
    }

  @Synchronized
  fun import(profile: String): WireGuardConfigV2 {
    val sanitized = WireGuardProfileImporter.sanitize(profile)
    val identifier = randomIdentifier()
    val privateKeyRef = "wireguard.$identifier.private.v2"
    val presharedKeyRefs =
      sanitized.presharedKeys.mapIndexed { index, key ->
        if (key.isEmpty()) "" else "wireguard.$identifier.psk.$index.v2"
      }
    val storedRefs = mutableListOf<String>()
    val destination = directory.resolve("$identifier.conf")
    val temporary = File.createTempFile("profile-", ".tmp", directory)
    try {
      secureStore.put(privateKeyRef, sanitized.privateKey)
      storedRefs += privateKeyRef
      sanitized.presharedKeys.forEachIndexed { index, key ->
        if (key.isNotEmpty()) {
          secureStore.put(presharedKeyRefs[index], key)
          storedRefs += presharedKeyRefs[index]
        }
      }
      FileOutputStream(temporary).use { output ->
        output.write(sanitized.content.toByteArray(Charsets.UTF_8))
        output.fd.sync()
      }
      Os.chmod(temporary.absolutePath, OWNER_READ_WRITE)
      Os.rename(temporary.absolutePath, destination.absolutePath)
      return WireGuardConfigV2(
        profilePath = destination.absolutePath,
        privateKeyRef = privateKeyRef,
        presharedKeyRefs = presharedKeyRefs,
      )
    } catch (expected: Throwable) {
      storedRefs.asReversed().forEach {
        try {
          secureStore.delete(it)
        } catch (_: Throwable) {}
      }
      destination.delete()
      throw expected
    } finally {
      temporary.delete()
    }
  }

  @Synchronized
  fun delete(config: WireGuardConfigV2) {
    val file = checkedProfileFile(config.profilePath)
    if (file.exists()) check(file.delete()) { "Unable to delete WireGuard profile" }
    secureStore.delete(config.privateKeyRef)
    config.presharedKeyRefs.filter(String::isNotEmpty).forEach(secureStore::delete)
  }

  /**
   * Returns a standard wg-quick profile containing private material for an explicit user export.
   * Callers must keep it in memory only and must never log or cache the returned value.
   */
  @Synchronized
  fun export(config: WireGuardConfigV2): String {
    val profile = checkedProfileFile(config.profilePath)
    require(profile.length() in 1..MAX_PROFILE_BYTES) { "Invalid WireGuard profile size" }
    val privateKey = secureStore.get(config.privateKeyRef)
    require(privateKey.isNotEmpty()) { "WireGuard private key is unavailable" }
    val presharedKeys =
      config.presharedKeyRefs.map { reference ->
        if (reference.isEmpty()) {
          ""
        } else {
          secureStore.get(reference).also {
            require(it.isNotEmpty()) { "WireGuard preshared key is unavailable" }
          }
        }
      }
    return WireGuardProfileImporter.restoreSecrets(
      sanitizedProfile = profile.readText(Charsets.UTF_8),
      privateKey = privateKey,
      presharedKeys = presharedKeys,
    )
  }

  private fun randomIdentifier(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }.also { bytes.fill(0) }
  }

  private fun checkedProfileFile(path: String): File {
    val file = File(path)
    val canonicalDirectory = directory.canonicalFile
    val canonicalFile = file.canonicalFile
    require(canonicalFile.parentFile == canonicalDirectory) { "Unexpected WireGuard profile path" }
    return canonicalFile
  }

  private companion object {
    const val OWNER_READ_WRITE = 0x180
    const val MAX_PROFILE_BYTES = 1024L * 1024L
  }
}

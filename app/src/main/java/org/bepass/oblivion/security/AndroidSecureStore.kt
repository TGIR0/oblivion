package org.bepass.oblivion.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed storage for core identities, licenses and private keys.
 *
 * Ciphertexts live under noBackupFilesDir and are never eligible for cloud backup or device
 * transfer. Aliases are hashed before becoming filenames.
 */
class AndroidSecureStore(context: Context) {
  private val directory = context.noBackupFilesDir.resolve("core-secrets-v2").also { it.mkdirs() }

  @Synchronized
  fun put(key: String, value: String) {
    requireValidKey(key)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
    val payload =
      ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + ciphertext.size)
        .putInt(cipher.iv.size)
        .put(cipher.iv)
        .put(ciphertext)
        .array()
    val destination = secretFile(key)
    val temporary = File.createTempFile("secret-", ".tmp", directory)
    try {
      FileOutputStream(temporary).use { output ->
        output.write(payload)
        output.fd.sync()
      }
      Os.rename(temporary.absolutePath, destination.absolutePath)
    } finally {
      temporary.delete()
    }
  }

  @Synchronized
  fun get(key: String): String {
    requireValidKey(key)
    val file = secretFile(key)
    if (!file.exists()) return ""
    require(file.length() in 1..MAX_SECRET_FILE_BYTES) { "Invalid encrypted payload size" }
    val buffer = ByteBuffer.wrap(file.readBytes())
    val ivSize = buffer.int
    require(ivSize in 12..16 && buffer.remaining() > ivSize) { "Invalid encrypted payload" }
    val iv = ByteArray(ivSize).also(buffer::get)
    val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
    return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
  }

  @Synchronized
  fun delete(key: String) {
    requireValidKey(key)
    val file = secretFile(key)
    if (file.exists() && !file.delete()) {
      error("Unable to delete secure value")
    }
  }

  private fun secretFile(key: String) =
    directory.resolve(
      java.security.MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    )

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
      return it
    }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build()
    )
    return generator.generateKey()
  }

  private fun requireValidKey(key: String) {
    require(key.matches(KEY_PATTERN)) { "Invalid secure-store key" }
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "oblivion.core.secrets.v2"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
    const val MAX_SECRET_FILE_BYTES = 1024L * 1024L
    val KEY_PATTERN = Regex("[a-zA-Z0-9._-]{1,128}")
  }
}

@file:Suppress("TooManyFunctions")

package org.bepass.oblivion.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.tencent.mmkv.MMKV
import org.bepass.oblivion.model.VpnConfig
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object FileManager {
  private val lock = ReentrantReadWriteLock()

  @Volatile private var mmkv: MMKV? = null

  object KeyHolder {
    const val DARK_MODE = "setting_dark_mode"
    const val RUNTIME_SOCKS_PORT = "RUNTIME_SOCKS_PORT"
    const val RUNTIME_BIND_ADDRESS = "RUNTIME_BIND_ADDRESS"
  }

  @JvmStatic
  fun initialize(context: Context) {
    lock.write {
      if (mmkv == null) {
        MMKV.initialize(context.applicationContext)
        mmkv = MMKV.mmkvWithID("UserData")
      }
    }
  }

  private fun requireInitialized(): MMKV =
    checkNotNull(mmkv) { "MMKV is not initialized. Call FileManager.initialize(Context) first." }

  @JvmStatic
  fun contains(key: String): Boolean = lock.read { requireInitialized().containsKey(key) }

  // ===========================================================================
  // Setters

  @JvmStatic
  fun set(name: String, value: String) {
    lock.write { requireInitialized().encode(name, value) }
  }

  @JvmStatic
  fun set(name: String, value: Boolean) {
    lock.write { requireInitialized().encode(name, value) }
  }

  @JvmStatic
  fun set(name: String, value: Set<String>) {
    lock.write { requireInitialized().encode(name, value) }
  }

  @JvmStatic
  fun set(name: String, value: Int) {
    lock.write { requireInitialized().encode(name, value) }
  }

  @JvmStatic
  fun set(name: String, value: Float) {
    lock.write { requireInitialized().encode(name, value) }
  }

  @JvmStatic
  fun set(name: String, value: Long) {
    lock.write { requireInitialized().encode(name, value) }
  }

  // ===========================================================================
  // Getters

  @JvmStatic
  fun getString(name: String): String =
    lock.read { requireInitialized().decodeString(name, "") ?: "" }

  @JvmStatic
  fun getString(name: String, defaultValue: String): String =
    lock.read { requireInitialized().decodeString(name, defaultValue) ?: defaultValue }

  @JvmStatic
  fun getStringSet(name: String, def: Set<String>): Set<String> =
    lock.read { requireInitialized().decodeStringSet(name, null) ?: def }

  @JvmStatic fun getBoolean(name: String): Boolean = getBoolean(name, false)

  @JvmStatic
  fun getBoolean(name: String, defaultValue: Boolean): Boolean =
    lock.read { requireInitialized().decodeBool(name, defaultValue) }

  @JvmStatic fun getInt(name: String): Int = getInt(name, 0)

  @JvmStatic
  fun getInt(name: String, defaultValue: Int): Int =
    lock.read { requireInitialized().decodeInt(name, defaultValue) }

  @JvmStatic fun getFloat(name: String): Float = getFloat(name, 0f)

  @JvmStatic
  fun getFloat(name: String, defaultValue: Float): Float =
    lock.read { requireInitialized().decodeFloat(name, defaultValue) }

  @JvmStatic fun getLong(name: String): Long = getLong(name, 0L)

  @JvmStatic
  fun getLong(name: String, defaultValue: Long): Long =
    lock.read { requireInitialized().decodeLong(name, defaultValue) }

  // ===========================================================================
  // Reset + migration

  @JvmStatic
  fun resetToDefault() {
    lock.write {
      val store = requireInitialized()
      store.clearAll()
      store.encode(KeyHolder.DARK_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
  }

  @JvmStatic
  fun cleanOrMigrateSettings(context: Context) {
    lock.write {
      val store = requireInitialized()

      val oldPrefs = context.getSharedPreferences("UserData", Context.MODE_PRIVATE)
      store.importFromSharedPreferences(oldPrefs)
      oldPrefs.edit { clear() }

      if (!getBoolean("isFirstValueInit", false)) {
        set("USERSETTING_endpoint", "engage.cloudflareclient.com:2408")
        set("USERSETTING_port", "8086")
        set("USERSETTING_dns", "1.1.1.1,1.0.0.1")
        set("USERSETTING_vpn_dns", "1.1.1.1,1.0.0.1")
        set("USERSETTING_gool", false)
        set("USERSETTING_lan", false)
        set("USERSETTING_proxymode", false)
        set("USERSETTING_endpoint_type", 0)
        set("isFirstValueInit", true)
      }

      val splitApps = getStringSet("splitTunnelApps", emptySet())
      if (splitApps.isNotEmpty()) {
        val pm = context.applicationContext.packageManager
        val shouldKeep = splitApps.filterTo(mutableSetOf()) { packageExists(pm, it) }
        set("splitTunnelApps", shouldKeep)
      }
    }
  }

  @JvmStatic
  fun getVpnConfig(bindAddress: String = ""): VpnConfig {
    val masquePresetIndex = getInt("USERSETTING_masquepreset_index")
    val masquePresets = arrayOf("default", "light", "medium", "heavy", "stealth", "gfw", "firewall")
    val endpoint = getString("USERSETTING_endpoint").trim()
    val isAuto = endpoint.isEmpty() ||
      endpoint == "engage.cloudflareclient.com:2408" ||
      endpoint.equals("auto", ignoreCase = true)

    return VpnConfig(
      endpoint = if (isAuto) "" else endpoint,
      bindAddress = bindAddress,
      license = getString("USERSETTING_license").trim(),
      dns = getString("USERSETTING_dns").ifBlank { "1.1.1.1,1.0.0.1" },
      vpnDns = getString("USERSETTING_vpn_dns").ifBlank { getString("USERSETTING_dns").ifBlank { "1.1.1.1,1.0.0.1" } },
      region = getInt("USERSETTING_region"),
      endpointType = getInt("USERSETTING_endpoint_type"),
      gool = getBoolean("USERSETTING_gool"),
      masque = getBoolean("USERSETTING_masque"),
      masquePreferred = getBoolean("USERSETTING_masquepreferred"),
      masqueNoize = getBoolean("USERSETTING_masquenoize"),
      masquePreset = masquePresets.getOrNull(masquePresetIndex) ?: "default",
      proxyMode = getBoolean("USERSETTING_proxymode"),
      anycastIPs = getString("USERSETTING_anycast_ips"),
      preferredFingerprint = getString("USERSETTING_preferred_fprint"),
      port = getString("USERSETTING_port").trim(),
      conduit = getBoolean("USERSETTING_conduit"),
      psiphonCountry = getString("USERSETTING_psiphon_country"),
      lan = getBoolean("USERSETTING_lan"),
      proxyBypass = getString("USERSETTING_proxy_bypass")
    )
  }

  private fun packageExists(pm: PackageManager, packageName: String): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION") pm.getPackageInfo(packageName, 0)
      }
      true
    } catch (_: PackageManager.NameNotFoundException) {
      false
    }
  }
}

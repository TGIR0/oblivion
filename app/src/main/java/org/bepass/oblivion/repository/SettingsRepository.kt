package org.bepass.oblivion.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bepass.oblivion.model.VpnConfig
import org.bepass.oblivion.utils.FileManager

@Singleton
class SettingsRepository @Inject constructor() {
  // We wrap FileManager to provide Flow-based reactivity
  private val _vpnConfig = MutableStateFlow(FileManager.getVpnConfig())
  val vpnConfig: Flow<VpnConfig> = _vpnConfig.asStateFlow()

  private val _vpnCore = MutableStateFlow(FileManager.getInt(FileManager.Keys.USERSETTING_VPN_CORE))
  val vpnCore: Flow<Int> = _vpnCore.asStateFlow()

  fun updateConfig() {
    _vpnConfig.value = FileManager.getVpnConfig()
    _vpnCore.value = FileManager.getInt(FileManager.Keys.USERSETTING_VPN_CORE)
  }

  fun setString(key: String, value: String) {
    FileManager.set(key, value)
    updateConfig()
  }

  fun setBoolean(key: String, value: Boolean) {
    FileManager.set(key, value)
    updateConfig()
  }

  fun setInt(key: String, value: Int) {
    FileManager.set(key, value)
    updateConfig()
  }

  fun getString(key: String, default: String = ""): String = FileManager.getString(key, default)

  fun getBoolean(key: String, default: Boolean = false): Boolean =
    FileManager.getBoolean(key, default)

  fun getInt(key: String, default: Int = 0): Int = FileManager.getInt(key, default)
}

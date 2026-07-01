package org.bepass.oblivion.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bepass.oblivion.enums.SplitTunnelMode
import org.bepass.oblivion.utils.FileManager

data class SplitTunnelAppInfo(
  val appName: String,
  val packageName: String,
  val isSelected: Boolean,
)

@HiltViewModel
class SplitTunnelViewModel
@Inject
constructor(@ApplicationContext private val appContext: Context) : ViewModel() {

  private val _apps = MutableStateFlow<List<SplitTunnelAppInfo>>(emptyList())
  val apps: StateFlow<List<SplitTunnelAppInfo>> = _apps.asStateFlow()

  private val _loading = MutableStateFlow(true)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  private val _showSystem = MutableStateFlow(false)
  val showSystem: StateFlow<Boolean> = _showSystem.asStateFlow()

  private val _mode = MutableStateFlow(SplitTunnelMode.getSplitTunnelMode())
  val mode: StateFlow<SplitTunnelMode> = _mode.asStateFlow()

  init {
    loadAppsList()
  }

  fun setShowSystem(show: Boolean) {
    _showSystem.value = show
    loadAppsList()
  }

  fun setSplitTunnelMode(newMode: SplitTunnelMode) {
    _mode.value = newMode
    viewModelScope.launch(Dispatchers.IO) {
      FileManager.set(FileManager.Keys.SPLIT_TUNNEL_MODE, newMode.name)
    }
  }

  fun toggleAppSelection(packageName: String, isSelected: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      val set =
        FileManager.getStringSet(FileManager.Keys.SPLIT_TUNNEL_APPS, emptySet()).toMutableSet()
      if (isSelected) {
        set.add(packageName)
      } else {
        set.remove(packageName)
      }
      FileManager.set(FileManager.Keys.SPLIT_TUNNEL_APPS, set)

      // Update state
      val currentList = _apps.value
      _apps.value =
        currentList
          .map {
            if (it.packageName == packageName) it.copy(isSelected = isSelected) else it
          }
          .sortedWith(
            compareByDescending<SplitTunnelAppInfo> { it.isSelected }
              .thenBy { it.appName.lowercase() }
          )
    }
  }

  private fun loadAppsList() {
    viewModelScope.launch {
      _loading.value = true
      _apps.value =
        withContext(Dispatchers.IO) {
          val showSys = _showSystem.value
          val selectedApps =
            FileManager.getStringSet(FileManager.Keys.SPLIT_TUNNEL_APPS, emptySet())
          val pm = appContext.packageManager

          pm
            .getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
            .filter { it.packageName != appContext.packageName }
            .filter { pkg ->
              val selected = selectedApps.contains(pkg.packageName)
              val appInfo = pkg.applicationInfo ?: return@filter selected
              val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
              !isSystem || showSys || selected
            }
            .filter { pkg ->
              val selected = selectedApps.contains(pkg.packageName)
              val hasInternet =
                pkg.requestedPermissions?.any { android.Manifest.permission.INTERNET == it }
                  ?: false
              hasInternet || selected
            }
            .mapNotNull {
              val appInfo = it.applicationInfo ?: return@mapNotNull null
              SplitTunnelAppInfo(
                appName = appInfo.loadLabel(pm).toString(),
                packageName = it.packageName,
                isSelected = selectedApps.contains(it.packageName),
              )
            }
            .sortedWith(
              compareByDescending<SplitTunnelAppInfo> { it.isSelected }
                .thenBy { it.appName.lowercase() }
            )
        }
      _loading.value = false
    }
  }
}

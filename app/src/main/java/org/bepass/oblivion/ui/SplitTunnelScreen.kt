package org.bepass.oblivion.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bepass.oblivion.R
import org.bepass.oblivion.enums.SplitTunnelMode
import org.bepass.oblivion.utils.FileManager

data class SplitTunnelAppInfo(
  val appName: String,
  val packageName: String,
  val icon: ImageBitmap,
  val isSelected: Boolean,
)

@Composable
fun SplitTunnelScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var apps by remember { mutableStateOf<List<SplitTunnelAppInfo>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var showSystem by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  var mode by remember { mutableStateOf(SplitTunnelMode.getSplitTunnelMode()) }

  LaunchedEffect(showSystem) {
    loading = true
    apps = withContext(Dispatchers.IO) { loadApps(context, showSystem) }
    loading = false
  }

  val filtered =
    apps.filter {
      query.isBlank() ||
        it.appName.contains(query, ignoreCase = true) ||
        it.packageName.contains(query, ignoreCase = true)
    }

  Column(Modifier.fillMaxSize()) {
    ScreenHeader(title = stringResource(R.string.blackList), onBack = onBack)
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(stringResource(R.string.selected_apps_format, apps.count { it.isSelected }, filtered.size))
      Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
          selected = mode == SplitTunnelMode.DISABLED,
          onClick = {
            mode = SplitTunnelMode.DISABLED
            FileManager.set(FileManager.Keys.SPLIT_TUNNEL_MODE, mode.name)
          },
        )
        Text(stringResource(R.string.disabledR), Modifier.weight(1f))
        RadioButton(
          selected = mode == SplitTunnelMode.BLACKLIST,
          onClick = {
            mode = SplitTunnelMode.BLACKLIST
            FileManager.set(FileManager.Keys.SPLIT_TUNNEL_MODE, mode.name)
          },
        )
        Text(stringResource(R.string.blackList))
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.showSystemAppsText), Modifier.weight(1f))
        Switch(checked = showSystem, onCheckedChange = { showSystem = it })
      }
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.split_tunnel_search_hint)) },
        modifier = Modifier.fillMaxWidth(),
      )
      if (loading) {
        CircularProgressIndicator()
      } else if (filtered.isEmpty()) {
        Text(stringResource(R.string.no_apps_match_filter))
      } else {
        LazyColumn {
          items(filtered, key = { it.packageName }) { app ->
            SplitTunnelAppRow(app) {
              val selected = !app.isSelected
              val set = FileManager.getStringSet(FileManager.Keys.SPLIT_TUNNEL_APPS, emptySet()).toMutableSet()
              if (selected) set.add(app.packageName) else set.remove(app.packageName)
              FileManager.set(FileManager.Keys.SPLIT_TUNNEL_APPS, set)
              apps = apps.map { if (it.packageName == app.packageName) it.copy(isSelected = selected) else it }
                .sortedWith(compareByDescending<SplitTunnelAppInfo> { it.isSelected }.thenBy { it.appName.lowercase() })
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SplitTunnelAppRow(app: SplitTunnelAppInfo, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
    Column(Modifier.weight(1f)) {
      Text(app.appName)
      Text(app.packageName)
    }
    Checkbox(checked = app.isSelected, onCheckedChange = { onClick() })
  }
}

@SuppressLint("QueryPermissionsNeeded")
private fun loadApps(context: Context, shouldShowSystemApps: Boolean): List<SplitTunnelAppInfo> {
  val selectedApps = FileManager.getStringSet(FileManager.Keys.SPLIT_TUNNEL_APPS, emptySet())
  val pm = context.packageManager
  return pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
    .filter { it.packageName != context.packageName }
    .filter { pkg ->
      val selected = selectedApps.contains(pkg.packageName)
      val appInfo = pkg.applicationInfo ?: return@filter selected
      val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
      !isSystem || shouldShowSystemApps || selected
    }
    .filter { pkg ->
      val selected = selectedApps.contains(pkg.packageName)
      val hasInternet = pkg.requestedPermissions?.any { android.Manifest.permission.INTERNET == it } ?: false
      hasInternet || selected
    }
    .mapNotNull {
      val appInfo = it.applicationInfo ?: return@mapNotNull null
      SplitTunnelAppInfo(
        appName = appInfo.loadLabel(pm).toString(),
        packageName = it.packageName,
        icon = appInfo.loadIcon(pm).toBitmap(64, 64).asImageBitmap(),
        isSelected = selectedApps.contains(it.packageName),
      )
    }
    .sortedWith(compareByDescending<SplitTunnelAppInfo> { it.isSelected }.thenBy { it.appName.lowercase() })
}

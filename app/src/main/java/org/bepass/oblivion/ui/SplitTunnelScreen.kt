package org.bepass.oblivion.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bepass.oblivion.R
import org.bepass.oblivion.enums.SplitTunnelMode
import org.bepass.oblivion.ui.viewmodel.SplitTunnelAppInfo
import org.bepass.oblivion.ui.viewmodel.SplitTunnelViewModel

@Composable
fun SplitTunnelScreen(
  onBack: () -> Unit,
  viewModel: SplitTunnelViewModel = hiltViewModel(),
) {
  val apps by viewModel.apps.collectAsStateWithLifecycle()
  val loading by viewModel.loading.collectAsStateWithLifecycle()
  val showSystem by viewModel.showSystem.collectAsStateWithLifecycle()
  val mode by viewModel.mode.collectAsStateWithLifecycle()
  var query by remember { mutableStateOf("") }
  val fontScale =
    org.bepass.oblivion.utils.FontSizeHelper.fontSizeFlow.collectAsStateWithLifecycle().value.scale

  val filtered =
    remember(apps, query) {
      apps.filter {
        query.isBlank() ||
          it.appName.contains(query, ignoreCase = true) ||
          it.packageName.contains(query, ignoreCase = true)
      }
    }

  Column(Modifier.fillMaxSize().testTag(UiTestTags.SPLIT_TUNNEL)) {
    ScreenHeader(title = stringResource(R.string.blackList), onBack = onBack)
    Column(
      Modifier.padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        stringResource(R.string.selected_apps_format, apps.count { it.isSelected }, filtered.size),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * fontScale).sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
          selected = mode == SplitTunnelMode.DISABLED,
          onClick = {
            viewModel.setSplitTunnelMode(SplitTunnelMode.DISABLED)
          },
        )
        Text(
          stringResource(R.string.disabledR),
          Modifier.weight(1f),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * fontScale).sp),
        )
        RadioButton(
          selected = mode == SplitTunnelMode.BLACKLIST,
          onClick = {
            viewModel.setSplitTunnelMode(SplitTunnelMode.BLACKLIST)
          },
        )
        Text(
          stringResource(R.string.blackList),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * fontScale).sp),
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(R.string.showSystemAppsText),
          Modifier.weight(1f),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * fontScale).sp),
        )
        Switch(checked = showSystem, onCheckedChange = { viewModel.setShowSystem(it) })
      }
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.split_tunnel_search_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
      if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      } else if (filtered.isEmpty()) {
        Text(
          stringResource(R.string.no_apps_match_filter),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * fontScale).sp),
        )
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          items(filtered, key = { it.packageName }) { app ->
            SplitTunnelAppRow(app, fontScale) {
              viewModel.toggleAppSelection(app.packageName, !app.isSelected)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SplitTunnelAppRow(
  app: SplitTunnelAppInfo,
  fontScale: Float,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  var iconBitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }

  LaunchedEffect(app.packageName) {
    iconBitmap =
      withContext(Dispatchers.IO) {
        try {
          val pm = context.packageManager
          val appInfo = pm.getApplicationInfo(app.packageName, 0)
          appInfo.loadIcon(pm).toBitmap(64, 64).asImageBitmap()
        } catch (expectedMissingPackage: PackageManager.NameNotFoundException) {
          null
        }
      }
  }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 12.dp, horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (iconBitmap != null) {
      Image(
        bitmap = iconBitmap!!,
        contentDescription = null,
        modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape),
      )
    } else {
      Box(
        modifier =
          Modifier.size(48.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
      )
    }
    Column(Modifier.weight(1f)) {
      Text(
        text = app.appName,
        style =
          MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = (16 * fontScale).sp,
          ),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = app.packageName,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = (12 * fontScale).sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Checkbox(checked = app.isSelected, onCheckedChange = { onClick() })
  }
}

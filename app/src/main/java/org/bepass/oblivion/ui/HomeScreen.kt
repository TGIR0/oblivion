package org.bepass.oblivion.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bepass.oblivion.R
import org.bepass.oblivion.enums.ConnectionState
import org.bepass.oblivion.utils.FileManager
import org.bepass.oblivion.utils.NetworkUtils
import org.bepass.oblivion.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
  onRequestVpnStart: () -> Unit,
  onStopVpn: () -> Unit,
  onSettings: () -> Unit,
  onInfo: () -> Unit,
  onLogs: () -> Unit,
  viewModel: MainViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val state by viewModel.connectionState.collectAsStateWithLifecycle()
  val publicIpDetails by viewModel.publicIpDetails.collectAsStateWithLifecycle()
  val loadingIp by viewModel.isFetchingPublicIp.collectAsStateWithLifecycle()
  var showLanguageDialog by remember { mutableStateOf(false) }

  Box(Modifier.fillMaxSize()) {
    IconButton(
      onClick = onInfo,
      modifier =
        Modifier.align(Alignment.TopStart)
          .statusBarsPadding()
          .padding(start = 16.dp, top = 8.dp)
          .size(48.dp),
    ) {
      Icon(
        painterResource(R.drawable.ic_info),
        contentDescription = stringResource(R.string.aboutApp),
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }
    Row(
      modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 16.dp, top = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onLogs, modifier = Modifier.size(48.dp)) {
        Icon(
          painterResource(R.drawable.ic_bug),
          contentDescription = stringResource(R.string.logApp),
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
      IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
        Icon(
          painterResource(R.drawable.ic_settings),
          contentDescription = stringResource(R.string.settingsText),
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 96.dp, bottom = 96.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = stringResource(R.string.app_name).uppercase(),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.basedOnWarp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(48.dp))
      Surface(
        onClick = {
          when (state) {
            ConnectionState.DISCONNECTED -> onRequestVpnStart()
            ConnectionState.CONNECTING, ConnectionState.CONNECTED -> onStopVpn()
          }
        },
        modifier = Modifier.size(180.dp).clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 0.dp,
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
          Icon(
            painter =
              painterResource(
                if (state == ConnectionState.DISCONNECTED) R.drawable.vpn_off else R.drawable.vpn_on
              ),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }

      Spacer(Modifier.height(28.dp))
      Text(
        text = connectionTitle(context, state),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (loadingIp) {
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator()
      }
      publicIpDetails?.ip?.let { ip ->
        Spacer(Modifier.height(16.dp))
        Text(
          "$ip ${publicIpDetails?.flag.orEmpty()}",
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }

    FloatingActionButton(
      onClick = { showLanguageDialog = true },
      modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(24.dp),
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
      Icon(painterResource(R.drawable.ic_translate), contentDescription = stringResource(R.string.select_language))
    }
  }

  if (showLanguageDialog) {
    LanguageDialog(
      onDismiss = { showLanguageDialog = false },
      onLocaleChanged = {
        showLanguageDialog = false
      },
    )
  }
}

private fun connectionTitle(context: Context, state: ConnectionState): String =
  when (state) {
    ConnectionState.DISCONNECTED -> context.getString(R.string.notConnected)
    ConnectionState.CONNECTING -> context.getString(R.string.connecting)
    ConnectionState.CONNECTED -> {
      if (FileManager.getBoolean(FileManager.Keys.USERSETTING_PROXYMODE)) {
        val isLan = FileManager.getBoolean(FileManager.Keys.USERSETTING_LAN)
        val ip = if (isLan) NetworkUtils.getLocalIpAddress(context) ?: "0.0.0.0" else "127.0.0.1"
        val mode = if (isLan) "socks5 over LAN" else "socks5"
        val port =
          FileManager.getInt(FileManager.Keys.RUNTIME_SOCKS_PORT, 0).takeIf { it in 1..65535 }
            ?: FileManager.getString(FileManager.Keys.USERSETTING_PORT).toIntOrNull()
            ?: 0
        "${context.getString(R.string.connected)}\n$mode on $ip:$port"
      } else {
        context.getString(R.string.connected)
      }
    }
  }

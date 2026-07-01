package org.bepass.oblivion.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import org.bepass.oblivion.R
import org.bepass.oblivion.enums.ConnectionState
import org.bepass.oblivion.ui.theme.OblivionV7Tokens
import org.bepass.oblivion.ui.viewmodel.MainViewModel
import org.bepass.oblivion.utils.FileManager

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
  val fontScale =
    org.bepass.oblivion.utils.FontSizeHelper.fontSizeFlow.collectAsStateWithLifecycle().value.scale
  var showLanguageDialog by remember { mutableStateOf(false) }

  Box(Modifier.fillMaxSize().testTag(UiTestTags.HOME)) {
    // Top Bar
    Row(
      modifier =
        Modifier.align(Alignment.TopCenter)
          .fillMaxSize()
          .statusBarsPadding()
          .padding(top = 16.dp, start = 20.dp, end = 20.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      IconButton(
        onClick = onInfo,
        modifier = Modifier.size(35.dp),
      ) {
        Icon(
          painterResource(R.drawable.ic_info),
          contentDescription = stringResource(R.string.aboutApp),
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxSize(),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        IconButton(onClick = onLogs, modifier = Modifier.size(35.dp)) {
          Icon(
            painterResource(R.drawable.ic_bug),
            contentDescription = stringResource(R.string.logApp),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize(),
          )
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(35.dp)) {
          Icon(
            painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.settingsText),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }

    // Center Content
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      // Spacer to adjust vertical bias to match v7 constraint layout bias roughly
      Spacer(Modifier.weight(0.2f))

      Text(
        text = stringResource(R.string.app_name).uppercase(),
        style =
          MaterialTheme.typography.displayMedium.copy(
            fontSize = (48 * fontScale).sp,
            fontWeight = FontWeight.Bold,
          ),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = stringResource(R.string.basedOnWarp),
        style =
          MaterialTheme.typography.titleLarge.copy(
            fontSize = (24 * fontScale).sp,
            fontWeight = FontWeight.Medium,
          ),
        color = MaterialTheme.colorScheme.primary,
      )

      Spacer(Modifier.weight(0.15f))

      // Switch Button
      val isConnected = state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
      val density = androidx.compose.ui.platform.LocalDensity.current
      val targetOffsetPx = remember(density) { with(density) { 85.dp.toPx() } }
      val thumbOffset by animateFloatAsState(if (isConnected) targetOffsetPx else 0f)
      val backgroundColor =
        if (isConnected) MaterialTheme.colorScheme.primary else OblivionV7Tokens.SwitchTrackOff

      Box(
        modifier =
          Modifier.width(160.dp)
            .height(75.dp)
            .clip(RoundedCornerShape(37.5.dp))
            .background(backgroundColor)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {
              when (state) {
                ConnectionState.DISCONNECTED -> onRequestVpnStart()
                ConnectionState.CONNECTING,
                ConnectionState.CONNECTED -> onStopVpn()
              }
            }
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        Box(
          modifier =
            Modifier.size(65.dp)
              .offset { IntOffset(thumbOffset.roundToInt(), 0) }
              .clip(CircleShape)
              .background(OblivionV7Tokens.FixedWhite)
        )
      }

      Spacer(Modifier.height(96.dp))

      Text(
        text = connectionTitle(context, state),
        style =
          MaterialTheme.typography.titleLarge.copy(
            fontSize = (24 * fontScale).sp,
            fontWeight = FontWeight.Medium,
          ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(Modifier.height(24.dp))

      if (loadingIp) {
        CircularProgressIndicator(
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp),
        )
      } else {
        publicIpDetails?.ip?.let { ip ->
          Text(
            "$ip ${publicIpDetails?.flag.orEmpty()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style =
              MaterialTheme.typography.titleMedium.copy(
                fontSize = (18 * fontScale).sp,
                fontWeight = FontWeight.Bold,
              ),
            textAlign = TextAlign.Center,
          )
        }
      }

      Spacer(Modifier.weight(0.3f))
    }

    // FAB
    FloatingActionButton(
      onClick = { showLanguageDialog = true },
      modifier =
        Modifier.align(Alignment.BottomEnd)
          .navigationBarsPadding()
          .padding(bottom = 24.dp, end = 24.dp),
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = OblivionV7Tokens.FixedWhite,
    ) {
      Icon(
        painterResource(R.drawable.ic_translate),
        contentDescription = stringResource(R.string.select_language),
        tint = OblivionV7Tokens.FixedWhite,
      )
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
        val port =
          FileManager.getInt(FileManager.Keys.RUNTIME_SOCKS_PORT, 0).takeIf { it in 1..65535 } ?: 0
        "${context.getString(R.string.connected)}\nsocks5 on 127.0.0.1:$port"
      } else {
        context.getString(R.string.connected)
      }
    }
  }

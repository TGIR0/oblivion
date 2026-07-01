package org.bepass.oblivion.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.bepass.oblivion.R
import org.bepass.oblivion.enums.VpnCoreType
import org.bepass.oblivion.platform.VpnServiceConnector
import org.bepass.oblivion.service.OblivionVpnService
import org.bepass.oblivion.ui.theme.OblivionTheme
import org.bepass.oblivion.utils.FileManager
import org.bepass.oblivion.utils.FontSizeHelper
import org.bepass.oblivion.utils.LocaleManager
import org.bepass.oblivion.utils.ThemeHelper

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var vpnServiceConnector: VpnServiceConnector

  private var pendingVpnStart = false
  private var pendingNotificationPermissionForVpnStart = false

  private val vpnPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK && pendingVpnStart) {
        startVpnServiceFromActivity(Intent(this, OblivionVpnService::class.java))
      } else if (pendingVpnStart) {
        Toast.makeText(this, R.string.permission_denied_vpn, Toast.LENGTH_LONG).show()
      }
      pendingVpnStart = false
    }

  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {
      if (pendingNotificationPermissionForVpnStart) {
        pendingNotificationPermissionForVpnStart = false
        continueVpnStartAfterNotificationCheck()
      }
    }

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LocaleManager.localizedContext(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    FileManager.initialize(applicationContext)
    ThemeHelper.getInstance().init()
    FontSizeHelper.init()
    applySystemBars(ThemeHelper.currentTheme)

    setContent {
      val theme by ThemeHelper.themeFlow.collectAsStateWithLifecycle()
      LaunchedEffect(theme) {
        applySystemBars(theme)
      }
      OblivionTheme {
        OblivionApp(
          onRequestVpnStart = { requestVpnStart() },
          onStopVpn = { OblivionVpnService.stopVpnService(this) },
        )
      }
    }
  }

  private fun requestVpnStart() {
    val core = VpnCoreType.getCurrent()
    if (!core.isReady) {
      Toast.makeText(this, core.availabilityReasonRes, Toast.LENGTH_LONG).show()
      return
    }
    if (shouldRequestNotificationPermissionBeforeVpnStart()) {
      FileManager.set(NOTIFICATION_PERMISSION_REQUESTED_KEY, true)
      pendingNotificationPermissionForVpnStart = true
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      return
    }

    continueVpnStartAfterNotificationCheck()
  }

  private fun shouldRequestNotificationPermissionBeforeVpnStart(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED &&
      !FileManager.getBoolean(NOTIFICATION_PERMISSION_REQUESTED_KEY, false)

  private fun continueVpnStartAfterNotificationCheck() {
    val vpnIntent = VpnService.prepare(this)
    if (vpnIntent != null) {
      pendingVpnStart = true
      vpnPermissionLauncher.launch(vpnIntent)
    } else {
      startVpnServiceFromActivity(Intent(this, OblivionVpnService::class.java))
    }
  }

  private fun startVpnServiceFromActivity(intent: Intent) {
    startVpnService(this, intent)
    vpnServiceConnector.ensureBound()
  }

  private fun applySystemBars(theme: ThemeHelper.Theme) {
    val isLight = theme == ThemeHelper.Theme.LIGHT
    val barColor =
      when (theme) {
        ThemeHelper.Theme.OLED -> AndroidColor.BLACK
        ThemeHelper.Theme.DARK -> AndroidColor.rgb(0x0f, 0x0a, 0x00)
        ThemeHelper.Theme.LIGHT -> AndroidColor.rgb(0xff, 0xfb, 0xf4)
      }

    enableEdgeToEdge(
      statusBarStyle =
        if (isLight) {
          SystemBarStyle.light(barColor, barColor)
        } else {
          SystemBarStyle.dark(barColor)
        },
      navigationBarStyle =
        if (isLight) {
          SystemBarStyle.light(barColor, barColor)
        } else {
          SystemBarStyle.dark(barColor)
        },
    )
  }

  companion object {
    private const val NOTIFICATION_PERMISSION_REQUESTED_KEY = "notification_permission_requested"

    @JvmStatic
    fun start(context: Context) {
      val starter =
        Intent(context, MainActivity::class.java).apply {
          putExtra("origin", context.javaClass.simpleName)
          addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
      context.startActivity(starter)
    }

    @JvmStatic
    fun startVpnService(context: Context, intent: Intent) {
      val dns = FileManager.getString(FileManager.Keys.USERSETTING_DNS).ifEmpty { "1.1.1.1" }

      intent.apply {
        putExtra(
          FileManager.Keys.USERSETTING_PROXYMODE,
          FileManager.getBoolean(FileManager.Keys.USERSETTING_PROXYMODE),
        )
        putExtra(
          FileManager.Keys.USERSETTING_ENDPOINT,
          FileManager.getString(FileManager.Keys.USERSETTING_ENDPOINT),
        )
        putExtra(FileManager.Keys.USERSETTING_DNS, dns)
        putExtra(
          FileManager.Keys.USERSETTING_VPN_DNS,
          FileManager.getString(FileManager.Keys.USERSETTING_VPN_DNS, dns),
        )
        action = OblivionVpnService.FLAG_VPN_START
      }
      ContextCompat.startForegroundService(context, intent)
    }
  }
}

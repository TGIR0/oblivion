package org.bepass.oblivion.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.IBinder
import android.os.Messenger
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import org.bepass.oblivion.R
import org.bepass.oblivion.enums.ConnectionState
import org.bepass.oblivion.logging.SecureLog as Log
import org.bepass.oblivion.ui.MainActivity

class QuickStartService : TileService() {
  private var isBound = false
  private var serviceMessenger: Messenger? = null

  private val connection =
    object : ServiceConnection {
      override fun onServiceConnected(className: ComponentName, service: IBinder) {
        serviceMessenger = Messenger(service)
        isBound = true
        subscribe()
      }

      override fun onServiceDisconnected(name: ComponentName) {
        serviceMessenger = null
        isBound = false
      }
    }

  override fun onStartListening() {
    bindService(Intent(this, OblivionVpnService::class.java), connection, Context.BIND_AUTO_CREATE)
  }

  override fun onStopListening() {
    if (isBound) {
      unsubscribe()
      isBound = false
    }

    runCatching { unbindService(connection) }
      .onFailure { Log.w("QuickStartService", "Unable to unbind VPN service", it) }
    serviceMessenger = null
  }

  override fun onClick() {
    val tile = qsTile ?: return

    if (tile.state == Tile.STATE_ACTIVE) {
      OblivionVpnService.stopVpnService(this)
    } else if (VpnService.prepare(this) != null) {
      Toast.makeText(this, getString(R.string.quick_settings_connect_once), Toast.LENGTH_LONG)
        .show()
    } else {
      MainActivity.startVpnService(this, Intent(this, OblivionVpnService::class.java))
    }
  }

  private fun subscribe() {
    val messenger = serviceMessenger ?: return
    if (!isBound) return

    OblivionVpnService.registerConnectionStateObserver(CONNECTION_OBSERVER_KEY, messenger) { state
      ->
      val tile = qsTile ?: return@registerConnectionStateObserver

      when (state) {
        ConnectionState.DISCONNECTED -> {
          tile.state = Tile.STATE_INACTIVE
          tile.label = getString(R.string.app_name)
          tile.icon = Icon.createWithResource(applicationContext, R.drawable.vpn_off)
        }
        ConnectionState.CONNECTING -> {
          tile.state = Tile.STATE_ACTIVE
          tile.label = getString(R.string.connecting)
          tile.icon = Icon.createWithResource(applicationContext, R.drawable.vpn_off)
        }
        ConnectionState.CONNECTED -> {
          tile.state = Tile.STATE_ACTIVE
          tile.label = getString(R.string.connected)
          tile.icon = Icon.createWithResource(applicationContext, R.drawable.vpn_on)
        }
      }

      tile.updateTile()
    }
  }

  private fun unsubscribe() {
    val messenger = serviceMessenger ?: return
    if (!isBound) return
    OblivionVpnService.unregisterConnectionStateObserver(CONNECTION_OBSERVER_KEY, messenger)
  }

  private companion object {
    private const val CONNECTION_OBSERVER_KEY = "quickstartToggleButton"
  }
}

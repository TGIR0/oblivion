package org.bepass.oblivion.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Messenger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bepass.oblivion.enums.ConnectionState
import org.bepass.oblivion.logging.SecureLog as Log
import org.bepass.oblivion.service.OblivionVpnService

@Singleton
class VpnServiceConnector @Inject constructor(@ApplicationContext private val context: Context) {
  private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
  val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  private val observerKey = "compose"

  private var isBound = false
  private var serviceMessenger: Messenger? = null

  private val connection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, service: IBinder) {
        val messenger = Messenger(service)
        serviceMessenger = messenger
        isBound = true
        OblivionVpnService.registerConnectionStateObserver(observerKey, messenger) { state ->
          _connectionState.value = state
        }
      }

      override fun onServiceDisconnected(name: ComponentName) {
        isBound = false
        serviceMessenger = null
        _connectionState.value = ConnectionState.DISCONNECTED
      }
    }

  @Synchronized
  fun ensureBound() {
    if (isBound) return
    try {
      context.bindService(
        Intent(context, OblivionVpnService::class.java),
        connection,
        Context.BIND_AUTO_CREATE,
      )
    } catch (securityFailure: SecurityException) {
      Log.w(TAG, "VPN service binding was denied", securityFailure)
      isBound = false
    }
  }

  @Synchronized
  fun release() {
    if (!isBound) return

    val messenger = serviceMessenger
    if (messenger != null) {
      OblivionVpnService.unregisterConnectionStateObserver(observerKey, messenger)
    }

    context.unbindService(connection)
    isBound = false
    serviceMessenger = null
  }

  private companion object {
    const val TAG = "VpnServiceConnector"
  }
}

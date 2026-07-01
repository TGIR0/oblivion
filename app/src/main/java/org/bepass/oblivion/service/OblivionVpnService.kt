@file:Suppress("LargeClass", "TooManyFunctions")

package org.bepass.oblivion.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hev.htproxy.TProxyService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bepass.oblivion.R
import org.bepass.oblivion.dns.AppDnsResolverFactory
import org.bepass.oblivion.enums.ConnectionState
import org.bepass.oblivion.enums.SplitTunnelMode
import org.bepass.oblivion.enums.VpnCoreType
import org.bepass.oblivion.model.PsiphonConfigV2
import org.bepass.oblivion.model.TunnelConfigV2
import org.bepass.oblivion.model.VpnConfig
import org.bepass.oblivion.security.AndroidSecureStore
import org.bepass.oblivion.ui.MainActivity
import org.bepass.oblivion.utils.FileManager
import org.bepass.oblivion.utils.HostPortParser
import tun2socks.Engine
import tun2socks.EngineListener
import tun2socks.SecureStore
import tun2socks.SocketProtector
import tun2socks.Tun2socks

@SuppressLint("VpnServicePolicy")
class OblivionVpnService : VpnService() {
  private val serviceMessenger = Messenger(IncomingHandler(this))

  private val connectionStateObservers: MutableMap<String, Messenger> = ConcurrentHashMap()

  private val stopLock = Any()
  private var stopping = false

  private val connectVerifyLock = Any()
  private var connectVerifyGeneration = 0L

  private val logFileLock = Any()

  private val networkLock = Any()
  private val nonVpnNetworks = HashSet<Network>()

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var coreJob: Job? = null
  private var connectVerifyJob: Job? = null
  private var networkLossJob: Job? = null
  private var nativeEngine: Engine? = null
  private var hevTunnel: TProxyService? = null
  private val secureStore by lazy { AndroidSecureStore(applicationContext) }
  private val nativeListener =
    object : EngineListener {
      override fun onStateChanged(statusJSON: String) {
        Log.d(TAG, "Native state: $statusJSON")
      }

      override fun onError(errorJSON: String) {
        Log.e(TAG, "Native error: $errorJSON")
      }

      override fun onLog(message: String) {
        serviceScope.launch(Dispatchers.IO) { appendLogsToFile(message) }
      }
    }
  private val nativeSecureStore =
    object : SecureStore {
      override fun get(key: String): String = secureStore.get(key)

      override fun put(key: String, value: String) = secureStore.put(key, value)

      override fun delete(key: String) = secureStore.delete(key)
    }

  private var notification: Notification? = null
  private var vpnInterface: ParcelFileDescriptor? = null
  private var bindAddress: String? = null
  private var wakeLock: PowerManager.WakeLock? = null

  private var lastKnownState: ConnectionState = ConnectionState.DISCONNECTED
  private var connectionStartTime = 0L

  private var serviceIntent: Intent? = null
  private var pingCounter = 0

  private var connectivityManager: ConnectivityManager? = null
  private var networkCallback: ConnectivityManager.NetworkCallback? = null
  private var currentConfig: VpnConfig? = null

  private val baseHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(PING_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(PING_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .dns(AppDnsResolverFactory.createDiagnosticsDns())
      .build()
  }

  override fun onBind(intent: Intent?): IBinder? {
    val action = intent?.action
    /*
     * If we override onBind, we never receive onRevoke.
     * return superclass onBind when action is SERVICE_INTERFACE to receive onRevoke
     * lifecycle call.
     */
    return if (action != null && action == SERVICE_INTERFACE) {
      super.onBind(intent)
    } else {
      serviceMessenger.binder
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action
    if (intent != null) {
      serviceIntent = intent
    }

    Log.d(TAG, "onStartCommand called with action: $action, lastKnownState: $lastKnownState")
    when (action) {
      FLAG_VPN_START -> start()
      FLAG_VPN_STOP -> requestStop(stopSelf = true, reason = "user_stop")
    }

    return START_STICKY
  }

  override fun onCreate() {
    super.onCreate()
    FileManager.initialize(applicationContext)
    createNotificationChannel()
    connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
  }

  override fun onDestroy() {
    stopNow(stopSelf = false, reason = "destroy")
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onRevoke() {
    requestStop(stopSelf = true, reason = "revoke")
  }

  @MainThread
  private fun start() {
    val intent = serviceIntent
    val stopInProgress = synchronized(stopLock) { stopping }
    val startBlockedReason =
      when {
        intent == null -> "missing serviceIntent"
        lastKnownState != ConnectionState.DISCONNECTED -> "current state is $lastKnownState"
        stopInProgress -> "stop in progress"
        !VpnCoreType.getCurrent().isReady -> "selected core has not passed production gates"
        else -> null
      }

    if (startBlockedReason != null) {
      Log.w(TAG, "Start ignored: $startBlockedReason")
      return
    }

    setLastKnownState(ConnectionState.CONNECTING)
    acquireWakeLock()

    val startedForeground =
      try {
        createNotification()
        val fgNotification = requireNotNull(notification)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
          startForeground(
            NOTIFICATION_ID,
            fgNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
          )
        } else {
          startForeground(NOTIFICATION_ID, fgNotification)
        }
        true
      } catch (expected: Throwable) {
        Log.e(TAG, "Failed to start foreground notification", expected)
        requestStop(stopSelf = true, reason = "start_foreground_failed")
        false
      }

    if (startedForeground) {
      coreJob?.cancel()
      coreJob = serviceScope.launch {
        Log.d(TAG, "Preparing core")
        try {
          withContext(Dispatchers.IO) {
            rotateLogFile(force = true)
            bindAddress = "127.0.0.1:0"
          }
          withContext(Dispatchers.Main) { startNetworkMonitoring() }
          configure()
        } catch (expected: Throwable) {
          Log.e(TAG, "Error in start execution", expected)
          requestStop(stopSelf = true, reason = "start_failed")
        }
      }
    }
  }

  private fun acquireWakeLock() {
    if (wakeLock != null) return
    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "oblivion:vpn")
    lock.setReferenceCounted(false)
    runCatching { lock.acquire(WAKELOCK_ACQUIRE_DURATION_MS) }
      .onFailure { Log.w(TAG, "Unable to acquire wake lock", it) }
    wakeLock = lock

    serviceScope.launch {
      delay(WAKELOCK_RELEASE_DELAY_MS)
      try {
        if (lock.isHeld) {
          lock.release()
          if (wakeLock === lock) {
            wakeLock = null
          }
        }
      } catch (_: Throwable) {}
    }
  }

  private suspend fun configure() {
    try {
      val intent = checkNotNull(serviceIntent) { "Missing serviceIntent" }
      val bAddress = requireNotNull(bindAddress) { "Missing bindAddress" }

      val configExtra =
        androidx.core.content.IntentCompat.getParcelableExtra(
          intent,
          "config",
          VpnConfig::class.java,
        )
      var config =
        configExtra?.copy(bindAddress = bAddress) ?: VpnConfig.fromIntent(intent, bAddress)

      val coreType = VpnCoreType.getCurrent()
      val nativeConfig =
        TunnelConfigV2(
          mode = coreType.tunnelMode,
          proxyOnly = true,
          mtu = VPN_MTU,
          dns = parseDns(config.vpnDns),
          endpoint = config.endpoint,
          psiphon = PsiphonConfigV2(country = config.psiphonCountry),
        )
      val configJson = Json.encodeToString(nativeConfig)
      val engine =
        Tun2socks.newEngine(
          nativeListener,
          SocketProtector { fd -> protect(fd.toInt()) },
          nativeSecureStore,
        )
      nativeEngine = engine
      engine.validateConfig(configJson)
      val startResult = withContext(Dispatchers.IO) { engine.start(configJson) }
      val proxyAddress =
        HostPortParser.parseOrNull(startResult.proxyAddress)
          ?: error("Native core returned an invalid proxy address")
      config = config.copy(bindAddress = startResult.proxyAddress)
      bindAddress = startResult.proxyAddress
      currentConfig = config
      FileManager.set(FileManager.KeyHolder.RUNTIME_SOCKS_PORT, proxyAddress.port())
      FileManager.set(FileManager.KeyHolder.RUNTIME_BIND_ADDRESS, startResult.proxyAddress)

      if (!config.proxyMode) {
        val builder = Builder()
        configureVpnBuilder(builder, config.vpnDns)
        Log.i(TAG, "Establishing VPN interface...")
        vpnInterface = builder.establish()
        val iface = requireNotNull(vpnInterface) { "failed to establish VPN interface" }
        val hev = TProxyService()
        hev.TProxyStartService(
          buildHevConfig(
            proxyAddress.host(),
            proxyAddress.port(),
            startResult.proxyUsername,
            startResult.proxyPassword,
          ),
          iface.fd,
        )
        delay(HEV_START_SETTLE_MS)
        check(hev.TProxyIsRunning()) { "HEV exited during startup" }
        hevTunnel = hev
      }

      Log.i(TAG, "Core proxy and TUN adapter are ready (mode=${coreType.modeId})")
      startConnectVerification()
      updateNotification()
    } catch (expected: Throwable) {
      Log.e(TAG, "Configuration failed", expected)
      requestStop(stopSelf = true, reason = "configure_failed")
    }
  }

  private fun parseDns(value: String): List<String> =
    value.split(Regex("[,\\s;]+")).map(String::trim).filter(String::isNotEmpty).ifEmpty {
      listOf("1.1.1.1", "1.0.0.1")
    }

  private fun buildHevConfig(
    proxyHost: String,
    proxyPort: Int,
    username: String,
    password: String,
  ): String {
    require(proxyHost == "127.0.0.1") { "HEV proxy must be loopback-only" }
    require(proxyPort in MIN_PORT..MAX_PORT)
    require(username.matches(SESSION_CREDENTIAL_PATTERN))
    require(password.matches(SESSION_CREDENTIAL_PATTERN))
    return """
      tunnel:
        mtu: $VPN_MTU
        ipv4: $PRIVATE_VLAN4_CLIENT
        ipv6: '$PRIVATE_VLAN6_CLIENT'
      socks5:
        address: 127.0.0.1
        port: $proxyPort
        udp: 'udp'
        username: '$username'
        password: '$password'
      misc:
        task-stack-size: 24576
        tcp-buffer-size: 8192
        max-session-count: 2048
        connect-timeout: 10000
        log-level: warn
      """
      .trimIndent()
  }

  private fun configureVpnBuilder(builder: VpnService.Builder, dnsSetting: String) {
    builder
      .setSession("oblivion")
      .setMtu(VPN_MTU)
      .addAddress(PRIVATE_VLAN4_CLIENT, PRIVATE_VLAN4_PREFIX_LENGTH)
      .addAddress(PRIVATE_VLAN6_CLIENT, PRIVATE_VLAN6_PREFIX_LENGTH)
      .addRoute("0.0.0.0", 0)
      .addRoute("::", 0)

    addDnsServers(builder, dnsSetting)

    val splitTunnelMode = SplitTunnelMode.getSplitTunnelMode()
    if (splitTunnelMode == SplitTunnelMode.BLACKLIST) {
      val splitTunnelApps = getSplitTunnelApps()
      for (pkg in splitTunnelApps) {
        try {
          builder.addDisallowedApplication(pkg)
        } catch (_: PackageManager.NameNotFoundException) {}
      }
    }
  }

  private fun addDnsServers(builder: VpnService.Builder, dnsSetting: String?) {
    val value = dnsSetting?.trim().orEmpty()
    var added = false

    for (token in value.split(Regex("[,\\s;]+"))) {
      val dns = token.trim()
      if (dns.isEmpty()) continue
      try {
        builder.addDnsServer(dns)
        added = true
      } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Ignoring invalid DNS server: $dns", e)
      }
    }

    if (!added) {
      builder.addDnsServer("1.1.1.1")
      builder.addDnsServer("1.0.0.1")
    }
  }

  private fun getSplitTunnelApps(): Set<String> {
    return FileManager.getStringSet("splitTunnelApps", emptySet())
  }

  private fun startConnectVerification() {
    val generation =
      synchronized(connectVerifyLock) {
        connectVerifyGeneration += 1
        connectVerifyGeneration
      }

    connectVerifyJob?.cancel()
    connectVerifyJob = serviceScope.launch { runConnectVerification(generation) }
  }

  private fun stopConnectVerification() {
    synchronized(connectVerifyLock) { connectVerifyGeneration += 1 }
    connectVerifyJob?.cancel()
    connectVerifyJob = null
  }

  private suspend fun runConnectVerification(generation: Long) =
    kotlinx.coroutines.coroutineScope {
      val startMs = SystemClock.elapsedRealtime()
      var backoffMs = CONNECT_VERIFY_INITIAL_BACKOFF_MS

      while (kotlin.coroutines.coroutineContext.isActive) {
        val stillCurrentGeneration =
          synchronized(connectVerifyLock) { generation == connectVerifyGeneration }
        if (!stillCurrentGeneration) return@coroutineScope

        val stopInProgress = synchronized(stopLock) { stopping }
        if (stopInProgress) return@coroutineScope

        if (lastKnownState != ConnectionState.CONNECTING) return@coroutineScope

        val ok =
          try {
            pingOverHTTP()
          } catch (expected: Throwable) {
            Log.w(TAG, "Connect verification attempt failed", expected)
            false
          }

        if (ok) {
          withContext(Dispatchers.Main) {
            setLastKnownState(ConnectionState.CONNECTED)
            updateNotification()
          }
          return@coroutineScope
        }

        val elapsed = SystemClock.elapsedRealtime() - startMs
        if (elapsed >= CONNECT_VERIFY_TIMEOUT_MS) {
          Log.w(TAG, "Connect verification timed out")
          requestStop(stopSelf = true, reason = "connect_verify_timeout")
          return@coroutineScope
        }

        val delayMs = min(backoffMs, CONNECT_VERIFY_MAX_BACKOFF_MS)
        val jitter = max(0L, delayMs / CONNECT_VERIFY_JITTER_DIVISOR)
        val sleepMs = delayMs + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1)
        delay(sleepMs)
        backoffMs = min(backoffMs * 2, CONNECT_VERIFY_MAX_BACKOFF_MS)
      }
    }

  private fun pingOverHTTP(): Boolean {
    pingCounter++
    Log.i(TAG, "Pinging (attempt #$pingCounter)")
    if (currentConfig?.proxyMode != true) {
      val request = Request.Builder().url(CONNECT_VERIFY_URL).build()
      return try {
        baseHttpClient.newCall(request).execute().use { response ->
          pingCounter = 0
          response.isSuccessful
        }
      } catch (e: IOException) {
        Log.e(TAG, "Error executing tunneled ping", e)
        false
      }
    }

    // Native Start already performed a health check through the authenticated proxy.
    // Java's global SOCKS authenticator is intentionally not modified here.
    if (nativeEngine?.getStatus()?.contains("\"state\":\"CONNECTED\"") == true) {
      pingCounter = 0
      return true
    }
    return false
  }

  private fun startNetworkMonitoring() {
    if (connectivityManager == null) {
      connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    val cm = connectivityManager ?: return
    if (networkCallback != null) return

    val request =
      NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .build()

    val callback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          synchronized(networkLock) {
            nonVpnNetworks.add(network)
            networkLossJob?.cancel()
            networkLossJob = null
          }
        }

        override fun onLost(network: Network) {
          val lost =
            synchronized(networkLock) {
              nonVpnNetworks.remove(network)
              nonVpnNetworks.isEmpty()
            }
          if (!lost) return
          scheduleStopOnNetworkLoss()
        }
      }

    networkCallback = callback
    try {
      cm.registerNetworkCallback(request, callback)
    } catch (expected: Exception) {
      Log.w(TAG, "Failed to register network callback", expected)
      networkCallback = null
    }
  }

  private fun scheduleStopOnNetworkLoss() {
    synchronized(networkLock) {
      if (networkLossJob?.isActive == true) return
      networkLossJob = serviceScope.launch {
        delay(NETWORK_LOSS_GRACE_MS)
        val stillLost =
          synchronized(networkLock) {
            networkLossJob = null
            nonVpnNetworks.isEmpty()
          }
        if (!stillLost) return@launch
        if (
          lastKnownState == ConnectionState.CONNECTING ||
            lastKnownState == ConnectionState.CONNECTED
        ) {
          requestStop(stopSelf = true, reason = "network_lost")
        }
      }
    }
  }

  private fun stopNetworkMonitoring() {
    val cm = connectivityManager
    val cb = networkCallback
    networkCallback = null
    if (cm != null && cb != null) {
      runCatching { cm.unregisterNetworkCallback(cb) }
        .onFailure { Log.w(TAG, "Unable to unregister network callback", it) }
    }

    synchronized(networkLock) {
      nonVpnNetworks.clear()
      networkLossJob?.cancel()
      networkLossJob = null
    }
  }

  private fun requestStop(stopSelf: Boolean, reason: String) {
    synchronized(stopLock) {
      if (stopping) return
      stopping = true
    }

    stopConnectVerification()

    serviceScope.launch { stopNow(stopSelf, reason) }
  }

  private fun stopNow(stopSelf: Boolean, reason: String) {
    Log.i(TAG, "Stopping VPN ($reason)")

    synchronized(stopLock) { stopping = true }

    pingCounter = 0
    coreJob?.cancel()
    coreJob = null
    stopConnectVerification()
    stopNetworkMonitoring()
    val hev = hevTunnel
    hevTunnel = null
    runCatching { hev?.TProxyStopService() }.onFailure { t -> Log.w(TAG, "HEV stop failed", t) }
    val engine = nativeEngine
    nativeEngine = null
    runCatching { engine?.stop() }.onFailure { t -> Log.w(TAG, "Native engine stop failed", t) }

    val iface = vpnInterface
    vpnInterface = null
    if (iface != null) {
      runCatching { iface.close() }.onFailure { t -> Log.w(TAG, "Error closing VPN interface", t) }
    }

    stopForegroundService()
    releaseWakeLock()

    FileManager.set(FileManager.KeyHolder.RUNTIME_SOCKS_PORT, 0)
    FileManager.set(FileManager.KeyHolder.RUNTIME_BIND_ADDRESS, "")

    setLastKnownState(ConnectionState.DISCONNECTED)

    synchronized(stopLock) { stopping = false }

    if (stopSelf) {
      stopSelf()
    }
  }

  private fun stopForegroundService() {
    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
      .onFailure { Log.w(TAG, "Unable to stop foreground service", it) }
    NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
  }

  private fun releaseWakeLock() {
    val lock = wakeLock ?: return
    runCatching {
        if (lock.isHeld) {
          lock.release()
        }
      }
      .onFailure { Log.w(TAG, "Unable to release wake lock", it) }
    wakeLock = null
  }

  private fun getLogFile(): File = File(applicationContext.filesDir, LOG_FILE_NAME)

  private fun rotateLogFile(force: Boolean) {
    synchronized(logFileLock) { rotateLogFileLocked(force) }
  }

  private fun rotateLogFileLocked(force: Boolean) {
    val filesDir = applicationContext.filesDir
    val current = File(filesDir, LOG_FILE_NAME)
    if (!current.exists()) {
      if (force) {
        overwriteLogFileLocked("=== Oblivion session ${System.currentTimeMillis()} ===\n")
      }
      return
    }

    if (!force && current.length() < LOG_ROTATE_MAX_BYTES) return

    val oldest = File(filesDir, "$LOG_FILE_NAME.$LOG_ROTATE_MAX_FILES")
    if (oldest.exists() && !oldest.delete()) {
      Log.w(TAG, "Failed to delete old log file: ${oldest.name}")
    }

    for (i in LOG_ROTATE_MAX_FILES - 1 downTo 1) {
      val from = File(filesDir, "$LOG_FILE_NAME.$i")
      if (!from.exists()) continue
      val to = File(filesDir, "$LOG_FILE_NAME.${i + 1}")
      if (to.exists() && !to.delete()) {
        Log.w(TAG, "Failed to delete rotated log file: ${to.name}")
      }
      from.renameTo(to)
    }

    val rotated = File(filesDir, "$LOG_FILE_NAME.1")
    if (rotated.exists() && !rotated.delete()) {
      Log.w(TAG, "Failed to delete rotated log file: ${rotated.name}")
    }
    current.renameTo(rotated)
    overwriteLogFileLocked("=== Oblivion session ${System.currentTimeMillis()} ===\n")
  }

  private fun overwriteLogFileLocked(header: String) {
    try {
      applicationContext.openFileOutput(LOG_FILE_NAME, Context.MODE_PRIVATE).use { fos ->
        fos.write(header.toByteArray(StandardCharsets.UTF_8))
      }
    } catch (e: IOException) {
      Log.e(TAG, "Error writing log header", e)
    }
  }

  private fun appendLogsToFile(logs: String) {
    if (logs.isBlank()) return
    synchronized(logFileLock) {
      rotateLogFileLocked(force = false)
      try {
        FileOutputStream(getLogFile(), true).use { fos ->
          fos.write(logs.toByteArray(StandardCharsets.UTF_8))
          if (!logs.endsWith("\n")) {
            fos.write('\n'.code)
          }
        }
      } catch (e: IOException) {
        Log.e(TAG, "Error appending logs", e)
      }
    }
  }

  private fun publishConnectionState(state: ConnectionState) {
    if (connectionStateObservers.isEmpty()) return
    for (observerKey in connectionStateObservers.keys) {
      publishConnectionStateTo(observerKey, state)
    }
  }

  private fun publishConnectionStateTo(observerKey: String, state: ConnectionState) {
    Log.i("Publisher", "Publishing state $state to $observerKey")
    val observer = connectionStateObservers[observerKey] ?: return
    val args = Bundle().apply { putString("state", state.toString()) }
    val replyMsg = Message.obtain(null, MSG_TILE_STATE_SUBSCRIPTION_RESULT).apply { data = args }
    try {
      observer.send(replyMsg)
    } catch (e: RemoteException) {
      Log.e(TAG, "Error publishing connection state to $observerKey", e)
    }
  }

  @Synchronized
  private fun setLastKnownState(newState: ConnectionState) {
    if (lastKnownState == newState) return
    Log.i(TAG, "Connection state changed from $lastKnownState to $newState")
    lastKnownState = newState

    if (newState == ConnectionState.CONNECTING) {
      connectionStartTime = System.currentTimeMillis()
      Log.d(TAG, "Connection attempt started at: $connectionStartTime")
    } else if (newState == ConnectionState.CONNECTED) {
      val connectionEndTime = System.currentTimeMillis()
      val connectionDuration = connectionEndTime - connectionStartTime
      Log.i(TAG, "VPN connected in ${connectionDuration}ms")
    }

    publishConnectionState(newState)
  }

  private fun getNotificationText(): String {
    val config = currentConfig
    val proxyMode = config?.proxyMode == true
    val portInUse = config?.bindAddress?.split(":")?.lastOrNull()

    val baseText =
      when (VpnCoreType.getCurrent()) {
        VpnCoreType.WARP -> getString(R.string.notification_warp)
        VpnCoreType.VWARP -> "VWarp MASQUE"
        VpnCoreType.PSIPHON -> "Psiphon"
        VpnCoreType.PSIPHON_OVER_WARP -> "Psiphon over WARP"
        VpnCoreType.WARP_OVER_PSIPHON -> "WARP over Psiphon"
        VpnCoreType.PSIPHON_FRONTED -> "Psiphon Fronted"
        VpnCoreType.WIREGUARD -> "WireGuard"
      }

    val proxyText =
      if (proxyMode && !portInUse.isNullOrBlank()) {
        " " + getString(R.string.notification_proxy_suffix, portInUse)
      } else {
        ""
      }

    val stateText =
      if (lastKnownState == ConnectionState.CONNECTED) {
        getString(R.string.connected)
      } else {
        getString(R.string.connecting)
      }

    return "$stateText • $baseText$proxyText"
  }

  private fun createNotificationChannel() {
    val notificationManager = NotificationManagerCompat.from(this)
    val notificationChannel =
      NotificationChannelCompat.Builder(
          NOTIFICATION_CHANNEL_ID,
          NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )
        .setName("Oblivion VPN")
        .build()
    notificationManager.createNotificationChannel(notificationChannel)
  }

  private fun createNotification() {
    val disconnectIntent =
      Intent(this, OblivionVpnService::class.java).apply { action = FLAG_VPN_STOP }
    val disconnectPendingIntent =
      PendingIntent.getService(this, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE)

    val contentPendingIntent =
      PendingIntent.getActivity(
        this,
        2,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
      )

    notification =
      NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle("Oblivion VPN")
        .setContentText("Oblivion - ${getNotificationText()}")
        .setSmallIcon(R.mipmap.ic_notification)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setAutoCancel(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(contentPendingIntent)
        .addAction(R.drawable.vpn_off, getString(R.string.cancel), disconnectPendingIntent)
        .build()
  }

  private fun updateNotification() {
    try {
      createNotification()
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (
          checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
          return
        }
      }
      NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, requireNotNull(notification))
    } catch (e: SecurityException) {
      Log.w(TAG, "Notification update blocked", e)
    } catch (expected: Throwable) {
      Log.w(TAG, "Failed to update notification", expected)
    }
  }

  private class IncomingHandler(service: OblivionVpnService) : Handler(Looper.getMainLooper()) {
    private val serviceRef = WeakReference(service)

    override fun handleMessage(msg: Message) {
      val service = serviceRef.get() ?: return
      when (msg.what) {
        MSG_CONNECTION_STATE_SUBSCRIBE -> {
          val key = msg.data.getString("key")
          if (key.isNullOrBlank()) {
            Log.e("IncomingHandler", "No key was provided for the connection state observer")
          } else if (!service.connectionStateObservers.containsKey(key)) {
            service.connectionStateObservers[key] = msg.replyTo
            service.publishConnectionStateTo(key, service.lastKnownState)
          }
        }
        MSG_CONNECTION_STATE_UNSUBSCRIBE -> {
          val key = msg.data.getString("key")
          if (key.isNullOrBlank()) {
            Log.e("IncomingHandler", "No observer was specified to unregister")
          } else {
            service.connectionStateObservers.remove(key)
          }
        }
        else -> super.handleMessage(msg)
      }
    }
  }

  companion object {
    const val FLAG_VPN_START = "org.bepass.oblivion.START"
    const val FLAG_VPN_STOP = "org.bepass.oblivion.STOP"

    internal const val MSG_CONNECTION_STATE_SUBSCRIBE = 1
    internal const val MSG_CONNECTION_STATE_UNSUBSCRIBE = 2
    internal const val MSG_TILE_STATE_SUBSCRIPTION_RESULT = 3

    private const val TAG = "oblivionVPN"
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_CHANNEL_ID = "vpn_service"
    private const val LOG_FILE_NAME = "logs.txt"
    private const val CONNECT_VERIFY_TIMEOUT_MS = 30_000L
    private const val CONNECT_VERIFY_INITIAL_BACKOFF_MS = 500L
    private const val CONNECT_VERIFY_MAX_BACKOFF_MS = 5_000L
    private const val CONNECT_VERIFY_URL = "https://www.gstatic.com/generate_204"
    private const val NETWORK_LOSS_GRACE_MS = 5_000L
    private const val LOG_ROTATE_MAX_BYTES = 2L * 1024L * 1024L
    private const val LOG_ROTATE_MAX_FILES = 3
    private const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
    private const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"
    private const val PRIVATE_VLAN4_PREFIX_LENGTH = 30
    private const val PRIVATE_VLAN6_PREFIX_LENGTH = 126
    private const val VPN_MTU = 1280
    private const val HEV_START_SETTLE_MS = 500L
    private val SESSION_CREDENTIAL_PATTERN = Regex("[A-Za-z0-9_-]{43}")

    private const val CONNECT_VERIFY_JITTER_DIVISOR = 5L
    private const val PING_CONNECT_TIMEOUT_SECONDS = 5L
    private const val PING_READ_TIMEOUT_SECONDS = 2L

    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535

    private const val WAKELOCK_ACQUIRE_DURATION_MS = 10 * 60 * 1000L
    private const val WAKELOCK_RELEASE_DELAY_MS = 3 * 60 * 1000L

    @JvmStatic
    @Synchronized
    fun stopVpnService(context: Context) {
      val intent = Intent(context, OblivionVpnService::class.java).apply { action = FLAG_VPN_STOP }
      context.startService(intent)
    }

    @JvmStatic
    fun registerConnectionStateObserver(
      key: String,
      serviceMessenger: Messenger,
      observer: (ConnectionState) -> Unit,
    ) {
      val subscriptionMessage = Message.obtain(null, MSG_CONNECTION_STATE_SUBSCRIBE)
      subscriptionMessage.data = Bundle().apply { putString("key", key) }

      subscriptionMessage.replyTo =
        Messenger(
          Handler(
            Looper.getMainLooper(),
            Handler.Callback { incoming ->
              val stateRaw = incoming.data.getString("state")
              val state =
                stateRaw?.let { runCatching { ConnectionState.valueOf(it) }.getOrNull() }
                  ?: ConnectionState.DISCONNECTED

              if (incoming.what == MSG_TILE_STATE_SUBSCRIPTION_RESULT) {
                observer(state)
              }
              true
            },
          )
        )

      try {
        serviceMessenger.send(subscriptionMessage)
      } catch (e: RemoteException) {
        Log.e(TAG, "Error sending subscription message", e)
      }
    }

    @JvmStatic
    fun unregisterConnectionStateObserver(key: String, serviceMessenger: Messenger) {
      val unsubscriptionMessage = Message.obtain(null, MSG_CONNECTION_STATE_UNSUBSCRIBE)
      unsubscriptionMessage.data = Bundle().apply { putString("key", key) }
      try {
        serviceMessenger.send(unsubscriptionMessage)
      } catch (e: RemoteException) {
        Log.e(TAG, "Error sending unsubscription message", e)
      }
    }
  }
}

package org.bepass.oblivion.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.bepass.oblivion.R;
import org.bepass.oblivion.enums.ConnectionState;
import org.bepass.oblivion.enums.SplitTunnelMode;
import org.bepass.oblivion.interfaces.ConnectionStateChangeListener;
import org.bepass.oblivion.ui.MainActivity;
import org.bepass.oblivion.utils.FileManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import tun2socks.StartOptions;
import tun2socks.Tun2socks;

@SuppressLint("VpnServicePolicy")
public class OblivionVpnService extends VpnService {
    public static final String FLAG_VPN_START = "org.bepass.oblivion.START";
    public static final String FLAG_VPN_STOP = "org.bepass.oblivion.STOP";
    static final int MSG_CONNECTION_STATE_SUBSCRIBE = 1;
    static final int MSG_CONNECTION_STATE_UNSUBSCRIBE = 2;
    static final int MSG_TILE_STATE_SUBSCRIPTION_RESULT = 3;

    private static final String TAG = "oblivionVPN";
    private static final String PRIVATE_VLAN4_CLIENT = "172.19.0.1";
    private static final String PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Messenger serviceMessenger = new Messenger(new IncomingHandler(this));
    private final Map<String, Messenger> connectionStateObservers = new ConcurrentHashMap<>();
    private final Runnable logRunnable = new Runnable() {
        @Override
        public void run() {
            if (scheduler != null && !scheduler.isShutdown()) {
                final android.content.Context appContext = getApplicationContext();
                scheduler.execute(() -> {
                    String logMessages = Tun2socks.getLogMessages();
                    if (!logMessages.isEmpty()) {
                        Log.d(TAG, "Tun2Socks: " + logMessages);
                        // Sync UI state with logs
                        if (logMessages.contains("handshake complete")) {
                            if (lastKnownState != ConnectionState.CONNECTED) {
                                setLastKnownState(ConnectionState.CONNECTED);
                                createNotification();
                                startForeground(1, notification);
                            }
                        } else if (logMessages.contains("failed to run warp")) {
                            onRevoke();
                        }

                        try (FileOutputStream fos = appContext.openFileOutput("logs.txt", Context.MODE_APPEND)) {
                            fos.write((logMessages + "\n").getBytes());
                        } catch (IOException e) {
                            Log.e(TAG, "Error writing to log file", e);
                        }
                    }
                });
            }
            // Adding jitter to avoid exact timing
            long jitter = (long) (Math.random() * 500); // Random delay between 0 to 500ms
            handler.postDelayed(this, 500 + jitter); // Poll every ~2 seconds with some jitter
        }
    };
    // For JNI Calling in a new threa
    private ExecutorService executorService;
    // For PingHTTPTestConnection to don't busy-waiting
    private ScheduledExecutorService scheduler;
    private Future<?> pingTaskFuture;
    private Notification notification;
    private ParcelFileDescriptor mInterface;
    private String bindAddress;
    private PowerManager.WakeLock wLock;
    private ConnectionState lastKnownState = ConnectionState.DISCONNECTED;
    private long connectionStartTime = 0;
    private Intent serviceIntent;
    private int pingCounter = 0;

    public static synchronized void stopVpnService(Context context) {
        Intent intent = new Intent(context, OblivionVpnService.class);
        intent.setAction(OblivionVpnService.FLAG_VPN_STOP);
        context.startService(intent);

    }
    public static void registerConnectionStateObserver(String key, Messenger serviceMessenger, ConnectionStateChangeListener observer) {
        // Create a message for the service
        Message subscriptionMessage = Message.obtain(null, OblivionVpnService.MSG_CONNECTION_STATE_SUBSCRIBE);
        Bundle data = new Bundle();
        data.putString("key", key);
        subscriptionMessage.setData(data);
        // Create a Messenger for the reply from the service
        subscriptionMessage.replyTo = new Messenger(new Handler(Looper.getMainLooper(), incomingMessage -> {
            ConnectionState state = ConnectionState.valueOf(incomingMessage.getData().getString("state"));
            if (incomingMessage.what == OblivionVpnService.MSG_TILE_STATE_SUBSCRIPTION_RESULT) {
                observer.onChange(state);
            }
            return true;
        }));
        try {
            // Send the message
            serviceMessenger.send(subscriptionMessage);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending subscription message", e);
        }
    }

    public static void unregisterConnectionStateObserver(String key, Messenger serviceMessenger) {
        Message unsubscriptionMessage = Message.obtain(null, OblivionVpnService.MSG_CONNECTION_STATE_UNSUBSCRIBE);
        Bundle data = new Bundle();
        data.putString("key", key);
        unsubscriptionMessage.setData(data);
        try {
            serviceMessenger.send(unsubscriptionMessage);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending unsubscription message", e);
        }
    }

    public static Map<String, Integer> splitHostAndPort(String hostPort) {
        if (hostPort == null || hostPort.isEmpty()) {
            return null;
        }
        Map<String, Integer> result = new HashMap<>();
        String host;
        int port = -1; // Default port value if not specified

        try {
            // Check if the host part is an IPv6 address (enclosed in square brackets)
            if (hostPort.startsWith("[")) {
                int closingBracketIndex = hostPort.indexOf(']');
                if (closingBracketIndex > 0) {
                    host = hostPort.substring(1, closingBracketIndex);
                    if (hostPort.length() > closingBracketIndex + 1 && hostPort.charAt(closingBracketIndex + 1) == ':') {
                        // There's a port number after the closing bracket
                        String portStr = hostPort.substring(closingBracketIndex + 2);
                        if (!portStr.isEmpty()) {
                            port = Integer.parseInt(portStr);
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Invalid IPv6 address format");
                }
            } else {
                // Handle IPv4 or hostname (split at the last colon)
                int lastColonIndex = hostPort.lastIndexOf(':');
                if (lastColonIndex > 0) {
                    host = hostPort.substring(0, lastColonIndex);
                    String portStr = hostPort.substring(lastColonIndex + 1);
                    if (!portStr.isEmpty()) {
                        port = Integer.parseInt(portStr);
                    }
                } else {
                    host = hostPort; // No port specified
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number format in: " + hostPort, e);
        }

        result.put(host, port);
        return result;
    }


    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore IOException on close()
            }
            return port;
        } catch (IOException ignored) {
        }
        throw new IllegalStateException("Could not find a free TCP/IP port to start embedded Jetty HTTP Server on");
    }

    public boolean pingOverHTTP(String bindAddress) {
        pingCounter++;
        Log.i(TAG, "Pinging (attempt #" + pingCounter + ")");
        Map<String, Integer> result = splitHostAndPort(bindAddress);
        if (result == null) {
            throw new RuntimeException("Could not split host and port of " + bindAddress);
        }
        String socksHost = result.keySet().iterator().next();
        int socksPort = result.values().iterator().next();
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksHost, socksPort));
        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(5, TimeUnit.SECONDS) // 5 seconds connection timeout
                .readTimeout(2, TimeUnit.SECONDS) // 5 seconds read timeout
                .build();
        Request request = new Request.Builder()
                .url("https://www.gstatic.com/generate_204")
                .build();
        try (Response response = client.newCall(request).execute()) {
            pingCounter = 0;
            return response.isSuccessful();
        } catch (IOException e) {
            Log.e(TAG, "Error executing ping", e);
            return false;
        }
    }


    public static String isLocalPortInUse(String bindAddress) {
        Map<String, Integer> result = splitHostAndPort(bindAddress);
        if (result == null) {
            return "exception";
        }
        int socksPort = result.values().iterator().next();
        if (socksPort == -1) {
            return "false"; // Consider no port specified as not in use
        }
        try {
            // ServerSocket try to open a LOCAL port
            new ServerSocket(socksPort).close();
            // local port can be opened, it's available
            return "false";
        } catch (IOException e) {
            // local port cannot be opened, it's in use
            return "true";
        }
    }


    private Set<String> getSplitTunnelApps() {
        return FileManager.getStringSet("splitTunnelApps", new HashSet<>());
    }


    private void performConnectionTest(String bindAddress, ConnectionStateChangeListener changeListener) {
        // Ping test removed by user request
        if (changeListener != null) {
             changeListener.onChange(ConnectionState.CONNECTED);
        }
    }

    // Gracefully shut down the scheduler
    private void shutdownScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            Log.i(TAG, "scheduler not null");
            scheduler.shutdownNow(); // Force shutdown immediately
            scheduler = null; // Ensure scheduler is nulled
            Log.i(TAG, "scheduler shutdownNow called.");
        }
    }

    // Shutdown executor service properly
    private void shutdownExecutor() {
        if (executorService != null && !executorService.isShutdown()) {
            Log.i(TAG, "ExecutorService not null");
            executorService.shutdownNow(); // Force shutdown immediately
            executorService = null; // Ensure executorService is nulled
            Log.i(TAG, "ExecutorService shutdownNow called.");
        } else {
            Log.w(TAG, "ExecutorService was not initialized or is already null");
        }
    }

    private void stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(1);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.deleteNotificationChannel("oblivion");
            }
        }
    }

    private String getBindAddress() {
        String port = serviceIntent.getStringExtra("USERSETTING_port");
        boolean enableLan = serviceIntent.getBooleanExtra("USERSETTING_lan",false);
        String bindAddress = "127.0.0.1:" + port;

        if (isLocalPortInUse(bindAddress).equals("true")) {
            port = String.valueOf(findFreePort());
        }
        String bind = "127.0.0.1:" + port;
        if (enableLan) {
            bind = "0.0.0.0:" + port;
        }
        return bind;
    }


    @Override
    public IBinder onBind(Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        /*
        If we override onBind, we never receive onRevoke.
        return superclass onBind when action is SERVICE_INTERFACE to receive onRevoke lifecycle call.
         */
        if (action != null && action.equals(VpnService.SERVICE_INTERFACE)) {
            return super.onBind(intent);
        }
        return serviceMessenger.getBinder();
    }

    private void clearLogFile() {
        try (FileOutputStream fos = getApplicationContext().openFileOutput("logs.txt", Context.MODE_PRIVATE)) {
            fos.write("".getBytes()); // Overwrite with empty content
        } catch (IOException e) {
            Log.e(TAG, "Error clearing log file", e);
        }
    }

    private void start() {
        if (lastKnownState != ConnectionState.DISCONNECTED) {
            onRevoke();
        }
        setLastKnownState(ConnectionState.CONNECTING);

        if (wLock == null) {
            wLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "oblivion:vpn");
            wLock.setReferenceCounted(false);
            wLock.acquire(10*60*1000L /*10 minutes*/);
            final PowerManager.WakeLock wakeLockRef = wLock;
            handler.postDelayed(() -> {
                if (wakeLockRef != null && wakeLockRef.isHeld()) {
                    wakeLockRef.release();
                    if (wLock == wakeLockRef) {
                        wLock = null;
                    }
                }
            }, 3 * 60 * 1000L /*3 minutes*/);
        }

        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }

        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }

        executorService.execute(() -> {
            Log.d("OblivionVpnService", "Starting VPN service");
            Log.i(TAG, "Clearing Logs");
            clearLogFile();
            bindAddress = getBindAddress();
            Log.i(TAG, "Configuring VPN service");
            try {
                Log.i(TAG, "Create Notification");
                createNotification();
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    startForeground(1, notification);
                }
                configure();
                // Waiting for logs to confirm connection...

            } catch (Throwable e) {
                onRevoke();
                Log.e(TAG, "Error in start execution", e);
                return;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        this.serviceIntent = intent;
        Log.d(TAG, "onStartCommand called with action: " + action + ", lastKnownState: " + lastKnownState);
        if (FLAG_VPN_START.equals(action)) {
            // Start VPN
            onRevoke();
            start();
        } else if (FLAG_VPN_STOP.equals(action)) {
            // Stop VPN
            onRevoke();
        }

        return START_STICKY;
    }

    private volatile boolean logPollingStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        // Defer starting log polling until after Tun2socks.start(so)
        createNotificationChannel(); // Create the notification channel here
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(logRunnable);
        logPollingStarted = false;
        if (wLock != null && wLock.isHeld()) {
            wLock.release();
            wLock = null;
        }
    }

    @Override
    public void onRevoke() {
        pingCounter = 0;
        handler.removeCallbacks(logRunnable);
        logPollingStarted = false;
        shutdownScheduler();
        shutdownExecutor();

        // Stop the foreground service
        stopForegroundService();
        Log.e(TAG, "VPN service is being forcefully stopped");

        // Release the wake lock if held
        if (wLock != null && wLock.isHeld()) {
            wLock.release();
            wLock = null;
            Log.e(TAG, "Wake lock released");
        } else {
            Log.w(TAG, "No wake lock to release");
        }
        // Close the VPN interface
        try {
            if (mInterface != null) {
                mInterface.close();
                mInterface = null; // Set to null to ensure it's not reused
                Log.e(TAG, "VPN interface closed successfully");
            } else {
                Log.w(TAG, "VPN interface was already null");
            }
        } catch (IOException e) {
            Log.e(TAG, "Critical error closing the VPN interface", e);
        }

        // Stop Tun2socks
        try {
            Tun2socks.stop();
            Log.e(TAG, "Tun2socks stopped successfully");
        } catch (Exception e) {
            Log.e(TAG, "Critical error stopping Tun2socks", e);
        }

        // Set the last known state to DISCONNECTED
        setLastKnownState(ConnectionState.DISCONNECTED);

        Log.e(TAG, "VPN stopped successfully or encountered errors. Check logs for details.");
    }

    private void publishConnectionState(ConnectionState state) {
        if (!connectionStateObservers.isEmpty()) {
            for (String observerKey : connectionStateObservers.keySet())
                publishConnectionStateTo(observerKey, state);
        }
    }

    private void publishConnectionStateTo(String observerKey, ConnectionState state) {
        Log.i("Publisher", "Publishing state " + state + " to " + observerKey);
        Messenger observer = connectionStateObservers.get(observerKey);
        if (observer == null) return;
        Bundle args = new Bundle();
        args.putString("state", state.toString());
        Message replyMsg = Message.obtain(null, MSG_TILE_STATE_SUBSCRIPTION_RESULT);
        replyMsg.setData(args);
        try {
            observer.send(replyMsg);
        } catch (RemoteException e) {
            Log.e(TAG, "Error publishing connection state to " + observerKey, e);
        }
    }

    private synchronized void setLastKnownState(ConnectionState newState) {
        if (lastKnownState != newState) {
            Log.i(TAG, "Connection state changed from " + lastKnownState + " to " + newState);
            lastKnownState = newState;

            if (newState == ConnectionState.CONNECTING) {
                connectionStartTime = System.currentTimeMillis();
                Log.d(TAG, "Connection attempt started at: " + connectionStartTime);
            } else if (newState == ConnectionState.CONNECTED) {
                long connectionEndTime = System.currentTimeMillis();
                long connectionDuration = connectionEndTime - connectionStartTime;
                 Log.i(TAG, "VPN connected in " + connectionDuration + "ms");
            }

            // Notify observers
            publishConnectionState(newState);
        }
    }

    private String getNotificationText() {
        boolean useWarp = serviceIntent.getBooleanExtra("USERSETTING_gool", false);
        boolean proxyMode = serviceIntent.getBooleanExtra("USERSETTING_proxymode", false);
        String portInUse = serviceIntent.getStringExtra("USERSETTING_port");
        String baseText;
        String proxyText = "";

        if (useWarp) {
            baseText = getString(R.string.notification_warp_in_warp);
        } else {
            baseText = getString(R.string.notification_warp);
        }

        if (proxyMode && portInUse != null && !portInUse.isEmpty()) {
            proxyText = " " + getString(R.string.notification_proxy_suffix, portInUse);
        }

        return baseText + proxyText;
    }

    private void createNotificationChannel() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        NotificationChannelCompat notificationChannel = new NotificationChannelCompat.Builder(
                "vpn_service", NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Oblivion VPN")
                .build();
        notificationManager.createNotificationChannel(notificationChannel);
    }

    private void createNotification() {
        Intent disconnectIntent = new Intent(this, OblivionVpnService.class);
        disconnectIntent.setAction(OblivionVpnService.FLAG_VPN_STOP);
        PendingIntent disconnectPendingIntent = PendingIntent.getService(
                this, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        notification = new NotificationCompat.Builder(this, "vpn_service")
                .setContentTitle("Oblivion VPN")
                .setContentText("Oblivion - " + getNotificationText())
                .setSmallIcon(R.mipmap.ic_notification)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setAutoCancel(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentPendingIntent)
                .addAction(R.drawable.vpn_off, getString(R.string.cancel), disconnectPendingIntent)
                .build();
    }

    public void addConnectionStateObserver(String key, Messenger messenger) {
        connectionStateObservers.put(key, messenger);
    }

    public void removeConnectionStateObserver(String key) {
        connectionStateObservers.remove(key);
    }

    private void configure() {
        Runnable configureTask = () -> {
            try {
                if(serviceIntent != null) {
                    // Clear any stale logs from previous sessions
                    Tun2socks.getLogMessages();
                    boolean proxyModeEnabled = serviceIntent.getBooleanExtra("USERSETTING_proxymode",false);
                    if (proxyModeEnabled) {
                        // Proxy mode logic
                        StartOptions so = new StartOptions();
                        so.setPath(getApplicationContext().getFilesDir().getAbsolutePath());
                        so.setVerbose(true);
                        so.setEndpoint(getEndpoint());
                        so.setBindAddress(bindAddress);
                        so.setLicense(Objects.requireNonNull(serviceIntent.getStringExtra("USERSETTING_license")).trim());
                        
                        String dns = serviceIntent.getStringExtra("USERSETTING_dns");
                        if (dns == null || dns.isEmpty()) dns = "1.1.1.1";
                        so.setDNS(dns);
                        
                        int region = serviceIntent.getIntExtra("USERSETTING_region", 0);
                        so.setRegion(region);

                        so.setEndpointType(serviceIntent.getIntExtra("USERSETTING_endpoint_type",0));

                        if (serviceIntent.getBooleanExtra("USERSETTING_gool", false)) {
                            so.setGool(true);
                        }
                        if (serviceIntent.getBooleanExtra("USERSETTING_masque", false)) {
                            so.setMasque(true);
                        }

                        // Runtime verification logs (PROXY mode)
                        {
                            String endpointEffective = getEndpoint();
                            String endpointLog = endpointEffective.isEmpty() ? "AUTO_SCAN" : endpointEffective;
                            int endpointType = serviceIntent.getIntExtra("USERSETTING_endpoint_type", 0);
                            boolean gool = serviceIntent.getBooleanExtra("USERSETTING_gool", false);
                            boolean masque = serviceIntent.getBooleanExtra("USERSETTING_masque", false);
                            String licenseVal = Objects.requireNonNull(serviceIntent.getStringExtra("USERSETTING_license")).trim();
                            String licenseLog = licenseVal.isEmpty() ? "(empty)" : ("len=" + licenseVal.length());
                            Log.i(TAG, "StartOptions {mode=PROXY, bindAddress=" + bindAddress
                                    + ", endpoint=" + endpointLog
                                    + ", endpointType=" + endpointType
                                    + ", gool=" + gool
                                    + ", masque=" + masque
                                    + ", region=" + region
                                    + ", dns=" + dns
                                    + ", license=" + licenseLog
                                    + ", path=" + getApplicationContext().getFilesDir().getAbsolutePath()
                                    + ", tunFd=-1}"
                            );
                        }

                        // Start tun2socks in proxy mode
                        Tun2socks.start(so);

                    } else {
                        // VPN mode logic
                        Builder builder = new Builder();
                        configureVpnBuilder(builder);

                        Log.i(TAG, "Establishing VPN interface...");
                        mInterface = builder.establish();
                        if (mInterface == null)
                            throw new RuntimeException("failed to establish VPN interface");
                        Log.i(TAG, "Interface created");

                        Log.i(TAG, "Creating StartOptions...");
                        StartOptions so = new StartOptions();
                        so.setPath(getApplicationContext().getFilesDir().getAbsolutePath());
                        so.setVerbose(true);
                        so.setEndpoint(getEndpoint());
                        so.setBindAddress(bindAddress);
                        so.setLicense(Objects.requireNonNull(serviceIntent.getStringExtra("USERSETTING_license")).trim());
                        
                        String dns = serviceIntent.getStringExtra("USERSETTING_dns");
                        if (dns == null || dns.isEmpty()) dns = "1.1.1.1";
                        so.setDNS(dns);

                        int region = serviceIntent.getIntExtra("USERSETTING_region", 0);
                        so.setRegion(region);
                        
                        so.setEndpointType(serviceIntent.getIntExtra("USERSETTING_endpoint_type",0));
                        so.setTunFd(mInterface.getFd());

                        if (serviceIntent.getBooleanExtra("USERSETTING_gool", false)) {
                            so.setGool(true);
                        }
                        if (serviceIntent.getBooleanExtra("USERSETTING_masque", false)) {
                            so.setMasque(true);
                        }

                        // Runtime verification logs (VPN mode)
                        {
                            String endpointEffective = getEndpoint();
                            String endpointLog = endpointEffective.isEmpty() ? "AUTO_SCAN" : endpointEffective;
                            int endpointType = serviceIntent.getIntExtra("USERSETTING_endpoint_type", 0);
                            boolean gool = serviceIntent.getBooleanExtra("USERSETTING_gool", false);
                            boolean masque = serviceIntent.getBooleanExtra("USERSETTING_masque", false);
                            String licenseVal = Objects.requireNonNull(serviceIntent.getStringExtra("USERSETTING_license")).trim();
                            String licenseLog = licenseVal.isEmpty() ? "(empty)" : ("len=" + licenseVal.length());
                            Log.i(TAG, "StartOptions {mode=VPN, bindAddress=" + bindAddress
                                    + ", endpoint=" + endpointLog
                                    + ", endpointType=" + endpointType
                                    + ", gool=" + gool
                                    + ", masque=" + masque
                                    + ", region=" + region
                                    + ", dns=" + dns
                                    + ", license=" + licenseLog
                                    + ", path=" + getApplicationContext().getFilesDir().getAbsolutePath()
                                    + ", tunFd=" + mInterface.getFd() + "}"
                            );
                        }

                        // Start tun2socks with VPN
                        Tun2socks.start(so);
                    }
                    if (!logPollingStarted) {
                        handler.post(logRunnable);
                        logPollingStarted = true;
                    }
                }
            } catch (Throwable e) {
                Log.e(TAG, "Configuration failed", e);
                onRevoke(); // Ensure we stop if configuration fails
            }
        };

        // Run the task on a separate thread if needed
        if (executorService != null && !executorService.isShutdown()) {
            executorService.execute(configureTask);
        } else {
            Log.e(TAG, "ExecutorService is not available to run configureTask");
        }
    }

    private void configureVpnBuilder(VpnService.Builder builder) throws Exception {
        builder.setSession("oblivion")
                .setMtu(1500)
                .addAddress(PRIVATE_VLAN4_CLIENT, 30)
                .addAddress(PRIVATE_VLAN6_CLIENT, 126)
                .addDnsServer("1.1.1.1")
                .addDnsServer("1.0.0.1")
                .addDisallowedApplication(getPackageName())
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0);

        // Determine split tunnel mode
        SplitTunnelMode splitTunnelMode = SplitTunnelMode.getSplitTunnelMode();
        if (splitTunnelMode == SplitTunnelMode.BLACKLIST) {
            Set<String> splitTunnelApps = getSplitTunnelApps();
            for (String packageName : splitTunnelApps) {
                try {
                    builder.addDisallowedApplication(packageName);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
        }
    }

    private String getEndpoint() {
        String endpoint = Objects.requireNonNull(serviceIntent.getStringExtra("USERSETTING_endpoint")).trim();
        if (endpoint.isEmpty() ||
                endpoint.equals("engage.cloudflareclient.com:2408") ||
                endpoint.equalsIgnoreCase("auto")) {
            return ""; // Let core scan and choose proper endpoint
        }
        return endpoint;
    }

    private static class IncomingHandler extends Handler {
        private final WeakReference<OblivionVpnService> serviceRef;

        IncomingHandler(OblivionVpnService service) {
            super(Looper.getMainLooper()); // Ensure the handler runs on the main thread
            serviceRef = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            OblivionVpnService service = serviceRef.get();
            if (service == null) return;

            switch (msg.what) {
                case MSG_CONNECTION_STATE_SUBSCRIBE: {
                    String key = msg.getData().getString("key");
                    if (key == null) {
                        Log.e("IncomingHandler", "No key was provided for the connection state observer");
                        return;
                    }
                    if (service.connectionStateObservers.containsKey(key)) {
                        // Already subscribed
                        return;
                    }
                    service.addConnectionStateObserver(key, msg.replyTo);
                    service.publishConnectionStateTo(key, service.lastKnownState);
                    break;
                }
                case MSG_CONNECTION_STATE_UNSUBSCRIBE: {
                    String key = msg.getData().getString("key");
                    if (key == null) {
                        Log.e("IncomingHandler", "No observer was specified to unregister");
                        return;
                    }
                    service.removeConnectionStateObserver(key);
                    break;
                }
                default: {
                    super.handleMessage(msg);
                }
            }
        }
    }
}

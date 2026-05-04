package org.bepass.oblivion.utils;

import static org.bepass.oblivion.service.OblivionVpnService.stopVpnService;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import org.bepass.oblivion.enums.ConnectionState;

import java.net.Inet4Address;
import java.net.InetAddress;

public class NetworkUtils {

    private static final Handler handler = new Handler(Looper.getMainLooper());
    public static void monitorInternetConnection(ConnectionState lastKnownConnectionState, Context context) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!lastKnownConnectionState.isDisconnected()) {
                    checkInternetConnectionAndDisconnectVPN(context);
                    handler.postDelayed(this, 3000); // Check every 3 seconds
                }
            }
        }, 5000); // Start checking after 5 seconds
    }
    // Periodically check internet connection and disconnect VPN if not connected
    private static void checkInternetConnectionAndDisconnectVPN(Context context) {
        if (!isConnectedToInternet(context)) {
            stopVpnService(context);
        }
    }

    private static boolean isConnectedToInternet(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
    public static String getLocalIpAddress(Context context) throws Exception {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return null;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return null;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return null;
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            throw new Exception("Operation not allowed on cellular data (4G). Please connect to Wi-Fi.");
        }

        LinkProperties linkProperties = connectivityManager.getLinkProperties(activeNetwork);
        if (linkProperties == null) {
            return null;
        }

        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            InetAddress inetAddress = linkAddress.getAddress();
            if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                return inetAddress.getHostAddress();
            }
        }

        return null; // Return null if no connection is available
    }


}

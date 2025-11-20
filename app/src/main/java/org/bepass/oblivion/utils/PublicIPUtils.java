package org.bepass.oblivion.utils;

import android.os.Handler;
import android.util.Log;

import org.bepass.oblivion.model.IPDetails;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * A utility class for fetching public IP details including the country and its flag.
 */
public class PublicIPUtils {
    private static final String TAG = "PublicIPUtils";
    private static PublicIPUtils instance;
    private static final int TIMEOUT_SECONDS = 5;
    private static final int RETRY_DELAY_MILLIS = 1000;
    private static final int TIMEOUT_MILLIS = 30 * 1000;
    private final ScheduledExecutorService scheduler;
    private static final String URL_COUNTRY_API = "https://api.country.is/";

    /**
     * Private constructor to enforce singleton pattern.
     */
    private PublicIPUtils() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Public method to get the singleton instance.
     *
     * @return The singleton instance of PublicIPUtils.
     */
    public static synchronized PublicIPUtils getInstance() {
        if (instance == null) {
            instance = new PublicIPUtils();
        }
        return instance;
    }

    /**
     * Fetches public IP details including the country and its flag. This method uses a
     * retry mechanism with a specified timeout to ensure reliable fetching of the IP details.
     *
     * @param callback The callback to handle the IP details once fetched.
     */
    public void getIPDetails(IPDetailsCallback callback) {
        attemptFetch(System.currentTimeMillis(), callback);
    }

    private void attemptFetch(long startTime, IPDetailsCallback callback) {
        if (System.currentTimeMillis() - startTime > TIMEOUT_MILLIS) {
            Log.d(TAG, "Timeout reached without successful IP details retrieval");
            new Handler(android.os.Looper.getMainLooper()).post(() -> callback.onDetailsReceived(new IPDetails()));
            return;
        }

        scheduler.execute(() -> {
            try {
                Log.d(TAG, "Attempting to fetch IP details");
                String portString = FileManager.getString("USERSETTING_port");
                if (portString == null || portString.isEmpty()) {
                    throw new IllegalStateException("USERSETTING_port is not set in FileManager");
                }

                int socksPort = Integer.parseInt(portString);
                Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", socksPort));

                OkHttpClient client = new OkHttpClient.Builder()
                        .proxy(proxy)
                        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .build();

                Request request = new Request.Builder()
                        .url(URL_COUNTRY_API)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject jsonData = new JSONObject(response.body().string());
                        IPDetails details = new IPDetails();
                        details.ip = jsonData.optString("ip");
                        details.country = jsonData.optString("country");
                        details.flag = CountryCodeUtils.toCountryFlagEmoji(new CountryCode(details.country));
                        
                        Log.d(TAG, "IP details retrieved successfully");
                        new Handler(android.os.Looper.getMainLooper()).post(() -> callback.onDetailsReceived(details));
                        return; // Success, stop here
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching IP details: " + e.getMessage());
            }

            // If we are here, it means failure. Schedule retry.
            Log.d(TAG, "Scheduling next retry after delay");
            scheduler.schedule(() -> attemptFetch(startTime, callback), RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * Callback interface for receiving IP details.
     */
    public interface IPDetailsCallback {

        /**
         * Called when IP details are received.
         *
         * @param details The IP details.
         */
        void onDetailsReceived(IPDetails details);
    }
}

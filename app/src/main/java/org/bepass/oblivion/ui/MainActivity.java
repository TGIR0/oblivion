package org.bepass.oblivion.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.bepass.oblivion.R;
import org.bepass.oblivion.base.StateAwareBaseActivity;
import org.bepass.oblivion.databinding.ActivityMainBinding;
import org.bepass.oblivion.enums.ConnectionState;
import org.bepass.oblivion.service.OblivionVpnService;
import org.bepass.oblivion.utils.FileManager;
import org.bepass.oblivion.utils.LocaleHandler;
import org.bepass.oblivion.utils.NetworkUtils;
import org.bepass.oblivion.utils.PublicIPUtils;
import org.bepass.oblivion.utils.ThemeHelper;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends StateAwareBaseActivity<ActivityMainBinding> {

    private static final String TAG = "MainActivity";
    private long backPressedTime;
    private Toast backToast;
    private LocaleHandler localeHandler;
    private ActivityResultLauncher<Intent> vpnPermissionLauncher;
    
    // Executor for background tasks to avoid blocking Main Thread
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    /**
     * Static helper to start the Activity
     */
    public static void start(Context context) {
        Intent starter = new Intent(context, MainActivity.class);
        starter.putExtra("origin", context.getClass().getSimpleName());
        starter.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(starter);
    }

    /**
     * Starts the Foreground Service with all necessary configuration extras.
     */
    public static void startVpnService(Context context, Intent intent) {
        // Fetch settings efficiently
        boolean proxyMode = FileManager.getBoolean("USERSETTING_proxymode");
        String license = FileManager.getString("USERSETTING_license");
        int endpointType = FileManager.getInt("USERSETTING_endpoint_type");
        boolean gool = FileManager.getBoolean("USERSETTING_gool");
        String endpoint = FileManager.getString("USERSETTING_endpoint");
        String port = FileManager.getString("USERSETTING_port");
        boolean lan = FileManager.getBoolean("USERSETTING_lan");

        intent.putExtra("USERSETTING_proxymode", proxyMode);
        intent.putExtra("USERSETTING_license", license);
        intent.putExtra("USERSETTING_endpoint_type", endpointType);
        intent.putExtra("USERSETTING_gool", gool);
        intent.putExtra("USERSETTING_endpoint", endpoint);
        intent.putExtra("USERSETTING_port", port);
        intent.putExtra("USERSETTING_lan", lan);

        intent.setAction(OblivionVpnService.FLAG_VPN_START);
        ContextCompat.startForegroundService(context, intent);
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_main;
    }

    @Override
    protected int getStatusBarColor() {
        return R.color.status_bar_color;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Utilities
        localeHandler = new LocaleHandler(this);
        ThemeHelper.getInstance().updateActivityBackground(binding.getRoot());

        // Perform heavy file operations in background
        backgroundExecutor.execute(() -> FileManager.cleanOrMigrateSettings(getApplicationContext()));

        setupUI();
        setupVPNConnection();
        requestNotificationPermission();
        handleBackPress();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }

    private void setupVPNConnection() {
        vpnPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        handleVpnButton(true);
                    } else {
                        Toast.makeText(this, R.string.permission_denied_vpn, Toast.LENGTH_LONG).show();
                        updateConnectButtonState(false);
                    }
                });

        binding.connectionButton.setOnClickListener(v -> {
            boolean isConnected = !lastKnownConnectionState.isDisconnected();
            handleVpnButton(!isConnected);
        });
    }

    /**
     * Updates the connection button state (icon).
     */
    private void updateConnectButtonState(boolean isConnected) {
        if (isConnected) {
            binding.connectionIcon.setImageResource(R.drawable.vpn_on);
        } else {
            binding.connectionIcon.setImageResource(R.drawable.vpn_off);
        }
    }

    private void handleVpnButton(boolean enableVpn) {
        // Initialize FileManager if not already done (failsafe)
        FileManager.initialize(getApplicationContext());

        if (enableVpn) {
            if (lastKnownConnectionState.isDisconnected()) {
                Intent vpnIntent = OblivionVpnService.prepare(this);
                if (vpnIntent != null) {
                    vpnPermissionLauncher.launch(vpnIntent);
                } else {
                    vpnIntent = new Intent(this, OblivionVpnService.class);
                    startVpnService(this, vpnIntent);
                }
                // Basic network monitoring
                NetworkUtils.monitorInternetConnection(lastKnownConnectionState, this);
            } else if (lastKnownConnectionState.isConnecting()) {
                OblivionVpnService.stopVpnService(this);
            }
        } else {
            if (!lastKnownConnectionState.isDisconnected()) {
                OblivionVpnService.stopVpnService(this);
            }
        }
    }

    private void handleBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    if (backToast != null) backToast.cancel();
                    finish();
                } else {
                    if (backToast != null) backToast.cancel();
                    backToast = Toast.makeText(MainActivity.this, R.string.press_back_again, Toast.LENGTH_SHORT);
                    backToast.show();
                }
                backPressedTime = System.currentTimeMillis();
            }
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityResultLauncher<String> pushNotificationPermissionLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), isGranted -> {
                        if (!isGranted) {
                            // Optionally show a rationale dialog here
                            Log.w(TAG, "Notification permission denied");
                        }
                    });
            pushNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void setupUI() {
        binding.floatingActionButton.setOnClickListener(v -> localeHandler.showLanguageSelectionDialog());
        binding.infoIcon.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, InfoActivity.class)));
        binding.bugIcon.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LogActivity.class)));
        binding.settingIcon.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
    }

    @NonNull
    @Override
    public String getKey() {
        return "mainActivity";
    }

    @Override
    protected void onResume() {
        super.onResume();
        observeConnectionStatus();
        // Refresh IP visibility in case connection dropped while paused
        if (lastKnownConnectionState.isDisconnected()) {
            binding.publicIP.setVisibility(View.GONE);
            binding.ipProgressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onConnectionStateChange(ConnectionState state) {
        runOnUiThread(() -> {
            Log.d(TAG, "Connection state changed to: " + state);
            updateUI(state);
        });
    }

    /**
     * Centralized UI update logic based on connection state.
     */
    private void updateUI(ConnectionState state) {
        switch (state) {
            case DISCONNECTED:
                binding.publicIP.setVisibility(View.GONE);
                binding.stateText.setText(R.string.notConnected);
                binding.ipProgressBar.setVisibility(View.GONE);
                binding.connectionButton.setEnabled(true);
                updateConnectButtonState(false);
                break;

            case CONNECTING:
                binding.stateText.setText(R.string.connecting);
                binding.publicIP.setVisibility(View.GONE);
                binding.ipProgressBar.setVisibility(View.VISIBLE);
                binding.connectionButton.setEnabled(true);
                updateConnectButtonState(true);
                break;

            case CONNECTED:
                handleConnectedStateUI();
                break;
        }
    }

    private void handleConnectedStateUI() {
        binding.connectionButton.setEnabled(true);
        updateConnectButtonState(true);
        binding.ipProgressBar.setVisibility(View.GONE);

        // Handle Text Description
        boolean isProxy = FileManager.getBoolean("USERSETTING_proxymode");
        String port = FileManager.getString("USERSETTING_port");
        
        if (isProxy) {
            boolean isLan = FileManager.getBoolean("USERSETTING_lan");
            String ip = "127.0.0.1";
            String modeText = "socks5";
            
            if (isLan) {
                try {
                    ip = NetworkUtils.getLocalIpAddress(this);
                    modeText = "socks5 over LAN";
                } catch (Exception e) {
                    ip = "0.0.0.0";
                }
            }
            
            binding.stateText.setText(String.format(Locale.getDefault(), 
                    "%s\n%s on %s:%s", 
                    getString(R.string.connected), modeText, ip, port));
        } else {
            binding.stateText.setText(R.string.connected);
        }

        // Fetch and Show Public IP
        fetchPublicIP();
    }

    private void fetchPublicIP() {
        // binding.ipProgressBar.setVisibility(View.VISIBLE); // Removed to prevent confusion with connection loading
        PublicIPUtils.getInstance().getIPDetails(details -> runOnUiThread(() -> {
            // Lifecycle check to prevent crash
            if (isFinishing() || isDestroyed()) return;

            binding.ipProgressBar.setVisibility(View.GONE); // Ensure it's gone when done
            if (details.ip != null) {
                String ipInfo = details.ip + " " + (details.flag != null ? details.flag : "");
                binding.publicIP.setText(ipInfo);
                binding.publicIP.setVisibility(View.VISIBLE);
            } else {
                // Optional: Handle error state or leave hidden
                binding.publicIP.setVisibility(View.INVISIBLE);
            }
        }));
    }
}
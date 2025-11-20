package org.bepass.oblivion.ui;

import static org.bepass.oblivion.utils.BatteryOptimizationKt.isBatteryOptimizationEnabled;
import static org.bepass.oblivion.utils.BatteryOptimizationKt.showBatteryOptimizationDialog;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.activity.OnBackPressedCallback;

import org.bepass.oblivion.EndpointsBottomSheet;
import org.bepass.oblivion.enums.ConnectionState;
import org.bepass.oblivion.EditSheet;
import org.bepass.oblivion.utils.FileManager;
import org.bepass.oblivion.R;
import org.bepass.oblivion.interfaces.SheetsCallBack;
import org.bepass.oblivion.base.StateAwareBaseActivity;
import org.bepass.oblivion.databinding.ActivitySettingsBinding;
import org.bepass.oblivion.utils.ThemeHelper;

public class SettingsActivity extends StateAwareBaseActivity<ActivitySettingsBinding> {

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_settings;
    }

    @Override
    protected int getStatusBarColor() {
        return R.color.status_bar_color;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Update background based on current theme
        ThemeHelper.getInstance().updateActivityBackground(binding.getRoot());
        // Set Current Values
        settingBasicValuesFromSPF();

        if (isBatteryOptimizationEnabled(this)) {
            binding.batteryOptimizationLayout.setOnClickListener(view -> showBatteryOptimizationDialog(this));
        } else {
            binding.batteryOptimizationLayout.setVisibility(View.GONE);
            binding.batteryOptLine.setVisibility(View.GONE);
        }

        binding.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        SheetsCallBack sheetsCallBack = this::settingBasicValuesFromSPF;

        binding.endpointType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                FileManager.set("USERSETTING_endpoint_type", position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing or handle the case where no item is selected.
            }
        });
        binding.endpointLayout.setOnClickListener(v -> {
            EndpointsBottomSheet bottomSheet = new EndpointsBottomSheet();
            bottomSheet.setEndpointSelectionListener(content -> {
                Log.d("100","Selected Endpoint: " + content);
                FileManager.set("USERSETTING_endpoint", content);
                binding.endpoint.post(() -> binding.endpoint.setText(content));
            });
            bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
        });

        binding.portLayout.setOnClickListener(v -> (new EditSheet(this, getString(R.string.portTunText), "port", sheetsCallBack)).start());

        binding.splitTunnelLayout.setOnClickListener(v -> startActivity(new Intent(this, SplitTunnelActivity.class)));

        binding.goolLayout.setOnClickListener(v -> binding.gool.setChecked(!binding.gool.isChecked()));
        binding.lanLayout.setOnClickListener(v -> binding.lan.setChecked(!binding.lan.isChecked()));

        binding.lan.setOnCheckedChangeListener((buttonView, isChecked) -> FileManager.set("USERSETTING_lan", isChecked));

        CheckBox.OnCheckedChangeListener goolListener = (buttonView, isChecked) -> FileManager.set("USERSETTING_gool", isChecked);

        CompoundButton.OnCheckedChangeListener proxyModeListener = (buttonView, isChecked) -> FileManager.set("USERSETTING_proxymode", isChecked);

        binding.txtDarkMode.setOnClickListener(view -> binding.checkBoxDarkMode.setChecked(!binding.checkBoxDarkMode.isChecked()));

        // Set the initial state of the checkbox based on the current theme
        binding.checkBoxDarkMode.setChecked(ThemeHelper.getInstance().getCurrentTheme() == ThemeHelper.Theme.DARK);
        // Set up the listener to change the theme when the checkbox is toggled
        binding.checkBoxDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Determine the new theme based on the checkbox state
            ThemeHelper.Theme newTheme = isChecked ? ThemeHelper.Theme.DARK : ThemeHelper.Theme.LIGHT;

            // Use ThemeHelper to apply the new theme
            ThemeHelper.getInstance().select(newTheme);
        });

        binding.gool.setOnCheckedChangeListener(goolListener);
        binding.resetAppLayout.setOnClickListener(v -> resetAppData());
        binding.proxyModeLayout.setOnClickListener(v -> binding.proxyMode.performClick());
        binding.proxyMode.setOnCheckedChangeListener(proxyModeListener);
    }

    private void resetAppData() {
        FileManager.resetToDefault();
        FileManager.cleanOrMigrateSettings(this);
        Intent intent = new Intent(this, MainActivity.class);
        finish();
        startActivity(intent);
    }
    private void settingBasicValuesFromSPF() {
        ArrayAdapter<CharSequence> etadapter = ArrayAdapter.createFromResource(this, R.array.endpointType, R.layout.country_item_layout);
        etadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.endpointType.post(() -> {
            binding.endpointType.setAdapter(etadapter);
            binding.endpointType.setSelection(FileManager.getInt("USERSETTING_endpoint_type"));
        });
        binding.endpoint.setText(FileManager.getString("USERSETTING_endpoint"));
        binding.port.setText(FileManager.getString("USERSETTING_port"));

        binding.lan.setChecked(FileManager.getBoolean("USERSETTING_lan"));
        binding.gool.setChecked(FileManager.getBoolean("USERSETTING_gool"));
        binding.proxyMode.setChecked(FileManager.getBoolean("USERSETTING_proxymode"));
    }

    @Override
    public String getKey() {
        return "settingsActivity";
    }

    @Override
    public void onConnectionStateChange(ConnectionState state) {
    }
}

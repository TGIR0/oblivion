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

        // Remove CheckBox listener and use Dialog
        binding.txtDarkMode.setOnClickListener(view -> showThemeSelectionDialog());
        binding.checkBoxDarkMode.setVisibility(View.GONE); // Hide checkbox, we use the layout click

        binding.gool.setOnCheckedChangeListener(goolListener);
        binding.resetAppLayout.setOnClickListener(v -> resetAppData());
        binding.proxyModeLayout.setOnClickListener(v -> binding.proxyMode.performClick());
        binding.proxyMode.setOnCheckedChangeListener(proxyModeListener);
        
        binding.batteryOptimizationLayout.setOnClickListener(view -> showBatteryOptimizationDialog(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isBatteryOptimizationEnabled(this)) {
            binding.batteryOptimizationLayout.setVisibility(View.VISIBLE);
            binding.batteryOptLine.setVisibility(View.VISIBLE);
        } else {
            binding.batteryOptimizationLayout.setVisibility(View.GONE);
            binding.batteryOptLine.setVisibility(View.GONE);
        }
    }

    private void showThemeSelectionDialog() {
        String[] themes = new String[]{"Light", "Dark", "Pitch Black (OLED)"};
        int checkedItem = 0;
        ThemeHelper.Theme current = ThemeHelper.getInstance().getCurrentTheme();
        if (current == ThemeHelper.Theme.DARK) checkedItem = 1;
        else if (current == ThemeHelper.Theme.OLED) checkedItem = 2;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Select Theme")
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    ThemeHelper.Theme selectedTheme = ThemeHelper.Theme.LIGHT;
                    if (which == 1) selectedTheme = ThemeHelper.Theme.DARK;
                    else if (which == 2) selectedTheme = ThemeHelper.Theme.OLED;

                    if (current != selectedTheme) {
                        ThemeHelper.getInstance().select(selectedTheme);
                        recreate();
                    }
                    dialog.dismiss();
                })
                .show();
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

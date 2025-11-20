package org.bepass.oblivion.utils;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {

    public enum Theme {
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES),
        FOLLOW_SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        UNSPECIFIED(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED),
        AUTO_BATTERY(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);

        private final int nightMode;

        Theme(int nightMode) {
            this.nightMode = nightMode;
        }

        @AppCompatDelegate.NightMode
        public int getNightMode() {
            return nightMode;
        }

        public static Theme fromNightMode(@AppCompatDelegate.NightMode int nightMode) {
            for (Theme theme : values()) {
                if (theme.getNightMode() == nightMode) {
                    return theme;
                }
            }
            return LIGHT; // Default to LIGHT if not found
        }
    }

    private static ThemeHelper instance;
    private Theme currentTheme = Theme.LIGHT;

    private ThemeHelper() {
    }

    public static synchronized ThemeHelper getInstance() {
        if (instance == null) {
            instance = new ThemeHelper();
        }
        return instance;
    }

    public void init() {
        int themeMode = FileManager.getInt(FileManager.KeyHolder.DARK_MODE);
        currentTheme = Theme.fromNightMode(themeMode);
        applyTheme();
    }

    public void applyTheme() {
        AppCompatDelegate.setDefaultNightMode(currentTheme.getNightMode());
    }

    public void select(Theme theme) {
        currentTheme = theme;
        FileManager.set(FileManager.KeyHolder.DARK_MODE, theme.nightMode);
        applyTheme();
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void updateActivityBackground(View view) {
        // Apply Material 3 colorSurface to root background
        Context context = view.getContext();
        int surfaceColor = getThemeColor(context, com.google.android.material.R.attr.colorSurface);
        view.setBackgroundColor(surfaceColor);

        // Configure status bar based on theme
        configureStatusBar(context instanceof Activity ? (Activity) context : null);
    }

    private int getThemeColor(Context context, int attr) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private void configureStatusBar(Activity activity) {
        if (activity == null) return;

        int surfaceColor = getThemeColor(activity, com.google.android.material.R.attr.colorSurface);
        activity.getWindow().setStatusBarColor(surfaceColor);

        // Determine UI visibility flags
        int uiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

        if (currentTheme == Theme.LIGHT) {
            // Set dark icons for light theme
            uiVisibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }

        activity.getWindow().getDecorView().setSystemUiVisibility(uiVisibility);
    }
}
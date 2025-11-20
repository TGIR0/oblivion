package org.bepass.oblivion.utils;

import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {

    public void updateActivityBackground(View root) {
    }

    public enum Theme {
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES),
        FOLLOW_SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        UNSPECIFIED(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED),
        // Note: AUTO_BATTERY is deprecated in API 31+, treated as FOLLOW_SYSTEM usually
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
            return FOLLOW_SYSTEM; // Default safe fallback
        }
    }

    private static volatile ThemeHelper instance;
    private Theme currentTheme = Theme.FOLLOW_SYSTEM;

    private ThemeHelper() {
        // Private constructor for Singleton
    }

    public static ThemeHelper getInstance() {
        if (instance == null) {
            synchronized (ThemeHelper.class) {
                if (instance == null) {
                    instance = new ThemeHelper();
                }
            }
        }
        return instance;
    }

    public void init() {
        int themeMode = FileManager.getInt(FileManager.KeyHolder.DARK_MODE);
        // If key doesn't exist or returns invalid, default to FOLLOW_SYSTEM
        if (themeMode == -1) {
            currentTheme = Theme.FOLLOW_SYSTEM;
        } else {
            currentTheme = Theme.fromNightMode(themeMode);
        }
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

}
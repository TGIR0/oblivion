package org.bepass.oblivion.utils;

import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {

    public void updateActivityBackground(View root) {
    }

    public enum Theme {
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_YES),
        FOLLOW_SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        UNSPECIFIED(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED, AppCompatDelegate.MODE_NIGHT_UNSPECIFIED),
        OLED(3, AppCompatDelegate.MODE_NIGHT_YES);

        private final int storageValue;
        private final int nightMode;

        Theme(int storageValue, int nightMode) {
            this.storageValue = storageValue;
            this.nightMode = nightMode;
        }

        public int getStorageValue() {
            return storageValue;
        }

        @AppCompatDelegate.NightMode
        public int getNightMode() {
            return nightMode;
        }

        public static Theme fromStorageKey(int storageValue) {
            for (Theme theme : values()) {
                if (theme.getStorageValue() == storageValue) {
                    return theme;
                }
            }
            return FOLLOW_SYSTEM;
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
        if (themeMode == -1 && !FileManager.contains(FileManager.KeyHolder.DARK_MODE)) {
            // Check if it was never set, default to FOLLOW_SYSTEM
            currentTheme = Theme.FOLLOW_SYSTEM;
        } else {
            currentTheme = Theme.fromStorageKey(themeMode);
        }
        applyTheme();
    }

    public void applyTheme() {
        AppCompatDelegate.setDefaultNightMode(currentTheme.getNightMode());
    }

    public void select(Theme theme) {
        currentTheme = theme;
        FileManager.set(FileManager.KeyHolder.DARK_MODE, theme.storageValue);
        applyTheme();
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }
    
    public boolean isOled() {
        return currentTheme == Theme.OLED;
    }

}
package org.bepass.oblivion.base;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

import org.bepass.oblivion.utils.FileManager;
import org.bepass.oblivion.utils.ThemeHelper;

/**
 * ApplicationLoader is a custom Application class that extends the Android Application class.
 * It is designed to provide a centralized context reference throughout the application.
 */
public class ApplicationLoader extends Application {

    // Tag for logging purposes

    /**
     * This method is called when the application is starting, before any activity, service, or receiver objects (excluding content providers) have been created.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        FileManager.initialize(this);
        DynamicColors.applyToActivitiesIfAvailable(this);
        ThemeHelper.getInstance().init();
        ThemeHelper.getInstance().applyTheme();
    }
}
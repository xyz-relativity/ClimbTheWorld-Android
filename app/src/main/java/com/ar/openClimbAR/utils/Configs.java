package com.ar.openClimbAR.utils;

import android.app.Activity;
import android.content.SharedPreferences;

/**
 * Created by xyz on 2/1/18.
 */

public class Configs {

    private static final int MAX_SHOW_NODES_DEFAULT = 20;
    private static final String MAX_SHOW_NODES_KEY = "maxNodeShow";
    private static final String DISPLAY_SYSTEM_DEFAULT = Constants.STANDARD_SYSTEM;
    private static final String DISPLAY_SYSTEM_KEY = "gradeSystem";
    private static final boolean KEEP_SCREEN_ON_DEFAULT = true;
    private static final String KEEP_SCREEN_ON_KEY = "keepScreenOn";

    // support variables
    private static final String PREFS_NAME = "generalPrefs";
    private final Activity activity;
    private final SharedPreferences settings;

    public Configs (Activity pActivity) {
        this.activity = pActivity;

        settings = activity.getSharedPreferences(PREFS_NAME, 0);
    }

    public int getMaxShowNodes() {
        return settings.getInt(MAX_SHOW_NODES_KEY, MAX_SHOW_NODES_DEFAULT);
    }

    public void setMaxShowNodes(int maxView) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(MAX_SHOW_NODES_KEY, maxView);

        // Commit the edits!
        editor.commit();
    }

    public String getDisplaySystem() {
        return settings.getString(DISPLAY_SYSTEM_KEY, DISPLAY_SYSTEM_DEFAULT);
    }

    public void setDisplaySystem(String displaySystem) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(DISPLAY_SYSTEM_KEY, displaySystem);

        // Commit the edits!
        editor.commit();
    }

    public boolean getKeepScreenOn() {
        return settings.getBoolean(KEEP_SCREEN_ON_KEY, KEEP_SCREEN_ON_DEFAULT);
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(KEEP_SCREEN_ON_KEY, keepScreenOn);

        // Commit the edits!
        editor.commit();
    }
}

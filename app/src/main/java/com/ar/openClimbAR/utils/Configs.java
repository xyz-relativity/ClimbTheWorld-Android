package com.ar.openClimbAR.utils;

import android.app.Activity;
import android.content.SharedPreferences;

import com.ar.openClimbAR.R;

/**
 * Created by xyz on 2/1/18.
 */

public class Configs {

    public enum ConfigKey {
        maxShowNodes(R.string.visible_route_limit, "visibleRouteLimit", 20),
        usedGradeSystem(R.string.ui_grade_system, "uiGradeSystem", Constants.STANDARD_SYSTEM),
        keepScreenOn(R.string.keep_screen_on, "keepScreenOn", true),
        useMobileData(R.string.use_mobile_data, "useMobileData", true);

        private ConfigKey(int stringID, String storeKeyID, Object defValue) {
            this.stringId = stringID;
            this.storeKeyID = storeKeyID;
            this.defaultVal = defValue;
        }
        public int stringId;
        public String storeKeyID;
        public Object defaultVal;
        public String typeName;
    }

    // support variables
    private static final String PREFS_NAME = "generalPrefs";
    private final Activity activity;
    private final SharedPreferences settings;

    public Configs (Activity pActivity) {
        this.activity = pActivity;

        settings = activity.getSharedPreferences(PREFS_NAME, 0);
    }

    public int getMaxShowNodes() {
        return settings.getInt(ConfigKey.maxShowNodes.storeKeyID, (int)ConfigKey.maxShowNodes.defaultVal);
    }

    public void setMaxShowNodes(int maxView) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(ConfigKey.maxShowNodes.storeKeyID, maxView);

        // Commit the edits!
        editor.commit();
    }

    public String getDisplaySystem() {
        return settings.getString(ConfigKey.usedGradeSystem.storeKeyID, (String)ConfigKey.usedGradeSystem.defaultVal);
    }

    public void setDisplaySystem(String displaySystem) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(ConfigKey.usedGradeSystem.storeKeyID, displaySystem);

        // Commit the edits!
        editor.commit();
    }

    public boolean getKeepScreenOn() {
        return settings.getBoolean(ConfigKey.keepScreenOn.storeKeyID, (boolean)ConfigKey.keepScreenOn.defaultVal);
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(ConfigKey.keepScreenOn.storeKeyID, keepScreenOn);

        // Commit the edits!
        editor.commit();
    }

    public boolean getUseDataConnection() {
        return settings.getBoolean(ConfigKey.useMobileData.storeKeyID, (boolean)ConfigKey.useMobileData.defaultVal);
    }

    public void setUseDataConnection(boolean useDataConnection) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(ConfigKey.useMobileData.storeKeyID, useDataConnection);

        // Commit the edits!
        editor.commit();
    }
}

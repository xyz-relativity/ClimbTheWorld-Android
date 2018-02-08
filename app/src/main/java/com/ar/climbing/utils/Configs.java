package com.ar.climbing.utils;

import android.app.Activity;
import android.content.SharedPreferences;

import com.ar.climbing.R;

/**
 * Created by xyz on 2/1/18.
 */

public class Configs {

    public enum ConfigKey {
        maxNodesShowCountLimit(R.string.visible_route_count_limit, "visibleRoutesCountLimit", 20, 0, 100),
        maxNodesShowDistanceLimit(R.string.visible_route_dist_limit, "visibleRoutesDistanceLimit", 100, 0, 300),
        usedGradeSystem(R.string.ui_grade_system, "uiGradeSystem", Constants.STANDARD_SYSTEM),
        showVirtualHorizon(R.string.show_virtual_horizon, "showVirtualHorizon", true),
        keepScreenOn(R.string.keep_screen_on, "keepScreenOn", true),
        useMobileDataForMap(R.string.use_mobile_data_for_map, "useMobileDataForMap", false),
        useMobileDataForRoutes(R.string.use_mobile_data_for_routes, "useMobileDataForRoutes", false);

        ConfigKey(int stringID, String storeKeyID, Object defValue) {
            this.stringId = stringID;
            this.storeKeyID = storeKeyID;
            this.defaultVal = defValue;
        }

        ConfigKey(int stringID, String storeKeyID, Object defValue, Object min, Object max) {
            this.stringId = stringID;
            this.storeKeyID = storeKeyID;
            this.defaultVal = defValue;
            this.minValue = min;
            this.maxValue = max;
        }

        public int stringId;
        public String storeKeyID;
        public Object defaultVal;
        public Object minValue = null;
        public Object maxValue = null;
    }

    // support variables
    private static final String PREFS_NAME = "generalConfigs";
    private final SharedPreferences settings;

    public Configs (Activity pActivity) {
        settings = pActivity.getSharedPreferences(PREFS_NAME, 0);
    }

    public int getMaxCountVisibleNodes() {
        return settings.getInt(ConfigKey.maxNodesShowCountLimit.storeKeyID, (int)ConfigKey.maxNodesShowCountLimit.defaultVal);
    }

    public void setMaxCountVisibleNodes(int maxView) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(ConfigKey.maxNodesShowCountLimit.storeKeyID, maxView);

        editor.apply();
    }

    public int getMaxDistanceVisibleNodes() {
        return settings.getInt(ConfigKey.maxNodesShowDistanceLimit.storeKeyID, (int)ConfigKey.maxNodesShowDistanceLimit.defaultVal);
    }

    public void setMaxDistanceVisibleNodes(int maxView) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(ConfigKey.maxNodesShowDistanceLimit.storeKeyID, maxView);

        editor.apply();
    }

    public String getDisplaySystem() {
        return settings.getString(ConfigKey.usedGradeSystem.storeKeyID, (String)ConfigKey.usedGradeSystem.defaultVal);
    }

    public void setDisplaySystem(String displaySystem) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(ConfigKey.usedGradeSystem.storeKeyID, displaySystem);

        editor.apply();
    }

    public boolean getShowVirtualHorizon() {
        return settings.getBoolean(ConfigKey.showVirtualHorizon.storeKeyID, (boolean)ConfigKey.showVirtualHorizon.defaultVal);
    }

    public void setShowVirtualHorizon(boolean showVirtualHorizon) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(ConfigKey.showVirtualHorizon.storeKeyID, showVirtualHorizon);

        editor.apply();
    }

    public boolean getKeepScreenOn() {
        return settings.getBoolean(ConfigKey.keepScreenOn.storeKeyID, (boolean)ConfigKey.keepScreenOn.defaultVal);
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(ConfigKey.keepScreenOn.storeKeyID, keepScreenOn);

        editor.apply();
    }

    public boolean getUseMobileDataForMap() {
        return settings.getBoolean(ConfigKey.useMobileDataForMap.storeKeyID, (boolean)ConfigKey.useMobileDataForMap.defaultVal);
    }

    public void setUseMobileDataForMap(boolean useDataConnection) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(ConfigKey.useMobileDataForMap.storeKeyID, useDataConnection);

        editor.apply();
    }

    public boolean getUseMobileDataForRoutes() {
        return settings.getBoolean(ConfigKey.useMobileDataForRoutes.storeKeyID, (boolean)ConfigKey.useMobileDataForMap.defaultVal);
    }

    public void setUseMobileDataForRoutes(boolean useDataConnection) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(ConfigKey.useMobileDataForRoutes.storeKeyID, useDataConnection);

        editor.apply();
    }
}
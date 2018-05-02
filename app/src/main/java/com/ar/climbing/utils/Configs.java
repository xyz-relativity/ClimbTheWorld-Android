package com.ar.climbing.utils;

import android.app.Activity;
import android.content.SharedPreferences;

import com.ar.climbing.R;
import com.ar.climbing.storage.database.GeoNode;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by xyz on 2/1/18.
 */

public class Configs {

    public enum ConfigKey {
        maxNodesShowCountLimit(R.string.visible_route_count_limit, "visibleRoutesCountLimit", 20, 0, 100),
        maxNodesShowDistanceLimit(R.string.visible_route_dist_limit, "visibleRoutesDistanceLimit", 100, 0, 500),
        usedGradeSystem(R.string.ui_grade_system, "uiGradeSystem", Constants.STANDARD_SYSTEM),
        filterMinGrade(R.string.filter_grade_min, "filterMinGrade", 0),
        filterMaxGrade(R.string.filter_grade_max, "filterMaxGrade", 0),
        filterStyles(R.string.climb_style, "filterStyles", GeoNode.ClimbingStyle.values()),
        showVirtualHorizon(R.string.show_virtual_horizon, "showVirtualHorizon", true),
        useArCore(R.string.use_ar_core, "useArCore", false),
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

    public int getInt(ConfigKey key) {
        return settings.getInt(key.storeKeyID, (int)key.defaultVal);
    }

    public void setInt(ConfigKey key, int value) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(key.storeKeyID, value);

        editor.apply();
    }

    public float getFloat(ConfigKey key) {
        return settings.getFloat(key.storeKeyID, (float)key.defaultVal);
    }

    public void setFloat(ConfigKey key, float value) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat(key.storeKeyID, value);

        editor.apply();
    }

    public boolean getBoolean(ConfigKey key) {
        return settings.getBoolean(key.storeKeyID, (boolean)key.defaultVal);
    }

    public void setBoolean(ConfigKey key, boolean value) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(key.storeKeyID, value);

        editor.apply();
    }

    public String getString(ConfigKey key) {
        return settings.getString(key.storeKeyID, (String) key.defaultVal);
    }

    public void setString(ConfigKey key, String value) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(key.storeKeyID, value);

        editor.apply();
    }

    public Set<GeoNode.ClimbingStyle> getClimbingStyles() {
        if (!settings.contains(ConfigKey.filterStyles.storeKeyID)) {
            return new TreeSet<>(Arrays.asList((GeoNode.ClimbingStyle[]) ConfigKey.filterStyles.defaultVal));
        }

        String styles = settings.getString(ConfigKey.filterStyles.storeKeyID, null);

        Set<GeoNode.ClimbingStyle> result = new TreeSet<>();
        if (styles.length() == 0) {
            return result;
        }

        for (String item: styles.split("#")) {
            result.add(GeoNode.ClimbingStyle.valueOf(item));
        }

        return result;
    }

    public void setClimbingStyles(Set<GeoNode.ClimbingStyle> styles) {
        StringBuilder value = new StringBuilder();

        for (GeoNode.ClimbingStyle item: styles) {
            value.append(item.name()).append("#");
        }

        if (value.lastIndexOf("#") > 0) {
            value.deleteCharAt(value.lastIndexOf("#"));
        }

        SharedPreferences.Editor editor = settings.edit();
        editor.putString(ConfigKey.filterStyles.storeKeyID, value.toString());

        editor.apply();
    }

    public boolean isFirstRun() {
        if (settings.contains("isFirstRun")) {
            return false;
        } else {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("isFirstRun", false);

            editor.apply();
            return true;
        }
    }
}

package com.climbtheworld.app.configs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.converter.tools.LengthSystem;
import com.climbtheworld.app.converter.tools.WeightSystem;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.UIConstants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by xyz on 2/1/18.
 */

public class Configs {
	private static final int RND_ID = (int) (Math.random() * 50 + 1);
	private static final Map<String, Object> volatileConfig = new HashMap<>();

	public enum ConfigKey {
		isFirstRun(-1, -1, "isFirstRun", true),
		installedVersion(-1, -1, "installedVersion", "2021.01"),
		callsign(R.string.callsign, R.string.callsign_description, "callsign", "Unnamed" + RND_ID),
		channel(R.string.channel, R.string.channel_description, "channel", "Unnamed" + RND_ID),
		showExperimentalAR(-1, -1, "showExperimentalAR", true),
		showDownloadClimbingData(-1, -1, "showDownloadClimbingData", true),
		showARWarning(-1, -1, "showExperimentalAR", true),
		maxNodesShowCountLimit(R.string.visible_route_count_limit, -1, "visibleRoutesCountLimit", 100, 0, 100),
		maxNodesShowDistanceLimit(R.string.visible_route_dist_limit, -1, "visibleRoutesDistanceLimit", 5000, 0, 5000),
		usedGradeSystem(R.string.ui_grade_system, R.string.ui_grade_system_description, "uiGradeSystem", UIConstants.STANDARD_SYSTEM.name()),
		converterGradeSystem(-1, -1, "converterGradeSystem", UIConstants.STANDARD_SYSTEM.getMainKey()),
		converterGradeValue(-1, -1, "converterGradeValue", 0),
		converterLengthSystem(-1, -1, "converterLengthSystem", LengthSystem.meter.name()),
		converterWeightSystem(-1, -1, "converterWeightSystem", WeightSystem.kiloGram.name()),
		filterString(R.string.filter_by_name, -1, "filterString", "", true),
		filterMinGrade(R.string.min_grade, -1, "filterMinGrade", -1),
		filterMaxGrade(R.string.max_grade, -1, "filterMaxGrade", -1),
		filterStyles(R.string.climb_style, -1, "filterStyles", GeoNode.ClimbingStyle.values()),
		filterNodeTypes(R.string.node_type, -1, "nodeTypes", GeoNode.NodeTypes.values()),
		showVirtualHorizon(R.string.show_virtual_horizon, R.string.show_virtual_horizon_description, "showVirtualHorizon", true),
		useArCore(R.string.use_ar_core, R.string.use_ar_core_description, "useArCore", false),
		keepScreenOn(R.string.keep_screen_on, R.string.keep_screen_on_description, "keepScreenOn", true),
		useMobileDataForMap(R.string.use_mobile_data_for_map, R.string.use_mobile_data_for_map_description, "useMobileDataForMap", true),
		useMobileDataForRoutes(R.string.use_mobile_data_for_routes, R.string.use_mobile_data_for_routes_description, "useMobileDataForRoutes", true),
		virtualCameraDegLat(-1, -1, "virtualCameraDegLat", 45.35384f),
		virtualCameraDegLon(-1, -1, "virtualCameraDegLon", 24.63507f),
		mapViewCompassOrientation(-1, -1, "mapviewRotationMode", 0),
		mapViewTileOrder(-1, -1, "mapViewTileOrder", 0),
		oauthToken(-1, -1, "oauthToken", null),
		oauthVerifier(-1, -1, "oauthVerifier", null);

		ConfigKey(int stringID, int descriptionID, String storeKeyID, Object defValue) {
			this(stringID, descriptionID, storeKeyID, defValue, false);
		}

		ConfigKey(int stringID, int descriptionID, String storeKeyID, Object defValue, boolean isVolatile) {
			this(stringID, descriptionID, storeKeyID, defValue, null, null, isVolatile);
		}

		ConfigKey(int stringID, int descriptionID, String storeKeyID, Object defValue, Object min, Object max) {
			this(stringID, descriptionID, storeKeyID, defValue, min, max, false);
		}

		ConfigKey(int stringID, int descriptionID, String storeKeyID, Object defValue, Object min, Object max, boolean isVolatile) {
			this.stringId = stringID;
			this.descriptionId = descriptionID;
			this.storeKeyID = storeKeyID;
			this.defaultVal = defValue;
			this.minValue = min;
			this.maxValue = max;
			this.isVolatile = isVolatile;
		}

		public int stringId;
		public int descriptionId;
		public String storeKeyID;
		public Object defaultVal;
		public Object minValue = null;
		public Object maxValue = null;
		private boolean isVolatile = false;
	}

	// support variables
	private static final String GENERAL_CONFIGS = "generalConfigs";
	private final SharedPreferences settings;

	public static Configs instance(AppCompatActivity pActivity) {
		return instance(pActivity.getBaseContext());
	}

	public static Configs instance(Context context) {
		return new Configs(context);
	}

	private Configs(Context context) {
		settings = context.getSharedPreferences(GENERAL_CONFIGS, 0);
	}

	public int getInt(ConfigKey key) {
		if (key.isVolatile) {
			return getValue(key, int.class);
		}
		return settings.getInt(key.storeKeyID, (int) key.defaultVal);
	}

	public void setInt(ConfigKey key, int value) {
		if (key.isVolatile) {
			volatileConfig.put(key.storeKeyID, value);
		} else {
			SharedPreferences.Editor editor = settings.edit();
			editor.putInt(key.storeKeyID, value);

			editor.apply();
		}
	}

	public float getFloat(ConfigKey key) {
		if (key.isVolatile) {
			return getValue(key, float.class);
		}
		return settings.getFloat(key.storeKeyID, (float) key.defaultVal);
	}

	public void setFloat(ConfigKey key, float value) {
		if (key.isVolatile) {
			volatileConfig.put(key.storeKeyID, value);
		} else {
			SharedPreferences.Editor editor = settings.edit();
			editor.putFloat(key.storeKeyID, value);

			editor.apply();
		}
	}

	public boolean getBoolean(ConfigKey key) {
		if (key.isVolatile) {
			return getValue(key, boolean.class);
		}
		return settings.getBoolean(key.storeKeyID, (boolean) key.defaultVal);
	}

	public void setBoolean(ConfigKey key, boolean value) {
		if (key.isVolatile) {
			volatileConfig.put(key.storeKeyID, value);
		} else {
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(key.storeKeyID, value);

			editor.apply();
		}
	}

	public String getString(ConfigKey key) {
		if (key.isVolatile) {
			return getValue(key, String.class);
		}
		return settings.getString(key.storeKeyID, (String) key.defaultVal);
	}

	public void setString(ConfigKey key, String value) {
		if (key.isVolatile) {
			volatileConfig.put(key.storeKeyID, value);
		} else {
			SharedPreferences.Editor editor = settings.edit();
			editor.putString(key.storeKeyID, value);

			editor.apply();
		}
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

		for (String item : styles.split("#")) {
			result.add(GeoNode.ClimbingStyle.valueOf(item));
		}

		return result;
	}

	public void setClimbingStyles(Set<GeoNode.ClimbingStyle> styles) {
		StringBuilder value = new StringBuilder();

		for (GeoNode.ClimbingStyle item : styles) {
			value.append(item.name()).append("#");
		}

		if (value.lastIndexOf("#") > 0) {
			value.deleteCharAt(value.lastIndexOf("#"));
		}

		SharedPreferences.Editor editor = settings.edit();
		editor.putString(ConfigKey.filterStyles.storeKeyID, value.toString());

		editor.apply();
	}

	public Set<GeoNode.NodeTypes> getNodeTypes() {
		if (!settings.contains(ConfigKey.filterNodeTypes.storeKeyID)) {
			return new TreeSet<>(Arrays.asList((GeoNode.NodeTypes[]) ConfigKey.filterNodeTypes.defaultVal));
		}

		String styles = settings.getString(ConfigKey.filterNodeTypes.storeKeyID, null);

		Set<GeoNode.NodeTypes> result = new TreeSet<>();
		if (styles.length() == 0) {
			return result;
		}

		for (String item : styles.split("#")) {
			result.add(GeoNode.NodeTypes.valueOf(item));
		}

		return result;
	}

	public void setNodeTypes(Set<GeoNode.NodeTypes> styles) {
		StringBuilder value = new StringBuilder();

		for (GeoNode.NodeTypes item : styles) {
			value.append(item.name()).append("#");
		}

		if (value.lastIndexOf("#") > 0) {
			value.deleteCharAt(value.lastIndexOf("#"));
		}

		SharedPreferences.Editor editor = settings.edit();
		editor.putString(ConfigKey.filterNodeTypes.storeKeyID, value.toString());

		editor.apply();
	}

	private <T> T getValue(ConfigKey key, Class<T> dataType) {
		if (volatileConfig.containsKey(key.storeKeyID)) {
			return dataType.cast(volatileConfig.get(key.storeKeyID));
		} else {
			return dataType.cast(key.defaultVal);
		}
	}
}

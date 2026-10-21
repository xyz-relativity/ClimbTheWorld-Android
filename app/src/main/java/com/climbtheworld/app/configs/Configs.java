package com.climbtheworld.app.configs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.HorizonMode;
import com.climbtheworld.app.converter.tools.LengthSystem;
import com.climbtheworld.app.converter.tools.TemperatureSystem;
import com.climbtheworld.app.converter.tools.WeightSystem;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.constants.UIConstants;

import java.lang.ref.WeakReference;
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
	// support variables
	private static final String GENERAL_CONFIGS = "generalConfigs";
	// The switch that the horizon modes replaced.
	private static final String LEGACY_SHOW_VIRTUAL_HORIZON = "showVirtualHorizon";
	// The horizon modes from before ConfigKey.arElevation, which said whether to use elevation,
	// with the mode that is now the horizon with elevation on.
	private static final String LEGACY_HORIZON_MODE = "arHorizonMode";
	private static final String LEGACY_HORIZON_ELEVATION = "HORIZON_ELEVATION";
	private final SharedPreferences settings;

	private Configs(Context context) {
		settings = context.getSharedPreferences(GENERAL_CONFIGS, 0);
	}

	public static Configs instance(WeakReference<AppCompatActivity> parent) {
		return instance(parent.get());
	}

	public static Configs instance(AppCompatActivity parent) {
		return instance(parent.getBaseContext());
	}

	public static Configs instance(Context context) {
		return new Configs(context);
	}

	public int getInt(ConfigKey key) {
		return getInt(key, "");
	}

	public int getInt(ConfigKey key, String subKey) {
		if (key.isVolatile) {
			return getValue(key, subKey, Integer.class);
		}
		return settings.getInt(key.storeKeyID + subKey, (int) key.defaultVal);
	}

	public void setInt(ConfigKey key, int value) {
		setInt(key, "", value);
	}

	public void setInt(ConfigKey key, String subKey, int value) {
		if (key.isVolatile) {
			volatileConfig.put(key.storeKeyID + subKey, value);
		} else {
			SharedPreferences.Editor editor = settings.edit();
			editor.putInt(key.storeKeyID + subKey, value);

			editor.apply();
		}
	}

	public float getFloat(ConfigKey key) {
		if (key.isVolatile) {
			return getValue(key, Float.class);
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
			return getValue(key, Boolean.class);
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

	public HorizonMode getHorizonMode() {
		String mode = settings.getString(ConfigKey.arHorizonMode.storeKeyID,
				settings.getString(LEGACY_HORIZON_MODE, null));
		if (mode == null) {
			// Before the horizon had modes it was a switch: a horizon switched off stays off.
			mode = settings.getBoolean(LEGACY_SHOW_VIRTUAL_HORIZON, true)
					? (String) ConfigKey.arHorizonMode.defaultVal : HorizonMode.OFF.name();
		} else if (mode.equals(LEGACY_HORIZON_ELEVATION)) {
			mode = HorizonMode.HORIZON.name();
		}
		try {
			return HorizonMode.valueOf(mode);
		} catch (IllegalArgumentException e) {
			// A mode that no longer exists.
			return HorizonMode.valueOf((String) ConfigKey.arHorizonMode.defaultVal);
		}
	}

	public void setHorizonMode(HorizonMode mode) {
		setString(ConfigKey.arHorizonMode, mode.name());
	}

	/**
	 * @return whether the AR view places the POIs at their elevation
	 */
	public boolean isArElevation() {
		// Before elevation had its own switch, the horizon mode said whether to use it. Off used it,
		// unlike what turning off the horizon says, so it now goes without.
		if (!settings.contains(ConfigKey.arElevation.storeKeyID)
				&& settings.contains(LEGACY_HORIZON_MODE)) {
			String mode = settings.getString(LEGACY_HORIZON_MODE, null);
			return !HorizonMode.OFF.name().equals(mode) && !HorizonMode.HORIZON.name().equals(mode);
		}
		return getBoolean(ConfigKey.arElevation);
	}

	public void setArElevation(boolean elevation) {
		setBoolean(ConfigKey.arElevation, elevation);
	}

	public Set<GeoNode.ClimbingStyle> getClimbingStyles() {
		return getEnumSet(ConfigKey.filterStyles, GeoNode.ClimbingStyle.class);
	}

	public void setClimbingStyles(Set<GeoNode.ClimbingStyle> styles) {
		setEnumSet(ConfigKey.filterStyles, styles);
	}

	public Set<OsmEntity.EntityClimbingType> getNodeTypes() {
		return getEnumSet(ConfigKey.filterNodeTypes, OsmEntity.EntityClimbingType.class);
	}

	public void setNodeTypes(Set<OsmEntity.EntityClimbingType> types) {
		setEnumSet(ConfigKey.filterNodeTypes, types);
	}

	/**
	 * The values stored under the key, by their names, or the key's default array when nothing
	 * is stored yet.
	 */
	@SuppressWarnings("unchecked")
	private <T extends Enum<T>> Set<T> getEnumSet(ConfigKey key, Class<T> type) {
		if (!settings.contains(key.storeKeyID)) {
			return new TreeSet<>(Arrays.asList((T[]) key.defaultVal));
		}

		String stored = settings.getString(key.storeKeyID, null);

		Set<T> result = new TreeSet<>();
		if (stored == null || stored.isEmpty()) {
			return result;
		}

		for (String item : stored.split("#")) {
			try {
				result.add(Enum.valueOf(type, item));
			} catch (IllegalArgumentException removedValue) {
				// Stored by an older version, like the "unknown" type that was a choice once.
			}
		}

		return result;
	}

	private void setEnumSet(ConfigKey key, Set<? extends Enum<?>> values) {
		StringBuilder value = new StringBuilder();

		for (Enum<?> item : values) {
			value.append(item.name()).append("#");
		}

		if (value.lastIndexOf("#") > 0) {
			value.deleteCharAt(value.lastIndexOf("#"));
		}

		SharedPreferences.Editor editor = settings.edit();
		editor.putString(key.storeKeyID, value.toString());

		editor.apply();
	}

	private <T> T getValue(ConfigKey key, Class<T> dataType) {
		return getValue(key, "", dataType);
	}

	/**
	 * @param dataType a boxed type, such as Boolean.class, as a primitive one does not cast
	 */
	private <T> T getValue(ConfigKey key, String subKey, Class<T> dataType) {
		String keyString = key.storeKeyID + subKey;
		if (volatileConfig.containsKey(keyString)) {
			return dataType.cast(volatileConfig.get(keyString));
		} else {
			return dataType.cast(key.defaultVal);
		}
	}

	public enum ConfigKey {
		isFirstRun(-1, -1, "isFirstRun", true),
		installedVersion(-1, -1, "installedVersion", "2021.01"),

		intercomHandsFreeSwitch(R.string.hands_free_switch, R.string.hands_free_switch_description,
				"handsFreeSwitch", false),
		// WebRTC VAD mode, from 0 (NORMAL) to 3 (VERY_AGGRESSIVE).
		intercomHandsFreeNoiseFilter(R.string.walkie_talkie_noise_filter, -1,
				"intercomHandsFreeNoiseFilter", 3, 0, 3),
		intercomCallsign(R.string.callsign, R.string.callsign_description, "intercomCallsign",
				"Guest" + RND_ID),
		intercomChannel(R.string.channel, R.string.channel_description, "intercomChannel",
				"Welcome"),

		showHardwareLimitation(-1, -1, "showHardwareLimitation", true),
		showExperimentalAR(-1, -1, "showExperimentalAR", true),
		showDownloadClimbingData(-1, -1, "showDownloadClimbingData", true),
		showARWarning(-1, -1, "showExperimentalAR", true),
		// Until the user has read that the A.R. view and placement run on Google Play Services for
		// AR.
		showArCoreNotice(-1, -1, "showArCoreNotice", true),
		// How many times in a row the A.R. view was refused ARCore, see ArViewController.
		arCoreViewRefusals(-1, -1, "arCoreViewRefusals", 0),
		// Whether the A.R. view was refused ARCore since the app started, see ArViewController.
		arCoreViewRefusedThisRun(-1, -1, "arCoreViewRefusedThisRun", false, true),
		maxNodesShowCountLimit(R.string.visible_route_count_limit, -1, "visibleRoutesCountLimit",
				100, 0, 500),
		maxNodesShowDistanceLimit(R.string.visible_route_dist_limit, -1,
				"visibleRoutesDistanceLimit", 2500, 0, 5000),
		usedGradeSystem(R.string.ui_grade_system, R.string.ui_grade_system_description,
				"uiGradeSystem", UIConstants.STANDARD_SYSTEM.name()),

		converterGradeSystem(-1, -1, "converterGradeSystem",
				UIConstants.STANDARD_SYSTEM.getMainKey()),
		// Grades are rows of the grade table: new keys when its rows change, so a grade saved
		// as an old row isn't read as another grade.
		converterGradeValue(-1, -1, "converterGradeRow", 0),
		converterLengthSystem(-1, -1, "converterLengthSystem", LengthSystem.meter.name()),
		converterWeightSystem(-1, -1, "converterWeightSystem", WeightSystem.kiloGram.name()),
		converterTemperatureSystem(-1, -1, "converterTemperatureSystem",
				TemperatureSystem.kelvin.name()),

		filterString(R.string.filter_by_name, -1, "filterString", "", true),
		filterMinGrade(R.string.min_grade, -1, "filterMinGradeRow", -1),
		filterMaxGrade(R.string.max_grade, -1, "filterMaxGradeRow", -1),
		filterStyles(R.string.climb_style, -1, "filterStyles", GeoNode.ClimbingStyle.values()),
		filterNodeTypes(R.string.node_type, -1, "filterNodeTypes",
				OsmEntity.EntityClimbingType.features()),

		// A new key, so the old modes keep telling the elevation they had, see isArElevation.
		arHorizonMode(R.string.ar_horizon, R.string.ar_horizon_description, "arHorizonView",
				HorizonMode.TERRAIN.name()),
		arElevation(R.string.ar_elevation, R.string.ar_elevation_description, "arElevation", true),
		arClimbingHulls(R.string.ar_climbing_hulls, R.string.ar_climbing_hulls_description,
				"arClimbingHulls", true),
		// Whether the A.R. view runs on ARCore where it can, in the non-FOSS build. Refusing ARCore
		// twice in a row turns it off, see ArViewController.
		useArCore(R.string.use_ar_core, R.string.use_ar_core_description, "useArCore", true),
		keepScreenOn(R.string.keep_screen_on, R.string.keep_screen_on_description, "keepScreenOn",
				true),
		useMobileDataForMap(R.string.use_mobile_data_for_map,
				R.string.use_mobile_data_for_map_description, "useMobileDataForMap", true),
		useMobileDataForRoutes(R.string.use_mobile_data_for_routes,
				R.string.use_mobile_data_for_routes_description, "useMobileDataForRoutes", false),
		virtualCameraDegLat(-1, -1, "virtualCameraDegLat", 45.35384f),
		virtualCameraDegLon(-1, -1, "virtualCameraDegLon", 24.63507f),
		mapStyleId(-1, -1, "mapStyleId", "openfreemap-liberty"),

		// OpenStreetMap's OAuth 2 access token, and the PKCE secrets of a login under way.
		oauth2Token(-1, -1, "oauth2Token", null),
		oauth2Verifier(-1, -1, "oauth2Verifier", null),
		oauth2State(-1, -1, "oauth2State", null),
		compassBazelAngle(-1, -1, "compassBazelAngle", 0f);

		public final int stringId;
		public final int descriptionId;
		public final String storeKeyID;
		public final Object defaultVal;
		public final Object minValue;
		public final Object maxValue;
		private final boolean isVolatile;

		ConfigKey(int stringID, int descriptionID, String storeKeyID, Object defValue) {
			this(stringID, descriptionID, storeKeyID, defValue, false);
		}

		ConfigKey(int stringID, int descriptionID, String storeKeyID, Object defValue,
		          boolean isVolatile) {
			this(stringID, descriptionID, storeKeyID, defValue, null, null, isVolatile);
		}

		ConfigKey(int stringID, int descriptionID, String storeKeyID, Object defValue, Object min,
		          Object max) {
			this(stringID, descriptionID, storeKeyID, defValue, min, max, false);
		}

		ConfigKey(int stringID, int descriptionID, String storeKeyID, Object defValue, Object min,
		          Object max, boolean isVolatile) {
			this.stringId = stringID;
			this.descriptionId = descriptionID;
			this.storeKeyID = storeKeyID;
			this.defaultVal = defValue;
			this.minValue = min;
			this.maxValue = max;
			this.isVolatile = isVolatile;
		}
	}
}

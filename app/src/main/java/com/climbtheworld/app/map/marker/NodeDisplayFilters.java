package com.climbtheworld.app.map.marker;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.Globals;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

public class NodeDisplayFilters {
	/**
	 * Shows on the activity's filter button whether any filter is on: in colour while one is,
	 * washed out while none is, as the GPS centering button.
	 */
	public static void showFilterState(AppCompatActivity activity, Configs configs) {
		FloatingActionButton button = activity.findViewById(R.id.filterButton);
		if (hasFilters(configs)) {
			button.clearColorFilter();
		} else {
			button.setColorFilter(MapLibreMapWidget.BUTTON_OFF_TINT);
		}
	}

	private static final int styleCount = GeoNode.ClimbingStyle.values().length;

	public static boolean matchFilters(Configs configs, GeoNode poi) {
		if (!matchGradientFilter(configs, poi)) {
			return false;
		}

		if (!matchStyleFilter(configs, poi)) {
			return false;
		}

		if (!matchStringFilter(configs, poi)) {
			return false;
		}

		return matchTypeFilter(configs, poi);
	}

	/**
	 * The routes a crag's or area's pin counts while any filter is on, see
	 * ClimbingRouteCounter.forDatabase: those the map and the A.R. view show unfiltered.
	 *
	 * @return null while no filter is on
	 */
	public static Predicate<GeoNode> routeFilter(Configs configs) {
		return hasFilters(configs) ? route -> matchFilters(configs, route) : null;
	}

	public static boolean hasFilters(Configs configs) {
		boolean stringFilter = !Globals.isEmptyOrWhitespace(configs.getString(Configs.ConfigKey.filterString));
		boolean minGradeFilter = configs.getInt(Configs.ConfigKey.filterMinGrade) != -1;
		boolean maxGradeFilter = configs.getInt(Configs.ConfigKey.filterMaxGrade) != -1;
		boolean stylesFilter = configs.getClimbingStyles().size() != GeoNode.ClimbingStyle.values().length;
		boolean typesFilter = configs.getNodeTypes().size() != OsmEntity.EntityClimbingType.features().length;

		return stringFilter || minGradeFilter || maxGradeFilter || stylesFilter || typesFilter;
	}

	private static boolean matchGradientFilter(Configs configs, GeoNode poi) {
		// Compared as the grades the user's system shows, which can cover several rows: a route
		// shown as the maximum grade can be on a row above the one the grade starts on.
		GradeSystem system = GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem));
		int nodeGrade = system.positionOf(poi.getLevelId(ClimbingTags.KEY_GRADE_TAG));
		int minGrade = system.positionOf(configs.getInt(Configs.ConfigKey.filterMinGrade));
		int maxGrade = system.positionOf(configs.getInt(Configs.ConfigKey.filterMaxGrade));

		if (minGrade == -1 && maxGrade == -1) return true; //no filters
		if (nodeGrade < 0) return false; // this node does not have a grade tag so display it.

		if (minGrade == -1) {
			return nodeGrade <= maxGrade;
		}

		if (maxGrade == -1) {
			return nodeGrade >= minGrade;
		}

		return nodeGrade >= minGrade && nodeGrade <= maxGrade;
	}

	private static boolean matchStyleFilter(Configs configs, GeoNode poi) {
		Set<GeoNode.ClimbingStyle> styles = configs.getClimbingStyles();

		if (styles.size() == styleCount) {
			return true; //no filters
		}

		return !Collections.disjoint(poi.getClimbingStyles(), styles);
	}

	private static boolean matchTypeFilter(Configs configs, GeoNode poi) {
		Set<OsmEntity.EntityClimbingType> types = configs.getNodeTypes();

		return types.contains(poi.getNodeType());
	}

	private static boolean matchStringFilter(Configs configs, GeoNode poi) {
		String text = configs.getString(Configs.ConfigKey.filterString);

		if (Globals.isEmptyOrWhitespace(text)) {
			return true;
		}

		return poi.getName().toLowerCase().contains(text);
	}
}

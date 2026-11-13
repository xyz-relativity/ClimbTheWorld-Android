package com.climbtheworld.app.map.marker;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.Globals;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

public class NodeDisplayFilters {
	/**
	 * Shows on the activity's filter button whether any filter is on: a check mark badge on its
	 * bottom corner while one is, as the compass button's status badge, see filter_button.xml.
	 */
	public static void showFilterState(AppCompatActivity activity, Configs configs) {
		activity.findViewById(R.id.filterStateBadge)
				.setVisibility(hasFilters(configs) ? View.VISIBLE : View.GONE);
	}

	private static final int styleCount = GeoNode.ClimbingStyle.values().length;

	/**
	 * The display filters as they were set when read, see read. Reading them takes the stored
	 * settings apart, so what tests many POIs, like a view on each frame, reads them once and
	 * tests the POIs against this. Two are equal when they filter alike, so a result kept for
	 * one holds for the other.
	 *
	 * @param gradeSystem the system the user's grades show in, which the grade filter compares in
	 * @param minGrade    the row of the lowest grade to show, -1 for any
	 * @param maxGrade    the row of the highest grade to show, -1 for any
	 * @param styles      the climbing styles to show, all of them for any
	 * @param types       the kinds of climbing feature to show
	 * @param text        what a name has to hold, empty or null for any
	 */
	public record Settings(GradeSystem gradeSystem, int minGrade, int maxGrade,
	                       Set<GeoNode.ClimbingStyle> styles,
	                       Set<OsmEntity.EntityClimbingType> types, String text) {

		/** @return whether the POI passes the filters, so shows as any other */
		public boolean matches(GeoNode poi) {
			return matchesGrade(poi) && matchesStyle(poi) && matchesText(poi)
					&& types.contains(poi.getNodeType());
		}

		/** @return whether any filter is on, so some POIs may not pass */
		public boolean hasFilters() {
			return !Globals.isEmptyOrWhitespace(text) || minGrade != -1 || maxGrade != -1
					|| styles.size() != styleCount
					|| types.size() != OsmEntity.EntityClimbingType.features().length;
		}

		private boolean matchesGrade(GeoNode poi) {
			// Compared as the grades the user's system shows, which can cover several rows: a
			// route shown as the maximum grade can be on a row above the one the grade starts on.
			int min = gradeSystem.positionOf(minGrade);
			int max = gradeSystem.positionOf(maxGrade);
			if (min == -1 && max == -1) {
				return true; //no filters
			}

			int nodeGrade = gradeSystem.positionOf(poi.getLevelId(ClimbingTags.KEY_GRADE_TAG));
			if (nodeGrade < 0) {
				return false; // a node without a grade tag passes no grade filter.
			}
			return (min == -1 || nodeGrade >= min) && (max == -1 || nodeGrade <= max);
		}

		private boolean matchesStyle(GeoNode poi) {
			if (styles.size() == styleCount) {
				return true; //no filters
			}
			return !Collections.disjoint(poi.getClimbingStyles(), styles);
		}

		private boolean matchesText(GeoNode poi) {
			if (Globals.isEmptyOrWhitespace(text)) {
				return true;
			}
			return poi.getName().toLowerCase().contains(text);
		}
	}

	/** Reads the display filters as they are set now. */
	public static Settings read(Configs configs) {
		return new Settings(
				GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)),
				configs.getInt(Configs.ConfigKey.filterMinGrade),
				configs.getInt(Configs.ConfigKey.filterMaxGrade),
				configs.getClimbingStyles(), configs.getNodeTypes(),
				configs.getString(Configs.ConfigKey.filterString));
	}

	/**
	 * Tests one POI against the display filters as they are set now. To test many, read the
	 * filters once and test against them, see Settings.
	 */
	public static boolean matchFilters(Configs configs, GeoNode poi) {
		return read(configs).matches(poi);
	}

	/**
	 * The routes a crag's or area's pin counts while any filter is on, see
	 * ClimbingRouteCounter.forDatabase: those the map and the A.R. view show unfiltered. They
	 * are tested against the filters as they are set now.
	 *
	 * @return null while no filter is on
	 */
	public static Predicate<GeoNode> routeFilter(Configs configs) {
		Settings filters = read(configs);
		return filters.hasFilters() ? filters::matches : null;
	}

	public static boolean hasFilters(Configs configs) {
		return read(configs).hasFilters();
	}
}

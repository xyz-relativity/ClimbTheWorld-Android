package com.climbtheworld.app.map.marker;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;

import java.util.Collections;
import java.util.Set;

public class NodeDisplayFilters {
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

	public static boolean hasFilters(Configs configs) {
		int minGrade = configs.getInt(Configs.ConfigKey.filterMinGrade);
		int maxGrade = configs.getInt(Configs.ConfigKey.filterMaxGrade);
		boolean stringFilter = !Globals.isEmptyOrWhitespace(configs.getString(Configs.ConfigKey.filterString));
		Set<GeoNode.ClimbingStyle> styles = configs.getClimbingStyles();
		Set<GeoNode.NodeTypes> types = configs.getNodeTypes();

		return stringFilter || minGrade != -1 || maxGrade != -1 || styles.size() != GeoNode.ClimbingStyle.values().length || types.size() != GeoNode.NodeTypes.values().length;
	}

	private static boolean matchGradientFilter(Configs configs, GeoNode poi) {
		int nodeGrade = poi.getLevelId(GeoNode.KEY_GRADE_TAG);
		int minGrade = configs.getInt(Configs.ConfigKey.filterMinGrade);
		int maxGrade = configs.getInt(Configs.ConfigKey.filterMaxGrade);

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
		Set<GeoNode.NodeTypes> types = configs.getNodeTypes();

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

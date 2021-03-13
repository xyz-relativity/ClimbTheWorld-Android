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
		boolean stringFilter = !Globals.isEmptyOrWhitespace(configs.getString(Configs.ConfigKey.filterString));
		boolean minGradeFilter = configs.getInt(Configs.ConfigKey.filterMinGrade) != -1;
		boolean maxGradeFilter = configs.getInt(Configs.ConfigKey.filterMaxGrade) != -1;
		boolean stylesFilter = configs.getClimbingStyles().size() != GeoNode.ClimbingStyle.values().length;
		boolean typesFilter = configs.getNodeTypes().size() != GeoNode.NodeTypes.values().length;

		return stringFilter || minGradeFilter || maxGradeFilter || stylesFilter || typesFilter;
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

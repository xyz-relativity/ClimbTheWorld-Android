package com.climbtheworld.app.storage;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.storage.database.GeoNode;

import java.util.Collections;
import java.util.Set;

public class NodeDisplayFilters {

	public static boolean passFilter(Configs configs, GeoNode poi) {
		if (!doGradingFilter(configs, poi)) {
			return false;
		}

		if (!doStyleFilter(configs, poi)) {
			return false;
		}

		return doTypeFilter(configs, poi);
	}

	public static boolean hasFilters(Configs configs) {
		int minGrade = configs.getInt(Configs.ConfigKey.filterMinGrade);
		int maxGrade = configs.getInt(Configs.ConfigKey.filterMaxGrade);
		Set<GeoNode.ClimbingStyle> styles = configs.getClimbingStyles();
		Set<GeoNode.NodeTypes> types = configs.getNodeTypes();

		return minGrade != -1 || maxGrade != -1 || styles.size() != GeoNode.ClimbingStyle.values().length || types.size() != GeoNode.NodeTypes.values().length;
	}

	private static boolean doGradingFilter(Configs configs, GeoNode poi) {
		int nodeGrade = poi.getLevelId(GeoNode.KEY_GRADE_TAG);
		if (nodeGrade < 0) return true; // this node does not have a grade tag so display it.
		int minGrade = configs.getInt(Configs.ConfigKey.filterMinGrade);
		int maxGrade = configs.getInt(Configs.ConfigKey.filterMaxGrade);

		if (minGrade == -1 && maxGrade == -1) return true; //no filters

		if (minGrade == -1) {
			return nodeGrade <= maxGrade;
		}

		if (maxGrade == -1) {
			return nodeGrade >= minGrade;
		}

		return nodeGrade >= minGrade && nodeGrade <= maxGrade;
	}

	private static boolean doStyleFilter(Configs configs, GeoNode poi) {
		Set<GeoNode.ClimbingStyle> styles = configs.getClimbingStyles();

		if (poi.getClimbingStyles().size() == 0) {
			return true;
		}

		return !Collections.disjoint(poi.getClimbingStyles(), styles);
	}

	private static boolean doTypeFilter(Configs configs, GeoNode poi) {
		Set<GeoNode.NodeTypes> types = configs.getNodeTypes();

		return types.contains(poi.getNodeType());
	}
}

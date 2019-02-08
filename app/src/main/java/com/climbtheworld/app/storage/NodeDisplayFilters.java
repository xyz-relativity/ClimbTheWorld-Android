package com.climbtheworld.app.storage;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NodeDisplayFilters {

    public static Map<Long, GeoNode> filterNodes(Map<Long, GeoNode> nodes) {
        Map<Long, GeoNode> result = new HashMap<>();

        for (GeoNode node : nodes.values()) {
            if (canAdd(node)) {
                result.put(node.getID(), node);
            }
        }

        return result;
    }

    public static boolean canAdd(GeoNode poi) {
        if (!doGradingFilter(poi)) {
            return false;
        }

        if (!doStyleFilter(poi)) {
            return false;
        }

        return doTypeFilter(poi);
    }

    private static boolean doGradingFilter(GeoNode poi) {
        int minGrade = Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade);
        int maxGrade = Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade);

        if (minGrade == 0 && maxGrade == 0) {
            return true;
        }
        if (minGrade != 0 && poi.getLevelId(GeoNode.KEY_GRADE_TAG) < minGrade) {
            return false;
        }

        return maxGrade == 0 || poi.getLevelId(GeoNode.KEY_GRADE_TAG) <= maxGrade;
    }

    private static boolean doStyleFilter(GeoNode poi) {
        Set<GeoNode.ClimbingStyle> styles = Globals.globalConfigs.getClimbingStyles();

        if (poi.getClimbingStyles().size() == 0) {
            return true;
        }

        return !Collections.disjoint(poi.getClimbingStyles(), styles);
    }

    private static boolean doTypeFilter(GeoNode poi) {
        Set<GeoNode.NodeTypes> styles = Globals.globalConfigs.getNodeTypes();

        return styles.contains(poi.getNodeType());
    }
}

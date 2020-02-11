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
            if (passFilter(node)) {
                result.put(node.getID(), node);
            }
        }

        return result;
    }

    public static boolean passFilter(GeoNode poi) {
        if (!doGradingFilter(poi)) {
            return false;
        }

        if (!doStyleFilter(poi)) {
            return false;
        }

        return doTypeFilter(poi);
    }

    private static boolean doGradingFilter(GeoNode poi) {
        int nodeGrade = poi.getLevelId(GeoNode.KEY_GRADE_TAG);
        if (nodeGrade < 0) return true; // this node does not have a grade tag so display it.
        int minGrade = Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade);
        int maxGrade = Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade);

        if (minGrade == -1 && maxGrade == -1) return true; //no filters

        if (minGrade == -1) {
            return nodeGrade <= maxGrade;
        }

        if (maxGrade == -1) {
            return nodeGrade >= minGrade;
        }

        return nodeGrade >= minGrade && nodeGrade <= maxGrade;
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

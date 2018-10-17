package com.climbtheworld.app.osm;


/*
[out:json][timeout:60];node["sport"="climbing"]["leisure"!="sports_centre"]["climbing"!="route_bottom"]["climbing"!="route_top"]["climbing"!="route"]["climbing"!="crag"][!"shop"]["leisure"!="pitch"]["tower:type"!="climbing"]({{bbox}});out body meta;
*/

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;

import org.osmdroid.util.BoundingBox;

import java.util.Locale;

public class OsmUtils {
    //Get all climbing routes: [out:json][timeout:60];node["sport"="climbing"][~"^climbing$"~"route_bottom"]({{bbox}});out body meta;
    //Get all climbing routes that were not done by me: [out:json][timeout:60];node["sport"="climbing"][~"^climbing$"~"route_bottom"]({{bbox}})->.newnodes; (.newnodes; - node.newnodes(user:xyz32);)->.newnodes; .newnodes out body meta;
    //Get all states: [out:json][timeout:60];node["place"="state"]({{bbox}});out body meta;
    //Get all countries: [out:json][timeout:60];node["place"="country"]({{bbox}});out body meta;

    private static final String QUERY_HEADER = "[out:json][timeout:" + Constants.HTTP_TIMEOUT_SECONDS + "]";
    private static final String QUERY_BBOX = "(%f,%f,%f,%f)";
    private static final String QUERY_META = "out body meta";
    private static final String QUERY_POI_IDs = "node(id:%s)";
    private static final String QUERY_COUNTRY_AREA = "area[type=boundary][\"ISO3166-1\"=\"%s\"]->.searchArea";

    private static final String QUERY_ROUTE_BOTTOM = "node[\"sport\"=\"climbing\"][\"climbing\"=\"route_bottom\"]";
    private static final String QUERY_CLIMBING_CRAG = "node[\"sport\"=\"climbing\"][\"climbing\"=\"crag\"]";
    private static final String QUERY_CLIMBING_GYM = "node[\"sport\"=\"climbing\"][\"leisure\"=\"sports_centre\"]";
    private static final String QUERY_CLIMBING_ARTIFICIAL_WALL = "node[\"sport\"=\"climbing\"][\"tower:type\"=\"climbing\"]";

    public static String buildBBoxQueryForType(GeoNode.NodeTypes type, BoundingBox bBox, String countryIso) {
        StringBuilder queryString = new StringBuilder();
        queryString.append(QUERY_HEADER).append(";");
        String boundingBox;

        if (countryIso.isEmpty()) {
            boundingBox = String.format(Locale.getDefault(), QUERY_BBOX,
                    bBox.getLatSouth(),
                    bBox.getLonWest(),
                    bBox.getLatNorth(),
                    bBox.getLonEast());
        } else {
            String area = String.format(Locale.getDefault(), QUERY_COUNTRY_AREA, countryIso);
            queryString.append(area).append(";");
            boundingBox = String.format(Locale.getDefault(), QUERY_BBOX,
                    bBox.getLatSouth(),
                    bBox.getLonWest(),
                    bBox.getLatNorth(),
                    bBox.getLonEast()) + "(area.searchArea)";
        }

        switch (type) {
            case route:
                queryString.append(QUERY_ROUTE_BOTTOM).append(boundingBox).append(";").append(QUERY_META).append(";");
                break;
            case crag:
                queryString.append(QUERY_CLIMBING_CRAG).append(boundingBox).append(";").append(QUERY_META).append(";");
                break;
            case artificial:
                queryString.append("(")
                        .append(QUERY_CLIMBING_GYM).append(boundingBox).append(";")
                        .append(QUERY_CLIMBING_ARTIFICIAL_WALL).append(boundingBox).append(";")
                        .append(");").append(QUERY_META).append(";");
                break;
        }

        return queryString.toString();
    }

    public static String buildPoiQueryForType(String nodeIds) {
        return QUERY_HEADER + ";" +
                String.format(Locale.getDefault(), QUERY_POI_IDs, nodeIds) + ";" +
                QUERY_META + ";";
    }

}

package com.climbtheworld.app.map;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;

import org.osmdroid.util.BoundingBox;

import java.util.Locale;

/*
cleanup queries
[out:json][timeout:60];node["sport"="climbing"]["leisure"!="sports_centre"]["climbing"!="route_bottom"]["climbing"!="route_top"]["climbing"!="route"]["climbing"!="crag"][!"shop"]["leisure"!="pitch"]["tower:type"!="climbing"]({{bbox}});out body meta;
*/


//Get all climbing routes: [out:json][timeout:60];node["sport"="climbing"][~"^climbing$"~"route_bottom"]({{bbox}});out body meta;
//Get all climbing routes that were not done by me: [out:json][timeout:60];node["sport"="climbing"][~"^climbing$"~"route_bottom"]({{bbox}})->.newnodes; (.newnodes; - node.newnodes(user:xyz32);)->.newnodes; .newnodes out body meta;
//Get all states: [out:json][timeout:60];node["place"="state"]({{bbox}});out body meta;
//Get all countries: [out:json][timeout:60];node["place"="country"]({{bbox}});out body meta;

public class OsmUtils {

	/*
	Search with relations (wip):
	[out:json][timeout:60];
	area[type=boundary]["ISO3166-1"="CA"]->.searchArea;
	(
	  node["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](area.searchArea);
      way["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](area.searchArea);
      >;
	);

	out body meta;
	rel(bn);
	out body meta;
	rel(br);
	out body meta;
	 */

    /*
    [out:json][timeout:240];
    area[type=boundary]["ISO3166-1"="CA"]->.searchArea;

    (
      node["sport"="climbing"]["climbing"="route_bottom"](area.searchArea);
      node["sport"="climbing"]["climbing"="crag"](area.searchArea);
      node["sport"="climbing"][~"^climbing:.*&"~".*"](area.searchArea);
      node["sport"="climbing"]["leisure"="sports_centre"](area.searchArea);
      node["sport"="climbing"]["tower:type"="climbing"](area.searchArea);
    );
    out body meta;
     */

    /*
    [out:json][timeout:240];
    area[type=boundary]["ISO3166-1"="CA"]->.searchArea;
    (
      node["sport"~"climbing"](area.searchArea);
      way["sport"~"climbing"](area.searchArea);
      relation["sport"~"climbing"](area.searchArea);
    );
    out center body meta;
     */


    /* to implement search.
    node({{bbox}})["sport"~"climbing"]["name"~"(?i).*Le bloc de.*"];
  foreach(
    is_in->.a;
    area.a[name][boundary=administrative][admin_level="2"] -> .a;
    convert node ::=::,
              ::id = id(),
              is_in=a.set(t["ISO3166-1"]);

    out center body meta;
  );
     */

	/*
	[out:json];
(
  node(49576799);
  node(351974698);
);
is_in;

out;
	 */

	private static final String ALL_NODES_QUERY = "node[\"sport\"~\"\\W*(climbing)\\W*\"]%s"; //->.climbingNodes;" +
//            "(" +
//                    "node.climbingNodes[\"climbing\"=\"route_bottom\"];" +
//                    "node.climbingNodes[\"climbing\"=\"crag\"];" +
//                    "node.climbingNodes[~\"^climbing:.*&\"~\".*\"];" +
//                    "node.climbingNodes[\"leisure\"=\"sports_centre\"];" +
//                    "node.climbingNodes[\"tower:type\"=\"climbing\"];" +
//                    ")";

	private static final String QUERY_BBOX = "(%f,%f,%f,%f)";
	private static final String QUERY_COUNTRY_AREA = "area[type=boundary][\"ISO3166-1\"=\"%s\"]->.searchArea";

	private static final String QUERY_HEADER = "[out:json][timeout:" + Constants.HTTP_TIMEOUT_SECONDS + "]";
	private static final String QUERY_META = "out center body meta";
	private static final String QUERY_POI_IDs = "node(id:%s)";

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

	public static String buildBBoxQuery(BoundingBox bBox) {
		String boundingBox = String.format(Locale.getDefault(), QUERY_BBOX,
				bBox.getLatSouth(),
				bBox.getLonWest(),
				bBox.getLatNorth(),
				bBox.getLonEast());

		return QUERY_HEADER + ";" +
				String.format(Locale.getDefault(), ALL_NODES_QUERY, boundingBox) + ";" + QUERY_META + ";";
	}

	// [out:json][timeout:240];area[type=boundary]["ISO3166-1"="CA"]->.searchArea;node["sport"~"\W*(climbing)\W*"](area.searchArea);out body meta;
	public static String buildCountryQuery(String countryIso) {
		String queryString = QUERY_HEADER + ";" + String.format(Locale.getDefault(), QUERY_COUNTRY_AREA, countryIso) + ";" +
				String.format(Locale.getDefault(), ALL_NODES_QUERY, "(area.searchArea)") + ";" + QUERY_META + ";";
		return String.format(Locale.getDefault(), queryString, countryIso);
	}

	public static String buildPoiQueryForType(String nodeIds) {
		return QUERY_HEADER + ";" +
				String.format(Locale.getDefault(), QUERY_POI_IDs, nodeIds) + ";" +
				QUERY_META + ";";
	}

}

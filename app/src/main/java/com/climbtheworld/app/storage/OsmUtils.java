package com.climbtheworld.app.storage;

import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.utils.constants.Constants;

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

	/* Search with relations (wip):
	[out:json][timeout:60];
	area[type=boundary]["ISO3166-1"="CA"]->.searchArea;
	(
	  node["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](area.searchArea);
      way["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](area.searchArea);
      >;
      rel["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](area.searchArea);
	);

	out body;
	rel["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](bn);
	out body;
	rel["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](br);
	out body;
	 */

	/* simplified:
	[out:json][timeout:60];
	area[type=boundary]["ISO3166-1"="CA"]->.searchArea;
	(
	  node["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](area.searchArea);
	  way["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](area.searchArea);
	  >;
	  rel["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](area.searchArea);
	);

	out body;
	 */

	/* origin:
	[out:json][timeout:60];
	area[type=boundary]["ISO3166-1"="CA"]->.searchArea;
	(
	  node["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](area.searchArea);
      way["sport"~"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$"](area.searchArea);
      >;
	);

	out body;
	rel(bn);
	out body;
	rel(br);
	out body;
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

	/** Climbing among the ;-separated values of sport, as {@link ClimbingTags#hasClimbingSport}. */
	private static final String SPORT_CLIMBING = "[\"sport\"~\"(^|;) *climbing *(;|$)\"]";
	/** The climbing values of a node that is a climbing feature. */
	private static final String CLIMBING_NODE = "[\"climbing\"~\"^(" + ClimbingTags.VALUE_ROUTE_BOTTOM
			+ "|" + ClimbingTags.VALUE_ROUTE_TOP + "|" + ClimbingTags.VALUE_ROUTE + "|"
			+ ClimbingTags.VALUE_CRAG + "|" + ClimbingTags.VALUE_BOULDER + ")$\"]";
	/** The climbing values of a way that is a climbing feature. */
	private static final String CLIMBING_WAY = "[\"climbing\"~\"^(" + ClimbingTags.VALUE_ROUTE + "|"
			+ ClimbingTags.VALUE_BOULDER + ")$\"]";
	private static final String FACILITY_LEISURE = "[\"" + ClimbingTags.KEY_LEISURE + "\"~\"^("
			+ ClimbingTags.VALUE_SPORTS_CENTRE + "|" + ClimbingTags.VALUE_SPORTS_HALL + "|"
			+ ClimbingTags.VALUE_PITCH + ")$\"]";
	private static final String FACILITY_TOWER = "[\"" + ClimbingTags.KEY_TOWER_TYPE + "\"=\""
			+ ClimbingTags.VALUE_CLIMBING + "\"]";
	private static final String FACILITY_BUILDING = "[\"" + ClimbingTags.KEY_BUILDING + "\"][\""
			+ ClimbingTags.KEY_BUILDING + "\"!=\"" + ClimbingTags.VALUE_NO + "\"]";
	private static final String NAMED = "[\"" + ClimbingTags.KEY_NAME + "\"]";

	private static final String QUERY_COUNTRY_AREA = "area[type=boundary][\"ISO3166-1\"=\"%s\"]->.searchArea";

	private static final String QUERY_HEADER = "[out:json][timeout:" + Constants.OVERPASS_QUERY_TIMEOUT_SECONDS + "]";
	private static final String QUERY_META = "out body center";

	/**
	 * Asks for the climbing features, see
	 * {@link com.climbtheworld.app.storage.database.ClimbingFeature}, and what they are drawn
	 * with, in the way Overpass answers fastest. What a statement costs Overpass:
	 * <ul>
	 * <li>A statement in the area first resolves its tag filters over the whole planet through
	 * the global tag index, exact values first, then bare keys, then value patterns, whatever
	 * their order in the statement. Once the first filter has matched a million elements,
	 * Overpass gives up on the index and loads everything in the area instead, which takes
	 * minutes for a country. So each statement in the area has a single filter, a value
	 * pattern that few elements match worldwide, and never a bare key such as name, which most
	 * elements have and which would be read for them all.</li>
	 * <li>Each statement in the area also reads the whole area, slow for a big country, so
	 * there are four: the nodes and the ways with a climbing tag, which the wiki lets go
	 * without the sport, and the nodes and the ways with the climbing sport, for the
	 * facilities.</li>
	 * <li>A statement on a named set checks its tags in the local index of the set's tiles,
	 * which printing the set reads anyway, so that is where names and facility tags are
	 * checked.</li>
	 * <li>Relations are found from their members, then their ancestors, as areas nest. Looking
	 * for them in the area would need the geometry of every candidate.</li>
	 * </ul>
	 * The importer keeps the climbing features of the answer, so the sets may hold a bit more.
	 */
	public static String buildCountryQuery(String countryIso) {
		return QUERY_HEADER + ";" + String.format(Locale.ROOT, QUERY_COUNTRY_AREA, countryIso) + ";"
				+ "node" + CLIMBING_NODE + "(area.searchArea)->.climbingNodes;"
				+ "node" + SPORT_CLIMBING + "(area.searchArea)->.sportNodes;"
				+ "way" + CLIMBING_WAY + "(area.searchArea)->.routeWays;"
				+ "way" + SPORT_CLIMBING + "(area.searchArea)->.sportWays;"
				+ "(rel(bn.climbingNodes);rel(bw.routeWays);)->.parents;"
				+ "(.parents;.parents <<;)->.ancestors;"
				+ "(rel.ancestors[\"" + ClimbingTags.KEY_CLIMBING + "\"];rel.ancestors" + SPORT_CLIMBING
				+ ";)->.relations;"
				// Only named nodes, as the others get no pin, and the nodes of the routes. A facility
				// is a pin at its center, so the nodes of its outline stay on the server.
				+ "(node.climbingNodes" + NAMED + ";"
				+ "node.sportNodes" + FACILITY_LEISURE + NAMED + ";"
				+ "node.sportNodes" + FACILITY_TOWER + NAMED + ";"
				+ ".routeWays;.routeWays >;"
				+ "way.sportWays" + FACILITY_LEISURE + ";"
				+ "way.sportWays" + FACILITY_TOWER + ";"
				+ "way.sportWays" + FACILITY_BUILDING + ";"
				+ ")->.climbing;"
				+ ".climbing " + QUERY_META + ";"
				+ ".relations " + QUERY_META + ";";
	}
}

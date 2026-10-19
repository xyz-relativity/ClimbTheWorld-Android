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
	private static final String GRADED = "[~\"^" + ClimbingTags.KEY_GRADE_PREFIX + "\"~\".\"]";
	private static final String GRADED_BOULDER = "[\"climbing\"=\"" + ClimbingTags.VALUE_BOULDER
			+ "\"]" + GRADED;
	private static final String FACILITY_LEISURE = SPORT_CLIMBING + "[\"leisure\"~\"^("
			+ ClimbingTags.VALUE_SPORTS_CENTRE + "|" + ClimbingTags.VALUE_SPORTS_HALL + "|"
			+ ClimbingTags.VALUE_PITCH + ")$\"]";
	private static final String FACILITY_TOWER = SPORT_CLIMBING + "[\"tower:type\"=\""
			+ ClimbingTags.VALUE_CLIMBING + "\"]";
	private static final String FACILITY_BUILDING = SPORT_CLIMBING + "[\"building\"][\"building\"!=\""
			+ ClimbingTags.VALUE_NO + "\"]";

	// The shapes of ClimbingFeature, one statement each, as Overpass has no OR inside a statement.
	// The importer still classifies what comes back.
	/** Nodes, only named ones, as the others get no pin. */
	private static final String[] NODE_FILTERS = {
			"[\"climbing\"~\"^(" + ClimbingTags.VALUE_ROUTE_BOTTOM + "|" + ClimbingTags.VALUE_ROUTE_TOP
					+ "|" + ClimbingTags.VALUE_ROUTE + "|" + ClimbingTags.VALUE_CRAG + ")$\"]",
			GRADED_BOULDER,
			FACILITY_LEISURE,
			FACILITY_TOWER,
	};
	/** Ways, which come with their nodes. */
	private static final String[] WAY_FILTERS = {
			"[\"climbing\"=\"" + ClimbingTags.VALUE_ROUTE + "\"]",
			GRADED_BOULDER,
			FACILITY_LEISURE,
			FACILITY_TOWER,
			FACILITY_BUILDING,
	};
	/** Facilities mapped as multipolygons, which come with their ways and nodes. */
	private static final String[] RELATION_FILTERS = {
			FACILITY_LEISURE,
			FACILITY_TOWER,
			FACILITY_BUILDING,
	};
	/** Relations found from their members at any depth: crags, areas and multi-pitch routes. */
	private static final String[] PARENT_FILTERS = {
			"[\"type\"=\"" + ClimbingTags.VALUE_SITE + "\"][\"climbing\"~\"^(" + ClimbingTags.VALUE_CRAG
					+ "|" + ClimbingTags.VALUE_AREA + ")$\"]",
			"[\"climbing\"=\"" + ClimbingTags.VALUE_ROUTE + "\"]",
	};

	private static final String QUERY_COUNTRY_AREA = "area[type=boundary][\"ISO3166-1\"=\"%s\"]->.searchArea";

	private static final String QUERY_HEADER = "[out:json][timeout:" + Constants.OVERPASS_QUERY_TIMEOUT_SECONDS + "]";
	private static final String QUERY_META = "out body center";

	public static String buildCountryQuery(String countryIso) {
		StringBuilder query = new StringBuilder(QUERY_HEADER).append(';')
				.append(String.format(Locale.ROOT, QUERY_COUNTRY_AREA, countryIso)).append(';');
		query.append('(');
		appendStatements(query, "way", WAY_FILTERS, "(area.searchArea)");
		appendStatements(query, "rel", RELATION_FILTERS, "(area.searchArea)");
		query.append(")->.collections;");
		query.append('(');
		appendStatements(query, "node", NODE_FILTERS, "[\"name\"](area.searchArea)");
		query.append(".collections;.collections >;");
		query.append(")->.climbing;");
		query.append(".climbing ").append(QUERY_META).append(';');
		// Parents of the nodes and ways, then theirs all the way up, as areas nest. Starting from
		// the relations spares looking up the ways of every node.
		query.append("(rel(bn.climbing);rel(bw.climbing);)->.parents;");
		query.append("(.parents;.parents <<;)->.ancestors;");
		query.append('(');
		appendStatements(query, "rel.ancestors", PARENT_FILTERS, "");
		query.append(");").append(QUERY_META).append(';');
		return query.toString();
	}

	private static void appendStatements(StringBuilder query, String elements, String[] filters,
	                                     String suffix) {
		for (String filter : filters) {
			query.append(elements).append(filter).append(suffix).append(';');
		}
	}
}

package com.climbtheworld.app.storage;

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

	private static final String CLIMBING_FILTERS_QUERY = "[\"sport\"~\"^climbing$|^climbing[:space:;]|[:space:;]climbing[:space:;]|[:space:;]climbing$\"]"; //->.climbingNodes;" +
//            "(" +
//                    "node.climbingNodes[\"climbing\"=\"route_bottom\"];" +
//                    "node.climbingNodes[\"climbing\"=\"crag\"];" +
//                    "node.climbingNodes[~\"^climbing:.*&\"~\".*\"];" +
//                    "node.climbingNodes[\"leisure\"=\"sports_centre\"];" +
//                    "node.climbingNodes[\"tower:type\"=\"climbing\"];" +
//                    ")";
	private static final String QUERY_COUNTRY_AREA = "area[type=boundary][\"ISO3166-1\"=\"%s\"]->.searchArea";

	private static final String QUERY_HEADER = "[out:json][timeout:" + Constants.HTTP_TIMEOUT_SECONDS + "]";
	private static final String QUERY_META = "out body";

	// [out:json][timeout:240];area[type=boundary]["ISO3166-1"="CA"]->.searchArea;node["sport"~"\W*(climbing)\W*"](area.searchArea);out body meta;
	public static String buildCountryQuery(String countryIso) {
		String queryString = QUERY_HEADER + ";" + String.format(Locale.getDefault(), QUERY_COUNTRY_AREA, countryIso) + ";"
				+ "("
				+ "node" + CLIMBING_FILTERS_QUERY + "(area.searchArea)" + ";"
				+ "way" + CLIMBING_FILTERS_QUERY + "(area.searchArea)" + ";"
				+ ">" + ";"
				+ ")" + ";"
				+ QUERY_META + ";"
				+ "rel" + CLIMBING_FILTERS_QUERY + "(bn)" + ";"
				+ QUERY_META + ";"
				+ "rel" + CLIMBING_FILTERS_QUERY + "(br)" + ";"
				+ QUERY_META + ";";
		return String.format(Locale.getDefault(), queryString, countryIso);
	}

	public static String buildPoiQueryForType(String nodeIds) {
		return QUERY_HEADER + ";" +
				String.format(Locale.getDefault(), "node(id:%s)", nodeIds) + ";" +
				QUERY_META + ";";
	}

}

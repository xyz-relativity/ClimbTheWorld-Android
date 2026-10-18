package com.climbtheworld.app.storage.database;

public interface ClimbingTags {
	String KEY_SEPARATOR = ":";
	String KEY_ID = "id";
	String KEY_TYPE = "type";
	String KEY_SPORT = "sport";
	String KEY_NAME = "name";
	String KEY_NODES = "nodes";
	String KEY_REF = "ref";
	String KEY_ROLE = "role";
	String KEY_MEMBERS = "members";
	String KEY_TAGS = "tags";
	String KEY_LAT = "lat";
	String KEY_LON = "lon";
	String KEY_ELEVATION = "ele";
	String KEY_CLIMBING = "climbing";
	String VALUE_ROUTE_TOP = "route_top";
	String VALUE_ROUTE_BOTTOM = "route_bottom";
	String KEY_ROUTES = KEY_CLIMBING + KEY_SEPARATOR + "routes";
	String KEY_BOLTS = KEY_CLIMBING + KEY_SEPARATOR + "bolts";
	String KEY_PITCHES = KEY_CLIMBING + KEY_SEPARATOR + "pitches";
	String KEY_LENGTH = KEY_CLIMBING + KEY_SEPARATOR + "length";
	String KEY_MAX_LENGTH = KEY_LENGTH + KEY_SEPARATOR + "max";
	String KEY_MIN_LENGTH = KEY_LENGTH + KEY_SEPARATOR + "min";
	String KEY_LEISURE = "leisure";
	String KEY_TOWER = "tower";
	String KEY_TOWER_TYPE = KEY_TOWER + KEY_SEPARATOR + "type";
	String KEY_MAN_MADE = "man_made";
	String KEY_DESCRIPTION = "description";
	String KEY_CONTACT = "contact";
	String KEY_WEBSITE = "website";
	String KEY_CONTACT_WEBSITE = KEY_CONTACT + KEY_SEPARATOR + KEY_WEBSITE;
	String KEY_PHONE = "phone";
	String KEY_CONTACT_PHONE = KEY_CONTACT + KEY_SEPARATOR + KEY_PHONE;
	String KEY_ADDRESS = "addr";
	String KEY_STREETNO = "housenumber";
	String KEY_ADDR_STREETNO = KEY_ADDRESS + KEY_SEPARATOR + KEY_STREETNO;
	String KEY_STREET = "street";
	String KEY_ADDR_STREET = KEY_ADDRESS + KEY_SEPARATOR + KEY_STREET;
	String KEY_UNIT = "unit";
	String KEY_ADDR_UNIT = KEY_ADDRESS + KEY_SEPARATOR + KEY_UNIT;
	String KEY_CITY = "city";
	String KEY_ADDR_CITY = KEY_ADDRESS + KEY_SEPARATOR + KEY_CITY;
	String KEY_PROVINCE = "province";
	String KEY_ADDR_PROVINCE = KEY_ADDRESS + KEY_SEPARATOR + KEY_PROVINCE;
	String KEY_POSTCODE = "postcode";
	String KEY_ADDR_POSTCODE = KEY_ADDRESS + KEY_SEPARATOR + KEY_POSTCODE;
	String KEY_GRADE = "grade";
	String KEY_GRADE_TAG = KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR + "%s";
	String KEY_MIN = "min";
	String KEY_GRADE_TAG_MIN = KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR + "%s" + KEY_SEPARATOR + KEY_MIN;
	String KEY_MAX = "max";
	String KEY_GRADE_TAG_MAX = KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR + "%s" + KEY_SEPARATOR + KEY_MAX;
	String KEY_MEAN = "mean";
	String KEY_GRADE_TAG_MEAN = KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR + "%s" + KEY_SEPARATOR + KEY_MEAN;
	String KEY_BOLTED = "bolted";

	String UNKNOWN_GRADE_STRING = "?";

	String VALUE_CLIMBING = "climbing";
	String KEY_SITE = "site";
	String VALUE_SITE = "site";
	String VALUE_ROUTE = "route";
	String VALUE_CRAG = "crag";
	String VALUE_AREA = "area";
	/** leisure=* of a large climbing gym. */
	String VALUE_SPORTS_CENTRE = "sports_centre";
	/** leisure=* of a smaller climbing gym. */
	String VALUE_SPORTS_HALL = "sports_hall";
	/** leisure=* of a simple climbing wall or tower, one smaller than a gym. */
	String VALUE_PITCH = "pitch";
	/**
	 * Matches sport=climbing in the text of a tags object, also when climbing is one of several
	 * ;-separated sports.
	 */
	String SPORT_CLIMBING_PATTERN = "\"sport\":\"(?:[^\"]*;)?\\s*climbing\\s*(?:;[^\"]*)?\"";
	/**
	 * Holds at the start of the text of a tags object for a route: climbing=route, route_bottom or
	 * route_top, or a graded climbing=boulder, which the OSM wiki's Climbing page makes a
	 * free-standing rock with a single problem. A boulder without a grade is mostly a rock whose
	 * problems are mapped apart. Test it at the start only: each of its lookaheads scans the whole
	 * text, so testing it at every position takes cubic time on long tags.
	 */
	String ROUTE_PATTERN = "(?:(?=.*\"climbing\":\"route(?:_[^\"]*)?\")|(?=.*\"climbing\":\"boulder\")(?=.*\"climbing:grade:))";
	/**
	 * Matches an artificial climbing facility in the text of a tags object: a gym
	 * (leisure=sports_centre or sports_hall), a wall (leisure=pitch), a tower (tower:type=climbing)
	 * or a building.
	 */
	String ARTIFICIAL_PATTERN = "\"(?:leisure\":\"(?:sports_centre|sports_hall|pitch)\"|tower:type\":\"climbing\"|building\":\"[^\"]+)";

	/** @return whether a leisure value is one of a climbing facility. */
	static boolean isClimbingFacility(String leisureValue) {
		return VALUE_SPORTS_CENTRE.equals(leisureValue) || VALUE_SPORTS_HALL.equals(leisureValue)
				|| VALUE_PITCH.equals(leisureValue);
	}

	/** @return whether climbing is among the ;-separated values of a sport tag. */
	static boolean hasClimbingSport(String sportValue) {
		if (sportValue == null) {
			return false;
		}
		for (String sport : sportValue.split(";")) {
			if (sport.trim().equals(VALUE_CLIMBING)) {
				return true;
			}
		}
		return false;
	}
}

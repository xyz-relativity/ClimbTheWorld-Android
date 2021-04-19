package com.climbtheworld.app.storage.database;

public interface ClimbingTags {
	String KEY_SEPARATOR = ":";
	String KEY_ID = "id";
	String KEY_TYPE = "type";
	String KEY_SPORT = "sport";
	String KEY_NAME = "name";
	String KEY_TAGS = "tags";
	String KEY_LAT = "lat";
	String KEY_LON = "lon";
	String KEY_ELEVATION = "ele";
	String KEY_CLIMBING = "climbing";
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

	int CLEAN_STATE = 0;
	int TO_DELETE_STATE = 1;
	int TO_UPDATE_STATE = 2;
}

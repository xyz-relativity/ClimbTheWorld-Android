package com.climbtheworld.app.storage.database;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the ele tag, which OSM wants as plain metres but which mappers also write as "1,234 m",
 * "1234,5", "~1200" or "1234;1250".
 */
public final class ElevationTag {
	// The first number, with its separators: the grouping is worked out once it is found.
	private static final Pattern NUMBER = Pattern.compile("[-−]?\\d+(?:[.,]\\d+)*");
	private static final Pattern PLAIN_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

	private ElevationTag() {
		//hide constructor.
	}

	/**
	 * The first number of an ele value, with its sign: -28 for "-28", 1234 for "1,234 m",
	 * 1234.5 for "1234,5", 1200 for "~1200"; 0, which the app takes as no elevation, when there
	 * is none. A single comma is a decimal one, as typed in many languages, unless three digits
	 * follow it, as in "1,234": no one maps a crag to the millimetre.
	 */
	public static double parse(String ele) {
		if (ele == null) {
			return 0;
		}
		Matcher number = NUMBER.matcher(ele);
		if (!number.find()) {
			return 0;
		}
		String text = number.group().replace('−', '-');
		int comma = text.indexOf(',');
		if (comma >= 0 && text.indexOf('.') < 0 && comma == text.lastIndexOf(',')
				&& text.length() - comma - 1 != 3) {
			text = text.replace(',', '.');
		} else {
			text = text.replace(",", "");
		}
		// A second dot ends the number: "1.234.5" reads as 1.234.
		Matcher plain = PLAIN_NUMBER.matcher(text);
		return plain.lookingAt() ? Double.parseDouble(plain.group()) : 0;
	}
}

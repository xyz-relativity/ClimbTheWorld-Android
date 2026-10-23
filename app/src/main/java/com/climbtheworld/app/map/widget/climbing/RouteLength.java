package com.climbtheworld.app.map.widget.climbing;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The length of a route, from its climbing:length tag, in meters.
 */
public final class RouteLength {
	private RouteLength() {
	}

	/**
	 * Parses a route length in meters, such as "25" or "25 m". Other units are ignored.
	 *
	 * @return NaN when the value gives no length in meters
	 */
	public static double parse(String value) {
		if (value == null) {
			return Double.NaN;
		}
		String length = value.trim();
		if (length.endsWith("m")) {
			length = length.substring(0, length.length() - 1).trim();
		}
		try {
			double result = Double.parseDouble(length);
			return result > 0 && !Double.isInfinite(result) ? result : Double.NaN;
		} catch (NumberFormatException ignored) {
			return Double.NaN;
		}
	}

	/**
	 * The length as the route pins show it, such as "25 m" or "12.5 m".
	 *
	 * @param value the climbing:length tag, null or empty when the route has none
	 * @return null when the value gives no length in meters
	 */
	public static String label(String value, Locale locale) {
		double length = parse(value);
		if (Double.isNaN(length)) {
			return null;
		}
		DecimalFormat format = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale));
		format.setRoundingMode(RoundingMode.HALF_UP);
		return format.format(length) + " m";
	}
}

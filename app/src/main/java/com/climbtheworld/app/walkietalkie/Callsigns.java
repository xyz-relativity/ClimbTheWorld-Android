package com.climbtheworld.app.walkietalkie;

/**
 * Which callsign the walkie-talkie goes by. Every version of the app drops an INSTANCE message
 * without a callsign as malformed, so a phone that sent one would be seen by no one. A callsign
 * of only spaces is taken by them, but shows as no name at all. Neither is ever sent.
 */
public final class Callsigns {
	// Four digits give 9000 names, so two phones nearby rarely share one, all of the same width,
	// and Guest plus four is 9 characters, under the 10 the edit field keeps.
	private static final String DEFAULT_PREFIX = "Guest";
	private static final int DEFAULT_MIN_NUMBER = 1000;
	private static final int DEFAULT_NUMBER_COUNT = 9000;

	private Callsigns() {
	}

	/**
	 * The callsign of a phone whose user has not picked one, from Guest1000 to Guest9999. It is
	 * not translated, so the others read the same name whatever their language.
	 *
	 * @param random a number from 0 included to 1 excluded, as Math.random() gives
	 */
	public static String defaultCallsign(double random) {
		return DEFAULT_PREFIX + (DEFAULT_MIN_NUMBER + (int) (random * DEFAULT_NUMBER_COUNT));
	}

	/**
	 * The callsign, or the fallback when it has nothing to show. A callsign with anything to show
	 * is kept exactly as it is, spaces included, as it is the name the others know the user by.
	 */
	public static String orFallback(String callsign, String fallback) {
		return isBlank(callsign) ? fallback : callsign;
	}

	/**
	 * Whether the callsign shows nothing: null, empty, or only spaces, of any kind.
	 */
	public static boolean isBlank(String callsign) {
		if (callsign == null) {
			return true;
		}
		return callsign.codePoints().allMatch(
				c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
	}
}

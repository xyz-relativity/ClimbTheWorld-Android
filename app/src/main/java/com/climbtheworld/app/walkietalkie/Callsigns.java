package com.climbtheworld.app.walkietalkie;

/**
 * Which callsign the walkie-talkie goes by. Every version of the app drops an INSTANCE message
 * without a callsign as malformed, so a phone that sent one would be seen by no one. A callsign
 * of only spaces is taken by them, but shows as no name at all. Neither is ever sent.
 */
public final class Callsigns {
	private Callsigns() {
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

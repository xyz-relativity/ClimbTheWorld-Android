package com.climbtheworld.app.utils;

/** Reads the numbers users type, whose keyboards in some languages give a decimal comma. */
public final class DecimalInput {
	private DecimalInput() {
		//hide constructor.
	}

	/**
	 * The number typed, with a dot or a comma as the decimal separator. There are no thousands
	 * separators: 1,000 is one. A text with more than one separator is not a number.
	 *
	 * @throws NumberFormatException when the text is not a number
	 */
	public static double parse(CharSequence text) {
		return Double.parseDouble(text.toString().trim().replace(',', '.'));
	}
}

package com.climbtheworld.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class DecimalInputTest {
	@Test
	public void takesADecimalDotOrComma() {
		assertEquals(1.5, DecimalInput.parse("1.5"), 0);
		assertEquals(1.5, DecimalInput.parse("1,5"), 0);
		assertEquals(-1.5, DecimalInput.parse("-1,5"), 0);
		assertEquals(0.5, DecimalInput.parse(",5"), 0);
		assertEquals(5, DecimalInput.parse("5,"), 0);
		assertEquals(1.5, DecimalInput.parse(" 1,5 "), 0);
		assertEquals(1000, DecimalInput.parse("1e3"), 0);
		assertEquals(1500, DecimalInput.parse("1,5e3"), 0);
	}

	@Test
	public void takesAnyCommaAsDecimal() {
		// No thousands separators: a comma is always the decimal one.
		assertEquals(1, DecimalInput.parse("1,000"), 0);
	}

	@Test
	public void rejectsSeveralSeparatorsAndNonNumbers() {
		for (String text : new String[] {"1.000,5", "1,000.5", "1,2,3", "", " ", "-", ",", "abc"}) {
			try {
				DecimalInput.parse(text);
				fail("\"" + text + "\" parsed");
			} catch (NumberFormatException expected) {
				// Not a number.
			}
		}
	}

	@Test
	public void readsWhatParseDoubleReadsTheSame() {
		for (String text : new String[] {"0", "-0", "1", "-1", "1.5", "-1.5", ".5", "5.", "+2",
				" 12.25 ", "1e3", "-1.5E-3", "123456789.123", "NaN", "Infinity", "-Infinity"}) {
			assertEquals(text, Double.parseDouble(text), DecimalInput.parse(text), 0);
		}
	}
}

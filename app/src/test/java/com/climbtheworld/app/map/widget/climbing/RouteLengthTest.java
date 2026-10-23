package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

public class RouteLengthTest {
	@Test
	public void lengthIsInMeters() {
		assertEquals(25, RouteLength.parse("25"), 0);
		assertEquals(25, RouteLength.parse(" 25 m "), 0);
		assertEquals(12.5, RouteLength.parse("12.5m"), 0);
	}

	@Test
	public void noLengthFromOtherUnitsOrNoValue() {
		assertTrue(Double.isNaN(RouteLength.parse("40 ft")));
		assertTrue(Double.isNaN(RouteLength.parse("0")));
		assertTrue(Double.isNaN(RouteLength.parse("")));
		assertTrue(Double.isNaN(RouteLength.parse(null)));
	}

	@Test
	public void labelDropsNeedlessDecimals() {
		assertEquals("25 m", RouteLength.label("25", Locale.US));
		assertEquals("25 m", RouteLength.label("25.0 m", Locale.US));
		assertEquals("12.5 m", RouteLength.label("12.5", Locale.US));
		assertEquals("12.3 m", RouteLength.label("12.25", Locale.US));
	}

	@Test
	public void labelUsesTheLocaleSeparator() {
		assertEquals("12,5 m", RouteLength.label("12.5", Locale.FRANCE));
	}

	@Test
	public void noLabelWithoutALength() {
		assertNull(RouteLength.label("", Locale.US));
		assertNull(RouteLength.label(null, Locale.US));
		assertNull(RouteLength.label("40 ft", Locale.US));
	}
}

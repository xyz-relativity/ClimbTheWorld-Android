package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ElevationTagTest {
	@Test
	public void plainMetresAreReadAsTheyAre() {
		assertEquals(1234, ElevationTag.parse("1234"), 0);
		assertEquals(1234.5, ElevationTag.parse("1234.5"), 0);
		assertEquals(0, ElevationTag.parse("0"), 0);
	}

	@Test
	public void belowSeaLevelKeepsItsSign() {
		assertEquals(-28, ElevationTag.parse("-28"), 0);
		assertEquals(-12.5, ElevationTag.parse("-12.5 m"), 0);
		assertEquals(-430, ElevationTag.parse("−430"), 0);
	}

	@Test
	public void aUnitOrAMarkAroundTheNumberIsLeftOut() {
		assertEquals(1234, ElevationTag.parse("1234 m"), 0);
		assertEquals(1234, ElevationTag.parse("1234m"), 0);
		assertEquals(1200, ElevationTag.parse("~1200"), 0);
		assertEquals(1200, ElevationTag.parse("ca. 1200"), 0);
		// Feet are not converted: OSM wants metres, and the number is the best there is.
		assertEquals(4000, ElevationTag.parse("4000ft"), 0);
	}

	@Test
	public void ofSeveralValuesTheFirstIsRead() {
		assertEquals(1234, ElevationTag.parse("1234;1250"), 0);
		assertEquals(1200, ElevationTag.parse("1200-1300"), 0);
	}

	@Test
	public void aCommaGroupsThousandsOrMarksTheDecimals() {
		assertEquals(1234, ElevationTag.parse("1,234"), 0);
		assertEquals(1234, ElevationTag.parse("1,234 m"), 0);
		assertEquals(1234.5, ElevationTag.parse("1,234.5"), 0);
		assertEquals(1234567, ElevationTag.parse("1,234,567"), 0);
		assertEquals(1234.5, ElevationTag.parse("1234,5"), 0);
		assertEquals(812.25, ElevationTag.parse("812,25 m"), 0);
		assertEquals(-3.5, ElevationTag.parse("-3,5"), 0);
	}

	@Test
	public void noNumberIsNoElevation() {
		assertEquals(0, ElevationTag.parse(""), 0);
		assertEquals(0, ElevationTag.parse("unknown"), 0);
		assertEquals(0, ElevationTag.parse("-"), 0);
		assertEquals(0, ElevationTag.parse("."), 0);
		assertEquals(0, ElevationTag.parse(null), 0);
	}

	@Test
	public void aSecondDotEndsTheNumber() {
		assertEquals(1.234, ElevationTag.parse("1.234.5"), 0);
		assertEquals(1234, ElevationTag.parse("1234."), 0);
	}
}

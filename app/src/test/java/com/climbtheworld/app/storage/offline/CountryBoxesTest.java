package com.climbtheworld.app.storage.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;

public class CountryBoxesTest {
	@Test
	public void smallestContainingBoxComesFirst() throws Exception {
		CountryBoxes boxes = CountryBoxes.read(new StringReader(
				"ISO3166 country code,country,long W,lat S,long E,lat N\n"
						+ "BIG,Big,0,0,10,10\n"
						+ "SMALL,Small,1,1,2,2\n"
						+ "FAR,Far,20,20,30,30\n"));

		assertEquals(Arrays.asList("SMALL", "BIG"), boxes.containing(1.5, 1.5));
		assertEquals(Arrays.asList("BIG"), boxes.containing(5, 5));
		assertTrue(boxes.containing(-5, -5).isEmpty());
	}

	@Test
	public void skipsGlobeWideBoxesAndBadLines() throws Exception {
		CountryBoxes boxes = CountryBoxes.read(new StringReader(
				"header\n"
						+ "FJ,Fiji,-180,-21,180,-12\n"
						+ "XX,Broken,a,b,c,d\n"
						+ "YY,Short,1,2\n"));

		assertTrue(boxes.containing(-17, 178).isEmpty());
	}

	@Test
	public void readsTheAppCountryList() throws Exception {
		try (Reader csv = new FileReader("src/main/res/raw/country_bbox.csv")) {
			CountryBoxes boxes = CountryBoxes.read(csv);

			assertTrue(boxes.containing(45.5, -73.6).contains("CA"));
			assertTrue(boxes.containing(44.43, 26.1).contains("RO"));
		}
	}
}

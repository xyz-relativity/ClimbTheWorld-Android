package com.climbtheworld.app.storage.views;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NearbyCountriesTest {
	// Boxes as country_bbox.csv has them: long W, lat S, long E, lat N.
	private static final List<CountryViewState> COUNTRIES = Arrays.asList(
			country("CH,Switzerland,6.02260949059,45.7769477403,10.4427014502,47.8308275417"),
			country("IT,Italy,6.7499552751,36.619987291,18.4802470232,47.1153931748"),
			country("FR,France,-5.1406,41.3337,9.56001631027,51.1485061713"),
			country("RU,Russia,-180,41.151416124,180,81.2504"),
			country("BAD,Broken,west,south,east,north"));

	@Test
	public void smallestBoxComesFirst() {
		assertEquals(Arrays.asList("CH", "IT", "FR"),
				NearbyCountries.containing(COUNTRIES, 46.8, 8.2, 3));
	}

	@Test
	public void keepsOnlyTheLimit() {
		assertEquals(Collections.singletonList("CH"),
				NearbyCountries.containing(COUNTRIES, 46.8, 8.2, 1));
	}

	@Test
	public void leavesOutBoxesAroundTheGlobe() {
		// Moscow is only in Russia's box, which spans every longitude.
		assertEquals(Collections.emptyList(),
				NearbyCountries.containing(COUNTRIES, 55.75, 37.62, 3));
	}

	private static CountryViewState country(String countryInfo) {
		return new CountryViewState(DataFragment.CountryState.ADD, countryInfo);
	}
}

package com.climbtheworld.app.map.marker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The display filters, as read once and tested against, see NodeDisplayFilters.Settings.
 */
@RunWith(RobolectricTestRunner.class)
public class NodeDisplayFiltersTest {
	private static final int ANY_GRADE = -1;
	private static final int GRADE_6_PLUS = GradeSystem.uiaa.indexOf("6+");
	private static final int GRADE_7_PLUS = GradeSystem.uiaa.indexOf("7+");

	@Test
	public void withoutFiltersEveryRoutePasses() throws JSONException {
		NodeDisplayFilters.Settings filters = filters(ANY_GRADE, ANY_GRADE, allStyles(), "");

		assertFalse(filters.hasFilters());
		assertTrue(filters.matches(route("Slab", null)));
		assertTrue(filters.matches(route("Slab", "6+", GeoNode.ClimbingStyle.sport)));
	}

	@Test
	public void theGradeFilterKeepsTheGradesFromItsLowestToItsHighest() throws JSONException {
		NodeDisplayFilters.Settings from7Plus =
				filters(GRADE_7_PLUS, ANY_GRADE, allStyles(), "");
		NodeDisplayFilters.Settings upTo6Plus =
				filters(ANY_GRADE, GRADE_6_PLUS, allStyles(), "");
		NodeDisplayFilters.Settings between =
				filters(GRADE_6_PLUS, GRADE_7_PLUS, allStyles(), "");

		assertTrue(from7Plus.hasFilters());
		assertTrue(from7Plus.matches(route("Hard", "7+")));
		assertFalse(from7Plus.matches(route("Easy", "6+")));
		assertTrue(upTo6Plus.matches(route("Easy", "6+")));
		assertFalse(upTo6Plus.matches(route("Hard", "7+")));
		assertTrue(between.matches(route("Easy", "6+")));
		assertTrue(between.matches(route("Hard", "7+")));
	}

	@Test
	public void aRouteWithoutAGradePassesNoGradeFilter() throws JSONException {
		assertFalse(filters(GRADE_6_PLUS, ANY_GRADE, allStyles(), "")
				.matches(route("Ungraded", null)));
	}

	@Test
	public void theStyleFilterKeepsTheRoutesOfOneOfItsStyles() throws JSONException {
		NodeDisplayFilters.Settings sportOnly = filters(ANY_GRADE, ANY_GRADE,
				EnumSet.of(GeoNode.ClimbingStyle.sport), "");

		assertTrue(sportOnly.hasFilters());
		assertTrue(sportOnly.matches(route("Bolted", "6+", GeoNode.ClimbingStyle.sport)));
		assertTrue(sportOnly.matches(route("Both", "6+", GeoNode.ClimbingStyle.trad,
				GeoNode.ClimbingStyle.sport)));
		assertFalse(sportOnly.matches(route("Crack", "6+", GeoNode.ClimbingStyle.trad)));
		assertFalse(sportOnly.matches(route("Unknown", "6+")));
	}

	@Test
	public void theTextFilterKeepsTheNamesHoldingIt() throws JSONException {
		NodeDisplayFilters.Settings crack = filters(ANY_GRADE, ANY_GRADE, allStyles(), "crack");

		assertTrue(crack.hasFilters());
		assertTrue(crack.matches(route("Big Crack", "6+")));
		assertFalse(crack.matches(route("Slab", "6+")));
	}

	@Test
	public void theTypeFilterKeepsItsKindsOfFeature() throws JSONException {
		NodeDisplayFilters.Settings cragsOnly = new NodeDisplayFilters.Settings(
				GradeSystem.uiaa, ANY_GRADE, ANY_GRADE, allStyles(),
				EnumSet.of(OsmEntity.EntityClimbingType.crag), "");

		assertTrue(cragsOnly.hasFilters());
		assertFalse(cragsOnly.matches(route("Slab", "6+")));
	}

	@Test
	public void filtersSetAlikeAreEqual() {
		// As Configs reads them: a set made anew at each reading.
		assertEquals(filters(GRADE_6_PLUS, ANY_GRADE, new TreeSet<>(allStyles()), "crack"),
				filters(GRADE_6_PLUS, ANY_GRADE, new TreeSet<>(allStyles()), "crack"));
		assertNotEquals(filters(GRADE_6_PLUS, ANY_GRADE, allStyles(), "crack"),
				filters(GRADE_6_PLUS, ANY_GRADE, allStyles(), "slab"));
		assertNotEquals(filters(GRADE_6_PLUS, ANY_GRADE, allStyles(), ""),
				filters(GRADE_6_PLUS, ANY_GRADE, EnumSet.of(GeoNode.ClimbingStyle.sport), ""));
	}

	/** Filters showing every kind of feature, with the grades in UIAA. */
	private static NodeDisplayFilters.Settings filters(int minGrade, int maxGrade,
	                                                   Set<GeoNode.ClimbingStyle> styles,
	                                                   String text) {
		return new NodeDisplayFilters.Settings(GradeSystem.uiaa, minGrade, maxGrade, styles,
				new TreeSet<>(Arrays.asList(OsmEntity.EntityClimbingType.features())), text);
	}

	private static Set<GeoNode.ClimbingStyle> allStyles() {
		return EnumSet.allOf(GeoNode.ClimbingStyle.class);
	}

	/**
	 * @param uiaaGrade null for a route without a grade
	 */
	private static GeoNode route(String name, String uiaaGrade, GeoNode.ClimbingStyle... styles)
			throws JSONException {
		JSONObject tags = new JSONObject().put("sport", "climbing")
				.put("climbing", "route_bottom").put("name", name);
		if (uiaaGrade != null) {
			tags.put("climbing:grade:uiaa", uiaaGrade);
		}
		for (GeoNode.ClimbingStyle style : styles) {
			tags.put(style.tagKey(), "yes");
		}
		return new GeoNode(new JSONObject().put("type", "node").put("id", 1)
				.put("lat", 45.5).put("lon", 24.5).put("tags", tags));
	}
}

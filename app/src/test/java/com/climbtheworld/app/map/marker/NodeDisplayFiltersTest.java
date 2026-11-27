package com.climbtheworld.app.map.marker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.logbook.BookmarkIndex;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
				EnumSet.of(OsmEntity.EntityClimbingType.crag), "", null);

		assertTrue(cragsOnly.hasFilters());
		assertFalse(cragsOnly.matches(route("Slab", "6+")));
	}

	@Test
	public void theBookmarkFilterKeepsTheBookmarkedAndTheirRoutes() throws JSONException {
		// Crag relation 7 holds route node 2 and route way 30, which runs from node 31 to 32.
		Map<String, List<String>> parts = new HashMap<>();
		parts.put("relation|7", Arrays.asList("node|2", "way|30"));
		parts.put("way|30", Arrays.asList("node|31"));
		BookmarkIndex.Parts osmData = new BookmarkIndex.Parts() {
			@Override
			public Collection<String> of(String key) {
				return parts.getOrDefault(key, Collections.emptyList());
			}

			@Override
			public Collection<String> routeWaysFrom(long nodeId) {
				return nodeId == 31
						? Collections.singletonList("way|30") : Collections.emptyList();
			}
		};
		NodeDisplayFilters.Settings bookmarkedCrag = bookmarkedOnly(
				BookmarkIndex.build(Collections.singletonList("relation|7"), osmData));
		NodeDisplayFilters.Settings bookmarkedRoute = bookmarkedOnly(
				BookmarkIndex.build(Collections.singletonList("node|1"), osmData));

		assertTrue(bookmarkedRoute.hasFilters());
		assertTrue(bookmarkedRoute.matches(route("Slab", 1, "node", "6+")));
		assertFalse(bookmarkedRoute.matches(route("Slab", 2, "node", "6+")));
		// The same ID as the bookmarked node, of another type.
		assertFalse(bookmarkedRoute.matches(route("Slab", 1, "way", "6+")));

		assertTrue(bookmarkedCrag.matches(route("Arete", 2, "node", "6+")));
		assertTrue(bookmarkedCrag.matches(route("Corner", 30, "way", "6+")));
		assertTrue(bookmarkedCrag.matches(route("Corner", 31, "node", "6+")));
		assertFalse(bookmarkedCrag.matches(route("Corner", 32, "node", "6+")));
		assertFalse(bookmarkedCrag.matches(route("Elsewhere", 3, "node", "6+")));
	}

	@Test
	public void aBookmarkedBottomTakesItsRouteWayAlong() throws JSONException {
		// The top of route way 30 opens its bottom, node 31; the crag counts the way.
		BookmarkIndex bottom = BookmarkIndex.build(Collections.singletonList("node|31"),
				new BookmarkIndex.Parts() {
					@Override
					public Collection<String> of(String key) {
						return "way|30".equals(key) ? Collections.singletonList("node|31")
								: Collections.emptyList();
					}

					@Override
					public Collection<String> routeWaysFrom(long nodeId) {
						return nodeId == 31 ? Collections.singletonList("way|30")
								: Collections.emptyList();
					}
				});

		assertTrue(bottom.isBookmarked("node|31"));
		assertFalse(bottom.isBookmarked("way|30"));
		assertTrue(bookmarkedOnly(bottom).matches(route("Corner", 30, "way", "6+")));
		// Its top may start another route.
		assertFalse(bookmarkedOnly(bottom).matches(route("Corner", 32, "node", null)));
	}

	@Test
	public void aBookmarkFilterWithoutBookmarksLeavesAllOut() throws JSONException {
		NodeDisplayFilters.Settings filters = bookmarkedOnly(BookmarkIndex.EMPTY);

		assertTrue(filters.hasFilters());
		assertFalse(filters.matches(route("Slab", "6+")));
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
				new TreeSet<>(Arrays.asList(OsmEntity.EntityClimbingType.features())), text, null);
	}

	/** Filters showing only the bookmarked, and every grade, style and kind of feature. */
	private static NodeDisplayFilters.Settings bookmarkedOnly(BookmarkIndex bookmarks) {
		return new NodeDisplayFilters.Settings(GradeSystem.uiaa, ANY_GRADE, ANY_GRADE,
				allStyles(), new TreeSet<>(Arrays.asList(OsmEntity.EntityClimbingType.features())),
				"", bookmarks);
	}

	private static Set<GeoNode.ClimbingStyle> allStyles() {
		return EnumSet.allOf(GeoNode.ClimbingStyle.class);
	}

	/**
	 * @param uiaaGrade null for a route without a grade
	 */
	private static GeoNode route(String name, String uiaaGrade, GeoNode.ClimbingStyle... styles)
			throws JSONException {
		return route(name, 1, "node", uiaaGrade, styles);
	}

	/**
	 * @param osmType the OSM type of the route's element: node, way or relation
	 */
	private static GeoNode route(String name, long osmId, String osmType, String uiaaGrade,
	                             GeoNode.ClimbingStyle... styles) throws JSONException {
		JSONObject tags = new JSONObject().put("sport", "climbing")
				.put("climbing", "route_bottom").put("name", name);
		if (uiaaGrade != null) {
			tags.put("climbing:grade:uiaa", uiaaGrade);
		}
		for (GeoNode.ClimbingStyle style : styles) {
			tags.put(style.tagKey(), "yes");
		}
		return new GeoNode(new JSONObject().put("type", osmType).put("id", osmId)
				.put("lat", 45.5).put("lon", 24.5).put("tags", tags));
	}
}

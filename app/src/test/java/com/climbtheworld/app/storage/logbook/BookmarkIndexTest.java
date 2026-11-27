package com.climbtheworld.app.storage.logbook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the "bookmarked only" display filter lets through, see BookmarkIndex.
 */
public class BookmarkIndexTest {
	// Area relation 1 holds crag relation 2, which holds route node 3, route way 4 (from node 5
	// to node 6) and route relation 7 (pitch way 8, from node 9). Route way 12 starts at the top
	// of route way 4, node 6, and is in no crag. Crag relation 10 holds route node 11 and area
	// relation 1, as mappers sometimes do the wrong way round. As Bookmarks gives them, the parts
	// of a route way are its bottom node only.
	private static final Map<String, List<String>> PARTS = new HashMap<>();
	private static final Map<Long, List<String>> ROUTE_WAYS_FROM = new HashMap<>();

	static {
		PARTS.put("relation|1", Arrays.asList("relation|2"));
		PARTS.put("relation|2", Arrays.asList("node|3", "way|4", "relation|7"));
		PARTS.put("way|4", Arrays.asList("node|5"));
		PARTS.put("relation|7", Arrays.asList("way|8"));
		PARTS.put("way|8", Arrays.asList("node|9"));
		PARTS.put("way|12", Arrays.asList("node|6"));
		PARTS.put("relation|10", Arrays.asList("node|11", "relation|1"));
		ROUTE_WAYS_FROM.put(5L, Arrays.asList("way|4"));
		ROUTE_WAYS_FROM.put(9L, Arrays.asList("way|8"));
		ROUTE_WAYS_FROM.put(6L, Arrays.asList("way|12"));
	}

	private static final BookmarkIndex.Parts OSM_DATA = new BookmarkIndex.Parts() {
		@Override
		public Collection<String> of(String key) {
			return PARTS.getOrDefault(key, Collections.emptyList());
		}

		@Override
		public Collection<String> routeWaysFrom(long nodeId) {
			return ROUTE_WAYS_FROM.getOrDefault(nodeId, Collections.emptyList());
		}
	};

	@Test
	public void aBookmarkedAreaLetsThroughEverythingItHolds() {
		BookmarkIndex index = build("relation|1");

		for (String key : Arrays.asList("relation|1", "relation|2", "node|3", "way|4", "node|5",
				"relation|7", "way|8", "node|9")) {
			assertTrue(key, index.shows(key));
		}
		// The top of route way 4 starts route way 12, which is in no bookmarked crag.
		assertFalse(index.shows("node|6"));
		assertFalse(index.shows("way|12"));
		assertFalse(index.shows("relation|10"));
		assertFalse(index.shows("node|11"));
		assertTrue(index.isBookmarked("relation|1"));
		assertFalse(index.isBookmarked("relation|2"));
	}

	@Test
	public void relationsHoldingEachOtherEndTheWalk() {
		PARTS.put("relation|1", Arrays.asList("relation|2", "relation|10"));
		try {
			BookmarkIndex index = build("relation|10");

			assertTrue(index.shows("node|11"));
			assertTrue(index.shows("node|3"));
		} finally {
			PARTS.put("relation|1", Arrays.asList("relation|2"));
		}
	}

	@Test
	public void aBookmarkedRouteLetsThroughOnlyItself() {
		BookmarkIndex index = build("node|3");

		assertTrue(index.shows("node|3"));
		assertFalse(index.shows("relation|2"));
		assertFalse(index.shows("way|4"));
		assertFalse(index.shows("way|3"));
	}

	@Test
	public void theBottomOfARouteWayTakesTheWayAlong() {
		BookmarkIndex index = build("node|5");

		assertTrue(index.shows("way|4"));
		assertFalse(index.shows("relation|2"));
	}

	@Test
	public void chainedRouteWaysStayApart() {
		// Node 6 is the top of route way 4 and the bottom of route way 12.
		BookmarkIndex lower = build("node|5");
		BookmarkIndex upper = build("node|6");

		assertFalse(lower.shows("node|6"));
		assertFalse(lower.shows("way|12"));
		assertTrue(upper.shows("way|12"));
		assertFalse(upper.shows("way|4"));
		assertFalse(upper.shows("node|5"));
	}

	@Test
	public void elementsNotOnTheDeviceAreStillLetThrough() {
		BookmarkIndex index = build("relation|99");

		assertTrue(index.shows("relation|99"));
		assertFalse(index.isEmpty());
	}

	@Test
	public void noBookmarksIsTheEmptyIndex() {
		assertSame(BookmarkIndex.EMPTY, build());
		assertTrue(BookmarkIndex.EMPTY.isEmpty());
		assertFalse(BookmarkIndex.EMPTY.shows("node|3"));
	}

	@Test
	public void indexesOfTheSameBookmarksAreAlike() {
		assertTrue(build("node|3", "relation|2").sameAs(build("relation|2", "node|3")));
		assertFalse(build("node|3").sameAs(build("relation|2")));
		// The same elements let through, from other bookmarks.
		assertFalse(build("relation|2", "node|3").sameAs(build("relation|2")));
	}

	private static BookmarkIndex build(String... keys) {
		return BookmarkIndex.build(Arrays.asList(keys), OSM_DATA);
	}
}

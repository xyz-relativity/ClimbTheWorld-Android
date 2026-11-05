package com.climbtheworld.app.map.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MapZoomLevelsTest {
	private static final List<Float> LEVELS = Arrays.asList(MapZoomLevels.AREA_MIN,
			MapZoomLevels.CRAG_MIN, MapZoomLevels.POI_AND_ROUTE_MIN);

	@Test
	public void aLevelBetweenTheZoomsIsCrossed() {
		assertTrue(MapZoomLevels.crosses(14.5, 15.5, LEVELS));
		assertTrue(MapZoomLevels.crosses(15.5, 14.5, LEVELS));
		assertTrue(MapZoomLevels.crosses(6, 19, LEVELS));
	}

	@Test
	public void zoomsBetweenTwoLevelsCrossNone() {
		assertFalse(MapZoomLevels.crosses(15.2, 17.9, LEVELS));
		assertFalse(MapZoomLevels.crosses(17.9, 15.2, LEVELS));
		assertFalse(MapZoomLevels.crosses(1, 6.9, LEVELS));
		assertFalse(MapZoomLevels.crosses(14, 16, Collections.emptyList()));
	}

	@Test
	public void aZoomAtTheLevelShowsOtherThanOneBelow() {
		assertTrue(MapZoomLevels.crosses(MapZoomLevels.CRAG_MIN, 14.9, LEVELS));
		assertTrue(MapZoomLevels.crosses(14.9, MapZoomLevels.CRAG_MIN, LEVELS));
		assertTrue(MapZoomLevels.crosses(MapZoomLevels.CRAG_MIN, 15.1, LEVELS));
	}

	@Test
	public void theSameZoomCrossesNothing() {
		assertFalse(MapZoomLevels.crosses(MapZoomLevels.CRAG_MIN, MapZoomLevels.CRAG_MIN,
				LEVELS));
	}
}

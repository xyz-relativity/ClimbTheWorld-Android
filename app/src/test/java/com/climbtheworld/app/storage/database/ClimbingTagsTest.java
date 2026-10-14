package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClimbingTagsTest {
	@Test
	public void climbingIsFoundAmongSeveralSports() {
		assertTrue(ClimbingTags.hasClimbingSport("climbing"));
		assertTrue(ClimbingTags.hasClimbingSport("hiking;climbing"));
		assertTrue(ClimbingTags.hasClimbingSport("climbing; hiking"));
		assertFalse(ClimbingTags.hasClimbingSport("ice_climbing"));
		assertFalse(ClimbingTags.hasClimbingSport(""));
		assertFalse(ClimbingTags.hasClimbingSport(null));
	}

	@Test
	public void sportPatternMatchesClimbingInTagsText() {
		String pattern = ".*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ".*";

		assertTrue("{\"sport\":\"climbing\",\"name\":\"x\"}".matches(pattern));
		assertTrue("{\"name\":\"x\",\"sport\":\"hiking;climbing\"}".matches(pattern));
		assertTrue("{\"sport\":\"climbing;hiking\"}".matches(pattern));
		assertFalse("{\"sport\":\"ice_climbing\"}".matches(pattern));
		assertFalse("{\"sport\":\"hiking\",\"name\":\"climbing\"}".matches(pattern));
		assertFalse("{\"sport\":\"climbing_gym\"}".matches(pattern));
	}

	@Test
	public void routePatternMatchesRoutesAndGradedBoulders() {
		String pattern = ClimbingTags.ROUTE_PATTERN + ".*";

		assertTrue("{\"climbing\":\"route_bottom\"}".matches(pattern));
		assertTrue("{\"climbing\":\"route_top\"}".matches(pattern));
		assertTrue("{\"climbing\":\"route\"}".matches(pattern));
		assertTrue("{\"climbing:grade:french\":\"6a\",\"climbing\":\"boulder\"}".matches(pattern));
		assertTrue("{\"climbing\":\"boulder\",\"climbing:grade:font\":\"6A\"}".matches(pattern));
		assertFalse("{\"climbing\":\"boulder\",\"natural\":\"stone\"}".matches(pattern));
		assertFalse("{\"climbing\":\"crag\",\"climbing:grade:uiaa:max\":\"7\"}".matches(pattern));
		assertFalse("{\"climbing\":\"routes\"}".matches(pattern));
	}

	@Test
	public void artificialPatternMatchesClimbingFacilities() {
		String pattern = ".*" + ClimbingTags.ARTIFICIAL_PATTERN + ".*";

		assertTrue("{\"leisure\":\"sports_centre\"}".matches(pattern));
		assertTrue("{\"leisure\":\"sports_hall\"}".matches(pattern));
		assertTrue("{\"sport\":\"climbing\",\"leisure\":\"pitch\"}".matches(pattern));
		assertTrue("{\"tower:type\":\"climbing\"}".matches(pattern));
		assertTrue("{\"building\":\"yes\"}".matches(pattern));
		assertFalse("{\"leisure\":\"pitches\"}".matches(pattern));
		assertFalse("{\"leisure\":\"nature_reserve\"}".matches(pattern));
		assertFalse("{\"tower:type\":\"communication\"}".matches(pattern));
	}
}

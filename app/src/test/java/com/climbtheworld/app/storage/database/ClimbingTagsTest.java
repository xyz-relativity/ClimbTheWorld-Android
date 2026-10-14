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

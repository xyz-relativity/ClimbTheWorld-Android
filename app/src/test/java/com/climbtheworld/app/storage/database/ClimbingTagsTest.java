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
}

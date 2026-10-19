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
}

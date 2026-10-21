package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * AlignedHeight.
 */
public class AlignedHeightTest {
	private static final double CENTIMETER = 0.01;

	@Test
	public void putsTheEyeAboveTheAimedBase() {
		// The phone 1.4 m above the route's base, which the terrain has at 812 m.
		assertEquals(813.4, AlignedHeight.eyeElevation(0.2, new double[]{-1.2},
				new double[]{812}), CENTIMETER);
	}

	@Test
	public void followsThePhoneUp() {
		// Climbed 15 m since aiming at the base, as ARCore tracked it.
		assertEquals(828.4, AlignedHeight.eyeElevation(15.2, new double[]{-1.2},
				new double[]{812}), CENTIMETER);
	}

	@Test
	public void averagesTheRoutes() {
		assertEquals(813, AlignedHeight.eyeElevation(0, new double[]{-1, -2},
				new double[]{812, 811}), CENTIMETER);
	}

	@Test
	public void leavesOutRoutesNotKnownBothWays() {
		assertEquals(813, AlignedHeight.eyeElevation(0, new double[]{-1, Double.NaN, -1},
				new double[]{812, 700, Double.NaN}), CENTIMETER);
	}

	@Test
	public void tellsNothingWithoutAKnownRoute() {
		assertTrue(Double.isNaN(AlignedHeight.eyeElevation(0, new double[0], new double[0])));
		assertTrue(Double.isNaN(AlignedHeight.eyeElevation(0, new double[]{-1},
				new double[]{Double.NaN})));
	}
}

package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

/**
 * AlignedElevations.
 */
public class AlignedElevationsTest {
	private static final double CENTIMETER = 0.01;
	private static final double LATITUDE = 45.52;
	private static final double LONGITUDE = -73.535;
	private static final double METERS_PER_DEGREE = 6378137 * Math.PI / 180;

	/**
	 * A route some meters east of the first one, its base aimed at some height in the session's
	 * world, where the terrain model has it at some elevation.
	 */
	private static AlignedElevations.AimedBase base(long osmId, double eastMeters, double baseY,
	                                                double modelElevation) {
		return new AlignedElevations.AimedBase(osmId, LATITUDE, LONGITUDE + eastMeters
				/ (METERS_PER_DEGREE * Math.cos(Math.toRadians(LATITUDE))), baseY, modelElevation);
	}

	private static double longitudeAt(double eastMeters) {
		return LONGITUDE + eastMeters / (METERS_PER_DEGREE * Math.cos(Math.toRadians(LATITUDE)));
	}

	@Test
	public void putsTheEyeAboveTheAimedBase() {
		// The phone 1.4 m above the route's base, which the terrain has at 812 m.
		AlignedElevations aligned = AlignedElevations.of(0.2, List.of(base(1, 0, -1.2, 812)));
		assertEquals(813.4, aligned.eyeElevationMeters(), CENTIMETER);
	}

	@Test
	public void followsThePhoneUp() {
		// Climbed 15 m since aiming at the base, as ARCore tracked it.
		AlignedElevations aligned = AlignedElevations.of(15.2, List.of(base(1, 0, -1.2, 812)));
		assertEquals(828.4, aligned.eyeElevationMeters(), CENTIMETER);
	}

	@Test
	public void averagesTheEyeOverTheRoutes() {
		AlignedElevations aligned = AlignedElevations.of(0,
				List.of(base(1, 0, -1, 812), base(2, 10, -2, 811)));
		assertEquals(813, aligned.eyeElevationMeters(), CENTIMETER);
	}

	@Test
	public void leavesOutRoutesNotKnownBothWays() {
		AlignedElevations aligned = AlignedElevations.of(0, List.of(base(1, 0, -1, 812),
				base(2, 5, Double.NaN, 700), base(3, 10, -1, Double.NaN)));
		assertEquals(813, aligned.eyeElevationMeters(), CENTIMETER);
		assertEquals(0, aligned.offsetMeters(3, LATITUDE, longitudeAt(10)), CENTIMETER);
	}

	@Test
	public void tellsNothingWithoutAKnownRoute() {
		assertNull(AlignedElevations.of(0, List.of()));
		assertNull(AlignedElevations.of(0, List.of(base(1, 0, -1, Double.NaN))));
	}

	@Test
	public void aSingleRouteCorrectsNothing() {
		AlignedElevations aligned = AlignedElevations.of(0.2, List.of(base(1, 0, -1.2, 812)));
		assertEquals(0, aligned.offsetMeters(1, LATITUDE, LONGITUDE), CENTIMETER);
		assertEquals(0, aligned.offsetMeters(0, LATITUDE, longitudeAt(20)), CENTIMETER);
	}

	@Test
	public void alignedRoutesStandWhereAimed() {
		// The terrain has both bases at 812 m, while the second is 2 m higher than the first.
		AlignedElevations aligned = AlignedElevations.of(0,
				List.of(base(1, 0, -2, 812), base(2, 10, 0, 812)));
		// The eye splits the difference...
		assertEquals(813, aligned.eyeElevationMeters(), CENTIMETER);
		// ... and the corrections put each pin at the height aimed at: 2 m below the eye at the
		// first, level with it at the second.
		assertEquals(-1, aligned.offsetMeters(1, LATITUDE, LONGITUDE), CENTIMETER);
		assertEquals(1, aligned.offsetMeters(2, LATITUDE, longitudeAt(10)), CENTIMETER);
		// By ID or by spot alike.
		assertEquals(1, aligned.offsetMeters(0, LATITUDE, longitudeAt(10)), CENTIMETER);
	}

	@Test
	public void spotsBetweenAlignedRoutesFollowTheSlopeBetweenThem() {
		AlignedElevations aligned = AlignedElevations.of(0,
				List.of(base(1, 0, -2, 812), base(2, 10, 0, 812)));
		assertEquals(0, aligned.offsetMeters(0, LATITUDE, longitudeAt(5)), CENTIMETER);
		double quarter = aligned.offsetMeters(0, LATITUDE, longitudeAt(2.5));
		assertEquals(-0.8, quarter, 0.1);
	}

	@Test
	public void spotsFarFromTheAlignedRoutesGetAboutTheirAverage() {
		AlignedElevations aligned = AlignedElevations.of(0,
				List.of(base(1, 0, -2, 812), base(2, 10, 0, 812)));
		// Still leaning a little toward the nearer one.
		assertEquals(0, aligned.offsetMeters(0, LATITUDE, longitudeAt(200)), 0.1);
	}
}

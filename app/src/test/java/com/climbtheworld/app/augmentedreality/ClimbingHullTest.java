package com.climbtheworld.app.augmentedreality;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.storage.database.GeoNode;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

public class ClimbingHullTest {
	private static final double LATITUDE = 45.52;
	private static final double LONGITUDE = -73.535;
	private static final double METERS_PER_DEGREE_LONGITUDE =
			ClimbingHull.METERS_PER_DEGREE * Math.cos(Math.toRadians(LATITUDE));

	/**
	 * A rectangle, as a closed ring, in meters east and north of the observer.
	 */
	private static ClimbingHull rectangle(double west, double south, double east, double north) {
		double[][] corners = {{west, south}, {west, north}, {east, north}, {east, south},
				{west, south}};
		double[] latitudes = new double[corners.length];
		double[] longitudes = new double[corners.length];
		for (int index = 0; index < corners.length; index++) {
			longitudes[index] = LONGITUDE + corners[index][0] / METERS_PER_DEGREE_LONGITUDE;
			latitudes[index] = LATITUDE + corners[index][1] / ClimbingHull.METERS_PER_DEGREE;
		}
		return new ClimbingHull("crag|relation|1", GeoNode.NodeTypes.crag, null, latitudes,
				longitudes, new long[]{11, 12});
	}

	private static Geometry range(double meters) {
		return ClimbingHull.GEOMETRY_FACTORY.createPoint(new Coordinate(0, 0)).buffer(meters, 16);
	}

	@Test
	public void spreadIsTheAngleBetweenTheOuterCorners() {
		double[] east = {100, 100, 200, 200, 100};
		double[] north = {-50, 50, 50, -50, -50};
		assertFalse(ClimbingHull.containsOrigin(east, north));
		assertEquals(2 * Math.toDegrees(Math.atan2(50, 100)),
				ClimbingHull.horizontalSpreadDegrees(east, north), 1e-9);
	}

	@Test
	public void spreadDoesNotWrapAroundBehindTheObserver() {
		// South of the observer, across the azimuth where the angles wrap around.
		double[] east = {-10, 10, 10, -10, -10};
		double[] north = {-100, -100, -120, -120, -100};
		assertEquals(2 * Math.toDegrees(Math.atan2(10, 100)),
				ClimbingHull.horizontalSpreadDegrees(east, north), 1e-9);
	}

	@Test
	public void insideRoutesAlwaysShow() {
		double[] east = {-1000, -1000, 1000, 1000, -1000};
		double[] north = {-5, 5, 5, -5, -5};
		assertTrue(ClimbingHull.containsOrigin(east, north));
		assertTrue(ClimbingHull.isShowingRoutes(east, north, false));
	}

	@Test
	public void routesAndPinTakeOverAtDifferentAngles() {
		// Spans 8 degrees, between PIN_MAX_DEGREES and ROUTES_MIN_DEGREES.
		double halfWidth = 1000 * Math.tan(Math.toRadians(4));
		double[] east = {-halfWidth, halfWidth, halfWidth, -halfWidth, -halfWidth};
		double[] north = {1000, 1000, 1010, 1010, 1000};
		assertFalse("Stays a pin", ClimbingHull.isShowingRoutes(east, north, false));
		assertTrue("Keeps showing the routes", ClimbingHull.isShowingRoutes(east, north, true));
	}

	@Test
	public void farHullIsDrawnWithItsPin() {
		ClimbingHull hull = rectangle(-10, 2000, 10, 2010);
		hull.build(LATITUDE, LONGITUDE, range(2500), null);
		assertFalse(hull.isShowingRoutes());
		assertNotNull(hull.getFill());
		assertEquals(1, hull.getOutline().size());
	}

	@Test
	public void hullBeyondTheRangeIsNotDrawn() {
		ClimbingHull hull = rectangle(-10, 3000, 10, 3010);
		hull.build(LATITUDE, LONGITUDE, range(2500), null);
		assertNull(hull.getFill());
		assertTrue(hull.getOutline().isEmpty());
	}

	@Test
	public void routesOfFarHullsAreHidden() {
		ClimbingHulls hulls = new ClimbingHulls();
		hulls.setHulls(java.util.Collections.singletonList(rectangle(-10, 2000, 10, 2010)));
		hulls.update(LATITUDE, LONGITUDE, 2500, null, 0, 0);
		assertTrue(hulls.isHidden(11));
		assertTrue(hulls.isHidden(12));
		assertFalse(hulls.isHidden(13));

		// Walking up to it.
		hulls.update(LATITUDE + 1990 / ClimbingHull.METERS_PER_DEGREE, LONGITUDE, 2500, null, 0, 0);
		assertTrue(hulls.getHulls().get(0).isShowingRoutes());
		assertFalse(hulls.isHidden(11));
	}

	@Test
	public void outlineIsCutToTheRangeInGrowingSteps() {
		// Crossing in front of the observer, beyond the range on both sides.
		ClimbingHull hull = rectangle(-3000, 20, 3000, 40);
		hull.build(LATITUDE, LONGITUDE, range(1000),
				(latitude, longitude, distance) -> 100 + distance);
		assertTrue(hull.isShowingRoutes());
		assertNotNull(hull.getFill());
		// The near and the far edge, the ends of the hull being out of range.
		assertEquals(2, hull.getOutline().size());

		for (ClimbingHull.GroundLine line : hull.getOutline()) {
			assertTrue(line.size > 2);
			for (int index = 0; index < line.size; index++) {
				double distance = Math.hypot(line.east[index], line.north[index]);
				assertTrue("Within the range", distance <= 1000 + 1e-6);
				assertEquals("Elevation looked up where the point is", 100 + distance,
						line.elevation[index], 1e-6);
				if (index > 0) {
					double step = Math.hypot(line.east[index] - line.east[index - 1],
							line.north[index] - line.north[index - 1]);
					double from = Math.hypot(line.east[index - 1], line.north[index - 1]);
					assertTrue("Step " + step + " from " + from, step <= Math.max(
							ClimbingHull.MIN_STEP_METERS, from * ClimbingHull.STEP_PER_METER)
							+ 1e-6);
				}
			}
		}
	}

	@Test
	public void fromInsideOnlyTheOutlineIsDrawn() {
		ClimbingHull hull = rectangle(-300, -200, 400, 100);
		hull.build(LATITUDE, LONGITUDE, range(2500), null);
		assertTrue(hull.isShowingRoutes());
		assertNull(hull.getFill());
		assertEquals(1, hull.getOutline().size());
	}

	@Test
	public void withoutElevationsTheyAreUnknown() {
		ClimbingHull hull = rectangle(-50, 20, 50, 40);
		hull.build(LATITUDE, LONGITUDE, range(1000), null);
		ClimbingHull.GroundLine fill = hull.getFill();
		assertNotNull(fill);
		for (int index = 0; index < fill.size; index++) {
			assertTrue(Double.isNaN(fill.elevation[index]));
		}
	}

	@Test
	public void reloadedHullKeepsShowingItsRoutes() {
		ClimbingHulls hulls = new ClimbingHulls();
		// Spans about 8 degrees from the observer.
		double halfWidth = 1000 * Math.tan(Math.toRadians(4));
		ClimbingHull shown = rectangle(-halfWidth, 1000, halfWidth, 1010);
		hulls.setHulls(java.util.Collections.singletonList(shown));
		// Its routes shown from close by, then seen from where it spans 8 degrees.
		double closer = 900 / ClimbingHull.METERS_PER_DEGREE;
		hulls.update(LATITUDE + closer, LONGITUDE, 2500, null, 0, 0);
		assertTrue(shown.isShowingRoutes());
		hulls.update(LATITUDE, LONGITUDE, 2500, null, 0, 0);
		assertTrue(shown.isShowingRoutes());

		ClimbingHull reloaded = rectangle(-halfWidth, 1000, halfWidth, 1010);
		assertTrue(hulls.setHulls(java.util.Collections.singletonList(reloaded)).isEmpty());
		hulls.update(LATITUDE, LONGITUDE, 2500, null, 0, 0);
		assertTrue(reloaded.isShowingRoutes());
		assertFalse(hulls.isHidden(11));
	}
}

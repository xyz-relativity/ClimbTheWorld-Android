package com.climbtheworld.app.augmentedreality;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.storage.database.OsmEntity;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ClimbingHullTest {
	private static final double LATITUDE = 45.52;
	private static final double LONGITUDE = -73.535;
	private static final double METERS_PER_DEGREE_LONGITUDE =
			ClimbingHull.METERS_PER_DEGREE * Math.cos(Math.toRadians(LATITUDE));
	// Between the distances at which the routes and the pin take over.
	private static final double BETWEEN_METERS = (ClimbingHull.ROUTES_MAX_DISTANCE_METERS
			+ ClimbingHull.PIN_MIN_DISTANCE_METERS) / 2;

	/**
	 * A crag as a rectangle, as a closed ring, in meters east and north of the observer.
	 */
	private static ClimbingHull rectangle(double west, double south, double east, double north) {
		return rectangle(OsmEntity.EntityClimbingType.crag, 1, new long[]{11, 12}, west, south,
				east, north);
	}

	/**
	 * A crag or an area as a rectangle, as a closed ring, in meters east and north of the
	 * observer.
	 */
	private static ClimbingHull rectangle(OsmEntity.EntityClimbingType type, long id,
	                                      long[] nodeIds, double west, double south, double east,
	                                      double north) {
		double[][] corners = {{west, south}, {west, north}, {east, north}, {east, south},
				{west, south}};
		double[] latitudes = new double[corners.length];
		double[] longitudes = new double[corners.length];
		for (int index = 0; index < corners.length; index++) {
			longitudes[index] = LONGITUDE + corners[index][0] / METERS_PER_DEGREE_LONGITUDE;
			latitudes[index] = LATITUDE + corners[index][1] / ClimbingHull.METERS_PER_DEGREE;
		}
		return new ClimbingHull(type + "|relation|" + id, type, null, latitudes, longitudes,
				nodeIds);
	}

	/**
	 * A crag close enough to show its routes, and an area too far to, left to its pin.
	 */
	private static List<ClimbingHull> closeCragAndFarArea() {
		return Arrays.asList(
				rectangle(OsmEntity.EntityClimbingType.crag, 1, new long[]{11, 12}, -10, 20, 10,
						30),
				rectangle(OsmEntity.EntityClimbingType.area, 2, new long[]{21}, -10, 2000, 10,
						2010));
	}

	private static Geometry range(double meters) {
		return ClimbingHull.GEOMETRY_FACTORY.createPoint(new Coordinate(0, 0)).buffer(meters, 16);
	}

	/**
	 * The ground a mesh covers, in square meters: the areas of its triangles.
	 */
	private static double area(ClimbingHull.GroundMesh mesh) {
		double area = 0;
		for (int triangle = 0; triangle < mesh.triangleCount; triangle++) {
			int a = mesh.triangles[triangle * 3];
			int b = mesh.triangles[triangle * 3 + 1];
			int c = mesh.triangles[triangle * 3 + 2];
			area += Math.abs((mesh.east[b] - mesh.east[a]) * (mesh.north[c] - mesh.north[a])
					- (mesh.east[c] - mesh.east[a]) * (mesh.north[b] - mesh.north[a])) / 2;
		}
		return area;
	}

	/**
	 * Checks that the fill and the outline of a hull are on a ground rising with the distance
	 * from where it was laid out, from an elevation there.
	 */
	private static void assertOnGround(double elevation, ClimbingHull hull) {
		ClimbingHull.GroundMesh fill = hull.getFill();
		for (int index = 0; index < fill.size; index++) {
			assertEquals(elevation + Math.hypot(fill.east[index], fill.north[index]),
					fill.elevation[index], 1e-6);
		}
		for (ClimbingHull.GroundLine line : hull.getOutline()) {
			for (int index = 0; index < line.size; index++) {
				assertEquals(elevation + Math.hypot(line.east[index], line.north[index]),
						line.elevation[index], 1e-6);
			}
		}
	}

	@Test
	public void distanceIsToTheNearestEdge() {
		double[] east = {100, 100, 200, 200, 100};
		double[] north = {-50, 50, 50, -50, -50};
		assertFalse(ClimbingHull.containsOrigin(east, north));
		assertEquals(100, ClimbingHull.distanceFromOrigin(east, north), 1e-9);
	}

	@Test
	public void distanceIsToTheNearestCorner() {
		double[] east = {30, 30, 40, 40, 30};
		double[] north = {30, 40, 40, 30, 30};
		assertEquals(30 * Math.sqrt(2), ClimbingHull.distanceFromOrigin(east, north), 1e-9);
	}

	@Test
	public void insideRoutesAlwaysShow() {
		double[] east = {-1000, -1000, 1000, 1000, -1000};
		double[] north = {-5, 5, 5, -5, -5};
		assertTrue(ClimbingHull.containsOrigin(east, north));
		assertTrue(ClimbingHull.isShowingRoutes(east, north, false));
	}

	@Test
	public void routesAndPinTakeOverAtDifferentDistances() {
		double[] east = {-10, 10, 10, -10, -10};
		double[] north = {BETWEEN_METERS, BETWEEN_METERS, BETWEEN_METERS + 10,
				BETWEEN_METERS + 10, BETWEEN_METERS};
		assertFalse("Stays a pin", ClimbingHull.isShowingRoutes(east, north, false));
		assertTrue("Keeps showing the routes", ClimbingHull.isShowingRoutes(east, north, true));
	}

	@Test
	public void thinHullSeenEndOnShowsItsRoutes() {
		// A wall running away from the observer spans little of the view, but is as close as a
		// neighbour facing them, so its routes show alike.
		double[] endOnEast = {-2, -2, 2, 2, -2};
		double[] endOnNorth = {50, 90, 90, 50, 50};
		double[] facingEast = {-20, -20, 20, 20, -20};
		double[] facingNorth = {50, 54, 54, 50, 50};
		assertTrue(ClimbingHull.isShowingRoutes(endOnEast, endOnNorth, false));
		assertTrue(ClimbingHull.isShowingRoutes(facingEast, facingNorth, false));
	}

	@Test
	public void farHullIsDrawnWithItsPin() {
		ClimbingHull hull = rectangle(-10, 2000, 10, 2010);
		hull.build(LATITUDE, LONGITUDE, range(2500), 2500, null);
		assertFalse(hull.isShowingRoutes());
		assertNotNull(hull.getFill());
		assertEquals(1, hull.getOutline().size());
	}

	@Test
	public void hullBeyondTheRangeIsNotDrawn() {
		ClimbingHull hull = rectangle(-10, 3000, 10, 3010);
		hull.build(LATITUDE, LONGITUDE, range(2500), 2500, null);
		assertNull(hull.getFill());
		assertTrue(hull.getOutline().isEmpty());
	}

	@Test
	public void hullOffTheRangeIsNotDrawn() {
		int[] lookups = {0};
		TerrainWireframe.ElevationSource elevations = (latitude, longitude, distance) -> {
			lookups[0]++;
			return 100;
		};
		// North-east, no farther north or east than the range reaches, but farther from the
		// observer.
		ClimbingHull hull = rectangle(1800, 1800, 1900, 1900);
		hull.build(LATITUDE, LONGITUDE, range(2500), 2500, elevations);
		assertNull(hull.getFill());
		assertTrue(hull.getOutline().isEmpty());
		assertEquals(0, lookups[0]);

		// A little closer, its near corner is within the range.
		hull = rectangle(1700, 1700, 1800, 1800);
		hull.build(LATITUDE, LONGITUDE, range(2500), 2500, elevations);
		assertNotNull(hull.getFill());
		assertFalse(hull.getOutline().isEmpty());
		assertTrue(lookups[0] > 0);
	}

	@Test
	public void routesOfFarHullsAreHidden() {
		ClimbingHulls hulls = new ClimbingHulls();
		hulls.setHulls(Collections.singletonList(rectangle(-10, 2000, 10, 2010)));
		hulls.update(LATITUDE, LONGITUDE, 2500, true, null, 0, 0);
		assertTrue(hulls.isHidden(11));
		assertTrue(hulls.isHidden(12));
		assertFalse(hulls.isHidden(13));

		// Walking up to it.
		hulls.update(LATITUDE + 1990 / ClimbingHull.METERS_PER_DEGREE, LONGITUDE, 2500, true, null,
				0, 0);
		assertTrue(hulls.getHulls().get(0).isShowingRoutes());
		assertFalse(hulls.isHidden(11));
	}

	@Test
	public void outlineIsCutToTheRangeInGrowingSteps() {
		// Crossing in front of the observer, beyond the range on both sides.
		ClimbingHull hull = rectangle(-3000, 20, 3000, 40);
		hull.build(LATITUDE, LONGITUDE, range(1000), 1000,
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
	public void fromInsideTheGroundAroundIsFilled() {
		ClimbingHull hull = rectangle(-300, -200, 400, 100);
		hull.build(LATITUDE, LONGITUDE, range(2500), 2500, null);
		assertTrue(hull.isShowingRoutes());
		ClimbingHull.GroundMesh fill = hull.getFill();
		assertNotNull(fill);
		// The whole hull, the observer in it, in cells no wider than the outline's steps there.
		assertEquals(700 * 300, area(fill), 1e-3);
		for (int index = 0; index < fill.size; index++) {
			assertTrue(fill.east[index] >= -300 - 1e-6 && fill.east[index] <= 400 + 1e-6);
			assertTrue(fill.north[index] >= -200 - 1e-6 && fill.north[index] <= 100 + 1e-6);
		}
		assertEquals(1, hull.getOutline().size());
	}

	@Test
	public void fillFollowsTheTerrain() {
		ClimbingHull hull = rectangle(-50, 20, 50, 40);
		hull.build(LATITUDE, LONGITUDE, range(1000), 1000,
				(latitude, longitude, distance) -> 100 + distance);
		ClimbingHull.GroundMesh fill = hull.getFill();
		assertNotNull(fill);
		assertEquals(100 * 20, area(fill), 1e-3);
		// Cut as the outline is, in steps of MIN_STEP_METERS this close.
		assertTrue(fill.triangleCount >= 2 * (100 / ClimbingHull.MIN_STEP_METERS)
				* (20 / ClimbingHull.MIN_STEP_METERS));
		for (int index = 0; index < fill.size; index++) {
			double distance = Math.hypot(fill.east[index], fill.north[index]);
			assertEquals("Elevation looked up where the corner is", 100 + distance,
					fill.elevation[index], 1e-6);
		}
	}

	@Test
	public void fillOfAWideHullIsCutInFewCells() {
		ClimbingHull hull = rectangle(-3000, -3000, 3000, 3000);
		Geometry range = range(2500);
		hull.build(LATITUDE, LONGITUDE, range, 2500, null);
		ClimbingHull.GroundMesh fill = hull.getFill();
		assertNotNull(fill);
		// The range, cut to it, in cells wide enough to be few, whose edge cells are cut to it.
		assertEquals(range.getArea(), area(fill), 1e-3);
		assertTrue(fill.triangleCount <= 8 * ClimbingHull.MAX_FILL_CELLS);
	}

	@Test
	public void withoutElevationsTheyAreUnknown() {
		ClimbingHull hull = rectangle(-50, 20, 50, 40);
		hull.build(LATITUDE, LONGITUDE, range(1000), 1000, null);
		ClimbingHull.GroundMesh fill = hull.getFill();
		assertNotNull(fill);
		for (int index = 0; index < fill.size; index++) {
			assertTrue(Double.isNaN(fill.elevation[index]));
		}
	}

	@Test
	public void reloadedHullKeepsShowingItsRoutes() {
		ClimbingHulls hulls = new ClimbingHulls();
		ClimbingHull shown = rectangle(-10, BETWEEN_METERS, 10, BETWEEN_METERS + 10);
		hulls.setHulls(Collections.singletonList(shown));
		// Its routes shown from 10 m away, then seen from further.
		double closer = (BETWEEN_METERS - 10) / ClimbingHull.METERS_PER_DEGREE;
		hulls.update(LATITUDE + closer, LONGITUDE, 2500, true, null, 0, 0);
		assertTrue(shown.isShowingRoutes());
		hulls.update(LATITUDE, LONGITUDE, 2500, true, null, 0, 0);
		assertTrue(shown.isShowingRoutes());

		ClimbingHull reloaded = rectangle(-10, BETWEEN_METERS, 10, BETWEEN_METERS + 10);
		assertTrue(hulls.setHulls(Collections.singletonList(reloaded)).isEmpty());
		hulls.update(LATITUDE, LONGITUDE, 2500, true, null, 0, 0);
		assertTrue(reloaded.isShowingRoutes());
		assertFalse(hulls.isHidden(11));
	}

	@Test
	public void hiddenOutlinesAreNotLaidOut() {
		int[] lookups = {0};
		TerrainWireframe.ElevationSource elevations = (latitude, longitude, distance) -> {
			lookups[0]++;
			return 100;
		};
		ClimbingHulls hidden = new ClimbingHulls();
		hidden.setHulls(closeCragAndFarArea());
		hidden.update(LATITUDE, LONGITUDE, 2500, false, elevations, 0, 0);
		ClimbingHulls shown = new ClimbingHulls();
		shown.setHulls(closeCragAndFarArea());
		shown.update(LATITUDE, LONGITUDE, 2500, true, null, 0, 0);

		assertEquals(0, lookups[0]);
		assertEquals(2, hidden.getHulls().size());
		for (int index = 0; index < 2; index++) {
			ClimbingHull hull = hidden.getHulls().get(index);
			assertNull(hull.getFill());
			assertTrue(hull.getOutline().isEmpty());
			// The routes show or give way to the pin all the same, the areas first.
			assertEquals(shown.getHulls().get(index).key, hull.key);
			assertEquals(shown.getHulls().get(index).isShowingRoutes(), hull.isShowingRoutes());
		}
		assertEquals(OsmEntity.EntityClimbingType.area, hidden.getHulls().get(0).type);
		assertFalse(hidden.getHulls().get(0).isShowingRoutes());
		assertTrue(hidden.getHulls().get(1).isShowingRoutes());
		for (long nodeId : new long[]{11, 12, 21}) {
			assertEquals(shown.isHidden(nodeId), hidden.isHidden(nodeId));
		}
		assertFalse(hidden.isHidden(11));
		assertTrue(hidden.isHidden(21));

		// Shown again, they are laid out, with nothing else changed.
		hidden.update(LATITUDE, LONGITUDE, 2500, true, elevations, 0, 0);
		assertTrue(lookups[0] > 0);
		for (ClimbingHull hull : hidden.getHulls()) {
			assertNotNull(hull.getFill());
			assertEquals(1, hull.getOutline().size());
		}

		// And hidden again, nothing is left of them.
		hidden.update(LATITUDE, LONGITUDE, 2500, false, elevations, 0, 0);
		for (ClimbingHull hull : hidden.getHulls()) {
			assertNull(hull.getFill());
			assertTrue(hull.getOutline().isEmpty());
		}
		assertTrue(hidden.isHidden(21));
	}

	@Test
	public void routesGiveWayToThePinWhileTheOutlinesAreHidden() {
		ClimbingHulls hulls = new ClimbingHulls();
		ClimbingHull hull = rectangle(-10, BETWEEN_METERS, 10, BETWEEN_METERS + 10);
		hulls.setHulls(Collections.singletonList(hull));
		// Its routes shown from 10 m away, kept from further, then left to the pin from far.
		double closer = (BETWEEN_METERS - 10) / ClimbingHull.METERS_PER_DEGREE;
		hulls.update(LATITUDE + closer, LONGITUDE, 2500, false, null, 0, 0);
		assertTrue(hull.isShowingRoutes());
		hulls.update(LATITUDE, LONGITUDE, 2500, false, null, 0, 0);
		assertTrue(hull.isShowingRoutes());
		assertFalse(hulls.isHidden(11));
		hulls.update(LATITUDE - 100 / ClimbingHull.METERS_PER_DEGREE, LONGITUDE, 2500, false, null,
				0, 0);
		assertFalse(hull.isShowingRoutes());
		assertTrue(hulls.isHidden(11));
	}

	@Test
	public void terrainLoadingMoreOnlyGivesTheElevationsAgain() {
		double[] ground = {100};
		int[] lookups = {0};
		TerrainWireframe.ElevationSource elevations = (latitude, longitude, distance) -> {
			lookups[0]++;
			return ground[0] + distance;
		};
		ClimbingHulls hulls = new ClimbingHulls();
		ClimbingHull hull = rectangle(-50, 20, 50, 40);
		hulls.setHulls(Collections.singletonList(hull));
		hulls.update(LATITUDE, LONGITUDE, 1000, true, elevations, 1, 0);
		ClimbingHull.GroundMesh fill = hull.getFill();
		assertNotNull(fill);
		List<ClimbingHull.GroundLine> outline = new ArrayList<>(hull.getOutline());
		assertFalse(outline.isEmpty());
		int points = fill.size;
		for (ClimbingHull.GroundLine line : outline) {
			points += line.size;
		}
		assertEquals(points, lookups[0]);
		assertOnGround(100, hull);

		// More of the terrain loaded, but the elevations were just looked up.
		ground[0] = 200;
		hulls.update(LATITUDE, LONGITUDE, 1000, true, elevations, 2,
				ClimbingHulls.TERRAIN_REBUILD_DELAY_MS - 1);
		assertEquals(points, lookups[0]);
		assertOnGround(100, hull);

		// Later, they are looked up again, for the hull as it was laid out.
		hulls.update(LATITUDE, LONGITUDE, 1000, true, elevations, 2,
				ClimbingHulls.TERRAIN_REBUILD_DELAY_MS);
		assertEquals(2 * points, lookups[0]);
		assertSame(fill, hull.getFill());
		assertEquals(outline.size(), hull.getOutline().size());
		for (int index = 0; index < outline.size(); index++) {
			assertSame(outline.get(index), hull.getOutline().get(index));
		}
		assertOnGround(200, hull);
		assertTrue(hull.isShowingRoutes());
		assertFalse(hulls.isHidden(11));

		// Loaded, there is nothing more to look up.
		hulls.update(LATITUDE, LONGITUDE, 1000, true, elevations, 2, 600_000);
		assertEquals(2 * points, lookups[0]);

		// The observer a few steps away, the hull stays laid out from where it was.
		ground[0] = 300;
		hulls.update(LATITUDE + 5 / ClimbingHull.METERS_PER_DEGREE, LONGITUDE, 1000, true,
				elevations, 3, 600_000);
		assertEquals(3 * points, lookups[0]);
		assertSame(fill, hull.getFill());
		assertOnGround(300, hull);
	}

	@Test
	public void terrainLoadingMoreLeavesHiddenOutlinesAlone() {
		int[] lookups = {0};
		TerrainWireframe.ElevationSource elevations = (latitude, longitude, distance) -> {
			lookups[0]++;
			return 100;
		};
		ClimbingHulls hulls = new ClimbingHulls();
		hulls.setHulls(closeCragAndFarArea());
		hulls.update(LATITUDE, LONGITUDE, 2500, false, elevations, 1, 0);
		hulls.update(LATITUDE, LONGITUDE, 2500, false, elevations, 2, 600_000);
		assertEquals(0, lookups[0]);
		for (ClimbingHull hull : hulls.getHulls()) {
			assertNull(hull.getFill());
			assertTrue(hull.getOutline().isEmpty());
		}
	}
}

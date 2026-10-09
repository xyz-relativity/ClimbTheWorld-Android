package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.storage.database.GeoNode;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The crag and area hulls around the observer, laid out for the AR view, along with the nodes
 * left to the pin of a hull too small in the view to make them out. Only to be used from the main
 * thread.
 */
public class ClimbingHulls {
	// How far the observer moves before the hulls are laid out around them again, for the cut to
	// the range and the choice between routes and pin. The projection takes the rest of the move.
	static final double REBUILD_DISTANCE_METERS = 10;
	// How often the outlines follow the terrain while it loads.
	static final long TERRAIN_REBUILD_DELAY_MS = 1000;
	private static final int RANGE_QUADRANT_SEGMENTS = 16;
	private final List<ClimbingHull> drawn = new ArrayList<>();
	private final Set<Long> hiddenNodes = new HashSet<>();
	private Map<String, ClimbingHull> hulls = new HashMap<>();
	private boolean changed;
	private double originLatitude = Double.NaN;
	private double originLongitude = Double.NaN;
	private double range;
	private boolean withElevation;
	private int terrainVersion;
	private long builtAtMillis;

	/**
	 * Replaces the hulls, keeping whether the ones already there show their routes.
	 *
	 * @return the hulls no longer there
	 */
	public List<ClimbingHull> setHulls(Collection<ClimbingHull> loaded) {
		Map<String, ClimbingHull> next = new HashMap<>();
		for (ClimbingHull hull : loaded) {
			ClimbingHull earlier = hulls.remove(hull.key);
			if (earlier != null) {
				hull.keepStateOf(earlier);
			}
			next.put(hull.key, hull);
		}
		List<ClimbingHull> removed = new ArrayList<>(hulls.values());
		hulls = next;
		changed = true;
		return removed;
	}

	/**
	 * Lays the hulls out around the observer again when they changed, the observer moved, or,
	 * now and then, when the terrain loaded more.
	 *
	 * @param range          how far from the observer the outlines are drawn, in meters
	 * @param elevations     source of the ground elevation, null to leave it unknown
	 * @param terrainVersion changes whenever the elevations may have changed
	 */
	public void update(double latitude, double longitude, double range,
	                   TerrainWireframe.ElevationSource elevations, int terrainVersion,
	                   long nowMillis) {
		boolean withElevation = elevations != null;
		boolean moved = Double.isNaN(originLatitude)
				|| Math.hypot(getEastOfOrigin(longitude), getNorthOfOrigin(latitude))
				> REBUILD_DISTANCE_METERS;
		boolean terrainChanged = withElevation && terrainVersion != this.terrainVersion
				&& nowMillis - builtAtMillis >= TERRAIN_REBUILD_DELAY_MS;
		if (!changed && !moved && !terrainChanged && range == this.range
				&& withElevation == this.withElevation) {
			return;
		}

		changed = false;
		originLatitude = latitude;
		originLongitude = longitude;
		this.range = range;
		this.withElevation = withElevation;
		this.terrainVersion = terrainVersion;
		builtAtMillis = nowMillis;

		Geometry rangeShape = ClimbingHull.GEOMETRY_FACTORY.createPoint(new Coordinate(0, 0))
				.buffer(range, RANGE_QUADRANT_SEGMENTS);
		drawn.clear();
		hiddenNodes.clear();
		for (ClimbingHull hull : hulls.values()) {
			hull.build(latitude, longitude, rangeShape, elevations);
			drawn.add(hull);
			if (!hull.isShowingRoutes()) {
				for (long nodeId : hull.getNodeIds()) {
					hiddenNodes.add(nodeId);
				}
			}
		}
		// As on the map, crags are drawn over the areas holding them.
		drawn.sort((first, second) -> Boolean.compare(first.type == GeoNode.NodeTypes.crag,
				second.type == GeoNode.NodeTypes.crag));
	}

	/**
	 * @return the hulls, areas first, as of the last {@link #update}
	 */
	public List<ClimbingHull> getHulls() {
		return drawn;
	}

	/**
	 * @return whether the node is in a hull too small in the view to make out its routes, which
	 * its pin stands in for, as of the last {@link #update}
	 */
	public boolean isHidden(long nodeId) {
		return hiddenNodes.contains(nodeId);
	}

	/**
	 * @return how far east of where the hulls were laid out from the position is, in meters
	 */
	double getEastOfOrigin(double longitude) {
		return (longitude - originLongitude) * ClimbingHull.METERS_PER_DEGREE
				* Math.cos(Math.toRadians(originLatitude));
	}

	/**
	 * @return how far north of where the hulls were laid out from the position is, in meters
	 */
	double getNorthOfOrigin(double latitude) {
		return (latitude - originLatitude) * ClimbingHull.METERS_PER_DEGREE;
	}
}

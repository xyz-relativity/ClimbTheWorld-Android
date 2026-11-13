package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.storage.database.OsmEntity;

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
 * left to the pin of a hull too far to make them out. Only to be used from the main thread.
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
	private final LayoutOrigin origin = new LayoutOrigin();
	private double range;
	private boolean outlines;
	private boolean withElevation;
	private int terrainVersion;
	// When the elevations were last looked up, along with a layout or on their own.
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
	 * Lays the hulls out around the observer again when they changed, the observer moved, or
	 * what is drawn of them changed. When only the terrain loaded more, now and then, they stay
	 * as laid out, and only take their elevations from it again.
	 *
	 * @param range          how far from the observer the outlines are drawn, in meters
	 * @param outlines       whether the outlines are drawn. Hidden, they are not laid out, and
	 *                       the hulls only decide between their routes and their pin.
	 * @param elevations     source of the ground elevation, null to leave it unknown
	 * @param terrainVersion changes whenever the elevations may have changed
	 */
	public void update(double latitude, double longitude, double range, boolean outlines,
	                   TerrainWireframe.ElevationSource elevations, int terrainVersion,
	                   long nowMillis) {
		// Hidden, the outlines have no elevations to follow.
		boolean withElevation = outlines && elevations != null;
		boolean layout = changed || origin.needsLayout(latitude, longitude) || range != this.range
				|| outlines != this.outlines || withElevation != this.withElevation;
		boolean terrainChanged = withElevation && terrainVersion != this.terrainVersion
				&& nowMillis - builtAtMillis >= TERRAIN_REBUILD_DELAY_MS;
		if (!layout && !terrainChanged) {
			return;
		}

		this.terrainVersion = terrainVersion;
		builtAtMillis = nowMillis;
		if (!layout) {
			// The terrain moves no hull: each is cut as it was, and chooses between its routes
			// and its pin as it did, so only the elevations are left to take again.
			for (ClimbingHull hull : drawn) {
				hull.lookUpElevations(elevations);
			}
			return;
		}

		changed = false;
		origin.moveTo(latitude, longitude);
		this.range = range;
		this.outlines = outlines;
		this.withElevation = withElevation;

		Geometry rangeShape = outlines
				? ClimbingHull.GEOMETRY_FACTORY.createPoint(new Coordinate(0, 0))
				.buffer(range, RANGE_QUADRANT_SEGMENTS) : null;
		drawn.clear();
		hiddenNodes.clear();
		for (ClimbingHull hull : hulls.values()) {
			// The range shape has its corners on the circle of the range, so reaches no farther.
			hull.build(latitude, longitude, rangeShape, range, elevations);
			drawn.add(hull);
			if (!hull.isShowingRoutes()) {
				for (long nodeId : hull.getNodeIds()) {
					hiddenNodes.add(nodeId);
				}
			}
		}
		// As on the map, crags are drawn over the areas holding them.
		drawn.sort((first, second) -> Boolean.compare(first.type == OsmEntity.EntityClimbingType.crag,
				second.type == OsmEntity.EntityClimbingType.crag));
	}

	/**
	 * @return the hulls, areas first, as of the last {@link #update}
	 */
	public List<ClimbingHull> getHulls() {
		return drawn;
	}

	/**
	 * @return whether the node is in a hull too far to make out its routes, which its pin stands
	 * in for, as of the last {@link #update}
	 */
	public boolean isHidden(long nodeId) {
		return hiddenNodes.contains(nodeId);
	}

	/** Where the hulls were laid out from, as of the last {@link #update} */
	LayoutOrigin getOrigin() {
		return origin;
	}
}

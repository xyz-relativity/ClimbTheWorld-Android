package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.storage.database.GeoNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The routes mapped as a way around the observer, laid out on the terrain for the AR view. Only
 * to be used from the main thread.
 */
public class RouteLines {
	/**
	 * Elevation of the bottom of a route, where its pin stands.
	 */
	public interface BottomElevation {
		/**
		 * @return the elevation above sea level in meters, or NaN while it is not known
		 */
		double getElevation(GeoNode bottom, double distanceMeters);
	}

	private List<RouteLine> routes = Collections.emptyList();
	private final Set<Long> topIds = new HashSet<>();
	private boolean changed;
	private final LayoutOrigin origin = new LayoutOrigin();
	private int terrainVersion;
	private long builtAtMillis;

	/**
	 * Replaces the routes.
	 *
	 * @return the routes no longer there, whose top pins are to go
	 */
	public List<RouteLine> setRoutes(List<RouteLine> loaded) {
		Set<Long> loadedTops = new HashSet<>();
		for (RouteLine route : loaded) {
			loadedTops.add(route.top.getID());
		}
		List<RouteLine> removed = new ArrayList<>();
		for (RouteLine route : routes) {
			if (!loadedTops.contains(route.top.getID())) {
				removed.add(route);
			}
		}
		routes = loaded;
		topIds.clear();
		topIds.addAll(loadedTops);
		changed = true;
		return removed;
	}

	/**
	 * Lays the routes out on the terrain around the observer again when they changed, the
	 * observer moved, or, now and then, when the terrain loaded more, as the hulls are, see
	 * {@link ClimbingHulls#update}.
	 *
	 * @param ground         source of the ground elevation along the routes and at their top
	 * @param bottoms        source of the elevation of their bottom
	 * @param terrainVersion changes whenever the elevations may have changed
	 */
	public void update(double latitude, double longitude,
	                   TerrainWireframe.ElevationSource ground, BottomElevation bottoms,
	                   int terrainVersion, long nowMillis) {
		boolean moved = origin.needsLayout(latitude, longitude);
		boolean terrainChanged = terrainVersion != this.terrainVersion
				&& nowMillis - builtAtMillis >= ClimbingHulls.TERRAIN_REBUILD_DELAY_MS;
		if (!changed && !moved && !terrainChanged) {
			return;
		}

		changed = false;
		origin.moveTo(latitude, longitude);
		this.terrainVersion = terrainVersion;
		builtAtMillis = nowMillis;
		for (RouteLine route : routes) {
			double distance = Math.hypot(origin.eastOf(route.bottom.decimalLongitude),
					origin.northOf(route.bottom.decimalLatitude));
			route.build(latitude, longitude, ground, bottoms.getElevation(route.bottom, distance));
		}
	}

	public List<RouteLine> getRoutes() {
		return routes;
	}

	/**
	 * @return whether the node is the top of one of the routes, which shows with it
	 */
	public boolean isTop(long nodeId) {
		return topIds.contains(nodeId);
	}

	/** Where the routes were laid out from, as of the last {@link #update} */
	LayoutOrigin getOrigin() {
		return origin;
	}
}

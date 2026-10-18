package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.climbing.RouteWay;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONException;
import org.locationtech.jts.geom.Coordinate;

import java.util.List;
import java.util.Map;

/**
 * A {@link RouteWay} as the AR view draws it: a line on the ground up to a pin at its top, both
 * in the colour of its grade.
 */
public class RouteLine {
	public final GeoNode bottom;
	public final RouteWay.Top top;
	// In the colour of the grade, as the pins of the route.
	public final int color;
	// The way, from the bottom to the top.
	private final List<MapCoordinate> path;
	// As of the last build, around its origin.
	private ClimbingHull.GroundLine line;

	private RouteLine(RouteWay route) {
		this.bottom = route.bottom;
		this.top = route.top;
		this.color = route.color;
		this.path = route.path;
	}

	/**
	 * The route along a climbing=route way, see {@link RouteWay#fromWay}.
	 *
	 * @param nodes the nodes of the way, by ID
	 * @return the route, null when fewer than two of its nodes are known
	 */
	public static RouteLine fromWay(OsmCollectionEntity way, Map<Long, OsmNode> nodes)
			throws JSONException {
		RouteWay route = RouteWay.fromWay(way, nodes);
		return route != null ? new RouteLine(route) : null;
	}

	/**
	 * Lays the route out on the ground around the origin.
	 *
	 * @param ground          source of the ground elevation along the route
	 * @param bottomElevation elevation of the bottom, where its pin stands, NaN while unknown
	 */
	void build(double originLatitude, double originLongitude,
	           TerrainWireframe.ElevationSource ground, double bottomElevation) {
		double metersPerDegreeLongitude =
				ClimbingHull.METERS_PER_DEGREE * Math.cos(Math.toRadians(originLatitude));
		Coordinate[] points = new Coordinate[path.size()];
		for (int index = 0; index < path.size(); index++) {
			MapCoordinate point = path.get(index);
			points[index] = new Coordinate(
					(point.getLongitude() - originLongitude) * metersPerDegreeLongitude,
					(point.getLatitude() - originLatitude) * ClimbingHull.METERS_PER_DEGREE);
		}
		line = ClimbingHull.GroundLine.along(points, originLatitude, originLongitude,
				metersPerDegreeLongitude, ground);
		// The pin stands at the foot of the wall rather than on the slope the terrain smooths the
		// wall into, so the line starts there once it is known.
		if (!Double.isNaN(bottomElevation)) {
			line.elevation[0] = bottomElevation;
		}
	}

	/**
	 * @return the route on the ground, as of the last build, null before
	 */
	ClimbingHull.GroundLine getLine() {
		return line;
	}
}

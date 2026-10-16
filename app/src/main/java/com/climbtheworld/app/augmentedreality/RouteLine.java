package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONException;
import org.json.JSONObject;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A route mapped as a way from its bottom to its top (climbing=route), which the AR view draws as
 * a line on the ground up to a pin at its top, both in the colour of its grade. The grade and the
 * other details of the route are on its bottom, see route_top_note, or else on the way.
 */
public class RouteLine {
	private static final String GRADE_TAG_PREFIX = ClimbingTags.KEY_CLIMBING
			+ ClimbingTags.KEY_SEPARATOR + ClimbingTags.KEY_GRADE + ClimbingTags.KEY_SEPARATOR;

	/**
	 * The top of a route as a POI. It carries the grade of the route, so its pin takes the colour
	 * of it, and leaves the rest to the route.
	 */
	public static final class Top extends GeoNode {
		// The bottom of the route, which holds its details.
		public final GeoNode route;

		Top(JSONObject jsonNodeInfo, GeoNode route) {
			super(jsonNodeInfo);
			this.route = route;
		}
	}

	public final GeoNode bottom;
	public final Top top;
	// In the colour of the grade, as the pins of the route.
	public final int color;
	// The way, from the bottom to the top.
	private final double[] latitudes;
	private final double[] longitudes;
	// As of the last build, around its origin.
	private ClimbingHull.GroundLine line;

	private RouteLine(GeoNode bottom, Top top, int color, double[] latitudes,
	                  double[] longitudes) {
		this.bottom = bottom;
		this.top = top;
		this.color = color;
		this.latitudes = latitudes;
		this.longitudes = longitudes;
	}

	/**
	 * The route along a climbing=route way. It runs from the bottom to the top, which the
	 * route_top tag tells apart when the way was drawn the other way round.
	 *
	 * @param nodes the nodes of the way, by ID
	 * @return the route, null when fewer than two of its nodes are known
	 */
	public static RouteLine fromWay(OsmCollectionEntity way, Map<Long, OsmNode> nodes)
			throws JSONException {
		List<OsmNode> path = new ArrayList<>();
		for (Long nodeId : way.osmNodes) {
			OsmNode node = nodes.get(nodeId);
			if (node != null) {
				path.add(node);
			}
		}
		if (path.size() < 2) {
			return null;
		}
		if (isRouteTop(path.get(0)) && !isRouteTop(path.get(path.size() - 1))) {
			Collections.reverse(path);
		}

		OsmNode bottomNode = path.get(0);
		JSONObject gradeTags = GeoNode.getLevelId(bottomNode.getTags(),
				ClimbingTags.KEY_GRADE_TAG) >= 0 ? bottomNode.getTags() : way.getTags();
		GeoNode bottom = new GeoNode(new JSONObject(bottomNode.jsonNodeInfo.toString()));

		JSONObject topInfo = new JSONObject(path.get(path.size() - 1).jsonNodeInfo.toString());
		JSONObject topTags = topInfo.optJSONObject(ClimbingTags.KEY_TAGS);
		if (topTags == null) {
			topTags = new JSONObject();
			topInfo.put(ClimbingTags.KEY_TAGS, topTags);
		}
		// Where the way gives it away rather than the tag, so it shows as a top all the same.
		topTags.put(ClimbingTags.KEY_CLIMBING, ClimbingTags.VALUE_ROUTE_TOP);
		if (!topTags.has(ClimbingTags.KEY_SPORT)) {
			topTags.put(ClimbingTags.KEY_SPORT, ClimbingTags.VALUE_CLIMBING);
		}
		for (Iterator<String> keys = gradeTags.keys(); keys.hasNext(); ) {
			String key = keys.next();
			if (key.toLowerCase().startsWith(GRADE_TAG_PREFIX)) {
				topTags.put(key, gradeTags.get(key));
			}
		}

		double[] latitudes = new double[path.size()];
		double[] longitudes = new double[path.size()];
		for (int index = 0; index < path.size(); index++) {
			latitudes[index] = path.get(index).decimalLatitude;
			longitudes[index] = path.get(index).decimalLongitude;
		}
		return new RouteLine(bottom, new Top(topInfo, bottom),
				Globals.gradeToColorState(GeoNode.getLevelId(gradeTags,
						ClimbingTags.KEY_GRADE_TAG)).getDefaultColor(),
				latitudes, longitudes);
	}

	private static boolean isRouteTop(OsmNode node) {
		return ClimbingTags.VALUE_ROUTE_TOP.equals(
				node.getTags().optString(ClimbingTags.KEY_CLIMBING));
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
		Coordinate[] points = new Coordinate[latitudes.length];
		for (int index = 0; index < latitudes.length; index++) {
			points[index] = new Coordinate(
					(longitudes[index] - originLongitude) * metersPerDegreeLongitude,
					(latitudes[index] - originLatitude) * ClimbingHull.METERS_PER_DEGREE);
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

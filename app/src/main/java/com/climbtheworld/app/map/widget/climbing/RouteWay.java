package com.climbtheworld.app.map.widget.climbing;

import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A route mapped as a way from its bottom to its top (climbing=route), which the map and the AR
 * view draw as a line up to a pin at its top, both in the colour of its grade. The grade and the
 * other details of the route are on its bottom, see route_top_note, or else on the way.
 */
public final class RouteWay {
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
	// Whether the bottom has a pin of its own, which the top goes with.
	public final boolean bottomPinned;
	public final Top top;
	// In the colour of the grade, as the pins of the route.
	public final int color;
	// The way, from the bottom to the top.
	public final List<MapCoordinate> path;

	private RouteWay(GeoNode bottom, boolean bottomPinned, Top top, int color,
	                 List<MapCoordinate> path) {
		this.bottom = bottom;
		this.bottomPinned = bottomPinned;
		this.top = top;
		this.color = color;
		this.path = path;
	}

	/**
	 * The route along a climbing=route way. It runs from the bottom to the top, which the
	 * route_top tag tells apart when the way was drawn the other way round.
	 *
	 * @param nodes the nodes of the way, by ID
	 * @return the route, null when fewer than two of its nodes are known
	 */
	public static RouteWay fromWay(OsmCollectionEntity way, Map<Long, OsmNode> nodes)
			throws JSONException {
		List<OsmNode> nodePath = new ArrayList<>();
		for (Long nodeId : way.osmNodes) {
			OsmNode node = nodes.get(nodeId);
			if (node != null) {
				nodePath.add(node);
			}
		}
		if (nodePath.size() < 2) {
			return null;
		}
		if (isRouteTop(nodePath.get(0)) && !isRouteTop(nodePath.get(nodePath.size() - 1))) {
			Collections.reverse(nodePath);
		}

		OsmNode bottomNode = nodePath.get(0);
		JSONObject gradeTags = GeoNode.getLevelId(bottomNode.getTags(),
				ClimbingTags.KEY_GRADE_TAG) >= 0 ? bottomNode.getTags() : way.getTags();
		GeoNode bottom = new GeoNode(new JSONObject(bottomNode.jsonNodeInfo.toString()));

		JSONObject topInfo =
				new JSONObject(nodePath.get(nodePath.size() - 1).jsonNodeInfo.toString());
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

		List<MapCoordinate> path = new ArrayList<>();
		for (OsmNode node : nodePath) {
			path.add(new MapCoordinate(node.decimalLatitude, node.decimalLongitude));
		}
		return new RouteWay(bottom, DataManager.hasPin(bottomNode), new Top(topInfo, bottom),
				Globals.gradeToColorState(GeoNode.getLevelId(gradeTags,
						ClimbingTags.KEY_GRADE_TAG)).getDefaultColor(),
				Collections.unmodifiableList(path));
	}

	private static boolean isRouteTop(OsmNode node) {
		return ClimbingTags.VALUE_ROUTE_TOP.equals(
				node.getTags().optString(ClimbingTags.KEY_CLIMBING));
	}
}

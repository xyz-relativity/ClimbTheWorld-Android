package com.climbtheworld.app.map.widget.climbing;

import android.content.Context;

import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A route mapped as a relation (climbing=route), usually of its pitches as climbing=route ways
 * from a route_bottom to a route_top node, with the details of the route on the relation. The
 * map and the AR view show it as a route pin at its start and a pin at its top, both in the
 * colour of its grade. Its pitches show as routes mapped as a way, each in the colour of its own
 * grade, along with the route rather than on their own, and without a top of their own.
 */
public final class RouteRelation {
	/**
	 * The start of the route as a POI: the node it starts from, carrying the details of the
	 * route, so its pin shows the route there and takes its number in the crag.
	 */
	public static final class Start extends RouteStandIn {
		Start(JSONObject jsonNodeInfo, GeoNode route) {
			super(jsonNodeInfo, route);
		}
	}

	// The relation, at its start.
	public final GeoNode route;
	public final Start start;
	public final RouteWay.Top top;
	// In the colour of the grade, as the pins of the route.
	public final int color;
	// The ways among its members: its pitches.
	public final Set<Long> pitchIds;

	private RouteRelation(GeoNode route, Start start, RouteWay.Top top, int color,
	                      Set<Long> pitchIds) {
		this.route = route;
		this.start = start;
		this.top = top;
		this.color = color;
		this.pitchIds = pitchIds;
	}

	/**
	 * @param nodes the nodes of the relation, by ID
	 * @return the route, null for anything but a route relation, or when its start and end are
	 * not known
	 */
	public static RouteRelation fromRelation(OsmCollectionEntity relation,
	                                         Map<Long, OsmNode> nodes) throws JSONException {
		if (relation.osmType != OsmEntity.EntityOsmType.relation
				|| relation.entityClimbingType != OsmEntity.EntityClimbingType.route) {
			return null;
		}
		OsmNode[] ends = findEnds(relation, nodes);
		if (ends == null) {
			return null;
		}

		JSONObject tags = relation.getTags();
		GeoNode route = new GeoNode(new JSONObject(relation.jsonNodeInfo.toString()));
		route.updatePOILocation(ends[0].decimalLatitude, ends[0].decimalLongitude, 0);
		JSONObject startInfo = new JSONObject(ends[0].jsonNodeInfo.toString());
		startInfo.put(ClimbingTags.KEY_TAGS, new JSONObject(tags.toString()));

		Set<Long> pitchIds = new HashSet<>();
		JSONArray members = relation.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS);
		for (int index = 0; members != null && index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member != null && OsmEntity.EntityOsmType.way.name().equals(
					member.optString(ClimbingTags.KEY_TYPE))) {
				pitchIds.add(member.optLong(ClimbingTags.KEY_REF));
			}
		}
		return new RouteRelation(route, new Start(startInfo, route),
				RouteWay.toTop(ends[1], tags, route), RouteWay.gradeColor(tags),
				Collections.unmodifiableSet(pitchIds));
	}

	/**
	 * @param nodes the nodes of the relation, by ID
	 * @return the node the route relation starts from, null when it is not known
	 */
	public static OsmNode findStart(OsmCollectionEntity relation, Map<Long, OsmNode> nodes) {
		OsmNode[] ends = findEnds(relation, nodes);
		return ends != null ? ends[0] : null;
	}

	/**
	 * The route runs from its route_bottom to its route_top. Where they are not tagged, it runs
	 * along its members as listed, as a route way runs along its nodes.
	 *
	 * @return the start and the end of the route, null when they are not known
	 */
	private static OsmNode[] findEnds(OsmCollectionEntity relation, Map<Long, OsmNode> nodes) {
		List<OsmNode> path = new ArrayList<>();
		OsmNode bottom = null;
		OsmNode top = null;
		for (Long nodeId : relation.osmNodes) {
			OsmNode node = nodes.get(nodeId);
			if (node == null) {
				continue;
			}
			path.add(node);
			if (bottom == null && ClimbingTags.VALUE_ROUTE_BOTTOM.equals(
					node.getTags().optString(ClimbingTags.KEY_CLIMBING))) {
				bottom = node;
			}
			if (top == null && RouteWay.isRouteTop(node)) {
				top = node;
			}
		}
		if (path.size() < 2) {
			return null;
		}

		OsmNode first = path.get(0);
		OsmNode last = path.get(path.size() - 1);
		if (bottom == null) {
			bottom = top != null && top.osmID == first.osmID ? last : first;
		}
		if (top == null) {
			top = bottom.osmID == last.osmID ? first : last;
		}
		return bottom.osmID != top.osmID ? new OsmNode[]{bottom, top} : null;
	}

	/**
	 * @return the route relations by the IDs of their pitches
	 */
	public static Map<Long, RouteRelation> byPitch(List<RouteRelation> relations) {
		Map<Long, RouteRelation> result = new HashMap<>();
		for (RouteRelation relation : relations) {
			for (Long pitchId : relation.pitchIds) {
				result.put(pitchId, relation);
			}
		}
		return result;
	}

	/**
	 * @return the route relations within the bounds. Must run off the UI thread.
	 */
	public static List<RouteRelation> load(Context context, MapBounds bounds) {
		DataManager dataManager = new DataManager();
		List<OsmCollectionEntity> relations = new ArrayList<>();
		List<Long> nodeIds = new ArrayList<>();
		for (OsmCollectionEntity collection : dataManager.loadCollectionData(context,
				dataManager.loadCollectionBBox(context, bounds,
						OsmEntity.EntityClimbingType.route)).values()) {
			if (collection.osmType == OsmEntity.EntityOsmType.relation) {
				relations.add(collection);
				nodeIds.addAll(collection.osmNodes);
			}
		}
		if (relations.isEmpty()) {
			return Collections.emptyList();
		}

		Map<Long, OsmNode> nodes = dataManager.loadNodeData(context, nodeIds);
		List<RouteRelation> result = new ArrayList<>();
		for (OsmCollectionEntity relation : relations) {
			try {
				RouteRelation route = fromRelation(relation, nodes);
				if (route != null) {
					result.add(route);
				}
			} catch (JSONException ignore) {
			}
		}
		return result;
	}
}

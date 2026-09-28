package com.climbtheworld.app.map.widget.climbing;

import android.content.Context;

import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts the unique climbing routes contained in an OSM entity, recursively through nested
 * relations, and groups them by climbing style. A route tagged with several styles counts
 * towards each of them. Must run off the UI thread when backed by the database.
 */
public final class ClimbingRouteCounter {

	public interface EntitySource {
		Map<Long, OsmNode> loadNodes(List<Long> ids);

		Map<Long, OsmCollectionEntity> loadCollections(List<Long> ids);
	}

	public static final class RouteSummary {
		private final int routeCount;
		private final Map<GeoNode.ClimbingStyle, Integer> styleCounts;

		private RouteSummary(int routeCount, Map<GeoNode.ClimbingStyle, Integer> styleCounts) {
			this.routeCount = routeCount;
			this.styleCounts = Collections.unmodifiableMap(styleCounts);
		}

		public int getRouteCount() {
			return routeCount;
		}

		/**
		 * Route count per climbing style; styles without routes are absent.
		 */
		public Map<GeoNode.ClimbingStyle, Integer> getStyleCounts() {
			return styleCounts;
		}
	}

	private static final class Accumulator {
		private int routeCount = 0;
		private final Map<GeoNode.ClimbingStyle, Integer> styleCounts =
				new EnumMap<>(GeoNode.ClimbingStyle.class);

		private void addRoute(JSONObject tags) {
			routeCount++;
			for (GeoNode.ClimbingStyle style : GeoNode.getClimbingStyles(tags)) {
				Integer current = styleCounts.get(style);
				styleCounts.put(style, current == null ? 1 : current + 1);
			}
		}

		private RouteSummary build() {
			return new RouteSummary(routeCount, styleCounts);
		}
	}

	private final EntitySource source;

	public ClimbingRouteCounter(EntitySource source) {
		this.source = source;
	}

	public static ClimbingRouteCounter forDatabase(Context context) {
		DataManagerNew dataManager = new DataManagerNew();
		return new ClimbingRouteCounter(new EntitySource() {
			@Override
			public Map<Long, OsmNode> loadNodes(List<Long> ids) {
				return ids.isEmpty() ? Collections.emptyMap()
						: dataManager.loadNodeData(context, ids);
			}

			@Override
			public Map<Long, OsmCollectionEntity> loadCollections(List<Long> ids) {
				return ids.isEmpty() ? Collections.emptyMap()
						: dataManager.loadCollectionData(context, ids);
			}
		});
	}

	public RouteSummary summarize(OsmNode node) {
		Accumulator accumulator = new Accumulator();
		if (node.entityClimbingType == OsmEntity.EntityClimbingType.route) {
			accumulator.addRoute(node.getTags());
		}
		return accumulator.build();
	}

	public RouteSummary summarize(OsmCollectionEntity collection) {
		return summarize(collection, null);
	}

	/**
	 * @param collectionNodes the already loaded {@link OsmCollectionEntity#osmNodes} of the
	 *                        collection, or null to load them from the source.
	 */
	public RouteSummary summarize(OsmCollectionEntity collection,
	                              Map<Long, OsmNode> collectionNodes) {
		Accumulator accumulator = new Accumulator();
		Set<String> visited = new HashSet<>();
		visited.add(entityKey(collection.osmType.name(), collection.osmID));
		if (collection.entityClimbingType == OsmEntity.EntityClimbingType.route) {
			accumulator.addRoute(collection.getTags());
		}

		Map<Long, OsmNode> nodes = collectionNodes != null
				? collectionNodes : source.loadNodes(collection.osmNodes);
		for (OsmNode node : nodes.values()) {
			if (node.entityClimbingType == OsmEntity.EntityClimbingType.route
					&& visited.add(entityKey(OsmEntity.EntityOsmType.node.name(), node.osmID))) {
				accumulator.addRoute(node.getTags());
			}
		}

		addContainedRouteCollections(collection, visited, accumulator);
		return accumulator.build();
	}

	private void addContainedRouteCollections(OsmCollectionEntity parent, Set<String> visited,
	                                          Accumulator accumulator) {
		JSONArray members = parent.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS);
		if (members == null) {
			return;
		}

		List<Long> collectionIds = new ArrayList<>();
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member != null && !OsmEntity.EntityOsmType.node.name()
					.equals(member.optString(ClimbingTags.KEY_TYPE))) {
				collectionIds.add(member.optLong(ClimbingTags.KEY_REF));
			}
		}
		if (collectionIds.isEmpty()) {
			return;
		}

		Map<Long, OsmCollectionEntity> collections = source.loadCollections(collectionIds);
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member == null) {
				continue;
			}
			String memberType = member.optString(ClimbingTags.KEY_TYPE);
			if (OsmEntity.EntityOsmType.node.name().equals(memberType)) {
				continue;
			}
			long memberId = member.optLong(ClimbingTags.KEY_REF);
			if (!visited.add(entityKey(memberType, memberId))) {
				continue;
			}
			OsmCollectionEntity collection = collections.get(memberId);
			if (collection == null || !collection.osmType.name().equals(memberType)) {
				continue;
			}
			if (collection.entityClimbingType == OsmEntity.EntityClimbingType.route) {
				accumulator.addRoute(collection.getTags());
			}
			if (collection.osmType == OsmEntity.EntityOsmType.relation) {
				addContainedRouteCollections(collection, visited, accumulator);
			}
		}
	}

	private static String entityKey(String osmType, long osmId) {
		return osmType + "|" + osmId;
	}
}

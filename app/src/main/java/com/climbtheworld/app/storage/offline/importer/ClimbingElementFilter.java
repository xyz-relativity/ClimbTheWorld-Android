package com.climbtheworld.app.storage.offline.importer;

import com.climbtheworld.app.storage.database.ClimbingFeature;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the offline database keeps of a download: the climbing features {@link ClimbingFeature}
 * accepts and the elements they are drawn with, the nodes of their ways and the members of their
 * relations, at any depth.
 */
final class ClimbingElementFilter {
	private ClimbingElementFilter() {
	}

	/** Takes everything else out of the collections, which may be views of maps. */
	static void retain(Collection<OsmNode> nodes, Collection<OsmCollectionEntity> collections) {
		Map<Long, OsmNode> nodesById = new HashMap<>();
		for (OsmNode node : nodes) {
			nodesById.put(node.osmID, node);
		}
		Map<String, OsmCollectionEntity> collectionsByKey = new HashMap<>();
		for (OsmCollectionEntity collection : collections) {
			collectionsByKey.put(key(collection.osmType.name(), collection.osmID), collection);
		}

		Deque<OsmEntity> pending = new ArrayDeque<>();
		for (OsmNode node : nodes) {
			if (isClimbingFeature(node)) {
				pending.add(node);
			}
		}
		for (OsmCollectionEntity collection : collections) {
			if (isClimbingFeature(collection)) {
				pending.add(collection);
			}
		}

		Set<OsmEntity> kept = Collections.newSetFromMap(new IdentityHashMap<>());
		while (!pending.isEmpty()) {
			OsmEntity entity = pending.pop();
			if (!kept.add(entity) || !(entity instanceof OsmCollectionEntity)) {
				continue;
			}
			OsmCollectionEntity collection = (OsmCollectionEntity) entity;
			if (collection.osmType == OsmEntity.EntityOsmType.way) {
				for (Long nodeId : collection.osmNodes) {
					addIfPresent(pending, nodesById.get(nodeId));
				}
				continue;
			}
			JSONArray members = collection.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS);
			for (int index = 0; members != null && index < members.length(); index++) {
				JSONObject member = members.optJSONObject(index);
				if (member == null) {
					continue;
				}
				String type = member.optString(ClimbingTags.KEY_TYPE);
				long ref = member.optLong(ClimbingTags.KEY_REF);
				addIfPresent(pending, OsmEntity.EntityOsmType.node.name().equals(type)
						? nodesById.get(ref) : collectionsByKey.get(key(type, ref)));
			}
		}

		nodes.removeIf(node -> !kept.contains(node));
		collections.removeIf(collection -> !kept.contains(collection));
	}

	private static boolean isClimbingFeature(OsmEntity entity) {
		return ClimbingFeature.of(entity.jsonNodeInfo) != ClimbingFeature.none;
	}

	private static void addIfPresent(Deque<OsmEntity> pending, OsmEntity entity) {
		if (entity != null) {
			pending.add(entity);
		}
	}

	private static String key(String type, long id) {
		return type + "|" + id;
	}
}

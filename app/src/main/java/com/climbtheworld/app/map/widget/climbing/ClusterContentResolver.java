package com.climbtheworld.app.map.widget.climbing;

import android.content.Context;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves what a map cluster holds: each POI is represented by the outermost relations it
 * belongs to, directly or through nested relations (for example the area holding the crag that
 * holds a route), and stands for itself when it is in no relation. Must run off the UI thread
 * when backed by the database.
 */
public final class ClusterContentResolver {
	// The relation bbox query excludes boxes that only touch the queried one, and a POI can sit on
	// the edge of its relation's box.
	private static final double BOUNDS_PADDING_DEGREES = 0.00001;

	public interface RelationSource {
		/**
		 * @return the relations whose bounding box intersects the bounds.
		 */
		Collection<OsmCollectionEntity> loadRelations(MapBounds bounds);
	}

	public static final class Content {
		/** The outermost relations, each listed once, in the order the POIs reached them. */
		public final List<OsmCollectionEntity> relations;
		/** The POIs that are in no relation. */
		public final List<DisplayableGeoNode> pois;

		private Content(List<OsmCollectionEntity> relations, List<DisplayableGeoNode> pois) {
			this.relations = Collections.unmodifiableList(relations);
			this.pois = Collections.unmodifiableList(pois);
		}
	}

	private final RelationSource source;

	public ClusterContentResolver(RelationSource source) {
		this.source = source;
	}

	public static ClusterContentResolver forDatabase(Context context) {
		DataManagerNew dataManager = new DataManagerNew();
		return new ClusterContentResolver(bounds -> dataManager.loadRelationsBBox(context, bounds));
	}

	public Content resolve(Collection<DisplayableGeoNode> pois) {
		if (pois.isEmpty()) {
			return new Content(new ArrayList<>(), new ArrayList<>());
		}

		Map<String, List<OsmCollectionEntity>> parentsByMember = new HashMap<>();
		for (OsmCollectionEntity relation : source.loadRelations(boundsOf(pois))) {
			if (relation.osmType != OsmEntity.EntityOsmType.relation) {
				continue;
			}
			JSONArray members = relation.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS);
			if (members == null) {
				continue;
			}
			for (int index = 0; index < members.length(); index++) {
				JSONObject member = members.optJSONObject(index);
				if (member == null) {
					continue;
				}
				List<OsmCollectionEntity> parents = parentsByMember.computeIfAbsent(
						entityKey(member.optString(ClimbingTags.KEY_TYPE),
								member.optLong(ClimbingTags.KEY_REF)),
						key -> new ArrayList<>());
				if (!parents.contains(relation)) {
					parents.add(relation);
				}
			}
		}

		Map<String, OsmCollectionEntity> relations = new LinkedHashMap<>();
		List<DisplayableGeoNode> loosePois = new ArrayList<>();
		for (DisplayableGeoNode poi : pois) {
			List<OsmCollectionEntity> outermost = findOutermostRelations(parentsByMember,
					entityKey(poi.geoNode));
			if (outermost.isEmpty()) {
				loosePois.add(poi);
			}
			for (OsmCollectionEntity relation : outermost) {
				relations.putIfAbsent(entityKey(relation), relation);
			}
		}
		return new Content(new ArrayList<>(relations.values()), loosePois);
	}

	/**
	 * Walks up from the entity through the relations listing it as a member, keeping the ones
	 * that are themselves in no relation.
	 */
	private static List<OsmCollectionEntity> findOutermostRelations(
			Map<String, List<OsmCollectionEntity>> parentsByMember, String entityKey) {
		List<OsmCollectionEntity> parents = parentsByMember.get(entityKey);
		if (parents == null) {
			return Collections.emptyList();
		}

		List<OsmCollectionEntity> result = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		Deque<OsmCollectionEntity> pending = new ArrayDeque<>(parents);
		while (!pending.isEmpty()) {
			OsmCollectionEntity relation = pending.pop();
			String key = entityKey(relation);
			if (!visited.add(key)) {
				continue;
			}
			List<OsmCollectionEntity> grandParents = parentsByMember.get(key);
			if (grandParents == null) {
				result.add(relation);
			} else {
				pending.addAll(grandParents);
			}
		}
		// Relations nesting each other in a loop have no outermost one, so the POI is shown
		// through the relation holding it.
		if (result.isEmpty()) {
			result.add(parents.get(0));
		}
		return result;
	}

	private static MapBounds boundsOf(Collection<DisplayableGeoNode> pois) {
		double north = -90;
		double south = 90;
		double east = -180;
		double west = 180;
		for (DisplayableGeoNode poi : pois) {
			north = Math.max(north, poi.geoNode.decimalLatitude);
			south = Math.min(south, poi.geoNode.decimalLatitude);
			east = Math.max(east, poi.geoNode.decimalLongitude);
			west = Math.min(west, poi.geoNode.decimalLongitude);
		}
		return new MapBounds(Math.min(90, north + BOUNDS_PADDING_DEGREES),
				Math.min(180, east + BOUNDS_PADDING_DEGREES),
				Math.max(-90, south - BOUNDS_PADDING_DEGREES),
				Math.max(-180, west - BOUNDS_PADDING_DEGREES));
	}

	private static String entityKey(GeoNode poi) {
		// Gyms mapped as buildings are way POIs.
		return entityKey(poi.jsonNodeInfo.optString(ClimbingTags.KEY_TYPE,
				OsmEntity.EntityOsmType.node.name()), poi.osmID);
	}

	private static String entityKey(OsmCollectionEntity collection) {
		return entityKey(collection.osmType.name(), collection.osmID);
	}

	private static String entityKey(String osmType, long osmId) {
		return osmType + "|" + osmId;
	}
}

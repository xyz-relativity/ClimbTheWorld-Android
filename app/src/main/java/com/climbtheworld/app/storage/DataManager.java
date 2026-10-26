package com.climbtheworld.app.storage;

import android.content.Context;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Vector4d;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class DataManager {
	private static final int SEARCH_RESULTS_LIMIT = 50;
	private static final double PARENT_SEARCH_MARGIN_DEGREES = 0.000001;

	// The nodes pinned on the map and in the A.R. view, and found by a search: the climbing
	// features. Only changes made in the app can leave a node that is none in the database.
	private static final OsmEntity.EntityClimbingType[] PIN_TYPES =
			OsmEntity.EntityClimbingType.features();

	public List<Long> loadCollectionBBox(Context appCompatActivity, MapBounds bBox, OsmEntity.EntityClimbingType ... type) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);
		return loadBBox(bBox, part -> appDB.osmCollectionDao().loadBBox(part.getNorth(),
				part.getEast(), part.getSouth(), part.getWest(), type));
	}

	/** The ids the query finds in the bounds, once for each side of the antimeridian. */
	private static List<Long> loadBBox(MapBounds bBox,
	                                   Function<MapBounds, ? extends Collection<Long>> query) {
		// The query compares longitudes as numbers, so it looks on either side of the antimeridian.
		Set<Long> ids = new LinkedHashSet<>();
		for (MapBounds part : bBox.splitAtAntimeridian()) {
			ids.addAll(query.apply(part));
		}
		return new ArrayList<>(ids);
	}

	/** Relations of a climbing feature whose bounding box intersects the bounds. */
	public List<OsmCollectionEntity> loadRelationsBBox(Context context, MapBounds bounds) {
		List<Long> ids = loadCollectionBBox(context, bounds,
				OsmEntity.EntityClimbingType.features());
		List<OsmCollectionEntity> result = new ArrayList<>();
		if (ids.isEmpty()) {
			return result;
		}
		// Ways can share an id with a relation, so the loaded collections are filtered by type.
		for (OsmCollectionEntity collection : loadCollectionData(context, ids).values()) {
			if (collection.osmType == OsmEntity.EntityOsmType.relation) {
				result.add(collection);
			}
		}
		return result;
	}

	/**
	 * The climbing relations listing the entity as a member. A relation's bounding box covers all
	 * of its members, so it is looked for among the ones covering a point of the entity.
	 */
	public List<OsmCollectionEntity> loadParentRelations(Context context,
	                                                     OsmEntity.EntityOsmType osmType,
	                                                     long osmId, double latitude,
	                                                     double longitude) {
		// Members on the edge of the bounding box only touch it, so the point is widened a bit.
		MapBounds bounds = new MapBounds(latitude + PARENT_SEARCH_MARGIN_DEGREES,
				longitude + PARENT_SEARCH_MARGIN_DEGREES, latitude - PARENT_SEARCH_MARGIN_DEGREES,
				longitude - PARENT_SEARCH_MARGIN_DEGREES);
		List<OsmCollectionEntity> result = new ArrayList<>();
		for (OsmCollectionEntity relation : loadRelationsBBox(context, bounds)) {
			if (RelationMembers.contains(relation.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS),
					osmType.name(), osmId)) {
				result.add(relation);
			}
		}
		return result;
	}

	/** The ISO codes of the countries the entity was downloaded with. */
	public List<String> loadCountries(Context context, OsmEntity.EntityOsmType osmType,
	                                  long osmId) {
		return AppDatabase.getInstance(context).entityCountryDao().findCountries(osmType, osmId);
	}

	public Map<String, OsmCollectionEntity> loadCollectionData(Context appCompatActivity, List<Long> ids) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);
		Map<String, OsmCollectionEntity> result = new HashMap<>();
		List<OsmCollectionEntity> data = appDB.osmCollectionDao().resolveData(ids);

		for (OsmCollectionEntity collection : data) {
			result.put(collectionKey(collection.osmType, collection.osmID), collection);
		}

		return result;
	}

	/**
	 * Named POIs matching the search string: the nodes the map pins, route and gym ways, and
	 * crag/area relations, sorted by name and capped at SEARCH_RESULTS_LIMIT.
	 */
	public List<DisplayableGeoNode> find(Context context, String searchString) {
		AppDatabase appDB = AppDatabase.getInstance(context);
		List<DisplayableGeoNode> result = new ArrayList<>();
		for (OsmNode node : appDB.osmNodeDao().find(searchString, SEARCH_RESULTS_LIMIT, PIN_TYPES)) {
			if (isDisplayablePoi(node)) {
				result.add(toDisplayableNode(node));
			}
		}
		ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(context);
		for (OsmCollectionEntity collection : appDB.osmCollectionDao().findCollections(searchString, SEARCH_RESULTS_LIMIT)) {
			result.add(toDisplayableNode(collection, routeCounter));
		}
		result.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
				a.getGeoNode().getName(), b.getGeoNode().getName()));
		return result.size() > SEARCH_RESULTS_LIMIT
				? new ArrayList<>(result.subList(0, SEARCH_RESULTS_LIMIT)) : result;
	}

	/** The way or relation a search result's GeoNode was built from, or null for a node. */
	public OsmCollectionEntity loadCollection(Context context, GeoNode poi) {
		OsmEntity.EntityOsmType osmType;
		try {
			osmType = OsmEntity.EntityOsmType.valueOf(poi.jsonNodeInfo.optString(ClimbingTags.KEY_TYPE));
		} catch (IllegalArgumentException e) {
			return null;
		}
		if (osmType == OsmEntity.EntityOsmType.node) {
			return null;
		}
		return AppDatabase.getInstance(context).osmCollectionDao().find(osmType, poi.osmID);
	}

	public static String collectionKey(OsmEntity.EntityOsmType osmType, long osmId) {
		return osmType.name() + "|" + osmId;
	}

	public boolean loadDisplayableNodesBBox(Context context, MapBounds bounds,
	                                        Map<Long, DisplayableGeoNode> poiMap) {
		List<Long> ids = loadNodeBBox(context, bounds, PIN_TYPES);
		boolean changed = false;
		for (OsmNode node : loadNodeData(context, ids).values()) {
			if (!isDisplayablePoi(node) || poiMap.containsKey(node.osmID)) {
				continue;
			}
			poiMap.put(node.osmID, toDisplayableNode(node));
			changed = true;
		}
		for (OsmCollectionEntity collection : loadCollectionData(context,
				loadCollectionBBox(context, bounds, OsmEntity.EntityClimbingType.artificial)).values()) {
			if (collection.osmType != OsmEntity.EntityOsmType.way) {
				continue;
			}
			long markerId = -collection.osmID;
			if (poiMap.containsKey(markerId)) {
				continue;
			}
			poiMap.put(markerId, toDisplayableNode(collection));
			changed = true;
		}
		return changed;
	}

	public static DisplayableGeoNode toDisplayableNode(OsmNode node) {
		GeoNode geoNode = new GeoNode(node.jsonNodeInfo);
		geoNode.countryIso = node.countryIso;
		return new DisplayableGeoNode(geoNode);
	}

	static boolean isDisplayablePoi(OsmNode node) {
		return !node.getTags().optString(ClimbingTags.KEY_NAME, "").trim().isEmpty();
	}

	/**
	 * @return whether the node has a pin of its own on the map and in the A.R. view, wherever it
	 * is, see {@link #loadDisplayableNodesBBox}
	 */
	public static boolean hasPin(OsmNode node) {
		return Arrays.asList(PIN_TYPES).contains(node.entityClimbingType)
				&& isDisplayablePoi(node);
	}

	/** The collection's pin without a route count of its own; see ClimbingRouteCounter.pin. */
	public static DisplayableGeoNode toDisplayableNode(OsmCollectionEntity collection) {
		return toDisplayableNode(collection, 0, false);
	}

	/** The collection's pin, with its routes counted; see ClimbingRouteCounter.pin. */
	public static DisplayableGeoNode toDisplayableNode(OsmCollectionEntity collection,
	                                                   ClimbingRouteCounter routeCounter) {
		return toDisplayableNode(collection, routeCounter.summarize(collection));
	}

	/** The collection's pin showing the count of its routes; see ClimbingRouteCounter.pin. */
	public static DisplayableGeoNode toDisplayableNode(OsmCollectionEntity collection,
	                                                   ClimbingRouteCounter.RouteSummary routes) {
		return toDisplayableNode(collection, routes.getMatchingRouteCount(), routes.isFiltered());
	}

	/**
	 * The collection's pin showing the route count, see ClimbingRouteCounter.pin, placed at the
	 * center Overpass gave it, or else in the middle of its bounding box.
	 */
	private static DisplayableGeoNode toDisplayableNode(OsmCollectionEntity collection,
	                                                    int routeCount, boolean filtered) {
		JSONObject center = collection.jsonNodeInfo.optJSONObject("center");
		MapCoordinate location = center != null
				? new MapCoordinate(center.optDouble("lat"), center.optDouble("lon"))
				: collectionCenter(collection);
		return new DisplayableGeoNode(ClimbingRouteCounter.pin(collection, location, routeCount,
				filtered));
	}

	public static MapCoordinate collectionCenter(OsmCollectionEntity collection) {
		double longitude;
		if (collection.bBoxWest <= collection.bBoxEast) {
			longitude = (collection.bBoxWest + collection.bBoxEast) / 2;
		} else {
			longitude = (collection.bBoxWest + collection.bBoxEast + 360) / 2;
			if (longitude > 180) {
				longitude -= 360;
			}
		}
		return new MapCoordinate((collection.bBoxNorth + collection.bBoxSouth) / 2, longitude);
	}

	public boolean loadAround(Context context, Vector4d center, double maxDistance,
	                          Map<Long, DisplayableGeoNode> poiMap) {
		return loadDisplayableNodesBBox(context, computeBoundingBox(center, maxDistance), poiMap);
	}

	/** The box reaching maxDistance meters from the center (latitude in x, longitude in y). */
	public static MapBounds computeBoundingBox(Vector4d center, double maxDistance) {
		double latitudeDelta = Math.toDegrees(maxDistance / GeoUtils.EARTH_RADIUS_M);
		double longitudeDelta = Math.toDegrees(maxDistance /
				(Math.cos(Math.toRadians(center.x)) * GeoUtils.EARTH_RADIUS_M));
		return MapBounds.fromUnwrapped(center.x + latitudeDelta, center.y + longitudeDelta,
				center.x - latitudeDelta, center.y - longitudeDelta);
	}

	public List<Long> loadNodeBBox(Context appCompatActivity, MapBounds bBox, OsmEntity.EntityClimbingType ... type) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);
		return loadBBox(bBox, part -> appDB.osmNodeDao().loadBBox(part.getNorth(),
				part.getEast(), part.getSouth(), part.getWest(), type));
	}

	public Map<Long, OsmNode> loadNodeData(Context appCompatActivity, List<Long> ids) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);
		Map<Long, OsmNode> result = new HashMap<>();
		List<OsmNode> data = appDB.osmNodeDao().resolveNodeData(ids);

		for (OsmNode node: data) {
			result.put(node.osmID, node);
		}

		return result;
	}
}

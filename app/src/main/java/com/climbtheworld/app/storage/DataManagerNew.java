package com.climbtheworld.app.storage;

import android.content.Context;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Vector4d;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataManagerNew {
	public List<Long> loadCollectionBBox(Context appCompatActivity, MapBounds bBox, OsmEntity.EntityClimbingType ... type) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);

		return appDB.osmCollectionDao().loadBBox(bBox.getNorth(), bBox.getEast(), bBox.getSouth(), bBox.getWest(), type);
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

	public List<OsmNode> find(Context context, String searchString) {
		return AppDatabase.getInstance(context).osmNodeDao().find(searchString);
	}

	public static String collectionKey(OsmEntity.EntityOsmType osmType, long osmId) {
		return osmType.name() + "|" + osmId;
	}

	public boolean loadDisplayableNodesBBox(Context context, MapBounds bounds,
	                                        Map<Long, DisplayableGeoNode> poiMap) {
		List<Long> ids = loadNodeBBox(context, bounds,
				OsmEntity.EntityClimbingType.route,
				OsmEntity.EntityClimbingType.crag,
				OsmEntity.EntityClimbingType.area,
				OsmEntity.EntityClimbingType.artificial,
				OsmEntity.EntityClimbingType.others);
		boolean changed = false;
		for (OsmNode node : loadNodeData(context, ids).values()) {
			if (poiMap.containsKey(node.osmID)) {
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

	static DisplayableGeoNode toDisplayableNode(OsmCollectionEntity collection) {
		GeoNode geoNode = new GeoNode(collection.jsonNodeInfo);
		JSONObject center = collection.jsonNodeInfo.optJSONObject("center");
		if (center != null) {
			geoNode.updatePOILocation(center.optDouble("lat"), center.optDouble("lon"), 0);
		} else {
			MapCoordinate centerCoordinate = collectionCenter(collection);
			geoNode.updatePOILocation(centerCoordinate.getLatitude(), centerCoordinate.getLongitude(), 0);
		}
		return new DisplayableGeoNode(geoNode);
	}

	private static MapCoordinate collectionCenter(OsmCollectionEntity collection) {
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
		double latitudeDelta = Math.toDegrees(maxDistance / GeoUtils.EARTH_RADIUS_M);
		double longitudeDelta = Math.toDegrees(maxDistance /
				(Math.cos(Math.toRadians(center.x)) * GeoUtils.EARTH_RADIUS_M));
		return loadDisplayableNodesBBox(context, new MapBounds(center.x + latitudeDelta,
				center.y + longitudeDelta, center.x - latitudeDelta, center.y - longitudeDelta), poiMap);
	}

	public List<Long> loadNodeBBox(Context appCompatActivity, MapBounds bBox, OsmEntity.EntityClimbingType ... type) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);

		return appDB.osmNodeDao().loadBBox(bBox.getNorth(), bBox.getEast(), bBox.getSouth(), bBox.getWest(), type);
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

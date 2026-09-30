package com.climbtheworld.app.storage;

import android.content.Context;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

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
		return changed;
	}

	static DisplayableGeoNode toDisplayableNode(OsmNode node) {
		GeoNode geoNode = new GeoNode(node.jsonNodeInfo);
		geoNode.countryIso = node.countryIso;
		return new DisplayableGeoNode(geoNode);
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

package com.climbtheworld.app.storage;

import android.content.Context;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataManagerNew {
	public void parseOsmJsonString(Context context, String data, String countryIso) throws JSONException {
		final Map<Long, OsmNode> nodeDbCache = new HashMap<>();
		final Map<Long, OsmCollectionEntity> collectionDbCache = new HashMap<>();

		JSONObject jObject = new JSONObject(data);
		JSONArray jArray = jObject.getJSONArray("elements");

		for (int i = 0; i < jArray.length(); i++) {
			JSONObject elementInfo = jArray.getJSONObject(i);

			switch (OsmEntity.EntityOsmType.valueOf(elementInfo.optString(ClimbingTags.KEY_TYPE))) {
				case node:
					parseNode(nodeDbCache, elementInfo, countryIso);
					break;
				case way:
				case relation:
					parseCollection(collectionDbCache, elementInfo);
					break;
			}
		}
		
		computeCache(nodeDbCache, collectionDbCache);
		pushDb(context, true, nodeDbCache, collectionDbCache);
	}

	private void pushDb(Context context, boolean replace, Map<Long, OsmNode> nodeDbCache, Map<Long, OsmCollectionEntity> collectionDbCache) {
		AppDatabase appDB = AppDatabase.getInstance(context);

		if (replace) {
			appDB.osmNodeDao().insertNodesWithReplace(new ArrayList<>(nodeDbCache.values()));
			appDB.osmCollectionDao().insertCollectionWithReplace(new ArrayList<>(collectionDbCache.values()));
		} else {
			appDB.osmNodeDao().insertNodesWithIgnore(new ArrayList<>(nodeDbCache.values()));
			appDB.osmCollectionDao().insertCollectionWithIgnore(new ArrayList<>(collectionDbCache.values()));
		}
	}

	private void computeCache(Map<Long, OsmNode> nodeDbCache, Map<Long, OsmCollectionEntity> collectionEntityCache) {
		for (OsmCollectionEntity collection: collectionEntityCache.values()) {
			collection.computeCache(nodeDbCache, collectionEntityCache);
		}
	}

	private void parseCollection(Map<Long, OsmCollectionEntity> collectionDbCache, JSONObject elementInfo) {
		OsmCollectionEntity result = new OsmCollectionEntity(elementInfo);
		collectionDbCache.put(result.osmID, result);
	}

	private void parseNode(Map<Long, OsmNode> nodeDbCache, JSONObject elementInfo, String countryIso) {
		OsmNode result = new OsmNode(elementInfo);
		result.countryIso = countryIso.toUpperCase();
		nodeDbCache.put(result.osmID, result);
	}

	public List<Long> loadCollectionBBox(Context appCompatActivity, BoundingBox bBox, OsmEntity.EntityClimbingType ... type) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);

		return appDB.osmCollectionDao().loadBBox(bBox.getLatNorth(), bBox.getLonEast(), bBox.getLatSouth(), bBox.getLonWest(), type);
	}

	public Map<Long, OsmCollectionEntity> loadCollectionData(Context appCompatActivity, List<Long> ids) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);
		Map<Long, OsmCollectionEntity> result = new HashMap<>();
		List<OsmCollectionEntity> data = appDB.osmCollectionDao().resolveData(ids);

		for (OsmCollectionEntity node: data) {
			result.put(node.osmID, node);
		}

		return result;
	}

	public List<Long> loadNodeBBox(Context appCompatActivity, BoundingBox bBox, OsmEntity.EntityClimbingType ... type) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);

		return appDB.osmNodeDao().loadBBox(bBox.getLatNorth(), bBox.getLonEast(), bBox.getLatSouth(), bBox.getLonWest(), type);
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

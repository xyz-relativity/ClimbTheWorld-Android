package com.climbtheworld.app.storage;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.database.OsmRelation;
import com.climbtheworld.app.storage.database.OsmWay;

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
		final Map<Long, OsmWay> wayDbCache = new HashMap<>();
		final Map<Long, OsmRelation> relationDbCache = new HashMap<>();

		JSONObject jObject = new JSONObject(data);
		JSONArray jArray = jObject.getJSONArray("elements");

		for (int i = 0; i < jArray.length(); i++) {
			JSONObject elementInfo = jArray.getJSONObject(i);

			switch (elementInfo.optString(ClimbingTags.KEY_TYPE)) {
				case "node":
					parseNode(nodeDbCache, elementInfo, countryIso);
					break;
				case "way":
					parseWay(wayDbCache, elementInfo);
					break;
				case "relation":
					parseRelation(relationDbCache, elementInfo);
					break;
			}
		}
		
		computeCache(nodeDbCache, wayDbCache, relationDbCache);
		pushDb(context, true, nodeDbCache, wayDbCache, relationDbCache);
	}

	private void pushDb(Context context, boolean replace, Map<Long, OsmNode> nodeDbCache, Map<Long, OsmWay> wayDbCache, Map<Long, OsmRelation> relationDbCache) {
		AppDatabase appDB = AppDatabase.getInstance(context);

		if (replace) {
			appDB.osmNodeDao().insertNodesWithReplace(new ArrayList<>(nodeDbCache.values()));
			appDB.osmWayDao().insertWayWithReplace(new ArrayList<>(wayDbCache.values()));
			appDB.osmRelationDao().insertRelationWithReplace(new ArrayList<>(relationDbCache.values()));
		} else {
			appDB.osmNodeDao().insertNodesWithIgnore(new ArrayList<>(nodeDbCache.values()));
			appDB.osmWayDao().insertWayWithIgnore(new ArrayList<>(wayDbCache.values()));
			appDB.osmRelationDao().insertRelationWithIgnore(new ArrayList<>(relationDbCache.values()));
		}
	}

	private void computeCache(Map<Long, OsmNode> nodeDbCache, Map<Long, OsmWay> wayDbCache, Map<Long, OsmRelation> relationDbCache) {
		for (OsmWay way: wayDbCache.values()) {
			way.computeCache(nodeDbCache);
		}

		for (OsmRelation relation: relationDbCache.values()) {
			relation.computeCache(nodeDbCache, wayDbCache, relationDbCache);
		}
	}

	private void parseRelation(Map<Long, OsmRelation> relationDbCache, JSONObject elementInfo) {
		OsmRelation result = new OsmRelation(elementInfo);
		relationDbCache.put(result.osmID, result);
	}

	private void parseWay(Map<Long, OsmWay> wayDbCache, JSONObject elementInfo) {
		OsmWay result = new OsmWay(elementInfo);
		wayDbCache.put(result.osmID, result);
	}

	private void parseNode(Map<Long, OsmNode> nodeDbCache, JSONObject elementInfo, String countryIso) {
		OsmNode result = new OsmNode(elementInfo);
		result.countryIso = countryIso.toUpperCase();
		nodeDbCache.put(result.osmID, result);
	}

	public List<OsmRelation> loadBBox(AppCompatActivity appCompatActivity, BoundingBox bBox, OsmEntity.EntityType type) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);

		return appDB.osmRelationDao().loadBBox(bBox.getLatNorth(), bBox.getLonEast(), bBox.getLatSouth(), bBox.getLonWest(), type);
	}

	public Map<Long, OsmNode> loadNodes(AppCompatActivity appCompatActivity, List<Long> ids) {
		AppDatabase appDB = AppDatabase.getInstance(appCompatActivity);
		Map<Long, OsmNode> result = new HashMap<>();
		List<OsmNode> data = appDB.osmNodeDao().resolveNodes(ids);

		for (OsmNode node: data) {
			result.put(node.osmID, node);
		}

		return result;
	}
}

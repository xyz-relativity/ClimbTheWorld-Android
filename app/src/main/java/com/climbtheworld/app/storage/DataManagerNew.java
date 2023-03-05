package com.climbtheworld.app.storage;

import android.content.Context;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.database.OsmRelation;
import com.climbtheworld.app.storage.database.OsmWay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DataManagerNew {
	private final Map<Long, OsmNode> nodeDbCache = new HashMap<>();
	private final Map<Long, OsmWay> wayDbCache = new HashMap<>();
	private final Map<Long, OsmRelation> relationDbCache = new HashMap<>();

	public void parseOsmJsonString(Context context, String data, String countryIso) throws JSONException {
		JSONObject jObject = new JSONObject(data);
		JSONArray jArray = jObject.getJSONArray("elements");

		for (int i = 0; i < jArray.length(); i++) {
			JSONObject elementInfo = jArray.getJSONObject(i);

			switch (elementInfo.optString(ClimbingTags.KEY_TYPE)) {
				case "node":
					parseNode(elementInfo, countryIso);
					break;
				case "way":
					parseWay(elementInfo);
					break;
				case "relation":
					parseRelation(elementInfo);
					break;
			}
		}
		
		computeCache();
		pushDb(context, true);
	}

	private void pushDb(Context context, boolean replace) {
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

	private void computeCache() {
		for (OsmWay way: wayDbCache.values()) {
			way.computeCache(nodeDbCache);
		}

		for (OsmRelation relation: relationDbCache.values()) {
			relation.computeCache(nodeDbCache, wayDbCache, relationDbCache);
		}
	}

	private void parseRelation(JSONObject elementInfo) {
		OsmRelation result = new OsmRelation(elementInfo);
		relationDbCache.put(result.osmID, result);
	}

	private void parseWay(JSONObject elementInfo) {
		OsmWay result = new OsmWay(elementInfo);
		wayDbCache.put(result.osmID, result);
	}

	private void parseNode(JSONObject elementInfo, String countryIso) {
		OsmNode result = new OsmNode(elementInfo);
		result.countryIso = countryIso.toUpperCase();
		nodeDbCache.put(result.osmID, result);
	}
}

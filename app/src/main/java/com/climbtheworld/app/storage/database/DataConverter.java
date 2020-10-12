package com.climbtheworld.app.storage.database;

import androidx.room.TypeConverter;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by xyz on 2/9/18.
 */

public class DataConverter {

	@TypeConverter
	public JSONObject storedStringToJSONObject(String value) {
		try {
			return new JSONObject(value);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return new JSONObject();
	}

	@TypeConverter
	public String JSONObjectToStoredString(JSONObject cl) {
		return cl.toString();
	}

	@TypeConverter
	public GeoNode.NodeTypes storedStringToNodeType(String value) {
		if (value != null && !value.isEmpty()) {
			return GeoNode.NodeTypes.valueOf(value);
		} else {
			return GeoNode.NodeTypes.route;
		}
	}

	@TypeConverter
	public String nodeTypeToStoredString(GeoNode.NodeTypes cl) {
		return cl.name();
	}
}

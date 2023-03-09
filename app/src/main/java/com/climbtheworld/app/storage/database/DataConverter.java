package com.climbtheworld.app.storage.database;

import androidx.room.TypeConverter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by xyz on 2/9/18.
 */

public class DataConverter {
	private static final String LIST_SEPARATOR = " ";

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

	@TypeConverter
	public List<Long> osmIDStringListToOsmIDList(String value) {
		List<Long> result = new LinkedList<>();

		String trimValue = value.trim();
		if (!trimValue.isEmpty()) {
			for (String item : trimValue.split(LIST_SEPARATOR)) {
				result.add(Long.parseLong(item));
			}
		}

		return result;
	}

	@TypeConverter
	public String osmIDListToOsmIDStringList(List<Long> nodes) {
		StringBuilder result = new StringBuilder(LIST_SEPARATOR);

		for (Long item: nodes) {
			result.append(item).append(LIST_SEPARATOR);
		}

		return result.toString();
	}
}

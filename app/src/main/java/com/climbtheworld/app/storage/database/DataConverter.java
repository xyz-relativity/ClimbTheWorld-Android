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
		if (value == null) {
			return null;
		}
		try {
			return new JSONObject(value);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return new JSONObject();
	}

	@TypeConverter
	public String JSONObjectToStoredString(JSONObject cl) {
		return cl == null ? null : cl.toString();
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

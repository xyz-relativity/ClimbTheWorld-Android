package com.climbtheworld.app.storage.upload;

import com.climbtheworld.app.storage.database.ClimbingTags;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** OSM elements in the form the app stores them. */
public final class OsmElements {
	/** What the importer keeps of an element; the API's metadata, like version and user, is not. */
	private static final String[] STORED_KEYS = {ClimbingTags.KEY_TYPE, ClimbingTags.KEY_ID,
			ClimbingTags.KEY_LAT, ClimbingTags.KEY_LON, ClimbingTags.KEY_TAGS,
			ClimbingTags.KEY_NODES, ClimbingTags.KEY_MEMBERS};

	private OsmElements() {
	}

	/** A copy holding only what the app stores of an element. */
	public static JSONObject storable(JSONObject element) {
		return only(copy(element), STORED_KEYS);
	}

	/** A deep copy, so that changes to one do not show in the other. */
	public static JSONObject copy(JSONObject element) {
		try {
			return new JSONObject(element.toString());
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The element's values of the keys it has, shared with it. */
	public static JSONObject only(JSONObject element, String... keys) {
		JSONObject result = new JSONObject();
		try {
			for (String key : keys) {
				if (element.has(key)) {
					result.put(key, element.get(key));
				}
			}
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		return result;
	}

	/** The element's tags, an empty object for none. */
	public static JSONObject tags(JSONObject element) {
		JSONObject tags = element.optJSONObject(ClimbingTags.KEY_TAGS);
		return tags == null ? new JSONObject() : tags;
	}

	/** The keys of the tags, in their order. */
	public static List<String> keys(JSONObject tags) {
		List<String> result = new ArrayList<>();
		Iterator<String> iterator = tags.keys();
		while (iterator.hasNext()) {
			result.add(iterator.next());
		}
		return result;
	}

	/** The tags' values as text, as they are uploaded; empty for no tags. */
	public static Map<String, String> tagMap(JSONObject tags) {
		Map<String, String> result = new HashMap<>();
		if (tags == null) {
			return result;
		}
		for (String key : keys(tags)) {
			result.put(key, tags.optString(key));
		}
		return result;
	}

	/** @return the tag's value, or null for a tag the element does not have */
	public static String value(JSONObject tags, String key) {
		return tags.has(key) ? tags.optString(key) : null;
	}
}

package com.climbtheworld.app.storage.upload;

import com.climbtheworld.app.storage.database.ClimbingTags;

import org.json.JSONException;
import org.json.JSONObject;

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
		JSONObject result = new JSONObject();
		try {
			JSONObject copy = new JSONObject(element.toString());
			for (String key : STORED_KEYS) {
				if (copy.has(key)) {
					result.put(key, copy.get(key));
				}
			}
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		return result;
	}
}

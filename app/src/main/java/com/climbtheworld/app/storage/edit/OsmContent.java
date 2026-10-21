package com.climbtheworld.app.storage.edit;

import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.upload.OsmElements;

import org.json.JSONArray;
import org.json.JSONObject;

/** Compares two versions of an OSM element by what would be uploaded: tags, position, nodes and members. */
public final class OsmContent {
	/** Well below the 7 decimals OSM stores coordinates with. */
	private static final double COORDINATE_TOLERANCE = 1e-9;

	private OsmContent() {
	}

	public static boolean sameContent(JSONObject first, JSONObject second) {
		return sameTags(first.optJSONObject(ClimbingTags.KEY_TAGS),
				second.optJSONObject(ClimbingTags.KEY_TAGS))
				&& sameCoordinate(first, second, ClimbingTags.KEY_LAT)
				&& sameCoordinate(first, second, ClimbingTags.KEY_LON)
				&& sameNodes(first.optJSONArray(ClimbingTags.KEY_NODES),
				second.optJSONArray(ClimbingTags.KEY_NODES))
				&& sameMembers(first.optJSONArray(ClimbingTags.KEY_MEMBERS),
				second.optJSONArray(ClimbingTags.KEY_MEMBERS));
	}

	static boolean sameTags(JSONObject first, JSONObject second) {
		return OsmElements.tagMap(first).equals(OsmElements.tagMap(second));
	}

	private static boolean sameCoordinate(JSONObject first, JSONObject second, String key) {
		if (first.has(key) != second.has(key)) {
			return false;
		}
		return !first.has(key)
				|| Math.abs(first.optDouble(key) - second.optDouble(key)) <= COORDINATE_TOLERANCE;
	}

	private static boolean sameNodes(JSONArray first, JSONArray second) {
		int length = length(first);
		if (length != length(second)) {
			return false;
		}
		for (int index = 0; index < length; index++) {
			if (first.optLong(index) != second.optLong(index)) {
				return false;
			}
		}
		return true;
	}

	private static boolean sameMembers(JSONArray first, JSONArray second) {
		int length = length(first);
		if (length != length(second)) {
			return false;
		}
		for (int index = 0; index < length; index++) {
			JSONObject firstMember = first.optJSONObject(index);
			JSONObject secondMember = second.optJSONObject(index);
			if (firstMember == null || secondMember == null) {
				if (firstMember != secondMember) {
					return false;
				}
				continue;
			}
			if (firstMember.optLong(ClimbingTags.KEY_REF) != secondMember.optLong(ClimbingTags.KEY_REF)
					|| !firstMember.optString(ClimbingTags.KEY_TYPE)
					.equals(secondMember.optString(ClimbingTags.KEY_TYPE))
					|| !firstMember.optString(ClimbingTags.KEY_ROLE)
					.equals(secondMember.optString(ClimbingTags.KEY_ROLE))) {
				return false;
			}
		}
		return true;
	}

	private static int length(JSONArray array) {
		return array == null ? 0 : array.length();
	}
}

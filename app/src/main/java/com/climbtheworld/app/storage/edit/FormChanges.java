package com.climbtheworld.app.storage.edit;

import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.upload.OsmElements;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The editor form writes every field it shows, its own way: grades also in the system picked for
 * them, coordinates rounded. So that only what the user changed reaches OSM, an edit is the
 * element as loaded plus the differences between what the untouched form writes and what the form
 * writes now.
 *
 * <p>Tags the form writes as one value go together: changing the grade writes it in every system
 * the element is graded in, so no stale grade is left beside the new one. One addition is made
 * without a change: a grade without its value in the system the form saves it in gets it.
 */
public final class FormChanges {
	private static final String GRADE_PREFIX = ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR
			+ ClimbingTags.KEY_GRADE + ClimbingTags.KEY_SEPARATOR;
	private static final double COORDINATE_TOLERANCE = 1e-9;

	private FormChanges() {
	}

	/**
	 * @param original  the element before editing
	 * @param untouched what the form writes when nothing was changed in it
	 * @param edited    what the form writes now
	 * @return a copy of original with the user's changes
	 */
	public static JSONObject resolve(JSONObject original, JSONObject untouched, JSONObject edited) {
		JSONObject result = OsmElements.copy(original);
		try {
			result.put(ClimbingTags.KEY_TAGS, resolveTags(
					OsmElements.tagMap(OsmElements.tags(original)),
					OsmElements.tagMap(OsmElements.tags(untouched)),
					OsmElements.tagMap(OsmElements.tags(edited))));
			if (moved(untouched, edited)) {
				result.put(ClimbingTags.KEY_LAT, edited.optDouble(ClimbingTags.KEY_LAT));
				result.put(ClimbingTags.KEY_LON, edited.optDouble(ClimbingTags.KEY_LON));
			}
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		return result;
	}

	private static JSONObject resolveTags(Map<String, String> original, Map<String, String> untouched,
	                                      Map<String, String> edited) throws JSONException {
		Map<String, String> result = new HashMap<>(original);
		Set<String> groups = new LinkedHashSet<>();
		for (String key : untouched.keySet()) {
			groups.add(group(key));
		}
		for (String key : edited.keySet()) {
			groups.add(group(key));
		}
		for (String group : groups) {
			Map<String, String> editedGroup = inGroup(edited, group);
			if (Objects.equals(inGroup(untouched, group), editedGroup)) {
				if (group.startsWith(GRADE_PREFIX)) {
					// The form only adds the grade in the system it saves in; the grades as loaded stay.
					for (Map.Entry<String, String> tag : editedGroup.entrySet()) {
						result.putIfAbsent(tag.getKey(), tag.getValue());
					}
				}
				continue;
			}
			result.keySet().removeIf(key -> group(key).equals(group));
			result.putAll(editedGroup);
		}

		JSONObject tags = new JSONObject();
		for (Map.Entry<String, String> tag : result.entrySet()) {
			tags.put(tag.getKey(), tag.getValue());
		}
		return tags;
	}

	/** The tags the form writes as one value share a group; any other tag is a group alone. */
	static String group(String key) {
		if (key.equals(ClimbingTags.KEY_WEBSITE) || key.equals(ClimbingTags.KEY_CONTACT_WEBSITE)) {
			return ClimbingTags.KEY_WEBSITE;
		}
		if (key.equals(ClimbingTags.KEY_PHONE) || key.equals(ClimbingTags.KEY_CONTACT_PHONE)) {
			return ClimbingTags.KEY_PHONE;
		}
		if (key.startsWith(GRADE_PREFIX)) {
			// climbing:grade:<system>[:min|:max|:mean], grouped by what follows the system.
			String[] parts = key.split(ClimbingTags.KEY_SEPARATOR, 4);
			return GRADE_PREFIX + "*" + (parts.length > 3 ? ClimbingTags.KEY_SEPARATOR + parts[3] : "");
		}
		return key;
	}

	private static Map<String, String> inGroup(Map<String, String> tags, String group) {
		Map<String, String> result = new HashMap<>();
		for (Map.Entry<String, String> tag : tags.entrySet()) {
			if (group(tag.getKey()).equals(group)) {
				result.put(tag.getKey(), tag.getValue());
			}
		}
		return result;
	}

	private static boolean moved(JSONObject untouched, JSONObject edited) {
		return Math.abs(untouched.optDouble(ClimbingTags.KEY_LAT, 0)
				- edited.optDouble(ClimbingTags.KEY_LAT, 0)) > COORDINATE_TOLERANCE
				|| Math.abs(untouched.optDouble(ClimbingTags.KEY_LON, 0)
				- edited.optDouble(ClimbingTags.KEY_LON, 0)) > COORDINATE_TOLERANCE;
	}
}

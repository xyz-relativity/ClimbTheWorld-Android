package com.climbtheworld.app.storage.upload;

import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.edit.OsmContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** What a change made to an element, for listing it before upload. */
public final class ChangeSummary {
	/** The keys of the added, changed and removed tags, sorted. */
	public final List<String> tags;
	public final boolean moved;
	/** The members of a relation, or the nodes of a way, changed. */
	public final boolean members;

	private ChangeSummary(List<String> tags, boolean moved, boolean members) {
		this.tags = Collections.unmodifiableList(tags);
		this.moved = moved;
		this.members = members;
	}

	public static ChangeSummary of(JSONObject base, JSONObject local) {
		JSONObject baseTags = tags(base);
		JSONObject localTags = tags(local);
		TreeSet<String> keys = new TreeSet<>();
		addKeys(keys, baseTags);
		addKeys(keys, localTags);
		List<String> changed = new ArrayList<>();
		for (String key : keys) {
			if (!Objects.equals(value(baseTags, key), value(localTags, key))) {
				changed.add(key);
			}
		}
		return new ChangeSummary(changed,
				!OsmContent.sameContent(only(base, ClimbingTags.KEY_LAT, ClimbingTags.KEY_LON),
						only(local, ClimbingTags.KEY_LAT, ClimbingTags.KEY_LON)),
				!OsmContent.sameContent(only(base, ClimbingTags.KEY_MEMBERS, ClimbingTags.KEY_NODES),
						only(local, ClimbingTags.KEY_MEMBERS, ClimbingTags.KEY_NODES)));
	}

	private static JSONObject tags(JSONObject element) {
		JSONObject tags = element.optJSONObject(ClimbingTags.KEY_TAGS);
		return tags == null ? new JSONObject() : tags;
	}

	private static JSONObject only(JSONObject element, String... keys) {
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

	private static void addKeys(TreeSet<String> keys, JSONObject tags) {
		Iterator<String> iterator = tags.keys();
		while (iterator.hasNext()) {
			keys.add(iterator.next());
		}
	}

	private static String value(JSONObject tags, String key) {
		return tags.has(key) ? tags.optString(key) : null;
	}
}

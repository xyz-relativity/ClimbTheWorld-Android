package com.climbtheworld.app.storage.upload;

import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.edit.OsmContent;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
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
		JSONObject baseTags = OsmElements.tags(base);
		JSONObject localTags = OsmElements.tags(local);
		TreeSet<String> keys = new TreeSet<>();
		keys.addAll(OsmElements.keys(baseTags));
		keys.addAll(OsmElements.keys(localTags));
		List<String> changed = new ArrayList<>();
		for (String key : keys) {
			if (!Objects.equals(OsmElements.value(baseTags, key), OsmElements.value(localTags, key))) {
				changed.add(key);
			}
		}
		return new ChangeSummary(changed,
				!OsmContent.sameContent(OsmElements.only(base, ClimbingTags.KEY_LAT, ClimbingTags.KEY_LON),
						OsmElements.only(local, ClimbingTags.KEY_LAT, ClimbingTags.KEY_LON)),
				!OsmContent.sameContent(OsmElements.only(base, ClimbingTags.KEY_MEMBERS, ClimbingTags.KEY_NODES),
						OsmElements.only(local, ClimbingTags.KEY_MEMBERS, ClimbingTags.KEY_NODES)));
	}
}

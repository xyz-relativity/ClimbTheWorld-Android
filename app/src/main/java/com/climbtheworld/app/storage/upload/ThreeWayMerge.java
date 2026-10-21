package com.climbtheworld.app.storage.upload;

import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.edit.OsmContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Puts a change made in the app together with what others changed on OSM since the download.
 * Each tag, the position and the member list stand on their own: whichever side changed one
 * wins, and when both changed it differently it is a conflict, where the app's value is kept
 * until the user decides.
 */
public final class ThreeWayMerge {
	/** A conflict on the position of a node. */
	public static final String POSITION = "position";
	/** A conflict on the members of a relation, or the nodes of a way. */
	public static final String MEMBERS = "members";
	/** The element was deleted on OSM. */
	public static final String DELETED = "deleted";

	private static final String KEY_VISIBLE = "visible";

	private ThreeWayMerge() {
	}

	public static final class Result {
		/** The change with the other side's changes; the app's values where they conflict. */
		public final JSONObject merged;
		/** Tag keys, POSITION, MEMBERS or DELETED; empty when the merge is clean. */
		public final List<String> conflicts;

		Result(JSONObject merged, List<String> conflicts) {
			this.merged = merged;
			this.conflicts = Collections.unmodifiableList(conflicts);
		}
	}

	/**
	 * @param base   the downloaded version the change started from
	 * @param local  the element as changed in the app
	 * @param server the element as the OSM API has it now
	 */
	public static Result merge(JSONObject base, JSONObject local, JSONObject server) {
		JSONObject merged = OsmElements.storable(local);
		List<String> conflicts = new ArrayList<>();
		if (isDeleted(server)) {
			conflicts.add(DELETED);
			return new Result(merged, conflicts);
		}
		try {
			merged.put(ClimbingTags.KEY_TAGS, mergeTags(base.optJSONObject(ClimbingTags.KEY_TAGS),
					local.optJSONObject(ClimbingTags.KEY_TAGS),
					server.optJSONObject(ClimbingTags.KEY_TAGS), conflicts));

			if (local.has(ClimbingTags.KEY_LAT)) {
				JSONObject position = pick(OsmElements.only(base, ClimbingTags.KEY_LAT, ClimbingTags.KEY_LON),
						OsmElements.only(local, ClimbingTags.KEY_LAT, ClimbingTags.KEY_LON),
						OsmElements.only(server, ClimbingTags.KEY_LAT, ClimbingTags.KEY_LON), POSITION, conflicts);
				merged.put(ClimbingTags.KEY_LAT, position.opt(ClimbingTags.KEY_LAT));
				merged.put(ClimbingTags.KEY_LON, position.opt(ClimbingTags.KEY_LON));
			}
			for (String listKey : new String[]{ClimbingTags.KEY_MEMBERS, ClimbingTags.KEY_NODES}) {
				if (local.has(listKey)) {
					merged.put(listKey, pick(OsmElements.only(base, listKey), OsmElements.only(local, listKey),
							OsmElements.only(server, listKey), MEMBERS, conflicts).opt(listKey));
				}
			}
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		return new Result(merged, conflicts);
	}

	/** For a deleted element: whether OSM still has the version it was deleted from. */
	public static boolean unchangedSince(JSONObject base, JSONObject server) {
		return !isDeleted(server) && OsmContent.sameContent(base, server);
	}

	public static boolean isDeleted(JSONObject server) {
		return !server.optBoolean(KEY_VISIBLE, true);
	}

	private static JSONObject mergeTags(JSONObject base, JSONObject local, JSONObject server,
	                                    List<String> conflicts) throws JSONException {
		base = base == null ? new JSONObject() : base;
		local = local == null ? new JSONObject() : local;
		server = server == null ? new JSONObject() : server;
		TreeSet<String> keys = new TreeSet<>();
		keys.addAll(OsmElements.keys(base));
		keys.addAll(OsmElements.keys(local));
		keys.addAll(OsmElements.keys(server));

		JSONObject result = new JSONObject();
		for (String key : keys) {
			String baseValue = OsmElements.value(base, key);
			String localValue = OsmElements.value(local, key);
			String serverValue = OsmElements.value(server, key);
			String chosen;
			if (Objects.equals(localValue, baseValue)) {
				chosen = serverValue;
			} else if (Objects.equals(serverValue, baseValue) || Objects.equals(serverValue, localValue)) {
				chosen = localValue;
			} else {
				conflicts.add(key);
				chosen = localValue;
			}
			if (chosen != null) {
				result.put(key, chosen);
			}
		}
		return result;
	}

	/** The side that changed the part, compared with OsmContent; the app's on a conflict. */
	private static JSONObject pick(JSONObject base, JSONObject local, JSONObject server,
	                               String conflict, List<String> conflicts) {
		if (OsmContent.sameContent(local, base)) {
			return server;
		}
		if (!OsmContent.sameContent(server, base) && !OsmContent.sameContent(server, local)) {
			conflicts.add(conflict);
		}
		return local;
	}
}

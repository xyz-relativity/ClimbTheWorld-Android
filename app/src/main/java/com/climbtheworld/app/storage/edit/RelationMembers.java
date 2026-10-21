package com.climbtheworld.app.storage.edit;

import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Operations on a relation's OSM "members" array, each member being {type, ref, role}. The array
 * is changed in place, and members are only moved or dropped, never rebuilt, so roles and members
 * that are not downloaded are kept.
 */
public final class RelationMembers {
	private RelationMembers() {
	}

	/** The members of the element that are objects, in order; none for an element without. */
	public static List<JSONObject> of(JSONObject element) {
		return list(element.optJSONArray(ClimbingTags.KEY_MEMBERS));
	}

	/** The members of the array that are objects, in order; none for null. */
	public static List<JSONObject> list(JSONArray members) {
		List<JSONObject> result = new ArrayList<>();
		for (int index = 0; members != null && index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member != null) {
				result.add(member);
			}
		}
		return result;
	}

	public static long ref(JSONObject member) {
		return member.optLong(ClimbingTags.KEY_REF);
	}

	/** @return the member's type, or null for one that is no node, way or relation */
	public static OsmEntity.EntityOsmType osmType(JSONObject member) {
		try {
			return OsmEntity.EntityOsmType.valueOf(member.optString(ClimbingTags.KEY_TYPE));
		} catch (IllegalArgumentException unknownType) {
			return null;
		}
	}

	/** The ids of the members that are nodes, in order. */
	public static List<Long> nodeRefs(List<JSONObject> members) {
		List<Long> result = new ArrayList<>();
		for (JSONObject member : members) {
			if (osmType(member) == OsmEntity.EntityOsmType.node) {
				result.add(ref(member));
			}
		}
		return result;
	}

	/** The ids of the members that are ways or relations, in order. */
	public static List<Long> collectionRefs(List<JSONObject> members) {
		List<Long> result = new ArrayList<>();
		for (JSONObject member : members) {
			if (osmType(member) != OsmEntity.EntityOsmType.node) {
				result.add(ref(member));
			}
		}
		return result;
	}

	public static JSONObject member(String type, long ref, String role) {
		JSONObject result = new JSONObject();
		try {
			result.put(ClimbingTags.KEY_TYPE, type);
			result.put(ClimbingTags.KEY_REF, ref);
			result.put(ClimbingTags.KEY_ROLE, role == null ? "" : role);
		} catch (JSONException e) {
			throw new IllegalArgumentException(e);
		}
		return result;
	}

	public static void append(JSONArray members, String type, long ref, String role) {
		members.put(member(type, ref, role));
	}

	public static boolean contains(JSONArray members, String type, long ref) {
		return indexOf(members, type, ref) >= 0;
	}

	/** @return the position of the member's first occurrence, or -1. */
	public static int indexOf(JSONArray members, String type, long ref) {
		for (int index = 0; members != null && index < members.length(); index++) {
			if (isMember(members.optJSONObject(index), type, ref)) {
				return index;
			}
		}
		return -1;
	}

	/** Removes every occurrence of the member. @return how many were removed. */
	public static int removeAll(JSONArray members, String type, long ref) {
		int removed = 0;
		for (int index = members.length() - 1; index >= 0; index--) {
			if (isMember(members.optJSONObject(index), type, ref)) {
				members.remove(index);
				removed++;
			}
		}
		return removed;
	}

	/** Moves the member at from to position to, shifting the members in between. */
	public static void move(JSONArray members, int from, int to) {
		if (from < 0 || from >= members.length() || to < 0 || to >= members.length()) {
			throw new IndexOutOfBoundsException("Cannot move member " + from + " to " + to
					+ " of " + members.length());
		}
		try {
			Object moved = members.get(from);
			int step = from < to ? 1 : -1;
			for (int index = from; index != to; index += step) {
				members.put(index, members.get(index + step));
			}
			members.put(to, moved);
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Points the member's references at a new id, used once a placeholder id gets its OSM id. */
	public static int replaceRef(JSONArray members, String type, long oldRef, long newRef) {
		int replaced = 0;
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (isMember(member, type, oldRef)) {
				try {
					member.put(ClimbingTags.KEY_REF, newRef);
				} catch (JSONException e) {
					throw new IllegalStateException(e);
				}
				replaced++;
			}
		}
		return replaced;
	}

	/** Identifies a member by its type and id, as in "node|42". */
	public static String key(String type, long ref) {
		return type + "|" + ref;
	}

	/** @return the key of the member, see key(String, long). */
	public static String key(JSONObject member) {
		return key(member.optString(ClimbingTags.KEY_TYPE), member.optLong(ClimbingTags.KEY_REF));
	}

	/** The members' ids in order, whatever their type. */
	public static List<Long> refs(JSONArray members) {
		List<Long> result = new ArrayList<>();
		for (JSONObject member : list(members)) {
			result.add(ref(member));
		}
		return result;
	}

	private static boolean isMember(JSONObject member, String type, long ref) {
		return member != null && member.optLong(ClimbingTags.KEY_REF) == ref
				&& type.equals(member.optString(ClimbingTags.KEY_TYPE));
	}
}

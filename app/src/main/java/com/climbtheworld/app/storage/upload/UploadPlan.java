package com.climbtheworld.app.storage.upload;

import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.edit.RelationMembers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which changes go up together, and in what order. A selected change brings along what OSM needs
 * with it: the new elements a relation lists, and the relations an element was taken out of
 * before being deleted, as OSM refuses to delete an element a relation still lists. Then new
 * elements come before the relations listing them, and relations are deleted before what they
 * listed.
 */
public final class UploadPlan {
	private static final String NODE = "node";
	private static final String RELATION = "relation";

	private UploadPlan() {
	}

	/**
	 * @param all      every pending change
	 * @param selected the keys of the changes to upload, see PendingChange.key
	 * @return the changes to upload, in upload order
	 */
	public static List<PendingChange> plan(List<PendingChange> all, Set<String> selected) {
		Map<String, PendingChange> byKey = new LinkedHashMap<>();
		for (PendingChange change : all) {
			byKey.put(change.key(), change);
		}

		Set<String> included = new LinkedHashSet<>();
		Deque<String> pending = new ArrayDeque<>();
		for (String key : selected) {
			if (byKey.containsKey(key) && included.add(key)) {
				pending.push(key);
			}
		}
		while (!pending.isEmpty()) {
			for (String dependency : dependencies(byKey.get(pending.pop()), all)) {
				if (byKey.containsKey(dependency) && included.add(dependency)) {
					pending.push(dependency);
				}
			}
		}

		List<PendingChange> creates = new ArrayList<>();
		List<PendingChange> modifies = new ArrayList<>();
		List<PendingChange> deletes = new ArrayList<>();
		for (String key : included) {
			PendingChange change = byKey.get(key);
			switch (change.action) {
				case create:
					creates.add(change);
					break;
				case modify:
					modifies.add(change);
					break;
				default:
					deletes.add(change);
			}
		}

		List<PendingChange> result = new ArrayList<>();
		// Members first: a new relation may list new nodes and new relations.
		result.addAll(membersFirst(creates));
		result.addAll(nodesFirst(modifies));
		// Parents first: a relation goes before the relations and nodes it listed.
		List<PendingChange> deleteOrder = membersFirst(deletes);
		for (int index = deleteOrder.size() - 1; index >= 0; index--) {
			result.add(deleteOrder.get(index));
		}
		return result;
	}

	private static List<String> dependencies(PendingChange change, List<PendingChange> all) {
		List<String> result = new ArrayList<>();
		if (change.action != PendingChange.Action.delete) {
			for (String member : memberKeys(change.local)) {
				if (refOf(member) < 0) {
					result.add(member);
				}
			}
			return result;
		}
		String deleted = change.key();
		for (PendingChange other : all) {
			if (other != change && other.base != null && memberKeys(other.base).contains(deleted)) {
				result.add(other.key());
			}
		}
		return result;
	}

	/** Relations after the relations they list, nodes before all of them. */
	private static List<PendingChange> membersFirst(List<PendingChange> changes) {
		Map<String, PendingChange> byKey = new LinkedHashMap<>();
		for (PendingChange change : changes) {
			byKey.put(change.key(), change);
		}
		List<PendingChange> result = new ArrayList<>();
		Set<String> placed = new HashSet<>();
		for (PendingChange change : nodesFirst(changes)) {
			place(change, byKey, placed, new HashSet<>(), result);
		}
		return result;
	}

	private static void place(PendingChange change, Map<String, PendingChange> byKey,
	                          Set<String> placed, Set<String> visiting, List<PendingChange> result) {
		if (placed.contains(change.key()) || !visiting.add(change.key())) {
			return;
		}
		JSONObject listing = change.action == PendingChange.Action.delete ? change.base : change.local;
		for (String member : memberKeys(listing)) {
			PendingChange memberChange = byKey.get(member);
			if (memberChange != null) {
				place(memberChange, byKey, placed, visiting, result);
			}
		}
		placed.add(change.key());
		result.add(change);
	}

	private static List<PendingChange> nodesFirst(List<PendingChange> changes) {
		List<PendingChange> result = new ArrayList<>();
		for (PendingChange change : changes) {
			if (NODE.equals(change.type)) {
				result.add(change);
			}
		}
		for (PendingChange change : changes) {
			if (!NODE.equals(change.type)) {
				result.add(change);
			}
		}
		return result;
	}

	private static List<String> memberKeys(JSONObject element) {
		List<String> result = new ArrayList<>();
		if (element == null || !RELATION.equals(element.optString(ClimbingTags.KEY_TYPE))) {
			return result;
		}
		JSONArray members = element.optJSONArray(ClimbingTags.KEY_MEMBERS);
		for (int index = 0; members != null && index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member != null) {
				result.add(RelationMembers.key(member));
			}
		}
		return result;
	}

	private static long refOf(String key) {
		return Long.parseLong(key.substring(key.indexOf('|') + 1));
	}
}

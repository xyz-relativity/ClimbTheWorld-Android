package com.climbtheworld.app.map.editor;

import com.climbtheworld.app.storage.edit.RelationMembers;

import org.json.JSONArray;

/**
 * The members of a relation being edited, as the edit has them so far, in the relation's members
 * array, which the editor saves. RelationMemberList shows them.
 * <p>
 * An element tapped on the map joins right at the tap, never once it is loaded for its row, like
 * its pin: a save coming before that would leave it out without a word. Whether it can join is
 * known at the tap, from the map: its kind, and whether the relation lists it already. Only
 * whether it is stored needs the load, and one that is not is taken out again then.
 * <p>
 * Changed on the UI thread only.
 */
final class MemberEdits {
	private final JSONArray members;

	MemberEdits(JSONArray members) {
		this.members = members;
	}

	boolean contains(String type, long ref) {
		return RelationMembers.contains(members, type, ref);
	}

	/**
	 * Adds the element at the end, unless the relation lists it already.
	 *
	 * @return whether it was added
	 */
	boolean join(String type, long ref) {
		if (contains(type, ref)) {
			return false;
		}
		RelationMembers.append(members, type, ref, "");
		return true;
	}

	/**
	 * Takes out an element that joined, its load having found it is not stored: the editor only
	 * adds elements it has. Nothing if the user already took it out.
	 *
	 * @return whether it was still listed
	 */
	boolean notStored(String type, long ref) {
		return RelationMembers.removeAll(members, type, ref) > 0;
	}

	void remove(int index) {
		members.remove(index);
	}
}

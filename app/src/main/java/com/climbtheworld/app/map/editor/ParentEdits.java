package com.climbtheworld.app.map.editor;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
import com.climbtheworld.app.storage.edit.RelationMembers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The parents of the element an editor is on, as the edit has them so far: the relations the
 * element is in, and the stored ones it was taken out of. ParentMembership shows them, and the
 * editor saves the changed ones with the element.
 * <p>
 * The element joins or leaves a parent right when the user says so, never once something is
 * loaded for the parent's row, like its pin: a save coming before that would leave the change
 * out without a word.
 * <p>
 * Changed on the UI thread only.
 */
final class ParentEdits {
	/** A parent the element is in. */
	static final class Membership {
		OsmCollectionEntity relation;
		/**
		 * The relation as its pin draws it, with the count of its routes as they are stored, see
		 * ParentMembership.pinOf; null until it is loaded.
		 */
		DisplayableGeoNode pin;
		/** Whether the edit changed the relation, which is then saved with the element. */
		boolean changed;

		Membership(OsmCollectionEntity relation, DisplayableGeoNode pin, boolean changed) {
			this.relation = relation;
			this.pin = pin;
			this.changed = changed;
		}
	}

	private final String memberType;
	private final long memberId;
	private final List<Membership> memberships = new ArrayList<>();
	/** Stored parents the element was taken out of; saving them records the change. */
	private final List<OsmCollectionEntity> leftParents = new ArrayList<>();

	ParentEdits(EntityOsmType memberType, long memberId) {
		this.memberType = memberType.name();
		this.memberId = memberId;
	}

	/** The parents the element is in now, in the order they joined. */
	List<Membership> memberships() {
		return Collections.unmodifiableList(memberships);
	}

	/** The stored parents the element was taken out of. */
	List<OsmCollectionEntity> leftParents() {
		return Collections.unmodifiableList(leftParents);
	}

	boolean listsElement(OsmCollectionEntity relation) {
		return RelationMembers.contains(relation.getMembers(), memberType, memberId);
	}

	/** The membership in the relation of this id, or null. */
	Membership membershipIn(long relationId) {
		Membership result = null;
		for (Membership candidate : memberships) {
			if (candidate.relation.osmID == relationId) {
				result = candidate;
			}
		}
		return result;
	}

	/**
	 * Takes the parents as the edit starts with them, once they are loaded: the ones the element
	 * is in, and the stored ones it left earlier in the edit.
	 */
	void addLoaded(List<Membership> loaded, List<OsmCollectionEntity> left) {
		memberships.addAll(loaded);
		leftParents.addAll(left);
	}

	/**
	 * Adds the element at the end of the parent, unless the parent lists it already. The parent
	 * is changed from here on, its pin still to be loaded.
	 */
	Membership join(OsmCollectionEntity relation) {
		// Taken out and added back in the same edit, it is still listed in the stored one.
		leftParents.removeIf(left -> left.osmID == relation.osmID);
		if (!listsElement(relation)) {
			RelationMembers.append(relation.getMembers(), memberType, memberId, "");
		}
		Membership membership = new Membership(relation, null, true);
		memberships.add(membership);
		return membership;
	}

	/**
	 * Takes the element out of the parent. A stored parent is kept, to be saved without the
	 * element; a new one is never stored.
	 */
	void leave(Membership membership) {
		OsmCollectionEntity relation = membership.relation;
		RelationMembers.removeAll(relation.getMembers(), memberType, memberId);
		memberships.remove(membership);
		if (!relation.isNew()) {
			leftParents.add(relation);
		}
	}

	/** The parents whose members the edit changed, to save with the element. */
	List<OsmCollectionEntity> changed() {
		List<OsmCollectionEntity> result = new ArrayList<>(leftParents);
		for (Membership membership : memberships) {
			if (membership.changed) {
				result.add(membership.relation);
			}
		}
		return result;
	}
}

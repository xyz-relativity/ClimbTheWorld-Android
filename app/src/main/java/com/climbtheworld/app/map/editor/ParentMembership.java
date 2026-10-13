package com.climbtheworld.app.map.editor;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmEntity.EntityClimbingType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
import com.climbtheworld.app.storage.edit.OsmEditStore;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import needle.UiRelatedTask;

/**
 * An editor's section listing the relations the edited element is a member of, a route's crags or
 * a crag's areas, with its place in each, adding it to a nearby or a new one, and taking it out of
 * one. An element joins at the end; its place is changed in the parent's own editor, which a new
 * parent opens in right away. Nothing is stored until the editor saves the parents together with
 * the element.
 */
public class ParentMembership {
	private static final double NEARBY_PARENT_METERS = 1000;

	/** What the parents are. */
	public enum Kind {
		/** The crags of a route node. */
		CRAG(EntityOsmType.node, EntityClimbingType.crag, ClimbingTags.VALUE_CRAG,
				R.string.crags, R.string.add_to_crag, R.string.new_crag,
				R.string.unnamed_crag, R.string.not_in_a_crag, R.string.remove_from_crag),
		/** The areas of a crag relation. */
		AREA(EntityOsmType.relation, EntityClimbingType.area, ClimbingTags.VALUE_AREA,
				R.string.areas, R.string.add_to_area, R.string.new_area,
				R.string.unnamed_area, R.string.not_in_an_area, R.string.remove_from_area);

		final EntityOsmType memberType;
		final EntityClimbingType parentType;
		final String climbingValue;
		@StringRes
		final int title;
		@StringRes
		final int add;
		@StringRes
		final int create;
		@StringRes
		final int unnamed;
		@StringRes
		final int none;
		@StringRes
		final int remove;

		Kind(EntityOsmType memberType, EntityClimbingType parentType, String climbingValue,
		     int title, int add, int create, int unnamed, int none, int remove) {
			this.memberType = memberType;
			this.parentType = parentType;
			this.climbingValue = climbingValue;
			this.title = title;
			this.add = add;
			this.create = create;
			this.unnamed = unnamed;
			this.none = none;
			this.remove = remove;
		}
	}

	private final AppCompatActivity parent;
	private final Kind kind;
	private final OsmEditStore store;
	private final long memberId;
	private final Supplier<GeoNode> editedElement;
	private final View section;
	private final ViewGroup parentsContainer;
	private final TextView noParent;
	private final TextView addButton;

	private final List<Membership> memberships = new ArrayList<>();
	/** Stored parents the element was taken out of; saving them records the change. */
	private final List<OsmCollectionEntity> leftParents = new ArrayList<>();
	private boolean canAdd = true;
	private ParentEditor parentEditor;

	/**
	 * Opens a parent's editor as part of the element's edit, for one the element is in or a new
	 * one; the parent comes back through parentEdited.
	 */
	public interface ParentEditor {
		void editParent(OsmCollectionEntity parent);
	}

	private static final class Membership {
		OsmCollectionEntity relation;
		/** The relation's stored members by RelationMembers.key. */
		Map<String, OsmEntity> members;
		boolean changed;

		Membership(OsmCollectionEntity relation, Map<String, OsmEntity> members, boolean changed) {
			this.relation = relation;
			this.members = members;
			this.changed = changed;
		}
	}

	/**
	 * @param editedElement the element as the form has it now, placed where it is, for its route
	 *                      number and for finding the parents around it.
	 */
	public ParentMembership(AppCompatActivity parent, ViewGroup container, Kind kind, long memberId,
	                        Supplier<GeoNode> editedElement) {
		this.parent = parent;
		this.kind = kind;
		this.store = new OsmEditStore(parent);
		this.memberId = memberId;
		this.editedElement = editedElement;

		section = parent.getLayoutInflater().inflate(R.layout.fragment_edit_parents, container, false);
		container.addView(section);
		((TextView) section.findViewById(R.id.textParentsTitle)).setText(kind.title);
		parentsContainer = section.findViewById(R.id.parentsContainer);
		noParent = section.findViewById(R.id.textNoParent);
		noParent.setText(kind.none);
		addButton = section.findViewById(R.id.buttonAddToParent);
		addButton.setText(kind.add);
		addButton.setOnClickListener(view -> pickParent());

		loadMemberships();
	}

	/** Whether the element can be added to more parents; any element shows the ones it is in. */
	public void setCanAdd(boolean canAdd) {
		this.canAdd = canAdd;
		render();
	}

	/** Makes the parents clickable, and new parents open in their editor before joining. */
	public void setParentEditor(ParentEditor editor) {
		parentEditor = editor;
		render();
	}

	/** The parents whose members changed, to save together with the element. */
	public List<OsmCollectionEntity> changedParents() {
		List<OsmCollectionEntity> result = new ArrayList<>(leftParents);
		for (Membership membership : memberships) {
			if (membership.changed) {
				result.add(membership.relation);
			}
		}
		return result;
	}

	/** The names of the stored parents listing the element, which deleting it takes it out of. */
	public List<String> storedParentNames() {
		List<String> result = new ArrayList<>();
		for (Membership membership : memberships) {
			if (!membership.relation.isNew()) {
				result.add(nameOf(membership.relation));
			}
		}
		for (OsmCollectionEntity relation : leftParents) {
			result.add(nameOf(relation));
		}
		return result;
	}

	/**
	 * Takes back a parent saved in its editor, opened through the ParentEditor: one the element
	 * was in, or a new one it joins. A parent that no longer lists the element leaves the section.
	 */
	public void parentEdited(OsmCollectionEntity relation) {
		Membership membership = null;
		for (Membership candidate : memberships) {
			if (candidate.relation.osmID == relation.osmID) {
				membership = candidate;
			}
		}
		boolean listsElement = RelationMembers.contains(relation.getMembers(),
				kind.memberType.name(), memberId);
		if (membership == null) {
			if (listsElement) {
				// Changes in its editor happen in the same edit.
				leftParents.removeIf(left -> left.osmID == relation.osmID);
				membership = new Membership(relation, new HashMap<>(), true);
				memberships.add(membership);
			} else {
				return;
			}
		}
		if (!listsElement) {
			memberships.remove(membership);
			if (!relation.isNew()) {
				leftParents.add(relation);
			}
			render();
			return;
		}
		Membership updated = membership;
		updated.relation = relation;
		updated.changed = true;
		render();
		// Members added in the parent's editor are looked up for the route numbers.
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<Map<String, OsmEntity>>() {
			@Override
			protected Map<String, OsmEntity> doWork() {
				return store.loadMembers(relation.getMembers());
			}

			@Override
			protected void thenDoUiRelatedWork(Map<String, OsmEntity> members) {
				updated.members = members;
				render();
			}
		});
	}

	private void loadMemberships() {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<List<Membership>>() {
			@Override
			protected List<Membership> doWork() {
				List<Membership> result = new ArrayList<>();
				for (OsmCollectionEntity relation : store.loadParents(kind.memberType, memberId)) {
					if (relation.entityClimbingType != EntityClimbingType.NAN) {
						result.add(new Membership(relation,
								store.loadMembers(relation.getMembers()), false));
					}
				}
				return result;
			}

			@Override
			protected void thenDoUiRelatedWork(List<Membership> result) {
				memberships.addAll(result);
				render();
			}
		});
	}

	private void render() {
		parentsContainer.removeAllViews();
		for (Membership membership : memberships) {
			parentsContainer.addView(buildRow(membership));
		}
		addButton.setVisibility(canAdd ? View.VISIBLE : View.GONE);
		noParent.setVisibility(canAdd && memberships.isEmpty() ? View.VISIBLE : View.GONE);
		section.setVisibility(canAdd || !memberships.isEmpty() ? View.VISIBLE : View.GONE);
	}

	private View buildRow(Membership membership) {
		View row = parent.getLayoutInflater().inflate(R.layout.list_item_edit_parent, parentsContainer, false);
		((TextView) row.findViewById(R.id.textParentName)).setText(nameOf(membership.relation));
		((TextView) row.findViewById(R.id.textParentPosition)).setText(positionText(membership));
		View removeButton = row.findViewById(R.id.buttonRemoveFromParent);
		removeButton.setContentDescription(parent.getString(kind.remove));
		removeButton.setOnClickListener(view -> remove(membership));
		if (parentEditor != null) {
			row.setOnClickListener(view -> parentEditor.editParent(membership.relation));
		} else {
			row.setClickable(false);
		}
		return row;
	}

	private void remove(Membership membership) {
		OsmCollectionEntity relation = membership.relation;
		Runnable removal = () -> {
			RelationMembers.removeAll(relation.getMembers(), kind.memberType.name(), memberId);
			memberships.remove(membership);
			if (!relation.isNew()) {
				leftParents.add(relation);
			}
			render();
		};

		JSONArray members = relation.getMembers();
		int others = 0;
		for (int index = 0; index < members.length(); index++) {
			if (!isEditedElement(members.optJSONObject(index))) {
				others++;
			}
		}
		if (others > 0 || relation.isNew()) {
			removal.run();
			return;
		}
		new AlertDialog.Builder(parent)
				.setMessage(parent.getString(R.string.crag_deleted_when_empty, nameOf(relation)))
				.setPositiveButton(android.R.string.ok, (dialog, which) -> removal.run())
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	/** Lists the parents around the element, last changed first, and a new one. */
	private void pickParent() {
		GeoNode element = editedElement.get();
		double latitude = element.decimalLatitude;
		double longitude = element.decimalLongitude;
		Set<Long> listed = new HashSet<>();
		for (Membership membership : memberships) {
			listed.add(membership.relation.osmID);
		}

		Constants.DB_EXECUTOR.execute(new UiRelatedTask<List<OsmCollectionEntity>>() {
			@Override
			protected List<OsmCollectionEntity> doWork() {
				List<OsmCollectionEntity> result = new ArrayList<>();
				for (OsmCollectionEntity relation : store.loadNearbyRelations(latitude, longitude,
						NEARBY_PARENT_METERS, kind.parentType)) {
					// A crag doesn't go into itself.
					if (!listed.contains(relation.osmID) && !(relation.osmID == memberId
							&& kind.memberType == EntityOsmType.relation)) {
						result.add(relation);
					}
				}
				return result;
			}

			@Override
			protected void thenDoUiRelatedWork(List<OsmCollectionEntity> relations) {
				CharSequence[] labels = new CharSequence[relations.size() + 1];
				labels[0] = parent.getString(kind.create);
				for (int index = 0; index < relations.size(); index++) {
					OsmCollectionEntity relation = relations.get(index);
					labels[index + 1] = nameOf(relation) + " (" + Globals.getDistanceString(
							OsmEditStore.distanceMeters(latitude, longitude, relation)) + ")";
				}
				new AlertDialog.Builder(parent)
						.setTitle(kind.add)
						.setItems(labels, (dialog, which) -> {
							if (which == 0) {
								createParent();
							} else {
								addTo(relations.get(which - 1));
							}
						})
						.setNegativeButton(android.R.string.cancel, null)
						.show();
			}
		});
	}

	private void addTo(OsmCollectionEntity relation) {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<Map<String, OsmEntity>>() {
			@Override
			protected Map<String, OsmEntity> doWork() {
				return store.loadMembers(relation.getMembers());
			}

			@Override
			protected void thenDoUiRelatedWork(Map<String, OsmEntity> members) {
				// Taken out and added back in the same edit, it is still listed in the stored one.
				leftParents.removeIf(left -> left.osmID == relation.osmID);
				join(new Membership(relation, members, true));
			}
		});
	}

	/**
	 * A new parent holding the element, opened in its editor to be named and filled in; it joins
	 * the section when that editor saves it.
	 */
	private void createParent() {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<OsmCollectionEntity>() {
			@Override
			protected OsmCollectionEntity doWork() {
				// The new id comes from the database.
				OsmCollectionEntity relation = store.newRelation();
				try {
					relation.getTags()
							.put(ClimbingTags.KEY_TYPE, ClimbingTags.VALUE_SITE)
							.put(ClimbingTags.KEY_SITE, ClimbingTags.VALUE_CLIMBING)
							.put(ClimbingTags.KEY_SPORT, ClimbingTags.VALUE_CLIMBING)
							.put(ClimbingTags.KEY_CLIMBING, kind.climbingValue);
				} catch (JSONException e) {
					throw new IllegalStateException(e);
				}
				RelationMembers.append(relation.getMembers(), kind.memberType.name(), memberId, "");
				return relation;
			}

			@Override
			protected void thenDoUiRelatedWork(OsmCollectionEntity relation) {
				if (parentEditor != null) {
					parentEditor.editParent(relation);
				} else {
					join(new Membership(relation, new HashMap<>(), true));
				}
			}
		});
	}

	/** Adds the element at the end of the parent, unless the parent lists it already. */
	private void join(Membership membership) {
		JSONArray members = membership.relation.getMembers();
		if (!RelationMembers.contains(members, kind.memberType.name(), memberId)) {
			RelationMembers.append(members, kind.memberType.name(), memberId, "");
		}
		memberships.add(membership);
		render();
	}

	/** "Route 3 of 12" when the element takes a route number, else its place among all members. */
	private String positionText(Membership membership) {
		JSONArray members = membership.relation.getMembers();
		int routes = 0;
		int routeNumber = 0;
		int memberIndex = -1;
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			boolean edited = isEditedElement(member);
			if (edited && memberIndex < 0) {
				memberIndex = index;
			}
			if (takesRouteNumber(membership, member)) {
				routes++;
				if (edited && routeNumber == 0) {
					routeNumber = routes;
				}
			}
		}
		if (routeNumber > 0) {
			return parent.getString(R.string.route_position, routeNumber, routes);
		}
		return parent.getString(R.string.member_position, memberIndex + 1, members.length());
	}

	/** Same rule as the map's route numbers. */
	private boolean takesRouteNumber(Membership membership, JSONObject member) {
		if (member == null) {
			return false;
		}
		if (isEditedElement(member)) {
			GeoNode element = editedElement.get();
			return element.getNodeType() == GeoNode.NodeTypes.route && !element.isRouteTop();
		}
		OsmEntity entity = membership.members.get(RelationMembers.key(member));
		return entity != null && ClimbingRouteCounter.takesRouteNumber(entity);
	}

	private boolean isEditedElement(JSONObject member) {
		return member != null && member.optLong(ClimbingTags.KEY_REF) == memberId
				&& kind.memberType.name().equals(member.optString(ClimbingTags.KEY_TYPE));
	}

	private String nameOf(OsmCollectionEntity relation) {
		String name = relation.getTags().optString(ClimbingTags.KEY_NAME).trim();
		return name.isEmpty() ? parent.getString(kind.unnamed) : name;
	}
}

package com.climbtheworld.app.map.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmEntity.EntityClimbingType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
import com.climbtheworld.app.storage.edit.OsmEditStore;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import needle.UiRelatedTask;

/**
 * An editor's section listing the relations the edited element is a member of, a route's crags or
 * a crag's areas, with its place in each, adding it to one nearby or on the editor's map, one found
 * by name or a new one, and taking it out of one. An element joins at the end; its place is changed in the
 * parent's own editor, which a new parent opens in right away, and where the parent can join its
 * own parents in turn, like a route's new crag joining an area. Nothing is stored until the editor
 * saves the parents together with the element, followed by the parents' parents.
 */
public class ParentMembership {
	private static final double NEARBY_PARENT_METERS = 1000;
	/** The size of the picker's pins, against the map's. */
	private static final double PICKER_PIN_SCALE = 0.78;

	/** What the parents are. */
	public enum Kind {
		/** The crags of a route node. */
		CRAG(EntityOsmType.node, EntityClimbingType.crag, ClimbingTags.VALUE_CRAG,
				R.string.crags, R.string.add_to_crag, R.string.new_crag, R.string.find_crag,
				R.string.unnamed_crag, R.string.not_in_a_crag, R.string.remove_from_crag),
		/** The areas of a crag relation. */
		AREA(EntityOsmType.relation, EntityClimbingType.area, ClimbingTags.VALUE_AREA,
				R.string.areas, R.string.add_to_area, R.string.new_area, R.string.find_area,
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
		final int find;
		@StringRes
		final int unnamed;
		@StringRes
		final int none;
		@StringRes
		final int remove;

		Kind(EntityOsmType memberType, EntityClimbingType parentType, String climbingValue,
		     int title, int add, int create, int find, int unnamed, int none, int remove) {
			this.memberType = memberType;
			this.parentType = parentType;
			this.climbingValue = climbingValue;
			this.title = title;
			this.add = add;
			this.create = create;
			this.find = find;
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
	private final Supplier<MapBounds> shownBounds;
	private final View section;
	private final ViewGroup parentsContainer;
	private final TextView noParent;
	private final TextView addButton;

	private final List<Membership> memberships = new ArrayList<>();
	/** Stored parents the element was taken out of; saving them records the change. */
	private final List<OsmCollectionEntity> leftParents = new ArrayList<>();
	/**
	 * Parents changed earlier in the same edit, by id, standing in for their stored versions: the
	 * areas a route's editor kept from the crag's editor, when it opens again.
	 */
	private final Map<Long, OsmCollectionEntity> editedParents = new HashMap<>();
	/** The parents' parents changed in the parents' editors, by id, like the areas of the crags. */
	private final Map<Long, OsmCollectionEntity> grandparents = new LinkedHashMap<>();
	/** The parents that came back from their editor, the ones that can have changed grandparents. */
	private final Set<Long> parentsEdited = new HashSet<>();
	private boolean canAdd = true;
	private ParentEditor parentEditor;

	/**
	 * Opens a parent's editor as part of the element's edit, for one the element is in or a new
	 * one; the parent comes back through parentEdited.
	 */
	public interface ParentEditor {
		void editParent(OsmCollectionEntity parent);
	}

	/** A parent the picker lists, as its row shows it. */
	private static final class ListedParent {
		final OsmCollectionEntity relation;
		/** The relation as its pin draws it, with its route count. */
		final DisplayableGeoNode pin;
		final double distanceMeters;

		ListedParent(OsmCollectionEntity relation, DisplayableGeoNode pin, double distanceMeters) {
			this.relation = relation;
			this.pin = pin;
			this.distanceMeters = distanceMeters;
		}
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
	 *                      number and for the distances to the parents.
	 * @param shownBounds   the part of the editor's map on screen, where the parents to choose from
	 *                      are listed besides the nearby ones, or null before the map is ready.
	 * @param editedParents parents changed earlier in the same edit, which the element may be in or
	 *                      have left; see changedGrandparents.
	 */
	public ParentMembership(AppCompatActivity parent, ViewGroup container, Kind kind, long memberId,
	                        Supplier<GeoNode> editedElement, Supplier<MapBounds> shownBounds,
	                        List<OsmCollectionEntity> editedParents) {
		this.parent = parent;
		this.kind = kind;
		this.store = new OsmEditStore(parent);
		this.memberId = memberId;
		this.editedElement = editedElement;
		this.shownBounds = shownBounds;
		for (OsmCollectionEntity relation : editedParents) {
			this.editedParents.put(relation.osmID, relation);
		}

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

	/**
	 * The parents whose members changed, then the grandparents changed in the parents' editors, to
	 * save together with the element in this order: a new area is placed by its crags once they are
	 * stored. The grandparents no longer list the parents this edit drops.
	 */
	public List<OsmCollectionEntity> changedParents() {
		List<OsmCollectionEntity> result = new ArrayList<>(leftParents);
		for (Membership membership : memberships) {
			if (membership.changed) {
				result.add(membership.relation);
			}
		}
		Set<Long> dropped = droppedParents();
		for (OsmCollectionEntity grandparent : grandparents.values()) {
			OsmCollectionEntity saved = copy(grandparent);
			for (long droppedId : dropped) {
				RelationMembers.removeAll(saved.getMembers(), EntityOsmType.relation.name(), droppedId);
			}
			result.add(saved);
		}
		return result;
	}

	/**
	 * The grandparents changed so far, for the next parent's editor to start from, as the parents
	 * it opens with.
	 */
	public List<OsmCollectionEntity> changedGrandparents() {
		return new ArrayList<>(grandparents.values());
	}

	/**
	 * The parents back from their editor that the edit doesn't keep: new ones the element left,
	 * which are never stored, and ones left without members, which saving deletes.
	 */
	private Set<Long> droppedParents() {
		Set<Long> result = new HashSet<>(parentsEdited);
		for (Membership membership : memberships) {
			if (membership.relation.getMembers().length() > 0) {
				result.remove(membership.relation.osmID);
			}
		}
		for (OsmCollectionEntity relation : leftParents) {
			if (relation.getMembers().length() > 0) {
				result.remove(relation.osmID);
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
		parentEdited(relation, Collections.emptyList());
	}

	/**
	 * Takes back a parent as parentEdited(OsmCollectionEntity) does, with the parents it joined or
	 * left in its editor, which replace the ones changed in an earlier visit.
	 */
	public void parentEdited(OsmCollectionEntity relation, List<OsmCollectionEntity> relationParents) {
		Membership membership = null;
		for (Membership candidate : memberships) {
			if (candidate.relation.osmID == relation.osmID) {
				membership = candidate;
			}
		}
		boolean listsElement = listsElement(relation);
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
		parentsEdited.add(relation.osmID);
		for (OsmCollectionEntity grandparent : relationParents) {
			grandparents.put(grandparent.osmID, grandparent);
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
			/** The stored parents the element left earlier in the edit. */
			private final List<OsmCollectionEntity> left = new ArrayList<>();

			@Override
			protected List<Membership> doWork() {
				List<Membership> result = new ArrayList<>();
				for (OsmCollectionEntity relation : store.loadParents(kind.memberType, memberId)) {
					OsmCollectionEntity edited = editedParents.get(relation.osmID);
					if (edited != null) {
						if (!listsElement(edited)) {
							left.add(edited);
						}
					} else if (relation.entityClimbingType != EntityClimbingType.NAN) {
						result.add(new Membership(relation,
								store.loadMembers(relation.getMembers()), false));
					}
				}
				for (OsmCollectionEntity edited : editedParents.values()) {
					if (listsElement(edited)) {
						result.add(new Membership(edited, store.loadMembers(edited.getMembers()), true));
					}
				}
				return result;
			}

			@Override
			protected void thenDoUiRelatedWork(List<Membership> result) {
				memberships.addAll(result);
				leftParents.addAll(left);
				render();
			}
		});
	}

	private boolean listsElement(OsmCollectionEntity relation) {
		return RelationMembers.contains(relation.getMembers(), kind.memberType.name(), memberId);
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

	private void pickParent() {
		new ParentPicker().show();
	}

	/**
	 * Lists the parents near the element or on the editor's map, last changed first, which zooming
	 * the map out widens, then offers a new parent and finding one by name. Finding opens a filter
	 * listing the parents of the name typed instead, at any distance.
	 */
	private final class ParentPicker {
		private final double latitude;
		private final double longitude;
		/** Around the element, and the part of the map shown once the map is ready. */
		private final List<MapBounds> areas = new ArrayList<>();
		/** The parents the element is in already. */
		private final Set<Long> listed = new HashSet<>();
		/** The listed parents, in the adapter's order. */
		private final List<ListedParent> relations = new ArrayList<>();
		private final BaseAdapter adapter;
		private final View filterContainer;
		private final EditText filter;
		private final AlertDialog dialog;

		ParentPicker() {
			GeoNode element = editedElement.get();
			latitude = element.decimalLatitude;
			longitude = element.decimalLongitude;
			areas.add(DataManager.computeBoundingBox(
					new Vector4d(latitude, longitude, 0, 0), NEARBY_PARENT_METERS));
			MapBounds shown = shownBounds.get();
			if (shown != null) {
				areas.add(shown);
			}
			for (Membership membership : memberships) {
				listed.add(membership.relation.osmID);
			}

			ViewGroup content = (ViewGroup) parent.getLayoutInflater()
					.inflate(R.layout.fragment_dialog_pick_parent, null);
			dialog = new AlertDialog.Builder(parent)
					.setTitle(kind.add)
					.setView(content)
					.setNegativeButton(android.R.string.cancel, null)
					.create();

			adapter = new BaseAdapter() {
				@Override
				public int getCount() {
					return relations.size();
				}

				@Override
				public Object getItem(int position) {
					return relations.get(position);
				}

				@Override
				public long getItemId(int position) {
					return position;
				}

				@Override
				public View getView(int position, View view, ViewGroup group) {
					ListedParent listedParent = relations.get(position);
					// The rows of the map's cluster list, with the pin as the map draws it, smaller.
					Drawable pin = new PoiMarkerDrawable(parent, listedParent.pin).getDrawable();
					View row = ListViewItemBuilder.getPaddedBuilder(parent, view, true)
							.setTitle(nameOf(listedParent.relation))
							.setDescription(Globals.getDistanceString(listedParent.distanceMeters))
							.setIcon(pin)
							.setIconSize(pin.getIntrinsicWidth() * PICKER_PIN_SCALE,
									pin.getIntrinsicHeight() * PICKER_PIN_SCALE)
							.build();
					row.setOnClickListener(clicked -> {
						dialog.dismiss();
						addTo(listedParent.relation);
					});
					return row;
				}
			};
			((ListView) content.findViewById(R.id.listParents)).setAdapter(adapter);

			// Below the list, where the filter opens in place of the find row.
			content.addView(buildChoice(kind.create, view -> {
				dialog.dismiss();
				createParent();
			}), 1);
			content.addView(buildChoice(kind.find, this::openFilter), 2);

			filterContainer = content.findViewById(R.id.filterContainer);
			filter = content.findViewById(R.id.editFind);
			filter.setHint(kind.find);
			filter.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					search();
				}
			});
		}

		void show() {
			dialog.show();
			search();
		}

		/** A row below the list, looking like the list's own rows. */
		private View buildChoice(@StringRes int text, View.OnClickListener listener) {
			View row = ListViewItemBuilder.getPaddedBuilder(parent, null, true)
					.setTitle(parent.getString(text))
					.build();
			row.setOnClickListener(listener);
			return row;
		}

		/** Turns the find row into the filter, ready to type in. */
		private void openFilter(View findRow) {
			findRow.setVisibility(View.GONE);
			filterContainer.setVisibility(View.VISIBLE);
			filter.requestFocus();
			// Once the field is laid out, which it isn't while it was gone.
			filter.post(() -> ((InputMethodManager) parent.getSystemService(
					Context.INPUT_METHOD_SERVICE)).showSoftInput(filter,
					InputMethodManager.SHOW_IMPLICIT));
		}

		private void search() {
			String name = filter.getText().toString().trim();
			Constants.DB_EXECUTOR.execute(new UiRelatedTask<List<ListedParent>>() {
				@Override
				protected List<ListedParent> doWork() {
					List<OsmCollectionEntity> found = name.isEmpty()
							? store.loadRelationsIn(areas, latitude, longitude, kind.parentType)
							: store.findRelations(name, latitude, longitude, kind.parentType);
					ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(parent);
					List<ListedParent> result = new ArrayList<>();
					for (OsmCollectionEntity relation : found) {
						// A crag doesn't go into itself.
						if (!listed.contains(relation.osmID) && !(relation.osmID == memberId
								&& kind.memberType == EntityOsmType.relation)) {
							result.add(new ListedParent(relation,
									DataManager.toDisplayableNode(relation, routeCounter),
									OsmEditStore.distanceMeters(latitude, longitude, relation)));
						}
					}
					return result;
				}

				@Override
				protected void thenDoUiRelatedWork(List<ListedParent> result) {
					// The database runs one task at a time, so the last search typed lands last.
					relations.clear();
					relations.addAll(result);
					adapter.notifyDataSetChanged();
				}
			});
		}
	}

	private void addTo(OsmCollectionEntity listed) {
		// Joins the parent as changed earlier in the edit, keeping those changes.
		OsmCollectionEntity relation = editedParents.containsKey(listed.osmID)
				? editedParents.get(listed.osmID) : listed;
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
		if (!listsElement(membership.relation)) {
			RelationMembers.append(membership.relation.getMembers(), kind.memberType.name(), memberId, "");
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

	private static OsmCollectionEntity copy(OsmCollectionEntity relation) {
		try {
			return new OsmCollectionEntity(new JSONObject(relation.jsonNodeInfo.toString()));
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
	}

	private String nameOf(OsmCollectionEntity relation) {
		String name = relation.getTags().optString(ClimbingTags.KEY_NAME).trim();
		return name.isEmpty() ? parent.getString(kind.unnamed) : name;
	}
}

package com.climbtheworld.app.map.editor;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmEntity.EntityClimbingType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
import com.climbtheworld.app.storage.database.OsmNode;
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
 * The editor's crags section: the crags a node is a member of and its place in each, adding a
 * route to a nearby or a new crag, and taking it out of one. A route joins a crag at the end;
 * its place is changed in the crag's own editor. Nothing is stored until the editor saves the
 * crags together with the node.
 */
public class CragMembership {
	private static final double NEARBY_CRAG_METERS = 1000;
	private static final int DIALOG_PADDING = Globals.convertDpToPixel(20).intValue();

	private final AppCompatActivity parent;
	private final OsmEditStore store;
	private final long nodeId;
	private final Supplier<GeoNode> editedNode;
	private final View section;
	private final ViewGroup cragsContainer;
	private final View notInACrag;
	private final View addButton;

	private final List<Membership> memberships = new ArrayList<>();
	/** Stored crags the node was taken out of; saving them records the change. */
	private final List<OsmCollectionEntity> leftCrags = new ArrayList<>();
	private boolean canAdd;
	private OnCragClickListener cragClickListener;

	/** Opens one of the node's crags. */
	public interface OnCragClickListener {
		void onCragClick(OsmCollectionEntity crag);
	}

	private static final class Membership {
		final OsmCollectionEntity crag;
		/** The crag's stored members by memberKey, apart from the edited node. */
		final Map<String, OsmEntity> members;
		boolean changed;

		Membership(OsmCollectionEntity crag, Map<String, OsmEntity> members, boolean changed) {
			this.crag = crag;
			this.members = members;
			this.changed = changed;
		}
	}

	/**
	 * @param editedNode the node as the form has it now, for showing it among the members.
	 */
	public CragMembership(AppCompatActivity parent, ViewGroup container, long nodeId,
	                      Supplier<GeoNode> editedNode) {
		this.parent = parent;
		this.store = new OsmEditStore(parent);
		this.nodeId = nodeId;
		this.editedNode = editedNode;

		section = parent.getLayoutInflater().inflate(R.layout.fragment_edit_crags, container, false);
		container.addView(section);
		cragsContainer = section.findViewById(R.id.cragsContainer);
		notInACrag = section.findViewById(R.id.textNotInACrag);
		addButton = section.findViewById(R.id.buttonAddToCrag);
		addButton.setOnClickListener(view -> pickCrag());

		loadMemberships();
	}

	/** Only a route can be added to a crag; any type shows the crags it is already in. */
	public void setNodeType(GeoNode.NodeTypes type) {
		canAdd = type == GeoNode.NodeTypes.route;
		render();
	}

	/** Makes the crags clickable. */
	public void setOnCragClickListener(OnCragClickListener listener) {
		cragClickListener = listener;
		render();
	}

	/** The crags whose members changed, to save together with the node. */
	public List<OsmCollectionEntity> changedCrags() {
		List<OsmCollectionEntity> result = new ArrayList<>(leftCrags);
		for (Membership membership : memberships) {
			if (membership.changed) {
				result.add(membership.crag);
			}
		}
		return result;
	}

	/** The names of the stored crags listing the node, which deleting it takes it out of. */
	public List<String> storedCragNames() {
		List<String> result = new ArrayList<>();
		for (Membership membership : memberships) {
			if (!membership.crag.isNew()) {
				result.add(nameOf(membership.crag));
			}
		}
		for (OsmCollectionEntity crag : leftCrags) {
			result.add(nameOf(crag));
		}
		return result;
	}

	private void loadMemberships() {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<List<Membership>>() {
			@Override
			protected List<Membership> doWork() {
				List<Membership> result = new ArrayList<>();
				for (OsmCollectionEntity crag : store.loadParents(EntityOsmType.node, nodeId)) {
					if (crag.entityClimbingType != EntityClimbingType.NAN) {
						result.add(new Membership(crag, loadMembers(crag), false));
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

	/** Runs on the database thread. */
	private Map<String, OsmEntity> loadMembers(OsmCollectionEntity crag) {
		Map<String, OsmEntity> result = new HashMap<>();
		List<Long> nodeIds = new ArrayList<>();
		JSONArray members = crag.getMembers();
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member == null || isEditedNode(member)) {
				continue;
			}
			long ref = member.optLong(ClimbingTags.KEY_REF);
			EntityOsmType memberType;
			try {
				memberType = EntityOsmType.valueOf(member.optString(ClimbingTags.KEY_TYPE));
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (memberType == EntityOsmType.node) {
				nodeIds.add(ref);
				continue;
			}
			OsmEntity collection = store.load(memberType, ref);
			if (collection != null) {
				result.put(memberKey(memberType.name(), ref), collection);
			}
		}
		for (OsmNode node : store.loadNodes(nodeIds)) {
			result.put(memberKey(EntityOsmType.node.name(), node.osmID), node);
		}
		return result;
	}

	private void render() {
		cragsContainer.removeAllViews();
		for (Membership membership : memberships) {
			cragsContainer.addView(buildRow(membership));
		}
		addButton.setVisibility(canAdd ? View.VISIBLE : View.GONE);
		notInACrag.setVisibility(canAdd && memberships.isEmpty() ? View.VISIBLE : View.GONE);
		section.setVisibility(canAdd || !memberships.isEmpty() ? View.VISIBLE : View.GONE);
	}

	private View buildRow(Membership membership) {
		View row = parent.getLayoutInflater().inflate(R.layout.list_item_edit_crag, cragsContainer, false);
		((TextView) row.findViewById(R.id.textCragName)).setText(nameOf(membership.crag));
		((TextView) row.findViewById(R.id.textCragPosition)).setText(positionText(membership));
		row.findViewById(R.id.buttonRemoveFromCrag).setOnClickListener(view -> remove(membership));
		if (cragClickListener != null) {
			row.setOnClickListener(view -> cragClickListener.onCragClick(membership.crag));
		} else {
			row.setClickable(false);
		}
		return row;
	}

	private void remove(Membership membership) {
		Runnable removal = () -> {
			RelationMembers.removeAll(membership.crag.getMembers(), EntityOsmType.node.name(), nodeId);
			memberships.remove(membership);
			if (!membership.crag.isNew()) {
				leftCrags.add(membership.crag);
			}
			render();
		};

		JSONArray members = membership.crag.getMembers();
		int others = 0;
		for (int index = 0; index < members.length(); index++) {
			if (!isEditedNode(members.optJSONObject(index))) {
				others++;
			}
		}
		if (others > 0 || membership.crag.isNew()) {
			removal.run();
			return;
		}
		new AlertDialog.Builder(parent)
				.setMessage(parent.getString(R.string.crag_deleted_when_empty, nameOf(membership.crag)))
				.setPositiveButton(android.R.string.ok, (dialog, which) -> removal.run())
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	/** Lists the crags around the node, last changed first, and a new crag. */
	private void pickCrag() {
		GeoNode node = editedNode.get();
		double latitude = node.decimalLatitude;
		double longitude = node.decimalLongitude;
		Set<Long> listed = new HashSet<>();
		for (Membership membership : memberships) {
			listed.add(membership.crag.osmID);
		}

		Constants.DB_EXECUTOR.execute(new UiRelatedTask<List<OsmCollectionEntity>>() {
			@Override
			protected List<OsmCollectionEntity> doWork() {
				List<OsmCollectionEntity> result = new ArrayList<>();
				for (OsmCollectionEntity crag : store.loadNearbyRelations(latitude, longitude,
						NEARBY_CRAG_METERS, EntityClimbingType.crag)) {
					if (!listed.contains(crag.osmID)) {
						result.add(crag);
					}
				}
				return result;
			}

			@Override
			protected void thenDoUiRelatedWork(List<OsmCollectionEntity> crags) {
				CharSequence[] labels = new CharSequence[crags.size() + 1];
				labels[0] = parent.getString(R.string.new_crag);
				for (int index = 0; index < crags.size(); index++) {
					OsmCollectionEntity crag = crags.get(index);
					labels[index + 1] = nameOf(crag) + " (" + Globals.getDistanceString(
							OsmEditStore.distanceMeters(latitude, longitude, crag)) + ")";
				}
				new AlertDialog.Builder(parent)
						.setTitle(R.string.add_to_crag)
						.setItems(labels, (dialog, which) -> {
							if (which == 0) {
								askNewCragName();
							} else {
								addTo(crags.get(which - 1));
							}
						})
						.setNegativeButton(android.R.string.cancel, null)
						.show();
			}
		});
	}

	private void addTo(OsmCollectionEntity crag) {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<Map<String, OsmEntity>>() {
			@Override
			protected Map<String, OsmEntity> doWork() {
				return loadMembers(crag);
			}

			@Override
			protected void thenDoUiRelatedWork(Map<String, OsmEntity> members) {
				// Taken out and added back in the same edit, it is still listed in the stored crag.
				leftCrags.removeIf(left -> left.osmID == crag.osmID);
				join(new Membership(crag, members, true));
			}
		});
	}

	private void askNewCragName() {
		EditText input = new EditText(parent);
		input.setHint(R.string.crag_name);
		input.setSingleLine();
		input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		FrameLayout frame = new FrameLayout(parent);
		frame.setPadding(DIALOG_PADDING, DIALOG_PADDING / 2, DIALOG_PADDING, 0);
		frame.addView(input);

		new AlertDialog.Builder(parent)
				.setTitle(R.string.new_crag)
				.setView(frame)
				.setPositiveButton(android.R.string.ok,
						(dialog, which) -> createCrag(input.getText().toString().trim()))
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void createCrag(String name) {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<OsmCollectionEntity>() {
			@Override
			protected OsmCollectionEntity doWork() {
				// The new id comes from the database.
				OsmCollectionEntity crag = store.newRelation();
				try {
					crag.getTags()
							.put(ClimbingTags.KEY_TYPE, ClimbingTags.VALUE_SITE)
							.put(ClimbingTags.KEY_SITE, ClimbingTags.VALUE_CLIMBING)
							.put(ClimbingTags.KEY_SPORT, ClimbingTags.VALUE_CLIMBING)
							.put(ClimbingTags.KEY_CLIMBING, ClimbingTags.VALUE_CRAG);
					if (!name.isEmpty()) {
						crag.getTags().put(ClimbingTags.KEY_NAME, name);
					}
				} catch (JSONException e) {
					throw new IllegalStateException(e);
				}
				return crag;
			}

			@Override
			protected void thenDoUiRelatedWork(OsmCollectionEntity crag) {
				join(new Membership(crag, new HashMap<>(), true));
			}
		});
	}

	/** Adds the node at the end of the crag, unless the crag lists it already. */
	private void join(Membership membership) {
		JSONArray members = membership.crag.getMembers();
		if (!RelationMembers.contains(members, EntityOsmType.node.name(), nodeId)) {
			RelationMembers.append(members, EntityOsmType.node.name(), nodeId, "");
		}
		memberships.add(membership);
		render();
	}

	/** "Route 3 of 12" when the node takes a route number, else its place among all members. */
	private String positionText(Membership membership) {
		JSONArray members = membership.crag.getMembers();
		int routes = 0;
		int routeNumber = 0;
		int memberIndex = -1;
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			boolean edited = isEditedNode(member);
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
		if (isEditedNode(member)) {
			GeoNode node = editedNode.get();
			return node.getNodeType() == GeoNode.NodeTypes.route && !node.isRouteTop();
		}
		OsmEntity entity = membership.members.get(memberKey(member.optString(ClimbingTags.KEY_TYPE),
				member.optLong(ClimbingTags.KEY_REF)));
		return entity != null && ClimbingRouteCounter.takesRouteNumber(entity);
	}

	private boolean isEditedNode(JSONObject member) {
		return member != null && member.optLong(ClimbingTags.KEY_REF) == nodeId
				&& EntityOsmType.node.name().equals(member.optString(ClimbingTags.KEY_TYPE));
	}

	private String nameOf(OsmCollectionEntity crag) {
		String name = crag.getTags().optString(ClimbingTags.KEY_NAME).trim();
		return name.isEmpty() ? parent.getString(R.string.unnamed_crag) : name;
	}

	private static String memberKey(String type, long ref) {
		return type + "|" + ref;
	}
}

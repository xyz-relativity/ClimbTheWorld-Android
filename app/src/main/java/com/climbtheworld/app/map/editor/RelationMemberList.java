package com.climbtheworld.app.map.editor;

import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.utils.views.MemberIconCard;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * The members of a relation being edited, as a strip in their order, left to right as along the
 * wall: long press and drag one to move it, tap it to take it out. The members array is changed
 * in place.
 */
public class RelationMemberList {

	public interface OnMembersChangedListener {
		void onMembersChanged();
	}

	private final AppCompatActivity parent;
	private final JSONArray members;
	private final Map<String, OsmEntity> resolved;
	private final Map<String, Integer> routeCounts;
	private final String highlightedKey;
	private final OnMembersChangedListener listener;
	private final RecyclerView list;
	private final MemberAdapter adapter = new MemberAdapter();

	/**
	 * @param resolved       the stored members by RelationMembers.key, as OsmEditStore.loadMembers
	 *                       gives them; members added here are put in it too.
	 * @param routeCounts    the route counts of the crags among them by RelationMembers.key,
	 *                       which their pins show, see ClimbingRouteCounter.pin; members added
	 *                       here bring theirs.
	 * @param highlightedKey the member to outline, the element whose editor opened this one, or
	 *                       null.
	 */
	public RelationMemberList(AppCompatActivity parent, ViewGroup container, boolean area,
	                          JSONArray members, Map<String, OsmEntity> resolved,
	                          Map<String, Integer> routeCounts, String highlightedKey,
	                          OnMembersChangedListener listener) {
		this.parent = parent;
		this.members = members;
		this.resolved = resolved;
		this.routeCounts = routeCounts;
		this.highlightedKey = highlightedKey;
		this.listener = listener;

		View section = parent.getLayoutInflater().inflate(R.layout.fragment_edit_members, container, false);
		container.addView(section);
		((TextView) section.findViewById(R.id.textMembersTitle)).setText(area ? R.string.crags : R.string.routes);
		((TextView) section.findViewById(R.id.textMembersHint))
				.setText(area ? R.string.members_hint_area : R.string.members_hint_crag);

		list = section.findViewById(R.id.listMembers);
		list.setLayoutManager(new LinearLayoutManager(parent, LinearLayoutManager.HORIZONTAL, false));
		list.setAdapter(adapter);
		new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
				ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
			@Override
			public boolean onMove(@NonNull RecyclerView recyclerView,
			                      @NonNull RecyclerView.ViewHolder dragged,
			                      @NonNull RecyclerView.ViewHolder target) {
				int from = dragged.getBindingAdapterPosition();
				int to = target.getBindingAdapterPosition();
				if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
					return false;
				}
				RelationMembers.move(members, from, to);
				adapter.notifyItemMoved(from, to);
				return true;
			}

			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
			}

			@Override
			public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
				super.onSelectedChanged(viewHolder, actionState);
				if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
					// Keeps the editor's scroll view from taking over the drag.
					list.getParent().requestDisallowInterceptTouchEvent(true);
				}
			}

			@Override
			public void clearView(@NonNull RecyclerView recyclerView,
			                      @NonNull RecyclerView.ViewHolder viewHolder) {
				super.clearView(recyclerView, viewHolder);
				membersChanged();
			}
		}).attachToRecyclerView(list);

		if (highlightedKey != null) {
			for (int index = 0; index < members.length(); index++) {
				if (highlightedKey.equals(keyAt(index))) {
					list.scrollToPosition(index);
					break;
				}
			}
		}
	}

	public boolean contains(OsmEntity entity) {
		return RelationMembers.contains(members, entity.osmType.name(), entity.osmID);
	}

	/** Adds the entity at the end, with its route count for a crag, see the constructor. */
	public void add(OsmEntity entity, int routeCount) {
		String key = RelationMembers.key(entity.osmType.name(), entity.osmID);
		resolved.put(key, entity);
		routeCounts.put(key, routeCount);
		RelationMembers.append(members, entity.osmType.name(), entity.osmID, "");
		adapter.notifyItemInserted(members.length() - 1);
		list.scrollToPosition(members.length() - 1);
		membersChanged();
	}

	public int size() {
		return members.length();
	}

	/** The stored member at the index, null when it is not downloaded. */
	public OsmEntity memberAt(int index) {
		return resolved.get(keyAt(index));
	}

	/** The route number of each member, 0 for one that takes none; see ClimbingRouteCounter. */
	public int[] routeNumbers() {
		int[] result = new int[members.length()];
		int number = 0;
		for (int index = 0; index < result.length; index++) {
			OsmEntity member = memberAt(index);
			if (member != null && ClimbingRouteCounter.takesRouteNumber(member)) {
				result[index] = ++number;
			}
		}
		return result;
	}

	/**
	 * The member at the index as the map and the strip show it, placed where it is, or null when
	 * it is not downloaded.
	 */
	public DisplayableGeoNode pinAt(int index) {
		OsmEntity member = memberAt(index);
		if (member instanceof OsmNode) {
			return DataManager.toDisplayableNode((OsmNode) member);
		}
		if (member instanceof OsmCollectionEntity) {
			Integer routeCount = routeCounts.get(keyAt(index));
			return DataManager.toDisplayableNode((OsmCollectionEntity) member,
					routeCount != null ? routeCount : 0);
		}
		return null;
	}

	private String keyAt(int index) {
		JSONObject member = members.optJSONObject(index);
		return member == null ? "" : RelationMembers.key(member);
	}

	private void membersChanged() {
		// The route numbers follow the order.
		list.post(adapter::notifyDataSetChanged);
		listener.onMembersChanged();
	}

	private void remove(int index) {
		members.remove(index);
		adapter.notifyItemRemoved(index);
		membersChanged();
	}

	private void bind(View card, int index) {
		DisplayableGeoNode pin = pinAt(index);
		boolean downloaded = pin != null;
		if (!downloaded) {
			pin = new DisplayableGeoNode(new GeoNode(new JSONObject()));
		}
		String name = downloaded ? pin.getGeoNode().getName()
				: parent.getString(R.string.member_not_downloaded);
		MemberIconCard.show(parent, card, pin, name, routeNumbers()[index]);
		card.setBackgroundResource(keyAt(index).equals(highlightedKey)
				? R.drawable.bg_route_member_icon_selected : R.drawable.bg_route_member_icon);
	}

	private final class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberHolder> {
		@NonNull
		@Override
		public MemberHolder onCreateViewHolder(@NonNull ViewGroup group, int viewType) {
			return new MemberHolder(parent.getLayoutInflater()
					.inflate(R.layout.list_item_climbing_member_icon, group, false));
		}

		@Override
		public void onBindViewHolder(@NonNull MemberHolder holder, int position) {
			bind(holder.itemView, position);
		}

		@Override
		public int getItemCount() {
			return members.length();
		}

		final class MemberHolder extends RecyclerView.ViewHolder {
			MemberHolder(View view) {
				super(view);
				view.setOnClickListener(card -> {
					int index = getBindingAdapterPosition();
					if (index == RecyclerView.NO_POSITION) {
						return;
					}
					PopupMenu menu = new PopupMenu(parent, card);
					menu.getMenu().add(R.string.remove_member);
					menu.setOnMenuItemClickListener(item -> {
						remove(index);
						return true;
					});
					menu.show();
				});
			}
		}
	}
}

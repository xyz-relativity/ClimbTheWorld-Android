package com.climbtheworld.app.map.editor;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * The members of a relation being edited, as a strip in their order, left to right as along the
 * wall: long press and drag one to move it, tap it to take it out. The members array is changed
 * in place.
 */
public class RelationMemberList {
	private static final int MEMBER_LABEL_MIN_WIDTH = Globals.convertDpToPixel(80).intValue();

	public interface OnMembersChangedListener {
		void onMembersChanged();
	}

	private final AppCompatActivity parent;
	private final JSONArray members;
	private final Map<String, OsmEntity> resolved;
	private final String highlightedKey;
	private final OnMembersChangedListener listener;
	private final RecyclerView list;
	private final MemberAdapter adapter = new MemberAdapter();

	/**
	 * @param resolved       the stored members by RelationMembers.key, as OsmEditStore.loadMembers
	 *                       gives them; members added here are put in it too.
	 * @param highlightedKey the member to outline, the element whose editor opened this one, or
	 *                       null.
	 */
	public RelationMemberList(AppCompatActivity parent, ViewGroup container, boolean area,
	                          JSONArray members, Map<String, OsmEntity> resolved,
	                          String highlightedKey, OnMembersChangedListener listener) {
		this.parent = parent;
		this.members = members;
		this.resolved = resolved;
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

	/** Adds the entity at the end. */
	public void add(OsmEntity entity) {
		resolved.put(RelationMembers.key(entity.osmType.name(), entity.osmID), entity);
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

	/** The member as the map and the strip show it, placed where it is. */
	public static DisplayableGeoNode toDisplayable(OsmEntity member) {
		if (member instanceof OsmNode) {
			return DataManagerNew.toDisplayableNode((OsmNode) member);
		}
		return DataManagerNew.toDisplayableNode((OsmCollectionEntity) member);
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
		OsmEntity member = memberAt(index);
		GeoNode poi = member != null
				? toDisplayable(member).getGeoNode() : new GeoNode(new JSONObject());

		Drawable icon = new PoiMarkerDrawable(parent, new DisplayableGeoNode(poi)).getDrawable();
		int iconWidth = Math.max(icon.getIntrinsicWidth() * 2, 1);
		ImageView image = card.findViewById(R.id.memberIconImage);
		image.setImageDrawable(icon);
		image.getLayoutParams().width = iconWidth;
		image.getLayoutParams().height = Math.max(icon.getIntrinsicHeight() * 2, 1);
		card.findViewById(R.id.memberIconLabel).getLayoutParams().width =
				Math.max(iconWidth, MEMBER_LABEL_MIN_WIDTH);

		String name = member != null ? poi.getName() : parent.getString(R.string.member_not_downloaded);
		TextView nameView = card.findViewById(R.id.memberIconName);
		nameView.setText(name);
		TextView numberView = card.findViewById(R.id.memberIconNumber);
		int routeNumber = routeNumbers()[index];
		if (routeNumber > 0) {
			numberView.setText(routeNumber + ".");
			numberView.setVisibility(View.VISIBLE);
			nameView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
			name = routeNumber + ". " + name;
		} else {
			numberView.setVisibility(View.GONE);
			nameView.setGravity(Gravity.CENTER);
		}
		card.setBackgroundResource(keyAt(index).equals(highlightedKey)
				? R.drawable.bg_route_member_icon_selected : R.drawable.bg_route_member_icon);
		card.setContentDescription(name);
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

package com.climbtheworld.app.augmentedreality.ARCore;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.placement.AlignmentChoices;
import com.climbtheworld.app.augmentedreality.placement.AlignmentOrder;
import com.climbtheworld.app.augmentedreality.placement.AlignmentSuggestions;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.widget.climbing.ClimbingParent;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.ClimbingPoiRow;
import com.climbtheworld.app.utils.views.TextWatchers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import needle.UiRelatedTask;

/**
 * Asks which mapped route to align on, from the routes around the user, as their crags list them,
 * see AlignmentOrder, in the rows of the lists of climbing POIs, see ClimbingPoiRow, each name
 * after the route's number in its crag, as the map's pins show it, see
 * ClimbingRouteCounter.numberRoutes, with a quick filter on their names over them, see
 * AlignmentChoices. The list opens with the closest route in its middle, its neighbours along
 * the wall around it, and so it does again for what the user types in the filter. Once a route
 * is aligned on, the routes that would best set the heading come first, under a title of their
 * own, see AlignmentSuggestions, and the rest under another. The routes already aligned on are
 * listed wherever they are, each with a button to delete its alignment.
 */
class AlignmentPicker {
	// How far around where the user seems to be routes are looked for, as GPS can be that far
	// off.
	private static final double SEARCH_METERS = 150;
	// How many routes are listed, around the closest in order; the filter looks through all of
	// them.
	private static final int MAX_ROUTES = 40;
	// The list's rows: a section title, or a route.
	private static final int TYPE_TITLE = 0;
	private static final int TYPE_ROUTE = 1;

	private final AppCompatActivity activity;
	private AlertDialog dialog;

	AlignmentPicker(AppCompatActivity activity) {
		this.activity = activity;
	}

	/**
	 * Where a node is from a point, in meters east and north of it.
	 */
	private static double[] eastNorthMeters(GeoNode node, double latitude, double longitude) {
		return AlignmentSuggestions.eastNorthMeters(node.decimalLatitude, node.decimalLongitude,
				latitude, longitude);
	}

	private static double distanceMeters(GeoNode node, double latitude, double longitude) {
		double[] eastNorth = eastNorthMeters(node, latitude, longitude);
		return Math.hypot(eastNorth[0], eastNorth[1]);
	}

	/**
	 * The routes that would best set the heading, aligned on next, see AlignmentSuggestions.
	 *
	 * @param aligned the OSM IDs of the routes aligned on now
	 */
	private static List<GeoNode> suggested(List<GeoNode> routes, double latitude,
	                                       double longitude, Set<Long> aligned) {
		List<GeoNode> candidates = new ArrayList<>();
		List<double[]> alignedEastNorth = new ArrayList<>();
		for (GeoNode route : routes) {
			if (aligned.contains(route.osmID)) {
				alignedEastNorth.add(eastNorthMeters(route, latitude, longitude));
			} else {
				candidates.add(route);
			}
		}
		return AlignmentSuggestions.suggested(candidates,
				route -> eastNorthMeters(route, latitude, longitude), alignedEastNorth);
	}

	/**
	 * The routes around a point, as their crags list them, see AlignmentOrder: only nodes, which
	 * are at one spot, and with a pin of their own, as the A.R. view shows them, all of them, for
	 * the filter to look through. The routes aligned on are among them, however far.
	 *
	 * @param routeNumbers filled with the routes' numbers in their crags, by OSM ID
	 */
	private List<GeoNode> loadRoutes(double latitude, double longitude, long excludedId,
	                                 Set<Long> aligned, Map<Long, Integer> routeNumbers) {
		DataManager dataManager = new DataManager();
		Map<Long, DisplayableGeoNode> found = new HashMap<>();
		dataManager.loadAround(activity, new Vector4d(latitude, longitude, 0, 0), SEARCH_METERS,
				found);
		List<GeoNode> routes = new ArrayList<>();
		for (DisplayableGeoNode poi : found.values()) {
			GeoNode node = poi.getGeoNode();
			if (node.osmID != excludedId && node.getNodeType() == OsmEntity.EntityClimbingType.route) {
				routes.add(node);
			}
		}

		// The routes aligned on farther away, for their alignments to be deleted too.
		Set<Long> missing = new HashSet<>(aligned);
		for (GeoNode route : routes) {
			missing.remove(route.osmID);
		}
		if (!missing.isEmpty()) {
			for (OsmNode node : dataManager.loadNodeData(activity, new ArrayList<>(missing))
					.values()) {
				routes.add(DataManager.toDisplayableNode(node).getGeoNode());
			}
		}
		routes.sort(Comparator.comparingDouble(node -> distanceMeters(node, latitude, longitude)));
		return AlignmentOrder.ordered(routes,
				loadCragOrders(dataManager, latitude, longitude, routeNumbers), node -> node.osmID);
	}

	/**
	 * Per crag relation around a point, the OSM IDs of its member nodes in the relation's order.
	 * Any crag with a route around reaches the point, as its bounding box holds the route.
	 *
	 * @param routeNumbers filled with the routes' numbers in these crags, by OSM ID, the first
	 *                     crag's for a route of several
	 */
	private List<List<Long>> loadCragOrders(DataManager dataManager, double latitude,
	                                        double longitude, Map<Long, Integer> routeNumbers) {
		ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(activity);
		List<List<Long>> crags = new ArrayList<>();
		for (OsmCollectionEntity relation : dataManager.loadRelationsBBox(activity,
				DataManager.computeBoundingBox(new Vector4d(latitude, longitude, 0, 0),
						SEARCH_METERS))) {
			if (relation.entityClimbingType == OsmEntity.EntityClimbingType.crag) {
				crags.add(RelationMembers.nodeRefs(RelationMembers.list(relation.getMembers())));
				routeCounter.numberRoutes(relation).forEach(routeNumbers::putIfAbsent);
			}
		}
		return crags;
	}

	/**
	 * @param latitude   where the user seems to be
	 * @param longitude  where the user seems to be
	 * @param excludedId the node being edited, which is not to align on
	 * @param aligned    the OSM IDs of the routes aligned on
	 */
	void show(double latitude, double longitude, long excludedId, Set<Long> aligned,
	          Listener listener) {
		// Kept up to date as the user deletes alignments in the dialog.
		Set<Long> alignedLeft = new HashSet<>(aligned);
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<List<GeoNode>>() {
			// What each route is part of, by OSM ID, see ClimbingParent.
			private final Map<Long, String> parentLabels = new HashMap<>();
			// Each route's number in its crag, by OSM ID.
			private final Map<Long, Integer> routeNumbers = new HashMap<>();

			@Override
			protected List<GeoNode> doWork() {
				List<GeoNode> routes = loadRoutes(latitude, longitude, excludedId, alignedLeft,
						routeNumbers);
				for (GeoNode route : routes) {
					parentLabels.put(route.osmID, ClimbingParent.labelOf(activity, route));
				}
				return routes;
			}

			@Override
			protected void thenDoUiRelatedWork(List<GeoNode> routes) {
				if (activity.isFinishing() || activity.isDestroyed()) {
					return;
				}
				showDialog(routes, parentLabels, routeNumbers, latitude, longitude, alignedLeft,
						listener);
			}
		});
	}

	void dismiss() {
		if (dialog != null) {
			dialog.dismiss();
		}
	}

	/**
	 * @param routes       the routes around, in the order to list them, see loadRoutes
	 * @param parentLabels what each route is part of, by OSM ID
	 * @param routeNumbers each route's number in its crag, by OSM ID
	 * @param aligned      the OSM IDs of the routes aligned on, which deleting an alignment
	 *                     updates
	 */
	private void showDialog(List<GeoNode> routes, Map<Long, String> parentLabels,
	                        Map<Long, Integer> routeNumbers, double latitude, double longitude,
	                        Set<Long> aligned, Listener listener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				.setTitle(R.string.ar_placement_align_title)
				.setNegativeButton(android.R.string.cancel,
						(cancelled, which) -> listener.onCancelled())
				.setOnCancelListener(cancelled -> listener.onCancelled());
		if (!aligned.isEmpty()) {
			builder.setNeutralButton(R.string.ar_placement_align_clear,
					(clicked, which) -> listener.onCleared());
		}
		if (routes.isEmpty()) {
			builder.setMessage(R.string.ar_placement_align_none);
			dialog = builder.show();
			return;
		}

		View content = activity.getLayoutInflater()
				.inflate(R.layout.fragment_dialog_align_route, null);
		builder.setView(content);
		dialog = builder.create();
		EditText filter = content.findViewById(R.id.editFind);
		RowsAdapter adapter = new RowsAdapter(routes, parentLabels, routeNumbers, latitude,
				longitude, aligned, filter, listener);
		ListView list = content.findViewById(R.id.listAlignRoutes);
		filter.addTextChangedListener(TextWatchers.afterTextChanged(typed -> {
			adapter.refresh();
			centre(list, adapter, adapter.closestRow());
		}));
		adapter.refresh();
		list.setAdapter(adapter);
		// The filter field takes the focus, but most of the time the route is the closest or one
		// of its neighbours, in the list's middle rows.
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		dialog.show();
		// Once the list is laid out, for its height.
		list.post(() -> centre(list, adapter, adapter.closestRow()));
	}

	/**
	 * Scrolls a list for a row to be in its middle, or as near as the list's ends let it.
	 *
	 * @param row the row's position, none for -1
	 */
	private static void centre(ListView list, BaseAdapter adapter, int row) {
		if (row < 0) {
			return;
		}
		View view = adapter.getView(row, null, list);
		view.measure(View.MeasureSpec.makeMeasureSpec(list.getWidth(), View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		list.setSelectionFromTop(row, (list.getHeight() - view.getMeasuredHeight()) / 2);
	}

	/**
	 * The list's rows: the routes the filter lets through, see AlignmentChoices, the suggested
	 * ones first under a title of their own, when there are any, and the rest under another.
	 */
	private class RowsAdapter extends BaseAdapter {
		private final List<GeoNode> routes;
		// What each route is part of, by OSM ID.
		private final Map<Long, String> parentLabels;
		// Each route's number in its crag, by OSM ID.
		private final Map<Long, Integer> routeNumbers;
		// From where the user is, as the routes were looked for around; the map shown from a row
		// closes the dialog as the back button does.
		private final ClimbingPoiRow poiRows;
		private final double latitude;
		private final double longitude;
		// The OSM IDs of the routes aligned on, which deleting an alignment updates.
		private final Set<Long> aligned;
		// Listed wherever they are as the list opens, even once their alignment is deleted.
		private final Set<Long> alignedAtOpen;
		private final EditText filter;
		private final Listener listener;
		// A string resource ID for a title, or a GeoNode.
		private final List<Object> rows = new ArrayList<>();

		RowsAdapter(List<GeoNode> routes, Map<Long, String> parentLabels,
		            Map<Long, Integer> routeNumbers, double latitude, double longitude,
		            Set<Long> aligned, EditText filter, Listener listener) {
			this.routes = routes;
			this.parentLabels = parentLabels;
			this.routeNumbers = routeNumbers;
			this.poiRows = new ClimbingPoiRow(activity, () -> dialog.cancel(), latitude,
					longitude);
			this.latitude = latitude;
			this.longitude = longitude;
			this.aligned = aligned;
			this.alignedAtOpen = new HashSet<>(aligned);
			this.filter = filter;
			this.listener = listener;
		}

		/**
		 * Lists the rows again, for what the filter holds and the routes aligned on now.
		 */
		void refresh() {
			String text = filter.getText().toString();
			List<GeoNode> suggested = suggested(routes, latitude, longitude, aligned);
			suggested = AlignmentChoices.listed(suggested, text, GeoNode::getName,
					route -> false, this::distance, suggested.size());
			List<GeoNode> others = new ArrayList<>(routes);
			others.removeAll(suggested);
			List<GeoNode> listed = AlignmentChoices.listed(others, text, GeoNode::getName,
					route -> alignedAtOpen.contains(route.osmID), this::distance, MAX_ROUTES);
			rows.clear();
			if (!suggested.isEmpty()) {
				rows.add(R.string.ar_placement_align_suggested);
				rows.addAll(suggested);
				if (!listed.isEmpty()) {
					rows.add(R.string.ar_placement_align_by_crag);
				}
			}
			rows.addAll(listed);
			notifyDataSetChanged();
		}

		/**
		 * @return the row of the closest route listed, -1 for none
		 */
		int closestRow() {
			return AlignmentChoices.closest(rows, row -> row instanceof GeoNode
					? distance((GeoNode) row) : Double.POSITIVE_INFINITY);
		}

		private double distance(GeoNode route) {
			return distanceMeters(route, latitude, longitude);
		}

		@Override
		public int getCount() {
			return rows.size();
		}

		@Override
		public Object getItem(int i) {
			return rows.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public int getItemViewType(int i) {
			return rows.get(i) instanceof Integer ? TYPE_TITLE : TYPE_ROUTE;
		}

		@Override
		public boolean areAllItemsEnabled() {
			return false;
		}

		@Override
		public boolean isEnabled(int i) {
			return getItemViewType(i) == TYPE_ROUTE;
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			if (getItemViewType(i) == TYPE_TITLE) {
				TextView title = view != null ? (TextView) view
						: (TextView) activity.getLayoutInflater().inflate(
						R.layout.list_item_section_title, viewGroup, false);
				title.setText((Integer) rows.get(i));
				return title;
			}
			GeoNode route = (GeoNode) rows.get(i);
			if (view == null) {
				view = View.inflate(activity, R.layout.list_item_alignment_route, null);
			}
			// The number by the name rather than on the pin, as the relation editor's members
			// show it, see MemberIconCard.
			Integer routeNumber = routeNumbers.get(route.osmID);
			String name = routeNumber != null ? routeNumber + ". " + route.getName()
					: route.getName();
			view = poiRows.build(view, new DisplayableGeoNode(route), name,
					parentLabels.getOrDefault(route.osmID, ""));
			view.setOnClickListener(clicked -> {
				dialog.dismiss();
				listener.onPicked(route);
			});
			View delete = view.findViewById(R.id.buttonDeleteAlignment);
			delete.setVisibility(aligned.contains(route.osmID) ? View.VISIBLE : View.GONE);
			delete.setOnClickListener(clicked -> {
				aligned.remove(route.osmID);
				listener.onDeleted(route);
				// The suggestions follow the routes aligned on.
				refresh();
				if (aligned.isEmpty()) {
					dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
				}
			});
			return view;
		}
	}

	interface Listener {
		void onPicked(GeoNode route);

		/**
		 * The user deleted the alignment on a route; the dialog stays open.
		 */
		void onDeleted(GeoNode route);

		void onCleared();

		/**
		 * The user closed the list without picking a route.
		 */
		void onCancelled();
	}
}

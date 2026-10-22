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
import com.climbtheworld.app.augmentedreality.placement.AlignmentSuggestions;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.TextWatchers;
import com.climbtheworld.app.utils.views.dialogs.DialogueUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import needle.UiRelatedTask;

/**
 * Asks which mapped route to align on, from the routes around the user, closest first, in rows
 * like the search results, with a quick filter on their names over them, see AlignmentChoices.
 * Once a route is aligned on, the routes that would best set the heading come first, under a
 * title of their own, see AlignmentSuggestions, and the rest under another. The routes already
 * aligned on are listed wherever they are, each with a button to delete its alignment.
 */
class AlignmentPicker {
	// How far around where the user seems to be routes are looked for, as GPS can be that far
	// off.
	private static final double SEARCH_METERS = 150;
	// How many routes are listed, the closest; the filter looks through all of them.
	private static final int MAX_ROUTES = 40;
	private static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;
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
		double north = (node.decimalLatitude - latitude) * METERS_PER_DEGREE;
		double east = (node.decimalLongitude - longitude) * METERS_PER_DEGREE
				* Math.cos(Math.toRadians(latitude));
		return new double[]{east, north};
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
	 * The routes around a point, closest first: only nodes, which are at one spot, and with a pin
	 * of their own, as the A.R. view shows them, all of them, for the filter to look through. The
	 * routes aligned on are among them, however far.
	 */
	private List<GeoNode> loadRoutes(double latitude, double longitude, long excludedId,
	                                 Set<Long> aligned) {
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
		return routes;
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
			@Override
			protected List<GeoNode> doWork() {
				return loadRoutes(latitude, longitude, excludedId, alignedLeft);
			}

			@Override
			protected void thenDoUiRelatedWork(List<GeoNode> routes) {
				if (activity.isFinishing() || activity.isDestroyed()) {
					return;
				}
				showDialog(routes, latitude, longitude, alignedLeft, listener);
			}
		});
	}

	void dismiss() {
		if (dialog != null) {
			dialog.dismiss();
		}
	}

	/**
	 * @param routes  the routes around, closest first, see loadRoutes
	 * @param aligned the OSM IDs of the routes aligned on, which deleting an alignment updates
	 */
	private void showDialog(List<GeoNode> routes, double latitude, double longitude,
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
		RowsAdapter adapter = new RowsAdapter(routes, latitude, longitude, aligned, filter,
				listener);
		filter.addTextChangedListener(TextWatchers.afterTextChanged(typed -> adapter.refresh()));
		adapter.refresh();
		((ListView) content.findViewById(R.id.listAlignRoutes)).setAdapter(adapter);
		// The filter field takes the focus, but most of the time the closest route is the one.
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		dialog.show();
	}

	/**
	 * The list's rows: the routes the filter lets through, see AlignmentChoices, the suggested
	 * ones first under a title of their own, when there are any, and the rest under another.
	 */
	private class RowsAdapter extends BaseAdapter {
		private final List<GeoNode> routes;
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

		RowsAdapter(List<GeoNode> routes, double latitude, double longitude, Set<Long> aligned,
		            EditText filter, Listener listener) {
			this.routes = routes;
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
					route -> false, suggested.size());
			List<GeoNode> others = new ArrayList<>(routes);
			others.removeAll(suggested);
			List<GeoNode> listed = AlignmentChoices.listed(others, text, GeoNode::getName,
					route -> alignedAtOpen.contains(route.osmID), MAX_ROUTES);
			rows.clear();
			if (!suggested.isEmpty()) {
				rows.add(R.string.ar_placement_align_suggested);
				rows.addAll(suggested);
				if (!listed.isEmpty()) {
					rows.add(R.string.ar_placement_align_closest);
				}
			}
			rows.addAll(listed);
			notifyDataSetChanged();
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
			String distance = activity.getString(R.string.ar_placement_align_distance,
					Math.round(distanceMeters(route, latitude, longitude)));
			if (view == null) {
				view = View.inflate(activity, R.layout.list_item_alignment_route, null);
			}
			view = ListViewItemBuilder.getPaddedBuilder(activity, view, true)
					.setTitle(route.getName())
					.setDescription(distance + "\n"
							+ DialogueUtils.buildDescription(activity, route))
					.setIcon(new PoiMarkerDrawable(activity, new DisplayableGeoNode(route)))
					.build();
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

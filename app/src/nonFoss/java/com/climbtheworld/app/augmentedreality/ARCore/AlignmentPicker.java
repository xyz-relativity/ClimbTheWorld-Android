package com.climbtheworld.app.augmentedreality.ARCore;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
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
 * like the search results. The routes already aligned on are listed wherever they are, each with
 * a button to delete its alignment.
 */
class AlignmentPicker {
	// How far around where the user seems to be routes are looked for, as GPS can be that far
	// off.
	private static final double SEARCH_METERS = 150;
	private static final int MAX_ROUTES = 40;
	private static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;

	private final AppCompatActivity activity;
	private AlertDialog dialog;

	AlignmentPicker(AppCompatActivity activity) {
		this.activity = activity;
	}

	private static double distanceMeters(GeoNode node, double latitude, double longitude) {
		double north = (node.decimalLatitude - latitude) * METERS_PER_DEGREE;
		double east = (node.decimalLongitude - longitude) * METERS_PER_DEGREE
				* Math.cos(Math.toRadians(latitude));
		return Math.hypot(north, east);
	}

	/**
	 * The routes around a point, closest first: only nodes, which are at one spot, and with a pin
	 * of their own, as the A.R. view shows them. The routes aligned on are among them, however far.
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
			if (node.osmID != excludedId && node.getNodeType() == GeoNode.NodeTypes.route) {
				routes.add(node);
			}
		}
		Comparator<GeoNode> closestFirst =
				Comparator.comparingDouble(node -> distanceMeters(node, latitude, longitude));
		routes.sort(closestFirst);
		if (routes.size() > MAX_ROUTES) {
			routes = new ArrayList<>(routes.subList(0, MAX_ROUTES));
		}

		// The routes aligned on and left out above, for their alignments to be deleted too.
		Set<Long> missing = new HashSet<>(aligned);
		for (GeoNode route : routes) {
			missing.remove(route.osmID);
		}
		if (!missing.isEmpty()) {
			for (OsmNode node : dataManager.loadNodeData(activity, new ArrayList<>(missing))
					.values()) {
				routes.add(DataManager.toDisplayableNode(node).getGeoNode());
			}
			routes.sort(closestFirst);
		}
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

		ListView list = new ListView(activity);
		builder.setView(list);
		dialog = builder.create();
		list.setAdapter(new BaseAdapter() {
			@Override
			public int getCount() {
				return routes.size();
			}

			@Override
			public Object getItem(int i) {
				return routes.get(i);
			}

			@Override
			public long getItemId(int i) {
				return i;
			}

			@Override
			public View getView(int i, View view, ViewGroup viewGroup) {
				GeoNode route = routes.get(i);
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
					notifyDataSetChanged();
					if (aligned.isEmpty()) {
						dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
					}
				});
				return view;
			}
		});
		dialog.show();
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

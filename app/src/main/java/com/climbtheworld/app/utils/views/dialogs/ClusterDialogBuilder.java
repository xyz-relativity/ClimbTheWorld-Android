package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.climbing.ClimbingParent;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.map.widget.climbing.ClusterContentResolver;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.ClimbingPoiRow;
import com.climbtheworld.app.utils.views.FilteredListAdapter;
import com.climbtheworld.app.utils.views.TextWatchers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import needle.UiRelatedTask;

/**
 * Lists what a map cluster holds, see ClimbingPoiRow: the outermost relations its POIs belong to,
 * followed by the POIs that are in no relation. Each entry opens its own info dialog.
 */
public class ClusterDialogBuilder {
	// Larger than any relation rank, so single POIs sort after every relation.
	private static final int POI_RANK_OFFSET = 10;

	private ClusterDialogBuilder() {
		//hide constructor
	}

	/**
	 * @param onZoomIn zooms the map into the cluster, offered as a dialog button
	 */
	public static void showClusterDialog(AppCompatActivity parent, List<DisplayableGeoNode> pois,
	                                     Runnable onZoomIn) {
		DialogBuilder.showLoadingDialogue(parent,
				parent.getResources().getString(R.string.loading_message), null);
		Constants.ASYNC_TASK_EXECUTOR.execute(new UiRelatedTask<List<ClusterEntry>>() {
			@Override
			protected List<ClusterEntry> doWork() {
				return loadEntries(parent, pois);
			}

			@Override
			protected void thenDoUiRelatedWork(List<ClusterEntry> entries) {
				DialogBuilder.dismissLoadingDialogue();
				showDialog(parent, pois.size(), entries, onZoomIn);
			}
		});
	}

	private static void showDialog(AppCompatActivity parent, int poiCount,
	                               List<ClusterEntry> entries, Runnable onZoomIn) {
		AlertDialog alertDialog = DialogBuilder.getNewDialog(parent, true);
		alertDialog.setCancelable(true);
		alertDialog.setCanceledOnTouchOutside(true);
		alertDialog.setView(buildClusterDialog(parent, alertDialog.getListView(), poiCount,
				entries));
		DialogueUtils.addOkButton(parent, alertDialog);
		alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL,
				parent.getResources().getString(R.string.zoom_in), (dialog, which) -> {
					dialog.dismiss();
					onZoomIn.run();
				});
		alertDialog.create();
		// The filter field takes the focus, but the list is what the user came for.
		alertDialog.getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		alertDialog.show();
	}

	private static View buildClusterDialog(AppCompatActivity parent, ViewGroup container,
	                                       int poiCount, List<ClusterEntry> entries) {
		View result = parent.getLayoutInflater()
				.inflate(R.layout.fragment_dialog_cluster, container, false);

		((TextView) result.findViewById(R.id.textTitle)).setText(
				parent.getResources().getString(R.string.points_of_interest_value, poiCount));
		((ImageView) result.findViewById(R.id.imageIcon)).setImageDrawable(
				MarkerUtils.getClusterIcon(parent, DisplayableGeoNode.CLUSTER_DEFAULT_COLOR,
						DisplayableGeoNode.POI_ICON_ALPHA_VISIBLE));
		// A cluster has no OSM element to share, edit or navigate to.
		result.findViewById(R.id.menu).setVisibility(View.GONE);

		// The dialog closes with the other dialogs.
		ClimbingPoiRow rows = new ClimbingPoiRow(parent, null);
		FilteredListAdapter<ClusterEntry> adapter = new FilteredListAdapter<ClusterEntry>(entries) {
			@Override
			protected boolean isVisible(int i, String filter) {
				return initialList.get(i).getName().toLowerCase().contains(filter);
			}

			@Override
			public View getView(int i, View view, ViewGroup viewGroup) {
				ClusterEntry entry = visibleList.get(i);
				view = rows.build(view, new DisplayableGeoNode(entry.poi), entry.getName(),
						entry.parentLabel);
				view.setOnClickListener(clicked -> entry.showInfo(parent));
				return view;
			}
		};

		EditText filter = result.findViewById(R.id.editFind);
		filter.addTextChangedListener(TextWatchers.afterTextChanged(s -> adapter.applyFilter(s.toString())));

		((ListView) result.findViewById(R.id.listGroupItems)).setAdapter(adapter);
		return result;
	}

	private static List<ClusterEntry> loadEntries(AppCompatActivity parent,
	                                              List<DisplayableGeoNode> pois) {
		ClusterContentResolver.Content content =
				ClusterContentResolver.forDatabase(parent).resolve(pois);
		ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(parent);
		List<ClusterEntry> result = new ArrayList<>();
		for (OsmCollectionEntity relation : content.relations) {
			MapCoordinate center = DataManager.collectionCenter(relation);
			GeoNode poi = routeCounter.pin(relation, center);
			result.add(new ClusterEntry(poi, relation, center,
					ClimbingParent.labelOf(parent, poi)));
		}
		for (DisplayableGeoNode poi : content.pois) {
			result.add(new ClusterEntry(poi.geoNode, null, null,
					ClimbingParent.labelOf(parent, poi.geoNode)));
		}
		// From the outermost relations down to the single POIs.
		Collections.sort(result, Comparator.comparingInt(ClusterEntry::getRank)
				.thenComparing(ClusterEntry::getName, String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	/**
	 * A relation (with its center) or a single POI (without), with what it is part of.
	 */
	private record ClusterEntry(GeoNode poi, OsmCollectionEntity relation,
	                            MapCoordinate coordinate, String parentLabel) {

		private String getName() {
			return !poi.getName().isEmpty() ? poi.getName() : Long.toString(poi.osmID);
		}

		/**
		 * Relations before POIs, each from the widest kind of place to a single route.
		 */
		private int getRank() {
			int typeRank;
			switch (poi.getNodeType()) {
				case area:
					typeRank = 0;
					break;
				case crag:
					typeRank = 1;
					break;
				case artificial:
					typeRank = 2;
					break;
				case route:
					typeRank = 3;
					break;
				default:
					typeRank = 4;
					break;
			}
			return relation != null ? typeRank : POI_RANK_OFFSET + typeRank;
		}

		private void showInfo(AppCompatActivity parent) {
			if (relation == null) {
				NodeDialogBuilder.showNodeInfoDialog(parent, poi);
			} else {
				NodeDialogBuilder.showCollectionInfoDialog(parent, relation, coordinate);
			}
		}
	}
}

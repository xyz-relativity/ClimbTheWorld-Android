package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.map.widget.climbing.ClusterContentResolver;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.FilteredListAdapter;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.Sorters;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import needle.UiRelatedTask;

/**
 * Lists what a map cluster holds: the outermost relations its POIs belong to, followed by the
 * POIs that are in no relation. Each entry opens its own info dialog.
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
			private String errorMessage;

			@Override
			protected List<ClusterEntry> doWork() {
				try {
					return loadEntries(parent, pois);
				} catch (JSONException exception) {
					errorMessage = parent.getString(
							R.string.exception_message, exception.getMessage());
					return Collections.emptyList();
				}
			}

			@Override
			protected void thenDoUiRelatedWork(List<ClusterEntry> entries) {
				DialogBuilder.dismissLoadingDialogue();
				if (errorMessage != null) {
					DialogBuilder.showErrorDialog(parent, errorMessage, null);
					return;
				}
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

		FilteredListAdapter<ClusterEntry> adapter = new FilteredListAdapter<ClusterEntry>(entries) {
			@Override
			protected boolean isVisible(int i, String filter) {
				return initialList.get(i).getName().toLowerCase().contains(filter);
			}

			@Override
			public View getView(int i, View view, ViewGroup viewGroup) {
				ClusterEntry entry = visibleList.get(i);
				view = ListViewItemBuilder.getPaddedBuilder(parent, view, true)
						.setTitle(entry.getName())
						.setDescription(entry.description)
						.setIcon(new PoiMarkerDrawable(parent, new DisplayableGeoNode(entry.poi)))
						.build();
				view.setOnClickListener(clicked -> entry.showInfo(parent));
				return view;
			}
		};

		EditText filter = result.findViewById(R.id.editFind);
		filter.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				adapter.applyFilter(s.toString());
			}
		});

		((ListView) result.findViewById(R.id.listGroupItems)).setAdapter(adapter);
		return result;
	}

	private static List<ClusterEntry> loadEntries(AppCompatActivity parent,
	                                              List<DisplayableGeoNode> pois)
			throws JSONException {
		ClusterContentResolver.Content content =
				ClusterContentResolver.forDatabase(parent).resolve(pois);
		ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(parent);
		List<ClusterEntry> result = new ArrayList<>();
		for (OsmCollectionEntity relation : content.relations) {
			ClimbingRouteCounter.RouteSummary routes = routeCounter.summarize(relation);
			MapCoordinate center = DataManagerNew.collectionCenter(relation);
			// A copy, so the route count drawn on the icon stays out of the relation's own tags.
			GeoNode poi = new GeoNode(new JSONObject(relation.jsonNodeInfo.toString()));
			poi.updatePOILocation(center.getLatitude(), center.getLongitude(), 0);
			if (routes.getRouteCount() > 0) {
				poi.setKey(ClimbingTags.KEY_ROUTES, Integer.toString(routes.getRouteCount()));
			}
			result.add(new ClusterEntry(poi, relation, center, routes,
					describeRelation(parent, relation, routes)));
		}
		for (DisplayableGeoNode poi : content.pois) {
			result.add(new ClusterEntry(poi.geoNode, null, null, null,
					DialogueUtils.buildDescription(parent, poi.geoNode)));
		}
		// From the outermost relations down to the single POIs.
		Collections.sort(result, Comparator.comparingInt(ClusterEntry::getRank)
				.thenComparing(ClusterEntry::getName, String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	private static String describeRelation(AppCompatActivity parent,
	                                       OsmCollectionEntity relation,
	                                       ClimbingRouteCounter.RouteSummary routes) {
		StringBuilder result = new StringBuilder(
				parent.getString(relation.entityClimbingType.getNameId()));
		String separator = ": ";
		for (GeoNode.ClimbingStyle style : Sorters.sortStyles(parent,
				new ArrayList<>(routes.getStyleCounts().keySet()))) {
			result.append(separator).append(parent.getString(style.getNameId()));
			separator = ", ";
		}
		if (routes.getRouteCount() == 0) {
			return result.toString();
		}

		result.append('\n').append(parent.getString(R.string.number_of_routes)).append(": ")
				.append(routes.getRouteCount());
		if (routes.getMinGrade() != ClimbingRouteCounter.UNKNOWN_GRADE) {
			GradeSystem gradeSystem = GradeSystem.fromString(
					Configs.instance(parent).getString(Configs.ConfigKey.usedGradeSystem));
			String gradeSystemName = parent.getString(gradeSystem.shortName);
			result.append('\n').append(parent.getString(R.string.min_grade, gradeSystemName))
					.append(": ").append(gradeSystem.getGrade(routes.getMinGrade()))
					.append('\n').append(parent.getString(R.string.max_grade, gradeSystemName))
					.append(": ").append(gradeSystem.getGrade(routes.getMaxGrade()));
		}
		return result.toString();
	}

	/**
	 * A relation (with its center and routes) or a single POI (with neither).
	 */
	private record ClusterEntry(GeoNode poi, OsmCollectionEntity relation,
	                            MapCoordinate coordinate,
	                            ClimbingRouteCounter.RouteSummary routes, String description) {

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
				NodeDialogBuilder.showCollectionInfoDialog(parent, relation, coordinate,
						routes.getRouteCount() > 0 ? routes.getRouteCount() : -1);
			}
		}
	}
}

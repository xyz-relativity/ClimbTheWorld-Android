package com.climbtheworld.app.activities;

import android.Manifest;
import android.content.Intent;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ListPopupWindow;
import android.widget.SearchView;

import androidx.activity.OnBackPressedCallback;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.model.MapZoomLevels;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.SearchResultsAdapter;
import com.climbtheworld.app.utils.views.dialogs.FilterDialogue;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.maplibre.android.MapLibre;

import java.util.ArrayList;
import java.util.List;

import needle.UiRelatedTask;

public class MapActivity extends MapWidgetActivity implements ConfigFragment.OnConfigChangeListener {
	private OrientationManager orientationManager;
	private DeviceLocationManager deviceLocationManager;

	private static final int LOCATION_UPDATE = 500;
	private static final double MAP_CENTER_ON_ZOOM_LEVEL = 24;
	private static final int SEARCH_DEBOUNCE_MS = 300;
	private Configs configs;

	private final DataManager dataManager = new DataManager();
	private final Handler searchHandler = new Handler(Looper.getMainLooper());
	private Runnable pendingSearch;
	private UiRelatedTask<List<SearchResultsAdapter.Result>> searchTask;
	private SearchView searchView;
	private ListPopupWindow searchResultsPopup;
	private OnBackPressedCallback searchResultsBackCallback;
	private SearchResultsAdapter searchResultsAdapter;
	private final List<SearchResultsAdapter.Result> searchResults = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MapLibre.getInstance(this);
		setContentView(R.layout.activity_map);

		EdgeToEdge.padForSystemBars(findViewById(R.id.mapViewContainer));

		configs = Configs.instance(this);

		Ask.on(this)
				.id(500) // in case you are invoking multiple time Ask from same activity or fragment
				.addPermission(Manifest.permission.ACCESS_FINE_LOCATION, R.string.map_location_rational)
				.go();

		mapWidget = new MapLibreMapWidget(this, findViewById(R.id.mapViewContainer), savedInstanceState);

		Intent intent = getIntent();
		if (intent != null && intent.hasExtra("GeoPoint")) {
			String intentGeoPoint = intent.getStringExtra("GeoPoint");
			if (intentGeoPoint != null) {
				centerOnLocation(MapCoordinate.fromDelimitedString(intentGeoPoint));
			}
		}

		//location
		deviceLocationManager = new DeviceLocationManager(this, LOCATION_UPDATE);

		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_UI);
		mapWidget.setOnFollowCompassChangeListener(orientationManager::setFastUpdates);

		FloatingActionButton createNew = findViewById(R.id.createButton);
		createNew.setImageDrawable(MarkerUtils.getLayoutIcon(this, R.layout.icon_climbing_add_display));
		createNew.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MapActivity.this, EditNodeActivity.class);
				MapCoordinate tapLocation = mapWidget.getTapLocation();
				intent.putExtra("poiLat", tapLocation.getLatitude());
				intent.putExtra("poiLon", tapLocation.getLongitude());
				startActivityForResult(intent, Constants.OPEN_EDIT_ACTIVITY);
			}
		});

		updateFilterIcon();
		initSearch();
	}

	private void initSearch() {
		searchView = findViewById(R.id.searchView);
		searchView.setQueryHint(getString(R.string.search));

		searchResultsAdapter = new SearchResultsAdapter(this, searchResults,
				this::onSearchResultSelected, () -> {
					searchResultsPopup.dismiss();
					searchView.clearFocus();
				});

		searchResultsPopup = new ListPopupWindow(this);
		// Anchor to the search bar's container (the space between the FAB columns) so the list is
		// wide enough to read even though the SearchView itself is wrap_content.
		searchResultsPopup.setAnchorView((View) searchView.getParent());
		searchResultsPopup.setAdapter(searchResultsAdapter);
		searchResultsPopup.setModal(false);
		// Keep the keyboard up while the list is shown so the user can keep refining the query.
		searchResultsPopup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NEEDED);

		// Back closes the results list first. Only enabled while it shows, so predictive back
		// still animates when there is nothing to close.
		searchResultsBackCallback = new OnBackPressedCallback(false) {
			@Override
			public void handleOnBackPressed() {
				searchResultsPopup.dismiss();
			}
		};
		getOnBackPressedDispatcher().addCallback(this, searchResultsBackCallback);
		searchResultsPopup.setOnDismissListener(() -> searchResultsBackCallback.setEnabled(false));

		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				scheduleSearch(query, 0);
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				scheduleSearch(newText, SEARCH_DEBOUNCE_MS);
				return true;
			}
		});

		searchView.setOnCloseListener(() -> {
			scheduleSearch("", 0);
			return false;
		});
	}

	private void scheduleSearch(final String query, int delayMs) {
		searchHandler.removeCallbacks(pendingSearch);
		pendingSearch = () -> doSearch(query.trim());
		searchHandler.postDelayed(pendingSearch, delayMs);
	}

	private void doSearch(final String searchFor) {
		if (searchTask != null) {
			searchTask.cancel();
			searchTask = null;
		}
		if (searchFor.isEmpty()) {
			showSearchResults(new ArrayList<>());
			return;
		}

		searchTask = new UiRelatedTask<List<SearchResultsAdapter.Result>>() {
			@Override
			protected List<SearchResultsAdapter.Result> doWork() {
				return SearchResultsAdapter.Result.load(MapActivity.this,
						dataManager.find(MapActivity.this, searchFor));
			}

			@Override
			protected void thenDoUiRelatedWork(List<SearchResultsAdapter.Result> result) {
				searchTask = null;
				showSearchResults(result);
			}
		};
		Constants.DB_EXECUTOR.execute(searchTask);
	}

	private void showSearchResults(List<SearchResultsAdapter.Result> result) {
		if (isFinishing() || isDestroyed()) {
			return;
		}
		searchResults.clear();
		searchResults.addAll(result);
		searchResultsAdapter.notifyDataSetChanged();
		if (searchResults.isEmpty()) {
			searchResultsPopup.dismiss();
			return;
		}
		searchResultsPopup.setWidth(searchResultsPopup.getAnchorView().getWidth());
		searchResultsPopup.show();
		searchResultsBackCallback.setEnabled(true);
	}

	private void onSearchResultSelected(final GeoNode node) {
		searchResultsPopup.dismiss();
		searchView.clearFocus();
		if (!OsmEntity.EntityOsmType.relation.name().equals(node.jsonNodeInfo.optString(ClimbingTags.KEY_TYPE))) {
			centerOnLocation(new MapCoordinate(node.decimalLatitude, node.decimalLongitude, node.elevationMeters));
			return;
		}

		// Crags/areas mapped as relations: fit their outline, at a zoom where the map shows its label.
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<OsmCollectionEntity>() {
			@Override
			protected OsmCollectionEntity doWork() {
				return dataManager.loadCollection(MapActivity.this, node);
			}

			@Override
			protected void thenDoUiRelatedWork(OsmCollectionEntity collection) {
				if (isFinishing() || isDestroyed()) {
					return;
				}
				if (collection == null) {
					centerOnLocation(new MapCoordinate(node.decimalLatitude, node.decimalLongitude, node.elevationMeters));
					return;
				}
				boolean isArea = collection.entityClimbingType == OsmEntity.EntityClimbingType.area;
				double minZoom = isArea ? MapZoomLevels.AREA_MIN : MapZoomLevels.CRAG_MIN;
				// Labels hide at the next detail level, so stay just below it.
				double maxZoom = (isArea ? MapZoomLevels.CRAG_MIN : MapZoomLevels.POI_AND_ROUTE_MIN) - 0.5;
				mapWidget.centerOnBounds(new MapBounds(collection.bBoxNorth, collection.bBoxEast,
						collection.bBoxSouth, collection.bBoxWest), minZoom, maxZoom);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		deviceLocationManager.requestUpdates(this);
		orientationManager.requestUpdates(this);
	}

	@Override
	protected void onPause() {
		deviceLocationManager.stopUpdates();
		orientationManager.stopUpdates();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		searchHandler.removeCallbacks(pendingSearch);
		if (searchTask != null) {
			searchTask.cancel();
		}
		searchResultsPopup.dismiss();
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == Constants.OPEN_EDIT_ACTIVITY || requestCode == Constants.OPEN_TOOLS_ACTIVITY) {
			mapWidget.invalidateData();
		}
	}

	public void centerOnLocation(MapCoordinate location) {
		centerOnLocation(location, MAP_CENTER_ON_ZOOM_LEVEL);
	}

	public void centerOnLocation(MapCoordinate location, Double zoom) {
		mapWidget.centerOnLocation(location, zoom);
	}

	@Override
	public void onConfigChange() {
		updateFilterIcon();
		mapWidget.invalidateData();
	}

	private void updateFilterIcon() {
		NodeDisplayFilters.showFilterState(this, configs);
	}

	public void onClick(View v) {
		Intent intent;
		int id = v.getId();
		if (id == R.id.filterButton) {
			FilterDialogue.showFilterDialog(this, this);
		} else if (id == R.id.toolsButton) {
			intent = new Intent(MapActivity.this, ToolsActivity.class);
			startActivityForResult(intent, Constants.OPEN_TOOLS_ACTIVITY);
		}
	}
}


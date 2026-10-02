package com.climbtheworld.app.activities;

import android.Manifest;
import android.content.Intent;
import android.graphics.drawable.LayerDrawable;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListPopupWindow;
import android.widget.SearchView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.dialogs.DialogueUtils;
import com.climbtheworld.app.utils.views.dialogs.FilterDialogue;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.maplibre.android.MapLibre;

import java.util.ArrayList;
import java.util.List;

import needle.UiRelatedTask;

public class MapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, ConfigFragment.OnConfigChangeListener {
	private MapLibreMapWidget mapWidget;
	private OrientationManager orientationManager;
	private DeviceLocationManager deviceLocationManager;

	private static final int LOCATION_UPDATE = 500;
	private static final double MAP_CENTER_ON_ZOOM_LEVEL = 24;
	private static final int SEARCH_DEBOUNCE_MS = 300;
	private Configs configs;

	private final DataManagerNew dataManager = new DataManagerNew();
	private final Handler searchHandler = new Handler(Looper.getMainLooper());
	private Runnable pendingSearch;
	private UiRelatedTask<List<DisplayableGeoNode>> searchTask;
	private SearchView searchView;
	private ListPopupWindow searchResultsPopup;
	private BaseAdapter searchResultsAdapter;
	private final List<DisplayableGeoNode> searchResults = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MapLibre.getInstance(this);
		setContentView(R.layout.activity_map);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mapViewContainer), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

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

		searchResultsAdapter = new BaseAdapter() {
			@Override
			public int getCount() {
				return searchResults.size();
			}

			@Override
			public Object getItem(int i) {
				return searchResults.get(i);
			}

			@Override
			public long getItemId(int i) {
				return i;
			}

			@Override
			public View getView(int i, View view, ViewGroup viewGroup) {
				final DisplayableGeoNode marker = searchResults.get(i);
				view = ListViewItemBuilder.getPaddedBuilder(MapActivity.this, view, true)
						.setTitle(marker.getGeoNode().getName())
						.setDescription(DialogueUtils.buildDescription(MapActivity.this, marker.getGeoNode()))
						.setIcon(new PoiMarkerDrawable(MapActivity.this, marker))
						.build();
				view.setOnClickListener(v -> onSearchResultSelected(marker.getGeoNode()));
				return view;
			}
		};

		searchResultsPopup = new ListPopupWindow(this);
		// Anchor to the search bar's container (the space between the FAB columns) so the list is
		// wide enough to read even though the SearchView itself is wrap_content.
		searchResultsPopup.setAnchorView((View) searchView.getParent());
		searchResultsPopup.setAdapter(searchResultsAdapter);
		searchResultsPopup.setModal(false);
		// Keep the keyboard up while the list is shown so the user can keep refining the query.
		searchResultsPopup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NEEDED);

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

		searchTask = new UiRelatedTask<List<DisplayableGeoNode>>() {
			@Override
			protected List<DisplayableGeoNode> doWork() {
				return dataManager.find(MapActivity.this, searchFor);
			}

			@Override
			protected void thenDoUiRelatedWork(List<DisplayableGeoNode> result) {
				searchTask = null;
				showSearchResults(result);
			}
		};
		Constants.DB_EXECUTOR.execute(searchTask);
	}

	private void showSearchResults(List<DisplayableGeoNode> result) {
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
	}

	private void onSearchResultSelected(GeoNode node) {
		searchResultsPopup.dismiss();
		searchView.clearFocus();
		centerOnLocation(new MapCoordinate(node.decimalLatitude, node.decimalLongitude, node.elevationMeters));
	}

	@Override
	public void onBackPressed() {
		if (searchResultsPopup.isShowing()) {
			searchResultsPopup.dismiss();
			return;
		}
		super.onBackPressed();
	}

	@Override
	public void updateOrientation(OrientationManager.OrientationEvent event) {
		mapWidget.onOrientationChange(event.screen);
	}

	@Override
	public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
		Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

		mapWidget.onLocationChange(new MapCoordinate(pDecLatitude, pDecLongitude, pMetersAltitude));
	}

	@Override
	protected void onStart() {
		super.onStart();
		mapWidget.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();

		Globals.onResume(this);
		mapWidget.onResume();

		deviceLocationManager.requestUpdates(this);
		orientationManager.requestUpdates(this);
	}

	@Override
	protected void onPause() {
		deviceLocationManager.stopUpdates();
		orientationManager.stopUpdates();

		Globals.onPause(this);
		mapWidget.onPause();

		super.onPause();
	}

	@Override
	protected void onStop() {
		mapWidget.onStop();
		super.onStop();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mapWidget.onLowMemory();
	}

	@Override
	protected void onDestroy() {
		searchHandler.removeCallbacks(pendingSearch);
		if (searchTask != null) {
			searchTask.cancel();
		}
		searchResultsPopup.dismiss();
		mapWidget.onDestroy();
		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		mapWidget.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
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
		LayerDrawable icon = (LayerDrawable) ResourcesCompat.getDrawable(this.getResources(), R.drawable.ic_filter_checkable, null);
		if (icon == null) {
			return;
		}
		// The layers share ConstantState with every other use of the same drawables (e.g. ic_done);
		// mutate so the alpha change stays local to this FAB icon.
		icon.mutate();

		if (NodeDisplayFilters.hasFilters(configs)) {
			icon.findDrawableByLayerId(R.id.icon_notification).setAlpha(255);
		} else {
			icon.findDrawableByLayerId(R.id.icon_notification).setAlpha(0);
		}
		((FloatingActionButton) findViewById(R.id.filterButton)).setImageDrawable(icon);
	}

	public void onClick(View v) {
		Intent intent;
		switch (v.getId()) {
			case R.id.filterButton:
				FilterDialogue.showFilterDialog(this, this);
				break;

			case R.id.toolsButton:
				intent = new Intent(MapActivity.this, ToolsActivity.class);
				startActivityForResult(intent, Constants.OPEN_TOOLS_ACTIVITY);
				break;
		}
	}
}


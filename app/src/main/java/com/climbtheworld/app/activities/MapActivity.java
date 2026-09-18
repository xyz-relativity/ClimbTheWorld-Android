package com.climbtheworld.app.activities;

import android.Manifest;
import android.content.Intent;
import android.graphics.drawable.LayerDrawable;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.dialogs.FilterDialogue;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.maplibre.android.MapLibre;

public class MapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, ConfigFragment.OnConfigChangeListener {
	private MapLibreMapWidget mapWidget;
	private OrientationManager orientationManager;
	private DeviceLocationManager deviceLocationManager;

	private static final int LOCATION_UPDATE = 500;
	private static final double MAP_CENTER_ON_ZOOM_LEVEL = 24;
	private Configs configs;

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


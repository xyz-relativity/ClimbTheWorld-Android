package com.climbtheworld.app.activities;

import static com.climbtheworld.app.map.widget.MapViewWidget.MAP_CENTER_ON_ZOOM_LEVEL;

import android.Manifest;
import android.content.Intent;
import android.graphics.drawable.LayerDrawable;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.widget.MapViewWidget;
import com.climbtheworld.app.map.widget.MapWidgetBuilder;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.dialogs.FilterDialogue;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.util.GeoPoint;

public class MapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, ConfigFragment.OnConfigChangeListener {
	private MapViewWidget mapWidget;
	private OrientationManager orientationManager;
	private DeviceLocationManager deviceLocationManager;

	private static final int LOCATION_UPDATE = 500;
	private Configs configs;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		configs = Configs.instance(this);

		Ask.on(this)
				.id(500) // in case you are invoking multiple time Ask from same activity or fragment
				.forPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
				.withRationales(getString(R.string.map_location_rational)) //optional
				.go();

		mapWidget = MapWidgetBuilder.getBuilder(this, false)
				.enableTapMarker()
				.enableAutoDownload()
				.setFilterMethod(MapViewWidget.FilterType.USER)
				.build();

		Intent intent = getIntent();
		if (intent != null && intent.hasExtra("GeoPoint")) {
			GeoPoint location = GeoPoint.fromDoubleString(intent.getStringExtra("GeoPoint"), ',');
			centerOnLocation(location);
			mapWidget.setMapAutoFollow(false);
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
				intent.putExtra("poiLat", mapWidget.getTapMarker().getPosition().getLatitude());
				intent.putExtra("poiLon", mapWidget.getTapMarker().getPosition().getLongitude());
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

		mapWidget.onLocationChange(Globals.geoNodeToGeoPoint(Globals.virtualCamera));
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
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == Constants.OPEN_EDIT_ACTIVITY || requestCode == Constants.OPEN_TOOLS_ACTIVITY) {
			mapWidget.setClearState(true);
			mapWidget.invalidateData();
		}
	}

	public void centerOnLocation(GeoPoint location) {
		centerOnLocation(location, MAP_CENTER_ON_ZOOM_LEVEL);
	}

	public void centerOnLocation(GeoPoint location, Double zoom) {
		mapWidget.getTapMarker().setPosition(location);
		mapWidget.setMapAutoFollow(false);
		mapWidget.centerOnGoePoint(location, zoom);
	}

	@Override
	public void onConfigChange() {
		updateFilterIcon();
		mapWidget.setClearState(true);
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


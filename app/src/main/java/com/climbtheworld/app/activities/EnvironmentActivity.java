package com.climbtheworld.app.activities;

import android.Manifest;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.map.widget.MapViewWidget;
import com.climbtheworld.app.map.widget.MapWidgetBuilder;
import com.climbtheworld.app.navigate.widgets.CompassWidget;
import com.climbtheworld.app.sensors.environment.EnvironmentalSensors;
import com.climbtheworld.app.sensors.environment.IEnvironmentListener;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Quaternion;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.DecimalFormat;
import java.util.Locale;

public class EnvironmentActivity extends AppCompatActivity implements ILocationListener, IOrientationListener, IEnvironmentListener {

	private DeviceLocationManager deviceLocationManager;
	private OrientationManager orientationManager;
	private MapViewWidget mapWidget;

	private static final int LOCATION_UPDATE_DELAY_MS = 500;
	private static final String COORD_VALUE = "%.6f";
	DecimalFormat decimalFormat = new DecimalFormat("000.0°");
	private TextView editLatitude;
	private TextView editLongitude;
	private TextView editElevation;
	private TextView editAzimuthName;
	private TextView editAzimuthValue;
	private BottomNavigationView navigation;
	private ViewGroup viewSwitcher;
	private EnvironmentalSensors sensorManager;
	private TextView editTemperature;
	private TextView editPressure;
	private TextView editLight;
	private TextView editRelativeHumidity;
	private TextView editDewPoint;
	private TextView editAbsoluteHumidity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_environment);

		Ask.on(this)
				.id(500) // in case you are invoking multiple time Ask from same activity or fragment
				.forPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
				.withRationales(getString(R.string.map_location_rational)) //optional
				.go();

		viewSwitcher = findViewById(R.id.viewEnvSwitcher);
		viewSwitcher.findViewById(R.id.sensorViewContainer).setVisibility(View.GONE);

		navigation = findViewById(R.id.buttonsNavigationBar);
		navigation.setItemIconTintList(null);

		navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
			@Override
			public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
				switch (menuItem.getItemId()) {
					case R.id.map_navigation:
						viewSwitcher.findViewById(R.id.mapViewContainer).setVisibility(View.VISIBLE);
						viewSwitcher.findViewById(R.id.sensorViewContainer).setVisibility(View.GONE);
						return true;
					case R.id.sensor_navigation:
						viewSwitcher.findViewById(R.id.mapViewContainer).setVisibility(View.GONE);
						viewSwitcher.findViewById(R.id.sensorViewContainer).setVisibility(View.VISIBLE);
						return true;
				}
				return false;
			}
		});

		mapWidget = MapWidgetBuilder.getBuilder(this, true).build();

		final CompassWidget compass = new CompassWidget(findViewById(R.id.compassFace));

		//location
		deviceLocationManager = new DeviceLocationManager(this, LOCATION_UPDATE_DELAY_MS, this);

		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_UI);
		orientationManager.addListener(this, compass);

		sensorManager = new EnvironmentalSensors(this);
		sensorManager.addListener(this);

		editLatitude = findViewById(R.id.editLatitude);
		editLongitude = findViewById(R.id.editLongitude);
		editElevation = findViewById(R.id.editElevation);
		editAzimuthName = findViewById(R.id.editAzimuthName);
		editAzimuthValue = findViewById(R.id.editAzimuthValue);

		editTemperature = findViewById(R.id.editTemperature);
		editPressure = findViewById(R.id.editPressure);
		editLight = findViewById(R.id.editLight);
		editRelativeHumidity = findViewById(R.id.editRelativeHumidity);
		editDewPoint = findViewById(R.id.editDewPoint);
		editAbsoluteHumidity = findViewById(R.id.editAbsoluteHunidity);
	}

	@Override
	public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
		Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

		mapWidget.onLocationChange(Globals.poiToGeoPoint(Globals.virtualCamera));
	}

	@Override
	public void updateOrientation(OrientationManager.OrientationEvent event) {
		Globals.virtualCamera.updateOrientation(event);

		mapWidget.onOrientationChange(event);

		int azimuthID = (int) Math.round(((event.global.x % 360) / 22.5)) % 16;
		editAzimuthName.setText(getResources().getStringArray(R.array.cardinal_names)[azimuthID]);

		editLatitude.setText(String.format(Locale.getDefault(), COORD_VALUE, Globals.virtualCamera.decimalLatitude));
		editLongitude.setText(String.format(Locale.getDefault(), COORD_VALUE, Globals.virtualCamera.decimalLongitude));
		editElevation.setText(String.format(Locale.getDefault(), COORD_VALUE, Globals.virtualCamera.elevationMeters));

		editAzimuthValue.setText(decimalFormat.format(event.global.x));
	}

	@Override
	protected void onResume() {
		super.onResume();

		Globals.onResume(this);

		deviceLocationManager.onResume();
		orientationManager.onResume();
		sensorManager.onResume();
	}

	@Override
	protected void onPause() {
		deviceLocationManager.onPause();
		orientationManager.onPause();
		sensorManager.onPause();

		Globals.onPause(this);

		super.onPause();
	}

	@Override
	public void updateSensors(Quaternion sensors) {
		editTemperature.setText(String.format(Locale.getDefault(), "%.2f (°C)",sensors.w));
		editPressure.setText(String.format(Locale.getDefault(), "%.2f (hPa)",sensors.x));
		editLight.setText(String.format(Locale.getDefault(), "%.2f (lux)",sensors.y));
		editRelativeHumidity.setText(String.format(Locale.getDefault(), "%.2f (%%)",sensors.z));

		double Tn = 243.12;
		double m = 17.62;
		double lnHumidity = Math.log1p(sensors.z/100);
		double tempFactor = (m*sensors.w)/(Tn + sensors.w);
		double dewPoint = Tn*((lnHumidity + tempFactor)/(m-(lnHumidity + tempFactor)));
		editDewPoint.setText(String.format(Locale.getDefault(), "%.2f (°C)",dewPoint));

		double absoluteHumidity = 216.7*(((sensors.z/100)*6.112*Math.exp(tempFactor))/(273.15 + sensors.w));
		editAbsoluteHumidity.setText(String.format(Locale.getDefault(), "%.2f (g/㎥)",absoluteHumidity));
	}
}

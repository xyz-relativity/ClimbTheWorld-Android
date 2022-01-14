package com.climbtheworld.app.activities;

import android.Manifest;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.widget.MapViewWidget;
import com.climbtheworld.app.map.widget.MapWidgetBuilder;
import com.climbtheworld.app.navigate.widgets.CompassWidget;
import com.climbtheworld.app.sensors.environment.EnvironmentalSensors;
import com.climbtheworld.app.sensors.environment.IEnvironmentListener;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Quaternion;
import com.climbtheworld.app.utils.views.RotationGestureDetector;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.shredzone.commons.suncalc.SunTimes;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Locale;

public class EnvironmentActivity extends AppCompatActivity implements IEnvironmentListener {

	private DeviceLocationManager deviceLocationManager;
	private OrientationManager orientationManager;
	private MapViewWidget mapWidget;

	private static final int LOCATION_UPDATE_DELAY_MS = 500;
	private static final String COORD_VALUE = "%.6f°";
	DecimalFormat decimalFormat = new DecimalFormat("000.00°");
	DateFormat format;

	private TextView editLatitude;
	private TextView editLatitudeDMS;
	private TextView editLongitude;
	private TextView editLongitudeDMS;
	private TextView editElevation;
	private TextView editSunrise;
	private TextView editSunset;
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
	private CompassWidget compass;
	private String[] orientationLat = new String[2];
	private String[] orientationLong = new String[2];
	private View compassBazel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_environment);

		Ask.on(this)
				.id(500) // in case you are invoking multiple time Ask from same activity or fragment
				.forPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
				.withRationales(getString(R.string.map_location_rational)) //optional
				.go();

		orientationLat[0] = getResources().getStringArray(R.array.cardinal_names)[0];
		orientationLat[1] = getResources().getStringArray(R.array.cardinal_names)[8];

		orientationLong[0] = getResources().getStringArray(R.array.cardinal_names)[4];
		orientationLong[1] = getResources().getStringArray(R.array.cardinal_names)[12];

		viewSwitcher = findViewById(R.id.viewEnvSwitcher);
		viewSwitcher.findViewById(R.id.mapViewContainer).setVisibility(View.GONE);

		compassBazel = findViewById(R.id.compassBazel);

		editLatitude = findViewById(R.id.editLatitude);
		editLatitudeDMS = findViewById(R.id.editLatitudeDMS);
		editLongitude = findViewById(R.id.editLongitude);
		editLongitudeDMS = findViewById(R.id.editLongitudeDMS);
		editElevation = findViewById(R.id.editElevation);

		editSunrise = findViewById(R.id.editSunrise);
		editSunset = findViewById(R.id.editSunset);

		editAzimuthName = findViewById(R.id.editAzimuthName);
		editAzimuthValue = findViewById(R.id.editAzimuthValue);

		editTemperature = findViewById(R.id.editTemperature);
		editPressure = findViewById(R.id.editPressure);
		editLight = findViewById(R.id.editLight);
		editRelativeHumidity = findViewById(R.id.editRelativeHumidity);
		editDewPoint = findViewById(R.id.editDewPoint);
		editAbsoluteHumidity = findViewById(R.id.editAbsoluteHunidity);

		format = android.text.format.DateFormat.getTimeFormat(this);

		navigation = findViewById(R.id.buttonsNavigationBar);
		navigation.setItemIconTintList(null);

		navigation.setOnItemSelectedListener(this::onNavigationItemSelected);

		mapWidget = MapWidgetBuilder.getBuilder(this, true).build();

		compass = new CompassWidget(findViewById(R.id.compassRoseHand));

		//location
		deviceLocationManager = new DeviceLocationManager(this, LOCATION_UPDATE_DELAY_MS, this::updatePosition);

		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_UI);
		orientationManager.addListener(this::updateOrientation);

		sensorManager = new EnvironmentalSensors(this);
		sensorManager.addListener(this);

		setupBazel();
	}

	private void setupBazel() {
		Configs configs = Configs.instance(this);

		RotationGestureDetector rotationGestureDetector = new RotationGestureDetector(new RotationGestureDetector.RotationListener() {
			@Override
			public void onRotate(float deltaAngle) {
				rotateBazel(configs, (360 + (compassBazel.getRotation() + deltaAngle)) % 360, false);
			}
		});

		compassBazel.setRotation(configs.getFloat(Configs.ConfigKey.compassBazelAngle));
		findViewById(R.id.compassBazelContainer).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				rotationGestureDetector.onTouch(motionEvent);
				return true;
			};
		});
		findViewById(R.id.azimuthContainer).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				float angle = Float.parseFloat((String) editAzimuthValue.getText().subSequence(0, 6));
				rotateBazel(configs, 360 - angle, true);
			}
		});

		findViewById(R.id.userPointing).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				rotateBazel(configs, 0, true);
			}
		});
	}

	private void rotateBazel(Configs configs, float angle, boolean withAnimation) {
		configs.setFloat(Configs.ConfigKey.compassBazelAngle, angle);

		System.out.println("start: " + compassBazel.getRotation() + " end: " + angle);
		if (withAnimation) {
			float a = (float) AugmentedRealityUtils.diffAngle(angle, compassBazel.getRotation());

			RotateAnimation rotate = new RotateAnimation(0, a, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
			rotate.setDuration(200);
			rotate.setFillEnabled(true);
			rotate.setInterpolator(new LinearInterpolator());
			rotate.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
					if (animation.hasEnded()) {
						compassBazel.setRotation(angle);
					}
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					if (animation.hasEnded()) {
						compassBazel.setRotation(angle);
					}
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
					if (animation.hasEnded()) {
						compassBazel.setRotation(angle);
					}
				}
			});
			compassBazel.startAnimation(rotate);
		} else {
			compassBazel.setRotation(angle);
		}

//		compassBazel.setRotation(angle);
	}

	public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
		Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

		editLatitude.setText(String.format(Locale.getDefault(), COORD_VALUE,
				Globals.virtualCamera.decimalLatitude));
		editLatitudeDMS.setText(CoordinatesConverter.processCoordinates(Globals.virtualCamera.decimalLatitude, orientationLat));
		editLongitude.setText(String.format(Locale.getDefault(), COORD_VALUE,
				Globals.virtualCamera.decimalLongitude));
		editLongitudeDMS.setText(CoordinatesConverter.processCoordinates(Globals.virtualCamera.decimalLongitude, orientationLong));
		editElevation.setText(String.format(Locale.getDefault(), "%.2f (m)", Globals.virtualCamera.elevationMeters));

		SunTimes times = SunTimes.compute()
				.on(new Date())   // set a date
				.at(pDecLatitude, pDecLongitude)   // set a location
				.execute();     // get the results

		editSunrise.setText(format.format(times.getRise()));
		editSunset.setText(format.format(times.getSet()));

		mapWidget.onLocationChange(Globals.geoNodeToGeoPoint(Globals.virtualCamera));
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

	public void updateOrientation(OrientationManager.OrientationEvent event) {
		Globals.virtualCamera.updateOrientation(event);

		mapWidget.onOrientationChange(event.screen);

		editAzimuthName.setText(AugmentedRealityUtils.getStringBearings(EnvironmentActivity.this, event.screen.x));

		editAzimuthValue.setText(decimalFormat.format(event.screen.x));
		compass.updateOrientation(event.screen);
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

	private static class CoordinatesConverter {
		public static String processCoordinates(double coordinate, String[] orientations) {
			final String dmsLat = coordinate > 0 ? orientations[0] : orientations[1];
			return decimalToDMS(coordinate) + " " + dmsLat;
		}

		private static String decimalToDMS(double dd) {
			int d = (int)dd;
			int m = (int)((dd - d) * 60);
			double s = (dd - d - m / 60.0) * 3600;

			return Math.abs(d) + "°" + Math.abs(m) + "'" + String.format(Locale.getDefault(), "%.2f", Math.abs(s)) + "\"";
		}
	}
}

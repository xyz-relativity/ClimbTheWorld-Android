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

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.CompassAccuracyArcDrawable;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.navigate.widgets.CompassStatusBadge;
import com.climbtheworld.app.navigate.widgets.CompassWidget;
import com.climbtheworld.app.sensors.environment.EnvironmentalSensors;
import com.climbtheworld.app.sensors.environment.IEnvironmentListener;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.RotationGestureDetector;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.maplibre.android.MapLibre;
import org.shredzone.commons.suncalc.SunTimes;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EnvironmentActivity extends MapWidgetActivity implements IEnvironmentListener, IOrientationListener {

	private DeviceLocationManager deviceLocationManager;
	private OrientationManager orientationManager;

	private static final int LOCATION_UPDATE_DELAY_MS = 500;
	private static final String COORD_VALUE = "%.6f°";
	DecimalFormat decimalFormat = new DecimalFormat("000.00°");

	private TextView editLatitude;
	private TextView editLatitudeDMS;
	private TextView editLongitude;
	private TextView editLongitudeDMS;
	private TextView editElevation;
	private TextView editSunrise;
	private TextView editSunset;
	private TextView editAzimuthName;
	private TextView editAzimuthValue;
	// The azimuth the texts show, in hundredths of a degree, as the value is written, so that
	// they are only set again once they would read differently: the orientation comes many
	// times a second, and each text set is formatted and laid out again. Unset at first.
	private long shownAzimuthHundredths = Long.MIN_VALUE;
	private BottomNavigationView navigation;
	private ViewGroup viewSwitcher;
	private EnvironmentalSensors sensorManager;
	private TextView editTemperature;
	private TextView editPressure;
	private TextView editLight;
	private TextView editRelativeHumidity;
	private TextView editDewPoint;
	private TextView editAbsoluteHumidity;
	// The reading each of the sensors' texts shows. The sensors report one at a time, several
	// times a second, and each report brings all the readings: only the texts whose reading
	// changed are formatted again.
	private final Map<TextView, Double> shownReadings = new HashMap<>();
	private CompassWidget compass;
	private CompassStatusBadge compassStatusBadge;
	private final CompassAccuracyArcDrawable compassAccuracyArc = new CompassAccuracyArcDrawable();
	private final String[] orientationLat = new String[2];
	private final String[] orientationLong = new String[2];
	private View compassBazel;
	private final View[] compassBazelCardinals = new View[4];
	// The angle the fingers have turned the bezel to and that is not saved yet, or null. It is
	// saved once they lift: a move comes with every frame, and each one saved had the settings
	// written again.
	private Float unsavedBazelAngle;
	private DateTimeFormatter dateTimeFormater;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MapLibre.getInstance(this);
		setContentView(R.layout.activity_environment);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

		Ask.on(this)
				.id(502) // in case you are invoking multiple time Ask from same activity or fragment
				.addPermission(Manifest.permission.ACCESS_FINE_LOCATION, R.string.map_location_rational)
				.go();

		orientationLat[0] = getResources().getStringArray(R.array.cardinal_names)[0];
		orientationLat[1] = getResources().getStringArray(R.array.cardinal_names)[8];

		orientationLong[0] = getResources().getStringArray(R.array.cardinal_names)[4];
		orientationLong[1] = getResources().getStringArray(R.array.cardinal_names)[12];

		viewSwitcher = findViewById(R.id.viewEnvSwitcher);
		viewSwitcher.findViewById(R.id.mapViewContainer).setVisibility(View.GONE);

		compassBazel = findViewById(R.id.compassBazel);
		compassBazelCardinals[0] = findViewById(R.id.compassNorthLabel);
		compassBazelCardinals[1] = findViewById(R.id.compassEastLabel);
		compassBazelCardinals[2] = findViewById(R.id.compassSouthLabel);
		compassBazelCardinals[3] = findViewById(R.id.compassWestLabel);

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

		DateFormat format = android.text.format.DateFormat.getTimeFormat(this);
		dateTimeFormater = DateTimeFormatter.ofPattern(((SimpleDateFormat) format).toLocalizedPattern(), Locale.getDefault());

		navigation = findViewById(R.id.buttonsNavigationBar);
		navigation.setItemIconTintList(null);

		navigation.setOnItemSelectedListener(this::onNavigationItemSelected);

		mapWidget = new MapLibreMapWidget(
				this, findViewById(R.id.mapViewContainer), savedInstanceState);

		compass = new CompassWidget(findViewById(R.id.compassRoseHand), true);
		compassStatusBadge = new CompassStatusBadge(findViewById(R.id.compassDialStatusBadge));
		findViewById(R.id.compassAccuracyArc).setForeground(compassAccuracyArc);

		//location
		deviceLocationManager = new DeviceLocationManager(this, LOCATION_UPDATE_DELAY_MS);

		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_UI);
		mapWidget.setOnFollowCompassChangeListener(orientationManager::setFastUpdates);

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

		rotateBazel(configs.getFloat(Configs.ConfigKey.compassBazelAngle));
		findViewById(R.id.compassBazelContainer).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				rotationGestureDetector.onTouch(motionEvent);
				int action = motionEvent.getActionMasked();
				if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
					saveBazelAngle();
				}
				return true;
			}
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
		if (withAnimation) {
			// A tap, saved at once: it comes after what a turn under way has left to save.
			unsavedBazelAngle = null;
			configs.setFloat(Configs.ConfigKey.compassBazelAngle, angle);

			float a = (float) GeoUtils.diffAngle(angle, compassBazel.getRotation());

			RotateAnimation rotate = new RotateAnimation(0, a, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
			rotate.setDuration((long)Math.abs(a));
			rotate.setFillEnabled(true);
			rotate.setInterpolator(new LinearInterpolator());
			rotate.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
					if (animation.hasEnded()) {
						rotateBazel(angle);
					}
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					if (animation.hasEnded()) {
						rotateBazel(angle);
					}
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
					if (animation.hasEnded()) {
						rotateBazel(angle);
					}
				}
			});
			compassBazel.startAnimation(rotate);
		} else {
			// A move of the fingers turning the bezel, saved once they lift.
			unsavedBazelAngle = angle;
			rotateBazel(angle);
		}
	}

	/** Saves the angle the fingers turned the bezel to, when it is not saved yet. */
	private void saveBazelAngle() {
		if (unsavedBazelAngle != null) {
			Configs.instance(this).setFloat(Configs.ConfigKey.compassBazelAngle,
					unsavedBazelAngle);
			unsavedBazelAngle = null;
		}
	}

	private void rotateBazel(float angle) {
		compassBazel.setRotation(angle);
		for (View view : compassBazelCardinals) {
			view.setRotation(-angle);
		}
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

		if (times.getRise() != null) {
			editSunrise.setText(times.getRise().format(dateTimeFormater));
		}

		if (times.getSet() != null) {
			editSunset.setText(times.getSet().format(dateTimeFormater));
		}

		mapWidget.onLocationChange(new MapCoordinate(pDecLatitude, pDecLongitude, pMetersAltitude));
	}

	@Override
	protected void onResume() {
		super.onResume();
		deviceLocationManager.requestUpdates(this::updatePosition);
		orientationManager.requestUpdates(this);
		sensorManager.onResume();
	}

	@Override
	protected void onPause() {
		// The screen may be left with the fingers still on the bezel, and may not come back.
		saveBazelAngle();
		deviceLocationManager.stopUpdates();
		orientationManager.stopUpdates();
		sensorManager.onPause();
		super.onPause();
	}

	public void updateOrientation(OrientationManager.OrientationEvent event) {
		Vector4d heading = event.getAdjusted();
		compassStatusBadge.setStatus(event.compassStatus);
		compassAccuracyArc.setHalfWidthDegrees(
				MapLibreMapWidget.headingAccuracyWedgeDegrees(event.headingAccuracyDegrees));
		mapWidget.onOrientationChange(heading, event);

		long azimuthHundredths = Math.round(heading.x * 100);
		if (azimuthHundredths != shownAzimuthHundredths) {
			shownAzimuthHundredths = azimuthHundredths;
			String name = AugmentedRealityUtils.getStringBearings(EnvironmentActivity.this,
					heading.x);
			// The name only changes every 22.5 degrees.
			if (!name.contentEquals(editAzimuthName.getText())) {
				editAzimuthName.setText(name);
			}
			editAzimuthValue.setText(decimalFormat.format(heading.x));
		}
		compass.updateOrientation(heading);
	}

	@Override
	public void updateSensors(Vector4d sensors) {
		showReading(editTemperature, sensors.w, "%.2f (°C)");
		showReading(editPressure, sensors.x, "%.2f (hPa)");
		showReading(editLight, sensors.y, "%.2f (lux)");
		showReading(editRelativeHumidity, sensors.z, "%.2f (%%)");
		showReading(editDewPoint, EnvironmentalSensors.dewPoint(sensors.w, sensors.z),
				"%.2f (°C)");
		showReading(editAbsoluteHumidity,
				EnvironmentalSensors.absoluteHumidity(sensors.w, sensors.z), "%.2f (g/㎥)");
	}

	/**
	 * Shows a reading, or that there is none when the device lacks the sensors it comes from.
	 */
	private void showReading(TextView view, double value, String format) {
		Double shown = shownReadings.put(view, value);
		// Compared as Doubles, for which a missing reading, NaN, equals itself.
		if (shown != null && shown.equals(value)) {
			return;
		}
		String text = Double.isNaN(value) ? getString(R.string.n_a)
				: String.format(Locale.getDefault(), format, value);
		// A reading often changes by less than its text shows.
		if (!text.contentEquals(view.getText())) {
			view.setText(text);
		}
	}

	public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
		int id = menuItem.getItemId();
		if (id == R.id.map_navigation) {
			View mapContainer = viewSwitcher.findViewById(R.id.mapViewContainer);
			mapContainer.setVisibility(View.VISIBLE);
			viewSwitcher.findViewById(R.id.sensorViewContainer).setVisibility(View.GONE);
			mapContainer.post(mapWidget::invalidateData);
			return true;
		} else if (id == R.id.sensor_navigation) {
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

package com.climbtheworld.app.sensors.orientation;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.view.Surface;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;

import java.lang.ref.WeakReference;

/**
 * Created by xyz on 11/24/17.
 */

public class OrientationManager implements SensorEventListener {
	// Over tens of kilometres, the earth's field turns by a fraction of a degree and changes its
	// strength by a fraction of a percent, so it is only recomputed once the observer moved about
	// this far.
	private static final double FIELD_UPDATE_DEGREES = 0.1;
	private static final long INTERFERENCE_WARNING_INTERVAL_MS = 60_000;

	// Shared by every screen, so that going from one to the next does not repeat the warning.
	private static long lastInterferenceWarningMs;

	private final WeakReference<AppCompatActivity> parent;
	private IOrientationListener orientationListener;
	private final SensorManager sensorManager;
	private final DisplayManager displayManager;
	private int samplingPeriodUs = SensorManager.SENSOR_DELAY_NORMAL;
	private int displayRotation = Surface.ROTATION_0;

	// Turning the screen half way around, from one landscape to the other, does not recreate the
	// activity, so the display rotation is followed for as long as the sensors are.
	private final DisplayManager.DisplayListener displayListener =
			new DisplayManager.DisplayListener() {
				@Override
				public void onDisplayAdded(int displayId) {
				}

				@Override
				public void onDisplayRemoved(int displayId) {
				}

				@Override
				public void onDisplayChanged(int displayId) {
					updateDisplayRotation();
				}
			};

	// Holds sensor data
	private static final float[] originRotationMatrix = new float[16];
	private static final float[] remappedRotationMatrix = new float[16];
	private final float[] screenRotationMatrix = new float[16];
	private static float[] orientationVector = new float[3];
	private final OrientationEvent orientation = new OrientationEvent();
	private double fieldLatitude = Double.NaN;
	private double fieldLongitude = Double.NaN;
	private GeomagneticField cachedField;
	private int lastMagnetometerAccuracy;
	private final MagneticInterferenceDetector interferenceDetector =
			new MagneticInterferenceDetector();

	public static class OrientationEvent {
		public Vector4d screen = new Vector4d();
		public Vector4d camera = new Vector4d();

		public Vector4d getAdjusted() {
			if (screen.y > 45 || screen.y < -45) {
				return camera;
			} else {
				return screen;
			}
		}
	}

	public OrientationManager(AppCompatActivity parent, int samplingPeriodUs) {
		this.parent = new WeakReference<>(parent);
		this.samplingPeriodUs = samplingPeriodUs;

		sensorManager = (SensorManager) parent.getSystemService(Context.SENSOR_SERVICE);
		displayManager = (DisplayManager) parent.getSystemService(Context.DISPLAY_SERVICE);
	}

	/**
	 * Whether the device can tell which way it is facing. Without a compass no orientation is
	 * ever reported.
	 */
	public static boolean hasCompass(Context context) {
		return getRotationSensor(
				(SensorManager) context.getSystemService(Context.SENSOR_SERVICE)) != null;
	}

	/**
	 * The rotation vector, which also uses the gyroscope to react faster and steadier, or on
	 * devices without one, the geomagnetic rotation vector, which only needs the accelerometer
	 * and the magnetometer. Null when there is no magnetometer, since then neither exists.
	 */
	private static Sensor getRotationSensor(SensorManager sensorManager) {
		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		if (sensor == null) {
			sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
		}
		return sensor;
	}

	public void requestUpdates(IOrientationListener orientationListener) {
		this.orientationListener = orientationListener;
		Sensor rotationSensor = getRotationSensor(sensorManager);
		if (rotationSensor == null) {
			return;
		}

		updateDisplayRotation();
		displayManager.registerDisplayListener(displayListener, null);
		sensorManager.registerListener(this, rotationSensor, samplingPeriodUs);

		// The rotation vector's own accuracy does not reliably reflect the magnetometer
		// calibration, and says nothing of a disturbed field, so the magnetometer is listened to
		// as well, for its accuracy and the strength of the field it measures.
		Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		if (magnetometer != null) {
			lastMagnetometerAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
			interferenceDetector.reset();
			sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	public void stopUpdates() {
		sensorManager.unregisterListener(this);
		displayManager.unregisterDisplayListener(displayListener);
		this.orientationListener = null;
	}

	private void updateDisplayRotation() {
		AppCompatActivity activity = parent.get();
		if (activity != null) {
			displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
			// Read from the events rather than onAccuracyChanged: that one only reports changes,
			// starting from unreliable, so a magnetometer unreliable from the start is never
			// reported.
			checkMagnetometerAccuracy(event.accuracy);
			checkMagneticInterference(event);
		} else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR
				|| event.sensor.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
			// The rotation vector points to magnetic north, while maps and bearings use true north.
			// The declination is the angle between them, positive when magnetic north is east of
			// true north.
			double declination = getGeomagneticField().getDeclination();
			SensorManager.getRotationMatrixFromVector(originRotationMatrix, event.values);

			// The screen azimuth follows the top of the screen, which is another side of the
			// device once the screen is turned.
			remapToDisplay(originRotationMatrix, displayRotation, screenRotationMatrix);
			orientationVector = SensorManager.getOrientation(screenRotationMatrix, orientationVector);
			orientation.screen.x = toTrueAzimuth(Math.toDegrees(orientationVector[0]), declination);  // yah, azimuth
			orientation.screen.y = (Math.toDegrees(orientationVector[1]) % 180);// pitch
			orientation.screen.z = (Math.toDegrees(orientationVector[2]) % 180);// roll

			//align coordinates with the camera (for AR)
			SensorManager.remapCoordinateSystem(originRotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedRotationMatrix);
			orientationVector = SensorManager.getOrientation(remappedRotationMatrix, orientationVector);
			orientation.camera.x = toTrueAzimuth(Math.toDegrees(orientationVector[0]), declination);  // yah, azimuth
			orientation.camera.y = (Math.toDegrees(orientationVector[1]) % 180);
			orientation.camera.z = (Math.toDegrees(orientationVector[2]) % 180);

			Globals.virtualCamera.updateOrientation(orientation);
			if (orientationListener != null) {
				orientationListener.updateOrientation(orientation);
			}
		}
	}

	/**
	 * Turns a rotation matrix of the device into one of the screen as it is displayed: its X axis
	 * points to the right of the screen and its Y axis to the top, whichever way the screen is
	 * turned.
	 *
	 * @param displayRotation one of the Surface.ROTATION_ values, see Display.getRotation()
	 */
	static void remapToDisplay(float[] deviceRotationMatrix, int displayRotation,
	                           float[] screenRotationMatrix) {
		int axisX;
		int axisY;
		switch (displayRotation) {
			case Surface.ROTATION_90:
				axisX = SensorManager.AXIS_Y;
				axisY = SensorManager.AXIS_MINUS_X;
				break;
			case Surface.ROTATION_180:
				axisX = SensorManager.AXIS_MINUS_X;
				axisY = SensorManager.AXIS_MINUS_Y;
				break;
			case Surface.ROTATION_270:
				axisX = SensorManager.AXIS_MINUS_Y;
				axisY = SensorManager.AXIS_X;
				break;
			default:
				axisX = SensorManager.AXIS_X;
				axisY = SensorManager.AXIS_Y;
				break;
		}
		SensorManager.remapCoordinateSystem(deviceRotationMatrix, axisX, axisY,
				screenRotationMatrix);
	}

	/**
	 * The earth's magnetic field at the observer, as a model predicts it, without whatever
	 * disturbs it locally.
	 */
	private GeomagneticField getGeomagneticField() {
		double latitude = Globals.virtualCamera.decimalLatitude;
		double longitude = Globals.virtualCamera.decimalLongitude;
		if (Double.isNaN(fieldLatitude)
				|| Math.abs(latitude - fieldLatitude) > FIELD_UPDATE_DEGREES
				|| Math.abs(longitude - fieldLongitude) > FIELD_UPDATE_DEGREES) {
			cachedField = new GeomagneticField((float) latitude, (float) longitude,
					(float) Globals.virtualCamera.elevationMeters, System.currentTimeMillis());
			fieldLatitude = latitude;
			fieldLongitude = longitude;
		}
		return cachedField;
	}

	/**
	 * Turns an azimuth measured from magnetic north into one measured from true north, between 0
	 * and 360 degrees.
	 */
	static double toTrueAzimuth(double magneticAzimuth, double declination) {
		return (((magneticAzimuth + declination) % 360) + 360) % 360;
	}

	/**
	 * Asks for a calibration whenever the magnetometer accuracy drops. Improvements stay quiet, so
	 * the warnings do not pile up while the user is calibrating.
	 */
	private void checkMagnetometerAccuracy(int accuracy) {
		if (accuracy < SensorManager.SENSOR_STATUS_UNRELIABLE) {
			return;
		}
		boolean dropped = accuracy < lastMagnetometerAccuracy;
		lastMagnetometerAccuracy = accuracy;

		AppCompatActivity activity = parent.get();
		if (!dropped || activity == null) {
			return;
		}

		String level;
		switch (accuracy) {
			case SensorManager.SENSOR_STATUS_UNRELIABLE:
				level = "0%";
				break;
			case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
				level = "10%";
				break;
			default:
				level = "50%";
				break;
		}
		DialogBuilder.toastOnMainThread(activity,
				activity.getString(R.string.sensor_magnetometer_calibration, level));
	}

	/**
	 * Warns when something near the device disturbs the magnetic field, since the compass is then
	 * wrong whatever its calibration. Walking past metal can start a disturbance every few steps,
	 * so the warnings are spaced out.
	 */
	private void checkMagneticInterference(SensorEvent event) {
		// Until it is calibrated, the magnetometer adds offsets of its own to the earth's field,
		// which the calibration warning covers.
		if (event.accuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE) {
			return;
		}
		boolean wasDisturbed = interferenceDetector.isDisturbed();
		// The magnetometer measures in microtesla, the model in nanotesla.
		double expectedMicroTesla = getGeomagneticField().getFieldStrength() / 1000.0;
		boolean disturbed = interferenceDetector.update(
				MagneticInterferenceDetector.strength(event.values), expectedMicroTesla,
				event.timestamp);
		if (!disturbed || wasDisturbed) {
			return;
		}

		long now = SystemClock.elapsedRealtime();
		AppCompatActivity activity = parent.get();
		if (activity == null || (lastInterferenceWarningMs != 0
				&& now - lastInterferenceWarningMs < INTERFERENCE_WARNING_INTERVAL_MS)) {
			return;
		}
		lastInterferenceWarningMs = now;
		DialogBuilder.toastOnMainThread(activity,
				activity.getString(R.string.sensor_magnetic_interference));
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// The accuracy is read from the sensor events, see onSensorChanged.
	}

}

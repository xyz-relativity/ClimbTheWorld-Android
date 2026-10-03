package com.climbtheworld.app.sensors.orientation;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

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
	// The declination drifts by a fraction of a degree over tens of kilometres, so it is only
	// recomputed once the observer moved about this far.
	private static final double DECLINATION_UPDATE_DEGREES = 0.1;

	private final WeakReference<AppCompatActivity> parent;
	private IOrientationListener orientationListener;
	private final SensorManager sensorManager;
	private int samplingPeriodUs = SensorManager.SENSOR_DELAY_NORMAL;

	// Holds sensor data
	private static final float[] originRotationMatrix = new float[16];
	private static final float[] remappedRotationMatrix = new float[16];
	private static float[] orientationVector = new float[3];
	private final OrientationEvent orientation = new OrientationEvent();
	private double declinationLatitude = Double.NaN;
	private double declinationLongitude = Double.NaN;
	private double cachedDeclination;
	private int lastMagnetometerAccuracy;

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
	}

	public void requestUpdates(IOrientationListener orientationListener) {
		this.orientationListener = orientationListener;
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), samplingPeriodUs);

		// The rotation vector's own accuracy does not reliably reflect the magnetometer
		// calibration, so the magnetometer is listened to as well, only for its accuracy.
		Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		if (magnetometer != null) {
			lastMagnetometerAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
			sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	public void stopUpdates() {
		sensorManager.unregisterListener(this);
		this.orientationListener = null;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
			// Read from the events rather than onAccuracyChanged: that one only reports changes,
			// starting from unreliable, so a magnetometer unreliable from the start is never
			// reported.
			checkMagnetometerAccuracy(event.accuracy);
		} else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
			// The rotation vector points to magnetic north, while maps and bearings use true north.
			double declination = getDeclination();
			SensorManager.getRotationMatrixFromVector(originRotationMatrix, event.values);

			orientationVector = SensorManager.getOrientation(originRotationMatrix, orientationVector);
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
	 * Angle between magnetic and true north at the observer, positive when magnetic north is east
	 * of true north.
	 */
	private double getDeclination() {
		double latitude = Globals.virtualCamera.decimalLatitude;
		double longitude = Globals.virtualCamera.decimalLongitude;
		if (Double.isNaN(declinationLatitude)
				|| Math.abs(latitude - declinationLatitude) > DECLINATION_UPDATE_DEGREES
				|| Math.abs(longitude - declinationLongitude) > DECLINATION_UPDATE_DEGREES) {
			cachedDeclination = new GeomagneticField((float) latitude, (float) longitude,
					(float) Globals.virtualCamera.elevationMeters, System.currentTimeMillis())
					.getDeclination();
			declinationLatitude = latitude;
			declinationLongitude = longitude;
		}
		return cachedDeclination;
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

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// The accuracy is read from the sensor events, see onSensorChanged.
	}

}

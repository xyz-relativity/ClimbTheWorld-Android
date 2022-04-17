package com.climbtheworld.app.sensors.orientation;

import android.content.Context;
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
	private final WeakReference<AppCompatActivity> parent;
	private IOrientationListener orientationListener;
	private final SensorManager sensorManager;
	private int samplingPeriodUs = SensorManager.SENSOR_DELAY_NORMAL;

	// Holds sensor data
	private static final float[] originRotationMatrix = new float[16];
	private static final float[] remappedRotationMatrix = new float[16];
	private static float[] orientationVector = new float[3];
	private final OrientationEvent orientation = new OrientationEvent();

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
	}

	public void stopUpdates() {
		sensorManager.unregisterListener(this);
		this.orientationListener = null;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
			SensorManager.getRotationMatrixFromVector(originRotationMatrix, event.values);

			orientationVector = SensorManager.getOrientation(originRotationMatrix, orientationVector);
			orientation.screen.x = ((Math.toDegrees(orientationVector[0]) + 360) % 360);  // yah, azimuth
			orientation.screen.y = (Math.toDegrees(orientationVector[1]) % 180);// pitch
			orientation.screen.z = (Math.toDegrees(orientationVector[2]) % 180);// roll

			//align coordinates with the camera (for AR)
			SensorManager.remapCoordinateSystem(originRotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedRotationMatrix);
			orientationVector = SensorManager.getOrientation(remappedRotationMatrix, orientationVector);
			orientation.camera.x = ((Math.toDegrees(orientationVector[0]) + 360) % 360);  // yah, azimuth
			orientation.camera.y = (Math.toDegrees(orientationVector[1]) % 180);
			orientation.camera.z = (Math.toDegrees(orientationVector[2]) % 180);

			Globals.virtualCamera.updateOrientation(orientation);
			if (orientationListener != null) {
				orientationListener.updateOrientation(orientation);
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		switch(sensor.getType()) {
			case Sensor.TYPE_MAGNETIC_FIELD :
				switch(accuracy) {
					case SensorManager.SENSOR_STATUS_ACCURACY_LOW :
						DialogBuilder.toastOnMainThread(parent.get(), parent.get().getString(R.string.sensor_magnetometer_calibration, "10%"));
						break;
					case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM :
						DialogBuilder.toastOnMainThread(parent.get(), parent.get().getString(R.string.sensor_magnetometer_calibration, "50%"));
						break;
					case SensorManager.SENSOR_STATUS_ACCURACY_HIGH :
						break;
				}
				break;
			default:
				break;
		}
	}

}

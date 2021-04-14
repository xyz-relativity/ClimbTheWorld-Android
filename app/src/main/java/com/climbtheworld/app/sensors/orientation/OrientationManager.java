package com.climbtheworld.app.sensors.orientation;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Quaternion;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xyz on 11/24/17.
 */

public class OrientationManager implements SensorEventListener {
	private List<IOrientationListener> handler = new ArrayList<>();
	private SensorManager sensorManager;
	private int samplingPeriodUs = SensorManager.SENSOR_DELAY_NORMAL;

	// Holds sensor data
	private static float[] originRotationMatrix = new float[16];
	private static float[] remappedRotationMatrix = new float[16];
	private static float[] orientationVector = new float[3];
	private OrientationEvent orientation = new OrientationEvent();

	public static class OrientationEvent {
		public Quaternion screen = new Quaternion();
		public Quaternion camera = new Quaternion();

		public Quaternion getAdjusted() {
			if (screen.y > 45 || screen.y < -45) {
				return camera;
			} else {
				return screen;
			}
		}
	}

	public OrientationManager(AppCompatActivity pActivity, int samplingPeriodUs) {
		this.samplingPeriodUs = samplingPeriodUs;
		addListener(Globals.virtualCamera);

		sensorManager = (SensorManager) pActivity.getSystemService(Context.SENSOR_SERVICE);
	}

	public void addListener(IOrientationListener... pHandler) {
		for (IOrientationListener i : pHandler) {
			if (!handler.contains(i)) {
				handler.add(i);
			}
		}
	}

	public void removeListener(IOrientationListener... pHandler) {
		for (IOrientationListener i : pHandler) {
			if (!handler.contains(i)) {
				handler.remove(i);
			}
		}
	}

	public void onResume() {
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), samplingPeriodUs);
	}

	public void onPause() {
		sensorManager.unregisterListener(this);
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

			for (IOrientationListener client : handler) {
				client.updateOrientation(orientation);
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

}

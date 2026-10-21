package com.climbtheworld.app.sensors.pressure;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * The phone's barometer, anchored to GPS, see BarometricAltitude, for a steadier altitude than
 * GPS alone. Without a barometer, the altitude is the GPS one.
 */
public class Barometer implements SensorEventListener {
	private final SensorManager sensorManager;
	// Null on phones without a barometer.
	private final Sensor sensor;
	private final BarometricAltitude altitude = new BarometricAltitude();

	public Barometer(Context context) {
		sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
	}

	public void start() {
		if (sensor != null) {
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	public void stop() {
		sensorManager.unregisterListener(this);
	}

	/**
	 * See BarometricAltitude.anchor.
	 */
	public double anchor(double gpsMeters) {
		return altitude.anchor(gpsMeters);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		altitude.addReading(SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
				event.values[0]));
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}
}

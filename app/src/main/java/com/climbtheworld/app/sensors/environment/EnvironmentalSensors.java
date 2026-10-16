package com.climbtheworld.app.sensors.environment;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.utils.Vector4d;

import java.util.ArrayList;
import java.util.List;

public class EnvironmentalSensors implements SensorEventListener {
	// Constants of the Magnus formula for the vapour pressure over water, in °C.
	private static final double MAGNUS_TEMPERATURE = 243.12;
	private static final double MAGNUS_FACTOR = 17.62;

	private final Sensor pressure;
	private final Sensor temperature;
	private final Sensor light;
	private final Sensor relativeHumidity;
	private final List<IEnvironmentListener> handler = new ArrayList<>();
	private final SensorManager sensorManager;
	// Each reading is NaN until its sensor reports, and for good when the device has none.
	private final Vector4d result = new Vector4d(Double.NaN, Double.NaN, Double.NaN, Double.NaN);

	public EnvironmentalSensors(AppCompatActivity pActivity) {
		sensorManager = (SensorManager) pActivity.getSystemService(Context.SENSOR_SERVICE);

		temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
		light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
		relativeHumidity = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
	}

	public void addListener(IEnvironmentListener... pHandler) {
		for (IEnvironmentListener i : pHandler) {
			if (!handler.contains(i)) {
				handler.add(i);
			}
		}
	}

	public void removeListener(IEnvironmentListener... pHandler) {
		for (IEnvironmentListener i : pHandler) {
			handler.remove(i);
		}
	}

	public void onResume() {
		register(temperature);
		register(light);
		register(pressure);
		register(relativeHumidity);

		// Readings only come from the sensors the device has, so the listeners learn which ones
		// it lacks from here.
		notifyListeners();
	}

	private void register(Sensor sensor) {
		if (sensor != null) {
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	private void notifyListeners() {
		for (IEnvironmentListener client : handler) {
			client.updateSensors(result);
		}
	}

	/**
	 * Temperature at which the air would start to condense, in °C, or NaN unless both readings
	 * are known.
	 */
	public static double dewPoint(double temperatureCelsius, double relativeHumidityPercent) {
		if (!(relativeHumidityPercent > 0)) {
			return Double.NaN;
		}
		double gamma = Math.log(relativeHumidityPercent / 100)
				+ MAGNUS_FACTOR * temperatureCelsius / (MAGNUS_TEMPERATURE + temperatureCelsius);
		return MAGNUS_TEMPERATURE * gamma / (MAGNUS_FACTOR - gamma);
	}

	/**
	 * Mass of water vapour in the air, in g/m³, or NaN unless both readings are known.
	 */
	public static double absoluteHumidity(double temperatureCelsius,
	                                      double relativeHumidityPercent) {
		// The vapour pressure, in hPa, turned into a density through the gas law.
		double vapourPressure = relativeHumidityPercent / 100 * 6.112 * Math.exp(
				MAGNUS_FACTOR * temperatureCelsius / (MAGNUS_TEMPERATURE + temperatureCelsius));
		return 216.7 * vapourPressure / (273.15 + temperatureCelsius);
	}

	public  void onPause() {
		sensorManager.unregisterListener(this);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
			case Sensor.TYPE_AMBIENT_TEMPERATURE:
				result.w = event.values[0];
				break;
			case Sensor.TYPE_PRESSURE:
				result.x = event.values[0];
				break;
			case Sensor.TYPE_LIGHT:
				result.y = event.values[0];
				break;
			case Sensor.TYPE_RELATIVE_HUMIDITY:
				result.z = event.values[0];
				break;
		}

		notifyListeners();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}
}

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
	private final Sensor pressure;
	private final Sensor temperature;
	private final Sensor light;
	private final Sensor relativeHumidity;
	private final List<IEnvironmentListener> handler = new ArrayList<>();
	private final SensorManager sensorManager;
	private final Vector4d result = new Vector4d();

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
			if (!handler.contains(i)) {
				handler.remove(i);
			}
		}
	}

	public void onResume() {
		sensorManager.registerListener(this, temperature, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, relativeHumidity, SensorManager.SENSOR_DELAY_NORMAL);
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

		for (IEnvironmentListener client : handler) {
			client.updateSensors(result);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}
}

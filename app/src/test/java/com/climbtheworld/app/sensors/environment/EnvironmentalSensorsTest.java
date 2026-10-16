package com.climbtheworld.app.sensors.environment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EnvironmentalSensorsTest {
	@Test
	public void dewPointIsBelowTheAirTemperature() {
		assertEquals(9.26, EnvironmentalSensors.dewPoint(20, 50), 0.01);
		assertEquals(16.69, EnvironmentalSensors.dewPoint(25, 60), 0.01);
		assertEquals(-7.92, EnvironmentalSensors.dewPoint(-5, 80), 0.01);
	}

	@Test
	public void saturatedAirIsAtItsDewPoint() {
		assertEquals(20, EnvironmentalSensors.dewPoint(20, 100), 1e-9);
	}

	@Test
	public void noDewPointWithoutBothReadings() {
		assertTrue(Double.isNaN(EnvironmentalSensors.dewPoint(Double.NaN, 50)));
		assertTrue(Double.isNaN(EnvironmentalSensors.dewPoint(20, Double.NaN)));
		// Perfectly dry air never condenses.
		assertTrue(Double.isNaN(EnvironmentalSensors.dewPoint(20, 0)));
	}

	@Test
	public void absoluteHumidityIsTheWaterInTheAir() {
		assertEquals(8.62, EnvironmentalSensors.absoluteHumidity(20, 50), 0.01);
		assertEquals(30.26, EnvironmentalSensors.absoluteHumidity(30, 100), 0.01);
	}

	@Test
	public void noAbsoluteHumidityWithoutBothReadings() {
		assertTrue(Double.isNaN(EnvironmentalSensors.absoluteHumidity(Double.NaN, 50)));
		assertTrue(Double.isNaN(EnvironmentalSensors.absoluteHumidity(20, Double.NaN)));
	}
}

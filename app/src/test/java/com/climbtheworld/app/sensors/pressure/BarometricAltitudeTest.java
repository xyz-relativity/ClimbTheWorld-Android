package com.climbtheworld.app.sensors.pressure;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * BarometricAltitude.
 */
public class BarometricAltitudeTest {
	private static final double CENTIMETER = 0.01;

	@Test
	public void goesByGpsWithoutABarometer() {
		BarometricAltitude altitude = new BarometricAltitude();
		assertEquals(812, altitude.anchor(812), CENTIMETER);
		assertEquals(0, altitude.anchor(0), CENTIMETER);
	}

	@Test
	public void startsAnchoredOnTheFirstFix() {
		BarometricAltitude altitude = new BarometricAltitude();
		// The weather puts the barometer 90 m off.
		altitude.addReading(900);
		assertEquals(810, altitude.anchor(810), CENTIMETER);
	}

	@Test
	public void evensOutTheGpsJumps() {
		BarometricAltitude altitude = new BarometricAltitude();
		altitude.addReading(900);
		altitude.anchor(810);
		// GPS jumps 10 m up and down while the phone stays where it is.
		double highest = 0;
		double lowest = Double.MAX_VALUE;
		for (int fix = 0; fix < 40; fix++) {
			double told = altitude.anchor(fix % 2 == 0 ? 820 : 800);
			highest = Math.max(highest, told);
			lowest = Math.min(lowest, told);
		}
		assertEquals(810, (highest + lowest) / 2, 1);
		assertEquals(0, highest - lowest, 1.5);
	}

	@Test
	public void followsAClimbAtOnce() {
		BarometricAltitude altitude = new BarometricAltitude();
		altitude.addReading(900);
		altitude.anchor(810);
		// Climbing 20 m, as the barometer tells, before GPS catches up.
		for (int reading = 0; reading < 100; reading++) {
			altitude.addReading(920);
		}
		assertEquals(830, altitude.anchor(810), 1.5);
	}

	@Test
	public void keepsTheAltitudeWithoutGps() {
		BarometricAltitude altitude = new BarometricAltitude();
		altitude.addReading(900);
		altitude.anchor(810);
		assertEquals(810, altitude.anchor(0), CENTIMETER);
	}
}

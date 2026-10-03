package com.climbtheworld.app.sensors.orientation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OrientationManagerTest {

	@Test
	public void toTrueAzimuthAddsDeclination() {
		assertEquals(110, OrientationManager.toTrueAzimuth(100, 10), 1e-9);
		assertEquals(86, OrientationManager.toTrueAzimuth(100, -14), 1e-9);
	}

	@Test
	public void toTrueAzimuthWrapsAroundNorth() {
		assertEquals(351, OrientationManager.toTrueAzimuth(5, -14), 1e-9);
		assertEquals(4, OrientationManager.toTrueAzimuth(354, 10), 1e-9);
		// getOrientation reports azimuths between -180 and 180.
		assertEquals(336, OrientationManager.toTrueAzimuth(-10, -14), 1e-9);
		assertEquals(190, OrientationManager.toTrueAzimuth(-175, 5), 1e-9);
	}
}

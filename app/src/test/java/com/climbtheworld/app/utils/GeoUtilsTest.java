package com.climbtheworld.app.utils;

import static org.junit.Assert.assertEquals;

import com.climbtheworld.app.storage.database.GeoNode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GeoUtilsTest {
	private static final double LATITUDE = 46;
	private static final double LONGITUDE = -74;

	@Test
	public void azimuthOfCardinalDirections() {
		GeoNode observer = new GeoNode(LATITUDE, LONGITUDE, 0);

		assertEquals(0, GeoUtils.calculateTheoreticalAzimuth(observer, offset(0, 100)), 1e-6);
		assertEquals(90, GeoUtils.calculateTheoreticalAzimuth(observer, offset(100, 0)), 1e-3);
		assertEquals(180, GeoUtils.calculateTheoreticalAzimuth(observer, offset(0, -100)), 1e-6);
		assertEquals(-90, GeoUtils.calculateTheoreticalAzimuth(observer, offset(-100, 0)), 1e-3);
	}

	@Test
	public void azimuthAccountsForLongitudeShrinkingWithLatitude() {
		GeoNode observer = new GeoNode(LATITUDE, LONGITUDE, 0);

		// Equal distances east and north, which are far from equal degree differences at 46°.
		assertEquals(45, GeoUtils.calculateTheoreticalAzimuth(observer, offset(100, 100)), 0.01);
		assertEquals(-30, GeoUtils.calculateTheoreticalAzimuth(observer,
				offset(-100 * Math.sin(Math.toRadians(30)), 100 * Math.cos(Math.toRadians(30)))),
				0.01);
	}

	private static GeoNode offset(double eastMeters, double northMeters) {
		Vector2d offset = GeoUtils.latLongOffset(LATITUDE, new Vector2d(eastMeters, northMeters));
		return new GeoNode(LATITUDE + offset.y, LONGITUDE + offset.x, 0);
	}
}

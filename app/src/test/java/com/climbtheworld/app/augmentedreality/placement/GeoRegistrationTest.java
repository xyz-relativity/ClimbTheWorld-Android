package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.utils.GeoUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GeoRegistrationTest {
	private static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;
	private static final double ORIGIN_LATITUDE = 45.52;
	private static final double ORIGIN_LONGITUDE = -73.535;
	// Where true north is in the session's world, clockwise from its north.
	private static final double OFFSET_DEGREES = 30;
	private static final double CENTIMETER = 0.01;

	/**
	 * Where a point of a session whose origin is at ORIGIN and turned by OFFSET_DEGREES really is.
	 */
	private static double[] truth(double sessionX, double sessionZ) {
		double offset = Math.toRadians(OFFSET_DEGREES);
		double east = sessionX * Math.cos(offset) - sessionZ * Math.sin(offset);
		double north = -sessionZ * Math.cos(offset) - sessionX * Math.sin(offset);
		return new double[]{ORIGIN_LATITUDE + north / METERS_PER_DEGREE, ORIGIN_LONGITUDE
				+ east / (METERS_PER_DEGREE * Math.cos(Math.toRadians(ORIGIN_LATITUDE)))};
	}

	private static void addHeadings(GeoRegistration registration, int count) {
		for (int i = 0; i < count; i++) {
			double sessionAzimuth = 10 * i;
			registration.addHeading(sessionAzimuth + OFFSET_DEGREES, sessionAzimuth, 5);
		}
	}

	private static void addFix(GeoRegistration registration, double sessionX, double sessionZ,
	                           double northError, double accuracy) {
		double[] fix = truth(sessionX, sessionZ);
		registration.addLocation(fix[0] + northError / METERS_PER_DEGREE, fix[1], accuracy,
				sessionX, sessionZ);
	}

	private static void addCompass(GeoRegistration registration, double offsetDegrees,
	                               double accuracy) {
		for (int i = 0; i < GeoRegistration.MIN_HEADING_READINGS; i++) {
			double sessionAzimuth = 10 * i;
			registration.addHeading(sessionAzimuth + offsetDegrees, sessionAzimuth, accuracy);
		}
	}

	/**
	 * A mapped node where it really is, as the session has it.
	 */
	private static GeoRegistration.Landmark landmark(double sessionX, double sessionZ) {
		double[] mapped = truth(sessionX, sessionZ);
		return new GeoRegistration.Landmark(sessionX, sessionZ, mapped[0], mapped[1]);
	}

	private static double northMeters(double[] latLon, double[] expected) {
		return (latLon[0] - expected[0]) * METERS_PER_DEGREE;
	}

	private static void assertAt(double[] expected, double[] latLon) {
		assertEquals(0, northMeters(latLon, expected), CENTIMETER);
		assertEquals(0, (latLon[1] - expected[1]) * METERS_PER_DEGREE
				* Math.cos(Math.toRadians(ORIGIN_LATITUDE)), CENTIMETER);
	}

	@Test
	public void sessionAzimuthIsClockwiseFromMinusZ() {
		assertEquals(0, GeoRegistration.sessionAzimuthDegrees(0, -1), 1e-9);
		assertEquals(90, GeoRegistration.sessionAzimuthDegrees(1, 0), 1e-9);
		assertEquals(180, GeoRegistration.sessionAzimuthDegrees(0, 1), 1e-9);
		assertEquals(-90, GeoRegistration.sessionAzimuthDegrees(-1, 0), 1e-9);
	}

	@Test
	public void placesPointsWhereTheyAre() {
		GeoRegistration registration = new GeoRegistration();
		addHeadings(registration, GeoRegistration.MIN_HEADING_READINGS);
		// The phone walks along the foot of the crag.
		addFix(registration, 0, 0, 0, 5);
		addFix(registration, 10, -2, 0, 5);
		addFix(registration, 25, -3, 0, 5);

		assertAt(truth(12, -18), registration.toLatLon(12, -18));
		assertAt(truth(-40, 7), registration.toLatLon(-40, 7));
	}

	@Test
	public void averagesTheHeadingAcrossNorth() {
		GeoRegistration registration = new GeoRegistration();
		registration.addHeading(359, 0, 5);
		registration.addHeading(1, 0, 5);

		assertEquals(0, registration.getHeadingOffsetDegrees(), 1e-9);
	}

	@Test
	public void weighsTheHeadingByAccuracy() {
		GeoRegistration registration = new GeoRegistration();
		registration.addHeading(10, 0, 5);
		registration.addHeading(40, 0, 50);

		assertTrue(registration.getHeadingOffsetDegrees() < 11);
	}

	@Test
	public void averagesTheFixes() {
		GeoRegistration registration = new GeoRegistration();
		addHeadings(registration, GeoRegistration.MIN_HEADING_READINGS);
		addFix(registration, 0, 0, 4, 5);
		addFix(registration, 10, -2, -4, 5);

		assertAt(truth(5, -10), registration.toLatLon(5, -10));
	}

	@Test
	public void weighsTheFixesByAccuracy() {
		GeoRegistration registration = new GeoRegistration();
		addHeadings(registration, GeoRegistration.MIN_HEADING_READINGS);
		addFix(registration, 0, 0, 0, 4);
		addFix(registration, 10, -2, 20, 40);

		assertEquals(0, northMeters(registration.toLatLon(0, 0), truth(0, 0)), 0.5);
	}

	@Test
	public void leavesOutInaccurateFixes() {
		GeoRegistration registration = new GeoRegistration();
		addHeadings(registration, GeoRegistration.MIN_HEADING_READINGS);
		addFix(registration, 0, 0, 0, GeoRegistration.MAX_LOCATION_ACCURACY_METERS + 1);
		addFix(registration, 0, 0, 0, Double.NaN);

		assertFalse(registration.hasLocation());
		assertNull(registration.toLatLon(0, 0));
	}

	@Test
	public void waitsForEnoughCompassReadings() {
		GeoRegistration registration = new GeoRegistration();
		addFix(registration, 0, 0, 0, 5);
		addHeadings(registration, GeoRegistration.MIN_HEADING_READINGS - 1);
		assertFalse(registration.isReady());

		addHeadings(registration, 1);
		assertTrue(registration.isReady());
	}

	@Test
	public void forgetsEverythingOnReset() {
		GeoRegistration registration = new GeoRegistration();
		addHeadings(registration, GeoRegistration.MIN_HEADING_READINGS);
		addFix(registration, 0, 0, 0, 5);
		registration.reset();

		assertFalse(registration.hasHeading());
		assertFalse(registration.hasLocation());
		assertTrue(Double.isNaN(registration.getHeadingOffsetDegrees()));
	}

	@Test
	public void movesTheWorldOntoALandmark() {
		GeoRegistration registration = new GeoRegistration();
		addHeadings(registration, GeoRegistration.MIN_HEADING_READINGS);
		// GPS is 10 m off to the north, the whole time.
		addFix(registration, 0, 0, 10, 5);
		addFix(registration, 10, -2, 10, 5);
		registration.setLandmarks(Collections.singletonList(landmark(3, -8)));

		assertAt(truth(5, -9), registration.toLatLon(5, -9));
	}

	@Test
	public void needsNoGpsWithALandmark() {
		GeoRegistration registration = new GeoRegistration();
		addHeadings(registration, GeoRegistration.MIN_HEADING_READINGS);
		registration.setLandmarks(Collections.singletonList(landmark(3, -8)));

		assertTrue(registration.isReady());
		assertAt(truth(5, -9), registration.toLatLon(5, -9));
	}

	@Test
	public void needsNoCompassWithLandmarksApart() {
		GeoRegistration registration = new GeoRegistration();
		registration.setLandmarks(List.of(landmark(-10, -8), landmark(10, -6)));

		assertTrue(registration.hasHeading());
		assertEquals(OFFSET_DEGREES, registration.getHeadingOffsetDegrees(), 1e-3);
		assertAt(truth(0, -20), registration.toLatLon(0, -20));
	}

	@Test
	public void tellsNoHeadingFromLandmarksTooClose() {
		GeoRegistration registration = new GeoRegistration();
		registration.setLandmarks(List.of(landmark(0, -8), landmark(1, -8)));

		assertFalse(registration.hasHeading());
		assertFalse(registration.isReady());
	}

	@Test
	public void landmarksApartSetTheHeadingOverTheCompass() {
		GeoRegistration registration = new GeoRegistration();
		addCompass(registration, OFFSET_DEGREES + 10, GeoRegistration.MIN_HEADING_ACCURACY_DEGREES);
		registration.setLandmarks(List.of(landmark(-5, -8), landmark(5, -8)));

		assertEquals(OFFSET_DEGREES, registration.getHeadingOffsetDegrees(), 1e-3);
	}

	@Test
	public void usesTheCompassUntilLandmarksTellTheHeading() {
		GeoRegistration registration = new GeoRegistration();
		addCompass(registration, OFFSET_DEGREES + 10, GeoRegistration.DEFAULT_HEADING_ACCURACY_DEGREES);
		registration.setLandmarks(List.of(landmark(-1, -8), landmark(1, -8)));

		assertTrue(registration.hasHeading());
		assertEquals(OFFSET_DEGREES + 10, registration.getHeadingOffsetDegrees(), 1e-3);
	}

	@Test
	public void landmarkHeadingIsSurerTheFartherApartTheyAre() {
		double apart = GeoRegistration.landmarkHeadingAccuracyDegrees(
				List.of(new double[]{-50, 0}, new double[]{50, 0}));
		double close = GeoRegistration.landmarkHeadingAccuracyDegrees(
				List.of(new double[]{-5, 0}, new double[]{5, 0}));

		assertTrue(apart > 0 && apart < close);
		// Mapped 2 m off at 10 m apart, as the compass about 15 degrees off.
		assertEquals(16, close, 1);
		assertEquals(1.6, apart, 0.1);
	}

	@Test
	public void landmarksTooFewOrTooCloseTellNoHeadingAccuracy() {
		assertTrue(Double.isNaN(GeoRegistration.landmarkHeadingAccuracyDegrees(
				List.of(new double[]{0, 0}))));
		assertTrue(Double.isNaN(GeoRegistration.landmarkHeadingAccuracyDegrees(
				List.of(new double[]{0, 0}, new double[]{2.9, 0}))));
		assertFalse(Double.isNaN(GeoRegistration.landmarkHeadingAccuracyDegrees(
				List.of(new double[]{0, 0}, new double[]{3.1, 0}))));
	}

	@Test
	public void splitsTheDifferenceBetweenLandmarksMappedOff() {
		GeoRegistration registration = new GeoRegistration();
		addHeadings(registration, GeoRegistration.MIN_HEADING_READINGS);
		GeoRegistration.Landmark exact = landmark(-6, -8);
		GeoRegistration.Landmark mappedOff = landmark(6, -8);
		// Mapped 2 m north of where it is.
		List<GeoRegistration.Landmark> landmarks = new ArrayList<>(List.of(exact,
				new GeoRegistration.Landmark(6, -8, mappedOff.latitude() + 2 / METERS_PER_DEGREE,
						mappedOff.longitude())));
		registration.setLandmarks(landmarks);

		// The world moves 1 m north: half way to the one mapped off.
		assertEquals(1, northMeters(registration.toLatLon(0, -8), truth(0, -8)), 0.1);
	}

	@Test
	public void fallsBackOnGpsWithoutLandmarks() {
		GeoRegistration registration = new GeoRegistration();
		addHeadings(registration, GeoRegistration.MIN_HEADING_READINGS);
		addFix(registration, 0, 0, 0, 5);
		registration.setLandmarks(Collections.singletonList(landmark(30, -8)));
		registration.setLandmarks(Collections.emptyList());

		assertAt(truth(5, -9), registration.toLatLon(5, -9));
	}
}

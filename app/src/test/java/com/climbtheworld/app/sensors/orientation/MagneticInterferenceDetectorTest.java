package com.climbtheworld.app.sensors.orientation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MagneticInterferenceDetectorTest {
	private static final double EARTH_MICRO_TESLA = 53;
	private static final double EARTH_DIP = 69;
	// The magnetometer is read at 5 Hz, so five seconds of readings.
	private static final int FIVE_SECONDS = 25;
	private static final long READING_INTERVAL_NANOS = 200_000_000L;

	// Rotation matrices map device axes to world axes (east, north, up); each column is a device
	// axis.
	private static final float[] FLAT = {
			1, 0, 0, 0,
			0, 1, 0, 0,
			0, 0, 1, 0,
			0, 0, 0, 1};
	// Standing upright, the top of the device pointing up and the screen facing south.
	private static final float[] UPRIGHT = {
			1, 0, 0, 0,
			0, 0, -1, 0,
			0, 1, 0, 0,
			0, 0, 0, 1};

	private final MagneticInterferenceDetector detector = new MagneticInterferenceDetector();
	private long timestampNanos;

	@Test
	public void strengthCombinesTheThreeAxes() {
		assertEquals(13, MagneticInterferenceDetector.strength(new float[]{3, 4, -12}), 1e-6);
	}

	@Test
	public void dipIsTheAngleOfTheFieldBelowTheHorizon() {
		float north = (float) (EARTH_MICRO_TESLA * Math.cos(Math.toRadians(EARTH_DIP)));
		float down = (float) (EARTH_MICRO_TESLA * Math.sin(Math.toRadians(EARTH_DIP)));

		// Lying flat, the device axes are the world's.
		assertEquals(EARTH_DIP,
				MagneticInterferenceDetector.dipDegrees(new float[]{0, north, -down}, FLAT), 1e-3);
		// Standing upright, the field points down the device and out of its back, to the north.
		assertEquals(EARTH_DIP,
				MagneticInterferenceDetector.dipDegrees(new float[]{0, -down, -north}, UPRIGHT),
				1e-3);
	}

	@Test
	public void earthFieldIsUndisturbed() {
		assertFalse(read(57, EARTH_DIP + 4, FIVE_SECONDS));
		assertFalse(read(49, EARTH_DIP - 4, FIVE_SECONDS));
	}

	@Test
	public void strongerOrWeakerFieldIsDisturbed() {
		assertTrue(read(75, EARTH_DIP, FIVE_SECONDS));

		detector.reset();
		assertTrue(read(31, EARTH_DIP, FIVE_SECONDS));
	}

	@Test
	public void fieldBentAtTheRightStrengthIsDisturbed() {
		// As measured with the compass about 90 degrees off: 8% weak only, but 30 degrees too
		// flat.
		assertTrue(read(49, 39, FIVE_SECONDS));
	}

	@Test
	public void singleOddReadingIsIgnored() {
		read(EARTH_MICRO_TESLA, EARTH_DIP, FIVE_SECONDS);

		assertFalse(read(106, 20, 1));
		assertFalse(read(EARTH_MICRO_TESLA, EARTH_DIP, FIVE_SECONDS));
	}

	@Test
	public void nothingIsJudgedBeforeTheReadingsCoverTheSmoothingTime() {
		// The first readings after the magnetometer starts can be off.
		assertFalse(read(106, EARTH_DIP, 5));
		assertTrue(read(106, EARTH_DIP, 1));
	}

	@Test
	public void strengthDisturbanceOnlyEndsWellWithinTheLimit() {
		assertTrue(read(75, EARTH_DIP, FIVE_SECONDS));
		// 20% off: no longer enough to become disturbed, but not enough to stop either.
		assertTrue(read(64, EARTH_DIP, FIVE_SECONDS));
		assertFalse(read(58, EARTH_DIP, FIVE_SECONDS));
	}

	@Test
	public void dipDisturbanceOnlyEndsWellWithinTheLimit() {
		assertTrue(read(EARTH_MICRO_TESLA, EARTH_DIP - 20, FIVE_SECONDS));
		assertTrue(read(EARTH_MICRO_TESLA, EARTH_DIP - 10, FIVE_SECONDS));
		assertFalse(read(EARTH_MICRO_TESLA, EARTH_DIP - 5, FIVE_SECONDS));
	}

	@Test
	public void readingsFasterThanAskedForWeighLess() {
		read(EARTH_MICRO_TESLA, EARTH_DIP, FIVE_SECONDS);

		// A tenth of a second of odd readings at 50 Hz moves the average as little as one
		// reading at 10 Hz.
		for (int reading = 0; reading < 5; reading++) {
			timestampNanos += READING_INTERVAL_NANOS / 10;
			assertFalse(detector.update(106, EARTH_MICRO_TESLA, EARTH_DIP, EARTH_DIP,
					timestampNanos));
		}
	}

	/**
	 * Feeds readings of the same field, a fifth of a second apart.
	 *
	 * @return whether the field is disturbed after the last one
	 */
	private boolean read(double microTesla, double dipDegrees, int readings) {
		boolean disturbed = detector.isDisturbed();
		for (int reading = 0; reading < readings; reading++) {
			timestampNanos += READING_INTERVAL_NANOS;
			disturbed = detector.update(microTesla, EARTH_MICRO_TESLA, dipDegrees, EARTH_DIP,
					timestampNanos);
		}
		return disturbed;
	}
}

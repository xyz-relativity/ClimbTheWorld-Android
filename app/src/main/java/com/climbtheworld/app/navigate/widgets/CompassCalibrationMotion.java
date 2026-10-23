package com.climbtheworld.app.navigate.widgets;

/**
 * The motion that calibrates the compass, as CompassCalibrationView shows it: the phone draws an
 * infinity sign in the air and turns every way as it goes, so that the magnetometer measures the
 * field from every side. Each loop starts at the middle of the sign, heading up and to the right.
 */
final class CompassCalibrationMotion {
	// The sign is a lemniscate of Bernoulli stretched upwards, so that its loops are as round as
	// those of the infinity sign rather than pointed.
	static final double SIGN_STRETCH = 1.35;
	// How far either way the phone turns over a loop. It faces the viewer each time it crosses
	// the middle, rolled one way and then the other; tilts back and then forward along each half
	// of the sign; and is turned away at either end, showing its side.
	static final double YAW_DEGREES = 65;
	static final double PITCH_DEGREES = 60;
	static final double ROLL_DEGREES = 40;

	private CompassCalibrationMotion() {
		//hide constructor
	}

	/**
	 * A point of the sign, which reaches 1 either side of its middle, the y axis pointing down
	 * as on the screen.
	 *
	 * @param loop how far along a loop, from 0 to 1, by the curve's own parameter rather than by
	 *             length
	 * @param point set to x and y
	 */
	static void signPoint(double loop, double[] point) {
		// The curve crosses its middle at a quarter and three quarters of its own parameter.
		double t = Math.PI / 2 + 2 * Math.PI * loop;
		double sin = Math.sin(t);
		double cos = Math.cos(t);
		double divisor = 1 + sin * sin;
		point[0] = -cos / divisor;
		point[1] = SIGN_STRETCH * sin * cos / divisor;
	}

	/**
	 * How the phone is turned along a loop.
	 *
	 * @param loop  how far along it, from 0 to 1 by length
	 * @param turns set to the yaw, pitch and roll in degrees, see rotation()
	 */
	static void phoneTurns(double loop, double[] turns) {
		double angle = 2 * Math.PI * loop;
		turns[0] = YAW_DEGREES * Math.sin(angle);
		turns[1] = -PITCH_DEGREES * Math.sin(2 * angle);
		turns[2] = ROLL_DEGREES * Math.cos(angle);
	}

	/**
	 * The rotation of the phone, in its own axes: x to its right, y to its top and z out of its
	 * screen, which faces the viewer before it is turned. The phone turns about its y axis by the
	 * yaw, its screen towards its right, then tilts about the x axis by the pitch, its top
	 * towards the viewer, and rolls about the line of sight by the roll, anticlockwise.
	 *
	 * @param turns  the yaw, pitch and roll in degrees
	 * @param matrix set to the rotation, by rows
	 */
	static void rotation(double[] turns, double[] matrix) {
		double sinYaw = Math.sin(Math.toRadians(turns[0]));
		double cosYaw = Math.cos(Math.toRadians(turns[0]));
		double sinPitch = Math.sin(Math.toRadians(turns[1]));
		double cosPitch = Math.cos(Math.toRadians(turns[1]));
		double sinRoll = Math.sin(Math.toRadians(turns[2]));
		double cosRoll = Math.cos(Math.toRadians(turns[2]));
		// Roll * pitch * yaw.
		matrix[0] = cosRoll * cosYaw - sinRoll * sinPitch * sinYaw;
		matrix[1] = -sinRoll * cosPitch;
		matrix[2] = cosRoll * sinYaw + sinRoll * sinPitch * cosYaw;
		matrix[3] = sinRoll * cosYaw + cosRoll * sinPitch * sinYaw;
		matrix[4] = cosRoll * cosPitch;
		matrix[5] = sinRoll * sinYaw - cosRoll * sinPitch * cosYaw;
		matrix[6] = -cosPitch * sinYaw;
		matrix[7] = sinPitch;
		matrix[8] = cosPitch * cosYaw;
	}
}

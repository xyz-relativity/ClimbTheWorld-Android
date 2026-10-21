package com.climbtheworld.app.augmentedreality.placement;

/**
 * The camera ARCore tracks, in the terms of the A.R. view's camera, VirtualCamera: which way it
 * looks, how far it is turned on the screen, and how much it sees.
 */
public class SessionViewAngles {
	// The camera's axis, see GeoRegistration.sessionAzimuthDegrees.
	public final double azimuthDegrees;
	// The camera's axis above the horizontal.
	public final double pitchDegrees;
	// How far the phone is turned clockwise from upright, as VirtualCamera.degRoll with no screen
	// rotation: the A.R. view turns the world the other way on the screen.
	public final double rollDegrees;
	// Across the view and up it.
	public final double horizontalAngleOfViewDegrees;
	public final double verticalAngleOfViewDegrees;

	SessionViewAngles(double azimuthDegrees, double pitchDegrees, double rollDegrees,
	                  double horizontalAngleOfViewDegrees, double verticalAngleOfViewDegrees) {
		this.azimuthDegrees = azimuthDegrees;
		this.pitchDegrees = pitchDegrees;
		this.rollDegrees = rollDegrees;
		this.horizontalAngleOfViewDegrees = horizontalAngleOfViewDegrees;
		this.verticalAngleOfViewDegrees = verticalAngleOfViewDegrees;
	}

	/**
	 * @param xAxis      the screen's right, of the camera's pose as the display shows it
	 * @param yAxis      the screen's up
	 * @param zAxis      out of the screen, as the camera looks down -Z
	 * @param projection the camera's projection for the view, column-major
	 */
	public static SessionViewAngles of(float[] xAxis, float[] yAxis, float[] zAxis,
	                                   float[] projection) {
		double azimuth = GeoRegistration.sessionAzimuthDegrees(-zAxis[0], -zAxis[2]);
		double pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -zAxis[1]))));
		// Which way up the world shows on the screen, clockwise from the screen's up: the world's
		// up along the screen's right and up.
		double worldUpOnScreen = Math.toDegrees(Math.atan2(xAxis[1], yAxis[1]));
		return new SessionViewAngles(azimuth, pitch, -worldUpOnScreen,
				Math.toDegrees(2 * Math.atan(1 / projection[0])),
				Math.toDegrees(2 * Math.atan(1 / projection[5])));
	}
}

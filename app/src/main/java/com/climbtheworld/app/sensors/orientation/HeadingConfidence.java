package com.climbtheworld.app.sensors.orientation;

/**
 * How far the heading is to be trusted, in three steps, for the views to show: a compass blurred
 * a little while the heading is rough, more while it is not to be trusted, see CompassBlur. Set
 * from how far off the heading may be, see OrientationManager.OrientationEvent's
 * headingUncertaintyDegrees, with some leeway either side of each step, so that an uncertainty
 * wavering about a step does not make the compass flicker.
 */
public enum HeadingConfidence {
	// Off by no more than SURE_DEGREES.
	SURE,
	// Off by no more than ROUGH_DEGREES.
	ROUGH,
	UNSURE;

	static final double SURE_DEGREES = 10;
	static final double ROUGH_DEGREES = 25;
	// How far past a step the uncertainty must go, either way, before the level changes.
	static final double HYSTERESIS_DEGREES = 2;

	/**
	 * @param uncertaintyDegrees how far off the heading may be, NaN when nothing tells, which
	 *                           leaves it SURE: a blur is for a doubt the device knows of
	 * @param previous           the level so far, which the leeway favours, null at first
	 */
	public static HeadingConfidence of(double uncertaintyDegrees, HeadingConfidence previous) {
		if (Double.isNaN(uncertaintyDegrees)) {
			return SURE;
		}
		double sureLimit = SURE_DEGREES;
		double roughLimit = ROUGH_DEGREES;
		if (previous != null) {
			sureLimit += previous == SURE ? HYSTERESIS_DEGREES : -HYSTERESIS_DEGREES;
			roughLimit += previous == UNSURE ? -HYSTERESIS_DEGREES : HYSTERESIS_DEGREES;
		}
		if (uncertaintyDegrees <= sureLimit) {
			return SURE;
		}
		if (uncertaintyDegrees <= roughLimit) {
			return ROUGH;
		}
		return UNSURE;
	}
}

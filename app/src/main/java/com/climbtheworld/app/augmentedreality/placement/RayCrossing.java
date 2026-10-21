package com.climbtheworld.app.augmentedreality.placement;

/**
 * Where two rays aimed at the same spot from two places cross: a route top too far for the depth,
 * sighted once, then again from a few meters to the side. As no aim is exact, the rays mostly pass
 * each other: the spot is taken half way between where they come closest.
 */
public final class RayCrossing {
	// Rays closer to parallel than this tell little how far the spot is.
	static final double MIN_ANGLE_DEGREES = 2;
	// How far off an aim through the crosshair may be.
	static final double AIM_ERROR_DEGREES = 0.25;

	private RayCrossing() {
		//hide
	}

	/**
	 * @param first        the first sighting
	 * @param second       the second sighting, the camera's now
	 * @param facingNormal which way the rock faces there, null to face the second camera
	 * @return the spot, null when the rays cross behind either camera, or at too narrow an angle
	 */
	public static CursorHit cross(ViewRay first, ViewRay second, double[] facingNormal) {
		double cos = CursorHit.dot(first.direction, second.direction);
		double angle = Math.acos(Math.max(-1, Math.min(1, cos)));
		if (angle < Math.toRadians(MIN_ANGLE_DEGREES)) {
			return null;
		}

		// How far along each ray they come closest.
		double[] between = CursorHit.subtract(first.origin, second.origin);
		double firstOff = CursorHit.dot(first.direction, between);
		double secondOff = CursorHit.dot(second.direction, between);
		double denominator = 1 - cos * cos;
		double alongFirst = (cos * secondOff - firstOff) / denominator;
		double alongSecond = (secondOff - cos * firstOff) / denominator;
		if (alongFirst <= 0 || alongSecond <= 0) {
			return null;
		}

		double[] onFirst = first.at(alongFirst);
		double[] onSecond = second.at(alongSecond);
		double[] spot = CursorHit.scale(new double[]{onFirst[0] + onSecond[0],
				onFirst[1] + onSecond[1], onFirst[2] + onSecond[2]}, 0.5);
		double[] towardCamera = CursorHit.subtract(second.origin, spot);
		double distance = CursorHit.length(towardCamera);
		double[] normal = facingNormal != null ? facingNormal
				: CursorHit.scale(towardCamera, 1 / distance);
		// An aim off by the aim error moves the spot along the other ray by this much.
		double uncertainty = Math.toRadians(AIM_ERROR_DEGREES)
				* Math.hypot(alongFirst, alongSecond) / Math.sin(angle);
		return new CursorHit(spot, normal, distance, CursorHit.Source.SIGHTING, uncertainty);
	}
}

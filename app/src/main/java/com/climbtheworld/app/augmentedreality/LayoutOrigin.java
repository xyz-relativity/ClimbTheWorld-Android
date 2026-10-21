package com.climbtheworld.app.augmentedreality;

/**
 * Where the hulls or the routes were last laid out from, in a local frame of meters, and how
 * far from there a position is.
 */
final class LayoutOrigin {
	private double latitude = Double.NaN;
	private double longitude = Double.NaN;

	/**
	 * @return whether the layout has to be done again from the position: nothing was laid out
	 * yet, or it is too far from where it was for the flat frame to hold
	 */
	boolean needsLayout(double latitude, double longitude) {
		return Double.isNaN(this.latitude) || Math.hypot(eastOf(longitude), northOf(latitude))
				> ClimbingHulls.REBUILD_DISTANCE_METERS;
	}

	void moveTo(double latitude, double longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}

	/** @return how far east of the origin the position is, in meters */
	double eastOf(double longitude) {
		return (longitude - this.longitude) * ClimbingHull.METERS_PER_DEGREE
				* Math.cos(Math.toRadians(latitude));
	}

	/** @return how far north of the origin the position is, in meters */
	double northOf(double latitude) {
		return (latitude - this.latitude) * ClimbingHull.METERS_PER_DEGREE;
	}
}

package com.climbtheworld.app.augmentedreality;

/**
 * Whether the user is close enough to mapped routes to align the A.R. on them, which is when the
 * A.R. view shows ARCore's aiming tools, see ArViewTracking. Farther away, they add nothing. Near
 * once a route is within NEAR_METERS, and until all are farther than FAR_METERS: the gap keeps the
 * tools from coming and going at the edge, as GPS wanders.
 */
public class AlignmentRange {
	// ARCore measures depth up to about 20 m outdoors; a little more, as the user aims from where
	// they stand.
	public static final double NEAR_METERS = 30;
	public static final double FAR_METERS = 60;

	private boolean near;

	/**
	 * @param nearestMeters how far the closest mapped route is, NaN when none is known, which
	 *                      counts as far
	 * @return whether the user is near routes now
	 */
	public boolean update(double nearestMeters) {
		if (nearestMeters <= NEAR_METERS) {
			near = true;
		} else if (!(nearestMeters < FAR_METERS)) {
			near = false;
		}
		return near;
	}

	public boolean isNear() {
		return near;
	}
}

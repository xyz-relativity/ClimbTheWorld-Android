package com.climbtheworld.app.augmentedreality.placement;

/**
 * The eye's elevation, from the mapped routes the user aligned on: each route's base is at the
 * ground elevation the terrain model has under it, and ARCore knows how far above or below the
 * aimed spot the phone is. So the pins then stand at the height they were aimed at, and the eye
 * follows the phone up and down as ARCore tracks it, as when climbing.
 */
public final class AlignedHeight {
	private AlignedHeight() {
	}

	/**
	 * @param cameraY        the phone's height in the session's world
	 * @param baseYs         the aimed spots' heights in the session's world, one per route, NaN
	 *                       where the session does not know it now
	 * @param baseElevations the ground elevation under each route above sea level, NaN where it
	 *                       is not known
	 * @return the eye's elevation above sea level, averaged over the routes known both ways, NaN
	 * without any
	 */
	public static double eyeElevation(double cameraY, double[] baseYs, double[] baseElevations) {
		double sum = 0;
		int count = 0;
		for (int route = 0; route < baseYs.length; route++) {
			if (Double.isNaN(baseYs[route]) || Double.isNaN(baseElevations[route])) {
				continue;
			}
			sum += baseElevations[route] + (cameraY - baseYs[route]);
			count++;
		}
		return count == 0 ? Double.NaN : sum / count;
	}
}

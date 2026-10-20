package com.climbtheworld.app.augmentedreality.placement;

/**
 * Where a point of the session's world shows in the view, to draw a pin over it.
 */
public class ScreenPoint {
	// How far above the point the world's up is taken, to turn a pin upright.
	private static final double UP_METERS = 1;

	// In view pixels, from the top left.
	public final float x;
	public final float y;
	// Clockwise, what keeps a pin drawn there upright in the world, as the view turns with the
	// phone.
	public final float uprightDegrees;

	ScreenPoint(float x, float y, float uprightDegrees) {
		this.x = x;
		this.y = y;
		this.uprightDegrees = uprightDegrees;
	}

	/**
	 * @param viewProjection the camera's projection times its view, column-major
	 * @return where the point shows, null when it is behind the camera
	 */
	public static ScreenPoint project(float[] viewProjection, double[] point, int viewWidth,
	                                  int viewHeight) {
		double[] at = toView(viewProjection, point[0], point[1], point[2], viewWidth, viewHeight);
		if (at == null) {
			return null;
		}
		double[] above = toView(viewProjection, point[0], point[1] + UP_METERS, point[2],
				viewWidth, viewHeight);
		double upright = above == null ? 0
				: Math.toDegrees(Math.atan2(above[0] - at[0], at[1] - above[1]));
		return new ScreenPoint((float) at[0], (float) at[1], (float) upright);
	}

	private static double[] toView(float[] m, double x, double y, double z, int viewWidth,
	                               int viewHeight) {
		double clipX = m[0] * x + m[4] * y + m[8] * z + m[12];
		double clipY = m[1] * x + m[5] * y + m[9] * z + m[13];
		double clipW = m[3] * x + m[7] * y + m[11] * z + m[15];
		if (clipW <= 0) {
			return null;
		}
		return new double[]{(clipX / clipW + 1) / 2 * viewWidth,
				(1 - clipY / clipW) / 2 * viewHeight};
	}
}

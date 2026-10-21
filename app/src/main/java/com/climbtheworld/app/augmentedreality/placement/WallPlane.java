package com.climbtheworld.app.augmentedreality.placement;

/**
 * A plane fitted to the rock the depth sees well around the crosshair, to carry the cursor on up
 * the wall past where ARCore measures depth: a route top 25 m up is beyond it, while the wall
 * lower down is not. It is wrong where the wall changes angle, at a roof or a big ledge, which
 * sighting the spot from two places gets right, see RayCrossing.
 */
public class WallPlane {
	// The depth is only fitted to this close, where ARCore measures it best.
	public static final double MAX_SOURCE_METERS = 15;
	// The patch fitted to, in depth pixels either side of the crosshair, and between its samples.
	static final int PATCH_PIXELS = 12;
	static final int PATCH_STEP = 2;
	static final int MIN_POINTS = 12;
	// A patch further off any plane than this, on average, is no flat rock to carry on from.
	static final double MAX_RMS_METERS = 0.15;
	// A ray this close to along the plane meets it too far off for the slightest error.
	static final double MIN_INCIDENCE_DEGREES = 10;
	static final double MAX_DISTANCE_METERS = 150;
	private static final int POWER_ITERATIONS = 50;

	public final double[] point;
	// A unit vector, out of the rock towards where it was seen from.
	public final double[] normal;

	WallPlane(double[] point, double[] normal) {
		this.point = point;
		this.normal = normal;
	}

	/**
	 * @param pixelX where the crosshair is in the depth image
	 * @param pixelY where the crosshair is in the depth image
	 * @return the plane, null where the depth around the crosshair is not close, flat or known
	 * enough
	 */
	public static WallPlane fit(DepthFrame frame, double pixelX, double pixelY) {
		int centreX = Math.max(0, Math.min(frame.getWidth() - 1, (int) Math.floor(pixelX)));
		int centreY = Math.max(0, Math.min(frame.getHeight() - 1, (int) Math.floor(pixelY)));
		double centreDepth = frame.depthMeters(centreX, centreY);
		if (Double.isNaN(centreDepth) || centreDepth > MAX_SOURCE_METERS) {
			return null;
		}

		// The patch on the same surface as the crosshair.
		int capacity = (2 * PATCH_PIXELS / PATCH_STEP + 1) * (2 * PATCH_PIXELS / PATCH_STEP + 1);
		double[][] points = new double[capacity][];
		int count = 0;
		double[] middle = new double[3];
		for (int y = centreY - PATCH_PIXELS; y <= centreY + PATCH_PIXELS; y += PATCH_STEP) {
			for (int x = centreX - PATCH_PIXELS; x <= centreX + PATCH_PIXELS; x += PATCH_STEP) {
				if (x < 0 || y < 0 || x >= frame.getWidth() || y >= frame.getHeight()) {
					continue;
				}
				double depth = frame.depthMeters(x, y);
				if (Double.isNaN(depth) || !DepthWireframe.sameSurface(centreDepth, depth)) {
					continue;
				}
				double[] point = new double[3];
				frame.toWorld(x, y, depth, point);
				points[count++] = point;
				for (int axis = 0; axis < 3; axis++) {
					middle[axis] += point[axis];
				}
			}
		}
		if (count < MIN_POINTS) {
			return null;
		}
		for (int axis = 0; axis < 3; axis++) {
			middle[axis] /= count;
		}

		double[][] covariance = new double[3][3];
		for (int i = 0; i < count; i++) {
			double[] offset = CursorHit.subtract(points[i], middle);
			for (int row = 0; row < 3; row++) {
				for (int column = 0; column < 3; column++) {
					covariance[row][column] += offset[row] * offset[column] / count;
				}
			}
		}
		double[] towardCamera = CursorHit.subtract(frame.cameraPosition(), middle);
		double[] normal = leastSpreadDirection(covariance, towardCamera);
		if (normal == null) {
			return null;
		}

		double squares = 0;
		for (int i = 0; i < count; i++) {
			double off = CursorHit.dot(CursorHit.subtract(points[i], middle), normal);
			squares += off * off;
		}
		if (Math.sqrt(squares / count) > MAX_RMS_METERS) {
			return null;
		}
		if (CursorHit.dot(normal, towardCamera) < 0) {
			normal = CursorHit.scale(normal, -1);
		}
		return new WallPlane(middle, normal);
	}

	/**
	 * The direction the points spread least along, the plane's normal: the eigenvector of the
	 * smallest eigenvalue of their covariance, found as the largest of the trace less it.
	 *
	 * @param start a first guess, not along the plane
	 * @return a unit vector, null when there is none
	 */
	private static double[] leastSpreadDirection(double[][] covariance, double[] start) {
		double trace = covariance[0][0] + covariance[1][1] + covariance[2][2];
		double[] vector = start.clone();
		for (int iteration = 0; iteration < POWER_ITERATIONS; iteration++) {
			double length = CursorHit.length(vector);
			if (length < 1e-12) {
				return null;
			}
			double[] unit = CursorHit.scale(vector, 1 / length);
			for (int row = 0; row < 3; row++) {
				vector[row] = trace * unit[row];
				for (int column = 0; column < 3; column++) {
					vector[row] -= covariance[row][column] * unit[column];
				}
			}
		}
		double length = CursorHit.length(vector);
		return length < 1e-12 ? null : CursorHit.scale(vector, 1 / length);
	}

	/**
	 * @return where the ray meets the plane, null when it does not in front of it, does too far
	 * away, or only glancingly
	 */
	public CursorHit intersect(ViewRay ray) {
		double along = CursorHit.dot(normal, ray.direction);
		if (Math.abs(along) < Math.sin(Math.toRadians(MIN_INCIDENCE_DEGREES))) {
			return null;
		}
		double distance = CursorHit.dot(normal, CursorHit.subtract(point, ray.origin)) / along;
		if (distance <= 0 || distance > MAX_DISTANCE_METERS) {
			return null;
		}
		// Facing the camera the ray comes from.
		double[] facing = along > 0 ? CursorHit.scale(normal, -1) : normal;
		return new CursorHit(ray.at(distance), facing, distance, CursorHit.Source.WALL_PLANE,
				Double.NaN);
	}
}

package com.climbtheworld.app.augmentedreality.placement;

/**
 * The rock as ARCore's depth sees it: a net of lines fixed in the session's world, draped over
 * the rock. The lines are where the rock crosses evenly spaced planes: level ones, upright ones
 * along the way the user faced, and upright ones across it. So they stay on the rock while the
 * phone moves, and bend over its shape. A grid laid on the depth image instead would only ever
 * look like a flat grid on the screen, since seen from where it was measured, depth does not show.
 * <p>
 * Each frame's depth is cut into triangles between neighbouring pixels on the same surface, and
 * each triangle into the pieces of lines crossing it. Keeps its buffers from one frame to the
 * next.
 */
public class DepthWireframe {
	// Depth pixels sampled across the image; the rows are as far apart.
	static final int COLUMNS = 80;
	// Neighbours whose depths differ by more than this share of the nearer one are on different
	// surfaces, like a tree in front of the rock, and are not joined. Up to it, a surface is
	// joined even seen 80 degrees from square on.
	static final double MAX_DEPTH_STEP = 0.15;
	// The net is finer close by. Each spacing is twice the one before, so the far lines go on
	// from the close ones. The depths up to which each spacing but the last is used:
	static final double[] SPACING_DEPTHS_METERS = {10, 20};
	static final double[] SPACINGS_METERS = {0.5, 1, 2};
	// The lines fade from close to far, which also shows how far the rock is.
	static final double NEAR_METERS = 2;
	static final float[] NEAR_COLOR = {0.45f, 1f, 0.85f, 0.85f};
	static final float[] FAR_COLOR = {0.35f, 0.55f, 1f, 0.2f};
	// The coordinates the planes are spaced along, see coordinates.
	private static final int ALONG = 0;
	private static final int UP = 1;
	private static final int ACROSS = 2;

	private final float[] color = new float[4];
	// The way the user faced, and to its right, horizontal unit vectors of the session's world.
	private double facingX = 0;
	private double facingZ = -1;
	private double rightX = 1;
	private double rightZ = 0;
	private double[] depths = new double[0];
	private double[][] points = new double[0][];
	// Per sample: along, up and across, see ALONG, UP and ACROSS.
	private double[][] coordinates = new double[0][];

	/**
	 * Whether two depths are close enough to be on the same surface.
	 */
	static boolean sameSurface(double depth, double otherDepth) {
		return Math.abs(depth - otherDepth) <= MAX_DEPTH_STEP * Math.min(depth, otherDepth);
	}

	static double spacingAt(double depth) {
		for (int band = 0; band < SPACING_DEPTHS_METERS.length; band++) {
			if (depth < SPACING_DEPTHS_METERS[band]) {
				return SPACINGS_METERS[band];
			}
		}
		return SPACINGS_METERS[SPACINGS_METERS.length - 1];
	}

	private void colorAt(double depth) {
		double share = (depth - NEAR_METERS) / (DepthFrame.MAX_DEPTH_METERS - NEAR_METERS);
		float far = (float) Math.max(0, Math.min(1, share));
		for (int i = 0; i < color.length; i++) {
			color[i] = NEAR_COLOR[i] + (FAR_COLOR[i] - NEAR_COLOR[i]) * far;
		}
	}

	/**
	 * Lines the net up with the way the user faces, so that on the rock in front of them it is
	 * square: upright and level lines.
	 *
	 * @param sessionAzimuthDegrees see GeoRegistration.sessionAzimuthDegrees
	 */
	public void setFacing(double sessionAzimuthDegrees) {
		double azimuth = Math.toRadians(sessionAzimuthDegrees);
		facingX = Math.sin(azimuth);
		facingZ = -Math.cos(azimuth);
		rightX = Math.cos(azimuth);
		rightZ = Math.sin(azimuth);
	}

	/**
	 * Adds the net over the rock a depth image sees to out.
	 */
	public void build(DepthFrame frame, LineVertices out) {
		int step = Math.max(1, frame.getWidth() / COLUMNS);
		int columns = (frame.getWidth() - 1) / step + 1;
		int rows = (frame.getHeight() - 1) / step + 1;
		ensureCapacity(columns * rows);

		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				int index = row * columns + column;
				int x = column * step;
				int y = row * step;
				depths[index] = frame.depthMeters(x, y);
				if (!Double.isNaN(depths[index])) {
					double[] point = points[index];
					frame.toWorld(x, y, depths[index], point);
					coordinates[index][ALONG] = point[0] * rightX + point[2] * rightZ;
					coordinates[index][UP] = point[1];
					coordinates[index][ACROSS] = point[0] * facingX + point[2] * facingZ;
				}
			}
		}

		for (int row = 0; row + 1 < rows; row++) {
			for (int column = 0; column + 1 < columns; column++) {
				int index = row * columns + column;
				triangle(index, index + 1, index + columns, out);
				triangle(index + 1, index + columns + 1, index + columns, out);
			}
		}
	}

	private void triangle(int a, int b, int c, LineVertices out) {
		if (Double.isNaN(depths[a]) || Double.isNaN(depths[b]) || Double.isNaN(depths[c])
				|| !sameSurface(depths[a], depths[b]) || !sameSurface(depths[b], depths[c])
				|| !sameSurface(depths[a], depths[c])) {
			return;
		}
		double spacing = spacingAt(Math.max(depths[a], Math.max(depths[b], depths[c])));
		colorAt((depths[a] + depths[b] + depths[c]) / 3);
		for (int coordinate = ALONG; coordinate <= ACROSS; coordinate++) {
			contour(a, b, c, coordinate, spacing, out);
		}
	}

	/**
	 * Adds the pieces of the planes, spacing apart along a coordinate, that cross a triangle.
	 */
	private void contour(int a, int b, int c, int coordinate, double spacing,
	                     LineVertices out) {
		double valueA = coordinates[a][coordinate] / spacing;
		double valueB = coordinates[b][coordinate] / spacing;
		double valueC = coordinates[c][coordinate] / spacing;
		double highest = Math.max(valueA, Math.max(valueB, valueC));
		for (double level = Math.ceil(Math.min(valueA, Math.min(valueB, valueC)));
		     level <= highest; level++) {
			boolean aboveA = valueA > level;
			boolean aboveB = valueB > level;
			boolean aboveC = valueC > level;
			if (aboveA == aboveB && aboveB == aboveC) {
				continue;
			}
			// The plane crosses the two sides between a corner above it and one not.
			if (aboveA != aboveB) {
				addCrossing(a, b, valueA, valueB, level, out);
			}
			if (aboveB != aboveC) {
				addCrossing(b, c, valueB, valueC, level, out);
			}
			if (aboveC != aboveA) {
				addCrossing(c, a, valueC, valueA, level, out);
			}
		}
	}

	private void addCrossing(int from, int to, double fromValue, double toValue, double level,
	                         LineVertices out) {
		double share = (level - fromValue) / (toValue - fromValue);
		double[] start = points[from];
		double[] end = points[to];
		out.add(start[0] + (end[0] - start[0]) * share, start[1] + (end[1] - start[1]) * share,
				start[2] + (end[2] - start[2]) * share, color);
	}

	private void ensureCapacity(int size) {
		if (depths.length >= size) {
			return;
		}
		depths = new double[size];
		points = new double[size][3];
		coordinates = new double[size][3];
	}
}

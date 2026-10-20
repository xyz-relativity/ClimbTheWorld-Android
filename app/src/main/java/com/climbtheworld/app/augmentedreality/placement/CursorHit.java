package com.climbtheworld.app.augmentedreality.placement;

import java.util.Arrays;

/**
 * Where the crosshair meets the rock: the point of the depth image under it, and which way the
 * rock faces there.
 */
public class CursorHit {
	// How many depth pixels apart the points the rock's slope is measured between are.
	static final int NORMAL_SPAN = 3;

	// In the session's world.
	public final double[] point;
	// A unit vector out of the rock, towards the camera's side.
	public final double[] normal;
	// From the camera, in meters.
	public final double distance;

	CursorHit(double[] point, double[] normal, double distance) {
		this.point = point;
		this.normal = normal;
		this.distance = distance;
	}

	/**
	 * @param pixelX where the crosshair is in the depth image
	 * @param pixelY where the crosshair is in the depth image
	 * @return the hit, null where the depth around the crosshair is unknown
	 */
	public static CursorHit find(DepthFrame frame, double pixelX, double pixelY) {
		int x = clamp((int) Math.floor(pixelX), frame.getWidth());
		int y = clamp((int) Math.floor(pixelY), frame.getHeight());
		double depth = medianDepth(frame, x, y);
		if (Double.isNaN(depth)) {
			return null;
		}

		double[] point = new double[3];
		frame.toWorld(pixelX, pixelY, depth, point);
		double[] camera = frame.cameraPosition();
		double[] towardCamera = subtract(camera, point);
		double distance = length(towardCamera);

		double[] normal = surfaceNormal(frame, x, y, depth, point);
		if (normal == null) {
			normal = scale(towardCamera, 1 / distance);
		} else if (dot(normal, towardCamera) < 0) {
			normal = scale(normal, -1);
		}
		return new CursorHit(point, normal, distance);
	}

	private static int clamp(int value, int size) {
		return Math.max(0, Math.min(size - 1, value));
	}

	/**
	 * The median of the known depths of a pixel and the ones around it, which steadies the point
	 * against single wrong pixels.
	 */
	private static double medianDepth(DepthFrame frame, int x, int y) {
		double[] known = new double[9];
		int count = 0;
		for (int row = Math.max(0, y - 1); row <= Math.min(frame.getHeight() - 1, y + 1); row++) {
			for (int column = Math.max(0, x - 1); column <= Math.min(frame.getWidth() - 1, x + 1);
			     column++) {
				double depth = frame.depthMeters(column, row);
				if (!Double.isNaN(depth)) {
					known[count++] = depth;
				}
			}
		}
		if (count == 0) {
			return Double.NaN;
		}
		Arrays.sort(known, 0, count);
		return known[count / 2];
	}

	/**
	 * Which way the surface faces, from points NORMAL_SPAN pixels across and down, taken on the
	 * hit's own side of a gap when the other side is not the same surface.
	 *
	 * @return a unit vector facing either way, null when no slope can be measured
	 */
	private static double[] surfaceNormal(DepthFrame frame, int x, int y, double depth,
	                                      double[] point) {
		double[] across = slope(neighbour(frame, x - NORMAL_SPAN, y, depth),
				neighbour(frame, x + NORMAL_SPAN, y, depth), point);
		double[] down = slope(neighbour(frame, x, y - NORMAL_SPAN, depth),
				neighbour(frame, x, y + NORMAL_SPAN, depth), point);
		if (across == null || down == null) {
			return null;
		}
		double[] normal = cross(across, down);
		double length = length(normal);
		return length < 1e-9 ? null : scale(normal, 1 / length);
	}

	private static double[] neighbour(DepthFrame frame, int x, int y, double depth) {
		if (x < 0 || y < 0 || x >= frame.getWidth() || y >= frame.getHeight()) {
			return null;
		}
		double neighbourDepth = frame.depthMeters(x, y);
		if (Double.isNaN(neighbourDepth)
				|| !DepthWireframe.sameSurface(depth, neighbourDepth)) {
			return null;
		}
		double[] point = new double[3];
		frame.toWorld(x, y, neighbourDepth, point);
		return point;
	}

	private static double[] slope(double[] before, double[] after, double[] middle) {
		if (before != null && after != null) {
			return subtract(after, before);
		}
		if (after != null) {
			return subtract(after, middle);
		}
		if (before != null) {
			return subtract(middle, before);
		}
		return null;
	}

	static double[] subtract(double[] a, double[] b) {
		return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	static double[] scale(double[] a, double factor) {
		return new double[]{a[0] * factor, a[1] * factor, a[2] * factor};
	}

	static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	static double[] cross(double[] a, double[] b) {
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
				a[0] * b[1] - a[1] * b[0]};
	}

	static double length(double[] a) {
		return Math.sqrt(dot(a, a));
	}
}

package com.climbtheworld.app.augmentedreality.placement;

/**
 * The 3D cursor: a ring lying on the rock where the crosshair meets it, and a stick out of it
 * the way the rock faces. It grows with the distance, to keep about the same size on screen.
 */
public final class CursorRing {
	static final int SEGMENTS = 32;
	// About 3.4 degrees across.
	static final double RADIUS_PER_METER = 0.03;
	static final double MIN_RADIUS_METERS = 0.05;
	static final float[] COLOR = {1f, 0.75f, 0.1f, 1f};

	private CursorRing() {
		//hide
	}

	static double radius(double distance) {
		return Math.max(MIN_RADIUS_METERS, distance * RADIUS_PER_METER);
	}

	/**
	 * Adds the cursor at a hit to out.
	 */
	public static void build(CursorHit hit, LineVertices out) {
		double radius = radius(hit.distance);
		double[] normal = hit.normal;
		// Two directions along the rock, square to each other.
		double[] along = CursorHit.cross(normal, new double[]{0, 1, 0});
		if (CursorHit.length(along) < 1e-3) {
			along = CursorHit.cross(normal, new double[]{1, 0, 0});
		}
		along = CursorHit.scale(along, 1 / CursorHit.length(along));
		double[] across = CursorHit.cross(normal, along);

		double[] previous = onRing(hit.point, along, across, radius, 0);
		for (int segment = 1; segment <= SEGMENTS; segment++) {
			double[] next = onRing(hit.point, along, across, radius,
					2 * Math.PI * segment / SEGMENTS);
			out.addLine(previous, next, COLOR);
			previous = next;
		}
		double[] tip = {hit.point[0] + normal[0] * radius, hit.point[1] + normal[1] * radius,
				hit.point[2] + normal[2] * radius};
		out.addLine(hit.point, tip, COLOR);
	}

	private static double[] onRing(double[] centre, double[] along, double[] across,
	                               double radius, double angle) {
		double cos = Math.cos(angle) * radius;
		double sin = Math.sin(angle) * radius;
		return new double[]{centre[0] + along[0] * cos + across[0] * sin,
				centre[1] + along[1] * cos + across[1] * sin,
				centre[2] + along[2] * cos + across[2] * sin};
	}
}

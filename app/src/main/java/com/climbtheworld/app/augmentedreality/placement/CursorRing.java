package com.climbtheworld.app.augmentedreality.placement;

/**
 * The 3D cursor: a ring lying on the rock where the crosshair meets it, and a stick out of it
 * the way the rock faces. It grows with the distance, to keep about the same size on screen, and
 * its colour tells where the point comes from, see CursorHit.Source.
 */
public final class CursorRing {
	static final int SEGMENTS = 32;
	// About 3.4 degrees across.
	static final double RADIUS_PER_METER = 0.03;
	static final double MIN_RADIUS_METERS = 0.05;
	static final float[] DEPTH_COLOR = {1f, 0.75f, 0.1f, 1f};
	static final float[] WALL_PLANE_COLOR = {0.4f, 0.8f, 1f, 1f};
	static final float[] SIGHTING_COLOR = {0.45f, 1f, 0.45f, 1f};
	// The first sighting, while waiting for the second, see sightingLine.
	static final float[] SIGHTING_LINE_COLOR = {0.45f, 1f, 0.45f, 0.7f};
	// How far the first sighting's line reaches.
	static final double SIGHTING_LINE_METERS = WallPlane.MAX_DISTANCE_METERS;
	// Where the line starts, clear of the camera it was sighted from.
	static final double SIGHTING_LINE_START_METERS = 0.5;

	private CursorRing() {
		//hide
	}

	static float[] colorOf(CursorHit.Source source) {
		switch (source) {
			case WALL_PLANE:
				return WALL_PLANE_COLOR;
			case SIGHTING:
				return SIGHTING_COLOR;
			default:
				return DEPTH_COLOR;
		}
	}

	static double radius(double distance) {
		return Math.max(MIN_RADIUS_METERS, distance * RADIUS_PER_METER);
	}

	/**
	 * Adds the cursor at a hit to out.
	 */
	public static void build(CursorHit hit, LineVertices out) {
		float[] color = colorOf(hit.source);
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
			out.addLine(previous, next, color);
			previous = next;
		}
		double[] tip = {hit.point[0] + normal[0] * radius, hit.point[1] + normal[1] * radius,
				hit.point[2] + normal[2] * radius};
		out.addLine(hit.point, tip, color);
	}

	/**
	 * Adds the line of a first sighting to out, for the second one to be aimed across.
	 */
	public static void sightingLine(ViewRay sighting, LineVertices out) {
		out.addLine(sighting.at(SIGHTING_LINE_START_METERS), sighting.at(SIGHTING_LINE_METERS),
				SIGHTING_LINE_COLOR);
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

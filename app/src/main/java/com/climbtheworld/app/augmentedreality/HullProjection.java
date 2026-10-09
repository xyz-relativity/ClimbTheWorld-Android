package com.climbtheworld.app.augmentedreality;

import java.util.Arrays;

/**
 * Projects {@link ClimbingHull.GroundLine}s into the AR view the way
 * {@link TerrainWireframe#project} projects the terrain, leaving the roll to the caller. Unlike
 * the terrain samples, a hull edge can run behind the camera, so the lines are cut to what is in
 * front of it, and to a little beyond the view, so they do not run off towards infinity.
 */
final class HullProjection {
	// Closer to the plane of the camera, in meters, the lines are cut.
	static final double NEAR_METERS = 0.5;
	// How far beyond the view the lines are cut, as a share of it, so the cuts are out of sight.
	private static final double CUT_MARGIN = 1.2;
	private static final int PLANE_COUNT = 5;
	// The planes the lines are cut to, as a * right + b * down + c * forward + d >= 0.
	private final double[][] planes = new double[PLANE_COUNT][4];
	private double sinAzimuth;
	private double cosAzimuth;
	private double sinPitch;
	private double cosPitch;
	private double focalX;
	private double focalY;
	private double centreX;
	private double centreY;
	// In camera space: right, down and forward, in meters.
	private double[] right = new double[64];
	private double[] down = new double[64];
	private double[] forward = new double[64];
	private double[] clippedRight = new double[64];
	private double[] clippedDown = new double[64];
	private double[] clippedForward = new double[64];
	private float[] polygon = new float[128];
	private float[] lines = new float[128];
	private int lineCount;

	/**
	 * @param azimuthDeg compass azimuth of the camera axis
	 * @param pitchDeg   angle of the camera axis above the horizontal
	 * @param focalX     horizontal distance in pixel between the projection centre and the view
	 * @param focalY     vertical distance in pixel between the projection centre and the view
	 * @param centreX    horizontal position of the view centre, half the width of the container
	 * @param centreY    vertical position of the view centre, half the height of the container
	 */
	void setCamera(double azimuthDeg, double pitchDeg, double focalX, double focalY,
	               double centreX, double centreY) {
		sinAzimuth = Math.sin(Math.toRadians(azimuthDeg));
		cosAzimuth = Math.cos(Math.toRadians(azimuthDeg));
		sinPitch = Math.sin(Math.toRadians(pitchDeg));
		cosPitch = Math.cos(Math.toRadians(pitchDeg));
		this.focalX = focalX;
		this.focalY = focalY;
		this.centreX = centreX;
		this.centreY = centreY;

		double slopeX = centreX * CUT_MARGIN / focalX;
		double slopeY = centreY * CUT_MARGIN / focalY;
		setPlane(0, 0, 0, 1, -NEAR_METERS);
		setPlane(1, 1, 0, slopeX, 0);
		setPlane(2, -1, 0, slopeX, 0);
		setPlane(3, 0, 1, slopeY, 0);
		setPlane(4, 0, -1, slopeY, 0);
	}

	private void setPlane(int plane, double right, double down, double forward, double offset) {
		planes[plane][0] = right;
		planes[plane][1] = down;
		planes[plane][2] = forward;
		planes[plane][3] = offset;
	}

	/**
	 * Projects a closed line as a polygon, read with {@link #getPolygon}.
	 *
	 * @param observerEast   position of the observer, in meters east of the origin of the line
	 * @param observerNorth  position of the observer, in meters north of the origin of the line
	 * @param eyeElevation   elevation of the observer's eye above sea level, NaN when unknown
	 * @param groundBelowEye how far the ground is below the eye, where an elevation is unknown
	 * @return the number of corners of the polygon, 0 when none of it is in view
	 */
	int projectPolygon(ClimbingHull.GroundLine line, double observerEast, double observerNorth,
	                   double eyeElevation, double groundBelowEye) {
		int count = toCamera(line, observerEast, observerNorth, eyeElevation, groundBelowEye);
		for (double[] plane : planes) {
			count = clip(plane, count);
			if (count == 0) {
				return 0;
			}
		}

		if (polygon.length < count * 2) {
			polygon = new float[count * 2];
		}
		for (int index = 0; index < count; index++) {
			polygon[index * 2] = screenX(right[index], forward[index]);
			polygon[index * 2 + 1] = screenY(down[index], forward[index]);
		}
		return count;
	}

	/**
	 * @return the corners of the last projected polygon, as x, y pairs
	 */
	float[] getPolygon() {
		return polygon;
	}

	void clearLines() {
		lineCount = 0;
	}

	/**
	 * Projects the segments of a line, adding them to the {@link #getLines} to draw.
	 *
	 * @see #projectPolygon
	 */
	void projectLine(ClimbingHull.GroundLine line, double observerEast, double observerNorth,
	                 double eyeElevation, double groundBelowEye) {
		int count = toCamera(line, observerEast, observerNorth, eyeElevation, groundBelowEye);
		for (int index = 1; index < count; index++) {
			addSegment(index - 1, index);
		}
	}

	/**
	 * @return the segments projected since {@link #clearLines}, as x, y pairs of their ends, for
	 * Canvas.drawLines
	 */
	float[] getLines() {
		return lines;
	}

	/**
	 * @return the number of coordinates in the {@link #getLines}
	 */
	int getLineCount() {
		return lineCount;
	}

	private int toCamera(ClimbingHull.GroundLine line, double observerEast,
	                     double observerNorth, double eyeElevation, double groundBelowEye) {
		ensureCapacity(line.size);
		for (int index = 0; index < line.size; index++) {
			double east = line.east[index] - observerEast;
			double north = line.north[index] - observerNorth;
			// Without both elevations, the ground is level with the observer, as for the POIs.
			double up = Double.isNaN(eyeElevation) || Double.isNaN(line.elevation[index])
					? -groundBelowEye : line.elevation[index] - eyeElevation;
			double along = north * cosAzimuth + east * sinAzimuth;
			right[index] = east * cosAzimuth - north * sinAzimuth;
			forward[index] = along * cosPitch + up * sinPitch;
			down[index] = along * sinPitch - up * cosPitch;
		}
		return line.size;
	}

	/**
	 * Cuts the polygon in camera space to the side of the plane in view
	 * (Sutherland-Hodgman). The corners cut away are replaced by the points where the edges
	 * cross the plane.
	 *
	 * @return the number of corners left
	 */
	private int clip(double[] plane, int count) {
		ensureClippedCapacity(count * 2);
		int clipped = 0;
		for (int index = 0; index < count; index++) {
			int previous = (index + count - 1) % count;
			double previousSide = side(plane, previous);
			double side = side(plane, index);
			if ((previousSide >= 0) != (side >= 0)) {
				double share = previousSide / (previousSide - side);
				clippedRight[clipped] = lerp(right, previous, index, share);
				clippedDown[clipped] = lerp(down, previous, index, share);
				clippedForward[clipped] = lerp(forward, previous, index, share);
				clipped++;
			}
			if (side >= 0) {
				clippedRight[clipped] = right[index];
				clippedDown[clipped] = down[index];
				clippedForward[clipped] = forward[index];
				clipped++;
			}
		}

		double[] swap = right;
		right = clippedRight;
		clippedRight = swap;
		swap = down;
		down = clippedDown;
		clippedDown = swap;
		swap = forward;
		forward = clippedForward;
		clippedForward = swap;
		return clipped;
	}

	/**
	 * Adds the part of the segment between two points in camera space that is in view.
	 */
	private void addSegment(int from, int to) {
		double start = 0;
		double end = 1;
		for (double[] plane : planes) {
			double fromSide = side(plane, from);
			double toSide = side(plane, to);
			if (fromSide < 0 && toSide < 0) {
				return;
			}
			if (fromSide < 0) {
				start = Math.max(start, fromSide / (fromSide - toSide));
			} else if (toSide < 0) {
				end = Math.min(end, fromSide / (fromSide - toSide));
			}
		}
		if (start >= end) {
			return;
		}

		if (lines.length < lineCount + 4) {
			lines = Arrays.copyOf(lines, lines.length * 2);
		}
		lines[lineCount++] = screenX(lerp(right, from, to, start), lerp(forward, from, to, start));
		lines[lineCount++] = screenY(lerp(down, from, to, start), lerp(forward, from, to, start));
		lines[lineCount++] = screenX(lerp(right, from, to, end), lerp(forward, from, to, end));
		lines[lineCount++] = screenY(lerp(down, from, to, end), lerp(forward, from, to, end));
	}

	private double side(double[] plane, int index) {
		return plane[0] * right[index] + plane[1] * down[index] + plane[2] * forward[index]
				+ plane[3];
	}

	private static double lerp(double[] values, int from, int to, double share) {
		return values[from] + (values[to] - values[from]) * share;
	}

	private float screenX(double right, double forward) {
		return (float) (centreX + focalX * right / forward);
	}

	private float screenY(double down, double forward) {
		return (float) (centreY + focalY * down / forward);
	}

	private void ensureCapacity(int size) {
		if (right.length < size) {
			right = new double[size];
			down = new double[size];
			forward = new double[size];
		}
	}

	private void ensureClippedCapacity(int size) {
		if (clippedRight.length < size) {
			clippedRight = new double[size];
			clippedDown = new double[size];
			clippedForward = new double[size];
		}
	}
}

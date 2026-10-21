package com.climbtheworld.app.augmentedreality.placement;

import java.util.Arrays;

/**
 * The ends of coloured lines in the session's world, two per line: x, y, z, then red, green,
 * blue and alpha from 0 to 1.
 */
public class LineVertices {
	public static final int FLOATS_PER_VERTEX = 7;

	private float[] floats = new float[FLOATS_PER_VERTEX * 256];
	private int count;

	public void clear() {
		count = 0;
	}

	public void add(double x, double y, double z, float[] rgba) {
		int start = count * FLOATS_PER_VERTEX;
		if (start + FLOATS_PER_VERTEX > floats.length) {
			floats = Arrays.copyOf(floats, floats.length * 2);
		}
		floats[start] = (float) x;
		floats[start + 1] = (float) y;
		floats[start + 2] = (float) z;
		System.arraycopy(rgba, 0, floats, start + 3, 4);
		count++;
	}

	public void addLine(double[] from, double[] to, float[] rgba) {
		add(from[0], from[1], from[2], rgba);
		add(to[0], to[1], to[2], rgba);
	}

	/**
	 * @return how many vertices there are, twice the lines
	 */
	public int getCount() {
		return count;
	}

	/**
	 * @return the vertices, then unused room
	 */
	public float[] getFloats() {
		return floats;
	}
}

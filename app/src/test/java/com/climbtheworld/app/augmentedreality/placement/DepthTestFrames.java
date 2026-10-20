package com.climbtheworld.app.augmentedreality.placement;

import java.nio.ShortBuffer;

/**
 * Depth frames of made-up scenes, seen by a camera looking down -Z.
 */
final class DepthTestFrames {
	static final int WIDTH = 160;
	static final int HEIGHT = 120;
	// The camera texture is ten times the depth image.
	static final int[] TEXTURE_SIZE = {1600, 1200};
	static final float[] FOCAL_LENGTH = {1200, 1200};
	static final float[] PRINCIPAL_POINT = {800, 600};
	static final double FOCAL = 120;
	static final double CENTRE_X = 80;
	static final double CENTRE_Y = 60;

	private DepthTestFrames() {
	}

	static float[] cameraAt(float x, float y, float z) {
		return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1};
	}

	static DepthFrame frame(short[] millimeters, float[] cameraToWorld) {
		return new DepthFrame(ShortBuffer.wrap(millimeters), WIDTH, HEIGHT, WIDTH, FOCAL_LENGTH,
				PRINCIPAL_POINT, TEXTURE_SIZE, cameraToWorld);
	}

	static short[] flat(double meters) {
		short[] depth = new short[WIDTH * HEIGHT];
		java.util.Arrays.fill(depth, (short) Math.round(meters * 1000));
		return depth;
	}

	/**
	 * The depth of a plane through a point, facing along normal, in the camera's coordinates.
	 */
	static short[] plane(double[] point, double[] normal) {
		short[] depth = new short[WIDTH * HEIGHT];
		double reach = CursorHit.dot(normal, point);
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH; x++) {
				double[] ray = {(x - CENTRE_X) / FOCAL, (CENTRE_Y - y) / FOCAL, -1};
				double meters = reach / CursorHit.dot(normal, ray);
				depth[y * WIDTH + x] = (short) Math.round(meters * 1000);
			}
		}
		return depth;
	}
}

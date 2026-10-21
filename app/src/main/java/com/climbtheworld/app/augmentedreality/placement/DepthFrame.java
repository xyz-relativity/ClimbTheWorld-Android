package com.climbtheworld.app.augmentedreality.placement;

import java.nio.ShortBuffer;

/**
 * A depth image from ARCore, with the camera that saw it, to turn its pixels into points of the
 * session's world.
 * <p>
 * Each pixel holds the depth in millimeters along the camera's axis, 0 where it is unknown. The
 * image shows what the camera texture shows, at a lower resolution, so the texture's intrinsics,
 * scaled down, tell where each pixel looks.
 */
public class DepthFrame {
	// ARCore measures depth reliably up to about 25 m; past this it is too unsure to use.
	public static final double MAX_DEPTH_METERS = 30;

	private final ShortBuffer depth;
	private final int width;
	private final int height;
	private final int rowStride;
	private final double focalX;
	private final double focalY;
	private final double principalX;
	private final double principalY;
	private final float[] cameraToWorld;

	/**
	 * @param depth          the depth in millimeters, row by row
	 * @param rowStride      how many values a row takes in depth
	 * @param focalLength    the camera texture's focal lengths, in its pixels
	 * @param principalPoint the camera texture's principal point, in its pixels
	 * @param textureSize    the camera texture's width and height
	 * @param cameraToWorld  the camera's pose as a column-major matrix: +X right and +Y up in the
	 *                       image, looking down -Z
	 */
	public DepthFrame(ShortBuffer depth, int width, int height, int rowStride, float[] focalLength,
	                  float[] principalPoint, int[] textureSize, float[] cameraToWorld) {
		this.depth = depth;
		this.width = width;
		this.height = height;
		this.rowStride = rowStride;
		double scaleX = (double) width / textureSize[0];
		double scaleY = (double) height / textureSize[1];
		this.focalX = focalLength[0] * scaleX;
		this.focalY = focalLength[1] * scaleY;
		this.principalX = principalPoint[0] * scaleX;
		this.principalY = principalPoint[1] * scaleY;
		this.cameraToWorld = cameraToWorld;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	/**
	 * @return the depth at a pixel, NaN where it is unknown or too far to use
	 */
	public double depthMeters(int x, int y) {
		int millimeters = depth.get(y * rowStride + x) & 0xffff;
		double meters = millimeters / 1000.0;
		return millimeters == 0 || meters > MAX_DEPTH_METERS ? Double.NaN : meters;
	}

	/**
	 * Puts into out the point of the world seen at a pixel, at a depth.
	 */
	public void toWorld(double x, double y, double depthMeters, double[] out) {
		double cameraX = depthMeters * (x - principalX) / focalX;
		double cameraY = depthMeters * (principalY - y) / focalY;
		double cameraZ = -depthMeters;
		float[] m = cameraToWorld;
		out[0] = m[0] * cameraX + m[4] * cameraY + m[8] * cameraZ + m[12];
		out[1] = m[1] * cameraX + m[5] * cameraY + m[9] * cameraZ + m[13];
		out[2] = m[2] * cameraX + m[6] * cameraY + m[10] * cameraZ + m[14];
	}

	/**
	 * @return where the camera is in the world
	 */
	public double[] cameraPosition() {
		return new double[]{cameraToWorld[12], cameraToWorld[13], cameraToWorld[14]};
	}
}

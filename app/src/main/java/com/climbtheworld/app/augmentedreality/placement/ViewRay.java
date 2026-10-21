package com.climbtheworld.app.augmentedreality.placement;

/**
 * A ray of the session's world, from where the camera is through a point of what it sees.
 */
public class ViewRay {
	public final double[] origin;
	// A unit vector.
	public final double[] direction;

	public ViewRay(double[] origin, double[] direction) {
		this.origin = origin.clone();
		this.direction = CursorHit.scale(direction, 1 / CursorHit.length(direction));
	}

	/**
	 * The ray through a pixel of the camera texture.
	 *
	 * @param focalLength    the camera texture's focal lengths, in its pixels
	 * @param principalPoint the camera texture's principal point, in its pixels
	 * @param cameraToWorld  the camera's pose as a column-major matrix: +X right and +Y up in the
	 *                       image, looking down -Z
	 */
	public static ViewRay throughTexture(double x, double y, float[] focalLength,
	                                     float[] principalPoint, float[] cameraToWorld) {
		double cameraX = (x - principalPoint[0]) / focalLength[0];
		double cameraY = (principalPoint[1] - y) / focalLength[1];
		double cameraZ = -1;
		float[] m = cameraToWorld;
		return new ViewRay(new double[]{m[12], m[13], m[14]},
				new double[]{m[0] * cameraX + m[4] * cameraY + m[8] * cameraZ,
						m[1] * cameraX + m[5] * cameraY + m[9] * cameraZ,
						m[2] * cameraX + m[6] * cameraY + m[10] * cameraZ});
	}

	public double[] at(double distance) {
		return new double[]{origin[0] + direction[0] * distance,
				origin[1] + direction[1] * distance, origin[2] + direction[2] * distance};
	}
}

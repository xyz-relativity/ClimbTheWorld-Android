package com.climbtheworld.app.augmentedreality;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;

/**
 * Created by xyz on 12/26/17.
 */

public class AugmentedRealityUtils {

	private AugmentedRealityUtils() {
		//hide constructor
	}

	/**
	 * Calculate the location of the point
	 *
	 * @param yawDegAngle       azimuth of the point, relative to the camera azimuth
	 * @param elevationDegAngle angle of the point above the horizontal
	 * @param pitch             angle of the camera axis above the horizontal
	 * @param pRoll             roll angle
	 * @param screenRot         current screen orientation
	 * @param objSize           size of the object to be positioned
	 * @param fov               camera field of view in degree.
	 * @param viewSize          size in pixel of the camera view, which the field of view spans
	 * @param containerSize     size in pixel of the container the object is placed in, centred on
	 *                          the camera view
	 * @return returns the position of the object.
	 */
	public static Vector4d getXYPosition(double yawDegAngle, double elevationDegAngle, double pitch, double pRoll, double screenRot, Vector2d objSize, Vector2d fov, Vector2d viewSize, Vector2d containerSize) {
		double roll = (pRoll + screenRot);

		// Pinhole projection through the camera without its roll, which the caller applies by
		// rotating the container. The view centre is the container centre.
		double yaw = Math.toRadians(yawDegAngle);
		double elevation = Math.toRadians(elevationDegAngle);
		double cameraPitch = Math.toRadians(pitch);
		double right = Math.cos(elevation) * Math.sin(yaw);
		double down = Math.cos(elevation) * Math.cos(yaw) * Math.sin(cameraPitch)
				- Math.sin(elevation) * Math.cos(cameraPitch);
		double forward = forwardComponent(yaw, elevation, cameraPitch);

		Vector2d point = new Vector2d(containerSize.x / 2 + focalLength(fov.x, viewSize.x) * right / forward,
				containerSize.y / 2 + focalLength(fov.y, viewSize.y) * down / forward);

		// Roll pivots horizontally around the screen centre, but vertically around the point itself.
		Vector2d origin = new Vector2d(containerSize.x / 2, point.y);

		// Rotate the coordinates to match the roll.
		Vector4d result = rotatePoint(point, origin, roll);

		result.x = result.x - objSize.x / 2;
		result.y = result.y - objSize.y / 2;

		return result;
	}

	/**
	 * Angle between the camera axis and the direction of a point. Points further from the axis
	 * than the corners of the view cannot be in view, whatever the roll.
	 *
	 * @param yawDegAngle       azimuth of the point, relative to the camera azimuth
	 * @param elevationDegAngle angle of the point above the horizontal
	 * @param pitch             angle of the camera axis above the horizontal
	 * @return the angle in degree, between 0 and 180
	 */
	public static double angleFromCameraAxis(double yawDegAngle, double elevationDegAngle, double pitch) {
		double forward = forwardComponent(Math.toRadians(yawDegAngle),
				Math.toRadians(elevationDegAngle), Math.toRadians(pitch));
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, forward))));
	}

	/**
	 * Component along the camera axis of the unit vector pointing at a point, all angles in radian.
	 */
	private static double forwardComponent(double yaw, double elevation, double cameraPitch) {
		return Math.cos(elevation) * Math.cos(yaw) * Math.cos(cameraPitch)
				+ Math.sin(elevation) * Math.sin(cameraPitch);
	}

	/**
	 * Distance in pixel between the projection centre and a view of the given size spanning the
	 * given angle of view.
	 */
	static double focalLength(double degAngleOfView, double viewSize) {
		return (viewSize / 2) / Math.tan(Math.toRadians(degAngleOfView / 2));
	}

	/**
	 * Rotates one point around an random origin
	 *
	 * @param p      point to rotate
	 * @param origin reference point for rotation
	 * @param roll   angle of rotation
	 * @return returns the new 2d coordinates
	 */
	public static Vector4d rotatePoint(Vector2d p, Vector2d origin, double roll) {
		Vector4d result = new Vector4d();
		result.w = roll;

		double pX = p.x - origin.x;
		double pY = p.y - origin.y;

		double sinRoll = Math.sin(Math.toRadians(roll));
		double cosRoll = Math.cos(Math.toRadians(roll));

		result.x = (pX * cosRoll - pY * sinRoll) + origin.x;
		result.y = (pY * cosRoll + pX * sinRoll) + origin.y;

		return result;
	}

	/**
	 * Map numbers form one scale to another.
	 *
	 * @param orgMin Minimum of the initial scale
	 * @param orgMax Maximum of the initial scale
	 * @param newMin Minimum of the new scale
	 * @param newMax Maximum of the new scale
	 * @param pos    Position on the original scale
	 * @return Position on the new scale
	 */
	public static double remapScale(double orgMin, double orgMax, double newMin, double newMax, double pos) {
		return (pos - orgMin) * (newMax - newMin) / (orgMax - orgMin) + newMin;
	}


	/**
	 * Map numbers form one scale to another.
	 *
	 * @param orgMin Minimum of the initial scale
	 * @param orgMax Maximum of the initial scale
	 * @param newMin Minimum of the new scale
	 * @param newMax Maximum of the new scale
	 * @param pos    Position on the original scale
	 * @return Position on the new scale
	 */
	public static double remapScaleToLog(double orgMin, double orgMax, double newMin, double newMax, double pos) {
		if (pos < 1) {
			pos = 1;
		}

		if (orgMin < 1) {
			orgMin = 1;
		}

		if (newMax < 1) {
			newMax = 1;
		}

		double result = (Math.log(pos) - Math.log(orgMin)) / (Math.log(orgMax) - Math.log(orgMin));

		result = remapScale(0, 1, newMin, newMax, result);

		return result;
	}

	public static String getStringBearings(AppCompatActivity parent, double orientation) {
		int azimuthID = (int) Math.round((((orientation + 360) % 360) / 22.5)) % 16;
		return parent.getResources().getStringArray(R.array.cardinal_names)[azimuthID];
	}

}

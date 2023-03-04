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
	 * @param yawDegAngle yaw angle
	 * @param pitch       pitch angle
	 * @param pRoll       roll angle
	 * @param screenRot   current screen orientation
	 * @param objSize     size of the object to be positioned
	 * @param fov         camera field of view in degree.
	 * @param displaySize size of the display in pixel
	 * @return returns the position of the object.
	 */
	public static Vector4d getXYPosition(double yawDegAngle, double pitch, double pRoll, double screenRot, Vector2d objSize, Vector2d fov, Vector2d displaySize) {
		double roll = (pRoll + screenRot);

		// rescale the yaw and pitch angels to screen coordinates.
		Vector2d point = new Vector2d(remapScale(-fov.x / 2, fov.x / 2, 0, displaySize.x, yawDegAngle),
				remapScale(-fov.y / 2, fov.y / 2, 0, displaySize.y, pitch));

		Vector2d origin = new Vector2d(displaySize.x / 2, displaySize.y / 2);
		origin.y = origin.y + (point.y - origin.y);

		// Rotate the coordinates to match the roll.
		Vector4d result = rotatePoint(point, origin, roll);

		result.x = result.x - objSize.x / 2;
		result.y = result.y - objSize.y / 2;

		return result;
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

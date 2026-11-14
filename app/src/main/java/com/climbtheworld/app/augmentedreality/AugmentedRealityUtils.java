package com.climbtheworld.app.augmentedreality;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;

/**
 * Created by xyz on 12/26/17.
 */

public class AugmentedRealityUtils {
	// Past this angle from the camera axis, near the camera's plane or behind it, the pinhole
	// projection puts a point anywhere, so getXYPosition lays it out along its direction from the
	// view centre instead, as far out as a point at this angle: off the view, the way to turn to
	// it. Nothing in view is that far from the axis.
	public static final double MAX_PROJECTED_DEGREES = 80;
	// How far forward a point at that angle is, and how far out it is laid: the same for every
	// point, so worked out once rather than for each one placed.
	private static final double MIN_PROJECTED_FORWARD =
			Math.cos(Math.toRadians(MAX_PROJECTED_DEGREES));
	private static final double MAX_PROJECTED_STRETCH =
			Math.tan(Math.toRadians(MAX_PROJECTED_DEGREES));

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
	 * @return returns the position of the object, off the view in its direction when it is too far
	 * from the camera axis to project, see MAX_PROJECTED_DEGREES
	 */
	public static Vector4d getXYPosition(double yawDegAngle, double elevationDegAngle, double pitch, double pRoll, double screenRot, Vector2d objSize, Vector2d fov, Vector2d viewSize, Vector2d containerSize) {
		Vector4d result = new Vector4d();
		getXYPosition(yawDegAngle, elevationDegAngle, pitch, pRoll, screenRot, objSize.x,
				objSize.y, fov, viewSize, containerSize, result);
		return result;
	}

	/**
	 * getXYPosition into a position the caller keeps, for what places many objects on every
	 * frame: it makes nothing to collect.
	 *
	 * @param objWidth  width of the object to be positioned
	 * @param objHeight height of the object to be positioned
	 * @param result    takes the position of the object
	 */
	public static void getXYPosition(double yawDegAngle, double elevationDegAngle, double pitch, double pRoll, double screenRot, double objWidth, double objHeight, Vector2d fov, Vector2d viewSize, Vector2d containerSize, Vector4d result) {
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
		// How far out along (right, down) the point is: tan of its angle from the axis, held at
		// MAX_PROJECTED_DEGREES.
		double stretch;
		double lateral = Math.hypot(right, down);
		if (forward > MIN_PROJECTED_FORWARD) {
			stretch = 1 / forward;
		} else if (lateral > 1e-9) {
			stretch = MAX_PROJECTED_STRETCH / lateral;
		} else {
			// Right behind the camera: to the right, as well as any other way.
			right = 1;
			stretch = MAX_PROJECTED_STRETCH;
		}

		double pointX = containerSize.x / 2 + focalLength(fov.x, viewSize.x) * right * stretch;
		double pointY = containerSize.y / 2 + focalLength(fov.y, viewSize.y) * down * stretch;

		// Rotate the coordinates to match the roll. Roll pivots horizontally around the screen
		// centre, but vertically around the point itself.
		rotatePoint(pointX, pointY, containerSize.x / 2, pointY, roll, result);

		result.x = result.x - objWidth / 2;
		result.y = result.y - objHeight / 2;
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
		rotatePoint(p.x, p.y, origin.x, origin.y, roll, result);
		return result;
	}

	/**
	 * rotatePoint into coordinates the caller keeps, see getXYPosition.
	 */
	private static void rotatePoint(double x, double y, double originX, double originY,
	                                double roll, Vector4d result) {
		// The coordinates may have held another point.
		result.z = 0;
		result.w = roll;

		double pX = x - originX;
		double pY = y - originY;

		double sinRoll = Math.sin(Math.toRadians(roll));
		double cosRoll = Math.cos(Math.toRadians(roll));

		result.x = (pX * cosRoll - pY * sinRoll) + originX;
		result.y = (pY * cosRoll + pX * sinRoll) + originY;
	}

	/**
	 * Size on screen of an object at the given distance. Through a camera, an object shrinks in
	 * inverse proportion to its distance, which would make far ones vanish. This softens the
	 * perspective into the power law going through both given sizes, so that each doubling of
	 * the distance still shrinks the object by the same factor, only less. Out of the distance
	 * range, the size is held at the closest end.
	 *
	 * @param distance     distance of the object
	 * @param nearDistance distance up to which the object has its near size
	 * @param nearSize     size of the object at the near distance
	 * @param farDistance  distance from which the object has its far size
	 * @param farSize      size of the object at the far distance
	 * @return the size, in the unit of the given sizes
	 */
	public static double perspectiveSize(double distance, double nearDistance, double nearSize,
	                                     double farDistance, double farSize) {
		return perspectiveSizeAlong(distance, nearDistance, nearSize, farDistance,
				perspectiveExponent(nearDistance, nearSize, farDistance, farSize));
	}

	/**
	 * The exponent of the power law of perspectiveSize. It only depends on the two ends, so
	 * what sizes many objects between the same ends on every frame works it out once, for
	 * perspectiveSizeAlong.
	 */
	public static double perspectiveExponent(double nearDistance, double nearSize,
	                                         double farDistance, double farSize) {
		return Math.log(nearSize / farSize) / Math.log(farDistance / nearDistance);
	}

	/**
	 * perspectiveSize along a power law whose exponent is at hand, see perspectiveExponent.
	 */
	public static double perspectiveSizeAlong(double distance, double nearDistance,
	                                          double nearSize, double farDistance,
	                                          double exponent) {
		double clamped = Math.max(nearDistance, Math.min(farDistance, distance));
		return nearSize * Math.pow(nearDistance / clamped, exponent);
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

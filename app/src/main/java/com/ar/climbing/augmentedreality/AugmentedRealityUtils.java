package com.ar.climbing.augmentedreality;

import android.content.Context;
import android.util.TypedValue;

import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.utils.Quaternion;
import com.ar.climbing.utils.Vector2d;

/**
 * Created by xyz on 12/26/17.
 */

public class AugmentedRealityUtils {
    public static final double EARTH_RADIUS_M = 6378137;
    public static final double EARTH_RADIUS_KM = EARTH_RADIUS_M / 1000;

    private AugmentedRealityUtils() {
        //hide constructor
    }

    /**
     * Calculate the location of the point
     * @param yawDegAngle yaw angle
     * @param pitch pitch angle
     * @param pRoll roll angle
     * @param screenRot current screen orientation
     * @param objSize size of the object to be positioned
     * @param fov camera field of view in degree.
     * @param displaySize size of the display in pixel
     * @return returns the position of the object.
     */
    public static Quaternion getXYPosition(double yawDegAngle, double pitch, double pRoll, double screenRot, Vector2d objSize, Vector2d fov, Vector2d displaySize) {
        double roll = (pRoll + screenRot);

        // rescale the yaw and pitch angels to screen coordinates.
        Vector2d point = new Vector2d(remapScale(-fov.x/2, fov.x/2, 0, displaySize.x, yawDegAngle),
                remapScale(-fov.y/2, fov.y/2, 0, displaySize.y, pitch));

        Vector2d origin = new Vector2d(displaySize.x/2, displaySize.y/2);
        origin.y = origin.y + (point.y - origin.y);

        // Rotate the coordinates to match the roll.
        Quaternion result = rotatePoint(point, origin, roll);

        result.x = result.x - objSize.x/2;
        result.y = result.y - objSize.y/2;

        return result;
    }

    /**
     * Rotates one point around an random origin
     * @param p point to rotate
     * @param origin reference point for rotation
     * @param roll angle of rotation
     * @return returns the new 2d coordinates
     */
    public static Quaternion rotatePoint(Vector2d p, Vector2d origin, double roll) {
        Quaternion result = new Quaternion();
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
     * @param orgMin Minimum of the initial scale
     * @param orgMax Maximum of the initial scale
     * @param newMin Minimum of the new scale
     * @param newMax Maximum of the new scale
     * @param pos Position on the original scale
     * @return Position on the new scale
     */
    public static double remapScale(double orgMin, double orgMax, double newMin, double newMax, double pos) {
        double result = newMax;

        double oldRange = (orgMax - orgMin);
        if (oldRange != 0)
        {
            result = ((pos - orgMin) * (newMax - newMin) / oldRange) + newMin;
        }

        return result;
    }


    /**
     * Map numbers form one scale to another.
     * @param orgMin Minimum of the initial scale
     * @param orgMax Maximum of the initial scale
     * @param newMin Minimum of the new scale
     * @param newMax Maximum of the new scale
     * @param pos Position on the original scale
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

        double result = (Math.log(pos) - Math.log(orgMin))/(Math.log(orgMax) - Math.log(orgMin));

        result = remapScale(0, 1, newMin, newMax, result);

        return result;
    }

    /**
     * Computes the azimuth between 2 points
     * @param obs Observer point
     * @param poi Destination point
     * @return  Returns the azimuth in degree
     */
    public static double calculateTheoreticalAzimuth(GeoNode obs, GeoNode poi) {
        return Math.toDegrees(Math.atan2(poi.decimalLongitude - obs.decimalLongitude,
                poi.decimalLatitude - obs.decimalLatitude));
    }

    /**
     * Calculate distance between 2 coordinates using the haversine algorithm.
     * @param obs Observer location
     * @param poi Point of interest location
     * @return Shortest as the crow flies distance in meters.
     */
    public static double calculateDistance(GeoNode obs, GeoNode poi) {
        double dLat = Math.toRadians(poi.decimalLatitude-obs.decimalLatitude);
        double dLon = Math.toRadians(poi.decimalLongitude-obs.decimalLongitude);

        double lat1 = Math.toRadians(obs.decimalLatitude);
        double lat2 = Math.toRadians(poi.decimalLatitude);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return EARTH_RADIUS_M * c;
    }

    /**
     * Calculates shortest difference between 2 angels
     * @param a origin angle
     * @param b dest angle
     * @return angle difference
     */
    public static double diffAngle(double a, double b) {
//        double x = Math.toRadians(a);
//        double y = Math.toRadians(b);
//        return Math.toDegrees(Math.atan2(Math.sin(x-y), Math.cos(x-y)));

        //this way should be more efficient
        double d = Math.abs(a - b) % 360;
        double r = d > 180 ? 360 - d : d;

        int sign = (a - b >= 0 && a - b <= 180) || (a - b <=-180 && a- b>= -360) ? 1 : -1;
        return (r * sign);
    }

    public static float sizeToDPI(Context context, float size) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                size, context.getResources().getDisplayMetrics());
    }
}

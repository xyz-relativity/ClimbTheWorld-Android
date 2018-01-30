package com.ar.openClimbAR.utils;

import com.ar.openClimbAR.tools.PointOfInterest;

/**
 * Created by xyz on 12/26/17.
 */

public class AugmentedRealityUtils {
    public static final double EARTH_RADIUS_M = 6378137f;
    public static final double EARTH_RADIUS_KM = EARTH_RADIUS_M / 1000f;

    private AugmentedRealityUtils() {
        //hide constructor
    }

    public static double[] getXYPosition(double yawDegAngle, double pitch, double pRoll, double screenRot, Vector2d objSize, Vector2d fov, Vector2d displaySize) {
        double roll = (pRoll + screenRot);

        double pX = remapScale(-fov.x/2f, fov.x/2f, 0, displaySize.x, yawDegAngle);
        double pY = remapScale(-fov.y/2f, fov.y/2f, 0, displaySize.y, pitch);

        double originX = displaySize.x/2;
        double originY = (displaySize.y/2) + (pY - (displaySize.y/2));

        double[] result = rotatePoint(new Vector2d(pX, pY), new Vector2d(originX, originY), roll);

        result[0] = result[0] - objSize.x/2;
        result[1] = result[1] - objSize.y/2;

        return result;
    }

    public static double[] rotatePoint(Vector2d p, Vector2d origin, double roll) {
        double[] result = new double[3];
        result[2] = roll;

        double pX = p.x - origin.x;
        double pY = p.y - origin.y;

        double sinRoll = Math.sin(Math.toRadians(roll));
        double cosRoll = Math.cos(Math.toRadians(roll));

        result[0] = (pX * cosRoll - pY * sinRoll) + origin.x;
        result[1] = (pY * cosRoll + pX * sinRoll) + origin.y;

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

    public static double calculateTheoreticalAzimuth(PointOfInterest obs, PointOfInterest poi) {
        return Math.toDegrees(Math.atan2(poi.decimalLongitude - obs.decimalLongitude,
                poi.decimalLatitude - obs.decimalLatitude));
    }

    /**
     * Calculate distance between 2 coordinates using the haversine algorithm.
     * @param obs Observer location
     * @param poi Point of interest location
     * @return Shortest as the crow flies distance in meters.
     */
    public static double calculateDistance(PointOfInterest obs, PointOfInterest poi) {
        double dLat = Math.toRadians(poi.decimalLatitude-obs.decimalLatitude);
        double dLon = Math.toRadians(poi.decimalLongitude-obs.decimalLongitude);

        double lat1 = Math.toRadians(obs.decimalLatitude);
        double lat2 = Math.toRadians(poi.decimalLatitude);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return EARTH_RADIUS_M * c;
    }

    public static double diffAngle(double a, double b) {
//        double x = Math.toRadians(a);
//        double y = Math.toRadians(b);
//        return (float)Math.toDegrees(Math.atan2(Math.sin(x-y), Math.cos(x-y)));

        //this way should be more efficient
        double d = Math.abs(a - b) % 360;
        double r = d > 180 ? 360 - d : d;

        int sign = (a - b >= 0 && a - b <= 180) || (a - b <=-180 && a- b>= -360) ? 1 : -1;
        return (r * sign);
    }
}

package com.ar.openClimbAR.utils;

import com.ar.openClimbAR.tools.PointOfInterest;

/**
 * Created by xyz on 12/26/17.
 */

public class ArUtils {
    public static final double EARTH_RADIUS_M = 6378137f;
    public static final double EARTH_RADIUS_KM = EARTH_RADIUS_M / 1000f;

    private ArUtils () {
        //hide constructor
    }

    public static double[] getXYPosition(double yawDegAngle, double pitch, double pRoll, double screenRot, Vector2d objSize, Vector2d fov, Vector2d displaySize) {
        double roll = (pRoll + screenRot);
        double fovH = fov.x;
        double fovV = fov.y;

        double pX = remapScale(-fovH/2f, fovH/2f, 0, displaySize.x, yawDegAngle) - (objSize.x/2);
        double pY = remapScale(-fovV/2f, fovV/2f, 0, displaySize.y, pitch) - (objSize.y/2);
        double[] result = rotatePoint(new Vector2d(pX, pY), displaySize, roll);

        return result;
    }

    public static double[] rotatePoint(Vector2d p, Vector2d origin, double roll) {
        double cX = origin.x/2f;
        double cY = origin.y/2f;

        double pX = p.x - cX;
        double pY = p.y - cY;

        double sinRoll = Math.sin(Math.toRadians(roll));
        double cosRoll = Math.cos(Math.toRadians(roll));

        pX = (pX* cosRoll - pY * sinRoll) + cX;
        pY = (pX* sinRoll + pY * cosRoll) + cY;


        double[] result = new double[3];
        result[0] = pX;
        result[1] = pY;
        result[2] = roll;

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

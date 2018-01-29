package com.ar.openClimbAR.utils;

import android.util.Size;
import android.util.SizeF;

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

    public static float[] getXYPosition(float yawDegAngle, float pitch, float pRoll, float screenRot, Size objSize, SizeF fov, SizeF displaySize) {
        float screenWidth = displaySize.getWidth();
        float screenHeight = displaySize.getHeight();
        float roll = (pRoll + screenRot);

        float absoluteY = (((pitch * screenHeight) / (fov.getHeight())) + (screenHeight/2)) - (objSize.getHeight()/2);
        float radiusX = (((yawDegAngle) * screenWidth) / (fov.getWidth())) - (objSize.getWidth()/2);
        float radiusY = (((pitch) * screenHeight) / (fov.getHeight())) - (objSize.getHeight()/2);

        float[] result = new float[3];

        result[0] = (float)(radiusX * Math.cos(Math.toRadians(roll))) + (screenWidth/2); //apply camera rotation to object Y position. Allow the object to track camera rotation
        result[1] = (float)(radiusY * Math.sin(Math.toRadians(roll))) + absoluteY; //apply camera rotation to object Y position. Allow the object to track camera rotation
        result[2] = roll;

        return result;
    }

    public static float remapScale(float orgMin, float orgMax, float newMin, float newMax, float pos) {
        float oldRange = (orgMax - orgMin);
        float result;
        if (oldRange == 0)
            result = newMax;
        else
        {
            result = (((pos - orgMin) * (newMin - newMax)) / oldRange) + newMax;
        }

        return result;
    }

    public static float calculateTheoreticalAzimuth(PointOfInterest obs, PointOfInterest poi) {
        return (float)Math.toDegrees(Math.atan2(poi.decimalLongitude - obs.decimalLongitude,
                poi.decimalLatitude - obs.decimalLatitude));
    }

    /**
     * Calculate distance between 2 coordinates using the haversine algorithm.
     * @param obs Observer location
     * @param poi Point of interest location
     * @return Shortest as the crow flies distance in meters.
     */
    public static float calculateDistance(PointOfInterest obs, PointOfInterest poi) {
        double dLat = Math.toRadians(poi.decimalLatitude-obs.decimalLatitude);
        double dLon = Math.toRadians(poi.decimalLongitude-obs.decimalLongitude);

        double lat1 = Math.toRadians(obs.decimalLatitude);
        double lat2 = Math.toRadians(poi.decimalLatitude);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return (float)(EARTH_RADIUS_M * c);
    }

    public static float diffAngle(float a, float b) {
//        double x = Math.toRadians(a);
//        double y = Math.toRadians(b);
//        return (float)Math.toDegrees(Math.atan2(Math.sin(x-y), Math.cos(x-y)));

        //this way should be more efficient
        float d = Math.abs(a - b) % 360;
        float r = d > 180 ? 360 - d : d;

        int sign = (a - b >= 0 && a - b <= 180) || (a - b <=-180 && a- b>= -360) ? 1 : -1;
        return (r * sign);
    }
}

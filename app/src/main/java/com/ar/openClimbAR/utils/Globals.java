package com.ar.openClimbAR.utils;

import android.util.Size;

import com.ar.openClimbAR.tools.OrientationPointOfInterest;
import com.ar.openClimbAR.tools.PointOfInterest;

import org.osmdroid.util.GeoPoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by xyz on 1/19/18.
 */

public class Globals {
    private Globals() {
        //hide constructor
    }

    public static OrientationPointOfInterest observer = new OrientationPointOfInterest(
            45.35384f, 24.63507f,
            100f);
    public static Map<Long, PointOfInterest> allPOIs = new ConcurrentHashMap<>(); //database
    public static Size displaySize = new Size(0,0);

    public static GeoPoint poiToGeoPoint(PointOfInterest poi) {
        return new GeoPoint(poi.decimalLatitude, poi.decimalLongitude, poi.elevationMeters);
    }
}

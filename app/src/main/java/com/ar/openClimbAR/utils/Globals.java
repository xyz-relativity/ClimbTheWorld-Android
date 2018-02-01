package com.ar.openClimbAR.utils;


import android.util.SparseArray;
import android.view.Surface;

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

    private static final SparseArray ORIENTATIONS = new SparseArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, -90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 90);
    }

    public static int getScreenRotationAngle(int rotation) {
        return (int)ORIENTATIONS.get(rotation);
    }

    public static OrientationPointOfInterest observer = new OrientationPointOfInterest(
            45.35384f, 24.63507f,
            100f);
    public static Map<Long, PointOfInterest> allPOIs = new ConcurrentHashMap<>(); //database
    public static Vector2d rotateCameraPreviewSize = new Vector2d(0,0);
    public static Configs globalConfigs;

    public static GeoPoint poiToGeoPoint(PointOfInterest poi) {
        return new GeoPoint(poi.decimalLatitude, poi.decimalLongitude, poi.elevationMeters);
    }
}

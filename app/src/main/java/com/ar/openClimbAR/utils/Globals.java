package com.ar.openClimbAR.utils;

import android.util.SizeF;
import android.util.SparseArray;
import android.view.Surface;

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

    private static final SparseArray ORIENTATIONS = new SparseArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0f);
        ORIENTATIONS.append(Surface.ROTATION_90, -90f);
        ORIENTATIONS.append(Surface.ROTATION_180, 180f);
        ORIENTATIONS.append(Surface.ROTATION_270, 90f);
    }


    public static float getScreenRotationAngle(int rotation) {
        return (float)ORIENTATIONS.get(rotation);
    }

    public static OrientationPointOfInterest observer = new OrientationPointOfInterest(
            45.35384f, 24.63507f,
            100f);
    public static Map<Long, PointOfInterest> allPOIs = new ConcurrentHashMap<>(); //database
    public static SizeF displaySize = new SizeF(0f,0f);

    public static GeoPoint poiToGeoPoint(PointOfInterest poi) {
        return new GeoPoint(poi.decimalLatitude, poi.decimalLongitude, poi.elevationMeters);
    }
}

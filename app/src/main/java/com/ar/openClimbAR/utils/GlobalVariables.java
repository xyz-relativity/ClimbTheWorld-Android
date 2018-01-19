package com.ar.openClimbAR.utils;

import com.ar.openClimbAR.tools.OrientationPointOfInterest;
import com.ar.openClimbAR.tools.PointOfInterest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by xyz on 1/19/18.
 */

public class GlobalVariables {
    public static OrientationPointOfInterest observer = new OrientationPointOfInterest(
            45.35384f, 24.63507f,
            100f);
    public static Map<Long, PointOfInterest> allPOIs = new ConcurrentHashMap<>(); //database

    private GlobalVariables() {
        //hide constructor
    }
}

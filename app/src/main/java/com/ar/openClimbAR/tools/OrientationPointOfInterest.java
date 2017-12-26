package com.ar.openClimbAR.tools;

/**
 * Created by xyz on 12/26/17.
 */

public class OrientationPointOfInterest extends PointOfInterest {
    public float degAzimuth = 0;
    public float degPitch = 0;
    public float degRoll = 0;
    public float horizontalFieldOfViewDeg = 0;
    public float screenRotation = 0;

    public OrientationPointOfInterest(POIType pType, float pDecimalLongitude, float pDecimalLatitude, float pMetersAltitude)
    {
        super(pType, pDecimalLongitude, pDecimalLatitude, pMetersAltitude);
    }
}

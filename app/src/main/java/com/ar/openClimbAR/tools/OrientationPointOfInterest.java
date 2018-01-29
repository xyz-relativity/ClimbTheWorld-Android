package com.ar.openClimbAR.tools;


import com.ar.openClimbAR.utils.Vector2f;

/**
 * Created by xyz on 12/26/17.
 */

public class OrientationPointOfInterest extends PointOfInterest {
    public float degAzimuth = 0;
    public float degPitch = 0;
    public float degRoll = 0;
    public Vector2f fieldOfViewDeg = new Vector2f(60f, 40f);
    public float screenRotation = 0;

    public OrientationPointOfInterest(float pDecimalLatitude, float pDecimalLongitude, float pMetersAltitude)
    {
        super(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
    }
}

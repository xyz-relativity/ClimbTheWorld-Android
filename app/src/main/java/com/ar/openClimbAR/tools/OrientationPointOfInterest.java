package com.ar.openClimbAR.tools;

import android.util.SizeF;

/**
 * Created by xyz on 12/26/17.
 */

public class OrientationPointOfInterest extends PointOfInterest {
    public float degAzimuth = 0;
    public float degPitch = 0;
    public float degRoll = 0;
    public SizeF fieldOfViewDeg = new SizeF(0, 0);
    public float screenRotation = 0;

    public OrientationPointOfInterest(float pDecimalLatitude, float pDecimalLongitude, float pMetersAltitude)
    {
        super(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
    }
}

package com.ar.openClimbAR.tools;

/**
 * Created by xyz on 1/13/18.
 */

public interface IEnvironmentHandler {
    void updateOrientation(float pAzimuth, float pPitch, float pRoll);
    void updatePosition(final float pDecLatitude, final float pDecLongitude, final float pMetersAltitude, final float accuracy);
}

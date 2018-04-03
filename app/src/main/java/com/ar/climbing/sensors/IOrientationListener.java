package com.ar.climbing.sensors;

/**
 * Created by xyz on 1/13/18.
 */

public interface IOrientationListener {
    void updateOrientation(double pAzimuth, double pPitch, double pRoll);
}

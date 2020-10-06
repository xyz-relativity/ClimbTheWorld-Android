package com.climbtheworld.app.sensors.orientation;

/**
 * Created by xyz on 1/13/18.
 */

public interface IOrientationListener {
    void updateOrientation(OrientationManager.OrientationEvent event);
}

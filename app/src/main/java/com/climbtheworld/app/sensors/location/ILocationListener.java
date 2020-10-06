package com.climbtheworld.app.sensors.location;

/**
 * Created by xyz on 1/19/18.
 */

public interface ILocationListener {
    void updatePosition(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude, final double accuracy);
}

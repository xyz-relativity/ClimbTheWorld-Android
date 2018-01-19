package com.ar.openClimbAR.tools;

/**
 * Created by xyz on 1/19/18.
 */

public interface ILocationListener {
    void updatePosition(final float pDecLatitude, final float pDecLongitude, final float pMetersAltitude, final float accuracy);
}

package com.ar.openClimbAR.utils;

import android.view.View;

/**
 * Created by xyz on 1/31/18.
 */

public class CompassWidget {
    private final View compass;

    public CompassWidget(View compassContainer) {
        this.compass = compassContainer;
    }

    public void invalidate() {
        compass.setRotation(-(float)Globals.observer.degAzimuth);
    }
}

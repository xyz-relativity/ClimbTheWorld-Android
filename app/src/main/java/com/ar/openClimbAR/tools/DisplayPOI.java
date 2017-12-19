package com.ar.openClimbAR.tools;

import android.support.annotation.NonNull;

/**
 * Created by xyz on 12/5/17.
 */

public class DisplayPOI implements Comparable {
    public float distance = 0;
    public PointOfInterest poi;
    public float deltaDegAzimuth = 0;
    public float difDegAngle = 0;

    public DisplayPOI(float pDistance, float pDeltaDegAzimuth, float pDiffDegAngle, PointOfInterest pPoi) {
        this.distance = pDistance;
        this.poi = pPoi;
        this.deltaDegAzimuth = pDeltaDegAzimuth;
        this.difDegAngle = pDiffDegAngle;
    }

    @Override
    public int compareTo(@NonNull Object o) {
        if (o instanceof DisplayPOI) {
            if (this.distance > ((DisplayPOI) o).distance) return 1;
            if (this.distance < ((DisplayPOI) o).distance) return -1;
            else return 0;
        }
        return 0;
    }
}

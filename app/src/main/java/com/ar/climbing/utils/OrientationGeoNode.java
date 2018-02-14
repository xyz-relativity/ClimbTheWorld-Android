package com.ar.climbing.utils;

import com.ar.climbing.storage.database.GeoNode;

/**
 * Created by xyz on 12/26/17.
 */

public class OrientationGeoNode extends GeoNode {
    public double degAzimuth = 0;
    public double degPitch = 0;
    public double degRoll = 0;
    public Vector2d fieldOfViewDeg = new Vector2d(60f, 40f);
    public double screenRotation = 0;

    public OrientationGeoNode(float pDecimalLatitude, float pDecimalLongitude, float pMetersAltitude)
    {
        super(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
    }
}

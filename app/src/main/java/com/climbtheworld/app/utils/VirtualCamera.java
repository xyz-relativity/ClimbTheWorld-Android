package com.climbtheworld.app.utils;

import com.climbtheworld.app.storage.database.GeoNode;

/**
 * Created by xyz on 12/26/17.
 */

public class VirtualCamera extends GeoNode {
    public double degAzimuth = 0;
    public double degPitch = 0;
    public double degRoll = 0;
    public Vector2d fieldOfViewDeg = new Vector2d(60f, 40f);
    public double screenRotation = 0;

    public VirtualCamera(float pDecimalLatitude, float pDecimalLongitude, float pMetersAltitude)
    {
        super(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
    }

    public void onPause() {
        loadLocation();
    }

    public void onResume() {
        saveLocation();
    }

    private void saveLocation() {
        Globals.globalConfigs.setFloat(Configs.ConfigKey.virtualCameraDegLat, (float)decimalLatitude);
        Globals.globalConfigs.setFloat(Configs.ConfigKey.virtualCameraDegLon, (float)decimalLongitude);
    }

    private void loadLocation() {
        decimalLatitude = Globals.globalConfigs.getFloat(Configs.ConfigKey.virtualCameraDegLat);
        decimalLongitude = Globals.globalConfigs.getFloat(Configs.ConfigKey.virtualCameraDegLon);
    }
}

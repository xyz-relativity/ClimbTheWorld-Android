package com.climbtheworld.app.utils;

import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.OrientationManager;
import com.climbtheworld.app.storage.database.GeoNode;

/**
 * Created by xyz on 12/26/17.
 */

public class VirtualCamera extends GeoNode implements ILocationListener, IOrientationListener {
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
        saveLocation();
    }

    public void onResume() {
        loadLocation();
    }

    private void saveLocation() {
        Globals.globalConfigs.setFloat(Configs.ConfigKey.virtualCameraDegLat, (float)decimalLatitude);
        Globals.globalConfigs.setFloat(Configs.ConfigKey.virtualCameraDegLon, (float)decimalLongitude);
    }

    private void loadLocation() {
        decimalLatitude = Globals.globalConfigs.getFloat(Configs.ConfigKey.virtualCameraDegLat);
        decimalLongitude = Globals.globalConfigs.getFloat(Configs.ConfigKey.virtualCameraDegLon);
    }

    @Override
    public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
        updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
    }

    @Override
    public void updateOrientation(OrientationManager.OrientationEvent event) {
            degAzimuth = event.camera.x;
            degPitch = event.camera.y;
            degRoll = event.camera.z;
    }
}

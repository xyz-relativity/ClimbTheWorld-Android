package com.climbtheworld.app.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.SparseArray;
import android.view.Surface;

import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeConverter;

import org.osmdroid.util.GeoPoint;

/**
 * Created by xyz on 1/19/18.
 */

public class Globals {
    private Globals() {
        //hide constructor
    }

    public static boolean showExperimental = true;

    public static Context baseContext = null;

    private static final SparseArray ORIENTATIONS = new SparseArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, -90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 90);
    }

    public static int orientationToAngle(int rotation) {
        return (int)ORIENTATIONS.get(rotation);
    }

    private static final SparseArray ANGLE = new SparseArray();

    static {
        ORIENTATIONS.append(0, Surface.ROTATION_0);
        ORIENTATIONS.append(-90, Surface.ROTATION_90);
        ORIENTATIONS.append(180, Surface.ROTATION_180);
        ORIENTATIONS.append(90, Surface.ROTATION_270);
        ORIENTATIONS.append(270, Surface.ROTATION_270);
    }

    public static int angleToOrientation(int angle) {
        return (int) ANGLE.get(angle);
    }

    public static VirtualCamera virtualCamera = new VirtualCamera(
            45.35384f, 24.63507f,
            100f);
    public static Vector2d rotateCameraPreviewSize = new Vector2d(0,0);
    public static Configs globalConfigs = null;
    public static AppDatabase appDB = null;

    public static String oauthToken = null;
    public static String oauthSecret = null;

    public static GeoPoint poiToGeoPoint(GeoNode poi) {
        return new GeoPoint(poi.decimalLatitude, poi.decimalLongitude, poi.elevationMeters);
    }

    public static ColorStateList gradeToColorState(int gradeID) {
        float remapGradeScale = (float) AugmentedRealityUtils.remapScale(0f,
                GradeConverter.getConverter().maxGrades,
                1f,
                0f,
                gradeID);

        return getColorGradient(remapGradeScale);
    }

    public static ColorStateList getColorGradient(float gradient) {
        return ColorStateList.valueOf(android.graphics.Color.HSVToColor(new float[]{gradient*120f,1f,1f}));
    }

    public static boolean checkWifiOnAndConnected(Context context) {
        WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (wifiMgr.isWifiEnabled()) { // Wi-Fi adapter is ON

            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();

            if( wifiInfo.getNetworkId() == -1 ){
                return false; // Not connected to an access point
            }
            return true; // Connected to an access point
        }
        else {
            return false; // Wi-Fi adapter is OFF
        }
    }

    public static boolean allowDataDownload() {
        return (Globals.globalConfigs.getBoolean(Configs.ConfigKey.useMobileDataForRoutes) || checkWifiOnAndConnected(baseContext));
    }

    public static boolean allowMapDownload(Context context) {
        return (Globals.globalConfigs.getBoolean(Configs.ConfigKey.useMobileDataForMap) || checkWifiOnAndConnected(context));
    }

}

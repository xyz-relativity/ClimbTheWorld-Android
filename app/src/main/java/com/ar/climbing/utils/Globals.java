package com.ar.climbing.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.SparseArray;
import android.view.Surface;

import com.ar.climbing.augmentedreality.AugmentedRealityUtils;
import com.ar.climbing.storage.database.AppDatabase;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.tools.GradeConverter;

import org.osmdroid.util.GeoPoint;

/**
 * Created by xyz on 1/19/18.
 */

public class Globals {
    private Globals() {
        //hide constructor
    }

    public static Context baseContext = null;

    private static final SparseArray ORIENTATIONS = new SparseArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, -90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 90);
    }

    public static int getScreenRotationAngle(int rotation) {
        return (int)ORIENTATIONS.get(rotation);
    }

    public static OrientationGeoNode observer = new OrientationGeoNode(
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

    public static void showErrorDialog(final Context parent, final String message, final DialogInterface.OnClickListener listener) {
        new android.app.AlertDialog.Builder(parent)
                .setTitle(parent.getResources().getString(android.R.string.dialog_alert_title))
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setNegativeButton(android.R.string.ok, listener).show();
    }
}

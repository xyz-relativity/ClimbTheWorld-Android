package com.climbtheworld.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.design.internal.BottomNavigationItemView;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.views.DataFragment;
import com.climbtheworld.app.tools.GradeConverter;

import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Created by xyz on 1/19/18.
 */

public class Globals {
    public static Context baseContext;

    private Globals() {
        //hide constructor
    }

    public static boolean emptyDb = true;

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
        WifiManager wifiMgr = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiMgr.isWifiEnabled()) { // Wi-Fi adapter is ON

            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();

            return wifiInfo.getNetworkId() != -1;
        }
        else {
            return false; // Wi-Fi adapter is OFF
        }
    }

    public static boolean allowDataDownload() {
        return (globalConfigs.getBoolean(Configs.ConfigKey.useMobileDataForRoutes) || checkWifiOnAndConnected(baseContext));
    }

    public static boolean allowMapDownload(Context context) {
        return (globalConfigs.getBoolean(Configs.ConfigKey.useMobileDataForMap) || checkWifiOnAndConnected(context));
    }

    public static void onResume(final Activity parent) {
        if (globalConfigs.getBoolean(Configs.ConfigKey.keepScreenOn)) {
            parent.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        virtualCamera.onResume();
        showNotifications(parent);
    }

    public static void onPause(final Activity parent) {
        parent.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        virtualCamera.onPause();
    }

    public static void showNotifications(final Activity parent) {
        (new Thread() {
            public void run() {
                emptyDb = Globals.appDB.nodeDao().loadAllNodes().isEmpty();

                final boolean uploadNotification = !Globals.appDB.nodeDao().loadAllUpdatedNodes().isEmpty();
                final boolean downloadNotification = Globals.appDB.nodeDao().loadAllNodes().isEmpty();// globalConfigs.getBoolean(Configs.ConfigKey.showPathToDownload);

                ColorStateList infoLevel = null;
                if (downloadNotification) {
                    infoLevel = ColorStateList.valueOf( parent.getResources().getColor(android.R.color.holo_green_light));
                }

                if (uploadNotification) {
                    infoLevel = ColorStateList.valueOf( parent.getResources().getColor(android.R.color.holo_orange_dark));
                }

                final ColorStateList notificationIconColor = infoLevel;

                parent.runOnUiThread(new Thread() {
                    public void run() {
                        if (parent.findViewById(R.id.infoIcon)!= null) {
                            if (notificationIconColor != null) {
                                parent.findViewById(R.id.infoIcon).setVisibility(View.VISIBLE);
                                ((ImageView)parent.findViewById(R.id.infoIcon).findViewById(R.id.notificationIcon)).setImageTintList(notificationIconColor);
                            } else {
                                parent.findViewById(R.id.infoIcon).setVisibility(View.GONE);
                            }
                        }

                        if (parent.findViewById(R.id.dataNavigationBar)!= null) {
                            if (uploadNotification) {
                                updateNavNotif(parent, 2, ColorStateList.valueOf(parent.getResources().getColor(android.R.color.holo_orange_dark)));
                            } else {
                                updateNavNotif(parent, 2, null);
                            }
                            if (downloadNotification) {
                                updateNavNotif(parent, 1, ColorStateList.valueOf(parent.getResources().getColor(android.R.color.holo_green_light)));
                            } else {
                                updateNavNotif(parent, 1, null);
                            }
                        }
                    }
                });

            }
        }).start();
    }

    private static void updateNavNotif(final Activity parent, int itemId, ColorStateList notificationIconColor) {
        BottomNavigationMenuView bottomNavigationMenuView =
                (BottomNavigationMenuView) ((BottomNavigationView)parent.findViewById(R.id.dataNavigationBar)).getChildAt(0);

        BottomNavigationItemView itemView = (BottomNavigationItemView) bottomNavigationMenuView.getChildAt(itemId);

        if (notificationIconColor != null) {
            if (!(itemView.getChildAt(itemView.getChildCount()-1) instanceof RelativeLayout)) {
                View badge = LayoutInflater.from(parent)
                        .inflate(R.layout.notification_icon, bottomNavigationMenuView, false);

                int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        18, parent.getResources().getDisplayMetrics());

                ImageView img = badge.findViewById(R.id.notificationIcon);
                img.getLayoutParams().width = size;
                img.getLayoutParams().height = size;
                img.setImageTintList(notificationIconColor);

                size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        10, parent.getResources().getDisplayMetrics());

                badge.setPadding(size, 0, size, 0);

                itemView.addView(badge);
            }
        } else {
            if (itemView.getChildAt(itemView.getChildCount()-1) instanceof RelativeLayout) {
                itemView.removeViewAt(itemView.getChildCount() - 1);
            }

            itemView = (BottomNavigationItemView)bottomNavigationMenuView.getChildAt(1);
            if (itemView.getChildAt(itemView.getChildCount()-1) instanceof RelativeLayout) {
                itemView.removeViewAt(itemView.getChildCount() - 1);
            }
        }

        bottomNavigationMenuView.invalidate();
    }

    public static void loadCountryList() {
        InputStream is = baseContext.getResources().openRawResource(R.raw.country_bbox);

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

        try {
            reader.readLine(); //ignore headers
            String line;
            while ((line = reader.readLine()) != null) {
                String[] country = line.split(",");
                DataFragment.sortedCountryList.add(country[0]);
                DataFragment.countryMap.put(country[0], new DataFragment.CountryViewState(DataFragment.CountryState.ADD, country));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

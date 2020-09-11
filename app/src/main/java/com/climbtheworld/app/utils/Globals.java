package com.climbtheworld.app.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.DisplayMetrics;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.climbtheworld.app.ClimbTheWorld;
import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.dialogs.DialogBuilder;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.views.DataFragment;
import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationMenuView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;
import needle.UiRelatedTask;

/**
 * Created by xyz on 1/19/18.
 */

public class Globals {
    public static boolean showDownloadPopup = true;

    private Globals() {
        //hide constructor
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, -90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 90);
    }

    public static int orientationToAngle(int rotation) {
        return (int)ORIENTATIONS.get(rotation);
    }

    static {
        ORIENTATIONS.append(0, Surface.ROTATION_0);
        ORIENTATIONS.append(-90, Surface.ROTATION_90);
        ORIENTATIONS.append(180, Surface.ROTATION_180);
        ORIENTATIONS.append(90, Surface.ROTATION_270);
        ORIENTATIONS.append(270, Surface.ROTATION_270);
    }

    public static VirtualCamera virtualCamera = new VirtualCamera(
            45.35384f, 24.63507f,
            100f);
    public static Vector2d rotateCameraPreviewSize = new Vector2d(0,0);
    public static AppDatabase appDB = null;

    public static GeoPoint poiToGeoPoint(GeoNode poi) {
        return new GeoPoint(poi.decimalLatitude, poi.decimalLongitude, poi.elevationMeters);
    }

    public static ColorStateList gradeToColorState(int gradeID) {
        return gradeToColorState(gradeID, 255);
    }

    public static ColorStateList gradeToColorState(int gradeID, int alpha) {
        float remapGradeScale = (float) AugmentedRealityUtils.remapScale(0f,
                GradeSystem.maxGrades,
                1f,
                0f,
                gradeID);

        return getColorGradient(remapGradeScale).withAlpha(alpha);
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

    public static boolean allowDataDownload(AppCompatActivity parent) {
        return (Configs.instance(parent).getBoolean(Configs.ConfigKey.useMobileDataForRoutes) || checkWifiOnAndConnected(parent));
    }

    public static boolean allowMapDownload(AppCompatActivity parent) {
        return (Configs.instance(parent).getBoolean(Configs.ConfigKey.useMobileDataForMap) || checkWifiOnAndConnected(parent));
    }

    public static void onResume(final AppCompatActivity parent) {
        if (Configs.instance(parent).getBoolean(Configs.ConfigKey.keepScreenOn)) {
            parent.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        virtualCamera.onResume(parent);
        showNotifications(parent);
    }

    public static void onPause(final AppCompatActivity parent) {
        parent.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        virtualCamera.onPause(parent);
    }

    public static void showNotifications(final AppCompatActivity parent) {
        Constants.DB_EXECUTOR.execute(new UiRelatedTask() {

            boolean uploadNotification;
            boolean downloadNotification;

            @Override
            protected Object doWork() {
                uploadNotification = !Globals.appDB.nodeDao().loadAllUpdatedNodes().isEmpty();
                downloadNotification = Globals.appDB.nodeDao().getSmallestId() == 0;
                return null;
            }

            @Override
            protected void thenDoUiRelatedWork(Object o) {
                ColorStateList infoLevel = null;
                Configs configs = Configs.instance(parent);
                if (downloadNotification) {
                    infoLevel = ColorStateList.valueOf( parent.getResources().getColor(android.R.color.holo_green_light));

                    if (showDownloadPopup
                            && !configs.getBoolean(Configs.ConfigKey.isFirstRun)
                            && configs.getBoolean(Configs.ConfigKey.showDownloadClimbingData)) {
                        DialogBuilder.buildDownloadRegionAlert(parent).show();
                        showDownloadPopup = false;
                    }
                }

                if (uploadNotification) {
                    infoLevel = ColorStateList.valueOf( parent.getResources().getColor(android.R.color.holo_orange_dark));
                }

                //update icon on view (main activity and tools activity)
                if (parent.findViewById(R.id.icon_notification)!= null) {
                    if (infoLevel != null) {
                        parent.findViewById(R.id.icon_notification).setVisibility(View.VISIBLE);
                        ((ImageView)parent.findViewById(R.id.icon_notification).findViewById(R.id.notificationIcon)).setImageTintList(infoLevel);

                        Drawable d = ((ImageView)parent.findViewById(R.id.icon_notification).findViewById(R.id.notificationIcon)).getDrawable();
                        animate(d, true);
                    } else {
                        parent.findViewById(R.id.icon_notification).setVisibility(View.GONE);
                        Drawable d = ((ImageView)parent.findViewById(R.id.icon_notification).findViewById(R.id.notificationIcon)).getDrawable();
                        animate(d, false);
                    }
                }

                //update navigation bar.
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

                //update float action
                if (parent.findViewById(R.id.downloadButton)!= null) {
                    LayerDrawable icon = (LayerDrawable) ResourcesCompat.getDrawable(parent.getResources(), R.drawable.ic_data_manager_checkable, null);
                    Drawable subIcon = icon.findDrawableByLayerId(R.id.icon_notification);
                    if (infoLevel != null) {
                        subIcon.setAlpha(255);
                        subIcon.setTintList(infoLevel);

                        animate(subIcon, true);
                    } else {
                        icon.findDrawableByLayerId(R.id.icon_notification).setAlpha(0);
                        animate(subIcon, false);
                    }
                    ((FloatingActionButton)parent.findViewById(R.id.downloadButton)).setImageDrawable(icon);
                }
            }
        });
    }

    private static void animate(Drawable icon, boolean start) {
        if (start) {
            if (icon instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) icon).start();
            } else if (icon instanceof AnimatedVectorDrawableCompat) {
                ((AnimatedVectorDrawableCompat) icon).start();
            }
        } else {
            if (icon instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) icon).stop();
            } else if (icon instanceof AnimatedVectorDrawableCompat) {
                ((AnimatedVectorDrawableCompat) icon).stop();
            }
        }
    }

    private static void updateNavNotif(final AppCompatActivity parent, int itemId, ColorStateList notificationIconColor) {
        BottomNavigationMenuView bottomNavigationMenuView =
                (BottomNavigationMenuView) ((BottomNavigationView)parent.findViewById(R.id.dataNavigationBar)).getChildAt(0);

        BottomNavigationItemView itemView = (BottomNavigationItemView) bottomNavigationMenuView.getChildAt(itemId);

        if (notificationIconColor != null) {
            if (!(itemView.getChildAt(itemView.getChildCount()-1) instanceof RelativeLayout)) {
                View badge = LayoutInflater.from(parent)
                        .inflate(R.layout.icon_notification, bottomNavigationMenuView, false);

                int size = (int) Globals.convertDpToPixel(24);

                ImageView img = badge.findViewById(R.id.notificationIcon);
                img.getLayoutParams().width = size;
                img.getLayoutParams().height = size;
                img.setImageTintList(notificationIconColor);

                Drawable d = img.getDrawable();
                animate(d, true);

                size = (int) Globals.convertDpToPixel(10);

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
        InputStream is = ClimbTheWorld.getContext().getResources().openRawResource(R.raw.country_bbox);

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

    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
     * @return A float value to represent px equivalent to dp depending on device density
     */
    public static float convertDpToPixel(float dp){
        return dp * ((float) Resources.getSystem().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    /**
     * This method converts device specific pixels to density independent pixels.
     *
     * @param px A value in px (pixels) unit. Which we need to convert into db
     * @return A float value to represent dp equivalent to px value
     */
    public static float convertPixelsToDp(float px){
        return px / ((float) Resources.getSystem().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    public static String getDistanceString(String distance) {
        try {
            return getDistanceString(Double.parseDouble(distance));
        } catch (NumberFormatException e) {
            return distance;
        }
    }

    public static String getDistanceString(String distance, String displayUnits) {
        try {
            return getDistanceString(Double.parseDouble(distance), displayUnits);
        } catch (NumberFormatException e) {
            return distance;
        }
    }

    public static String getDistanceString(double distance, String displayUnits) {
        return String.format(Locale.getDefault(), "%.2f %s", distance, displayUnits);
    }

    public static String getDistanceString(double distance) {
        String displayDistUnits;
        if (distance > 1000) {
            displayDistUnits = "km";
            distance = distance / 1000;
        } else {
            displayDistUnits = "m";
        }

        return getDistanceString(distance, displayDistUnits);
    }

    public static Long getNewNodeID() {
        long tmpID = Globals.appDB.nodeDao().getSmallestId();
        //if the smallest ID is positive this is the first node creates, so set the id to -1.
        if (tmpID >= 0) {
            tmpID = -1L;
        } else {
            tmpID -= 1;
        }
        return tmpID;
    }
}

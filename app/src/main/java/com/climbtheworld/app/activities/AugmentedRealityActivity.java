package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.augmentedreality.AugmentedRealityViewManager;
import com.climbtheworld.app.osm.MarkerGeoNode;
import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.LocationHandler;
import com.climbtheworld.app.sensors.SensorListener;
import com.climbtheworld.app.sensors.camera.AutoFitTextureView;
import com.climbtheworld.app.sensors.camera.CameraHandler;
import com.climbtheworld.app.sensors.camera.CameraTextureViewListener;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Quaternion;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.widgets.CompassWidget;
import com.climbtheworld.app.widgets.MapViewWidget;

import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import needle.UiRelatedTask;

public class AugmentedRealityActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {

    private AutoFitTextureView textureView;
    private CameraHandler camera;
    private CameraTextureViewListener cameraTextureListener;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;
    private View horizon;

    private Map<Long, GeoNode> boundingBoxPOIs = new HashMap<>(); //POIs around the virtualCamera.

    private MapViewWidget mapWidget;
    private AugmentedRealityViewManager viewManager;
    private DataManager downloadManager;

    private CountDownTimer gpsUpdateAnimationTimer;
    private double maxDistance;

    private List<GeoNode> visible = new ArrayList<>();
    private List<GeoNode> zOrderedDisplay = new ArrayList<>();
    private Map<Long, MarkerGeoNode> allPOIs = new LinkedHashMap<>();
    private AtomicBoolean updatingView = new AtomicBoolean();

    private final int locationUpdate = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_augmented_reality);

        //others
        Globals.virtualCamera.screenRotation = Globals.orientationToAngle(getWindowManager().getDefaultDisplay().getRotation());

        CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));
        this.viewManager = new AugmentedRealityViewManager(this);
        this.mapWidget = new MapViewWidget(this, findViewById(R.id.mapViewContainer), allPOIs);
        mapWidget.setShowObserver(true, null);
        mapWidget.addMapListener(new DelayedMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                if (event.getX() != 0 || event.getY() != 0) {
                    downloadBBox();
                }
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                downloadBBox();
                return false;
            }
        }));

        this.horizon = findViewById(R.id.horizon);

        horizon.post(new Runnable() {
            public void run() {
                viewManager.postInit();
                horizon.getLayoutParams().width = (int)Math.sqrt((viewManager.getContainerSize().x * viewManager.getContainerSize().x)
                        + (viewManager.getContainerSize().y * viewManager.getContainerSize().y));
            }
        });

        this.downloadManager = new DataManager(true);

        //camera
        this.textureView = findViewById(R.id.cameraTexture);
        assert textureView != null;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera = new CameraHandler((CameraManager) getSystemService(Context.CAMERA_SERVICE),
                    AugmentedRealityActivity.this, this, textureView);
            cameraTextureListener = new CameraTextureViewListener(camera);
            textureView.setSurfaceTextureListener(cameraTextureListener);
        }

        //location
        locationHandler = new LocationHandler(AugmentedRealityActivity.this, this, locationUpdate);
        locationHandler.addListener(this);

        //orientation
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this, compass);

        maxDistance = Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit);

        if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.showExperimentalAR)) {
            Drawable icon = this.getDrawable(android.R.drawable.ic_dialog_info).mutate();
            icon.setTint(this.getResources().getColor(android.R.color.holo_green_light));

            AlertDialog d = new AlertDialog.Builder(this)
                    .setCancelable(true) // This blocks the 'BACK' button
                    .setIcon(icon)
                    .setTitle(getResources().getString(R.string.experimental_view))
                    .setMessage(Html.fromHtml(getResources().getString(R.string.experimental_view_message)))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setNeutralButton(R.string.dont_show_again, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Globals.globalConfigs.setBoolean(Configs.ConfigKey.showExperimentalAR, false);
                        }
                    }).create();
            d.setIcon(icon);
            d.show();
            ((TextView) d.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    public void onCompassButtonClick (View v) {
        DialogBuilder.buildObserverInfoDialog(v).show();
    }

    public void onSettingsButtonClick (View v) {
        Intent intent = new Intent(AugmentedRealityActivity.this, SettingsActivity.class);
        startActivityForResult(intent, Constants.OPEN_CONFIG_ACTIVITY);
    }

    private void downloadBBox() {
        Constants.DB_EXECUTOR
                .execute(new UiRelatedTask<Boolean>() {
                    @Override
                    protected Boolean doWork() {
                        boolean result = downloadManager.loadBBox(mapWidget.getOsmMap().getBoundingBox(), allPOIs);
                        if (result) {
                            mapWidget.resetPOIs();
                        }
                        return result;
                    }

                    @Override
                    protected void thenDoUiRelatedWork(Boolean result) {
                    }
                });
    }

    private void downloadAround(final Quaternion center) {
        Constants.DB_EXECUTOR
                .execute(new UiRelatedTask<Boolean>() {
                    @Override
                    protected Boolean doWork() {
                        boolean result = downloadManager.loadAround(center, maxDistance, allPOIs,
                                GeoNode.NodeTypes.route,
                                GeoNode.NodeTypes.crag,
                                GeoNode.NodeTypes.artificial);

                        if (result) {
                            mapWidget.resetPOIs();
                        }
                        return result;
                    }

                    @Override
                    protected void thenDoUiRelatedWork(Boolean result) {
                        if (result) {
                            updateBoundingBox(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters);
                        }
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CameraHandler.REQUEST_CAMERA_PERMISSION || requestCode == LocationHandler.REQUEST_FINE_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(AugmentedRealityActivity.this, "Sorry!!!, you can't use this app without granting permission",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Globals.onResume(this);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera.startBackgroundThread();
            if (textureView.isAvailable()) {
                camera.openCamera(textureView.getWidth(), textureView.getHeight());
            } else {
                textureView.setSurfaceTextureListener(cameraTextureListener);
            }
        }

        locationHandler.onResume();

        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_GAME);

        if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.showVirtualHorizon)) {
            horizon.setVisibility(View.VISIBLE);
        } else {
            horizon.setVisibility(View.INVISIBLE);
        }

        updatePosition(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters, 1);
    }

    @Override
    protected void onPause() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera.closeCamera();
            camera.stopBackgroundThread();
        }

        sensorManager.unregisterListener(sensorListener);
        locationHandler.onPause();

        Globals.onPause(this);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.OPEN_EDIT_ACTIVITY) {
            recreate(); //reset the current activity
        }

        if (requestCode == Constants.OPEN_CONFIG_ACTIVITY) {
            recreate(); //reset the current activity
        }
    }

    public void updateOrientation(double pAzimuth, double pPitch, double pRoll) {
        Globals.virtualCamera.degAzimuth = pAzimuth;
        Globals.virtualCamera.degPitch = pPitch;
        Globals.virtualCamera.degRoll = pRoll;

        mapWidget.onOrientationChange(pAzimuth, pPitch, pRoll);
        mapWidget.invalidate();

        updateView();
    }

    public void updatePosition(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude, final double accuracy) {
        final int animationInterval = 100;

        downloadAround(new Quaternion(pDecLatitude, pDecLongitude, pMetersAltitude, 0));

        if (gpsUpdateAnimationTimer != null)
        {
            gpsUpdateAnimationTimer.cancel();
        }

        //Do a nice animation when moving to a new GPS position.
        gpsUpdateAnimationTimer = new CountDownTimer(Math.min(locationUpdate, animationInterval * Constants.POS_UPDATE_ANIMATION_STEPS)
                , animationInterval) {
            public void onTick(long millisUntilFinished) {
                long numSteps = millisUntilFinished / animationInterval;
                if (numSteps != 0) {
                    double xStepSize = (pDecLongitude - Globals.virtualCamera.decimalLongitude) / numSteps;
                    double yStepSize = (pDecLatitude - Globals.virtualCamera.decimalLatitude) / numSteps;

                    Globals.virtualCamera.updatePOILocation(Globals.virtualCamera.decimalLatitude + yStepSize,
                            Globals.virtualCamera.decimalLongitude + xStepSize, pMetersAltitude);

                    mapWidget.onLocationChange();
                    mapWidget.invalidate();
                    updateBoundingBox(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters);
                }
            }

            public void onFinish() {
                Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
                updateBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude);
            }
        }.start();
    }

    private void updateBoundingBox(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude) {
        double deltaLatitude = Math.toDegrees(maxDistance / AugmentedRealityUtils.EARTH_RADIUS_M);
        double deltaLongitude = Math.toDegrees(maxDistance / (Math.cos(Math.toRadians(pDecLatitude)) * AugmentedRealityUtils.EARTH_RADIUS_M));

        for (Long poiID: allPOIs.keySet()) {
            GeoNode poi = allPOIs.get(poiID).geoNode;
            if ((poi.decimalLatitude > pDecLatitude - deltaLatitude && poi.decimalLatitude < pDecLatitude + deltaLatitude)
                    && (poi.decimalLongitude > pDecLongitude - deltaLongitude && poi.decimalLongitude < pDecLongitude + deltaLongitude)) {

                boundingBoxPOIs.put(poiID, poi);
            } else if (boundingBoxPOIs.containsKey(poiID)) {
                viewManager.removePOIFromView(poi);
                boundingBoxPOIs.remove(poiID);
            }
        }

        updateView();
    }

    private void updateView()
    {
        if (updatingView.get()) {
            return;
        }
        updatingView.set(true);

        updateCardinals();

        visible.clear();
        //find elements in view and sort them by distance.

        double fov = Math.max(Globals.virtualCamera.fieldOfViewDeg.x / 2.0, Globals.virtualCamera.fieldOfViewDeg.y / 2.0);

        for (Long poiID : boundingBoxPOIs.keySet()) {
            GeoNode poi = boundingBoxPOIs.get(poiID);

            double distance = AugmentedRealityUtils.calculateDistance(Globals.virtualCamera, poi);
            if (distance < maxDistance) {
                double deltaAzimuth = AugmentedRealityUtils.calculateTheoreticalAzimuth(Globals.virtualCamera, poi);
                double difAngle = AugmentedRealityUtils.diffAngle(deltaAzimuth, Globals.virtualCamera.degAzimuth);
                if (Math.abs(difAngle) <= fov) {
                    poi.distanceMeters = distance;
                    poi.deltaDegAzimuth = deltaAzimuth;
                    poi.difDegAngle = difAngle;
                    visible.add(poi);
                    continue;
                }
            }
            viewManager.removePOIFromView(poi);
        }

        Collections.sort(visible);

        //display elements form largest to smallest. This will allow smaller elements to be clickable.
        int displayLimit = 0;
        zOrderedDisplay.clear();
        for (GeoNode poi: visible)
        {
            if (displayLimit < Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowCountLimit)) {
                displayLimit++;

                zOrderedDisplay.add(poi);
            } else {
                viewManager.removePOIFromView(poi);
            }
        }

        Collections.reverse(zOrderedDisplay);

        for (GeoNode zpoi: zOrderedDisplay) {
            viewManager.addOrUpdatePOIToView(zpoi);
        }

        updatingView.set(false);
    }

    private void updateCardinals() {
        // Both compass and map location are viewed in the mirror, so they need to be rotated in the opposite direction.
        Quaternion pos = AugmentedRealityUtils.getXYPosition(0, Globals.virtualCamera.degPitch,
                Globals.virtualCamera.degRoll, Globals.virtualCamera.screenRotation,
                new Vector2d(horizon.getLayoutParams().width, horizon.getLayoutParams().height),
                Globals.virtualCamera.fieldOfViewDeg, viewManager.getContainerSize());
        horizon.setRotation((float) pos.w);
        horizon.setY((float) pos.y);

        mapWidget.invalidate();
    }
}

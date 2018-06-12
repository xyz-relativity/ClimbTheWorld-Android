package com.climbtheworld.app.activitys;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.ARCore.helpers.BackgroundRenderer;
import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.LocationHandler;
import com.climbtheworld.app.storage.AsyncDataManager;
import com.climbtheworld.app.storage.IDataManagerEventListener;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.DialogBuilder;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.widgets.MapViewWidget;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import org.osmdroid.views.MapView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ViewTopoArCoreActivity extends AppCompatActivity implements GLSurfaceView.Renderer, ILocationListener, IDataManagerEventListener {
    private boolean mUserRequestedInstall = true;
    private Session session;
    private GLSurfaceView surfaceView;

    private MapViewWidget mapWidget;
    private AsyncDataManager downloadManager;
    private Map<Long, GeoNode> allPOIs = new ConcurrentHashMap<>();
    private Map<Long, GeoNode> boundingBoxPOIs = new HashMap<>(); //POIs around the virtualCamera.
    private LocationHandler locationHandler;
    private double maxDistance;
    private CountDownTimer gpsUpdateAnimationTimer;
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_topo_ar_core);

        surfaceView = findViewById(R.id.surfaceView);

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mUserRequestedInstall = false;

        this.mapWidget = new MapViewWidget(this, (MapView)findViewById(R.id.openMapView), allPOIs);
        mapWidget.setShowObserver(true, null);
        mapWidget.setShowPOIs(true);
        mapWidget.getOsmMap().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                downloadManager.loadBBox(mapWidget.getOsmMap().getBoundingBox(), allPOIs);
            }
        });

        this.downloadManager = new AsyncDataManager(true);
        downloadManager.addObserver(this);

        //location
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationHandler = new LocationHandler(locationManager, ViewTopoArCoreActivity.this, this);
        locationHandler.addListener(this);

        maxDistance = Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit);
    }

    void maybeEnableArButton() {
        // Likely called from Activity.onCreate() of an activity with AR buttons.
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // re-query at 5Hz while we check compatibility.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    maybeEnableArButton();
                }
            }, 200);
        }
        if (!availability.isSupported()) {
            switchToLegacy();
        }
    }

    private void switchToLegacy() {
        DialogBuilder.showErrorDialog(this, "Your device does not support ArCore. Switching to legacy Ar.", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(ViewTopoArCoreActivity.this, ViewTopoArCoreActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        Globals.onResume(this);

        try {
            if (session == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    case INSTALLED:
                        session = new Session(this);
                        // Success.
                        break;
                    case INSTALL_REQUESTED:
                        // Ensures next invocation of requestInstall() will either return
                        // INSTALLED or throw an exception.
                        mUserRequestedInstall = false;
                        return;
                }
            }
        } catch (UnavailableUserDeclinedInstallationException
                | UnavailableArcoreNotInstalledException
                | UnavailableDeviceNotCompatibleException
                | UnavailableSdkTooOldException
                | UnavailableApkTooOldException e) {
            switchToLegacy();
        }

        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            switchToLegacy();

            session = null;
            return;
        }

        surfaceView.onResume();

        locationHandler.onResume();

        updatePosition(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters, 1);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            surfaceView.onPause();
            session.pause();
        }

        locationHandler.onPause();
        Globals.onPause(this);
    }

    public void onButtonClick (View v) {
        switch (v.getId()) {
            case R.id.compassButton:
                DialogBuilder.buildObserverInfoDialog(v);
                break;
            case R.id.settingsButton:
                Intent intent = new Intent(ViewTopoArCoreActivity.this, SettingsActivity.class);
                startActivityForResult(intent, Constants.OPEN_CONFIG_ACTIVITY);
                break;
        }
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

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        try {
            backgroundRenderer.createOnGlThread(/*context=*/ this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            Frame frame = session.update();
            Camera camera = frame.getCamera();

            // If not tracking, don't draw 3d objects.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                return;
            }

            // Draw background.
            backgroundRenderer.draw(frame);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void updatePosition(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude, double accuracy) {
        final int animationInterval = 100;

        downloadManager.loadAround(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance, allPOIs);

        if (gpsUpdateAnimationTimer != null)
        {
            gpsUpdateAnimationTimer.cancel();
        }

        //Do a nice animation when moving to a new GPS position.
        gpsUpdateAnimationTimer = new CountDownTimer(Math.min(LocationHandler.LOCATION_MINIMUM_UPDATE_INTERVAL, animationInterval * Constants.POS_UPDATE_ANIMATION_STEPS)
                , animationInterval) {
            public void onTick(long millisUntilFinished) {
                long numSteps = millisUntilFinished / animationInterval;
                if (numSteps != 0) {
                    double xStepSize = (pDecLongitude - Globals.virtualCamera.decimalLongitude) / numSteps;
                    double yStepSize = (pDecLatitude - Globals.virtualCamera.decimalLatitude) / numSteps;

                    Globals.virtualCamera.updatePOILocation(Globals.virtualCamera.decimalLatitude + yStepSize,
                            Globals.virtualCamera.decimalLongitude + xStepSize, pMetersAltitude);
                    updateBoundingBox(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters);
                }
            }

            public void onFinish() {
                Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
                updateBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude);
            }
        }.start();
    }

    @Override
    public void onProgress(int progress, boolean hasChanges, Map<String, Object> results) {
        if (progress == 100 && hasChanges) {
            mapWidget.resetPOIs();

            runOnUiThread(new Thread() {
                public void run() {
                    updateBoundingBox(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters);
                }
            });
        }
    }

    private void updateBoundingBox(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude) {
        updateCardinals();
    }

    private void updateCardinals() {
        mapWidget.invalidate();
    }
}

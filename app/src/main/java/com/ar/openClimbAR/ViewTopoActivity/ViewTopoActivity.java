package com.ar.openClimbAR.ViewTopoActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.sensors.LocationHandler;
import com.ar.openClimbAR.sensors.camera.CameraHandler;
import com.ar.openClimbAR.sensors.camera.CameraTextureViewListener;
import com.ar.openClimbAR.tools.EnvironmentHandler;
import com.ar.openClimbAR.sensors.SensorListener;
import com.ar.openClimbAR.tools.OrientationPointOfInterest;

public class ViewTopoActivity extends AppCompatActivity {

    private TextureView textureView;
    private CameraHandler camera;
    private CameraTextureViewListener cameraTextureListener;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private LocationHandler locationHandler;
    private LocationManager locationManager;
    private EnvironmentHandler environmentHandler;

    private String[] cardinalNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_topo);

        //camera
        textureView = findViewById(R.id.texture);
        assert textureView != null;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera = new CameraHandler((CameraManager) getSystemService(Context.CAMERA_SERVICE),
                    ViewTopoActivity.this, this, textureView);
            cameraTextureListener = new CameraTextureViewListener(camera);
            textureView.setSurfaceTextureListener(cameraTextureListener);
        }

        environmentHandler = new EnvironmentHandler(ViewTopoActivity.this, camera);

        //location
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationHandler = new LocationHandler(locationManager, ViewTopoActivity.this, this, environmentHandler);

        //orientation
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener(environmentHandler);

        String[] translatedCardinals =  {getResources().getString(R.string.n),
                getResources().getString(R.string.nne),
                getResources().getString(R.string.ene),
                getResources().getString(R.string.e),
                getResources().getString(R.string.ese),
                getResources().getString(R.string.se),
                getResources().getString(R.string.sse),
                getResources().getString(R.string.s),
                getResources().getString(R.string.ssw),
                getResources().getString(R.string.sw),
                getResources().getString(R.string.wsw),
                getResources().getString(R.string.w),
                getResources().getString(R.string.wnw),
                getResources().getString(R.string.nw),
                getResources().getString(R.string.nnw),
                getResources().getString(R.string.n)};
        cardinalNames = translatedCardinals;
    }

    public void onCompassButtonClick (View v) {
        OrientationPointOfInterest obs = environmentHandler.getObserver();

        int azimuthID = (int)Math.floor(Math.abs(obs.degAzimuth - 11.25)/22.5);

        AlertDialog ad = new AlertDialog.Builder(this).create();
        ad.setCancelable(false); // This blocks the 'BACK' button
        ad.setTitle(obs.name);
        ad.setMessage(v.getResources().getString(R.string.longitude) + ": " + obs.getDecimalLongitude() + "°" +
                " " + v.getResources().getString(R.string.latitude) + ": " + obs.getDecimalLatitude() + "°" +
                "\n" + v.getResources().getString(R.string.altitude) + ": " + obs.getAltitudeMeters() + "m" +
                "\n" + v.getResources().getString(R.string.azimuth) + ": " + cardinalNames[azimuthID] + " (" + obs.degAzimuth + "°)");
        ad.setButton(DialogInterface.BUTTON_NEUTRAL, v.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }

    // Default onCreateOptionsMenu
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.basic_menu, menu);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CameraHandler.REQUEST_CAMERA_PERMISSION || requestCode == LocationHandler.REQUEST_FINE_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(ViewTopoActivity.this, "Sorry!!!, you can't use this app without granting permission",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera.startBackgroundThread();
            if (textureView.isAvailable()) {
                camera.openCamera();
            } else {
                textureView.setSurfaceTextureListener(cameraTextureListener);
            }
        }

        locationHandler.onResume();

        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                sensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera.closeCamera();
            camera.stopBackgroundThread();
        }

        sensorManager.unregisterListener(sensorListener);
        locationHandler.onPause();

        super.onPause();
    }
}

package com.ar.opentopo.opentopoar.ViewTopoActivity;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.Toast;

import com.ar.opentopo.opentopoar.R;
import com.ar.opentopo.opentopoar.camera.CameraHandler;
import com.ar.opentopo.opentopoar.camera.CameraTextureViewListener;
import com.ar.opentopo.opentopoar.tools.EnvironmentHandler;
import com.ar.opentopo.opentopoar.sensors.SensorListener;

public class ViewTopoActivity extends AppCompatActivity {

    private TextureView textureView;
    private CameraHandler camera;
    private CameraTextureViewListener cameraTextureListener;
    private SensorManager sensorManager;
    private SensorListener sensorListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_topo);

//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

        //camera
        textureView = findViewById(R.id.texture);
        assert textureView != null;
        camera = new CameraHandler((CameraManager) getSystemService(Context.CAMERA_SERVICE),
                ViewTopoActivity.this,this, textureView);
        cameraTextureListener = new CameraTextureViewListener(camera);
        textureView.setSurfaceTextureListener(cameraTextureListener);

        //orientation
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener(new EnvironmentHandler(ViewTopoActivity.this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CameraHandler.REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(ViewTopoActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera.startBackgroundThread();
        if (textureView.isAvailable()) {
            camera.openCamera();
        } else {
            textureView.setSurfaceTextureListener(cameraTextureListener);
        }

        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), sensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        camera.closeCamera();
        camera.stopBackgroundThread();

        sensorManager.unregisterListener(sensorListener);

        super.onPause();
    }
}
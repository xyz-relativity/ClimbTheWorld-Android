package com.ar.opentopo.opentopoar.ViewTopoActivity;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.Toast;

import com.ar.opentopo.opentopoar.R;
import com.ar.opentopo.opentopoar.utils.CameraHandler;

public class ViewTopoActivity extends AppCompatActivity {

    //Good docs:
    // https://inducesmile.com/android/android-camera2-api-example-tutorial/

    private static final String TAG = "AndroidCameraApi";
    private TextureView textureView;

    private CameraHandler camera;
    private CameraTextureViewListener cameraTextureListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_topo);

        textureView = findViewById(R.id.texture);
        assert textureView != null;
        camera = new CameraHandler((CameraManager) getSystemService(Context.CAMERA_SERVICE),
                ViewTopoActivity.this,this, textureView);
        cameraTextureListener = new CameraTextureViewListener(camera);
        textureView.setSurfaceTextureListener(cameraTextureListener);
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
    }
    @Override
    protected void onPause() {
        camera.closeCamera();
        camera.stopBackgroundThread();
        super.onPause();
    }
}

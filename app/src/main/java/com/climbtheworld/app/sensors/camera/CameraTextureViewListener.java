package com.climbtheworld.app.sensors.camera;

import android.graphics.SurfaceTexture;
import android.view.TextureView;

/**
 * Created by xyz on 11/24/17.
 */

public class CameraTextureViewListener implements TextureView.SurfaceTextureListener {
    private CameraHandler camera;

    public CameraTextureViewListener(CameraHandler pCamera)
    {
        this.camera = pCamera;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        //open your camera here
        camera.openCamera(width, height);
    }
    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Transform you image captured size according to the surface width and height
    }
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }
    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }
}

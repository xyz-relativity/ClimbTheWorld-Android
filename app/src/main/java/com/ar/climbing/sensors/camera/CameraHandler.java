package com.ar.climbing.sensors.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Size;
import android.util.SizeF;
import android.view.Surface;
import android.widget.Toast;

import com.ar.climbing.utils.Globals;
import com.ar.climbing.utils.Vector2d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by xyz on 11/24/17.
 */

public class CameraHandler {
    public static final int REQUEST_CAMERA_PERMISSION = 200;

    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;


    private String mCameraId = null;
    private CameraCharacteristics characteristics = null;
    private CameraManager cameraManager;
    private Activity activity;
    private Context context;
    private Size imageDimension;
    private AutoFitTextureView textureView;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private int sensorOrientationAngle;
    private int displayOrientation;
    private Size mPreviewSize;

    public CameraHandler(CameraManager pManager, Activity pActivity, Context pContext, AutoFitTextureView pTexture) {
        this.cameraManager = pManager;
        this.activity = pActivity;
        this.textureView = pTexture;
        this.context = pContext;
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    private void calculateFOV(double previewImgWidth, double previewImgHeight) {
        if (cameraManager != null && mCameraId != null) {
            float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

            if (focalLengths != null && focalLengths.length > 0) {
                SizeF sensorPhysicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                Rect activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                Size pixelArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);

                double focalLength = focalLengths[0];

                double previewWidth = Math.max(previewImgWidth, previewImgHeight);
                double previewHeight = Math.min(previewImgWidth, previewImgHeight);
                double sensorWith = Math.max(sensorPhysicalSize.getWidth(), sensorPhysicalSize.getHeight());
                double sensorHeight = Math.min(sensorPhysicalSize.getWidth(), sensorPhysicalSize.getHeight());
                double sensorActiveWith = Math.max(activeArray.right, activeArray.bottom);
                double sensorActiveHeight = Math.min(activeArray.right, activeArray.bottom);
                double sensorPixelWith = Math.max(pixelArray.getWidth(), pixelArray.getHeight());
                double sensorPixelHeight = Math.min(pixelArray.getWidth(), pixelArray.getHeight());
                double streamWith = Math.max(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                double streamHeight = Math.min(mPreviewSize.getWidth(), mPreviewSize.getHeight());

                double previewAspectRatio = previewWidth / previewHeight;
                double sensorActiveAspectRatio = sensorActiveWith / sensorActiveHeight;
                double streamAspectRatio = streamWith / streamHeight;

                double output_physical_with;
                double output_physical_height;

                if (previewAspectRatio <= sensorActiveAspectRatio) {
                    output_physical_with = sensorWith * (sensorActiveWith / sensorPixelWith);
                    output_physical_height = sensorHeight * (sensorActiveHeight / sensorPixelHeight) * previewAspectRatio / sensorActiveAspectRatio;
                } else {
                    output_physical_with = sensorWith * (sensorActiveWith / sensorPixelWith) * (previewAspectRatio / sensorActiveAspectRatio);
                    output_physical_height = sensorHeight * (sensorActiveHeight / sensorPixelHeight);
                }

                double fovWidth = Math.toDegrees(2.0 * Math.atan(output_physical_with / (2.0 * focalLength)));
                double fovHeight = Math.toDegrees(2.0 * Math.atan(output_physical_height / (2.0 * focalLength)));

                Globals.virtualCamera.fieldOfViewDeg = new Vector2d(fovWidth, fovHeight);

                System.out.println("Sensor: " + sensorOrientationAngle);
                System.out.println("Display: " + Globals.orientationToAngle(displayOrientation));
                System.out.println("FOV: " + Globals.virtualCamera.fieldOfViewDeg);
            }
        }
    }

    public void openCamera(int width, int height) {
        try {
            setUpCameraOutputs(width, height);
            if (mCameraId != null) {
                configureTransform(width, height);

                characteristics = cameraManager.getCameraCharacteristics(mCameraId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
                // Add permission for camera and let user grant the permission
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                    return;
                }
                cameraManager.openCamera(mCameraId, stateCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            assert textureView != null;
            if (textureView == null || imageDimension == null || textureView.getSurfaceTexture() == null) {
                return;
            }
            textureView.getSurfaceTexture().setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(textureView.getSurfaceTexture());
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    try {
                        cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(activity, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraHandler Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    public void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                characteristics
                        = manager.getCameraCharacteristics(cameraId);

                displayOrientation = activity.getWindowManager().getDefaultDisplay().getRotation();
                sensorOrientationAngle = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                mCameraId = cameraId;

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                Point tmpDisplaySize = new Point();

                activity.getWindowManager().getDefaultDisplay().getSize(tmpDisplaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = tmpDisplaySize.x;
                int maxPreviewHeight = tmpDisplaySize.y;

                if (swapDimensions()) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = tmpDisplaySize.y;
                    maxPreviewHeight = tmpDisplaySize.x;
                }

                Globals.rotateCameraPreviewSize = new Vector2d(width, height);

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);
            }
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    private boolean swapDimensions() {
        switch (displayOrientation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (sensorOrientationAngle == 90 || sensorOrientationAngle == 270) {
                    return true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (sensorOrientationAngle == 0 || sensorOrientationAngle == 180) {
                    return true;
                }
                break;
            default:
        }
        return false;
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == mPreviewSize || null == activity) {
            return;
        }

        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
        float scale = Math.max(
                (float) viewHeight / mPreviewSize.getHeight(),
                (float) viewWidth / mPreviewSize.getWidth());
        matrix.postScale(scale, scale, centerX, centerY);
        matrix.postRotate((float) Globals.virtualCamera.screenRotation, centerX, centerY);

        textureView.setTransform(matrix);

        calculateFOV(viewWidth, viewHeight);
    }

}

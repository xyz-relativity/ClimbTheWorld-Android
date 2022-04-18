package com.climbtheworld.app.sensors.camera;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.SizeF;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.view.PreviewView;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Vector2d;

/**
 * Created by xyz on 12/26/17.
 */

public class VirtualCamera extends GeoNode implements ILocationListener, IOrientationListener {
	public double degAzimuth = 0;
	public double degPitch = 0;
	public double degRoll = 0;
	public Vector2d fieldOfViewDeg = new Vector2d(70.0f, 60.0f);
	public double screenRotation = 0;

	public VirtualCamera(float pDecimalLatitude, float pDecimalLongitude, float pMetersAltitude) {
		super(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
	}

	public void onPause(AppCompatActivity parent) {
		saveLocation(Configs.instance(parent));
	}

	public void onResume(AppCompatActivity parent) {
		loadLocation(Configs.instance(parent));
	}

	private void saveLocation(Configs configs) {
		configs.setFloat(Configs.ConfigKey.virtualCameraDegLat, (float) decimalLatitude);
		configs.setFloat(Configs.ConfigKey.virtualCameraDegLon, (float) decimalLongitude);
	}

	private void loadLocation(Configs configs) {
		decimalLatitude = configs.getFloat(Configs.ConfigKey.virtualCameraDegLat);
		decimalLongitude = configs.getFloat(Configs.ConfigKey.virtualCameraDegLon);
	}

	@Override
	public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
		updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
	}

	@Override
	public void updateOrientation(OrientationManager.OrientationEvent event) {
		degAzimuth = event.camera.x;
		degPitch = event.camera.y;
		degRoll = event.camera.z;
	}

	@OptIn(markerClass = androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
	public void computeViewAngles(Context parent, Camera camera, PreviewView cameraView) {
		CameraManager cameraManager = (CameraManager) parent.getSystemService(Context.CAMERA_SERVICE);
		CameraCharacteristics characteristics = null;
		try {
			characteristics = cameraManager.getCameraCharacteristics(Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId());
		} catch (CameraAccessException e) {
			return;
		}
		// Note this is an approximation (see http://stackoverflow.com/questions/39965408/what-is-the-android-camera2-api-equivalent-of-camera-parameters-gethorizontalvie ).
		// This does not take into account the aspect ratio of the preview or camera, it's up to the caller to do this (e.g., see Preview.getViewAngleX(), getViewAngleY()).
		Rect sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
		SizeF physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
		android.util.Size pixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
		float [] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
		if( sensorSize == null || physicalSize == null || pixelSize == null || focalLengths == null || focalLengths.length == 0 ) {
			// in theory this should never happen according to the documentation, but I've had a report of physical_size (SENSOR_INFO_PHYSICAL_SIZE)
			// being null on an EXTERNAL Camera2 device, see https://sourceforge.net/p/opencamera/tickets/754/
			// fall back to a default
			return;
		}

		double viewAngleX = Math.toDegrees(2.0 * Math.atan(0.5 * physicalSize.getWidth() / focalLengths[0]));
		double viewAngleY = Math.toDegrees(2.0 * Math.atan(0.5 * physicalSize.getHeight() / focalLengths[0]));

		fieldOfViewDeg = new Vector2d(viewAngleX, viewAngleY);

//		StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//		Size[] resolutions = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
//		Size previewSize = getMaxResolution(resolutions);
	}

//	private Size getMaxResolution(Size[] resolutions) {
//		Size result = resolutions[0];
//		for (Size resolution: resolutions) {
//			if (calculateAspectRatio(result) < calculateAspectRatio(resolution)) {
//				result = resolution;
//			}
//		}
//
//		return result;
//	}
//
//	private double calculateAspectRatio(Size size) {
//		return (double)size.getWidth() / (double)size.getHeight();
//	}

	/** Returns the horizontal angle of view in degrees (when unzoomed).
	 */
	public double getViewAngleX(Vector2d size, Vector2d cameraSize) {
		if( size == null ) {
			return this.fieldOfViewDeg.x;
		}
		double view_aspect_ratio = cameraSize.x/cameraSize.y;
		double actual_aspect_ratio = ((float)size.x)/(float)size.y;
		if( Math.abs(actual_aspect_ratio - view_aspect_ratio) < 1.0e-5f ) {
			return this.fieldOfViewDeg.x;
		}
		else if( actual_aspect_ratio > view_aspect_ratio ) {
			return this.fieldOfViewDeg.x;
		}
		else {
			double aspect_ratio_scale = actual_aspect_ratio/view_aspect_ratio;
			//float actual_view_angle_x = view_angle_x*aspect_ratio_scale;
			double actual_view_angle_x = (float)Math.toDegrees(2.0 * Math.atan(aspect_ratio_scale * Math.tan(Math.toRadians(cameraSize.x) / 2.0)));
			/*if( MyDebug.LOG )
				Log.d(TAG, "actual_view_angle_x: " + actual_view_angle_x);*/
			return actual_view_angle_x;
		}
	}

	/** Returns the vertical angle of view in degrees (when unzoomed).
	 */
	public double getViewAngleY(Vector2d size, Vector2d cameraSize) {
		if( size == null ) {
			return this.fieldOfViewDeg.y;
		}
		double view_aspect_ratio = cameraSize.x/cameraSize.y;
		double actual_aspect_ratio = ((float)size.x)/(float)size.y;
		if( Math.abs(actual_aspect_ratio - view_aspect_ratio) < 1.0e-5f ) {
			return this.fieldOfViewDeg.y;
		}
		else if( actual_aspect_ratio > view_aspect_ratio ) {
			double aspect_ratio_scale = view_aspect_ratio/actual_aspect_ratio;
			//float actual_view_angle_y = view_angle_y*aspect_ratio_scale;
			double actual_view_angle_y = (float)Math.toDegrees(2.0 * Math.atan(aspect_ratio_scale * Math.tan(Math.toRadians(cameraSize.y) / 2.0)));
			/*if( MyDebug.LOG )
				Log.d(TAG, "actual_view_angle_y: " + actual_view_angle_y);*/
			return actual_view_angle_y;
		}
		else {
			return this.fieldOfViewDeg.y;
		}
	}


}

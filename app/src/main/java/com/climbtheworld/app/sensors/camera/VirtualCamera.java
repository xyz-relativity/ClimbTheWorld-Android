package com.climbtheworld.app.sensors.camera;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.util.SizeF;

import androidx.appcompat.app.AppCompatActivity;

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
	public Vector2d fieldOfViewDeg = new Vector2d(55.0f, 43.0f);
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

	private Vector2d computeViewAngles(CameraCharacteristics characteristics) {
		// Note this is an approximation (see http://stackoverflow.com/questions/39965408/what-is-the-android-camera2-api-equivalent-of-camera-parameters-gethorizontalvie ).
		// This does not take into account the aspect ratio of the preview or camera, it's up to the caller to do this (e.g., see Preview.getViewAngleX(), getViewAngleY()).
		Rect active_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
		SizeF physical_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
		android.util.Size pixel_size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
		float [] focal_lengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
		if( active_size == null || physical_size == null || pixel_size == null || focal_lengths == null || focal_lengths.length == 0 ) {
			// in theory this should never happen according to the documentation, but I've had a report of physical_size (SENSOR_INFO_PHYSICAL_SIZE)
			// being null on an EXTERNAL Camera2 device, see https://sourceforge.net/p/opencamera/tickets/754/
			// fall back to a default
			return fieldOfViewDeg;
		}
		//camera_features.view_angle_x = (float)Math.toDegrees(2.0 * Math.atan2(physical_size.getWidth(), (2.0 * focal_lengths[0])));
		//camera_features.view_angle_y = (float)Math.toDegrees(2.0 * Math.atan2(physical_size.getHeight(), (2.0 * focal_lengths[0])));
		float frac_x = ((float)active_size.width())/(float)pixel_size.getWidth();
		float frac_y = ((float)active_size.height())/(float)pixel_size.getHeight();
		float view_angle_x = (float)Math.toDegrees(2.0 * Math.atan2(physical_size.getWidth() * frac_x, (2.0 * focal_lengths[0])));
		float view_angle_y = (float)Math.toDegrees(2.0 * Math.atan2(physical_size.getHeight() * frac_y, (2.0 * focal_lengths[0])));
		return new Vector2d(view_angle_x, view_angle_y);
	}

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

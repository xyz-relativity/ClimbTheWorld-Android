package com.climbtheworld.app.augmentedreality;

import android.os.SystemClock;

import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.camera.VirtualCamera;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Vector4d;

/**
 * Shows on a minimap where a camera that ARCore moves is and which way it looks, as GPS and the
 * compass do on the A.R. view's minimap. ARCore moves it on every frame, faster than the minimap
 * needs to follow.
 */
public class CameraMinimap {
	// How often the minimap follows the camera, which it animates to.
	private static final long UPDATE_MS = 200;

	private final MapLibreMapWidget minimap;
	// Which way the camera looks, as the minimap takes it.
	private final OrientationManager.OrientationEvent orientation =
			new OrientationManager.OrientationEvent();
	private long updatedAt;

	public CameraMinimap(MapLibreMapWidget minimap) {
		this.minimap = minimap;
	}

	/**
	 * @param maxDistanceMeters how far the A.R. shows, which the view cone reaches
	 */
	public void show(VirtualCamera camera, double maxDistanceMeters) {
		long now = SystemClock.elapsedRealtime();
		if (now - updatedAt < UPDATE_MS) {
			return;
		}
		updatedAt = now;
		minimap.setViewCone(camera.angleOfViewDeg.x, maxDistanceMeters);
		minimap.onLocationChange(new MapCoordinate(camera.decimalLatitude, camera.decimalLongitude,
				camera.elevationMeters));
		orientation.camera = new Vector4d(camera.degAzimuth, camera.degPitch, camera.degRoll, 0);
		minimap.onOrientationChange(orientation.camera, orientation);
	}
}

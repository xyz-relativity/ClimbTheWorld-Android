package com.climbtheworld.app.augmentedreality;

import android.os.SystemClock;

import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.camera.VirtualCamera;
import com.climbtheworld.app.sensors.orientation.OrientationManager;

/**
 * Shows on a minimap where a camera that ARCore moves is and which way it looks, as GPS and the
 * compass do on the A.R. view's minimap.
 */
public class CameraMinimap {
	// How often the minimap moves to where the camera is, which it eases to: ARCore moves the
	// camera on every frame, more often than the minimap needs to follow.
	private static final long MOVE_MS = 200;

	private final MapLibreMapWidget minimap;
	// Which way the camera looks, as the minimap takes it.
	private final OrientationManager.OrientationEvent orientation =
			new OrientationManager.OrientationEvent();
	private long movedAt;

	public CameraMinimap(MapLibreMapWidget minimap) {
		this.minimap = minimap;
	}

	/**
	 * @param degrees how far off the heading the camera is turned by may be, see
	 *                OrientationManager.OrientationEvent's headingUncertaintyDegrees, for the
	 *                minimap's compass button to blur by; NaN when nothing tells
	 */
	public void setHeadingUncertaintyDegrees(double degrees) {
		orientation.headingUncertaintyDegrees = degrees;
	}

	/**
	 * @param maxDistanceMeters how far the A.R. shows, which the view cone reaches
	 */
	public void show(VirtualCamera camera, double maxDistanceMeters) {
		// Turned on every frame, as the compass turns it on every reading: it turns at once, with
		// no easing, so it would turn in steps if told less often.
		orientation.camera.x = camera.degAzimuth;
		orientation.camera.y = camera.degPitch;
		orientation.camera.z = camera.degRoll;
		minimap.onOrientationChange(orientation.camera, orientation);

		long now = SystemClock.elapsedRealtime();
		if (now - movedAt < MOVE_MS) {
			return;
		}
		movedAt = now;
		minimap.setViewCone(camera.angleOfViewDeg.x, maxDistanceMeters);
		minimap.onLocationChange(new MapCoordinate(camera.decimalLatitude, camera.decimalLongitude,
				camera.elevationMeters));
	}
}

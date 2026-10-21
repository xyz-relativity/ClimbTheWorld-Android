package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.sensors.orientation.OrientationManager;

/**
 * ARCore's tracking for the A.R. view, as the A.R. view sees it: once the camera may be used, it
 * takes the camera on the phones ARCore runs on and shows what it sees. While it tracks and its
 * world is tied to the map, by GPS, the compass and the routes the user aligns on, it moves and
 * turns Globals.virtualCamera in place of GPS and the sensors, which stand in the rest of the
 * time. Near mapped routes, see AlignmentRange, it also shows its tools to aim at them and align
 * on them. Elsewhere, the A.R. view runs on CameraX and the sensors.
 * <p>
 * Only the non-FOSS build has it, as ARCore is not free software; see ArCoreFeatures, which each
 * build has its own of. Without it, nothing here does anything.
 */
public interface ArViewTracking {
	/**
	 * Tries to take the camera, once the user let the app use it.
	 *
	 * @return false when it never does, as in the FOSS build; otherwise the listener is told
	 * whether it does
	 */
	default boolean start() {
		return false;
	}

	default void onResume() {
	}

	default void onPause() {
	}

	default void onDestroy() {
	}

	/**
	 * Takes a compass reading.
	 */
	default void onOrientation(OrientationManager.OrientationEvent event) {
	}

	/**
	 * Takes a GPS fix.
	 */
	default void onLocation(double latitude, double longitude, double altitudeMeters,
	                        double accuracyMeters) {
	}

	/**
	 * @param meters how far the closest mapped route is, NaN while none is known
	 */
	default void setNearestRouteMeters(double meters) {
	}

	/**
	 * Told on the main thread.
	 */
	interface Listener {
		/**
		 * ARCore has the camera, and shows what it sees, until onGaveUp. Told again whenever it
		 * starts running again, as after a pause.
		 */
		void onStarted();

		/**
		 * ARCore does not take the camera, or gave it back for good: the A.R. view runs on CameraX
		 * and the sensors.
		 */
		void onGaveUp();

		/**
		 * @param moves whether ARCore moves and turns Globals.virtualCamera from now on, rather
		 *              than GPS and the sensors
		 */
		void onMovesCameraChanged(boolean moves);

		/**
		 * ARCore moved and turned Globals.virtualCamera.
		 */
		void onCameraMoved();
	}
}
